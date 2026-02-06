package fr.renblood.medievalstructures.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import fr.renblood.medievalstructures.manager.DefinitionModeManager;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public class ModCommands {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("ms")
                .then(Commands.literal("pos1")
                        .executes(ModCommands::setPos1))
                .then(Commands.literal("pos2")
                        .executes(ModCommands::setPos2))
                .then(Commands.literal("validate")
                        .executes(ModCommands::validate)));
    }

    private static int setPos1(CommandContext<CommandSourceStack> context) {
        try {
            ServerPlayer player = context.getSource().getPlayerOrException();
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
}
