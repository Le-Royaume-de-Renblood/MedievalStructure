package fr.renblood.medievalstructures.init;

import fr.renblood.medievalstructures.MedievalStructures;
import fr.renblood.medievalstructures.gui.ExplorationHallMenu;
import fr.renblood.medievalstructures.gui.InnCustomerMenu;
import fr.renblood.medievalstructures.gui.InnOwnerMenu;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.network.IContainerFactory;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModMenuTypes {
    public static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(ForgeRegistries.MENU_TYPES, MedievalStructures.MODID);

    public static final RegistryObject<MenuType<InnOwnerMenu>> INN_OWNER_MENU =
            registerMenuType("inn_owner_menu", InnOwnerMenu::new);

    public static final RegistryObject<MenuType<InnCustomerMenu>> INN_CUSTOMER_MENU =
            registerMenuType("inn_customer_menu", InnCustomerMenu::new);

    public static final RegistryObject<MenuType<ExplorationHallMenu>> EXPLORATION_HALL_MENU =
            registerMenuType("exploration_hall_menu", ExplorationHallMenu::new);

    private static <T extends AbstractContainerMenu> RegistryObject<MenuType<T>> registerMenuType(String name, IContainerFactory<T> factory) {
        return MENUS.register(name, () -> IForgeMenuType.create(factory));
    }

    public static void register(IEventBus eventBus) {
        MENUS.register(eventBus);
    }
}
