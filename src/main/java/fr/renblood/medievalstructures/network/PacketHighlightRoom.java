package fr.renblood.medievalstructures.network;

import fr.renblood.medievalstructures.client.ClientPacketHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public class PacketHighlightRoom {
    private final List<BlockPos> propPositions;

    public PacketHighlightRoom(List<BlockPos> propPositions) {
        this.propPositions = propPositions;
    }

    public static void encode(PacketHighlightRoom msg, FriendlyByteBuf buf) {
        buf.writeInt(msg.propPositions.size());
        for (BlockPos pos : msg.propPositions) {
            buf.writeBlockPos(pos);
        }
    }

    public static PacketHighlightRoom decode(FriendlyByteBuf buf) {
        int size = buf.readInt();
        List<BlockPos> positions = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            positions.add(buf.readBlockPos());
        }
        return new PacketHighlightRoom(positions);
    }

    public static void handle(PacketHighlightRoom msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> 
                ClientPacketHandler.handleHighlightRoom(msg.propPositions)
            );
        });
        ctx.get().setPacketHandled(true);
    }
}
