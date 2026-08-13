package com.expandia.worldgen;

import net.minecraft.resources.ResourceKey;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;

public final class ExpandiaRadiusState {

    public static final DimState OVERWORLD = new DimState(false);
    public static final DimState NETHER = new DimState(true);

    private ExpandiaRadiusState() {
    }

    public static DimState of(ResourceKey<Level> dimension) {
        return Level.NETHER.equals(dimension) ? NETHER : OVERWORLD;
    }

    public static int getIslandRadius() {
        return OVERWORLD.getIslandRadius();
    }

    public static void setIslandRadius(int radius) {
        OVERWORLD.setIslandRadius(radius);
        NETHER.setIslandRadius(radius);
    }

    public static int getTransitionWidth() {
        return OVERWORLD.getTransitionWidth();
    }

    public static void setTransitionWidth(int width) {
        OVERWORLD.setTransitionWidth(width);
        NETHER.setTransitionWidth(width);
    }

    public static int getIslandCount() {
        return OVERWORLD.getIslandCount();
    }

    public static void setSpiralIndices(int[] indices) {
        OVERWORLD.setSpiralIndices(indices);
    }

    public static double[] getCenters() {
        return OVERWORLD.getCenters();
    }

    public static int nearestIslandSlot(int x, int z) {
        return OVERWORLD.nearestIslandSlot(x, z);
    }

    public static boolean isWithinIslandReach(int x, int z) {
        return OVERWORLD.isWithinIslandReach(x, z);
    }

    public static double oceanBlend(int x, int z) {
        return OVERWORLD.oceanBlend(x, z);
    }

    public static double nearestCenterDistance(int x, int z) {
        return OVERWORLD.nearestCenterDistance(x, z);
    }

    public static int islandSurfaceY(int x, int z, int seaLevel) {
        return OVERWORLD.islandSurfaceY(x, z, seaLevel);
    }

    public static boolean isActive() {
        return OVERWORLD.isActive();
    }

    public static void setActive(boolean value) {
        OVERWORLD.setActive(value);
        NETHER.setActive(value);
    }

    public static final class DimState {
        private static final double FOREST_CORE_RADIUS = 14.0D;

        private final boolean nether;
        private final int oceanGap;
        private volatile int islandRadius = 64;
        private volatile int transitionWidth = 8;
        private volatile int[] spiralIndices = {0};
        private volatile boolean active = true;
        private volatile double[] centers = {0.0D, 0.0D};

        private DimState(boolean nether) {
            this.nether = nether;
            this.oceanGap = nether ? ExpandiaIslandLayout.NETHER_OCEAN_GAP : ExpandiaIslandLayout.OVERWORLD_OCEAN_GAP;
        }

        public boolean nether() {
            return nether;
        }

        public int getOceanGap() {
            return oceanGap;
        }

        public int getIslandRadius() {
            return islandRadius;
        }

        public void setIslandRadius(int radius) {
            islandRadius = Math.max(0, radius);
            recomputeCenters();
        }

        public int getTransitionWidth() {
            return transitionWidth;
        }

        public void setTransitionWidth(int width) {
            transitionWidth = Math.max(1, width);
            recomputeCenters();
        }

        public int getIslandCount() {
            return spiralIndices.length;
        }

        public void setSpiralIndices(int[] indices) {
            spiralIndices = (indices == null || indices.length == 0) ? new int[]{0} : indices.clone();
            recomputeCenters();
        }

        public double[] getCenters() {
            return centers;
        }

        public int nearestIslandSlot(int x, int z) {
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

        public double[] nearestCenter(int x, int z) {
            double[] c = centers;
            double best = Double.MAX_VALUE;
            double bx = 0.0D;
            double bz = 0.0D;
            for (int i = 0; i + 1 < c.length; i += 2) {
                double dx = x - c[i];
                double dz = z - c[i + 1];
                double d = dx * dx + dz * dz;
                if (d < best) {
                    best = d;
                    bx = c[i];
                    bz = c[i + 1];
                }
            }
            return new double[]{bx, bz};
        }

        public boolean isWithinIslandReach(int x, int z) {
            return oceanBlend(x, z) < 1.0D;
        }

        public double oceanBlend(int x, int z) {
            if (!active || islandRadius <= 0) {
                return 1.0D;
            }
            int transition = transitionWidth;
            double dist = Math.max(0.0D, nearestCenterDistance(x, z) + boundaryWobble(x, z, transition));
            double coastInner = Math.max(FOREST_CORE_RADIUS, (double) islandRadius - transition);
            double coastOuter = (double) islandRadius + transition;
            if (dist <= coastInner) {
                return 0.0D;
            }
            if (dist >= coastOuter) {
                return 1.0D;
            }
            return smootherStep(coastInner, coastOuter, dist);
        }

        public double nearestCenterDistance(int x, int z) {
            double[] c = centers;
            double best = Double.MAX_VALUE;
            for (int i = 0; i + 1 < c.length; i += 2) {
                double dx = x - c[i];
                double dz = z - c[i + 1];
                double d = Math.sqrt(dx * dx + dz * dz);
                if (d < best) {
                    best = d;
                }
            }
            return best;
        }

        public int islandSurfaceY(int x, int z, int seaLevel) {
            double blend = oceanBlend(x, z);
            if (blend >= 1.0D) {
                return Integer.MIN_VALUE;
            }
            double dist = nearestCenterDistance(x, z);
            double radius = Math.max(1.0D, islandRadius);
            double t = Mth.clamp(dist / radius, 0.0D, 1.0D);
            double dome = Math.pow(Math.max(0.0D, 1.0D - t * t), nether ? 0.55D : 1.35D);
            double wobble = (Math.sin(x * 0.08D) * Math.cos(z * 0.05D)
                    + Math.sin(x * 0.13D + z * 0.09D) * 0.45D) * (nether ? 3.0D : 3.5D) * dome;
            double peak = nether ? Math.min(10.0D, radius * 0.32D) : 8.0D;
            double height = peak * dome * (1.0D - blend * 0.7D) + wobble;
            return (int) Math.round(seaLevel + Math.max(nether ? 2.0D : 2.0D, height));
        }

        public boolean isActive() {
            return active;
        }

        public void setActive(boolean value) {
            active = value;
        }

        private double boundaryWobble(int x, int z, int transition) {
            double amplitude = transition * 0.28D;
            double n = Math.sin(x * 0.07D) * Math.cos(z * 0.05D)
                    + Math.sin(x * 0.13D + z * 0.11D) * 0.55D
                    + Math.cos(x * 0.03D - z * 0.09D) * 0.35D;
            return n * amplitude;
        }

        private static double smootherStep(double edge0, double edge1, double value) {
            double t = Mth.clamp((value - edge0) / (edge1 - edge0), 0.0D, 1.0D);
            return t * t * t * (t * (t * 6.0D - 15.0D) + 10.0D);
        }

        private void recomputeCenters() {
            centers = ExpandiaIslandLayout.centersForIndices(spiralIndices, islandRadius, transitionWidth, oceanGap);
        }
    }
}
