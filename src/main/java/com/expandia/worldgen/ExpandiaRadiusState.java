package com.expandia.worldgen;

public final class ExpandiaRadiusState {

    private ExpandiaRadiusState() {
    }

    private static volatile int islandRadius = 64;
    private static volatile int transitionWidth = 8;
    private static volatile int[] spiralIndices = {0};
    private static volatile boolean active = true;
    private static volatile double[] centers = {0.0D, 0.0D};

    public static int getIslandRadius() {
        return islandRadius;
    }

    public static void setIslandRadius(int radius) {
        islandRadius = Math.max(0, radius);
        recomputeCenters();
    }

    public static int getTransitionWidth() {
        return transitionWidth;
    }

    public static void setTransitionWidth(int width) {
        transitionWidth = Math.max(1, width);
        recomputeCenters();
    }

    public static int getIslandCount() {
        return spiralIndices.length;
    }

    public static void setSpiralIndices(int[] indices) {
        spiralIndices = (indices == null || indices.length == 0) ? new int[]{0} : indices.clone();
        recomputeCenters();
    }

    public static double[] getCenters() {
        return centers;
    }

    public static int nearestIslandSlot(int x, int z) {
        double[] c = centers;
        int[] idx = spiralIndices;
        int bestArrayIndex = 0;
        double bestDistSq = Double.MAX_VALUE;
        for (int i = 0; i + 1 < c.length; i += 2) {
            double dx = x - c[i];
            double dz = z - c[i + 1];
            double d = dx * dx + dz * dz;
            if (d < bestDistSq) {
                bestDistSq = d;
                bestArrayIndex = i / 2;
            }
        }
        return bestArrayIndex < idx.length ? idx[bestArrayIndex] : 0;
    }

    public static boolean isWithinIslandReach(int x, int z) {
        double reach = (double) islandRadius + transitionWidth;
        double reachSq = reach * reach;
        double[] c = centers;
        for (int i = 0; i + 1 < c.length; i += 2) {
            double dx = x - c[i];
            double dz = z - c[i + 1];
            if (dx * dx + dz * dz <= reachSq) {
                return true;
            }
        }
        return false;
    }

    public static boolean isActive() {
        return active;
    }

    public static void setActive(boolean value) {
        active = value;
    }

    private static void recomputeCenters() {
        centers = ExpandiaIslandLayout.centersForIndices(spiralIndices, islandRadius, transitionWidth);
    }
}
