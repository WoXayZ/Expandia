package com.expandia.worldgen;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;

public final class ExpandiaIslandLayout {

    private static final int MAX_SLOT_SEARCH = 100_000;
    private static final double GOLDEN_ANGLE = Math.PI * (3.0D - Math.sqrt(5.0D));

    /** Closest-pair distance on the spiral is ~1.6532× scale; divide spacing by this to hit the target gap. */
    private static final double PHYLLOTAXIS_MIN_RATIO = 1.6532D;
    private static final double RADIAL_OFFSET = 2.0D;

    /** Must exceed 2× {@link ExpandiaChunkRegenerator#COAST_BLEND_BLOCKS} or regen discs overlap and wipe neighbours. */
    private static final int OCEAN_GAP = 64;

    private ExpandiaIslandLayout() {
    }

    public static double spacing(int islandRadius, int transitionWidth) {
        return 2.0D * islandRadius + OCEAN_GAP;
    }

    public static double[] center(int index, int islandRadius, int transitionWidth) {
        if (index <= 0) {
            return new double[]{0.0D, 0.0D};
        }
        double scale = spacing(islandRadius, transitionWidth) / PHYLLOTAXIS_MIN_RATIO;
        double distance = scale * Math.sqrt(index + RADIAL_OFFSET);
        double angle = index * GOLDEN_ANGLE;
        return new double[]{Math.cos(angle) * distance, Math.sin(angle) * distance};
    }

    public static double[] centers(int count, int islandRadius, int transitionWidth) {
        int safeCount = Math.max(1, count);
        double[] result = new double[safeCount * 2];
        for (int i = 0; i < safeCount; i++) {
            double[] c = center(i, islandRadius, transitionWidth);
            result[i * 2] = c[0];
            result[i * 2 + 1] = c[1];
        }
        return result;
    }

    public static double[] centersForIndices(int[] indices, int islandRadius, int transitionWidth) {
        if (indices == null || indices.length == 0) {
            return new double[]{0.0D, 0.0D};
        }
        double[] result = new double[indices.length * 2];
        for (int i = 0; i < indices.length; i++) {
            double[] c = center(indices[i], islandRadius, transitionWidth);
            result[i * 2] = c[0];
            result[i * 2 + 1] = c[1];
        }
        return result;
    }

    /** First slot at or after {@code startIndex} whose whole disc is currently unloaded. */
    public static int findPlaceableSlot(ServerLevel level, int startIndex, int islandRadius, int transitionWidth) {
        int index = Math.max(1, startIndex);
        for (int scanned = 0; scanned < MAX_SLOT_SEARCH; scanned++, index++) {
            double[] c = center(index, islandRadius, transitionWidth);
            if (isDiscUnloaded(level, c[0], c[1], islandRadius)) {
                return index;
            }
        }
        return index;
    }

    private static boolean isDiscUnloaded(ServerLevel level, double centerX, double centerZ, int islandRadius) {
        for (ChunkPos pos : ExpandiaChunkRegenerator.collectIslandDisc(centerX, centerZ, islandRadius)) {
            if (level.getChunkSource().hasChunk(pos.x(), pos.z())) {
                return false;
            }
        }
        return true;
    }
}
