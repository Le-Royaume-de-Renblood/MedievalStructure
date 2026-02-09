package fr.renblood.medievalstructures.block.entity;

import fr.renblood.medievalstructures.gui.InnOwnerMenu;
import fr.renblood.medievalstructures.init.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

public class InnStructureBlockEntity extends BlockEntity implements MenuProvider {
    private String innName = "";
    private BlockPos p1;
    private BlockPos p2;

    public InnStructureBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.INN_STRUCTURE_BE.get(), pos, state);
    }

    public void setInnName(String name) {
        this.innName = name;
        setChanged();
    }

    public String getInnName() {
        return innName;
    }

    public void setVolume(BlockPos p1, BlockPos p2) {
        this.p1 = p1;
        this.p2 = p2;
        setChanged();
    }

    public BlockPos getP1() {
        return p1;
    }

    public BlockPos getP2() {
        return p2;
    }

    public boolean isInside(BlockPos pos) {
        if (p1 == null || p2 == null) return false;
        AABB box = new AABB(p1, p2).expandTowards(1, 1, 1);
        return box.contains(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putString("InnName", innName);
        if (p1 != null) tag.putLong("P1", p1.asLong());
        if (p2 != null) tag.putLong("P2", p2.asLong());
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        innName = tag.getString("InnName");
        if (tag.contains("P1")) p1 = BlockPos.of(tag.getLong("P1"));
        if (tag.contains("P2")) p2 = BlockPos.of(tag.getLong("P2"));
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag() {
        return saveWithoutMetadata();
    }

    @Override
    public Component getDisplayName() {
        return Component.literal("Auberge: " + innName);
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return new InnOwnerMenu(id, inventory, ContainerLevelAccess.create(level, worldPosition));
    }
}
