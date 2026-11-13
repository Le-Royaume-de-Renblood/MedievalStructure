package fr.renblood.medievalstructures.manager;

import net.minecraft.world.level.ChunkPos;
import java.util.*;
import java.util.function.*;

/** Ticket requests and readiness polling only. Never waits for world generation. */
public final class ChunkPreparation implements AutoCloseable {
    public enum State { WORKING, READY, FAILED }
    private final List<ChunkPos> chunks;
    private final Set<ChunkPos> requested = new LinkedHashSet<>();
    private final Consumer<ChunkPos> request, release;
    private final Predicate<ChunkPos> ready;
    private final LongSupplier clock;
    private final long deadline;
    private boolean closed;
    private State state = State.WORKING;

    public ChunkPreparation(Collection<ChunkPos> chunks, Consumer<ChunkPos> request,
            Predicate<ChunkPos> ready, Consumer<ChunkPos> release, LongSupplier clock, long timeoutNanos) {
        this.chunks = List.copyOf(new LinkedHashSet<>(chunks));
        this.request = request; this.ready = ready; this.release = release; this.clock = clock;
        deadline = clock.getAsLong() + timeoutNanos;
        if (this.chunks.size() > 256) throw new IllegalArgumentException("Preparation too large");
    }

    public State tick() {
        if (closed || state == State.FAILED) return State.FAILED;
        if (clock.getAsLong() - deadline >= 0) { state = State.FAILED; close(); return state; }
        try {
            if (requested.size() < chunks.size()) {
                ChunkPos next = chunks.get(requested.size());
                requested.add(next); // Also release the ticket if its request partially fails.
                request.accept(next);
            }
            state = requested.size() == chunks.size() && chunks.stream().allMatch(ready)
                    ? State.READY : State.WORKING;
        } catch (RuntimeException failure) { state = State.FAILED; close(); }
        return state;
    }

    public int progress() {
        if (chunks.isEmpty()) return 100;
        return (int) (100L * requested.stream().filter(ready).count() / chunks.size());
    }

    @Override public void close() {
        if (closed) return;
        closed = true;
        for (ChunkPos chunk : requested) release.accept(chunk);
    }
}
