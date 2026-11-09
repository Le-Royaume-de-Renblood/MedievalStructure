package fr.renblood.medievalstructures.network;

import fr.renblood.medievalstructures.integration.NpcShopkeeperIntegration;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.function.Supplier;

public record PacketRequestInnRoutes(String innName) {
    public static void encode(PacketRequestInnRoutes message, FriendlyByteBuf buffer) { buffer.writeUtf(message.innName); }
    public static PacketRequestInnRoutes decode(FriendlyByteBuf buffer) { return new PacketRequestInnRoutes(buffer.readUtf()); }
    public static void handle(PacketRequestInnRoutes message, Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null || !player.hasPermissions(2)) return;
            PacketHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> player),
                    new PacketSyncInnRoutes(message.innName, NpcShopkeeperIntegration.getRouteSummaries()));
        });
        context.setPacketHandled(true);
    }
}
