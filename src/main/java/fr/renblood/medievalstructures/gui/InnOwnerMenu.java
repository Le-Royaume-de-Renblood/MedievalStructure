package fr.renblood.medievalstructures.gui;

import fr.renblood.medievalstructures.init.ModBlocks;
import fr.renblood.medievalstructures.init.ModMenuTypes;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;

public class InnOwnerMenu extends AbstractContainerMenu {
    private final ContainerLevelAccess access;

    // Constructeur client
    public InnOwnerMenu(int id, Inventory inv, FriendlyByteBuf extraData) {
        this(id, inv, ContainerLevelAccess.NULL);
    }

    // Constructeur serveur
    public InnOwnerMenu(int id, Inventory inv, ContainerLevelAccess access) {
        super(ModMenuTypes.INN_OWNER_MENU.get(), id);
        this.access = access;
        // Pas d'inventaire joueur
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return stillValid(access, player, ModBlocks.INN_STRUCTURE_BLOCK.get());
    }
}
