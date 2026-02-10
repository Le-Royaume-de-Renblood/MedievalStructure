package fr.renblood.medievalstructures.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import fr.renblood.medievalstructures.block.entity.InnStructureBlockEntity;
import fr.renblood.medievalstructures.init.ModDimensions;
import fr.renblood.medievalstructures.inn.Inn;
import fr.renblood.medievalstructures.inn.InnManager;
import fr.renblood.medievalstructures.inn.InnProp;
import fr.renblood.medievalstructures.inn.InnRoom;
import fr.renblood.medievalstructures.integration.MedievalCoinsIntegration;
import fr.renblood.medievalstructures.manager.DefinitionModeManager;
import fr.renblood.medievalstructures.manager.RoomCreationManager;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

import java.util.stream.Collectors;

public class ModCommands {

    private static final SuggestionProvider<CommandSourceStack> SUGGEST_PROP_TYPES = (context, builder) -> {
        return SharedSuggestionProvider.suggest(new String[]{"candle", "chest", "flower_pot", "furnace"}, builder);
    };

    private static final SuggestionProvider<CommandSourceStack> SUGGEST_INN_NAMES = (context, builder) -> {
        ServerLevel level = context.getSource().getLevel();
        InnManager manager = InnManager.get(level);
        return SharedSuggestionProvider.suggest(manager.getInns().stream().map(Inn::getName).collect(Collectors.toList()), builder);
    };

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        // Commandes /ms (Admin)
        dispatcher.register(Commands.literal("ms")
                .requires(s -> s.hasPermission(2))
                .then(Commands.literal("pos1")
                        .executes(ModCommands::setPos1))
                .then(Commands.literal("pos2")
                        .executes(ModCommands::setPos2))
                .then(Commands.literal("validate")
                        .executes(ModCommands::validate)));

        // Commandes /exploration (Admin)
        dispatcher.register(Commands.literal("exploration")
                .requires(s -> s.hasPermission(2))
                .then(Commands.literal("join")
                        .executes(ModCommands::joinExploration)));

        // Commandes /auberge (Admin)
        LiteralArgumentBuilder<CommandSourceStack> aubergeCommand = Commands.literal("auberge")
                .requires(s -> s.hasPermission(2));

        // ... (Reste des commandes auberge inchangé)
        // create
        aubergeCommand.then(Commands.literal("create")
                .then(Commands.argument("name", StringArgumentType.string())
                        .then(Commands.argument("maxRooms", IntegerArgumentType.integer(1))
                                .then(Commands.argument("propsPerRoom", IntegerArgumentType.integer(0))
                                        .executes(ModCommands::createInn)))));

        // remove
        aubergeCommand.then(Commands.literal("remove")
                .then(Commands.argument("name", StringArgumentType.string()).suggests(SUGGEST_INN_NAMES)
                        .executes(ModCommands::removeInn)));

        // list
        aubergeCommand.then(Commands.literal("list")
                .executes(ModCommands::listInns));

        // spawn
        aubergeCommand.then(Commands.literal("spawn")
                .then(Commands.argument("name", StringArgumentType.string()).suggests(SUGGEST_INN_NAMES)
                        .executes(ModCommands::spawnInn)));

        // link
        aubergeCommand.then(Commands.literal("link")
                .then(Commands.argument("name", StringArgumentType.string()).suggests(SUGGEST_INN_NAMES)
                        .executes(ModCommands::linkInn)));

