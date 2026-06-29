package com.expandia.worldgen;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.util.Mth;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.levelgen.DensityFunction;

public record ExpandiaLandMaskDensityFunction(
        DensityFunction wrapped,
        MaskMode mode,
        int seaLevel
) implements DensityFunction {

    private static final double OCEAN_CONTINENTS = -0.95D;
    private static final double CORE_LAND_FLOOR = 0.15D;
    private static final double COAST_LAND_FLOOR = 0.00D;
    private static final double FOREST_CORE_RADIUS = 14.0D;

    /** Vanilla offset band for dry, moderate-height island terrain (~128 blocks per +1.0 offset). */
    private static final double ISLAND_MIN_OFFSET = -0.42D;
    private static final double ISLAND_MAX_OFFSET = -0.30D;

    public enum MaskMode implements StringRepresentable {
        CONTINENTS("continents"),
        EROSION("erosion"),
        TEMPERATURE("temperature"),
        VEGETATION("vegetation"),
        OFFSET("offset");

        public static final Codec<MaskMode> CODEC = StringRepresentable.fromEnum(MaskMode::values);

        private final String name;

        MaskMode(String name) {
            this.name = name;
        }

        @Override
        public String getSerializedName() {
            return this.name;
        }
    }

    public static final MapCodec<ExpandiaLandMaskDensityFunction> CODEC =
            RecordCodecBuilder.mapCodec(instance -> instance.group(
                    MaskMode.CODEC.fieldOf("mode").forGetter(ExpandiaLandMaskDensityFunction::mode),
                    DensityFunction.HOLDER_HELPER_CODEC.fieldOf("argument")
                            .forGetter(ExpandiaLandMaskDensityFunction::wrapped)
            ).apply(instance, (mode, wrapped) -> new ExpandiaLandMaskDensityFunction(wrapped, mode, 63)));

    private static final KeyDispatchDataCodec<ExpandiaLandMaskDensityFunction> KEY_CODEC =
            KeyDispatchDataCodec.of(CODEC);

    @Override
    public double compute(FunctionContext context) {
        if (!ExpandiaRadiusState.isActive()) {
            return this.wrapped.compute(context);
        }

        int radius = ExpandiaRadiusState.getIslandRadius();
        if (radius <= 0) {
            return this.wrapped.compute(context);
        }

        return switch (this.mode) {
            case CONTINENTS -> maskContinents(context, radius);
            case EROSION -> maskErosion(context, radius);
            case TEMPERATURE -> maskTemperature(context, radius);
            case VEGETATION -> maskVegetation(context, radius);
            case OFFSET -> maskOffset(context, radius);
        };
    }

    private double maskOffset(FunctionContext context, int radius) {
        double vanillaOffset = this.wrapped.compute(context);
        int x = context.blockX();
        int z = context.blockZ();
        double blend = oceanBlend(x, z, radius);
        if (blend >= 1.0D) {
            return vanillaOffset;
        }
        double islandOffset = Mth.clamp(vanillaOffset, ISLAND_MIN_OFFSET, ISLAND_MAX_OFFSET);
        if (blend <= 0.0D) {
            return islandOffset;
        }
        return Mth.lerp(blend, islandOffset, vanillaOffset);
    }

    private static double oceanBlend(int x, int z, int radius) {
        double dist = effectiveDistance(x, z);
        int transition = ExpandiaRadiusState.getTransitionWidth();
        double coastInner = Math.max(FOREST_CORE_RADIUS, (double) radius - transition);
        double coastOuter = (double) radius + transition;

        if (dist <= coastInner) {
            return 0.0D;
        }
        if (dist >= coastOuter) {
            return 1.0D;
        }
        return smootherStep(coastInner, coastOuter, dist);
    }

    private double maskContinents(FunctionContext context, int radius) {
        double vanilla = this.wrapped.compute(context);
        double blend = oceanBlend(context.blockX(), context.blockZ(), radius);
        double land = landContinents(context.blockX(), context.blockZ(), vanilla, blend);
        return Mth.lerp(blend, land, OCEAN_CONTINENTS);
    }

    private static double landContinents(int x, int z, double vanilla, double blend) {
        double solidLand = Math.max(vanilla, interiorLandFloor(x, z));
        return Mth.lerp(Math.min(blend * 2.0D, 1.0D), solidLand, Mth.clamp(vanilla, OCEAN_CONTINENTS, 0.1D));
    }

    private static double interiorLandFloor(int x, int z) {
        double dist = nearestCenterDistance(x, z);
        int radius = ExpandiaRadiusState.getIslandRadius();
        int transition = ExpandiaRadiusState.getTransitionWidth();
        double coastInner = Math.max(FOREST_CORE_RADIUS, (double) radius - transition);
        double t = smootherStep(0.0D, coastInner, dist);
        return Mth.lerp(t, CORE_LAND_FLOOR, COAST_LAND_FLOOR);
    }

    private double maskErosion(FunctionContext context, int radius) {
        double vanilla = this.wrapped.compute(context);
        double blend = oceanBlend(context.blockX(), context.blockZ(), radius);
        return Mth.lerp(blend, vanilla, 0.0D);
    }

    private double maskTemperature(FunctionContext context, int radius) {
        return this.wrapped.compute(context);
    }

    private double maskVegetation(FunctionContext context, int radius) {
        return this.wrapped.compute(context);
    }

    private static double effectiveDistance(int x, int z) {
        double wobble = boundaryWobble(x, z);
        return Math.max(0.0D, nearestCenterDistance(x, z) + wobble);
    }

    private static double nearestCenterDistance(int x, int z) {
        double[] centers = ExpandiaRadiusState.getCenters();
        double best = Double.MAX_VALUE;
        for (int i = 0; i + 1 < centers.length; i += 2) {
            double dx = x - centers[i];
            double dz = z - centers[i + 1];
            double d = Math.sqrt(dx * dx + dz * dz);
            if (d < best) {
                best = d;
            }
        }
        return best;
    }

    private static double boundaryWobble(int x, int z) {
        int transition = ExpandiaRadiusState.getTransitionWidth();
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

    @Override
    public void fillArray(double[] array, ContextProvider context) {
        context.fillAllDirectly(array, this);
    }

    @Override
    public DensityFunction mapAll(Visitor visitor) {
        return visitor.apply(new ExpandiaLandMaskDensityFunction(this.wrapped.mapAll(visitor), this.mode, this.seaLevel));
    }

    @Override
    public double minValue() {
        return OCEAN_CONTINENTS;
    }

    @Override
    public double maxValue() {
        return this.wrapped.maxValue();
    }

    @Override
    public KeyDispatchDataCodec<? extends DensityFunction> codec() {
        return KEY_CODEC;
    }
}
