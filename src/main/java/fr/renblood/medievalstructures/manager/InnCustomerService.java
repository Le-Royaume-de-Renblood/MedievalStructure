package fr.renblood.medievalstructures.manager;

import fr.renblood.medievalstructures.inn.Inn;
import fr.renblood.medievalstructures.inn.InnManager;
import fr.renblood.medievalstructures.inn.InnMenuEntry;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

public final class InnCustomerService {
    private static final String INN_CUSTOMER_TAG = "RenbloodInnCustomer";
    private static final String INN_NAME_TAG = "RenbloodInnName";
    private static final String REQUEST_ITEM_ID_TAG = "RenbloodInnRequestItemId";
    private static final String REQUEST_ITEM_NAME_TAG = "RenbloodInnRequestItemName";
    private static final String REQUEST_PRICE_TAG = "RenbloodInnRequestPrice";
    private static final String FINISHED_TAG = "RenbloodInnCustomerFinished";

    private static final java.util.Set<java.util.UUID> SERVING = new java.util.HashSet<>();

    private InnCustomerService() {
    }

    public static boolean serveInnCustomer(ServerPlayer player, Entity npc) {
        if (player == null || npc == null || npc.level().isClientSide) return false;

        CompoundTag data = npc.getPersistentData();
        if (!data.getBoolean(INN_CUSTOMER_TAG)) return false;

        InnManager manager = InnManager.get(player.serverLevel());
        String innName = data.getString(INN_NAME_TAG);
        Inn inn = manager.getInn(innName);
        if (inn == null) {
            player.displayClientMessage(Component.literal("Cette demande n'est liee a aucune auberge active.").withStyle(ChatFormatting.RED), true);
            data.putBoolean(FINISHED_TAG, true);
            npc.discard();
            return true;
        }

        if (!canServe(player, inn)) {
            player.displayClientMessage(Component.literal("Seul le proprietaire, un employe ou un modo peut servir ce client.").withStyle(ChatFormatting.RED), true);
            return true;
        }
        if (!inn.canReceiveCustomers()) {
            player.displayClientMessage(Component.literal("L'auberge ne peut pas recevoir de clients actuellement.").withStyle(ChatFormatting.RED), true);
            data.putBoolean(FINISHED_TAG, true);
            npc.discard();
            return true;
        }

        String itemId = data.getString(REQUEST_ITEM_ID_TAG);
        InnMenuEntry entry = inn.getMenuEntry(itemId);
        if (entry == null) {
            player.displayClientMessage(Component.literal("Cet item n'est plus au menu.").withStyle(ChatFormatting.RED), true);
            data.putBoolean(FINISHED_TAG, true);
            npc.discard();
            return true;
        }

        if (data.getBoolean(FINISHED_TAG)) return true;
        if (!holdsRequestedItem(player, itemId)) {
            result(player, "Prends " + data.getString(REQUEST_ITEM_NAME_TAG) + " en main pour servir ce client.", false);
            return true;
        }
        long price = data.getLong(REQUEST_PRICE_TAG);
        if (price < 1 || !SERVING.add(npc.getUUID())) return true;
        String key="inn-customer:"+npc.getUUID();
        fr.renblood.medievalstructures.integration.CommerceAdapter.customer(player,key,
            fr.renblood.medievalstructures.integration.InnCommerceService.business(inn),inn.getOwnerUUID().toString(),inn.getName(),price,()->{
                if(npc.isRemoved()||data.getBoolean(FINISHED_TAG)||!canServe(player,inn)||!inn.canReceiveCustomers())return false;
                if (!holdsRequestedItem(player, itemId)) return false;
                player.getMainHandItem().shrink(1);
                player.getInventory().setChanged();
                inn.recordMenuSale(entry,price,player.getName().getString(),player.serverLevel().getGameTime());
                data.putBoolean(FINISHED_TAG, true);
                releaseCustomer(npc);
                npc.discard();
                manager.setDirty();
                return true;
            },()->manager.persist(player.serverLevel())).whenComplete((receipt,failure)->player.getServer().execute(()->{
                SERVING.remove(npc.getUUID());
                if (failure != null) {
                    Throwable cause = failure;
                    while (cause.getCause() != null) cause = cause.getCause();
                    String detail = String.valueOf(cause.getMessage());
                    String message = detail.contains("INSUFFICIENT_FUNDS") ? "La caisse des voyageurs est insuffisante."
                            : detail.contains("déjà en cours") ? "Une autre opération est en cours."
                            : "Service en échec. Vérifie le reçu dans Mes finances.";
                    result(player, message, false);
                } else if ("CONFIRMED".equals(receipt.get("state").getAsString())) {
                    result(player, "Plat servi. " + price + " fer versés à la caisse de l’auberge.", true);
                } else {
                    result(player, "Service annulé. Remets le plat en main et réessaie.", false);
                }
            }));
        return true;
    }

    private static boolean canServe(ServerPlayer player, Inn inn) {
        return player.hasPermissions(2)
                || player.getUUID().equals(inn.getOwnerUUID())
                || inn.isEmployee(player.getUUID());
    }

    private static boolean holdsRequestedItem(ServerPlayer player, String itemId) {
        ResourceLocation id = ResourceLocation.tryParse(itemId);
        Item item = id == null ? null : ForgeRegistries.ITEMS.getValue(id);
        ItemStack held = player.getMainHandItem();
        return item != null && !held.isEmpty() && held.is(item) && !held.hasTag();
    }

    private static void result(ServerPlayer player, String message, boolean success) {
        player.displayClientMessage(Component.literal(message)
                .withStyle(success ? ChatFormatting.GREEN : ChatFormatting.RED), true);
    }

    private static void releaseCustomer(Entity npc) {
        try {
            Class.forName("fr.renblood.npcshopkeeper.api.NpcShopkeeperAPI")
                    .getMethod("finishInnCustomer", Entity.class).invoke(null, npc);
        } catch (ReflectiveOperationException ignored) {
            // Older NPC versions can still serve the order.
        }
    }
}
