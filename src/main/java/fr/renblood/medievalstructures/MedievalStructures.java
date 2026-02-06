package fr.renblood.medievalstructures;

import com.mojang.logging.LogUtils;
import fr.renblood.medievalstructures.command.ModCommands;
import fr.renblood.medievalstructures.init.ModBlocks;
import fr.renblood.medievalstructures.init.ModCreativeModeTabs;
import fr.renblood.medievalstructures.init.ModItems;
import fr.renblood.medievalstructures.network.PacketHandler;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

@Mod(MedievalStructures.MODID)
public class MedievalStructures {
    public static final String MODID = "medieval_structures";
    public static final Logger LOGGER = LogUtils.getLogger();

    public MedievalStructures() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();

        ModItems.register(modEventBus);
        ModBlocks.register(modEventBus);
        ModCreativeModeTabs.register(modEventBus);

        modEventBus.addListener(this::commonSetup);

        MinecraftForge.EVENT_BUS.register(this);
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        PacketHandler.register();
    }

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        ModCommands.register(event.getDispatcher());
    }
}
