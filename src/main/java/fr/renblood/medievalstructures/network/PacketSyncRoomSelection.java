package fr.renblood.medievalstructures.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class PacketSyncRoomSelection {
    private final BlockPos point1;
    private final BlockPos point2;

    public PacketSyncRoomSelection(BlockPos point1, BlockPos point2) {
        this.point1 = point1;
        this.point2 = point2;
    }

    public static void encode(PacketSyncRoomSelection msg, FriendlyByteBuf buf) {
        buf.writeBoolean(msg.point1 != null);
        if (msg.point1 != null) {
            buf.writeBlockPos(msg.point1);
        }
        buf.writeBoolean(msg.point2 != null);
        if (msg.point2 != null) {
            buf.writeBlockPos(msg.point2);
        }
    }

    public static PacketSyncRoomSelection decode(FriendlyByteBuf buf) {
        BlockPos p1 = buf.readBoolean() ? buf.readBlockPos() : null;
        BlockPos p2 = buf.readBoolean() ? buf.readBlockPos() : null;
        return new PacketSyncRoomSelection(p1, p2);
    }

    public static void handle(PacketSyncRoomSelection msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> 
                fr.renblood.medievalstructures.client.ClientPacketHandler.handleSyncRoomSelection(msg.point1, msg.point2)
            );
        });
        ctx.get().setPacketHandled(true);
    }
}
