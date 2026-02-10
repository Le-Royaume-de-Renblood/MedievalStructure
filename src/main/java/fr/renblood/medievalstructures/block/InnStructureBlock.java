package fr.renblood.medievalstructures.block;

import fr.renblood.medievalstructures.block.entity.InnStructureBlockEntity;
import fr.renblood.medievalstructures.gui.InnCustomerMenu;
import fr.renblood.medievalstructures.gui.InnOwnerMenu;
import fr.renblood.medievalstructures.inn.Inn;
import fr.renblood.medievalstructures.inn.InnManager;
import fr.renblood.medievalstructures.inn.InnRoom;
import fr.renblood.medievalstructures.integration.MedievalCoinsIntegration;
import fr.renblood.medievalstructures.manager.DefinitionModeManager;
import fr.renblood.medievalstructures.network.PacketHandler;
import fr.renblood.medievalstructures.network.PacketSyncInnData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.network.NetworkHooks;
import net.minecraftforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

public class InnStructureBlock extends Block implements EntityBlock {
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;

    public InnStructureBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new InnStructureBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    public BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    public BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide && placer instanceof ServerPlayer player) {
            DefinitionModeManager manager = DefinitionModeManager.getInstance();
            if (manager.isInDefinitionMode(player)) {
                BlockPos existingPos = manager.getStructurePos(player);
                player.sendSystemMessage(Component.literal("Vous êtes déjà en train de configurer une structure en " + existingPos + ". Finissez ou annulez avant d'en poser une autre."));
                level.destroyBlock(pos, true); 
            } else {
                manager.enterDefinitionMode(player, pos);
                player.sendSystemMessage(Component.literal("Mode définition activé."));
                player.sendSystemMessage(Component.literal("1. Utilisez /ms pos1 et /ms pos2 pour définir la zone."));
                player.sendSystemMessage(Component.literal("2. Utilisez /ms validate pour confirmer."));
            }
        }
    }

    @Override
    public void playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            DefinitionModeManager.getInstance().onStructureBlockBroken(pos, serverPlayer);
        }
        super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof InnStructureBlockEntity innBe) {
                String innName = innBe.getInnName();
                if (innName == null || innName.isEmpty()) {
                    player.sendSystemMessage(Component.literal("Ce bloc n'est lié à aucune auberge."));
                    player.sendSystemMessage(Component.literal("Utilisez /auberge link <nom> en regardant le bloc pour le lier."));
                } else {
                    InnManager manager = InnManager.get((ServerLevel) level);
                    Inn inn = manager.getInn(innName);
                    
                    if (inn != null) {
                        // Vérification stricte du propriétaire
                        boolean isOwner = inn.getOwnerUUID() != null && inn.getOwnerUUID().equals(player.getUUID());
                        
                        // Vérification si le joueur est employé
                        boolean isEmployee = inn.isEmployee(player.getUUID());

                        // Vérification si le joueur loue une chambre
                        boolean isRenting = false;
                        for (InnRoom room : inn.getRooms()) {
                            if (room.getOccupantUUID() != null && room.getOccupantUUID().equals(player.getUUID())) {
                                isRenting = true;
                                break;
                            }
                        }

                        if (isOwner || isEmployee || isRenting) {
                            // Menu Propriétaire, Employé ou Locataire (qui permet de payer)
                            NetworkHooks.openScreen(serverPlayer, new SimpleMenuProvider(
                                    (id, inv, p) -> new InnOwnerMenu(id, inv, ContainerLevelAccess.create(level, pos)),
                                    Component.literal("Gestion Auberge")
                            ), pos);
                        } else {
                            // Menu Client (pour louer)
                            NetworkHooks.openScreen(serverPlayer, new SimpleMenuProvider(
                                    (id, inv, p) -> new InnCustomerMenu(id, inv, ContainerLevelAccess.create(level, pos)),
                                    Component.literal("Réservation")
                            ), pos);
                        }
                        
                        // Envoyer les données
                        PacketHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> serverPlayer), new PacketSyncInnData(inn));
                    }
                }
            }
        }
        return InteractionResult.SUCCESS;
    }
}
