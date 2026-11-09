package fr.renblood.medievalstructures.manager;

import java.util.ArrayList;
import java.util.List;

public final class CampLayout {
    public static final int HALF_SIZE = 2000;

    private CampLayout() {}

    public static boolean overlaps(int x, int z, int otherX, int otherZ) {
        return Math.abs((long) x - otherX) < 2L * HALF_SIZE + 16
                && Math.abs((long) z - otherZ) < 2L * HALF_SIZE + 16;
    }

    public static boolean contains(double x, double z, int centerX, int centerZ) {
        return Math.abs(x - centerX) < HALF_SIZE && Math.abs(z - centerZ) < HALF_SIZE;
    }

    public static List<Offset> chestOffsets() {
        List<Offset> positions = new ArrayList<>();
        for (int radius = 4; radius <= 64; radius += 2) {
            for (int x = -radius; x <= radius; x += 2) {
                positions.add(new Offset(x, -radius));
                positions.add(new Offset(x, radius));
            }
            for (int z = -radius + 2; z < radius; z += 2) {
                positions.add(new Offset(-radius, z));
                positions.add(new Offset(radius, z));
            }
        }
        return List.copyOf(positions);
    }

    public record Offset(int x, int z) {}
}
