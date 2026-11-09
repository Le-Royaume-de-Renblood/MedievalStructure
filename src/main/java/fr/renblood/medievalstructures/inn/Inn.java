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
    private UUID economyId=UUID.randomUUID();
    public UUID getEconomyId(){return economyId;}
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
    private List<InnMenuEntry> menuEntries;
    private String customerRouteId = "";
    private int customerRouteMinSeconds = 180;
    private int customerRouteMaxSeconds = 420;
    private int totalMenuSales;
    private long totalMenuRevenue;
    private String lastMenuSaleItem = "";
    private long lastMenuSalePrice;
    private List<InnMenuSale> recentMenuSales;

    public Inn(String name, UUID ownerUUID, int maxRooms, int propsPerRoom) {
        this.name = name;
        this.ownerUUID = ownerUUID;
        this.employees = new ArrayList<>();
        this.maxRooms = maxRooms;
        this.propsPerRoom = propsPerRoom;
        this.rooms = new ArrayList<>();
        this.props = new ArrayList<>();
        this.menuEntries = new ArrayList<>();
        this.recentMenuSales = new ArrayList<>();
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

    public int getMaxRooms() {
        return maxRooms;
    }

    public int getPropsPerRoom() {
        return propsPerRoom;
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

    public List<InnMenuEntry> getMenuEntries() {
        return menuEntries;
    }

    public InnMenuEntry getMenuEntry(String itemId) {
        if (itemId == null) return null;
        return menuEntries.stream().filter(entry -> entry.getItemId().equals(itemId)).findFirst().orElse(null);
    }

    public boolean addMenuEntry(InnMenuEntry entry) {
        if (entry == null || getMenuEntry(entry.getItemId()) != null) return false;
        menuEntries.add(entry);
        return true;
    }

    public boolean removeMenuEntry(String itemId) {
        return menuEntries.removeIf(entry -> entry.getItemId().equals(itemId));
    }

    public String getCustomerRouteId() {
        return customerRouteId;
    }

    public void setCustomerRouteId(String customerRouteId) {
        this.customerRouteId = customerRouteId == null ? "" : customerRouteId;
    }

    public int getCustomerRouteMinSeconds() {
        return customerRouteMinSeconds;
    }

    public int getCustomerRouteMaxSeconds() {
        return customerRouteMaxSeconds;
    }

    public void setCustomerRouteTiming(int minSeconds, int maxSeconds) {
        this.customerRouteMinSeconds = Math.max(10, minSeconds);
        this.customerRouteMaxSeconds = Math.max(this.customerRouteMinSeconds, maxSeconds);
    }

    public boolean hasCustomerRoute() {
        return customerRouteId != null && !customerRouteId.isBlank();
    }

    public boolean canReceiveCustomers() {
        return isActive && hasCustomerRoute()
                && countMenuType(InnMenuEntry.Type.DISH) >= 4
                && countMenuType(InnMenuEntry.Type.DRINK) >= 2
                && countMenuType(InnMenuEntry.Type.STARTER) >= 2;
    }

    public long countMenuType(InnMenuEntry.Type type) {
        return menuEntries.stream().filter(entry -> entry.getType() == type).count();
    }

    public void recordMenuSale(InnMenuEntry entry, long price, String sellerName, long soldAt) {
        if (entry == null) return;
        totalMenuSales++;
        totalMenuRevenue += Math.max(0L, price);
        lastMenuSaleItem = entry.getDisplayName();
        lastMenuSalePrice = Math.max(0L, price);
        recentMenuSales.add(0, new InnMenuSale(entry.getDisplayName(), sellerName == null ? "" : sellerName, Math.max(0L, price), soldAt));
        while (recentMenuSales.size() > 12) {
            recentMenuSales.remove(recentMenuSales.size() - 1);
        }
    }

    public int getTotalMenuSales() {
        return totalMenuSales;
    }

    public long getTotalMenuRevenue() {
        return totalMenuRevenue;
    }

    public String getLastMenuSaleItem() {
        return lastMenuSaleItem;
    }

    public long getLastMenuSalePrice() {
        return lastMenuSalePrice;
    }

    public List<InnMenuSale> getRecentMenuSales() {
        return recentMenuSales;
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("Name", name);
        tag.putUUID("EconomyId",economyId);
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

        ListTag menuTag = new ListTag();
        for (InnMenuEntry entry : menuEntries) {
            menuTag.add(entry.save());
        }
        tag.put("MenuEntries", menuTag);
        tag.putString("CustomerRouteId", customerRouteId);
        tag.putInt("CustomerRouteMinSeconds", customerRouteMinSeconds);
        tag.putInt("CustomerRouteMaxSeconds", customerRouteMaxSeconds);
        tag.putInt("TotalMenuSales", totalMenuSales);
        tag.putLong("TotalMenuRevenue", totalMenuRevenue);
        tag.putString("LastMenuSaleItem", lastMenuSaleItem);
        tag.putLong("LastMenuSalePrice", lastMenuSalePrice);

        ListTag salesTag = new ListTag();
        for (InnMenuSale sale : recentMenuSales) {
            salesTag.add(sale.save());
        }
        tag.put("RecentMenuSales", salesTag);

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
        if(tag.hasUUID("EconomyId"))inn.economyId=tag.getUUID("EconomyId");
        inn.balance = tag.getDouble("Balance");

        ListTag roomsTag = tag.getList("Rooms", Tag.TAG_COMPOUND);
        for (int i = 0; i < roomsTag.size(); i++) {
            inn.addRoom(InnRoom.load(roomsTag.getCompound(i)));
        }

        ListTag propsTag = tag.getList("Props", Tag.TAG_COMPOUND);
        for (int i = 0; i < propsTag.size(); i++) {
            inn.addProp(InnProp.load(propsTag.getCompound(i)));
        }

        ListTag menuTag = tag.getList("MenuEntries", Tag.TAG_COMPOUND);
        for (int i = 0; i < menuTag.size(); i++) {
            inn.addMenuEntry(InnMenuEntry.load(menuTag.getCompound(i)));
        }
        if (tag.contains("CustomerRouteId")) {
            inn.setCustomerRouteId(tag.getString("CustomerRouteId"));
        }
        if (tag.contains("CustomerRouteMinSeconds") || tag.contains("CustomerRouteMaxSeconds")) {
            inn.setCustomerRouteTiming(tag.getInt("CustomerRouteMinSeconds"), tag.getInt("CustomerRouteMaxSeconds"));
        }
        inn.totalMenuSales = tag.getInt("TotalMenuSales");
        inn.totalMenuRevenue = tag.getLong("TotalMenuRevenue");
        inn.lastMenuSaleItem = tag.getString("LastMenuSaleItem");
        inn.lastMenuSalePrice = tag.getLong("LastMenuSalePrice");
        ListTag salesTag = tag.getList("RecentMenuSales", Tag.TAG_COMPOUND);
        for (int i = 0; i < salesTag.size(); i++) {
            inn.recentMenuSales.add(InnMenuSale.load(salesTag.getCompound(i)));
        }

        return inn;
    }
}
