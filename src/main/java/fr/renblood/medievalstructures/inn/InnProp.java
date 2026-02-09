package fr.renblood.medievalstructures.inn;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;

public class InnProp {
    private int id;
    private BlockPos pos;
    private String type; // Type de prop (ex: "candle", "chest")
    private boolean isActive; // Si le prop nécessite une action (ex: bougie éteinte)

    public InnProp(int id, BlockPos pos, String type) {
        this.id = id;
        this.pos = pos;
        this.type = type;
        this.isActive = false;
    }

    public int getId() {
        return id;
    }

    public BlockPos getPos() {
        return pos;
    }

    public String getType() {
        return type;
    }

    public boolean isActive() {
        return isActive;
    }

    public void setActive(boolean active) {
        isActive = active;
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("Id", id);
        tag.put("Pos", NbtUtils.writeBlockPos(pos));
        tag.putString("Type", type);
        tag.putBoolean("IsActive", isActive);
        return tag;
    }

    public static InnProp load(CompoundTag tag) {
        int id = tag.getInt("Id");
        BlockPos pos = NbtUtils.readBlockPos(tag.getCompound("Pos"));
        String type = tag.getString("Type");
        InnProp prop = new InnProp(id, pos, type);
        prop.setActive(tag.getBoolean("IsActive"));
        return prop;
    }
}
