package fr.renblood.medievalstructures.network;

import fr.renblood.medievalstructures.block.entity.ExplorationHallBlockEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class PacketResendInvitation {
    private final BlockPos pos;
    private final int playerIndex;

    public PacketResendInvitation(BlockPos pos, int playerIndex) {
        this.pos = pos;
        this.playerIndex = playerIndex;
    }

    public static void encode(PacketResendInvitation msg, FriendlyByteBuf buf) {
        buf.writeBlockPos(msg.pos);
        buf.writeInt(msg.playerIndex);
    }

    public static PacketResendInvitation decode(FriendlyByteBuf buf) {
        return new PacketResendInvitation(buf.readBlockPos(), buf.readInt());
    }

    public static void handle(PacketResendInvitation msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer sender = ctx.get().getSender();
            if (sender != null) {
                Level level = sender.level();
                BlockEntity be = level.getBlockEntity(msg.pos);
                if (be instanceof ExplorationHallBlockEntity hall) {
                    if (msg.playerIndex >= 0 && msg.playerIndex < hall.getSavedConfigs().size()) {
                        PacketExplorationAction.PlayerConfig config = hall.getSavedConfigs().get(msg.playerIndex);
                        
                        if (config.playerName != null && !config.playerName.isEmpty()) {
                            ServerPlayer target = sender.server.getPlayerList().getPlayerByName(config.playerName);
                            if (target != null) {
                                sendInvitation(target, sender.getName().getString(), msg.pos);
                                sender.sendSystemMessage(Component.literal("Invitation renvoyée à " + config.playerName).withStyle(ChatFormatting.GREEN));
                            } else {
                                sender.sendSystemMessage(Component.literal("Joueur " + config.playerName + " introuvable.").withStyle(ChatFormatting.RED));
                            }
                        }
                    }
                }
            }
        });
        ctx.get().setPacketHandled(true);
    }
    
    private static void sendInvitation(ServerPlayer target, String hostName, BlockPos pos) {
        String posStr = pos.getX() + " " + pos.getY() + " " + pos.getZ();
        
        Component accept = Component.literal("[ACCEPTER]")
                .withStyle(Style.EMPTY
                        .withColor(ChatFormatting.GREEN)
                        .withBold(true)
                        .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/ms exploration accept " + posStr))
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("Rejoindre l'exploration"))));
        
        Component decline = Component.literal("[REFUSER]")
                .withStyle(Style.EMPTY
                        .withColor(ChatFormatting.RED)
                        .withBold(true)
                        .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/ms exploration decline " + posStr))
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("Refuser l'invitation"))));
        
        target.sendSystemMessage(Component.literal("--- Invitation Exploration (Rappel) ---").withStyle(ChatFormatting.GOLD));
        target.sendSystemMessage(Component.literal(hostName + " vous invite à une exploration !"));
        target.sendSystemMessage(Component.literal(" ").append(accept).append("  ").append(decline));
    }
}
