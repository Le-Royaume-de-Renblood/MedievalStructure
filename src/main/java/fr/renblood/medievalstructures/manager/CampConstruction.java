package fr.renblood.medievalstructures.manager;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import java.util.*;

/** Small reversible terrain edits; all accessed chunks are held by preparation tickets. */
public final class CampConstruction {
    private record Edit(BlockPos pos, BlockState state) {}
    private final Deque<Edit> edits = new ArrayDeque<>();
    public final Map<BlockPos, BlockState> before = new HashMap<>();
    public final List<BlockPos> changed = new ArrayList<>();
    private final int total;

    public CampConstruction(ServerLevel level, BlockPos center) {
        // Remember decoration ground too, without serializing an entire chunk.
        for (int x = -14; x <= 14; x++) for (int z = -14; z <= 14; z++)
            for (int y = -1; y <= 6; y++) {
                BlockPos p = center.offset(x, y, z);
                before.put(p, level.getBlockState(p));
            }
        for (int x = -6; x <= 6; x++) for (int z = -6; z <= 6; z++) {
            if (x*x + z*z > 36) continue;
            BlockPos ground = center.offset(x, -1, z);
            BlockState state = before.get(ground);
            if (!state.isSolid() || state.is(Blocks.GRASS_BLOCK) || state.is(Blocks.WATER))
                edits.add(new Edit(ground, Blocks.COARSE_DIRT.defaultBlockState()));
            for (int y = 0; y <= 5; y++) {
                BlockPos p = center.offset(x, y, z);
                if (!before.get(p).isAir()) edits.add(new Edit(p, Blocks.AIR.defaultBlockState()));
            }
        }
        total = edits.size();
    }

    public boolean tick(ServerLevel level) {
        long started = System.nanoTime();
        int budget = 24;
        while (!edits.isEmpty() && budget-- > 0 && System.nanoTime() - started < 2_000_000L) {
            Edit edit = edits.removeFirst();
            level.setBlock(edit.pos, edit.state, 2);
            changed.add(edit.pos);
        }
        return edits.isEmpty();
    }
    public int progress() { return total == 0 ? 100 : 100 * (total - edits.size()) / total; }
}
