package fr.renblood.medievalstructures.network;

import fr.renblood.medievalstructures.block.entity.ExplorationHallBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.function.Supplier;

public class PacketRemovePlayer {
    private final BlockPos pos;
    private final int playerIndex;

    public PacketRemovePlayer(BlockPos pos, int playerIndex) {
        this.pos = pos;
        this.playerIndex = playerIndex;
    }

    public static void encode(PacketRemovePlayer msg, FriendlyByteBuf buf) {
        buf.writeBlockPos(msg.pos);
        buf.writeInt(msg.playerIndex);
    }

    public static PacketRemovePlayer decode(FriendlyByteBuf buf) {
        return new PacketRemovePlayer(buf.readBlockPos(), buf.readInt());
    }

    public static void handle(PacketRemovePlayer msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player != null) {
                Level level = player.level();
                BlockEntity be = level.getBlockEntity(msg.pos);
                if (be instanceof ExplorationHallBlockEntity hall) {
                    hall.removePlayer(msg.playerIndex);
                    // Sync pour mettre à jour le GUI de tout le monde (si ouvert)
                    // Ici on sync juste avec celui qui a fait l'action pour l'instant
                    PacketHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> player), new PacketSyncExplorationState(true));
                }
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
