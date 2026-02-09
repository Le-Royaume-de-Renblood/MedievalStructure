package fr.renblood.medievalstructures.integration;

import fr.renblood.medievalcoins.api.MedievalCoinsAPI;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

public class MedievalCoinsIntegration {

    public static void addMoney(ServerPlayer player, double amount) {
        MedievalCoinsAPI.addMoney(player, (int) amount);
    }

    public static boolean removeMoney(ServerPlayer player, double amount) {
        double balance = MedievalCoinsAPI.getBalance(player);
        if (balance >= amount) {
            MedievalCoinsAPI.removeMoney(player, (int) amount);
            return true;
        }
        return false;
    }

    public static int getInnkeeperXP(Player player) {
        if (player instanceof ServerPlayer serverPlayer) {
            return (int) MedievalCoinsAPI.getJobXp(serverPlayer, "innkeeper");
        }
        return 0;
    }

    public static void addInnkeeperXP(ServerPlayer player, double amount) {
        int baseAmount = (int) amount;
        double chance = amount - baseAmount;
        if (Math.random() < chance) {
            baseAmount++;
        }
        if (baseAmount > 0) {
            MedievalCoinsAPI.addJobXp(player, "innkeeper", baseAmount);
        }
    }

    public static void removeInnkeeperXP(ServerPlayer player, int amount) {
        MedievalCoinsAPI.removeJobXp(player, "innkeeper", amount);
    }

    public static void giveCoins(ServerPlayer player, int amount) {
        // 64 iron = 1 bronze
        // 64 bronze = 1 silver
        // 64 silver = 1 gold

        int gold = amount / (64 * 64 * 64);
        amount %= (64 * 64 * 64);

        int silver = amount / (64 * 64);
        amount %= (64 * 64);

        int bronze = amount / 64;
        int iron = amount % 64;

        giveStack(player, "gold_coin", gold);
        giveStack(player, "silver_coin", silver);
        giveStack(player, "bronze_coin", bronze);
        giveStack(player, "iron_coin", iron);
    }

    private static void giveStack(ServerPlayer player, String coinName, int count) {
        if (count <= 0) return;

        Item item = ForgeRegistries.ITEMS.getValue(new ResourceLocation("medieval_coins", coinName));
        if (item != null) {
            while (count > 0) {
                int stackSize = Math.min(count, 64);
                ItemStack stack = new ItemStack(item, stackSize);
                if (!player.getInventory().add(stack)) {
                    player.drop(stack, false);
                }
                count -= stackSize;
            }
        }
    }
}
