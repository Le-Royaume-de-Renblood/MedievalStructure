package fr.renblood.medievalstructures.util;

import fr.renblood.medievalstructures.integration.MedievalCoinsIntegration;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class ExplorationProgression {
    public static final int[] DURATIONS = {20, 40, 60, 90, 120, 150};
    public static final int LEVEL_2_REQUIRED_EXPLORATIONS = 5;
    public static final int LEVEL_3_REQUIRED_EXPLORATIONS = 15;
    public static final int MAX_ANIMALS_PER_PLAYER = 3;

    private ExplorationProgression() {
    }

    public static int getHallLevel(int completedExplorations) {
        if (completedExplorations >= LEVEL_3_REQUIRED_EXPLORATIONS) return 3;
        if (completedExplorations >= LEVEL_2_REQUIRED_EXPLORATIONS) return 2;
        return 1;
    }

    public static int getBaseMaxDuration(int hallLevel) {
        return switch (Math.max(1, Math.min(3, hallLevel))) {
            case 2 -> 40;
            case 3 -> 60;
            default -> 20;
        };
    }

    public static int getMaxDuration(int hallLevel, Player player) {
        int max = getBaseMaxDuration(hallLevel);
        if (MedievalCoinsIntegration.hasExplorerTalent(player, 0)) max += 40;
        if (MedievalCoinsIntegration.hasExplorerTalent(player, 1)) max += 60;
        return max;
    }

    public static int getMaxDuration(int hallLevel, UUID playerUuid) {
        int max = getBaseMaxDuration(hallLevel);
        if (MedievalCoinsIntegration.hasExplorerTalent(playerUuid, 0)) max += 40;
        if (MedievalCoinsIntegration.hasExplorerTalent(playerUuid, 1)) max += 60;
        return max;
    }

    public static List<Integer> getAvailableDurations(int hallLevel, Player player) {
        return getAvailableDurations(getMaxDuration(hallLevel, player));
    }

    public static List<Integer> getAvailableDurations(int maxDuration) {
        List<Integer> available = new ArrayList<>();
        for (int duration : DURATIONS) {
            if (duration <= maxDuration) {
                available.add(duration);
            }
        }
        if (available.isEmpty()) available.add(DURATIONS[0]);
        return available;
    }

    public static boolean isDurationAllowed(int duration, int hallLevel, UUID playerUuid) {
        if (!isKnownDuration(duration)) return false;
        return duration <= getMaxDuration(hallLevel, playerUuid);
    }

    public static boolean isKnownDuration(int duration) {
        for (int known : DURATIONS) {
            if (known == duration) return true;
        }
        return false;
    }

    public static int getBaseMaxChests(int hallLevel) {
        return switch (Math.max(1, Math.min(3, hallLevel))) {
            case 2 -> 5;
            case 3 -> 6;
            default -> 4;
        };
    }

    public static int getMaxChests(int hallLevel, Player player) {
        int max = getBaseMaxChests(hallLevel);
        if (MedievalCoinsIntegration.hasExplorerTalent(player, 3)) max++;
        if (MedievalCoinsIntegration.hasExplorerTalent(player, 4)) max++;
        return Math.min(max, 8);
    }

    public static int getMaxChests(int hallLevel, UUID playerUuid) {
        int max = getBaseMaxChests(hallLevel);
        if (MedievalCoinsIntegration.hasExplorerTalent(playerUuid, 3)) max++;
        if (MedievalCoinsIntegration.hasExplorerTalent(playerUuid, 4)) max++;
        return Math.min(max, 8);
    }

    public static double getExtrasMultiplier(UUID playerUuid) {
        double multiplier = 1.0D;
        if (MedievalCoinsIntegration.hasExplorerTalent(playerUuid, 3)) multiplier -= 0.10D;
        if (MedievalCoinsIntegration.hasExplorerTalent(playerUuid, 4)) multiplier -= 0.15D;
        return Math.max(0.0D, multiplier);
    }

    public static int getEffectiveChestCountForCost(int chestCount, UUID playerUuid) {
        if (MedievalCoinsIntegration.hasExplorerTalent(playerUuid, 9)) {
            return Math.max(1, chestCount - 1);
        }
        return chestCount;
    }

    public static boolean shouldSpawnBeds(Player player) {
        return MedievalCoinsIntegration.hasExplorerTalent(player, 6);
    }

    public static boolean shouldSpawnLodestone(Player player) {
        return MedievalCoinsIntegration.hasExplorerTalent(player, 7);
    }
}
