package fr.renblood.medievalstructures.integration;

import fr.renblood.medievalcoins.api.MedievalCoinsAPI;
import fr.renblood.medievalstructures.inn.InnReferenceItem;
import fr.renblood.medievalcoins.api.model.PlayerModel;
import fr.renblood.medievalcoins.network.PlayerCache;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

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

    public static int getExplorerXP(Player player) {
        if (player instanceof ServerPlayer serverPlayer) {
            return (int) MedievalCoinsAPI.getJobXp(serverPlayer, "explorer");
        }
        PlayerModel.JobExperience job = getCachedJob(player, "explorer");
        return job != null ? (int) job.xp : 0;
    }

    public static boolean hasExplorerTalent(Player player, int talentIndex) {
        PlayerModel.JobExperience job = getCachedJob(player, "explorer");
        return hasTalent(job, talentIndex);
    }

    public static boolean hasExplorerTalent(ServerPlayer player, int talentIndex) {
        PlayerModel.JobExperience cached = getCachedJob(player, "explorer");
        if (hasTalent(cached, talentIndex)) return true;
        PlayerModel.JobExperience job = getCachedJob(player.getUUID().toString(), "explorer");
        return hasTalent(job, talentIndex);
    }

    public static boolean hasExplorerTalent(java.util.UUID uuid, int talentIndex) {
        if (uuid == null) return false;
        PlayerModel.JobExperience job = getCachedJob(uuid.toString(), "explorer");
        return hasTalent(job, talentIndex);
    }

    public static List<Integer> getUnlockedExplorerTalents(Player player) {
        PlayerModel.JobExperience job = getCachedJob(player, "explorer");
        List<Integer> talents = new ArrayList<>();
        if (job == null || job.progression == null) return talents;
        for (int i = 0; i < job.progression.size(); i++) {
            if (Boolean.TRUE.equals(job.progression.get(i))) {
                talents.add(i);
            }
        }
        return talents;
    }

    public static int getExplorerTalentSlots(Player player) {
        PlayerModel.JobExperience job = getCachedJob(player, "explorer");
        return job != null && job.progression != null ? job.progression.size() : 0;
    }

    public static int getExplorerLevel(Player player) {
        PlayerModel.JobExperience job = getCachedJob(player, "explorer");
        return job != null ? job.level : 0;
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

    public static void addExplorerXP(ServerPlayer player, int amount) {
        if (player != null && amount > 0) {
            MedievalCoinsAPI.addJobXp(player, "explorer", amount);
        }
    }

    public static long getReferencePrice(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return 0L;
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(stack.getItem());
        return id == null ? 0L : getReferencePrice(id.toString());
    }

    public static long getReferencePrice(String itemId) {
        if (itemId == null || itemId.isBlank()) return 0L;
        return MedievalCoinsAPI.getReferencePrice(itemId);
    }

    public static String formatMoney(long amount) {
        try {
            Object result = MedievalCoinsAPI.class.getMethod("formatMoney", long.class).invoke(null, amount);
            if (result instanceof String value) return value;
        } catch (ReflectiveOperationException ignored) {
        }
        long gold = amount / 262144L;
        amount %= 262144L;
        long silver = amount / 4096L;
        amount %= 4096L;
        long bronze = amount / 64L;
        long iron = amount % 64L;
        StringBuilder result = new StringBuilder();
        appendMoney(result, gold, "or");
        appendMoney(result, silver, "argent");
        appendMoney(result, bronze, "bronze");
        appendMoney(result, iron, "fer");
        return result.length() == 0 ? "0 fer" : result.toString();
    }

    private static void appendMoney(StringBuilder result, long value, String unit) {
        if (value == 0L) return;
        if (result.length() > 0) result.append(' ');
        result.append(value).append(' ').append(unit);
    }

    public static List<InnReferenceItem> getPriceReferences() {
        return MedievalCoinsAPI.getPriceReferences().stream()
                .filter(reference -> reference.referencePrice() != null && reference.referencePrice() > 0L)
                .map(reference -> new InnReferenceItem(reference.itemId(), reference.displayName(),
                        reference.category(), reference.referencePrice()))
                .toList();
    }

    public static CompletableFuture<Boolean> reloadPriceReferences() {
        try {
            Object result = MedievalCoinsAPI.class.getMethod("reloadPriceReferences").invoke(null);
            if (result instanceof CompletableFuture<?> future) {
                return future.thenApply(Boolean.TRUE::equals);
            }
        } catch (ReflectiveOperationException ignored) {
        }
        return CompletableFuture.completedFuture(false);
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

    private static PlayerModel.JobExperience getCachedJob(Player player, String jobName) {
        if (player == null) return null;
        return getCachedJob(player.getUUID().toString(), jobName);
    }

    private static PlayerModel.JobExperience getCachedJob(String uuid, String jobName) {
        PlayerModel pm = PlayerCache.getPlayer(uuid);
        if (pm != null && pm.experiences != null && pm.experiences.jobs != null) {
            return pm.experiences.jobs.get(jobName);
        }
        return null;
    }

    private static boolean hasTalent(PlayerModel.JobExperience job, int talentIndex) {
        return job != null
                && job.progression != null
                && talentIndex >= 0
                && talentIndex < job.progression.size()
                && Boolean.TRUE.equals(job.progression.get(talentIndex));
    }

}
