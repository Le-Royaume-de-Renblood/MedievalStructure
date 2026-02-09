package fr.renblood.medievalstructures.manager;

import fr.renblood.medievalstructures.block.entity.InnStructureBlockEntity;
import fr.renblood.medievalstructures.inn.Inn;
import fr.renblood.medievalstructures.inn.InnManager;
import fr.renblood.medievalstructures.inn.InnRoom;
import fr.renblood.medievalstructures.network.PacketHandler;
import fr.renblood.medievalstructures.network.PacketSyncRoomSelection;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraftforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class RoomCreationManager {
    private static final RoomCreationManager INSTANCE = new RoomCreationManager();
    
    private final Map<UUID, RoomCreationData> currentCreations = new HashMap<>();

    public static RoomCreationManager getInstance() {
        return INSTANCE;
    }

    public void startCreation(ServerPlayer player, String innName, int number, int size, double price) {
        currentCreations.put(player.getUUID(), new RoomCreationData(innName, number, size, price));
        syncToClient(player);
        player.sendSystemMessage(Component.literal("Mode création de chambre activé pour l'auberge '" + innName + "', chambre " + number + "."));
        player.sendSystemMessage(Component.literal("Utilisez le Bâton de l'Aubergiste pour définir la zone (Shift+Clic Gauche / Shift+Clic Droit)."));
        player.sendSystemMessage(Component.literal("Une fois la zone définie, tapez /auberge room confirm pour valider."));
    }

    public boolean isCreating(ServerPlayer player) {
        return currentCreations.containsKey(player.getUUID());
    }

    public void setPos1(ServerPlayer player, BlockPos pos) {
        RoomCreationData data = currentCreations.get(player.getUUID());
        if (data != null) {
            if (checkInsideInn(player, data.innName, pos)) {
                data.p1 = pos;
                syncToClient(player);
                player.sendSystemMessage(Component.literal("Position 1 définie : " + pos));
            } else {
                player.sendSystemMessage(Component.literal("Erreur : Ce point est en dehors de la zone de l'auberge '" + data.innName + "'."));
            }
        }
    }

    public void setPos2(ServerPlayer player, BlockPos pos) {
        RoomCreationData data = currentCreations.get(player.getUUID());
        if (data != null) {
            if (checkInsideInn(player, data.innName, pos)) {
                data.p2 = pos;
                syncToClient(player);
                player.sendSystemMessage(Component.literal("Position 2 définie : " + pos));
            } else {
                player.sendSystemMessage(Component.literal("Erreur : Ce point est en dehors de la zone de l'auberge '" + data.innName + "'."));
            }
        }
    }

    private boolean checkInsideInn(ServerPlayer player, String innName, BlockPos pos) {
        ServerLevel level = player.serverLevel();
        InnManager manager = InnManager.get(level);
        Inn inn = manager.getInn(innName);
        
        if (inn == null || inn.getLocation() == null) return false;
        
        BlockEntity be = level.getBlockEntity(inn.getLocation());
        if (be instanceof InnStructureBlockEntity innBe) {
            return innBe.isInside(pos);
        }
        return false;
    }

    public void validate(ServerPlayer player) {
        RoomCreationData data = currentCreations.get(player.getUUID());
        if (data == null) {
            player.sendSystemMessage(Component.literal("Vous n'êtes pas en train de créer une chambre."));
            return;
        }

        if (data.p1 == null || data.p2 == null) {
            player.sendSystemMessage(Component.literal("Vous devez définir les deux coins de la chambre avec le bâton."));
            return;
        }

        ServerLevel level = player.serverLevel();
        InnManager manager = InnManager.get(level);
        Inn inn = manager.getInn(data.innName);

        if (inn == null) {
            player.sendSystemMessage(Component.literal("L'auberge '" + data.innName + "' n'existe plus."));
            currentCreations.remove(player.getUUID());
            syncToClient(player);
            return;
        }
        
        // Double vérification que les points sont toujours dans l'auberge (au cas où l'auberge aurait bougé)
        if (!checkInsideInn(player, data.innName, data.p1) || !checkInsideInn(player, data.innName, data.p2)) {
            player.sendSystemMessage(Component.literal("Erreur : La zone définie n'est plus entièrement dans l'auberge."));
            return;
        }

        // Scan for bed
        List<BlockPos> beds = new ArrayList<>();
        int minX = Math.min(data.p1.getX(), data.p2.getX());
        int minY = Math.min(data.p1.getY(), data.p2.getY());
        int minZ = Math.min(data.p1.getZ(), data.p2.getZ());
        int maxX = Math.max(data.p1.getX(), data.p2.getX());
        int maxY = Math.max(data.p1.getY(), data.p2.getY());
        int maxZ = Math.max(data.p1.getZ(), data.p2.getZ());

        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    BlockState state = level.getBlockState(pos);
                    if (state.getBlock() instanceof BedBlock) {
                        if (state.getValue(BedBlock.PART) == BedPart.HEAD) {
                            beds.add(pos);
                        }
                    }
                }
            }
        }

        if (beds.isEmpty()) {
            player.sendSystemMessage(Component.literal("Erreur : Aucun lit trouvé dans la zone sélectionnée."));
            return;
        }
        if (beds.size() > 1) {
            player.sendSystemMessage(Component.literal("Erreur : Plusieurs lits trouvés. Une chambre ne doit contenir qu'un seul lit."));
            return;
        }

        BlockPos headPos = beds.get(0);
        BlockState bedState = level.getBlockState(headPos);
        Direction facing = bedState.getValue(BedBlock.FACING);
        BlockPos footPos = headPos.relative(facing.getOpposite());

        // Check if room number already exists
        if (inn.getRoom(data.number) != null) {
            player.sendSystemMessage(Component.literal("Erreur : La chambre " + data.number + " existe déjà."));
            return;
        }

        inn.addRoom(new InnRoom(data.number, headPos, footPos, data.p1, data.p2, data.size, data.price));
        manager.setDirty();

        player.sendSystemMessage(Component.literal("Chambre " + data.number + " créée avec succès !"));
        currentCreations.remove(player.getUUID());
        syncToClient(player);
    }

    private void syncToClient(ServerPlayer player) {
        RoomCreationData data = currentCreations.get(player.getUUID());
        BlockPos p1 = (data != null) ? data.p1 : null;
        BlockPos p2 = (data != null) ? data.p2 : null;
        PacketHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> player), new PacketSyncRoomSelection(p1, p2));
    }

    private static class RoomCreationData {
        String innName;
        int number;
        int size;
        double price;
        BlockPos p1;
        BlockPos p2;

        public RoomCreationData(String innName, int number, int size, double price) {
            this.innName = innName;
            this.number = number;
            this.size = size;
            this.price = price;
        }
    }
}
