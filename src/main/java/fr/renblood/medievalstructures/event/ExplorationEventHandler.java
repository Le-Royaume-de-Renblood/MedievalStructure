package fr.renblood.medievalstructures.event;

import fr.renblood.medievalstructures.MedievalStructures;
import fr.renblood.medievalstructures.init.ModDimensions;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = MedievalStructures.MODID)
public class ExplorationEventHandler {

    @SubscribeEvent
    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        // Vide pour l'instant, géré dans onPlayerRespawnMessage
    }
    
    @SubscribeEvent
    public static void onPlayerDeath(net.minecraftforge.event.entity.living.LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            if (player.level().dimension().equals(ModDimensions.EXPLORATION_LEVEL_KEY)) {
                // Le joueur est mort dans l'exploration
                player.getPersistentData().putBoolean("DiedInExploration", true);
                // On garde aussi l'origine si elle est présente
                if (player.getPersistentData().contains("ExplorationOrigin")) {
                    // Déjà stocké, rien à faire de plus, mais on s'assure qu'il est là
                }
            }
        }
    }
    
    @SubscribeEvent
    public static void onPlayerClone(PlayerEvent.Clone event) {
        // Copier les données persistantes de l'ancien joueur vers le nouveau
        if (event.isWasDeath()) {
            if (event.getOriginal().getPersistentData().getBoolean("DiedInExploration")) {
                event.getEntity().getPersistentData().putBoolean("DiedInExploration", true);
                
                if (event.getOriginal().getPersistentData().contains("ExplorationOrigin")) {
                    event.getEntity().getPersistentData().putLong("ExplorationOrigin", event.getOriginal().getPersistentData().getLong("ExplorationOrigin"));
                }
            }
        }
    }
    
    @SubscribeEvent
    public static void onPlayerRespawnMessage(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            if (player.getPersistentData().getBoolean("DiedInExploration")) {
                player.getPersistentData().remove("DiedInExploration");
                player.sendSystemMessage(Component.literal("La mort n'est qu'un début... mais pas pour vous. Vous êtes juste mauvais."));
                
                // Récupérer la position d'origine stockée
                BlockPos origin = null;
                if (player.getPersistentData().contains("ExplorationOrigin")) {
                    origin = BlockPos.of(player.getPersistentData().getLong("ExplorationOrigin"));
                    player.getPersistentData().remove("ExplorationOrigin");
                }

                ServerLevel overworld = player.server.overworld();
                
                // Si on a une origine valide, on TP à côté
                if (origin != null) {
                    player.teleportTo(overworld, origin.getX() + 0.5, origin.getY() + 1, origin.getZ() + 0.5, 0, 0);
                } else {
                    // Fallback : Spawn mondial si pas d'origine trouvée
                    BlockPos spawn = overworld.getSharedSpawnPos();
                    player.teleportTo(overworld, spawn.getX(), spawn.getY(), spawn.getZ(), 0, 0);
                }
            }
        }
    }
}
