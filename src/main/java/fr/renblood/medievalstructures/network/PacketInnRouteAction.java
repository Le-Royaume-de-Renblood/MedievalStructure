package fr.renblood.medievalstructures.network;

import fr.renblood.medievalstructures.inn.Inn;
import fr.renblood.medievalstructures.inn.InnManager;
import fr.renblood.medievalstructures.integration.NpcShopkeeperIntegration;
import fr.renblood.medievalstructures.manager.InnCustomerManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.function.Supplier;

public record PacketInnRouteAction(String innName, String selector) {
    public static void encode(PacketInnRouteAction message, FriendlyByteBuf buffer) {
        buffer.writeUtf(message.innName);
        buffer.writeUtf(message.selector);
    }
    public static PacketInnRouteAction decode(FriendlyByteBuf buffer) {
        return new PacketInnRouteAction(buffer.readUtf(), buffer.readUtf());
    }
    public static void handle(PacketInnRouteAction message, Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null || !player.hasPermissions(2)) return;
            InnManager manager = InnManager.get(player.serverLevel());
            Inn inn = manager.getInn(message.innName);
            if (inn == null) return;
            if (message.selector.isBlank()) {
                inn.setCustomerRouteId("");
                NpcShopkeeperIntegration.clearInnCustomers(player.serverLevel(), inn.getName());
            } else {
                String routeId = NpcShopkeeperIntegration.resolveRouteId(message.selector);
                if (routeId.isBlank()) return;
                inn.setCustomerRouteId(routeId);
            }
            InnCustomerManager.clearSchedule(inn.getName());
            manager.setDirty();
            PacketHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> player), new PacketSyncInnData(inn));
        });
        context.setPacketHandled(true);
    }
}
