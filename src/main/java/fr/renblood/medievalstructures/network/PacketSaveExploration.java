package fr.renblood.medievalstructures.network;

import fr.renblood.medievalstructures.block.entity.ExplorationHallBlockEntity;
import fr.renblood.medievalstructures.util.ExplorationUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

public class PacketSaveExploration {
    private final BlockPos pos;
    private final int duration;
    private final List<PacketExplorationAction.PlayerConfig> playerConfigs;

    public PacketSaveExploration(BlockPos pos, int duration, List<PacketExplorationAction.PlayerConfig> playerConfigs) {
        this.pos = pos;
        this.duration = duration;
        this.playerConfigs = playerConfigs;
    }

    public static void encode(PacketSaveExploration msg, FriendlyByteBuf buf) {
        buf.writeBlockPos(msg.pos);
        buf.writeInt(msg.duration);
        buf.writeInt(msg.playerConfigs.size());
        for (PacketExplorationAction.PlayerConfig config : msg.playerConfigs) {
            buf.writeInt(config.chestCount);
            buf.writeInt(config.animalCount);
            buf.writeUtf(config.playerName != null ? config.playerName : "");
            buf.writeBoolean(config.playerUUID != null);
            if (config.playerUUID != null) {
                buf.writeUUID(config.playerUUID);
            }
            buf.writeEnum(config.status);
        }
    }

    public static PacketSaveExploration decode(FriendlyByteBuf buf) {
        BlockPos pos = buf.readBlockPos();
        int duration = buf.readInt();
        int size = buf.readInt();
        List<PacketExplorationAction.PlayerConfig> configs = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            int chests = buf.readInt();
            int animals = buf.readInt();
            String name = buf.readUtf();
            UUID uuid = null;
            if (buf.readBoolean()) {
                uuid = buf.readUUID();
            }
            PacketExplorationAction.InvitationStatus status = buf.readEnum(PacketExplorationAction.InvitationStatus.class);
            configs.add(new PacketExplorationAction.PlayerConfig(chests, animals, name, uuid, status));
        }
        return new PacketSaveExploration(pos, duration, configs);
    }

    public static void handle(PacketSaveExploration msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player != null) {
                Level level = player.level();
                BlockEntity be = level.getBlockEntity(msg.pos);
                if (be instanceof ExplorationHallBlockEntity hall) {
                    // Mettre à jour le créateur (Joueur 1) comme ACCEPTED automatiquement
                    if (!msg.playerConfigs.isEmpty()) {
                        PacketExplorationAction.PlayerConfig creator = msg.playerConfigs.get(0);
                        creator.playerName = player.getName().getString();
                        creator.playerUUID = player.getUUID();
                        creator.status = PacketExplorationAction.InvitationStatus.ACCEPTED;
                    }
                    
                    // Gérer les invitations pour les autres joueurs
                    for (int i = 1; i < msg.playerConfigs.size(); i++) {
                        PacketExplorationAction.PlayerConfig config = msg.playerConfigs.get(i);
                        if (config.playerName != null && !config.playerName.isEmpty()) {
                            ServerPlayer target = player.server.getPlayerList().getPlayerByName(config.playerName);
                            if (target != null) {
                                config.playerUUID = target.getUUID();
                                config.status = PacketExplorationAction.InvitationStatus.PENDING;
                                sendInvitation(target, player.getName().getString(), msg.pos);
                            } else {
                                config.status = PacketExplorationAction.InvitationStatus.PENDING;
                            }
                        }
                    }

                    hall.saveExploration(msg.duration, msg.playerConfigs);
                    
                    // Renommer les coffres immédiatement
                    if (level instanceof ServerLevel serverLevel) {
                        ExplorationUtils.assignAndRenameChests(serverLevel, msg.pos, msg.playerConfigs);
                    }
                    
                    // Envoyer la confirmation au client pour mettre à jour le GUI
                    PacketHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> player), new PacketSyncExplorationState(true));
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
                        .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/exploration accept " + posStr))
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("Rejoindre l'exploration"))));
        
        Component decline = Component.literal("[REFUSER]")
                .withStyle(Style.EMPTY
                        .withColor(ChatFormatting.RED)
                        .withBold(true)
                        .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/exploration decline " + posStr))
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("Refuser l'invitation"))));
        
        target.sendSystemMessage(Component.literal("--- Invitation Exploration ---").withStyle(ChatFormatting.GOLD));
        target.sendSystemMessage(Component.literal(hostName + " vous invite à une exploration !"));
        target.sendSystemMessage(Component.literal(" ").append(accept).append("  ").append(decline));
    }
}
