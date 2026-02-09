package fr.renblood.medievalstructures.init;

import fr.renblood.medievalstructures.MedievalStructures;
import fr.renblood.medievalstructures.client.renderer.InnStructureBlockRenderer;
import fr.renblood.medievalstructures.gui.InnCustomerScreen;
import fr.renblood.medievalstructures.gui.InnOwnerScreen;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderers;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

@Mod.EventBusSubscriber(modid = MedievalStructures.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class ClientSetup {
    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            MenuScreens.register(ModMenuTypes.INN_OWNER_MENU.get(), InnOwnerScreen::new);
            MenuScreens.register(ModMenuTypes.INN_CUSTOMER_MENU.get(), InnCustomerScreen::new);
            BlockEntityRenderers.register(ModBlockEntities.INN_STRUCTURE_BE.get(), InnStructureBlockRenderer::new);
        });
    }
}
