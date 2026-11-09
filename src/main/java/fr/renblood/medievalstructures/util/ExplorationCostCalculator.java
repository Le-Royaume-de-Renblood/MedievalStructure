package fr.renblood.medievalstructures.util;

import fr.renblood.medievalstructures.network.PacketExplorationAction;

import java.util.List;
import java.util.UUID;

public class ExplorationCostCalculator {
    private static final int[] TIME_DURATIONS = {20, 40, 60, 90, 120, 150};
    private static final int[][] TIME_COSTS = {
            {0, 64, 128, 192, 256, 320, 384, 448, 512, 576},
            {64, 128, 192, 256, 320, 384, 448, 512, 576, 640},
            {96, 192, 288, 384, 480, 576, 672, 768, 864, 960},
            {128, 256, 384, 512, 640, 768, 896, 1024, 1152, 1280},
            {192, 384, 576, 768, 960, 1152, 1344, 1536, 1728, 1920},
            {256, 512, 768, 1024, 1280, 1536, 1792, 2048, 2304, 2560}
    };
    private static final int[][] CHEST_COSTS = {
            {0, 0, 0, 0, 0, 0, 0, 0, 0, 0},
            {32, 64, 96, 128, 160, 192, 224, 256, 288, 320},
            {64, 128, 192, 256, 320, 384, 448, 512, 576, 640},
            {96, 192, 288, 384, 480, 576, 672, 768, 864, 960},
            {128, 256, 384, 512, 640, 768, 896, 1024, 1152, 1280},
            {150, 300, 450, 600, 750, 900, 1050, 1200, 1350, 1500},
            {170, 340, 510, 680, 850, 1020, 1190, 1360, 1530, 1700},
            {180, 360, 540, 720, 900, 1080, 1260, 1440, 1620, 1800}
    };
    private static final int[][] ANIMAL_COSTS = {
            {128, 256, 384, 512, 640, 768, 896, 1024, 1152, 1280},
            {256, 512, 768, 1024, 1280, 1536, 1792, 2048, 2304, 2560},
            {384, 768, 1152, 1536, 1920, 2304, 2688, 3072, 3456, 3840}
    };

    private ExplorationCostCalculator() {
    }

    public static int calculateTotalCost(int duration, List<PacketExplorationAction.PlayerConfig> configs) {
        if (configs == null || configs.isEmpty()) return -1;
        int playerCount = configs.size();
        int timeCost = calculateTimeCost(duration, playerCount);
        if (timeCost < 0) return -1;

        int extrasCost = 0;
        for (PacketExplorationAction.PlayerConfig config : configs) {
            extrasCost += calculateExtrasCost(playerCount, config, config.playerUUID);
        }
        return timeCost + extrasCost;
    }

    public static int calculatePlayerCost(int duration, int playerCount, PacketExplorationAction.PlayerConfig config) {
        if (playerCount <= 0 || config == null) return 0;
        int timeCost = calculateTimeCost(duration, playerCount);
        if (timeCost < 0) return 0;
        int sharedTimeCost = (int) Math.ceil(timeCost / (double) playerCount);
        return sharedTimeCost + calculateExtrasCost(playerCount, config, config.playerUUID);
    }

    private static int calculateExtrasCost(int playerCount, PacketExplorationAction.PlayerConfig config, UUID playerUuid) {
        int effectiveChests = ExplorationProgression.getEffectiveChestCountForCost(config.chestCount, playerUuid);
        int raw = calculateChestCost(effectiveChests, playerCount) + calculateAnimalCost(config.animalCount, playerCount);
        return (int) Math.ceil(raw * ExplorationProgression.getExtrasMultiplier(playerUuid));
    }

    private static int calculateTimeCost(int duration, int playerCount) {
        int durationIndex = durationIndex(duration);
        if (durationIndex < 0) return -1;
        return TIME_COSTS[durationIndex][playerIndex(playerCount)];
    }

    private static int calculateChestCost(int chestCount, int playerCount) {
        if (chestCount <= 0) return 0;
        int row = Math.min(chestCount, CHEST_COSTS.length) - 1;
        return CHEST_COSTS[row][playerIndex(playerCount)];
    }

    private static int calculateAnimalCost(int animalCount, int playerCount) {
        if (animalCount <= 0) return 0;
        int row = Math.min(animalCount, ANIMAL_COSTS.length) - 1;
        return ANIMAL_COSTS[row][playerIndex(playerCount)];
    }

    private static int durationIndex(int duration) {
        for (int i = 0; i < TIME_DURATIONS.length; i++) {
            if (TIME_DURATIONS[i] == duration) return i;
        }
        return -1;
    }

    private static int playerIndex(int playerCount) {
        return Math.max(0, Math.min(playerCount - 1, TIME_COSTS[0].length - 1));
    }
    
    public static String formatCost(int ironCoins) {
        if (ironCoins == 0) return "Gratuit";
        
        int gold = ironCoins / (64 * 64 * 64);
        ironCoins %= (64 * 64 * 64);
        
        int silver = ironCoins / (64 * 64);
        ironCoins %= (64 * 64);
        
        int bronze = ironCoins / 64;
        int iron = ironCoins % 64;
        
        StringBuilder sb = new StringBuilder();
        if (gold > 0) sb.append(gold).append(" Or ");
        if (silver > 0) sb.append(silver).append(" Argent ");
        if (bronze > 0) sb.append(bronze).append(" Bronze ");
        if (iron > 0) sb.append(iron).append(" Fer");
        
        return sb.toString().trim();
    }
}
