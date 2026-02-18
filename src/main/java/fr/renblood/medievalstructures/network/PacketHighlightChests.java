package fr.renblood.medievalstructures.network;

import fr.renblood.medievalstructures.client.renderer.ExplorationHighlightRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public class PacketHighlightChests {
    private final List<BlockPos> chestPositions;
    private final int requiredChests;

    public PacketHighlightChests(List<BlockPos> chestPositions, int requiredChests) {
        this.chestPositions = chestPositions;
        this.requiredChests = requiredChests;
    }

    public static void encode(PacketHighlightChests msg, FriendlyByteBuf buf) {
        buf.writeInt(msg.requiredChests);
        buf.writeInt(msg.chestPositions.size());
        for (BlockPos pos : msg.chestPositions) {
            buf.writeBlockPos(pos);
        }
    }

    public static PacketHighlightChests decode(FriendlyByteBuf buf) {
        int required = buf.readInt();
        int size = buf.readInt();
        List<BlockPos> list = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            list.add(buf.readBlockPos());
        }
        return new PacketHighlightChests(list, required);
    }

    public static void handle(PacketHighlightChests msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ExplorationHighlightRenderer.updateChests(msg.chestPositions, msg.requiredChests);
        });
        ctx.get().setPacketHandled(true);
    }
}
