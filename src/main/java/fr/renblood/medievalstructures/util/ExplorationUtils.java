package fr.renblood.medievalstructures.util;

import fr.renblood.medievalstructures.network.PacketExplorationAction;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ExplorationUtils {

    public static List<BlockPos> findChests(ServerLevel level, BlockPos center) {
        List<BlockPos> chests = new ArrayList<>();
        // Zone : 3 au dessus, 5 en dessous, 7 de rayon autour (15x15)
        for (int x = -7; x <= 7; x++) {
            for (int z = -7; z <= 7; z++) {
                for (int y = -5; y <= 3; y++) {
                    if (x == 0 && y == 0 && z == 0) continue;
                    BlockPos p = center.offset(x, y, z);
                    if(!fr.renblood.medievalstructures.manager.ExplorationTerrain.loaded(level,p))continue;
                    BlockState state = level.getBlockState(p);
                    if (state.getBlock() instanceof ChestBlock) {
                        chests.add(p);
                    }
                }
            }
        }
        return chests;
    }

    public static Map<String, List<BlockPos>> assignAndRenameChests(ServerLevel level, BlockPos center, List<PacketExplorationAction.PlayerConfig> configs) {
        List<BlockPos> availableChests = findChests(level, center);
        
        // 0. Nettoyer TOUS les coffres de la zone avant attribution
        for (BlockPos pos : availableChests) {
            BlockEntity chestBe = level.getBlockEntity(pos);
            if (chestBe instanceof ChestBlockEntity chest) {
                // On enlève le nom personnalisé s'il commence par "Coffre de" (pour ne pas effacer les noms custom des joueurs hors système)
                // Ou on efface tout pour être sûr ? "Coffre de" est plus sûr.
                if (chest.hasCustomName() && chest.getCustomName().getString().startsWith("Coffre de ")) {
                    chest.setCustomName(null);
                    chest.setChanged();
                    level.sendBlockUpdated(pos, level.getBlockState(pos), level.getBlockState(pos), 3);
                }
            }
        }

        Map<BlockPos, String> chestOwners = new HashMap<>();
        Map<String, List<BlockPos>> playerChests = new HashMap<>();
        
        int chestIndex = 0;

        // 1. Attribuer les coffres
        for (PacketExplorationAction.PlayerConfig config : configs) {
            List<BlockPos> assigned = new ArrayList<>();
            for (int i = 0; i < config.chestCount; i++) {
                if (chestIndex < availableChests.size()) {
                    BlockPos pos = availableChests.get(chestIndex++);
                    chestOwners.put(pos, config.playerName);
                    assigned.add(pos);
                }
            }
            playerChests.put(config.playerName, assigned);
        }

        // 2. Appliquer les noms
        for (Map.Entry<BlockPos, String> entry : chestOwners.entrySet()) {
            BlockPos pos = entry.getKey();
            String owner = entry.getValue();
            
            BlockState state = level.getBlockState(pos);
            String finalName = "Coffre de " + owner;
            
            // Vérifier double coffre
            if (state.getBlock() instanceof ChestBlock) {
                ChestType type = state.getValue(ChestBlock.TYPE);
                if (type != ChestType.SINGLE) {
                    Direction facing = state.getValue(ChestBlock.FACING);
                    Direction otherDir = type == ChestType.RIGHT ? facing.getCounterClockWise() : facing.getClockWise();
                    BlockPos otherPos = pos.relative(otherDir);
                    
                    if (chestOwners.containsKey(otherPos)) {
                        String otherOwner = chestOwners.get(otherPos);
                        if (!otherOwner.equals(owner)) {
                            finalName = "Coffre de " + owner + " & " + otherOwner;
                        }
                    }
                }
            }
            
            BlockEntity chestBe = level.getBlockEntity(pos);
            if (chestBe instanceof ChestBlockEntity chest) {
                chest.setCustomName(Component.literal(finalName));
                chest.setChanged();
                level.sendBlockUpdated(pos, state, state, 3);
            }
        }
        
        return playerChests;
    }
}
