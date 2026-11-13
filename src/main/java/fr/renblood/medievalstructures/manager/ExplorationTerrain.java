package fr.renblood.medievalstructures.manager;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

public final class ExplorationTerrain {
    private static final TicketType<Integer> TICKET = TicketType.create("renblood_exploration", Integer::compare);
    private static final AtomicInteger JOB_IDS = new AtomicInteger();
    private ExplorationTerrain() {}

    public static ChunkPreparation prepare(ServerLevel level, Collection<ChunkPos> chunks) {
        int job = JOB_IDS.incrementAndGet();
        return new ChunkPreparation(chunks,
                pos -> level.getChunkSource().addRegionTicket(TICKET, pos, 0, job),
                pos -> level.getChunkSource().getChunkNow(pos.x, pos.z) != null,
                pos -> level.getChunkSource().removeRegionTicket(TICKET, pos, 0, job),
                System::nanoTime, 90_000_000_000L);
    }

    public static List<ChunkPos> around(BlockPos pos) {
        var chunks = new ArrayList<ChunkPos>();
        for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++)
            chunks.add(new ChunkPos((pos.getX() >> 4) + x, (pos.getZ() >> 4) + z));
        return chunks;
    }

    public static boolean loaded(ServerLevel level, BlockPos pos) {
        return level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4) != null;
    }
}
