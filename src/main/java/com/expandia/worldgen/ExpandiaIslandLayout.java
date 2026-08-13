package com.expandia.worldgen;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

public final class ExpandiaIslandLayout {

    private static final double GOLDEN_ANGLE = Math.PI * (3.0D - Math.sqrt(5.0D));
    private static final double PHYLLOTAXIS_MIN_RATIO = 1.6532D;
    private static final double RADIAL_OFFSET = 2.0D;
    public static final int OVERWORLD_OCEAN_GAP = 96;
    public static final int NETHER_OCEAN_GAP = 48;

    private ExpandiaIslandLayout() {
    }

    public static int oceanGap(ResourceKey<Level> dimension) {
        return Level.NETHER.equals(dimension) ? NETHER_OCEAN_GAP : OVERWORLD_OCEAN_GAP;
    }

    public static double spacing(int islandRadius, int transitionWidth, int oceanGap) {
        return 2.0D * islandRadius + oceanGap;
    }

    public static double[] center(int index, int islandRadius, int transitionWidth, int oceanGap) {
        if (index <= 0) {
            return new double[]{0.0D, 0.0D};
        }
        double scale = spacing(islandRadius, transitionWidth, oceanGap) / PHYLLOTAXIS_MIN_RATIO;
        double distance = scale * Math.sqrt(index + RADIAL_OFFSET);
        double angle = index * GOLDEN_ANGLE;
        return new double[]{Math.cos(angle) * distance, Math.sin(angle) * distance};
    }

    public static double[] centersForIndices(int[] indices, int islandRadius, int transitionWidth, int oceanGap) {
        if (indices == null || indices.length == 0) {
            return new double[]{0.0D, 0.0D};
        }
        double[] result = new double[indices.length * 2];
        for (int i = 0; i < indices.length; i++) {
            double[] c = center(indices[i], islandRadius, transitionWidth, oceanGap);
            result[i * 2] = c[0];
            result[i * 2 + 1] = c[1];
        }
        return result;
    }

    public static int nextSlot(int highestUsedIndex) {
        return Math.max(1, highestUsedIndex + 1);
    }
}
