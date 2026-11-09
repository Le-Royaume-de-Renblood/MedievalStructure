package fr.renblood.medievalstructures.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public record PacketSyncInnReferenceSuggestion(String itemId, int min, int max, boolean created) {
    public static void encode(PacketSyncInnReferenceSuggestion message, FriendlyByteBuf buffer) {
        buffer.writeUtf(message.itemId);
        buffer.writeInt(message.min);
        buffer.writeInt(message.max);
        buffer.writeBoolean(message.created);
    }

    public static PacketSyncInnReferenceSuggestion decode(FriendlyByteBuf buffer) {
        return new PacketSyncInnReferenceSuggestion(buffer.readUtf(), buffer.readInt(), buffer.readInt(), buffer.readBoolean());
    }

    public static void handle(PacketSyncInnReferenceSuggestion message, Supplier<NetworkEvent.Context> supplier) {
        supplier.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> fr.renblood.medievalstructures.client.ClientPacketHandler.handleReferenceSuggestion(
                        message.itemId, message.min, message.max, message.created)));
        supplier.get().setPacketHandled(true);
    }
}
