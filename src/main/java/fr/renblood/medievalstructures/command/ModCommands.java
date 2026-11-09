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
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;
import fr.renblood.medievalstructures.block.entity.ExplorationHallBlockEntity;
import fr.renblood.medievalstructures.block.entity.InnStructureBlockEntity;
import fr.renblood.medievalstructures.init.ModDimensions;
import fr.renblood.medievalstructures.inn.Inn;
import fr.renblood.medievalstructures.inn.InnManager;
import fr.renblood.medievalstructures.inn.InnProp;
import fr.renblood.medievalstructures.inn.InnRoom;
import fr.renblood.medievalstructures.integration.MedievalCoinsIntegration;
import fr.renblood.medievalstructures.integration.NpcShopkeeperIntegration;
import fr.renblood.medievalstructures.manager.InnCustomerManager;
import fr.renblood.medievalstructures.manager.DefinitionModeManager;
import fr.renblood.medievalstructures.manager.ExplorationManager;
import fr.renblood.medievalstructures.manager.RoomCreationManager;
import fr.renblood.medievalstructures.network.PacketExplorationAction;
import fr.renblood.medievalstructures.network.PacketHandler;
import fr.renblood.medievalstructures.network.PacketOpenInnTradeCatalog;
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
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.network.PacketDistributor;


public class ModCommands {

    private static final SuggestionProvider<CommandSourceStack> SUGGEST_PROP_TYPES = (context, builder) -> {
        return SharedSuggestionProvider.suggest(new String[]{"candle", "chest", "flower_pot", "furnace"}, builder);
    };

    private static final SuggestionProvider<CommandSourceStack> SUGGEST_INN_NAMES = (context, builder) ->
            suggestNames(InnManager.get(context.getSource().getLevel()).getInns().stream().map(Inn::getName), builder);

    private static final SuggestionProvider<CommandSourceStack> SUGGEST_CUSTOMER_ROUTES = (context, builder) ->
            suggestNames(NpcShopkeeperIntegration.getRouteSelectors().stream(), builder);

    private static CompletableFuture<Suggestions> suggestNames(Stream<String> names, SuggestionsBuilder builder) {
        String remaining = builder.getRemainingLowerCase();
        String prefix = remaining.startsWith("\"") ? remaining.substring(1) : remaining;
        names.distinct().filter(name -> name.toLowerCase(Locale.ROOT).startsWith(prefix)).limit(50)
                .map(StringArgumentType::escapeIfRequired).forEach(builder::suggest);
        return builder.buildFuture();
    }

    private static CompletableFuture<Suggestions> suggestRooms(CommandContext<CommandSourceStack> context,
                                                                SuggestionsBuilder builder, String argument) {
        Inn inn = InnManager.get(context.getSource().getLevel()).getInn(StringArgumentType.getString(context, argument));
        return inn == null ? builder.buildFuture() : SharedSuggestionProvider.suggest(
                inn.getRooms().stream().map(room -> Integer.toString(room.getNumber())), builder);
    }

    private static CompletableFuture<Suggestions> suggestProps(CommandContext<CommandSourceStack> context,
                                                                SuggestionsBuilder builder) {
        Inn inn = InnManager.get(context.getSource().getLevel()).getInn(StringArgumentType.getString(context, "innName"));
        return inn == null ? builder.buildFuture() : SharedSuggestionProvider.suggest(
                inn.getProps().stream().map(prop -> Integer.toString(prop.getId())), builder);
    }

