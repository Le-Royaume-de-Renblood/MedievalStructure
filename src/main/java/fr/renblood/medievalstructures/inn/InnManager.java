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

    /** Standard Minecraft SavedData save, verified read-only before proof. */
    public void persist(ServerLevel level){
        if(!level.getServer().isSameThread())throw new IllegalStateException("Server thread required");
        try{
            java.nio.file.Path root=level.getServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT);
            java.nio.file.Path folder=net.minecraft.world.level.dimension.DimensionType.getStorageFolder(level.dimension(),root).resolve("data");
            setDirty();save(folder.resolve(DATA_NAME+".dat").toFile());
            CompoundTag stored=net.minecraft.nbt.NbtIo.readCompressed(folder.resolve(DATA_NAME+".dat").toFile());
            if(!stored.getCompound("data").equals(save(new CompoundTag())))throw new java.io.IOException("Inn save not verified");
        }catch(Exception failure){throw new IllegalStateException("Auberge à rapprocher : sauvegarde non vérifiée",failure);}
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
