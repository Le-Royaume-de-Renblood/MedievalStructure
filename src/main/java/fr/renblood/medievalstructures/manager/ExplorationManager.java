package fr.renblood.medievalstructures.manager;

import fr.renblood.medievalstructures.init.ModDimensions;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class ExplorationManager extends SavedData {
    private static final String DATA_NAME = "medieval_structures_explorations";
    private final List<PendingExploration> pendingExplorations = new ArrayList<>();
    private final List<ActiveExploration> activeExplorations = new ArrayList<>();
    private int nextExplorationX = 0;
    
    public static boolean DEBUG_MODE = false;

    public static ExplorationManager get(ServerLevel level) {
        return level.getServer().overworld().getDataStorage().computeIfAbsent(ExplorationManager::load, ExplorationManager::new, DATA_NAME);
    }
    
    public BlockPos getLastExplorationSpawn() {
        if (!activeExplorations.isEmpty()) {
            return activeExplorations.get(activeExplorations.size() - 1).spawnPos;
        }
        return null;
    }

    public void startCountdown(ServerLevel level, BlockPos origin, int durationMinutes) {
        int x = nextExplorationX;
        nextExplorationX += 5000;
        
        if (DEBUG_MODE) {
            durationMinutes = 0;
        }
        
        pendingExplorations.add(new PendingExploration(origin, durationMinutes, x));
        setDirty();
    }

    public void tick(MinecraftServer server) {
        Iterator<PendingExploration> itPending = pendingExplorations.iterator();
        while (itPending.hasNext()) {
            PendingExploration explo = itPending.next();
            
            if (explo.state == ExplorationState.COUNTDOWN) {
                explo.ticksRemaining--;

                if (explo.ticksRemaining % 20 == 0) {
                    ServerLevel level = server.overworld();
                    spawnParticles(level, explo.origin);
                    
                    int seconds = explo.ticksRemaining / 20;
                    if (seconds == 30 || seconds <= 5) {
                        level.getPlayers(p -> p.distanceToSqr(explo.origin.getX(), explo.origin.getY(), explo.origin.getZ()) < 1000).forEach(p -> 
                            p.sendSystemMessage(Component.literal("Exploration dans " + seconds + "s !"))
                        );
                    }
                }

                if (explo.ticksRemaining <= 0) {
                    explo.state = ExplorationState.TELEPORTING;
                    explo.ticksRemaining = 5;
                    setDirty();
                }
            } else if (explo.state == ExplorationState.TELEPORTING) {
                if (explo.ticksRemaining > 0) {
                    explo.ticksRemaining--;
                    
                    if (explo.ticksRemaining == 2) {
                        ServerLevel explorationWorld = server.getLevel(ModDimensions.EXPLORATION_LEVEL_KEY);
                        if (explorationWorld != null) {
                            explorationWorld.getChunk(explo.targetX >> 4, 0);
                        }
                    }
                } else {
                    BlockPos actualSpawn = teleportPlayers(server, explo);
                    
                    if (actualSpawn != null) {
                        int durationTicks = explo.durationMinutes > 0 ? explo.durationMinutes * 60 * 20 : 30 * 20;
                        activeExplorations.add(new ActiveExploration(actualSpawn, durationTicks, explo.origin));
                    }
                    
                    itPending.remove();
                    setDirty();
                }
            }
        }
        
        Iterator<ActiveExploration> itActive = activeExplorations.iterator();
        while (itActive.hasNext()) {
            ActiveExploration active = itActive.next();
            active.ticksRemaining--;
            
            if (active.ticksRemaining % 20 == 0) {
                ServerLevel explorationWorld = server.getLevel(ModDimensions.EXPLORATION_LEVEL_KEY);
                if (explorationWorld != null) {
                    AABB zone = new AABB(active.spawnPos).inflate(2000, 320, 2000);
                    List<ServerPlayer> players = explorationWorld.getEntitiesOfClass(ServerPlayer.class, zone);
                    
                    int secondsLeft = active.ticksRemaining / 20;
                    String timeStr = String.format("%02d:%02d", secondsLeft / 60, secondsLeft % 60);
                    Component msg = Component.literal("Temps restant : " + timeStr).withStyle(net.minecraft.ChatFormatting.YELLOW);
                    
                    for (ServerPlayer player : players) {
                        player.displayClientMessage(msg, true);
                        if (active.ticksRemaining % 100 == 0) {
                            player.getPersistentData().putLong("ExplorationOrigin", active.origin.asLong());
                        }
                    }
                    
                    if (secondsLeft == 60 || secondsLeft == 30 || secondsLeft == 10) {
                        for (ServerPlayer player : players) {
                            player.sendSystemMessage(Component.literal("Fin de l'exploration dans " + secondsLeft + " secondes !").withStyle(net.minecraft.ChatFormatting.RED));
                        }
                    }
                }
            }
            
            if (active.ticksRemaining <= 0) {
                endExploration(server, active);
                itActive.remove();
                setDirty();
            }
        }
    }
    
    private void endExploration(MinecraftServer server, ActiveExploration active) {
        ServerLevel explorationWorld = server.getLevel(ModDimensions.EXPLORATION_LEVEL_KEY);
        ServerLevel overworld = server.overworld();
        
        if (explorationWorld != null) {
            AABB zone = new AABB(active.spawnPos).inflate(2000, 320, 2000);
            List<ServerPlayer> players = explorationWorld.getEntitiesOfClass(ServerPlayer.class, zone);
            
            BlockPos returnPos = active.origin;
            
            for (ServerPlayer player : players) {
                player.teleportTo(overworld, returnPos.getX() + 0.5, returnPos.getY() + 1, returnPos.getZ() + 0.5, 0, 0);
                player.sendSystemMessage(Component.literal("Exploration terminée !").withStyle(net.minecraft.ChatFormatting.GOLD));
                player.getPersistentData().remove("ExplorationOrigin");
            }
            
            // Collecter d'abord les entités à supprimer pour éviter ConcurrentModificationException
            List<Entity> toRemove = new ArrayList<>();
            explorationWorld.getEntities().getAll().forEach(entity -> {
                if (entity != null && !(entity instanceof ServerPlayer) && zone.contains(entity.position())) {
                    toRemove.add(entity);
                }
            });
            
            // Supprimer ensuite
            for (Entity entity : toRemove) {
                entity.discard();
            }
        }
    }

    private void spawnParticles(ServerLevel level, BlockPos center) {
        int r = 3;
        double y = center.getY() + 0.1;
        for (int x = -r; x <= r; x++) {
            level.sendParticles(ParticleTypes.FLAME, center.getX() + x + 0.5, y, center.getZ() - r + 0.5, 1, 0, 0, 0, 0);
            level.sendParticles(ParticleTypes.FLAME, center.getX() + x + 0.5, y, center.getZ() + r + 0.5, 1, 0, 0, 0, 0);
        }
        for (int z = -r; z <= r; z++) {
            level.sendParticles(ParticleTypes.FLAME, center.getX() - r + 0.5, y, center.getZ() + z + 0.5, 1, 0, 0, 0, 0);
            level.sendParticles(ParticleTypes.FLAME, center.getX() + r + 0.5, y, center.getZ() + z + 0.5, 1, 0, 0, 0, 0);
        }
    }

    private BlockPos teleportPlayers(MinecraftServer server, PendingExploration explo) {
        ServerLevel explorationWorld = server.getLevel(ModDimensions.EXPLORATION_LEVEL_KEY);
        
        if (explorationWorld != null) {
            ServerLevel originLevel = server.overworld();
            AABB zone = new AABB(explo.origin).inflate(3, 2, 3);
            List<ServerPlayer> players = originLevel.getEntitiesOfClass(ServerPlayer.class, zone);
            
            int targetX = explo.targetX;
            int targetZ = 0;
            
            // On force le chargement du chunk central pour avoir une hauteur correcte
            explorationWorld.getChunk(targetX >> 4, targetZ >> 4);
            
            BlockPos safePos = findSafeSpot(explorationWorld, targetX, targetZ);
            targetX = safePos.getX();
            targetZ = safePos.getZ();
            int y = safePos.getY();

            BlockPos.MutableBlockPos mutablePos = new BlockPos.MutableBlockPos();

            // Construire la plateforme
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    BlockPos p = mutablePos.set(targetX + dx, y - 1, targetZ + dz);
                    explorationWorld.setBlock(p, Blocks.OAK_PLANKS.defaultBlockState(), 2);
                }
            }
            
            // Dégager l'espace au dessus
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    for (int dy = 0; dy <= 2; dy++) {
                        BlockPos p = mutablePos.set(targetX + dx, y + dy, targetZ + dz);
                        if (explorationWorld.getBlockState(p).isSolid()) {
                            explorationWorld.setBlock(p, Blocks.AIR.defaultBlockState(), 2);
                        }
                    }
                }
            }

            BlockPos finalSpawn = new BlockPos(targetX, y, targetZ);

            for (ServerPlayer player : players) {
                player.teleportTo(explorationWorld, targetX + 0.5, y, targetZ + 0.5, 0, 0);
                player.sendSystemMessage(Component.literal("Bienvenue dans l'Exploration !"));
                player.getPersistentData().putLong("ExplorationOrigin", explo.origin.asLong());
            }
            
            return finalSpawn;
        } else {
            System.err.println("Erreur: Le monde d'exploration n'est pas chargé !");
            return null;
        }
    }
    
    private BlockPos findSafeSpot(ServerLevel level, int startX, int startZ) {
        for (int r = 0; r < 500; r += 32) {
            for (int i = 0; i < 16; i++) {
                double angle = i * (Math.PI / 8);
                int x = startX + (int)(Math.cos(angle) * r);
                int z = startZ + (int)(Math.sin(angle) * r);
                
                level.getChunk(x >> 4, z >> 4);
                
                net.minecraft.core.Holder<net.minecraft.world.level.biome.Biome> biome = level.getBiome(new BlockPos(x, 64, z));
                if (biome.is(net.minecraft.tags.BiomeTags.IS_OCEAN) || biome.is(net.minecraft.tags.BiomeTags.IS_RIVER) || biome.is(net.minecraft.tags.BiomeTags.IS_DEEP_OCEAN)) {
                    continue;
                }
                
                int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
                if (y < 63) continue;
                
                BlockPos pos = new BlockPos(x, y, z);
                
                if (isAreaClear(level, pos)) {
                    return pos;
                }
            }
        }
        
        level.getChunk(startX >> 4, startZ >> 4);
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, startX, startZ);
        if (y < 63) y = 63;
        return new BlockPos(startX, y + 1, startZ);
    }
    
    private boolean isAreaClear(ServerLevel level, BlockPos center) {
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                for (int dy = 0; dy <= 3; dy++) {
                    m.set(center.getX() + dx, center.getY() + dy, center.getZ() + dz);
                    if (level.getBlockState(m).isSolid()) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    // --- Sauvegarde ---

    public enum ExplorationState {
        COUNTDOWN,
        TELEPORTING
    }

    public static class PendingExploration {
        public BlockPos origin;
        public int ticksRemaining;
        public int durationMinutes;
        public ExplorationState state = ExplorationState.COUNTDOWN;
        public int targetX;

        public PendingExploration(BlockPos origin, int durationMinutes, int targetX) {
            this.origin = origin;
            this.ticksRemaining = 30 * 20;
            this.durationMinutes = durationMinutes;
            this.targetX = targetX;
        }
        
        public PendingExploration(CompoundTag tag) {
            this.origin = BlockPos.of(tag.getLong("Origin"));
            this.ticksRemaining = tag.getInt("Ticks");
            this.durationMinutes = tag.getInt("Duration");
            this.targetX = tag.getInt("TargetX");
            if (tag.contains("State")) {
                this.state = ExplorationState.valueOf(tag.getString("State"));
            }
        }

        public CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putLong("Origin", origin.asLong());
            tag.putInt("Ticks", ticksRemaining);
            tag.putInt("Duration", durationMinutes);
            tag.putInt("TargetX", targetX);
            tag.putString("State", state.name());
            return tag;
        }
    }
    
    public static class ActiveExploration {
        public BlockPos spawnPos;
        public int ticksRemaining;
        public BlockPos origin;
        
        public ActiveExploration(BlockPos spawnPos, int ticksRemaining, BlockPos origin) {
            this.spawnPos = spawnPos;
            this.ticksRemaining = ticksRemaining;
            this.origin = origin;
        }
        
        public ActiveExploration(CompoundTag tag) {
            this.spawnPos = BlockPos.of(tag.getLong("SpawnPos"));
            this.ticksRemaining = tag.getInt("Ticks");
            this.origin = BlockPos.of(tag.getLong("Origin"));
        }
        
        public CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putLong("SpawnPos", spawnPos.asLong());
            tag.putInt("Ticks", ticksRemaining);
            tag.putLong("Origin", origin.asLong());
            return tag;
        }
    }

    public ExplorationManager() {}
    
    public ExplorationManager(int nextX) {
        this.nextExplorationX = nextX;
    }

    public static ExplorationManager load(CompoundTag tag) {
        ExplorationManager manager = new ExplorationManager(tag.getInt("NextX"));
        
        if (tag.contains("Pending", Tag.TAG_LIST)) {
            ListTag list = tag.getList("Pending", Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) {
                manager.pendingExplorations.add(new PendingExploration(list.getCompound(i)));
            }
        }
        
        if (tag.contains("Active", Tag.TAG_LIST)) {
            ListTag list = tag.getList("Active", Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) {
                manager.activeExplorations.add(new ActiveExploration(list.getCompound(i)));
            }
        }
        
        return manager;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        tag.putInt("NextX", nextExplorationX);
        
        ListTag pendingList = new ListTag();
        for (PendingExploration explo : pendingExplorations) {
            pendingList.add(explo.save());
        }
        tag.put("Pending", pendingList);
        
        ListTag activeList = new ListTag();
        for (ActiveExploration active : activeExplorations) {
            activeList.add(active.save());
        }
        tag.put("Active", activeList);

        return tag;
    }
}
