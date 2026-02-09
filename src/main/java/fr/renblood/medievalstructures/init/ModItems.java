package fr.renblood.medievalstructures.init;

import fr.renblood.medievalstructures.MedievalStructures;
import fr.renblood.medievalstructures.item.InnkeeperWandItem;
import net.minecraft.world.item.Item;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModItems {
    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, MedievalStructures.MODID);

    public static final RegistryObject<Item> INNKEEPER_WAND = ITEMS.register("innkeeper_wand",
            () -> new InnkeeperWandItem(new Item.Properties().stacksTo(1)));

    public static void register(IEventBus eventBus) {
        ITEMS.register(eventBus);
    }
}
