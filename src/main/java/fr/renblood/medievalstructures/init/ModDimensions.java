package fr.renblood.medievalstructures.init;

import fr.renblood.medievalstructures.MedievalStructures;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.dimension.DimensionType;

public class ModDimensions {
    public static final ResourceKey<Level> EXPLORATION_LEVEL_KEY = ResourceKey.create(Registries.DIMENSION,
            new ResourceLocation(MedievalStructures.MODID, "exploration_world"));

    public static final ResourceKey<DimensionType> EXPLORATION_DIM_TYPE = ResourceKey.create(Registries.DIMENSION_TYPE,
            new ResourceLocation(MedievalStructures.MODID, "exploration_type"));

    public static void register() {
        System.out.println("Registering ModDimensions for " + MedievalStructures.MODID);
    }
}
