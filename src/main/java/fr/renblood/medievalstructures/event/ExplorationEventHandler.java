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
        if (event.getEntity() instanceof ServerPlayer player && !player.level().isClientSide) {
            // On vérifie si le joueur vient de mourir (pas un retour de l'End)
            if (!event.isEndConquered()) {
                // Malheureusement, l'event ne nous dit pas facilement d'où on vient.
                // Mais on peut vérifier si le joueur est dans la dimension d'exploration AVANT le respawn via un autre event (LivingDeathEvent)
                // Ou alors on suppose que si le joueur respawn, on lui envoie le message s'il était dans l'exploration.
                
                // Pour l'instant, on va juste envoyer le message si le joueur respawn dans l'Overworld
                // et qu'il n'a pas de lit (ce qui arrive souvent quand on meurt dans une dimension sans lit).
                
                // Une meilleure approche : Écouter LivingDeathEvent pour savoir où il est mort.
            }
        }
    }
    
    @SubscribeEvent
    public static void onPlayerDeath(net.minecraftforge.event.entity.living.LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            if (player.level().dimension().equals(ModDimensions.EXPLORATION_LEVEL_KEY)) {
                // Le joueur est mort dans l'exploration
                // On peut stocker un tag sur le joueur pour lui envoyer le message au respawn
                player.getPersistentData().putBoolean("DiedInExploration", true);
            }
        }
    }
    
    @SubscribeEvent
    public static void onPlayerRespawnMessage(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            if (player.getPersistentData().getBoolean("DiedInExploration")) {
                player.getPersistentData().remove("DiedInExploration");
                player.sendSystemMessage(Component.literal("La mort n'est qu'un début... mais pas pour vous. Vous êtes juste mauvais."));
                
                // Si on veut forcer le retour à l'Overworld (au cas où il aurait un lit dans l'exploration)
                if (player.level().dimension().equals(ModDimensions.EXPLORATION_LEVEL_KEY)) {
                    ServerLevel overworld = player.server.overworld();
                    BlockPos spawn = overworld.getSharedSpawnPos();
                    player.teleportTo(overworld, spawn.getX(), spawn.getY(), spawn.getZ(), 0, 0);
                }
            }
        }
    }
}
