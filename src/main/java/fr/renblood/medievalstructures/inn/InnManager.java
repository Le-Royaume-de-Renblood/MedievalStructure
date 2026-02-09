package fr.renblood.medievalstructures.inn;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class InnManager extends SavedData {
    private static final String DATA_NAME = "medieval_structures_inns";
    private final List<Inn> inns = new ArrayList<>();

    public static InnManager get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(InnManager::load, InnManager::new, DATA_NAME);
    }

    public InnManager() {
    }

    public void addInn(Inn inn) {
        inns.add(inn);
        setDirty();
    }

    public void removeInn(String name) {
        inns.removeIf(inn -> inn.getName().equals(name));
        setDirty();
    }

    @Nullable
    public Inn getInn(String name) {
        return inns.stream().filter(inn -> inn.getName().equals(name)).findFirst().orElse(null);
    }

    public List<Inn> getInns() {
        return inns;
    }

    public static InnManager load(CompoundTag tag) {
        InnManager manager = new InnManager();
        ListTag list = tag.getList("Inns", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            manager.addInn(Inn.load(list.getCompound(i)));
        }
        return manager;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag list = new ListTag();
        for (Inn inn : inns) {
            list.add(inn.save());
        }
        tag.put("Inns", list);
        return tag;
    }
}
