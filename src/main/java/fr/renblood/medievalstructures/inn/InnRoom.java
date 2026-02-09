package fr.renblood.medievalstructures.inn;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.world.phys.AABB;

import java.util.UUID;

public class InnRoom {
    private int number;
    private BlockPos headPos;
    private BlockPos footPos;
    private BlockPos p1; // Coin 1 de la zone de la chambre
    private BlockPos p2; // Coin 2 de la zone de la chambre
    private int size;
    private double price;
    private double balance; // Solde de la chambre (Positif = Avance, Négatif = Dette)
    private boolean isBusy;
    private boolean isDirty;
    private String occupantName = "";
    private UUID occupantUUID; // Null si PNJ

    public InnRoom(int number, BlockPos headPos, BlockPos footPos, BlockPos p1, BlockPos p2, int size, double price) {
        this.number = number;
        this.headPos = headPos;
        this.footPos = footPos;
        this.p1 = p1;
        this.p2 = p2;
        this.size = size;
        this.price = price;
        this.balance = 0.0;
        this.isBusy = false;
        this.isDirty = false;
    }

    public int getNumber() {
        return number;
    }

    public BlockPos getHeadPos() {
        return headPos;
    }

    public BlockPos getFootPos() {
        return footPos;
    }

    public BlockPos getBedPos() {
        return headPos;
    }

    public BlockPos getP1() {
        return p1;
    }

    public BlockPos getP2() {
        return p2;
    }

    public int getSize() {
        return size;
    }

    public boolean isInside(BlockPos pos) {
        if (p1 == null || p2 == null) return false;
        int minX = Math.min(p1.getX(), p2.getX());
        int minY = Math.min(p1.getY(), p2.getY());
        int minZ = Math.min(p1.getZ(), p2.getZ());
        int maxX = Math.max(p1.getX(), p2.getX());
        int maxY = Math.max(p1.getY(), p2.getY());
        int maxZ = Math.max(p1.getZ(), p2.getZ());
        
        return pos.getX() >= minX && pos.getX() <= maxX &&
               pos.getY() >= minY && pos.getY() <= maxY &&
               pos.getZ() >= minZ && pos.getZ() <= maxZ;
    }

    public double getPrice() {
        return price;
    }

    public void setPrice(double price) {
        this.price = price;
    }

    public double getBalance() {
        return balance;
    }

    public void setBalance(double balance) {
        this.balance = balance;
    }
    
    public void addToBalance(double amount) {
        this.balance += amount;
    }

    public boolean isBusy() {
        return isBusy;
    }

    public void setBusy(boolean busy) {
        isBusy = busy;
        if (!busy) {
            occupantName = "";
            occupantUUID = null;
            balance = 0.0; // Reset balance quand la chambre est libérée
        }
    }

    public boolean isDirty() {
        return isDirty;
    }

    public void setDirty(boolean dirty) {
        isDirty = dirty;
    }

    public String getOccupantName() {
        return occupantName;
    }

    public void setOccupantName(String occupantName) {
        this.occupantName = occupantName;
    }

    public UUID getOccupantUUID() {
        return occupantUUID;
    }

    public void setOccupantUUID(UUID occupantUUID) {
        this.occupantUUID = occupantUUID;
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("Number", number);
        tag.put("HeadPos", NbtUtils.writeBlockPos(headPos));
        if (footPos != null) tag.put("FootPos", NbtUtils.writeBlockPos(footPos));
        if (p1 != null) tag.put("P1", NbtUtils.writeBlockPos(p1));
        if (p2 != null) tag.put("P2", NbtUtils.writeBlockPos(p2));
        tag.putInt("Size", size);
        tag.putDouble("Price", price);
        tag.putDouble("Balance", balance);
        tag.putBoolean("IsBusy", isBusy);
        tag.putBoolean("IsDirty", isDirty);
        tag.putString("Occupant", occupantName);
        if (occupantUUID != null) tag.putUUID("OccupantUUID", occupantUUID);
        return tag;
    }

    public static InnRoom load(CompoundTag tag) {
        int number = tag.getInt("Number");
        BlockPos headPos;
        if (tag.contains("HeadPos")) {
            headPos = NbtUtils.readBlockPos(tag.getCompound("HeadPos"));
        } else {
            headPos = NbtUtils.readBlockPos(tag.getCompound("BedPos"));
        }
        
        BlockPos footPos = null;
        if (tag.contains("FootPos")) {
            footPos = NbtUtils.readBlockPos(tag.getCompound("FootPos"));
        } else {
            footPos = headPos; 
        }

        BlockPos p1 = null;
        if (tag.contains("P1")) p1 = NbtUtils.readBlockPos(tag.getCompound("P1"));
        BlockPos p2 = null;
        if (tag.contains("P2")) p2 = NbtUtils.readBlockPos(tag.getCompound("P2"));

        int size = tag.getInt("Size");
        double price = tag.getDouble("Price");
        InnRoom room = new InnRoom(number, headPos, footPos, p1, p2, size, price);
        if (tag.contains("Balance")) {
            room.setBalance(tag.getDouble("Balance"));
        }
        room.setBusy(tag.getBoolean("IsBusy"));
        room.setDirty(tag.getBoolean("IsDirty"));
        if (tag.contains("Occupant")) {
            room.setOccupantName(tag.getString("Occupant"));
        }
        if (tag.hasUUID("OccupantUUID")) {
            room.setOccupantUUID(tag.getUUID("OccupantUUID"));
        }
        return room;
    }
}
