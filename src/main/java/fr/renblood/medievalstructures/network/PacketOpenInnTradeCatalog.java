package fr.renblood.medievalstructures.network;

import fr.renblood.medievalstructures.client.ClientPacketHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class PacketOpenInnTradeCatalog {
    public static void encode(PacketOpenInnTradeCatalog message, FriendlyByteBuf buffer) {
    }

    public static PacketOpenInnTradeCatalog decode(FriendlyByteBuf buffer) {
        return new PacketOpenInnTradeCatalog();
    }

    public static void handle(PacketOpenInnTradeCatalog message, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(ClientPacketHandler::openAdminTradeCatalog);
        context.setPacketHandled(true);
    }
}
