package fr.renblood.medievalstructures.network;

import fr.renblood.medievalstructures.inn.InnReferenceItem;
import fr.renblood.medievalstructures.integration.MedievalCoinsIntegration;
import fr.renblood.medievalstructures.integration.NpcShopkeeperIntegration;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.List;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

public class PacketRequestInnReferences {
    private final boolean forceRefresh;

    public PacketRequestInnReferences() {
        this(false);
    }

    public PacketRequestInnReferences(boolean forceRefresh) {
        this.forceRefresh = forceRefresh;
    }

    public static void encode(PacketRequestInnReferences message, FriendlyByteBuf buffer) {
        buffer.writeBoolean(message.forceRefresh);
    }

    public static PacketRequestInnReferences decode(FriendlyByteBuf buffer) {
        return new PacketRequestInnReferences(buffer.readBoolean());
    }

    public static void handle(PacketRequestInnReferences message, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null) return;
            if (message.forceRefresh) {
                player.sendSystemMessage(net.minecraft.network.chat.Component.literal("Actualisation du catalogue en cours..."));
                MedievalCoinsIntegration.reloadPriceReferences().whenComplete((success, failure) ->
                        player.getServer().execute(() -> {
                            sendReferences(player);
                            player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                                    failure == null && Boolean.TRUE.equals(success)
                                            ? "Catalogue actualise depuis MedievalCoins."
                                            : "Actualisation backend impossible, catalogue local conserve."));
                        }));
                return;
            }
            sendReferences(player);
        });
        context.setPacketHandled(true);
    }

    private static void sendReferences(ServerPlayer player) {
            Map<String, InnReferenceItem> referencesById = new LinkedHashMap<>();
            for (String itemId : NpcShopkeeperIntegration.getTradeItemIds()) {
                ResourceLocation id = ResourceLocation.tryParse(itemId);
                ItemStack stack = id != null && ForgeRegistries.ITEMS.containsKey(id)
                        ? new ItemStack(ForgeRegistries.ITEMS.getValue(id)) : ItemStack.EMPTY;
                String displayName = stack.isEmpty() ? itemId : stack.getHoverName().getString();
                long price = MedievalCoinsIntegration.getReferencePrice(itemId);
                referencesById.put(itemId, new InnReferenceItem(itemId, displayName,
                        stack.isEdible() ? "Aliment de trade" : "Item de trade", price));
            }
            for (InnReferenceItem reference : MedievalCoinsIntegration.getPriceReferences()) {
                referencesById.put(reference.itemId(), reference);
            }
            List<InnReferenceItem> references = new ArrayList<>(referencesById.values());
            references.sort(Comparator.comparing(InnReferenceItem::displayName, String.CASE_INSENSITIVE_ORDER));
            PacketHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> player),
                    new PacketSyncInnReferences(references));
    }

}
