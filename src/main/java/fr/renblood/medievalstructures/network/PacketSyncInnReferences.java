package fr.renblood.medievalstructures.network;

import fr.renblood.medievalstructures.client.ClientPacketHandler;
import fr.renblood.medievalstructures.inn.InnReferenceItem;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.List;
import java.util.function.Supplier;

public record PacketSyncInnReferences(List<InnReferenceItem> references) {
    public static void encode(PacketSyncInnReferences message, FriendlyByteBuf buffer) {
        buffer.writeCollection(message.references, (target, reference) -> reference.write(target));
    }

    public static PacketSyncInnReferences decode(FriendlyByteBuf buffer) {
        return new PacketSyncInnReferences(buffer.readList(InnReferenceItem::read));
    }

    public static void handle(PacketSyncInnReferences message, Supplier<NetworkEvent.Context> contextSupplier) {
        contextSupplier.get().enqueueWork(() -> ClientPacketHandler.handleSyncInnReferences(message.references));
        contextSupplier.get().setPacketHandled(true);
    }
}
