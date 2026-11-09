package fr.renblood.medievalstructures.event;

import fr.renblood.medievalstructures.MedievalStructures;
import fr.renblood.medievalstructures.init.ModDimensions;
import fr.renblood.medievalstructures.manager.ExplorationManager;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = MedievalStructures.MODID)
public class ExplorationEventHandler {
    public static final String EXPLORATION_ID = "RenbloodExpeditionId";
    public static final String DIED_IN_EXPLORATION = "DiedInExploration";
    public static final String EXPLORATION_ORIGIN = "ExplorationOrigin";
    public static final String EXPLORATION_BED_RESPAWN = "ExplorationBedRespawn";

    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            ExplorationManager.get(player.server.overworld()).restorePlayer(player);
        }
    }

    @SubscribeEvent
    public static void onEntityJoin(EntityJoinLevelEvent event) {
        if (event.getLevel() instanceof ServerLevel level && level.dimension().equals(ModDimensions.EXPLORATION_LEVEL_KEY)) {
            ExplorationManager.get(level).tagEntity(event.getEntity());
        }
    }

    @SubscribeEvent
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (event.getEntity() instanceof ServerPlayer player
                && player.level().dimension().equals(ModDimensions.EXPLORATION_LEVEL_KEY)) {
            ExplorationManager.get(player.server.overworld()).claimEntity(player, event.getTarget());
        }
    }

    @SubscribeEvent
    public static void onPlayerDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!player.level().dimension().equals(ModDimensions.EXPLORATION_LEVEL_KEY)) return;

        if (player.getPersistentData().getBoolean(EXPLORATION_BED_RESPAWN)) {
            return;
        }

        player.getPersistentData().putBoolean(DIED_IN_EXPLORATION, true);
    }

    @SubscribeEvent
    public static void onPlayerClone(PlayerEvent.Clone event) {
        if (!event.isWasDeath()) return;
        var previous = event.getOriginal().getPersistentData();
        var current = event.getEntity().getPersistentData();
        if (previous.hasUUID(EXPLORATION_ID)) current.putUUID(EXPLORATION_ID, previous.getUUID(EXPLORATION_ID));
        if (previous.contains(EXPLORATION_ORIGIN)) current.putLong(EXPLORATION_ORIGIN, previous.getLong(EXPLORATION_ORIGIN));

        if (event.getOriginal().getPersistentData().getBoolean(EXPLORATION_BED_RESPAWN)) {
            event.getEntity().getPersistentData().putBoolean(EXPLORATION_BED_RESPAWN, true);
            event.getEntity().getPersistentData().remove(DIED_IN_EXPLORATION);
        } else if (event.getOriginal().getPersistentData().getBoolean(DIED_IN_EXPLORATION)) {
            event.getEntity().getPersistentData().putBoolean(DIED_IN_EXPLORATION, true);
            if (event.getOriginal().getPersistentData().contains(EXPLORATION_ORIGIN)) {
                event.getEntity().getPersistentData().putLong(EXPLORATION_ORIGIN, event.getOriginal().getPersistentData().getLong(EXPLORATION_ORIGIN));
            }
        }
    }

    @SubscribeEvent
    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        if (player.getPersistentData().getBoolean(EXPLORATION_BED_RESPAWN)) {
            if (!player.level().dimension().equals(ModDimensions.EXPLORATION_LEVEL_KEY)) {
                player.getPersistentData().remove(EXPLORATION_ID);
                player.getPersistentData().remove(EXPLORATION_ORIGIN);
                player.getPersistentData().remove(EXPLORATION_BED_RESPAWN);
            } else {
                player.server.execute(() -> ExplorationManager.get(player.server.overworld()).restorePlayer(player));
            }
            return;
        }

        if (!player.getPersistentData().getBoolean(DIED_IN_EXPLORATION)) {
            return;
        }

        player.getPersistentData().remove(DIED_IN_EXPLORATION);
        player.getPersistentData().remove(EXPLORATION_ID);
        player.sendSystemMessage(Component.literal("La mort vous ramene au hall d'exploration."));

        BlockPos origin = null;
        if (player.getPersistentData().contains(EXPLORATION_ORIGIN)) {
            origin = BlockPos.of(player.getPersistentData().getLong(EXPLORATION_ORIGIN));
            player.getPersistentData().remove(EXPLORATION_ORIGIN);
        }

        ServerLevel overworld = player.server.overworld();
        if (origin != null) {
            player.teleportTo(overworld, origin.getX() + 0.5, origin.getY() + 1, origin.getZ() + 0.5, 0, 0);
        } else {
            BlockPos spawn = overworld.getSharedSpawnPos();
            player.teleportTo(overworld, spawn.getX(), spawn.getY(), spawn.getZ(), 0, 0);
        }
    }
}
