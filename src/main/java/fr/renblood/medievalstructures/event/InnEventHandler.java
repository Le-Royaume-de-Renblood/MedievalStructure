package fr.renblood.medievalstructures.event;

import fr.renblood.medievalstructures.MedievalStructures;
import fr.renblood.medievalstructures.inn.Inn;
import fr.renblood.medievalstructures.inn.InnManager;
import fr.renblood.medievalstructures.inn.InnProp;
import fr.renblood.medievalstructures.inn.InnRoom;
import fr.renblood.medievalstructures.integration.MedievalCoinsIntegration;
import fr.renblood.medievalstructures.item.InnkeeperWandItem;
import fr.renblood.medievalstructures.manager.RoomCreationManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.FlintAndSteelItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.AbstractFurnaceBlock;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FlowerPotBlock;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerContainerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Random;
import java.util.UUID;

@Mod.EventBusSubscriber(modid = MedievalStructures.MODID)
public class InnEventHandler {

    private static long lastDayTime = 0;
    private static final Random RANDOM = new Random();

    @SubscribeEvent
    public static void onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.level.isClientSide) return;

        ServerLevel level = (ServerLevel) event.level;
        long time = level.getDayTime();

        if (time % 24000 < 100 && lastDayTime % 24000 >= 23900) {
            handleNewDay(level);
        }

        lastDayTime = time;
    }

    private static void handleNewDay(ServerLevel level) {
        InnManager manager = InnManager.get(level);
        for (Inn inn : manager.getInns()) {
            if (!inn.isActive()) continue;

            int dirtyPropsCount = 0;
            for (InnProp prop : inn.getProps()) {
                if (!prop.isActive()) {
                    dirtyPropsCount++;
                }
            }
            double penaltyFactor = 1.0 - (dirtyPropsCount * 0.05);
            if (penaltyFactor < 0) penaltyFactor = 0;

            for (InnRoom room : inn.getRooms()) {
                if (room.isBusy()) {
                    // Si c'est un joueur, on déduit le loyer de son solde
                    if (room.getOccupantUUID() != null) {
                        room.addToBalance(-room.getPrice());

                        // Notification si le joueur est connecté et en retard de paiement
                        if (room.getBalance() < 0) {
                            ServerPlayer occupant = level.getServer().getPlayerList().getPlayer(room.getOccupantUUID());
                            if (occupant != null) {
                                occupant.sendSystemMessage(Component.literal("Vous devez " + (int)Math.abs(room.getBalance()) + " pièces à l'auberge '" + inn.getName() + "'."));
                            }
                        }
                    } else {
                        // Si c'est un PNJ, on paie directement l'aubergiste (système existant)
                        if (!room.isDirty()) {
                            double gain = room.getPrice() * penaltyFactor;
                            inn.addBalance(gain);

                            UUID ownerUUID = inn.getOwnerUUID();
                            if (ownerUUID != null) {
                                ServerPlayer owner = level.getServer().getPlayerList().getPlayer(ownerUUID);
                                if (owner != null) {
                                    // XP pour PNJ (inchangé)
//                                    MedievalCoinsIntegration.addInnkeeperXP(owner, 5);
//                                    owner.sendSystemMessage(Component.literal("Gain de " + (int)gain + " pièces pour la chambre " + room.getNumber() + " (PNJ)"));
                                }
                            }
                        }
                    }

                    if (!room.isDirty()) {
                        room.setDirty(true);
                        makeBedDirty(level, room.getHeadPos());
                    }
                }
            }

            for (InnProp prop : inn.getProps()) {
                if (prop.getType().equals("candle")) {
                    handleCandleProp(level, prop);
                } else if (prop.getType().equals("chest")) {
                    handleChestProp(level, prop);
                } else if (prop.getType().equals("flower_pot")) {
                    handleFlowerPotProp(level, prop);
                } else if (prop.getType().equals("furnace")) {
                    handleFurnaceProp(level, prop);
                }
            }
        }
        manager.setDirty();
    }

    private static void handleCandleProp(ServerLevel level, InnProp prop) {
        BlockPos pos = prop.getPos();
        BlockState state = level.getBlockState(pos);

        if (state.hasProperty(BlockStateProperties.LIT) && state.getValue(BlockStateProperties.LIT)) {
            level.setBlock(pos, state.setValue(BlockStateProperties.LIT, false), 3);
            prop.setActive(false);
        }
    }

    private static void handleChestProp(ServerLevel level, InnProp prop) {
        BlockPos pos = prop.getPos();
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof Container container) {
            if (container.isEmpty()) {
                int count = 2 + RANDOM.nextInt(9); // 2 à 10
                for (int i = 0; i < count; i++) {
                    int slot = RANDOM.nextInt(container.getContainerSize());
                    if (container.getItem(slot).isEmpty()) {
                        container.setItem(slot, new ItemStack(Items.DEAD_BUSH, 1));
                    } else {
                        for (int j = 0; j < container.getContainerSize(); j++) {
                            if (container.getItem(j).isEmpty()) {
                                container.setItem(j, new ItemStack(Items.DEAD_BUSH, 1));
                                break;
                            }
                        }
                    }
                }
            }
            prop.setActive(false);
        }
    }

    private static void handleFlowerPotProp(ServerLevel level, InnProp prop) {
        BlockPos pos = prop.getPos();
        BlockState state = level.getBlockState(pos);

        if (state.getBlock() instanceof FlowerPotBlock pot && pot.getContent() != Blocks.AIR) {
            level.setBlock(pos, Blocks.FLOWER_POT.defaultBlockState(), 3);
            prop.setActive(false);
        }
    }

    private static void handleFurnaceProp(ServerLevel level, InnProp prop) {
        BlockPos pos = prop.getPos();
        BlockEntity be = level.getBlockEntity(pos);
        BlockState state = level.getBlockState(pos);

        if (be instanceof AbstractFurnaceBlockEntity furnace) {
            ItemStack fuel = furnace.getItem(1); // Slot carburant
            if (fuel.is(Items.COAL) || fuel.is(Items.CHARCOAL)) {
                fuel.shrink(1);
                prop.setActive(true);

                // Allumer visuellement le four si ce n'est pas déjà fait
                if (!state.getValue(AbstractFurnaceBlock.LIT)) {
                    level.setBlock(pos, state.setValue(AbstractFurnaceBlock.LIT, true), 3);
                }
            } else {
                prop.setActive(false);
                // Éteindre le four
                if (state.getValue(AbstractFurnaceBlock.LIT)) {
                    level.setBlock(pos, state.setValue(AbstractFurnaceBlock.LIT, false), 3);
                }
            }
        }
    }

    private static void makeBedDirty(ServerLevel level, BlockPos headPos) {
        BlockState state = level.getBlockState(headPos);
        if (state.getBlock() instanceof BedBlock) {
            Direction facing = state.getValue(BedBlock.FACING);

            // On remplace la tête
            level.setBlock(headPos, Blocks.BROWN_BED.defaultBlockState()
                    .setValue(BedBlock.FACING, facing)
                    .setValue(BedBlock.PART, BedPart.HEAD)
                    .setValue(BedBlock.OCCUPIED, state.getValue(BedBlock.OCCUPIED)), 18); // Flag 18 = Pas d'update, pas de check

            // On remplace le pied
            BlockPos footPos = headPos.relative(facing.getOpposite());
            BlockState footState = level.getBlockState(footPos);
            if (footState.getBlock() instanceof BedBlock) {
                level.setBlock(footPos, Blocks.BROWN_BED.defaultBlockState()
                        .setValue(BedBlock.FACING, facing)
                        .setValue(BedBlock.PART, BedPart.FOOT)
                        .setValue(BedBlock.OCCUPIED, footState.getValue(BedBlock.OCCUPIED)), 18);
            }
        }
    }

    @SubscribeEvent
    public static void onBlockPlace(BlockEvent.EntityPlaceEvent event) {
        if (event.getLevel().isClientSide() || !(event.getLevel() instanceof ServerLevel level)) return;

        if (event.getPlacedBlock().getBlock() instanceof BedBlock && event.getPlacedBlock().getBlock() != Blocks.BROWN_BED) {
            InnManager manager = InnManager.get(level);
            BlockPos pos = event.getPos();
            BlockState state = event.getPlacedBlock();
            BedPart part = state.getValue(BedBlock.PART);
            Direction facing = state.getValue(BedBlock.FACING);

            // Calculer la position de l'autre partie du lit posé
            BlockPos otherPartPos = part == BedPart.HEAD ? pos.relative(facing.getOpposite()) : pos.relative(facing);

            // Identifier quelle est la tête et quel est le pied du lit posé
            BlockPos placedHeadPos = part == BedPart.HEAD ? pos : otherPartPos;
            BlockPos placedFootPos = part == BedPart.HEAD ? otherPartPos : pos;

            for (Inn inn : manager.getInns()) {
                for (InnRoom room : inn.getRooms()) {
                    // Vérification stricte : La tête posée doit être à headPos ET le pied posé doit être à footPos
                    if (placedHeadPos.equals(room.getHeadPos()) &&
                        (room.getFootPos() == null || placedFootPos.equals(room.getFootPos()))) {

                        if (room.isDirty()) {
                            room.setDirty(false);
                            manager.setDirty();
                            if (event.getEntity() != null) {
                                event.getEntity().sendSystemMessage(Component.literal("Chambre " + room.getNumber() + " nettoyée !"));

                                // XP pour nettoyage de lit
                                if (event.getEntity() instanceof ServerPlayer player) {
                                    // Vérifier si le joueur est propriétaire ou employé
                                    if (inn.getOwnerUUID().equals(player.getUUID()) || inn.isEmployee(player.getUUID())) {
                                        MedievalCoinsIntegration.addInnkeeperXP(player, 1.0);
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (event.getLevel().isClientSide() || !(event.getLevel() instanceof ServerLevel level)) return;
        if (event.getPlayer().isCreative()) return;

        BlockPos pos = event.getPos();
        InnManager manager = InnManager.get(level);

        for (Inn inn : manager.getInns()) {
            for (InnRoom room : inn.getRooms()) {
                if (pos.equals(room.getHeadPos()) || (room.getFootPos() != null && pos.equals(room.getFootPos()))) {
                    if (!room.isDirty()) {
                        event.setCanceled(true);
                        event.getPlayer().sendSystemMessage(Component.literal("Vous ne pouvez pas casser ce lit car il n'est pas sale."));
                        return;
                    }
                }
            }
            for (InnProp prop : inn.getProps()) {
                if (prop.getPos().equals(pos)) {
                    event.setCanceled(true);
                    event.getPlayer().sendSystemMessage(Component.literal("Vous ne pouvez pas casser les accessoires de l'auberge."));
                    return;
                }
            }
        }
    }

    @SubscribeEvent
    public static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        if (event.getHand() != InteractionHand.MAIN_HAND) return;

        Player player = event.getEntity();
        ItemStack stack = player.getMainHandItem();

        if (stack.getItem() instanceof InnkeeperWandItem) {
            // Annuler la casse du bloc si on tient le bâton
            event.setCanceled(true);

            if (!player.level().isClientSide && player instanceof ServerPlayer serverPlayer) {
                if (RoomCreationManager.getInstance().isCreating(serverPlayer)) {
                    RoomCreationManager.getInstance().setPos1(serverPlayer, event.getPos());
                }
            }
        }
    }

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getHand() != InteractionHand.MAIN_HAND) return;

        Player player = event.getEntity();
        ItemStack stack = player.getMainHandItem();

        // Gestion du bâton d'aubergiste pour la création de chambre
        if (stack.getItem() instanceof InnkeeperWandItem) {
            if (!player.level().isClientSide && player instanceof ServerPlayer serverPlayer) {
                if (RoomCreationManager.getInstance().isCreating(serverPlayer)) {
                    RoomCreationManager.getInstance().setPos2(serverPlayer, event.getPos());
                    event.setCanceled(true); // Empêcher l'interaction normale (ex: ouvrir coffre) et l'inspection
                    return;
                }
            }
        }

        if (event.getLevel().isClientSide) return;

        ServerLevel level = (ServerLevel) event.getLevel();
        BlockPos pos = event.getPos();
        BlockState state = level.getBlockState(pos);
        InnManager manager = InnManager.get(level);

        for (Inn inn : manager.getInns()) {
            // Vérification des props (bougies, etc.)
            for (InnProp prop : inn.getProps()) {
                if (prop.getPos().equals(pos)) {
                    // Vérification du propriétaire ou employé
                    boolean isOwner = inn.getOwnerUUID() != null && inn.getOwnerUUID().equals(player.getUUID());
                    boolean isEmployee = inn.isEmployee(player.getUUID());
                    
                    if (!isOwner && !isEmployee && !player.isCreative()) {
                        event.setCanceled(true);
                        player.displayClientMessage(Component.literal("Seul le propriétaire ou un employé peut interagir avec cet accessoire."), true);
                        return;
                    }

                    // Si c'est un coffre prop, on empêche le dépôt d'items
                    if (prop.getType().equals("chest")) {
                        // On laisse l'ouverture se faire pour vider le coffre (maintenance)
                    }

                    if (!prop.isActive()) {
                        boolean reactivated = false;
                        if (prop.getType().equals("candle")) {
                            // On vérifie si le joueur tient un briquet ou un item pour allumer
                            if (stack.getItem() instanceof FlintAndSteelItem || stack.getItem() == Items.FIRE_CHARGE) {
                                reactivated = true;
                            }
                        } else if (prop.getType().equals("flower_pot")) {
                            // Si le joueur met une fleur dans le pot
                            // On vérifie si le bloc est un pot vide et si le joueur tient un item qui est un bloc (fleur potentielle)
                            if (state.getBlock() == Blocks.FLOWER_POT && !stack.isEmpty() && stack.getItem() instanceof BlockItem) {
                                reactivated = true;
                            }
                        } else if (prop.getType().equals("furnace")) {
                            // Pour le four, l'activation se fait via le GUI (ajout de charbon)
                            // On laisse l'interaction se faire pour ouvrir le GUI
                        }
                        // Pour les coffres, on gère la validation à la fermeture du GUI

                        if (reactivated) {
                            prop.setActive(true);
                            manager.setDirty();
                            event.getEntity().sendSystemMessage(Component.literal("Prop réactivé !"));

                            // XP pour nettoyage de prop (sauf coffre et four qui sont gérés ailleurs)
                            if (player instanceof ServerPlayer serverPlayer) {
                                if (inn.getOwnerUUID().equals(player.getUUID()) || inn.isEmployee(player.getUUID())) {
                                    MedievalCoinsIntegration.addInnkeeperXP(serverPlayer, 0.5);
                                }
                            }
                        }
                    } else {
                        // Si le prop est actif (propre), on empêche de le modifier
                        if (prop.getType().equals("flower_pot")) {
                            // Empêcher de retirer la fleur
                            event.setCanceled(true);
                            player.displayClientMessage(Component.literal("Ce pot de fleur est déjà décoré."), true);
                            return;
                        } else if (prop.getType().equals("candle")) {
                            // Empêcher d'éteindre la bougie si elle est active (propre)
                            if (state.getValue(BlockStateProperties.LIT)) {
                                event.setCanceled(true);
                                player.displayClientMessage(Component.literal("Cette bougie est déjà allumée."), true);
                                return;
                            }
                        } else if (prop.getType().equals("furnace")) {
                            // Empêcher d'ouvrir le four s'il est actif (contient du charbon)
                            // On affiche le temps restant (nombre de charbons)
                            BlockEntity be = level.getBlockEntity(pos);
                            if (be instanceof AbstractFurnaceBlockEntity furnace) {
                                ItemStack fuel = furnace.getItem(1);
                                int daysLeft = fuel.getCount();
                                player.displayClientMessage(Component.literal("Ce four est allumé pour encore " + daysLeft + " jours."), true);
                                event.setCanceled(true);
                                return;
                            }
                        }
                    }
                }
            }

            // Vérification des accès aux chambres (portes et coffres)
            for (InnRoom room : inn.getRooms()) {
                if (room.isInside(pos)) {
                    // Si c'est une porte ou un coffre
                    if (state.getBlock() instanceof DoorBlock || state.getBlock() instanceof ChestBlock) {
                        // Si la chambre est louée
                        if (room.isBusy()) {
                            // Si le joueur n'est pas l'occupant et n'est pas le propriétaire de l'auberge
                            boolean isOccupant = room.getOccupantUUID() != null && room.getOccupantUUID().equals(player.getUUID());
                            boolean isOwner = inn.getOwnerUUID() != null && inn.getOwnerUUID().equals(player.getUUID());
                            boolean isEmployee = inn.isEmployee(player.getUUID());

                            if (!isOccupant && !isOwner && !isEmployee && !player.isCreative()) {
                                event.setCanceled(true);
                                player.displayClientMessage(Component.literal("Cette chambre est louée, vous ne pouvez pas ouvrir ceci."), true);
                                return;
                            }
                        }
                    }
                }
            }
        }
    }

    @SubscribeEvent
    public static void onContainerClosed(PlayerContainerEvent.Close event) {
        if (event.getEntity().level().isClientSide || !(event.getEntity().level() instanceof ServerLevel level)) return;

        InnManager manager = InnManager.get(level);
        BlockPos playerPos = event.getEntity().blockPosition();
        Player player = event.getEntity();

        for (Inn inn : manager.getInns()) {
            for (InnProp prop : inn.getProps()) {
                if (prop.getPos().distSqr(playerPos) < 25) {
                    BlockEntity be = level.getBlockEntity(prop.getPos());

                    if (prop.getType().equals("chest")) {
                        if (be instanceof Container container) {
                            if (container.isEmpty() && !prop.isActive()) {
                                prop.setActive(true);
                                manager.setDirty();
                                event.getEntity().sendSystemMessage(Component.literal("Coffre vidé et validé !"));

                                // XP pour nettoyage de coffre
                                if (player instanceof ServerPlayer serverPlayer) {
                                    if (inn.getOwnerUUID().equals(player.getUUID()) || inn.isEmployee(player.getUUID())) {
                                        MedievalCoinsIntegration.addInnkeeperXP(serverPlayer, 0.5);
                                    }
                                }
                            } else if (!container.isEmpty() && !prop.isActive()) {
                                event.getEntity().sendSystemMessage(Component.literal("Ce coffre est sale, vous devez le vider entièrement pour le nettoyer."));
                            }
                        }
                    } else if (prop.getType().equals("furnace")) {
                        if (be instanceof AbstractFurnaceBlockEntity furnace) {
                            ItemStack fuel = furnace.getItem(1);
                            if (fuel.getCount() > 2) {
                                // Limiter à 2 charbons max
                                ItemStack excess = fuel.split(fuel.getCount() - 2);
                                event.getEntity().spawnAtLocation(excess);
                                event.getEntity().sendSystemMessage(Component.literal("Vous ne pouvez mettre que 2 charbons maximum dans ce four."));
                            }

                            // Vérification si actif (au moins 1 charbon)
                            if (!fuel.isEmpty() && (fuel.is(Items.COAL) || fuel.is(Items.CHARCOAL))) {
                                if (!prop.isActive()) {
                                    prop.setActive(true);
                                    manager.setDirty();
                                    event.getEntity().sendSystemMessage(Component.literal("Four rechargé !"));

                                    // Allumer visuellement le four
                                    BlockState state = level.getBlockState(prop.getPos());
                                    if (state.getBlock() instanceof AbstractFurnaceBlock && !state.getValue(AbstractFurnaceBlock.LIT)) {
                                        level.setBlock(prop.getPos(), state.setValue(AbstractFurnaceBlock.LIT, true), 3);
                                    }

                                    // XP pour rechargement de four
                                    if (player instanceof ServerPlayer serverPlayer) {
                                        if (inn.getOwnerUUID().equals(player.getUUID()) || inn.isEmployee(player.getUUID())) {
                                            MedievalCoinsIntegration.addInnkeeperXP(serverPlayer, 0.5);
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private static boolean isSameBedLocation(BlockPos pos1, BlockPos pos2) {
        return pos1.distSqr(pos2) <= 2.5;
    }
}
