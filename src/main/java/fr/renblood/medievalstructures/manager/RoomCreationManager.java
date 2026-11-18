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

import java.util.HashMap;
import java.util.LinkedHashMap;
import net.minecraft.server.MinecraftServer;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.util.Map;
import java.util.UUID;

@Mod.EventBusSubscriber(modid = "medieval_structures")
public class RoomCreationManager {
    private static final RoomCreationManager INSTANCE = new RoomCreationManager();
    
    private final Map<UUID, RoomCreationData> currentCreations = new HashMap<>();
    private final Map<UUID, ScanJob> scans = new LinkedHashMap<>();
    private record ScanJob(ServerPlayer player, RoomCreationData data, ServerLevel level, Inn inn,
                           int startedAt, RoomBedScan scan) {}

    @SubscribeEvent
    public static void logout(net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent event) {
        UUID id = event.getEntity().getUUID();
        INSTANCE.currentCreations.remove(id); INSTANCE.scans.remove(id);
    }
    @SubscribeEvent
    public static void stop(net.minecraftforge.event.server.ServerStoppedEvent event) {
        INSTANCE.currentCreations.clear(); INSTANCE.scans.clear();
    }

    public static RoomCreationManager getInstance() {
        return INSTANCE;
    }

    public void startCreation(ServerPlayer player, String innName, int number, int size, double price) {
        scans.remove(player.getUUID());
        currentCreations.put(player.getUUID(), new RoomCreationData(innName, number, size, price, player.level().dimension()));
        syncToClient(player);
        player.sendSystemMessage(Component.literal("Mode création de chambre activé pour l'auberge '" + innName + "', chambre " + number + "."));
        player.sendSystemMessage(Component.literal("Utilisez le Bâton de l'Aubergiste pour définir la zone (Shift+Clic Gauche / Shift+Clic Droit)."));
        player.sendSystemMessage(Component.literal("Une fois la zone définie, tapez /ms auberge room confirm pour valider."));
    }

    public boolean isCreating(ServerPlayer player) {
        return currentCreations.containsKey(player.getUUID());
    }

    public void setPos1(ServerPlayer player, BlockPos pos) {
        RoomCreationData data = currentCreations.get(player.getUUID());
        if (data != null) {
            if (player.level().dimension().equals(data.dimension) && checkInsideInn(player, data.innName, pos)) {
                scans.remove(player.getUUID());
                data.p1 = pos.immutable();
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
            if (player.level().dimension().equals(data.dimension) && checkInsideInn(player, data.innName, pos)) {
                scans.remove(player.getUUID());
                data.p2 = pos.immutable();
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
        
        if (inn == null || inn.getLocation() == null || !level.hasChunkAt(inn.getLocation())) return false;
        
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

        if (!player.level().dimension().equals(data.dimension)) {
            player.sendSystemMessage(Component.literal("Retournez dans la dimension de l'auberge pour valider la chambre."));
            return;
        }
        if (scans.containsKey(player.getUUID())) {
            player.sendSystemMessage(Component.literal("Vérification déjà en cours…"));
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

        if (inn.getRoom(data.number) != null) {
            player.sendSystemMessage(Component.literal("Erreur : La chambre " + data.number + " existe déjà."));
            return;
        }
        RoomBedScan scan = new RoomBedScan(data.p1, data.p2, level::hasChunkAt, pos -> {
            BlockState state = level.getBlockState(pos);
            return state.getBlock() instanceof BedBlock && state.getValue(BedBlock.PART) == BedPart.HEAD;
        });
        scans.put(player.getUUID(), new ScanJob(player, data, level, inn, level.getServer().getTickCount(), scan));
        player.sendSystemMessage(Component.literal("Vérification de la chambre en cours…"));
    }

    /** Shared budget: at most 4096 block reads per tick, 512 per room, round robin. */
    public void tick(MinecraftServer server) {
        tick(server, player -> server.getPlayerList().getPlayer(player.getUUID()) == player);
    }

    void tick(MinecraftServer server, java.util.function.Predicate<ServerPlayer> currentSession) {
        int turns = Math.min(scans.size(), 8);
        while (turns-- > 0 && !scans.isEmpty()) {
            UUID id = scans.keySet().iterator().next();
            ScanJob job = scans.remove(id);
            ServerPlayer player = job.player();
            if (player.isRemoved() || player.hasDisconnected() || !currentSession.test(player)
                    || currentCreations.get(id) != job.data()) continue;
            if (player.serverLevel() != job.level()) {
                player.sendSystemMessage(Component.literal("Vérification annulée : vous avez changé de dimension."));
                continue;
            }
            if (server.getTickCount() - job.startedAt() >= 1200) {
                player.sendSystemMessage(Component.literal("Vérification interrompue : réduisez la zone de la chambre puis confirmez à nouveau."));
                continue;
            }
            RoomBedScan.State state = job.scan().tick(512);
            if (state == RoomBedScan.State.WORKING) scans.put(id, job);
            else if (state == RoomBedScan.State.COMPLETE) finish(job);
            else player.sendSystemMessage(Component.literal(state == RoomBedScan.State.UNLOADED
                    ? "Vérification interrompue : une partie de la chambre n'est pas chargée. Approchez-vous puis confirmez à nouveau."
                    : "Erreur : Plusieurs lits trouvés. Une chambre ne doit contenir qu'un seul lit."));
        }
    }

    private void finish(ScanJob job) {
        ServerPlayer player = job.player();
        RoomCreationData data = job.data();
        ServerLevel level = job.level();
        InnManager manager = InnManager.get(level);
        Inn inn = manager.getInn(data.innName);
        if (inn != job.inn() || inn.getRoom(data.number) != null
                || !checkInsideInn(player, data.innName, data.p1) || !checkInsideInn(player, data.innName, data.p2)) {
            player.sendSystemMessage(Component.literal("L'auberge a changé pendant la vérification. Confirmez à nouveau."));
            return;
        }
        BlockPos headPos = job.scan().bed();
        if (headPos == null || !level.hasChunkAt(headPos)) {
            player.sendSystemMessage(Component.literal("Erreur : Aucun lit chargé trouvé dans la zone sélectionnée."));
            return;
        }
        BlockState bedState = level.getBlockState(headPos);
        if (!(bedState.getBlock() instanceof BedBlock) || bedState.getValue(BedBlock.PART) != BedPart.HEAD) {
            player.sendSystemMessage(Component.literal("Le lit a changé pendant la vérification. Confirmez à nouveau."));
            return;
        }
        Direction facing = bedState.getValue(BedBlock.FACING);
        BlockPos footPos = headPos.relative(facing.getOpposite());

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
        ResourceKey<Level> dimension;
        BlockPos p1;
        BlockPos p2;

        public RoomCreationData(String innName, int number, int size, double price, ResourceKey<Level> dimension) {
            this.dimension = dimension;
            this.innName = innName;
            this.number = number;
            this.size = size;
            this.price = price;
        }
    }
}
