package fr.renblood.medievalstructures.event;

import fr.renblood.medievalstructures.MedievalStructures;
import fr.renblood.medievalstructures.manager.ExplorationManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = MedievalStructures.MODID)
public class ServerTickHandler {

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END && event.getServer().overworld() != null) {
            ExplorationManager.get(event.getServer().overworld()).tick(event.getServer());
        }
    }
}
