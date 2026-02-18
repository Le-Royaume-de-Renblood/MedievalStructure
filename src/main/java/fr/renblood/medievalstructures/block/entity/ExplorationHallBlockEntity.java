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
import java.util.UUID;

public class ExplorationHallBlockEntity extends BlockEntity implements MenuProvider {

    private int savedDuration = 0;
    private List<PacketExplorationAction.PlayerConfig> savedConfigs = new ArrayList<>();
    private boolean hasExplorationReady = false;
    
    // Pour l'affichage client
    private int ticksRemaining = 0;
    private boolean waitingForPlayers = false;

    public ExplorationHallBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.EXPLORATION_HALL_BE.get(), pos, state);
    }

    public void saveExploration(int duration, List<PacketExplorationAction.PlayerConfig> configs) {
        this.savedDuration = duration;
        this.savedConfigs = new ArrayList<>(configs);
        this.hasExplorationReady = true;
        setChanged();
        sync();
    }
    
    public void clearExploration() {
        this.hasExplorationReady = false;
        this.savedConfigs.clear();
        this.savedDuration = 0;
        setChanged();
        sync();
    }
    
    public void updatePlayerStatus(UUID playerUUID, PacketExplorationAction.InvitationStatus status) {
        boolean changed = false;
        for (PacketExplorationAction.PlayerConfig config : savedConfigs) {
            if (playerUUID.equals(config.playerUUID)) {
                config.status = status;
                changed = true;
                break;
            }
        }
        
        if (changed) {
            setChanged();
            sync();
        }
    }
    
    public void removePlayer(int index) {
        if (index >= 0 && index < savedConfigs.size()) {
            savedConfigs.remove(index);
            setChanged();
            sync();
        }
    }
    
    public void setCountdown(int ticks, boolean waiting) {
        this.ticksRemaining = ticks;
        this.waitingForPlayers = waiting;
        setChanged();
        sync();
    }
    
    public int getTicksRemaining() {
        return ticksRemaining;
    }
    
    public boolean isWaitingForPlayers() {
        return waitingForPlayers;
    }
    
    private void sync() {
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
        tag.putInt("TicksRemaining", ticksRemaining);
        tag.putBoolean("WaitingForPlayers", waitingForPlayers);
        
        if (hasExplorationReady) {
            tag.putInt("Duration", savedDuration);
            ListTag list = new ListTag();
            for (PacketExplorationAction.PlayerConfig config : savedConfigs) {
                CompoundTag c = new CompoundTag();
                c.putInt("Chests", config.chestCount);
                c.putInt("Animals", config.animalCount);
                c.putString("Name", config.playerName != null ? config.playerName : "");
                if (config.playerUUID != null) c.putUUID("UUID", config.playerUUID);
                c.putString("Status", config.status.name());
                list.add(c);
            }
            tag.put("Configs", list);
        }
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        hasExplorationReady = tag.getBoolean("HasExploration");
        ticksRemaining = tag.getInt("TicksRemaining");
        waitingForPlayers = tag.getBoolean("WaitingForPlayers");

        if (hasExplorationReady) {
            savedDuration = tag.getInt("Duration");
            savedConfigs.clear();
            ListTag list = tag.getList("Configs", Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) {
                CompoundTag c = list.getCompound(i);
                String name = c.getString("Name");
                UUID uuid = c.hasUUID("UUID") ? c.getUUID("UUID") : null;
                PacketExplorationAction.InvitationStatus status = PacketExplorationAction.InvitationStatus.PENDING;
                if (c.contains("Status")) {
                    status = PacketExplorationAction.InvitationStatus.valueOf(c.getString("Status"));
                }
                savedConfigs.add(new PacketExplorationAction.PlayerConfig(c.getInt("Chests"), c.getInt("Animals"), name, uuid, status));
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
        return new ExplorationHallMenu(id, inventory, ContainerLevelAccess.create(level, worldPosition), worldPosition);
    }
}
