package fr.renblood.medievalstructures.inn;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class Inn {
    private String name;
    private UUID ownerUUID;
    private String ownerName = ""; // Cache du nom du propriétaire
    private List<UUID> employees;
    private Map<UUID, String> employeeNames = new HashMap<>(); // Cache des noms des employés
    private int maxRooms;
    private int propsPerRoom;
    private BlockPos location;
    private boolean isSpawned;
    private boolean isActive;
    private double balance; // Solde de l'auberge
    private List<InnRoom> rooms;
    private List<InnProp> props;

    public Inn(String name, UUID ownerUUID, int maxRooms, int propsPerRoom) {
        this.name = name;
        this.ownerUUID = ownerUUID;
        this.employees = new ArrayList<>();
        this.maxRooms = maxRooms;
        this.propsPerRoom = propsPerRoom;
        this.rooms = new ArrayList<>();
        this.props = new ArrayList<>();
        this.isSpawned = false;
        this.isActive = false;
        this.balance = 0;
    }

    public String getName() {
        return name;
    }

    public UUID getOwnerUUID() {
        return ownerUUID;
    }

    public void setOwnerUUID(UUID ownerUUID) {
        this.ownerUUID = ownerUUID;
    }
    
    public String getOwnerName() {
        return ownerName;
    }

    public void setOwnerName(String ownerName) {
        this.ownerName = ownerName;
    }

    public List<UUID> getEmployees() {
        return employees;
    }
    
    public Map<UUID, String> getEmployeeNames() {
        return employeeNames;
    }

    public void addEmployee(UUID employeeUUID, String name) {
        if (!employees.contains(employeeUUID)) {
            employees.add(employeeUUID);
            employeeNames.put(employeeUUID, name);
        }
    }

    public void removeEmployee(UUID employeeUUID) {
        employees.remove(employeeUUID);
        employeeNames.remove(employeeUUID);
    }

    public boolean isEmployee(UUID uuid) {
        return employees.contains(uuid);
    }

    public void setLocation(BlockPos location) {
        this.location = location;
        this.isSpawned = true;
    }

    public BlockPos getLocation() {
        return location;
    }

    public boolean isSpawned() {
        return isSpawned;
    }

    public void setActive(boolean active) {
        this.isActive = active;
    }

    public boolean isActive() {
        return isActive;
    }

    public double getBalance() {
        return balance;
    }

    public void addBalance(double amount) {
        this.balance += amount;
    }

    public void setBalance(double balance) {
        this.balance = balance;
    }

    public void addRoom(InnRoom room) {
        rooms.add(room);
    }

    public void removeRoom(int number) {
        rooms.removeIf(r -> r.getNumber() == number);
    }

    public InnRoom getRoom(int number) {
        return rooms.stream().filter(r -> r.getNumber() == number).findFirst().orElse(null);
    }

    public List<InnRoom> getRooms() {
        return rooms;
    }

    public void addProp(InnProp prop) {
        props.add(prop);
    }

    public void removeProp(int id) {
        props.removeIf(p -> p.getId() == id);
    }

    public List<InnProp> getProps() {
        return props;
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("Name", name);
        if (ownerUUID != null) tag.putUUID("Owner", ownerUUID);
        tag.putString("OwnerName", ownerName);
        
        ListTag employeesTag = new ListTag();
        for (UUID uuid : employees) {
            CompoundTag empTag = new CompoundTag();
            empTag.putUUID("UUID", uuid);
            String empName = employeeNames.get(uuid);
            if (empName != null) empTag.putString("Name", empName);
            employeesTag.add(empTag);
        }
        tag.put("Employees", employeesTag);

        tag.putInt("MaxRooms", maxRooms);
        tag.putInt("PropsPerRoom", propsPerRoom);
        if (location != null) {
            tag.put("Location", NbtUtils.writeBlockPos(location));
        }
        tag.putBoolean("IsSpawned", isSpawned);
        tag.putBoolean("IsActive", isActive);
        tag.putDouble("Balance", balance);

        ListTag roomsTag = new ListTag();
        for (InnRoom room : rooms) {
            roomsTag.add(room.save());
        }
        tag.put("Rooms", roomsTag);

        ListTag propsTag = new ListTag();
        for (InnProp prop : props) {
            propsTag.add(prop.save());
        }
        tag.put("Props", propsTag);

        return tag;
    }

    public static Inn load(CompoundTag tag) {
        String name = tag.getString("Name");
        UUID owner = tag.hasUUID("Owner") ? tag.getUUID("Owner") : null;
        int maxRooms = tag.getInt("MaxRooms");
        int propsPerRoom = tag.getInt("PropsPerRoom");
        Inn inn = new Inn(name, owner, maxRooms, propsPerRoom);
        
        if (tag.contains("OwnerName")) {
            inn.setOwnerName(tag.getString("OwnerName"));
        }

        if (tag.contains("Employees")) {
            ListTag employeesTag = tag.getList("Employees", Tag.TAG_COMPOUND);
            for (int i = 0; i < employeesTag.size(); i++) {
                CompoundTag empTag = employeesTag.getCompound(i);
                UUID uuid = empTag.getUUID("UUID");
                String empName = empTag.contains("Name") ? empTag.getString("Name") : "";
                inn.addEmployee(uuid, empName);
            }
        }

        if (tag.contains("Location")) {
            inn.setLocation(NbtUtils.readBlockPos(tag.getCompound("Location")));
        }
        inn.isSpawned = tag.getBoolean("IsSpawned");
        inn.isActive = tag.getBoolean("IsActive");
        inn.balance = tag.getDouble("Balance");

        ListTag roomsTag = tag.getList("Rooms", Tag.TAG_COMPOUND);
        for (int i = 0; i < roomsTag.size(); i++) {
            inn.addRoom(InnRoom.load(roomsTag.getCompound(i)));
        }

        ListTag propsTag = tag.getList("Props", Tag.TAG_COMPOUND);
        for (int i = 0; i < propsTag.size(); i++) {
            inn.addProp(InnProp.load(propsTag.getCompound(i)));
        }

        return inn;
    }
}
