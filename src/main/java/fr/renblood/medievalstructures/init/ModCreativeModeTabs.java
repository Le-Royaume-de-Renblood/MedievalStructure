package fr.renblood.medievalstructures.init;

import fr.renblood.medievalstructures.MedievalStructures;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

public class ModCreativeModeTabs {
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MedievalStructures.MODID);

    public static final RegistryObject<CreativeModeTab> MEDIEVAL_STRUCTURES_TAB = CREATIVE_MODE_TABS.register("medieval_structures_tab",
            () -> CreativeModeTab.builder().icon(() -> new ItemStack(ModBlocks.INN_STRUCTURE_BLOCK.get()))
                    .title(Component.translatable("creativetab.medieval_structures_tab"))
                    .displayItems((pParameters, pOutput) -> {
                        pOutput.accept(ModBlocks.INN_STRUCTURE_BLOCK.get());
                        pOutput.accept(ModBlocks.EXPLORATION_HALL_BLOCK.get());
                        pOutput.accept(ModItems.INNKEEPER_WAND.get());
                    })
                    .build());

    public static void register(IEventBus eventBus) {
        CREATIVE_MODE_TABS.register(eventBus);
    }
}
