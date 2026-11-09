package fr.renblood.medievalstructures.network;

import fr.renblood.medievalstructures.inn.Inn;
import fr.renblood.medievalstructures.inn.InnManager;
import fr.renblood.medievalstructures.inn.InnMenuEntry;
import fr.renblood.medievalstructures.integration.MedievalCoinsIntegration;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.function.Supplier;

public class PacketInnMenuAction {
    public static final int ADD_FROM_HAND = 0;
    public static final int REMOVE = 1;
    public static final int MARKUP = 2;
    public static final int TYPE = 3;
    public static final int ADD_REFERENCE = 4;

    private final String innName;
    private final int action;
    private final String itemId;
    private final String value;

    public PacketInnMenuAction(String innName, int action, String itemId, String value) {
        this.innName = innName;
        this.action = action;
        this.itemId = itemId == null ? "" : itemId;
        this.value = value == null ? "" : value;
    }

    public static void encode(PacketInnMenuAction msg, FriendlyByteBuf buf) {
        buf.writeUtf(msg.innName);
        buf.writeInt(msg.action);
        buf.writeUtf(msg.itemId);
        buf.writeUtf(msg.value);
    }

    public static PacketInnMenuAction decode(FriendlyByteBuf buf) {
        return new PacketInnMenuAction(buf.readUtf(), buf.readInt(), buf.readUtf(), buf.readUtf());
    }

    public static void handle(PacketInnMenuAction msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;

            ServerLevel level = player.serverLevel();
            InnManager manager = InnManager.get(level);
            Inn inn = manager.getInn(msg.innName);
            if (inn == null || !canEdit(player, inn)) return;
            if (!inn.hasCustomerRoute()) {
                player.sendSystemMessage(Component.literal("Contactez un admin pour definir une route client avant d'editer le menu."));
                return;
            }

            boolean updated = switch (msg.action) {
                case ADD_FROM_HAND -> addFromHand(player, inn, msg.value);
                case REMOVE -> inn.removeMenuEntry(msg.itemId);
                case MARKUP -> updateMarkup(inn, msg.itemId, msg.value);
                case TYPE -> updateType(inn, msg.itemId, msg.value);
                case ADD_REFERENCE -> addReference(player, inn, msg.itemId, msg.value);
                default -> false;
            };

            if (updated) {
                manager.setDirty();
                PacketHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> player), new PacketSyncInnData(inn));
            }
        });
        ctx.get().setPacketHandled(true);
    }

    private static boolean canEdit(ServerPlayer player, Inn inn) {
        return player.hasPermissions(2)
                || player.getUUID().equals(inn.getOwnerUUID())
                || inn.isEmployee(player.getUUID());
    }

    private static boolean addFromHand(ServerPlayer player, Inn inn, String typeId) {
        ItemStack stack = player.getMainHandItem();
        if (stack.isEmpty()) {
            player.sendSystemMessage(Component.literal("Tenez une nourriture referencee en main."));
            return false;
        }
        if (!stack.isEdible()) {
            player.sendSystemMessage(Component.literal("Seuls les items de nourriture peuvent entrer dans le menu."));
            return false;
        }

        ResourceLocation id = ForgeRegistries.ITEMS.getKey(stack.getItem());
        if (id == null) return false;

        String itemId = id.toString();
        if (inn.getMenuEntry(itemId) != null) {
            player.sendSystemMessage(Component.literal("Cet item est deja dans le menu."));
            return false;
        }

        long referencePrice = MedievalCoinsIntegration.getReferencePrice(itemId);
        if (referencePrice <= 0L) {
            player.sendSystemMessage(Component.literal("Impossible: cet item n'a pas de prix de reference."));
            return false;
        }

        InnMenuEntry.Type type = InnMenuEntry.Type.fromId(typeId);
        inn.addMenuEntry(new InnMenuEntry(itemId, stack.getHoverName().getString(), referencePrice, type, 0));
        return true;
    }

    private static boolean addReference(ServerPlayer player, Inn inn, String itemId, String typeId) {
        if (inn.getMenuEntry(itemId) != null) {
            player.sendSystemMessage(Component.literal("Cet item est deja dans le menu."));
            return false;
        }
        ResourceLocation id = ResourceLocation.tryParse(itemId);
        if (id == null || !ForgeRegistries.ITEMS.containsKey(id)) return false;
        ItemStack stack = new ItemStack(ForgeRegistries.ITEMS.getValue(id));
        if (!stack.isEdible()) {
            player.sendSystemMessage(Component.literal("Seuls les items de nourriture peuvent entrer dans le menu."));
            return false;
        }
        long referencePrice = MedievalCoinsIntegration.getReferencePrice(itemId);
        if (referencePrice <= 0L) {
            player.sendSystemMessage(Component.literal("Le prix de reference de cet item n'est plus disponible."));
            return false;
        }
        inn.addMenuEntry(new InnMenuEntry(itemId, stack.getHoverName().getString(), referencePrice,
                InnMenuEntry.Type.fromId(typeId), 0));
        return true;
    }

    private static boolean updateMarkup(Inn inn, String itemId, String value) {
        InnMenuEntry entry = inn.getMenuEntry(itemId);
        if (entry == null) return false;
        int direction = "-".equals(value) ? -1 : 1;
        entry.addMarkupStep(direction);
        return true;
    }

    private static boolean updateType(Inn inn, String itemId, String value) {
        InnMenuEntry entry = inn.getMenuEntry(itemId);
        if (entry == null) return false;
        entry.setType(InnMenuEntry.Type.fromId(value));
        return true;
    }
}