    private static int showRetainedChests(CommandContext<CommandSourceStack> context, int requestedPage) {
        CommandSourceStack source = context.getSource();
        var page = ExplorationManager.get(source.getLevel()).retainedChestPage(requestedPage);
        if (page.total() == 0) {
            source.sendSuccess(() -> Component.literal("Aucun coffre conservé signalé."), false);
            return 1;
        }
        source.sendSuccess(() -> Component.literal("Coffres conservés : " + page.total() + " — page "
                + page.number() + "/" + page.pages()).withStyle(net.minecraft.ChatFormatting.GOLD), false);
        for (var incident : page.incidents()) {
            BlockPos camp = BlockPos.of(incident.camp());
            BlockPos origin = BlockPos.of(incident.source());
            String position = camp.getX() + " " + camp.getY() + " " + camp.getZ();
            var line = Component.literal("Camp " + camp.toShortString() + " → origine " + origin.toShortString());
            line.append(Component.literal(" [Aller]").withStyle(style -> style.withColor(net.minecraft.ChatFormatting.AQUA)
                    .withClickEvent(new net.minecraft.network.chat.ClickEvent(net.minecraft.network.chat.ClickEvent.Action.SUGGEST_COMMAND,
                            "/execute in " + ModDimensions.EXPLORATION_LEVEL_KEY.location() + " run tp @s "
                                    + camp.getX() + " " + (camp.getY() + 1) + " " + camp.getZ()))));
            line.append(Component.literal(" [Clôturer]").withStyle(style -> style.withColor(net.minecraft.ChatFormatting.GREEN)
                    .withHoverEvent(new net.minecraft.network.chat.HoverEvent(net.minecraft.network.chat.HoverEvent.Action.SHOW_TEXT,
                            Component.literal("Récupère les objets, puis clôture cet incident. Le coffre chargé doit être vide ou retiré.")))
                    .withClickEvent(new net.minecraft.network.chat.ClickEvent(net.minecraft.network.chat.ClickEvent.Action.SUGGEST_COMMAND,
                            "/ms exploration incidents resolve " + position))));
            source.sendSuccess(() -> line, false);
        }
        if (page.number() < page.pages()) source.sendSuccess(() -> Component.literal("[Page suivante]")
                .withStyle(style -> style.withColor(net.minecraft.ChatFormatting.AQUA)
                        .withClickEvent(new net.minecraft.network.chat.ClickEvent(net.minecraft.network.chat.ClickEvent.Action.RUN_COMMAND,
                                "/ms exploration incidents page " + (page.number() + 1)))), false);
        return 1;
    }

