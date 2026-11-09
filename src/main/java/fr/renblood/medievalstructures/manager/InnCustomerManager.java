package fr.renblood.medievalstructures.manager;

import fr.renblood.medievalstructures.inn.Inn;
import fr.renblood.medievalstructures.inn.InnManager;
import fr.renblood.medievalstructures.inn.InnMenuEntry;
import fr.renblood.medievalstructures.integration.NpcShopkeeperIntegration;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

import java.util.Comparator;
import java.util.PriorityQueue;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

public final class InnCustomerManager {
    private static final Map<String, Appointment> NEXT_SPAWN = new HashMap<>();
    private static final PriorityQueue<Appointment> DUE = new PriorityQueue<>(Comparator.comparingLong(Appointment::tick));
    private static final Set<String> CLEARED_WHILE_BLOCKED = new HashSet<>();
    private static final Map<String, String> LAST_RESULT = new HashMap<>();
    private static final Random RANDOM = new Random();
    private static ServerLevel currentLevel;

    private InnCustomerManager() {
    }

    public static void tick(MinecraftServer server) {
        ServerLevel level = server.overworld();
        long now = level.getGameTime();
        InnManager manager = InnManager.get(level);
        if (currentLevel != level) {
            currentLevel = level;
            NEXT_SPAWN.clear();
            DUE.clear();
            LAST_RESULT.clear();
            CLEARED_WHILE_BLOCKED.clear();
            updateSchedules(level, manager, now);
        } else if (now % 20 == 0) {
            updateSchedules(level, manager, now);
        }

        int remaining = 2;
        while (remaining > 0 && !DUE.isEmpty() && DUE.peek().tick() <= now) {
            Appointment appointment = DUE.poll();
            if (NEXT_SPAWN.get(appointment.name()) != appointment) continue;
            NEXT_SPAWN.remove(appointment.name());
            Inn inn = manager.getInn(appointment.name());
            if (inn == null || !inn.canReceiveCustomers()) continue;
            remaining--;
            InnMenuEntry request = chooseRequest(inn);
            if (request != null) {
                String result = NpcShopkeeperIntegration.trySpawnInnCustomer(level, inn.getCustomerRouteId(), inn.getName(),
                        request.getItemId(), request.getDisplayName(), request.getFinalPrice(), request.getType().name());
                LAST_RESULT.put(inn.getName(), result);
            }
            schedule(inn, now);
        }
    }

    private static void updateSchedules(ServerLevel level, InnManager manager, long now) {
        Set<String> existing = new HashSet<>();
        for (Inn inn : manager.getInns()) {
            String key = inn.getName();
            existing.add(key);
            if (!inn.canReceiveCustomers()) {
                if (CLEARED_WHILE_BLOCKED.add(key)) NpcShopkeeperIntegration.clearInnCustomers(level, key);
                NEXT_SPAWN.remove(key);
                LAST_RESULT.remove(key);
                continue;
            }
            CLEARED_WHILE_BLOCKED.remove(key);
            if (!NEXT_SPAWN.containsKey(key)) schedule(inn, now);
        }
        NEXT_SPAWN.keySet().retainAll(existing);
        LAST_RESULT.keySet().retainAll(existing);
        CLEARED_WHILE_BLOCKED.retainAll(existing);
        DUE.removeIf(appointment -> NEXT_SPAWN.get(appointment.name()) != appointment);
    }

    private static void schedule(Inn inn, long now) {
        long delay = "NO_LOADED_POINT".equals(LAST_RESULT.get(inn.getName())) ? 20L * 20L : randomDelayTicks(inn);
        Appointment appointment = new Appointment(inn.getName(), now + delay);
        NEXT_SPAWN.put(inn.getName(), appointment);
        DUE.add(appointment);
    }

    public static String status(Inn inn, ServerLevel level) {
        if (!inn.isActive()) return "Auberge inactive.";
        if (!inn.hasCustomerRoute()) return "Aucune route associée.";
        if (!inn.canReceiveCustomers()) return "Menu incomplet : 4 plats, 2 boissons et 2 entrées sont nécessaires.";
        var next = currentLevel == level ? NEXT_SPAWN.get(inn.getName()) : null;
        String eta = next == null ? "planification en cours" : Math.max(0, (next.tick() - level.getGameTime() + 19) / 20) + " s";
        String result = switch (LAST_RESULT.getOrDefault(inn.getName(), "")) {
            case "SPAWNED" -> "client apparu";
            case "NO_ROUTE" -> "route introuvable ou incompatible";
            case "FULL" -> "capacité atteinte";
            case "NO_FREE_POINT" -> "aucun point libre";
            case "NO_LOADED_POINT" -> "points de la route dans des chunks déchargés";
            case "NO_NPC_AVAILABLE" -> "aucun modèle de PNJ disponible";
            case "UNAVAILABLE" -> "intégration NPC Shopkeeper indisponible";
            default -> "aucun essai enregistré";
        };
        return "Prochain essai : " + eta + " · Dernier résultat : " + result;
    }

    private record Appointment(String name, long tick) {}

    private static long randomDelayTicks(Inn inn) {
        int min = inn.getCustomerRouteMinSeconds();
        int max = Math.max(min, inn.getCustomerRouteMaxSeconds());
        return (long) (min + RANDOM.nextInt(max - min + 1)) * 20L;
    }

    private static InnMenuEntry chooseRequest(Inn inn) {
        if (inn.getMenuEntries().isEmpty()) return null;
        List<Long> prices = inn.getMenuEntries().stream()
                .map(InnMenuEntry::getFinalPrice)
                .sorted()
                .toList();
        long medianPrice = prices.get(prices.size() / 2);
        double totalWeight = 0.0D;
        for (InnMenuEntry entry : inn.getMenuEntries()) {
            totalWeight += weight(entry, medianPrice);
        }
        if (totalWeight <= 0.0D) return inn.getMenuEntries().get(RANDOM.nextInt(inn.getMenuEntries().size()));

        double roll = RANDOM.nextDouble() * totalWeight;
        for (InnMenuEntry entry : inn.getMenuEntries()) {
            roll -= weight(entry, medianPrice);
            if (roll <= 0.0D) return entry;
        }
        return inn.getMenuEntries().get(inn.getMenuEntries().size() - 1);
    }

    private static double weight(InnMenuEntry entry, long medianPrice) {
        double ratio = medianPrice <= 0 ? 1.0D : entry.getFinalPrice() / (double) medianPrice;
        double distance = Math.abs(Math.log(Math.max(0.1D, ratio)));
        double priceWeight = 1.0D / (1.0D + distance);
        double typeWeight = switch (entry.getType()) {
            case DISH -> 1.0D;
            case DRINK -> 0.85D;
            case STARTER -> 0.75D;
        };
        return priceWeight * typeWeight;
    }

    public static void clearSchedule(String innName) {
        NEXT_SPAWN.remove(innName);
        LAST_RESULT.remove(innName);
        CLEARED_WHILE_BLOCKED.remove(innName);
    }
}
