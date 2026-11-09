package fr.renblood.medievalstructures.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.List;
import java.util.function.Supplier;

public record PacketSyncInnRoutes(String innName, List<String> routes) {
    public static void encode(PacketSyncInnRoutes message, FriendlyByteBuf buffer) {
        buffer.writeUtf(message.innName);
        buffer.writeCollection(message.routes, FriendlyByteBuf::writeUtf);
    }
    public static PacketSyncInnRoutes decode(FriendlyByteBuf buffer) {
        return new PacketSyncInnRoutes(buffer.readUtf(), buffer.readList(FriendlyByteBuf::readUtf));
    }
    public static void handle(PacketSyncInnRoutes message, Supplier<NetworkEvent.Context> supplier) {
        supplier.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> fr.renblood.medievalstructures.client.ClientPacketHandler.handleSyncInnRoutes(
                        message.innName, message.routes)));
        supplier.get().setPacketHandled(true);
    }
}
