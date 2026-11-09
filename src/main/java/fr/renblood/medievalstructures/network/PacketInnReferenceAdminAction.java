package fr.renblood.medievalstructures.network;

import fr.renblood.medievalstructures.integration.NpcShopkeeperIntegration;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.function.Supplier;

public record PacketInnReferenceAdminAction(String itemId, int min, int max, boolean sendToBackend, boolean create) {
    public static void encode(PacketInnReferenceAdminAction message, FriendlyByteBuf buffer) {
        buffer.writeUtf(message.itemId);
        buffer.writeInt(message.min);
        buffer.writeInt(message.max);
        buffer.writeBoolean(message.sendToBackend);
        buffer.writeBoolean(message.create);
    }

    public static PacketInnReferenceAdminAction decode(FriendlyByteBuf buffer) {
        return new PacketInnReferenceAdminAction(buffer.readUtf(), buffer.readInt(), buffer.readInt(),
                buffer.readBoolean(), buffer.readBoolean());
    }

    public static void handle(PacketInnReferenceAdminAction message, Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null || !player.hasPermissions(2)) return;
            if (message.create) {
                boolean success = NpcShopkeeperIntegration.createTradePriceReference(player, message.itemId,
                        message.min, message.max, message.sendToBackend);
                PacketHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> player),
                        new PacketSyncInnReferenceSuggestion(message.itemId, message.min, message.max, success));
            } else {
                long[] range = NpcShopkeeperIntegration.suggestTradeReferenceRange(player, message.itemId);
                PacketHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> player),
                        new PacketSyncInnReferenceSuggestion(message.itemId, (int) range[0], (int) range[1], false));
            }
        });
        context.setPacketHandled(true);
    }
}
