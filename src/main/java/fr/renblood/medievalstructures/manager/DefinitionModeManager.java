package fr.renblood.medievalstructures.manager;

import fr.renblood.medievalstructures.block.entity.InnStructureBlockEntity;
import fr.renblood.medievalstructures.network.PacketHandler;
import fr.renblood.medievalstructures.network.PacketSyncDefinitionMode;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.network.chat.Component;
import net.minecraftforge.network.PacketDistributor;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class DefinitionModeManager {
    private static final DefinitionModeManager INSTANCE = new DefinitionModeManager();
    private final Map<UUID, BlockPos> playersInDefinitionMode = new HashMap<>();
    private final Map<UUID, BlockPos> point1 = new HashMap<>();
    private final Map<UUID, BlockPos> point2 = new HashMap<>();

    public static DefinitionModeManager getInstance() {
        return INSTANCE;
    }

    public void enterDefinitionMode(Player player, BlockPos structureBlockPos) {
        playersInDefinitionMode.put(player.getUUID(), structureBlockPos);
        syncToClient(player);
    }

    public void leaveDefinitionMode(Player player) {
        playersInDefinitionMode.remove(player.getUUID());
        point1.remove(player.getUUID());
        point2.remove(player.getUUID());
        syncToClient(player);
    }

    public boolean isInDefinitionMode(Player player) {
        return playersInDefinitionMode.containsKey(player.getUUID());
    }

    public BlockPos getStructurePos(Player player) {
        return playersInDefinitionMode.get(player.getUUID());
    }

    public void setPoint1(Player player, BlockPos pos) {
        if (isInDefinitionMode(player)) {
            point1.put(player.getUUID(), pos);
            if (player instanceof ServerPlayer serverPlayer) {
                serverPlayer.sendSystemMessage(Component.literal("Point 1 défini à: " + pos));
                checkReadyToValidate(serverPlayer);
                syncToClient(player);
            }
        }
    }

    public void setPoint2(Player player, BlockPos pos) {
        if (isInDefinitionMode(player)) {
            point2.put(player.getUUID(), pos);
            if (player instanceof ServerPlayer serverPlayer) {
                serverPlayer.sendSystemMessage(Component.literal("Point 2 défini à: " + pos));
                checkReadyToValidate(serverPlayer);
                syncToClient(player);
            }
        }
    }

    public void validate(ServerPlayer player) {
        if (!isInDefinitionMode(player)) {
            player.sendSystemMessage(Component.literal("Vous n'êtes pas en mode définition."));
            return;
        }

        UUID playerUUID = player.getUUID();
        if (point1.containsKey(playerUUID) && point2.containsKey(playerUUID)) {
            BlockPos pos1 = point1.get(playerUUID);
            BlockPos pos2 = point2.get(playerUUID);
            BlockPos structureBlockPos = playersInDefinitionMode.get(playerUUID);

            // Sauvegarde du volume dans le TileEntity
            BlockEntity be = player.level().getBlockEntity(structureBlockPos);
            if (be instanceof InnStructureBlockEntity innBe) {
                innBe.setVolume(pos1, pos2);
                player.sendSystemMessage(Component.literal("Zone validée !"));
                player.sendSystemMessage(Component.literal("Maintenant, liez ce bloc à une auberge avec /auberge link <nom> en regardant le bloc."));
            } else {
                player.sendSystemMessage(Component.literal("Erreur: Impossible de trouver le bloc de structure."));
            }

            leaveDefinitionMode(player);
        } else {
            player.sendSystemMessage(Component.literal("Vous devez définir les deux points avant de valider."));
        }
    }

    public BlockPos getPoint1(UUID playerUUID) {
        return point1.get(playerUUID);
    }

    public BlockPos getPoint2(UUID playerUUID) {
        return point2.get(playerUUID);
    }

    private void checkReadyToValidate(ServerPlayer player) {
        UUID playerUUID = player.getUUID();
        if (point1.containsKey(playerUUID) && point2.containsKey(playerUUID)) {
            player.sendSystemMessage(Component.literal("Zone définie. Tapez /ms validate pour confirmer."));
        }
    }

    public void syncToClient(Player player) {
        if (player instanceof ServerPlayer serverPlayer) {
            boolean inMode = isInDefinitionMode(player);
            BlockPos p1 = point1.get(player.getUUID());
            BlockPos p2 = point2.get(player.getUUID());
            PacketHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> serverPlayer), new PacketSyncDefinitionMode(inMode, p1, p2));
        }
    }

    public void onStructureBlockBroken(BlockPos pos, ServerPlayer player) {
        if (isInDefinitionMode(player) && playersInDefinitionMode.get(player.getUUID()).equals(pos)) {
            leaveDefinitionMode(player);
            player.sendSystemMessage(Component.literal("Configuration annulée car le bloc a été détruit."));
        }
    }
}
