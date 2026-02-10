package fr.renblood.medievalstructures.gui;

import fr.renblood.medievalstructures.init.ModBlocks;
import fr.renblood.medievalstructures.init.ModMenuTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;

public class ExplorationHallMenu extends AbstractContainerMenu {
    private final ContainerLevelAccess access;
    private final BlockPos pos;

    // Constructeur client (appelé par le Network)
    public ExplorationHallMenu(int id, Inventory inv, FriendlyByteBuf extraData) {
        this(id, inv, extraData.readBlockPos());
        System.out.println("ExplorationHallMenu: Client constructor called");
    }

    // Constructeur interne pour le client
    private ExplorationHallMenu(int id, Inventory inv, BlockPos pos) {
        super(ModMenuTypes.EXPLORATION_HALL_MENU.get(), id);
        this.pos = pos;
        this.access = ContainerLevelAccess.create(inv.player.level(), pos);
    }

    // Constructeur serveur (appelé par le Block)
    public ExplorationHallMenu(int id, Inventory inv, ContainerLevelAccess access, BlockPos pos) {
        super(ModMenuTypes.EXPLORATION_HALL_MENU.get(), id);
        this.access = access;
        this.pos = pos;
        System.out.println("ExplorationHallMenu: Server constructor called");
    }

    public BlockPos getPos() {
        return pos;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        // DEBUG: Toujours vrai pour tester si le menu s'ouvre
        return true; 
        // return stillValid(access, player, ModBlocks.EXPLORATION_HALL_BLOCK.get());
    }
}
