package fr.renblood.medievalstructures.block.entity;

import fr.renblood.medievalstructures.gui.ExplorationHallMenu;
import fr.renblood.medievalstructures.init.ModBlockEntities;
import fr.renblood.medievalstructures.network.PacketExplorationAction;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
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
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class ExplorationHallBlockEntity extends BlockEntity implements MenuProvider {

    private int savedDuration = 0;
    private List<PacketExplorationAction.PlayerConfig> savedConfigs = new ArrayList<>();
    private boolean hasExplorationReady = false;

    public ExplorationHallBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.EXPLORATION_HALL_BE.get(), pos, state);
    }

    public void saveExploration(int duration, List<PacketExplorationAction.PlayerConfig> configs) {
        this.savedDuration = duration;
        this.savedConfigs = new ArrayList<>(configs);
        this.hasExplorationReady = true;
        setChanged();
        if (level != null) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }
    
    public void clearExploration() {
        this.hasExplorationReady = false;
        this.savedConfigs.clear();
        this.savedDuration = 0;
        setChanged();
        if (level != null) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    public boolean hasExplorationReady() {
        return hasExplorationReady;
    }

    public int getSavedDuration() {
        return savedDuration;
    }

    public List<PacketExplorationAction.PlayerConfig> getSavedConfigs() {
        return savedConfigs;
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putBoolean("HasExploration", hasExplorationReady);
        if (hasExplorationReady) {
            tag.putInt("Duration", savedDuration);
            ListTag list = new ListTag();
            for (PacketExplorationAction.PlayerConfig config : savedConfigs) {
                CompoundTag c = new CompoundTag();
                c.putInt("Chests", config.chestCount);
                c.putInt("Animals", config.animalCount);
                list.add(c);
            }
            tag.put("Configs", list);
        }
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        hasExplorationReady = tag.getBoolean("HasExploration");
        if (hasExplorationReady) {
            savedDuration = tag.getInt("Duration");
            savedConfigs.clear();
            ListTag list = tag.getList("Configs", Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) {
                CompoundTag c = list.getCompound(i);
                savedConfigs.add(new PacketExplorationAction.PlayerConfig(c.getInt("Chests"), c.getInt("Animals")));
            }
        }
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
        return Component.translatable("gui.medieval_structures.exploration_hall");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        // On passe la position au menu pour que le client puisse la récupérer
        return new ExplorationHallMenu(id, inventory, ContainerLevelAccess.create(level, worldPosition), worldPosition);
    }
}
