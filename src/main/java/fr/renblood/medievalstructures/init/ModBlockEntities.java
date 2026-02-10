package fr.renblood.medievalstructures.init;

import fr.renblood.medievalstructures.MedievalStructures;
import fr.renblood.medievalstructures.block.entity.ExplorationHallBlockEntity;
import fr.renblood.medievalstructures.block.entity.InnStructureBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, MedievalStructures.MODID);

    public static final RegistryObject<BlockEntityType<InnStructureBlockEntity>> INN_STRUCTURE_BE =
            BLOCK_ENTITIES.register("inn_structure_be", () ->
                    BlockEntityType.Builder.of(InnStructureBlockEntity::new,
                            ModBlocks.INN_STRUCTURE_BLOCK.get()).build(null));

    public static final RegistryObject<BlockEntityType<ExplorationHallBlockEntity>> EXPLORATION_HALL_BE =
            BLOCK_ENTITIES.register("exploration_hall_be", () ->
                    BlockEntityType.Builder.of(ExplorationHallBlockEntity::new,
                            ModBlocks.EXPLORATION_HALL_BLOCK.get()).build(null));

    public static void register(IEventBus eventBus) {
        BLOCK_ENTITIES.register(eventBus);
    }
}
