package fr.renblood.medievalstructures.manager;

import fr.renblood.medievalstructures.init.ModDimensions;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class ExplorationManager extends SavedData {
    private static final String DATA_NAME = "medieval_structures_explorations";
    private final List<PendingExploration> pendingExplorations = new ArrayList<>();
    private int nextExplorationX = 0; // Coordonnée X pour la prochaine exploration

    public static ExplorationManager get(ServerLevel level) {
        return level.getServer().overworld().getDataStorage().computeIfAbsent(ExplorationManager::load, ExplorationManager::new, DATA_NAME);
    }

    public void startCountdown(ServerLevel level, BlockPos origin, int durationMinutes) {
        // On attribue une position unique (espacée de 5000 blocs)
        int x = nextExplorationX;
        nextExplorationX += 5000;
        
        pendingExplorations.add(new PendingExploration(origin, durationMinutes, x));
        setDirty();
    }

    public void tick(MinecraftServer server) {
        Iterator<PendingExploration> it = pendingExplorations.iterator();
        while (it.hasNext()) {
            PendingExploration explo = it.next();
            
            if (explo.state == ExplorationState.COUNTDOWN) {
                explo.ticksRemaining--;

                // Effets visuels
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
                    // On passe directement à la téléportation car le monde existe déjà
                    explo.state = ExplorationState.TELEPORTING;
                    explo.ticksRemaining = 5; // Petit délai
                    setDirty();
                }
            } else if (explo.state == ExplorationState.TELEPORTING) {
                if (explo.ticksRemaining > 0) {
                    explo.ticksRemaining--;
                } else {
                    teleportPlayers(server, explo);
                    it.remove();
                    setDirty();
                }
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

    private void teleportPlayers(MinecraftServer server, PendingExploration explo) {
        ServerLevel explorationWorld = server.getLevel(ModDimensions.EXPLORATION_LEVEL_KEY);
        
        if (explorationWorld != null) {
            ServerLevel originLevel = server.overworld();
            AABB zone = new AABB(explo.origin).inflate(3, 2, 3);
            List<ServerPlayer> players = originLevel.getEntitiesOfClass(ServerPlayer.class, zone);
            
            // Position cible : X attribué, Z=0
            int targetX = explo.targetX;
            int targetZ = 0;
            
            // Trouver le sol
            int y = explorationWorld.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, targetX, targetZ);
            if (y < 63) y = 63; // On s'assure de spawner au dessus de la couche 62

            BlockPos.MutableBlockPos mutablePos = new BlockPos.MutableBlockPos();

            // Nettoyer la zone 10x10
            for (int dx = -5; dx <= 5; dx++) {
                for (int dz = -5; dz <= 5; dz++) {
                    for (int dy = 0; dy < 10; dy++) {
                        explorationWorld.setBlock(mutablePos.set(targetX + dx, y + dy, targetZ + dz), Blocks.AIR.defaultBlockState(), 2);
                    }
                }
            }
            
            // Créer la plateforme 3x3
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    explorationWorld.setBlock(mutablePos.set(targetX + dx, y, targetZ + dz), Blocks.OAK_PLANKS.defaultBlockState(), 2);
                }
            }

            for (ServerPlayer player : players) {
                player.teleportTo(explorationWorld, targetX + 0.5, y + 1, targetZ + 0.5, 0, 0);
                player.sendSystemMessage(Component.literal("Bienvenue dans l'Exploration !"));
            }
        } else {
            System.err.println("Erreur: Le monde d'exploration n'est pas chargé !");
        }
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

    public ExplorationManager() {}
    
    public ExplorationManager(int nextX) {
        this.nextExplorationX = nextX;
    }

    public static ExplorationManager load(CompoundTag tag) {
        ExplorationManager manager = new ExplorationManager(tag.getInt("NextX"));
        // TODO: Charger la liste des pending si nécessaire
        return manager;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        tag.putInt("NextX", nextExplorationX);
        // TODO: Sauvegarder la liste
        return tag;
    }
}
