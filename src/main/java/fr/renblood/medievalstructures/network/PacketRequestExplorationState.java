package fr.renblood.medievalstructures.network;

import fr.renblood.medievalstructures.block.entity.ExplorationHallBlockEntity;
import fr.renblood.medievalstructures.manager.ExplorationManager;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.function.Supplier;

public class PacketRequestExplorationState {
    private final BlockPos pos;

    public PacketRequestExplorationState(BlockPos pos) {
        this.pos = pos;
    }

    public static void encode(PacketRequestExplorationState msg, FriendlyByteBuf buf) {
        buf.writeBlockPos(msg.pos);
    }

    public static PacketRequestExplorationState decode(FriendlyByteBuf buf) {
        return new PacketRequestExplorationState(buf.readBlockPos());
    }

    public static void handle(PacketRequestExplorationState msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player != null) {
                BlockEntity be = player.level().getBlockEntity(msg.pos);
                boolean hasExploration = false;
                if (be instanceof ExplorationHallBlockEntity hall) {
                    hasExploration = hall.hasExplorationReady();
                }
                
                PacketHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> player), 
                        new PacketSyncExplorationState(hasExploration, ExplorationManager.DEBUG_MODE));
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
