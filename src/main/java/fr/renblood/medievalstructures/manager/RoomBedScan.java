package fr.renblood.medievalstructures.manager;

import net.minecraft.core.BlockPos;
import java.util.function.Predicate;

/** Incremental inclusive volume scan. Never allocates the full volume or loads chunks. */
final class RoomBedScan {
    enum State { WORKING, COMPLETE, MULTIPLE_BEDS, UNLOADED }
    private final int minX, minY, minZ, maxX, maxY, maxZ;
    private final Predicate<BlockPos> loaded, bedHead;
    private final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
    private int x, y, z;
    private BlockPos bed;
    private State state = State.WORKING;
    RoomBedScan(BlockPos first, BlockPos second, Predicate<BlockPos> loaded, Predicate<BlockPos> bedHead) {
        minX = Math.min(first.getX(), second.getX()); maxX = Math.max(first.getX(), second.getX());
        minY = Math.min(first.getY(), second.getY()); maxY = Math.max(first.getY(), second.getY());
        minZ = Math.min(first.getZ(), second.getZ()); maxZ = Math.max(first.getZ(), second.getZ());
        x = minX; y = minY; z = minZ; this.loaded = loaded; this.bedHead = bedHead;
    }
    State tick(int budget) {
        for (int i = 0; i < budget && state == State.WORKING; i++) {
            cursor.set(x, y, z);
            if (!loaded.test(cursor)) { state = State.UNLOADED; break; }
            if (bedHead.test(cursor)) {
                if (bed != null) { state = State.MULTIPLE_BEDS; break; }
                bed = cursor.immutable();
            }
            if (z < maxZ) z++;
            else if (y < maxY) { z = minZ; y++; }
            else if (x < maxX) { z = minZ; y = minY; x++; }
            else state = State.COMPLETE;
        }
        return state;
    }
    BlockPos bed() { return bed; }
}