    private static int resolveRetainedChest(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        BlockPos camp = BlockPosArgument.getBlockPos(context, "camp");
        var result = ExplorationManager.get(source.getLevel()).resolveRetainedChest(source.getServer(), camp);
        switch (result) {
            case RESOLVED -> source.sendSuccess(() -> Component.literal("Incident clôturé en " + camp.toShortString()
                    + ". La zone du camp est libérée.").withStyle(net.minecraft.ChatFormatting.GREEN), true);
            case NOT_FOUND -> source.sendFailure(Component.literal("Aucun incident enregistré à ces coordonnées de camp."));
            case UNLOADED -> source.sendFailure(Component.literal("Le camp n’est pas chargé. Utilise [Aller], puis récupère le contenu du coffre."));
            case NOT_EMPTY -> source.sendFailure(Component.literal("Le coffre contient encore des objets. Récupère-les avant de clôturer l’incident."));
        }
        return result == fr.renblood.medievalstructures.manager.RetainedChestRegistry.Resolution.RESOLVED ? 1 : 0;
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        LiteralArgumentBuilder<CommandSourceStack> explorationCommand = Commands.literal("exploration")
                .then(Commands.literal("tuto").executes(context -> {
                    var source = context.getSource();
                    source.sendSuccess(() -> Component.literal("Exploration — les repères").withStyle(net.minecraft.ChatFormatting.GOLD), false);
                    source.sendSuccess(() -> Component.literal("1. Prépare ton groupe au hall et réponds à l’invitation."), false);
                    source.sendSuccess(() -> Component.literal("2. Garde libre le coffre d’origine pour permettre le retour du contenu du camp."), false);
                    source.sendSuccess(() -> Component.literal("3. Si un coffre reste au camp après le retour, contacte l’équipe pour récupérer son contenu."), false);
                    if (source.hasPermission(2)) source.sendSuccess(() -> Component.literal(
                            "Équipe : /ms exploration incidents. Récupère les objets, puis utilise resolve <x> <y> <z>."), false);
                    return 1;
                }))
                .then(Commands.literal("join")
                        .requires(s -> s.hasPermission(2)) // Admin only pour join direct
                        .executes(ModCommands::joinExploration))
                .then(Commands.literal("incidents")
                        .requires(s -> s.hasPermission(2))
                        .executes(context -> showRetainedChests(context, 1))
                        .then(Commands.literal("page")
                                .then(Commands.argument("page", IntegerArgumentType.integer(1))
                                        .executes(context -> showRetainedChests(context, IntegerArgumentType.getInteger(context, "page")))))
                        .then(Commands.literal("resolve")
                                .then(Commands.argument("camp", BlockPosArgument.blockPos())
                                        .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                                                ExplorationManager.get(context.getSource().getLevel()).retainedChestPositions().stream()
                                                        .map(pos -> pos.getX() + " " + pos.getY() + " " + pos.getZ()), builder))
                                        .executes(ModCommands::resolveRetainedChest))))
                .then(Commands.literal("debug")
                        .requires(s -> s.hasPermission(2))
                        .then(Commands.argument("enabled", BoolArgumentType.bool())
                                .executes(ModCommands::setExplorationDebug)))
                .then(Commands.literal("accept")
                        .then(Commands.argument("pos", BlockPosArgument.blockPos())
                                .executes(ctx -> respondInvitation(ctx, PacketExplorationAction.InvitationStatus.ACCEPTED))))
                .then(Commands.literal("decline")
                        .then(Commands.argument("pos", BlockPosArgument.blockPos())
                                .executes(ctx -> respondInvitation(ctx, PacketExplorationAction.InvitationStatus.DECLINED))));

        // Commandes /ms
        LiteralArgumentBuilder<CommandSourceStack> msCommand = Commands.literal("ms")
                .then(Commands.literal("pos1")
                        .requires(s -> s.hasPermission(2))
                        .executes(ModCommands::setPos1))
                .then(Commands.literal("pos2")
                        .requires(s -> s.hasPermission(2))
                        .executes(ModCommands::setPos2))
                .then(Commands.literal("validate")
                        .requires(s -> s.hasPermission(2))
                        .executes(ModCommands::validate))
                .then(explorationCommand);

        // Commandes /ms auberge (Admin)
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

        aubergeCommand.then(Commands.literal("status")
                .then(Commands.argument("name", StringArgumentType.string()).suggests(SUGGEST_INN_NAMES)
                        .executes(context -> {
                            String name = StringArgumentType.getString(context, "name");
                            ServerLevel level = context.getSource().getLevel();
                            Inn inn = InnManager.get(level).getInn(name);
                            if (inn == null) {
                                context.getSource().sendFailure(Component.literal("Auberge introuvable."));
                                return 0;
                            }
                            context.getSource().sendSuccess(() -> Component.literal(InnCustomerManager.status(inn, level)), false);
                            return 1;
                        })));

        // tuto
        aubergeCommand.then(Commands.literal("tuto")
                .executes(ModCommands::showInnTutorial));

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
                        .then(Commands.literal("list")
                                .executes(ModCommands::listRentableRooms))
                        .then(Commands.argument("roomNumber", IntegerArgumentType.integer(1))
                                .suggests((context, builder) -> suggestRooms(context, builder, "name"))
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
                                .suggests((context, builder) -> suggestRooms(context, builder, "name"))
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
                                        .suggests((context, builder) -> suggestRooms(context, builder, "innName"))
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
                                        .suggests(ModCommands::suggestProps)
                                .executes(ModCommands::removeProp))))
                .then(Commands.literal("list")
                        .then(Commands.argument("innName", StringArgumentType.string()).suggests(SUGGEST_INN_NAMES)
                                .executes(ModCommands::listProps))));

        aubergeCommand.then(Commands.literal("route")
                .then(Commands.literal("list")
                        .executes(ModCommands::listCustomerRoutes))
                .then(Commands.literal("set")
                        .then(Commands.argument("innName", StringArgumentType.string()).suggests(SUGGEST_INN_NAMES)
                                .then(Commands.argument("routeId", StringArgumentType.string()).suggests(SUGGEST_CUSTOMER_ROUTES)
                                        .executes(ModCommands::setCustomerRoute))))
                .then(Commands.literal("clear")
                        .then(Commands.argument("innName", StringArgumentType.string()).suggests(SUGGEST_INN_NAMES)
                                .executes(ModCommands::clearCustomerRoute)))
                .then(Commands.literal("timing")
                        .then(Commands.argument("innName", StringArgumentType.string()).suggests(SUGGEST_INN_NAMES)
                                .then(Commands.argument("minSeconds", IntegerArgumentType.integer(10))
                                        .then(Commands.argument("maxSeconds", IntegerArgumentType.integer(10))
                                                .executes(ModCommands::setCustomerRouteTiming))))));

        aubergeCommand.then(Commands.literal("menu")
                .then(Commands.literal("catalogue")
                        .executes(ModCommands::openAdminTradeCatalog)
                        .then(Commands.literal("refresh")
                                .executes(ModCommands::refreshAdminTradeCatalog)))
                .then(Commands.literal("stats")
                        .then(Commands.argument("innName", StringArgumentType.string()).suggests(SUGGEST_INN_NAMES)
                                .executes(ModCommands::showInnMenuStats))));

        msCommand.then(aubergeCommand);
        dispatcher.register(msCommand);
    }

    private static int respondInvitation(CommandContext<CommandSourceStack> context, PacketExplorationAction.InvitationStatus status) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        BlockPos pos = BlockPosArgument.getLoadedBlockPos(context, "pos");
        ServerLevel level = context.getSource().getLevel();
        
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof ExplorationHallBlockEntity hall) {
            hall.updatePlayerStatus(player.getUUID(), status);
            
            if (status == PacketExplorationAction.InvitationStatus.ACCEPTED) {
                context.getSource().sendSuccess(() -> Component.literal("Invitation acceptée !").withStyle(net.minecraft.ChatFormatting.GREEN), true);
            } else {
                context.getSource().sendSuccess(() -> Component.literal("Invitation refusée.").withStyle(net.minecraft.ChatFormatting.RED), true);
                // Optionnel : Supprimer le joueur de la liste s'il refuse ?
                // hall.removePlayer(player.getUUID()); // Il faudrait implémenter removePlayer par UUID
            }
            return 1;
        } else {
            context.getSource().sendFailure(Component.literal("Exploration introuvable ou expirée."));
            return 0;
        }
    }

    private static int joinExploration(CommandContext<CommandSourceStack> context) {
        try {
            ServerPlayer player = context.getSource().getPlayerOrException();
            ServerLevel explorationWorld = context.getSource().getServer().getLevel(ModDimensions.EXPLORATION_LEVEL_KEY);
            
            if (explorationWorld == null) {
                context.getSource().sendFailure(Component.literal("Le monde d'exploration n'est pas chargé."));
                return 0;
            }
            
            ExplorationManager manager = ExplorationManager.get(context.getSource().getServer().overworld());
            if (!manager.joinLatest(player)) {
                 context.getSource().sendFailure(Component.literal("Aucune exploration active à rejoindre."));
                 return 0;
            }
            
            context.getSource().sendSuccess(() -> Component.literal("Téléporté au monde d'exploration."), true);
            return 1;
        } catch (Exception e) {
            context.getSource().sendFailure(Component.literal("Erreur: " + e.getMessage()));
            return 0;
        }
    }
    
    private static int setExplorationDebug(CommandContext<CommandSourceStack> context) {
        boolean enabled = BoolArgumentType.getBool(context, "enabled");
        ExplorationManager.DEBUG_MODE = enabled;
        context.getSource().sendSuccess(() -> Component.literal("Mode Debug Exploration " + (enabled ? "ACTIVÉ (1 min)" : "DÉSACTIVÉ")), true);
        return 1;
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

    // --- Nouvelles methodes pour /ms auberge ---

    private static int showInnTutorial(CommandContext<CommandSourceStack> context) {
        context.getSource().sendSuccess(() -> Component.literal("--- Tuto Auberge ---").withStyle(net.minecraft.ChatFormatting.GOLD), false);
        context.getSource().sendSuccess(() -> Component.literal("1. /ms auberge create <nom> <maxChambres> <propsParChambre>"), false);
        context.getSource().sendSuccess(() -> Component.literal("2. Place le bloc auberge, regarde-le, puis /ms auberge link <nom>."), false);
        context.getSource().sendSuccess(() -> Component.literal("3. Zone globale: baton aubergiste pour les 2 coins, puis /ms validate."), false);
        context.getSource().sendSuccess(() -> Component.literal("4. Chambre: /ms auberge room create <auberge> <numero> <taille> <prix>."), false);
        context.getSource().sendSuccess(() -> Component.literal("5. Selectionne les 2 coins de la chambre au baton, puis /ms auberge room confirm."), false);
        context.getSource().sendSuccess(() -> Component.literal("6. Interface du bloc: Chambres pour voir/exclure, Maintenance pour localiser, Employes pour deleguer."), false);
        return 1;
    }

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

        context.getSource().sendSuccess(() -> Component.literal("--- Liste des auberges ---").withStyle(net.minecraft.ChatFormatting.GOLD), false);
        for (Inn inn : manager.getInns()) {
            long freeRooms = inn.getRooms().stream().filter(room -> !room.isBusy() && !room.isDirty()).count();
            long rentedRooms = inn.getRooms().stream().filter(InnRoom::isBusy).count();
            long dirtyProps = inn.getProps().stream().filter(prop -> !prop.isActive()).count();
            String location = inn.getLocation() == null ? "non liee" : inn.getLocation().toShortString();
            String status = inn.isActive() ? "active" : "inactive";

            context.getSource().sendSuccess(() -> Component.literal("- " + inn.getName()
                    + " | " + status
                    + " | chambres: " + inn.getRooms().size() + "/" + inn.getMaxRooms()
                    + " (" + freeRooms + " libres, " + rentedRooms + " louees)"
                    + " | props: " + dirtyProps + "/" + inn.getProps().size() + " a faire"
                    + " | pos: " + location), false);
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

    private static int listRentableRooms(CommandContext<CommandSourceStack> context) {
        String innName = StringArgumentType.getString(context, "name");
        ServerLevel level = context.getSource().getLevel();
        InnManager manager = InnManager.get(level);
        Inn inn = manager.getInn(innName);

        if (inn == null) {
            context.getSource().sendFailure(Component.literal("Auberge introuvable."));
            return 0;
        }

        context.getSource().sendSuccess(() -> Component.literal("--- Chambres disponibles : " + inn.getName() + " ---").withStyle(net.minecraft.ChatFormatting.GOLD), false);
        int count = 0;
        for (InnRoom room : inn.getRooms()) {
            if (!room.isBusy() && !room.isDirty()) {
                count++;
                context.getSource().sendSuccess(() -> Component.literal("- Chambre " + room.getNumber()
                        + " | taille " + room.getSize()
                        + " | prix " + (int) room.getPrice() + " pieces/jour"), false);
            }
        }

        if (count == 0) {
            context.getSource().sendSuccess(() -> Component.literal("Aucune chambre disponible."), false);
        }
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

    private static int openAdminTradeCatalog(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        PacketHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> player), new PacketOpenInnTradeCatalog());
        return 1;
    }

    private static int refreshAdminTradeCatalog(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        context.getSource().sendSuccess(() -> Component.literal("Actualisation du catalogue MedievalCoins en cours..."), false);
        MedievalCoinsIntegration.reloadPriceReferences().whenComplete((success, failure) -> player.getServer().execute(() -> {
            if (failure == null && Boolean.TRUE.equals(success)) {
                context.getSource().sendSuccess(() -> Component.literal("Catalogue actualise."), false);
                PacketHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> player), new PacketOpenInnTradeCatalog());
            } else {
                context.getSource().sendFailure(Component.literal("Impossible d'actualiser le catalogue depuis le backend."));
            }
        }));
        return 1;
    }

    private static int setCustomerRoute(CommandContext<CommandSourceStack> context) {
        String innName = StringArgumentType.getString(context, "innName");
        String routeId = StringArgumentType.getString(context, "routeId");
        ServerLevel level = context.getSource().getLevel();
        InnManager manager = InnManager.get(level);
        Inn inn = manager.getInn(innName);

        if (inn == null) {
            context.getSource().sendFailure(Component.literal("Auberge introuvable."));
            return 0;
        }
        String canonicalRouteId = NpcShopkeeperIntegration.resolveRouteId(routeId);
        if (canonicalRouteId.isBlank()) {
            context.getSource().sendFailure(Component.literal("Route NpcShopkeeper introuvable: " + routeId));
            return 0;
        }

        inn.setCustomerRouteId(canonicalRouteId);
        InnCustomerManager.clearSchedule(innName);
        manager.setDirty();
        context.getSource().sendSuccess(() -> Component.literal("Route client liee a l'auberge '" + innName + "': " + routeId), true);
        return 1;
    }

    private static int listCustomerRoutes(CommandContext<CommandSourceStack> context) {
        java.util.List<String> routes = NpcShopkeeperIntegration.getRouteSummaries();
        if (routes.isEmpty()) {
            context.getSource().sendFailure(Component.literal("Aucune route NpcShopkeeper disponible."));
            return 0;
        }
        context.getSource().sendSuccess(() -> Component.literal("--- Routes NpcShopkeeper disponibles ---"), false);
        for (String route : routes) {
            context.getSource().sendSuccess(() -> Component.literal("- " + route), false);
        }
        return routes.size();
    }

    private static int clearCustomerRoute(CommandContext<CommandSourceStack> context) {
        String innName = StringArgumentType.getString(context, "innName");
        ServerLevel level = context.getSource().getLevel();
        InnManager manager = InnManager.get(level);
        Inn inn = manager.getInn(innName);

        if (inn == null) {
            context.getSource().sendFailure(Component.literal("Auberge introuvable."));
            return 0;
        }

        inn.setCustomerRouteId("");
        NpcShopkeeperIntegration.clearInnCustomers(level, innName);
        InnCustomerManager.clearSchedule(innName);
        manager.setDirty();
        context.getSource().sendSuccess(() -> Component.literal("Route client retiree de l'auberge '" + innName + "'."), true);
        return 1;
    }

    private static int setCustomerRouteTiming(CommandContext<CommandSourceStack> context) {
        String innName = StringArgumentType.getString(context, "innName");
        int minSeconds = IntegerArgumentType.getInteger(context, "minSeconds");
        int maxSeconds = IntegerArgumentType.getInteger(context, "maxSeconds");
        ServerLevel level = context.getSource().getLevel();
        InnManager manager = InnManager.get(level);
        Inn inn = manager.getInn(innName);

        if (inn == null) {
            context.getSource().sendFailure(Component.literal("Auberge introuvable."));
            return 0;
        }
        if (maxSeconds < minSeconds) {
            context.getSource().sendFailure(Component.literal("Le timing max doit etre superieur ou egal au min."));
            return 0;
        }

        inn.setCustomerRouteTiming(minSeconds, maxSeconds);
        InnCustomerManager.clearSchedule(innName);
        manager.setDirty();
        context.getSource().sendSuccess(() -> Component.literal("Timing client de '" + innName + "': " + minSeconds + "-" + maxSeconds + " secondes."), true);
        return 1;
    }

    private static int showInnMenuStats(CommandContext<CommandSourceStack> context) {
        String innName = StringArgumentType.getString(context, "innName");
        ServerLevel level = context.getSource().getLevel();
        InnManager manager = InnManager.get(level);
        Inn inn = manager.getInn(innName);

        if (inn == null) {
            context.getSource().sendFailure(Component.literal("Auberge introuvable."));
            return 0;
        }

        context.getSource().sendSuccess(() -> Component.literal("--- Menu auberge: " + innName + " ---"), false);
        context.getSource().sendSuccess(() -> Component.literal("Route: " + (inn.hasCustomerRoute() ? inn.getCustomerRouteId() : "manquante")), false);
        context.getSource().sendSuccess(() -> Component.literal("Timing: " + inn.getCustomerRouteMinSeconds() + "-" + inn.getCustomerRouteMaxSeconds() + " secondes"), false);
        context.getSource().sendSuccess(() -> Component.literal("Disponibilite: " + (inn.canReceiveCustomers() ? "clients actifs" : "clients bloques")), false);
        context.getSource().sendSuccess(() -> Component.literal("Menu: "
                + inn.countMenuType(fr.renblood.medievalstructures.inn.InnMenuEntry.Type.DISH) + "/4 plats, "
                + inn.countMenuType(fr.renblood.medievalstructures.inn.InnMenuEntry.Type.DRINK) + "/2 boissons, "
                + inn.countMenuType(fr.renblood.medievalstructures.inn.InnMenuEntry.Type.STARTER) + "/2 starters"), false);
        context.getSource().sendSuccess(() -> Component.literal("Ventes: " + inn.getTotalMenuSales() + " | CA menu: " + inn.getTotalMenuRevenue() + " pieces"), false);
        if (!inn.getLastMenuSaleItem().isBlank()) {
            context.getSource().sendSuccess(() -> Component.literal("Derniere vente: " + inn.getLastMenuSaleItem() + " (" + inn.getLastMenuSalePrice() + " pieces)"), false);
        }
        if (!inn.getRecentMenuSales().isEmpty()) {
            context.getSource().sendSuccess(() -> Component.literal("Dernieres ventes:"), false);
            inn.getRecentMenuSales().stream().limit(5).forEach(sale ->
                    context.getSource().sendSuccess(() -> Component.literal("- " + sale.itemName()
                            + " | " + sale.price() + " pieces | servi par " + sale.sellerName()), false));
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