        // setowner
        aubergeCommand.then(Commands.literal("setowner")
                .then(Commands.argument("name", StringArgumentType.string()).suggests(SUGGEST_INN_NAMES)
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(ModCommands::setOwner))));

        // rent
        aubergeCommand.then(Commands.literal("rent")
                .then(Commands.argument("name", StringArgumentType.string()).suggests(SUGGEST_INN_NAMES)
                        .then(Commands.argument("roomNumber", IntegerArgumentType.integer(1))
                                .then(Commands.literal("player")
                                        .then(Commands.argument("player", EntityArgument.player())
                                                .executes(ctx -> rentRoom(ctx, EntityArgument.getPlayer(ctx, "player"), true))))
                                .then(Commands.literal("npc")
                                        .then(Commands.argument("npcName", StringArgumentType.string())
                                                .executes(ctx -> rentRoom(ctx, null, false)))))));

        // leave
        aubergeCommand.then(Commands.literal("leave")
                .then(Commands.argument("name", StringArgumentType.string()).suggests(SUGGEST_INN_NAMES)
                        .then(Commands.argument("roomNumber", IntegerArgumentType.integer(1))
                                .executes(ModCommands::leaveRoom))));

        // activate
        aubergeCommand.then(Commands.literal("activate")
                .then(Commands.argument("name", StringArgumentType.string()).suggests(SUGGEST_INN_NAMES)
                        .then(Commands.argument("active", BoolArgumentType.bool())
                                .executes(ModCommands::activateInn))));

        // room create (start creation mode)
        aubergeCommand.then(Commands.literal("room")
                .then(Commands.literal("create")
                        .then(Commands.argument("innName", StringArgumentType.string()).suggests(SUGGEST_INN_NAMES)
                                .then(Commands.argument("number", IntegerArgumentType.integer(1))
                                        .then(Commands.argument("size", IntegerArgumentType.integer(1))
                                                .then(Commands.argument("price", DoubleArgumentType.doubleArg(0))
                                                        .executes(ModCommands::startRoomCreation)))))));

        // room confirm (validate creation)
        aubergeCommand.then(Commands.literal("room")
                .then(Commands.literal("confirm")
                        .executes(ModCommands::confirmRoomCreation)));

        // room remove & list
        aubergeCommand.then(Commands.literal("room")
                .then(Commands.literal("remove")
                        .then(Commands.argument("innName", StringArgumentType.string()).suggests(SUGGEST_INN_NAMES)
                                .then(Commands.argument("number", IntegerArgumentType.integer(1))
                                        .executes(ModCommands::removeRoom))))
                .then(Commands.literal("list")
                        .then(Commands.argument("innName", StringArgumentType.string()).suggests(SUGGEST_INN_NAMES)
                                .executes(ModCommands::listRooms))));

        // props
        aubergeCommand.then(Commands.literal("props")
                .then(Commands.literal("create")
                        .then(Commands.argument("innName", StringArgumentType.string()).suggests(SUGGEST_INN_NAMES)
                                .then(Commands.argument("id", IntegerArgumentType.integer(1))
                                        .then(Commands.argument("type", StringArgumentType.string()).suggests(SUGGEST_PROP_TYPES)
                                                .then(Commands.argument("pos", BlockPosArgument.blockPos())
                                                        .executes(ModCommands::createProp))))))
                .then(Commands.literal("remove")
                        .then(Commands.argument("innName", StringArgumentType.string()).suggests(SUGGEST_INN_NAMES)
                                .then(Commands.argument("id", IntegerArgumentType.integer(1))
                                        .executes(ModCommands::removeProp))))
                .then(Commands.literal("list")
                        .then(Commands.argument("innName", StringArgumentType.string()).suggests(SUGGEST_INN_NAMES)
                                .executes(ModCommands::listProps))));

        dispatcher.register(aubergeCommand);
    }

    private static int joinExploration(CommandContext<CommandSourceStack> context) {
        try {
            ServerPlayer player = context.getSource().getPlayerOrException();
            ServerLevel explorationWorld = context.getSource().getServer().getLevel(ModDimensions.EXPLORATION_LEVEL_KEY);
            
            if (explorationWorld == null) {
                context.getSource().sendFailure(Component.literal("Le monde d'exploration n'est pas chargé."));
                return 0;
            }
            
            // TP au spawn du monde d'exploration
            BlockPos spawn = explorationWorld.getSharedSpawnPos();
            player.teleportTo(explorationWorld, spawn.getX(), 100, spawn.getZ(), 0, 0);
            context.getSource().sendSuccess(() -> Component.literal("Téléporté au monde d'exploration."), true);
            return 1;
        } catch (Exception e) {
            context.getSource().sendFailure(Component.literal("Erreur: " + e.getMessage()));
            return 0;
        }
    }

    // --- Méthodes existantes ---
    private static int setPos1(CommandContext<CommandSourceStack> context) {
        try {
            ServerPlayer player = context.getSource().getPlayerOrException();
            
            // Vérification XP
            if (MedievalCoinsIntegration.getInnkeeperXP(player) < 0) {
                context.getSource().sendFailure(Component.literal("Vous n'avez pas le métier d'aubergiste."));
                return 0;
            }
            
            BlockPos pos = new BlockPos((int)player.getX(), (int)player.getY(), (int)player.getZ());
            DefinitionModeManager.getInstance().setPoint1(player, pos);
            return 1;
        } catch (Exception e) {
            context.getSource().sendFailure(Component.literal("Erreur: " + e.getMessage()));
            return 0;
        }
    }

    private static int setPos2(CommandContext<CommandSourceStack> context) {
        try {
            ServerPlayer player = context.getSource().getPlayerOrException();
            
            // Vérification XP
            if (MedievalCoinsIntegration.getInnkeeperXP(player) < 0) {
                context.getSource().sendFailure(Component.literal("Vous n'avez pas le métier d'aubergiste."));
                return 0;
            }
            
            BlockPos pos = new BlockPos((int)player.getX(), (int)player.getY(), (int)player.getZ());
            DefinitionModeManager.getInstance().setPoint2(player, pos);
            return 1;
        } catch (Exception e) {
            context.getSource().sendFailure(Component.literal("Erreur: " + e.getMessage()));
            return 0;
        }
    }

    private static int validate(CommandContext<CommandSourceStack> context) {
        try {
            ServerPlayer player = context.getSource().getPlayerOrException();
            DefinitionModeManager.getInstance().validate(player);
            return 1;
        } catch (Exception e) {
            context.getSource().sendFailure(Component.literal("Erreur: " + e.getMessage()));
            return 0;
        }
    }

    // --- Nouvelles méthodes pour /auberge ---

    private static int createInn(CommandContext<CommandSourceStack> context) {
        try {
            String name = StringArgumentType.getString(context, "name");
            int maxRooms = IntegerArgumentType.getInteger(context, "maxRooms");
            int propsPerRoom = IntegerArgumentType.getInteger(context, "propsPerRoom");
            ServerPlayer player = context.getSource().getPlayerOrException();
            ServerLevel level = context.getSource().getLevel();
            InnManager manager = InnManager.get(level);

            if (manager.getInn(name) != null) {
                context.getSource().sendFailure(Component.literal("Une auberge avec ce nom existe déjà."));
                return 0;
            }

            manager.addInn(new Inn(name, player.getUUID(), maxRooms, propsPerRoom));
            context.getSource().sendSuccess(() -> Component.literal("Auberge '" + name + "' créée avec succès. Propriétaire: " + player.getName().getString()), true);
            return 1;
        } catch (Exception e) {
            context.getSource().sendFailure(Component.literal("Erreur: " + e.getMessage()));
            return 0;
        }
    }

    private static int removeInn(CommandContext<CommandSourceStack> context) {
        String name = StringArgumentType.getString(context, "name");
        ServerLevel level = context.getSource().getLevel();
        InnManager manager = InnManager.get(level);

        if (manager.getInn(name) == null) {
            context.getSource().sendFailure(Component.literal("Aucune auberge trouvée avec ce nom."));
            return 0;
        }

        manager.removeInn(name);
        context.getSource().sendSuccess(() -> Component.literal("Auberge '" + name + "' supprimée."), true);
        return 1;
    }

    private static int listInns(CommandContext<CommandSourceStack> context) {
        ServerLevel level = context.getSource().getLevel();
        InnManager manager = InnManager.get(level);
        
        if (manager.getInns().isEmpty()) {
            context.getSource().sendSuccess(() -> Component.literal("Aucune auberge enregistrée."), false);
            return 1;
        }

        context.getSource().sendSuccess(() -> Component.literal("--- Liste des auberges ---"), false);
        for (Inn inn : manager.getInns()) {
            context.getSource().sendSuccess(() -> Component.literal("- " + inn.getName() + 
                    " (Spawned: " + inn.isSpawned() + 
                    ", Rooms: " + inn.getRooms().size() + "/" + /*inn.getMaxRooms() +*/ // TODO: Add getter
                    ", Active: " + inn.isActive() + ")"), false);
        }
        return 1;
    }

    private static int spawnInn(CommandContext<CommandSourceStack> context) {
        String name = StringArgumentType.getString(context, "name");
        ServerLevel level = context.getSource().getLevel();
        InnManager manager = InnManager.get(level);
        Inn inn = manager.getInn(name);

        if (inn == null) {
            context.getSource().sendFailure(Component.literal("Auberge introuvable."));
            return 0;
        }

        if (inn.isSpawned()) {
            context.getSource().sendFailure(Component.literal("Cette auberge est déjà placée en " + inn.getLocation()));
            return 0;
        }

        // TODO: Vérifier que toutes les chambres sont définies ?
        
        BlockPos pos = BlockPos.containing(context.getSource().getPosition());
        inn.setLocation(pos);
        manager.setDirty();
        
        // TODO: Faire spawn le bloc ou le NPC ici si nécessaire
        // Pour l'instant on set juste la location
        
        context.getSource().sendSuccess(() -> Component.literal("Auberge '" + name + "' placée en " + pos), true);
        return 1;
    }

    private static int linkInn(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        String name = StringArgumentType.getString(context, "name");
        ServerPlayer player = context.getSource().getPlayerOrException();
        ServerLevel level = context.getSource().getLevel();
        InnManager manager = InnManager.get(level);
        Inn inn = manager.getInn(name);

        if (inn == null) {
            context.getSource().sendFailure(Component.literal("Auberge introuvable."));
            return 0;
        }
        
        // Vérification XP
        if (MedievalCoinsIntegration.getInnkeeperXP(player) < 0) {
            context.getSource().sendFailure(Component.literal("Vous n'avez pas le métier d'aubergiste."));
            return 0;
        }

        HitResult hit = player.pick(20.0D, 0.0F, false);
        if (hit.getType() == HitResult.Type.BLOCK) {
            BlockPos pos = ((BlockHitResult) hit).getBlockPos();
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof InnStructureBlockEntity innBe) {
                innBe.setInnName(name);
                inn.setLocation(pos); // On met à jour la location de l'auberge
                manager.setDirty();
                context.getSource().sendSuccess(() -> Component.literal("Bloc lié à l'auberge '" + name + "'."), true);
                return 1;
            } else {
                context.getSource().sendFailure(Component.literal("Ce n'est pas un bloc de structure d'auberge."));
                return 0;
            }
        } else {
            context.getSource().sendFailure(Component.literal("Vous devez regarder le bloc de structure."));
            return 0;
        }
    }

    private static int setOwner(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        String name = StringArgumentType.getString(context, "name");
        ServerPlayer player = EntityArgument.getPlayer(context, "player");
        ServerLevel level = context.getSource().getLevel();
        InnManager manager = InnManager.get(level);
        Inn inn = manager.getInn(name);

        if (inn == null) {
            context.getSource().sendFailure(Component.literal("Auberge introuvable."));
            return 0;
        }

        inn.setOwnerUUID(player.getUUID());
        manager.setDirty();
        context.getSource().sendSuccess(() -> Component.literal("Propriétaire de l'auberge '" + name + "' changé pour " + player.getName().getString()), true);
        return 1;
    }

    private static int rentRoom(CommandContext<CommandSourceStack> context, ServerPlayer player, boolean isPlayer) {
        String innName = StringArgumentType.getString(context, "name");
        int roomNumber = IntegerArgumentType.getInteger(context, "roomNumber");
        ServerLevel level = context.getSource().getLevel();
        InnManager manager = InnManager.get(level);
        Inn inn = manager.getInn(innName);

        if (inn == null) {
            context.getSource().sendFailure(Component.literal("Auberge introuvable."));
            return 0;
        }

        InnRoom room = inn.getRoom(roomNumber);
        if (room == null) {
            context.getSource().sendFailure(Component.literal("Chambre introuvable."));
            return 0;
        }

        if (room.isBusy()) {
            context.getSource().sendFailure(Component.literal("Cette chambre est déjà occupée."));
            return 0;
        }

        String occupantName;
        if (isPlayer) {
            if (player == null) {
                context.getSource().sendFailure(Component.literal("Joueur introuvable ou non connecté."));
                return 0;
            }
            occupantName = player.getName().getString();
            room.setOccupantUUID(player.getUUID());
        } else {
            occupantName = StringArgumentType.getString(context, "npcName");
            room.setOccupantUUID(null);
        }

        room.setBusy(true);
        room.setOccupantName(occupantName);
        
        // Renommer les coffres
        updateRoomChests(level, room, occupantName);
        
        manager.setDirty();
        
        context.getSource().sendSuccess(() -> Component.literal("Chambre " + roomNumber + " louée à " + occupantName + "."), true);
        return 1;
    }

    private static int leaveRoom(CommandContext<CommandSourceStack> context) {
        String innName = StringArgumentType.getString(context, "name");
        int roomNumber = IntegerArgumentType.getInteger(context, "roomNumber");
        ServerLevel level = context.getSource().getLevel();
        InnManager manager = InnManager.get(level);
        Inn inn = manager.getInn(innName);

        if (inn == null) {
            context.getSource().sendFailure(Component.literal("Auberge introuvable."));
            return 0;
        }

        InnRoom room = inn.getRoom(roomNumber);
        if (room == null) {
            context.getSource().sendFailure(Component.literal("Chambre introuvable."));
            return 0;
        }

        if (!room.isBusy()) {
            context.getSource().sendFailure(Component.literal("Cette chambre n'est pas occupée."));
            return 0;
        }

        String oldOccupant = room.getOccupantName();
        room.setBusy(false);
        room.setOccupantName("");
        room.setOccupantUUID(null);
        
        // Réinitialiser le nom des coffres
        updateRoomChests(level, room, "Chest");
        
        manager.setDirty();
        
        context.getSource().sendSuccess(() -> Component.literal("Chambre " + roomNumber + " libérée (était occupée par " + oldOccupant + ")."), true);
        return 1;
    }
    
    private static void updateRoomChests(ServerLevel level, InnRoom room, String name) {
        if (room.getP1() == null || room.getP2() == null) return;
        
        int minX = Math.min(room.getP1().getX(), room.getP2().getX());
        int minY = Math.min(room.getP1().getY(), room.getP2().getY());
        int minZ = Math.min(room.getP1().getZ(), room.getP2().getZ());
        int maxX = Math.max(room.getP1().getX(), room.getP2().getX());
        int maxY = Math.max(room.getP1().getY(), room.getP2().getY());
        int maxZ = Math.max(room.getP1().getZ(), room.getP2().getZ());

        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    BlockState state = level.getBlockState(pos);
                    if (state.getBlock() instanceof ChestBlock) {
                        BlockEntity be = level.getBlockEntity(pos);
                        if (be instanceof ChestBlockEntity chest) {
                            chest.setCustomName(Component.literal(name));
                            chest.setChanged();
                            level.sendBlockUpdated(pos, state, state, 3);
                        }
                    }
                }
            }
        }
    }

    private static int activateInn(CommandContext<CommandSourceStack> context) {
        String name = StringArgumentType.getString(context, "name");
        boolean active = BoolArgumentType.getBool(context, "active");
        ServerLevel level = context.getSource().getLevel();
        InnManager manager = InnManager.get(level);
        Inn inn = manager.getInn(name);

        if (inn == null) {
            context.getSource().sendFailure(Component.literal("Auberge introuvable."));
            return 0;
        }

        inn.setActive(active);
        manager.setDirty();
        context.getSource().sendSuccess(() -> Component.literal("Auberge '" + name + "' " + (active ? "activée" : "désactivée") + "."), true);
        return 1;
    }

    private static int startRoomCreation(CommandContext<CommandSourceStack> context) {
        try {
            String innName = StringArgumentType.getString(context, "innName");
            int number = IntegerArgumentType.getInteger(context, "number");
            int size = IntegerArgumentType.getInteger(context, "size");
            double price = DoubleArgumentType.getDouble(context, "price");
            ServerPlayer player = context.getSource().getPlayerOrException();
            
            ServerLevel level = context.getSource().getLevel();
            InnManager manager = InnManager.get(level);
            Inn inn = manager.getInn(innName);

            if (inn == null) {
                context.getSource().sendFailure(Component.literal("Auberge introuvable."));
                return 0;
            }

            if (inn.getRoom(number) != null) {
                context.getSource().sendFailure(Component.literal("La chambre " + number + " existe déjà."));
                return 0;
            }

            RoomCreationManager.getInstance().startCreation(player, innName, number, size, price);
            return 1;

        } catch (Exception e) {
            context.getSource().sendFailure(Component.literal("Erreur: " + e.getMessage()));
            return 0;
        }
    }

    private static int confirmRoomCreation(CommandContext<CommandSourceStack> context) {
        try {
            ServerPlayer player = context.getSource().getPlayerOrException();
            RoomCreationManager.getInstance().validate(player);
            return 1;
        } catch (Exception e) {
            context.getSource().sendFailure(Component.literal("Erreur: " + e.getMessage()));
            return 0;
        }
    }

    private static int removeRoom(CommandContext<CommandSourceStack> context) {
        String innName = StringArgumentType.getString(context, "innName");
        int number = IntegerArgumentType.getInteger(context, "number");
        
        ServerLevel level = context.getSource().getLevel();
        InnManager manager = InnManager.get(level);
        Inn inn = manager.getInn(innName);

        if (inn == null) {
            context.getSource().sendFailure(Component.literal("Auberge introuvable."));
            return 0;
        }

        if (inn.getRoom(number) == null) {
            context.getSource().sendFailure(Component.literal("La chambre " + number + " n'existe pas."));
            return 0;
        }

        inn.removeRoom(number);
        manager.setDirty();
        context.getSource().sendSuccess(() -> Component.literal("Chambre " + number + " supprimée."), true);
        return 1;
    }

    private static int listRooms(CommandContext<CommandSourceStack> context) {
        String innName = StringArgumentType.getString(context, "innName");
        ServerLevel level = context.getSource().getLevel();
        InnManager manager = InnManager.get(level);
        Inn inn = manager.getInn(innName);

        if (inn == null) {
            context.getSource().sendFailure(Component.literal("Auberge introuvable."));
            return 0;
        }

        context.getSource().sendSuccess(() -> Component.literal("--- Chambres de " + innName + " ---"), false);
        for (InnRoom room : inn.getRooms()) {
            context.getSource().sendSuccess(() -> Component.literal("- Chambre " + room.getNumber() + 
                    " (Prix: " + room.getPrice() + ", Occupée: " + room.isBusy() + ", Sale: " + room.isDirty() + ")"), false);
        }
        return 1;
    }

    private static int createProp(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        String innName = StringArgumentType.getString(context, "innName");
        int id = IntegerArgumentType.getInteger(context, "id");
        String type = StringArgumentType.getString(context, "type");
        BlockPos pos = BlockPosArgument.getLoadedBlockPos(context, "pos");

        ServerLevel level = context.getSource().getLevel();
        InnManager manager = InnManager.get(level);
        Inn inn = manager.getInn(innName);

        if (inn == null) {
            context.getSource().sendFailure(Component.literal("Auberge introuvable."));
            return 0;
        }

        if (!checkInside(level, inn, pos)) {
            context.getSource().sendFailure(Component.literal("La position du prop doit être à l'intérieur de la zone de l'auberge."));
            return 0;
        }
        
        // Vérifier si le prop est dans une chambre
        for (InnRoom room : inn.getRooms()) {
            if (room.isInside(pos)) {
                if (type.equals("chest")) {
                    context.getSource().sendFailure(Component.literal("Impossible de créer un prop 'chest' à l'intérieur d'une chambre."));
                    return 0;
                }
            }
        }
        
        inn.addProp(new InnProp(id, pos, type));
        
        // Renommer le coffre si c'est un prop de type chest
        if (type.equals("chest")) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof ChestBlockEntity chest) {
                chest.setCustomName(Component.literal("Coffre de l'Auberge"));
                chest.setChanged();
                level.sendBlockUpdated(pos, level.getBlockState(pos), level.getBlockState(pos), 3);
            }
        }

        manager.setDirty();
        context.getSource().sendSuccess(() -> Component.literal("Prop " + id + " (" + type + ") ajouté."), true);
        return 1;
    }

    private static int removeProp(CommandContext<CommandSourceStack> context) {
        String innName = StringArgumentType.getString(context, "innName");
        int id = IntegerArgumentType.getInteger(context, "id");

        ServerLevel level = context.getSource().getLevel();
        InnManager manager = InnManager.get(level);
        Inn inn = manager.getInn(innName);

        if (inn == null) {
            context.getSource().sendFailure(Component.literal("Auberge introuvable."));
            return 0;
        }

        inn.removeProp(id);
        manager.setDirty();
        context.getSource().sendSuccess(() -> Component.literal("Prop " + id + " supprimé."), true);
        return 1;
    }

    private static int listProps(CommandContext<CommandSourceStack> context) {
        String innName = StringArgumentType.getString(context, "innName");
        ServerLevel level = context.getSource().getLevel();
        InnManager manager = InnManager.get(level);
        Inn inn = manager.getInn(innName);

        if (inn == null) {
            context.getSource().sendFailure(Component.literal("Auberge introuvable."));
            return 0;
        }

        context.getSource().sendSuccess(() -> Component.literal("--- Props de " + innName + " ---"), false);
        for (InnProp prop : inn.getProps()) {
            context.getSource().sendSuccess(() -> Component.literal("- Prop " + prop.getId() + 
                    " (" + prop.getType() + ") en " + prop.getPos()), false);
        }
        return 1;
    }

    private static boolean checkInside(ServerLevel level, Inn inn, BlockPos pos) {
        if (inn.getLocation() == null) return false;
        BlockEntity be = level.getBlockEntity(inn.getLocation());
        if (be instanceof InnStructureBlockEntity innBe) {
            return innBe.isInside(pos);
        }
        return false;
    }
}
