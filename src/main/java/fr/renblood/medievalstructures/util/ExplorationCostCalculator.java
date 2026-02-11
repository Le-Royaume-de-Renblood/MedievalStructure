package fr.renblood.medievalstructures.util;

import fr.renblood.medievalstructures.network.PacketExplorationAction;

import java.util.List;

public class ExplorationCostCalculator {

    public static int calculateTotalCost(int duration, List<PacketExplorationAction.PlayerConfig> configs) {
        int playerCount = configs.size();
        int timeCost = calculateTimeCost(duration, playerCount);
        int extrasCost = 0;

        for (PacketExplorationAction.PlayerConfig config : configs) {
            extrasCost += calculateChestCost(config.chestCount);
            extrasCost += calculateAnimalCost(config.animalCount);
        }

        return timeCost + extrasCost;
    }

    private static int calculateTimeCost(int duration, int playerCount) {
        // Grille de prix fournie
        // 1 joueur : 20s: 0, 40s: 64, 60s: 96, 90s: 128, 120s: 192, 150s: 256
        // Formule approximative ou switch case ?
        // On va utiliser un switch case pour être exact selon la grille
        
        int baseCost = 0;
        switch (duration) {
            case 20: baseCost = 0; break;
            case 40: baseCost = 64; break;
            case 60: baseCost = 96; break;
            case 90: baseCost = 128; break;
            case 120: baseCost = 192; break;
            case 150: baseCost = 256; break;
            default: baseCost = 0; break;
        }

        // Multiplicateur joueurs (selon la grille, c'est linéaire ?)
        // 1j: 0, 64...
        // 2j: 64, 128... (+64 par rapport à 1j ?)
        // Regardons 20s : 1j=0, 2j=64, 3j=128... => (playerCount - 1) * 64
        // Regardons 40s : 1j=64, 2j=128, 3j=192... => playerCount * 64
        
        // Il semble y avoir une logique : Coût de base par joueur + Coût du temps
        // Analysons la grille :
        // 20s : (N-1) * 64
        // 40s : N * 64
        // 60s : N * 96 (sauf 3j=288? non 3*96=288. 1j=96. Donc N * 96)
        // 90s : N * 128
        // 120s : N * 192
        // 150s : N * 256
        
        // Donc la formule est : playerCount * CoûtUnitaire(duration)
        // Sauf pour 20s où c'est (playerCount - 1) * 64 ?
        // Vérifions 20s : 1j=0, 2j=64, 3j=128... Oui c'est (N-1)*64.
        
        if (duration == 20) {
            return (playerCount - 1) * 64;
        } else {
            return playerCount * baseCost;
        }
    }

    private static int calculateChestCost(int chestCount) {
        // C1: 0, C2: 32, C3: 64, C4: 96, C5: 128, C6: 150, C7: 170, C8: 180
        // C'est le coût TOTAL pour X coffres, ou le coût du Xème coffre ?
        // Supposons coût total pour la config.
        switch (chestCount) {
            case 1: return 0;
            case 2: return 32;
            case 3: return 64;
            case 4: return 96;
            case 5: return 128;
            case 6: return 150;
            case 7: return 170;
            case 8: return 180;
            default: return 0;
        }
    }

    private static int calculateAnimalCost(int animalCount) {
        // A1: 128, A2: 256, A3: 384
        // Supposons que c'est le coût total pour X animaux.
        // A1 = 128
        // A2 = 256 (128*2)
        // A3 = 384 (128*3)
        // Donc 128 par animal.
        return animalCount * 128;
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
