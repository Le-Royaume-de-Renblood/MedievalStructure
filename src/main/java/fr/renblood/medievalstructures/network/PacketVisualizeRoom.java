package fr.renblood.medievalstructures.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class PacketVisualizeRoom {
    private final BlockPos p1;
    private final BlockPos p2;
    private final int duration; // en ticks

    public PacketVisualizeRoom(BlockPos p1, BlockPos p2, int duration) {
        this.p1 = p1;
        this.p2 = p2;
        this.duration = duration;
    }

    public static void encode(PacketVisualizeRoom msg, FriendlyByteBuf buf) {
        buf.writeBlockPos(msg.p1);
        buf.writeBlockPos(msg.p2);
        buf.writeInt(msg.duration);
    }

    public static PacketVisualizeRoom decode(FriendlyByteBuf buf) {
        return new PacketVisualizeRoom(buf.readBlockPos(), buf.readBlockPos(), buf.readInt());
    }

    public static void handle(PacketVisualizeRoom msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> 
                fr.renblood.medievalstructures.client.ClientPacketHandler.handleVisualizeRoom(msg.p1, msg.p2, msg.duration)
            );
        });
        ctx.get().setPacketHandled(true);
    }
}
