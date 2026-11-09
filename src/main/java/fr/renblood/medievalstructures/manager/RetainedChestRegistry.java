package fr.renblood.medievalstructures.manager;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.LongPredicate;

/** Coordinates use the existing packed BlockPos save format; all access is on the server thread. */
public final class RetainedChestRegistry {
    public static final int PAGE_SIZE = 10;
    private final Map<Long, Long> incidents = new LinkedHashMap<>();

    public enum Resolution { RESOLVED, NOT_FOUND, UNLOADED, NOT_EMPTY }
    public record Incident(long camp, long source) { }
    public record Page(int number, int pages, int total, List<Incident> incidents) { }

    public void put(long camp, long source) { incidents.put(camp, source); }

    public List<Incident> snapshot() {
        return incidents.entrySet().stream().map(entry -> new Incident(entry.getKey(), entry.getValue())).toList();
    }

    public boolean anyCamp(LongPredicate predicate) {
        for (long camp : incidents.keySet()) if (predicate.test(camp)) return true;
        return false;
    }

    public Page page(int requested) {
        int pages = incidents.isEmpty() ? 1 : (incidents.size() - 1) / PAGE_SIZE + 1;
        int number = Math.min(Math.max(1, requested), pages);
        List<Incident> rows = incidents.entrySet().stream().skip((long) (number - 1) * PAGE_SIZE)
                .limit(PAGE_SIZE).map(entry -> new Incident(entry.getKey(), entry.getValue())).toList();
        return new Page(number, pages, incidents.size(), rows);
    }

    public Resolution resolve(long camp, LongPredicate loaded, LongPredicate containsItems) {
        if (!incidents.containsKey(camp)) return Resolution.NOT_FOUND;
        // Do not inspect block entities or generate chunks until the location is loaded.
        if (!loaded.test(camp)) return Resolution.UNLOADED;
        if (containsItems.test(camp)) return Resolution.NOT_EMPTY;
        incidents.remove(camp);
        return Resolution.RESOLVED;
    }
}
