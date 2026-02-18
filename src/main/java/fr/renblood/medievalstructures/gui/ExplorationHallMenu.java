package fr.renblood.medievalstructures.gui;

import fr.renblood.medievalstructures.block.entity.ExplorationHallBlockEntity;
import fr.renblood.medievalstructures.init.ModBlocks;
import fr.renblood.medievalstructures.init.ModMenuTypes;
import fr.renblood.medievalstructures.manager.ExplorationManager;
import fr.renblood.medievalstructures.network.PacketHandler;
import fr.renblood.medievalstructures.network.PacketSyncExplorationState;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.network.PacketDistributor;

public class ExplorationHallMenu extends AbstractContainerMenu {
    private final ContainerLevelAccess access;
    private final BlockPos pos;
    private boolean isDebugMode = false;

    // Constructeur client (appelé par le Network)
    public ExplorationHallMenu(int id, Inventory inv, FriendlyByteBuf extraData) {
        this(id, inv, extraData.readBlockPos());
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
        
        // Synchroniser l'état (Debug + Exploration) dès l'ouverture
        if (inv.player instanceof ServerPlayer serverPlayer) {
            boolean hasExploration = false;
            BlockEntity be = inv.player.level().getBlockEntity(pos);
            if (be instanceof ExplorationHallBlockEntity hall) {
                hasExploration = hall.hasExplorationReady();
            }
            
            PacketHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> serverPlayer), 
                    new PacketSyncExplorationState(hasExploration, ExplorationManager.DEBUG_MODE));
        }
    }
    
    public void setDebugMode(boolean debugMode) {
        this.isDebugMode = debugMode;
    }
    
    public boolean isDebugMode() {
        return isDebugMode;
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
        return stillValid(access, player, ModBlocks.EXPLORATION_HALL_BLOCK.get());
    }
}
