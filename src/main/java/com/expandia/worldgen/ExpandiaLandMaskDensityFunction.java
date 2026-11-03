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
    private static final double COAST_LAND_FLOOR = -0.08D;

    /**
     * Surface height is about {@code 128 + offset * 128}. Sea level 63 is -0.508.
     * Hills stay inland; the rim is only a few blocks above the water so the shore
     * can slope down instead of falling off in dirt terraces.
     */
    private static final double HILL_OFFSET_MIN = -0.46D;
    private static final double HILL_OFFSET_MAX = -0.36D;
    private static final double RIM_OFFSET = -0.47D;

    public enum MaskMode implements StringRepresentable {
        CONTINENTS("continents"),
        EROSION("erosion"),
        TEMPERATURE("temperature"),
        VEGETATION("vegetation"),
        OFFSET("offset"),
        FINAL("final");

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
                            .forGetter(ExpandiaLandMaskDensityFunction::wrapped),
                    Codec.INT.optionalFieldOf("sea_level", 63).forGetter(ExpandiaLandMaskDensityFunction::seaLevel)
            ).apply(instance, (mode, wrapped, seaLevel) -> new ExpandiaLandMaskDensityFunction(wrapped, mode, seaLevel)));

    private static final KeyDispatchDataCodec<ExpandiaLandMaskDensityFunction> KEY_CODEC =
            KeyDispatchDataCodec.of(CODEC);

    @Override
    public double compute(FunctionContext context) {
        ExpandiaRadiusState.DimState state = state();
        if (!state.isActive() || state.getIslandRadius() <= 0) {
            return this.wrapped.compute(context);
        }

        int radius = state.getIslandRadius();
        return switch (this.mode) {
            case CONTINENTS -> maskContinents(context);
            case EROSION -> maskErosion(context, radius);
            case TEMPERATURE -> maskTemperature(context, radius);
            case VEGETATION -> maskVegetation(context, radius);
            case OFFSET -> maskOffset(context, radius);
            case FINAL -> maskFinal(context);
        };
    }

    private ExpandiaRadiusState.DimState state() {
        return this.mode == MaskMode.FINAL ? ExpandiaRadiusState.NETHER : ExpandiaRadiusState.OVERWORLD;
    }

    private double maskFinal(FunctionContext context) {
        int x = context.blockX();
        int y = context.blockY();
        int z = context.blockZ();
        int sea = this.seaLevel;
        double blend = ExpandiaRadiusState.NETHER.oceanBlend(x, z);
        double ocean = lavaOcean(y, sea);
        if (blend >= 1.0D) {
            return ocean;
        }

        int surface = ExpandiaRadiusState.NETHER.islandSurfaceY(x, z, sea);
        // Hold a dry shell above the lava, then drop into the sea only at the outer rim.
        double rim = Mth.lerp(smootherStep(0.0D, 0.85D, blend), surface, sea + 5.0D);
        double island = (rim - y) / 4.0D;
        if (blend <= 0.75D) {
            return island;
        }
        return Mth.lerp(smootherStep(0.75D, 1.0D, blend), island, ocean);
    }

    private static double lavaOcean(int y, int sea) {
        if (y < sea - 10) {
            return 1.0D;
        }
        if (y <= sea) {
            return -0.55D;
        }
        return -1.0D;
    }

    private double maskOffset(FunctionContext context, int radius) {
        double vanillaOffset = this.wrapped.compute(context);
        int x = context.blockX();
        int z = context.blockZ();
        double blend = oceanBlend(x, z);
        if (blend >= 1.0D) {
            return vanillaOffset;
        }
        double dist = ExpandiaRadiusState.nearestCenterDistance(x, z);
        double edge = Math.max(1.0D, radius);
        double inland = 1.0D - smootherStep(edge * 0.2D, edge, dist);
        double hill = Mth.clamp(vanillaOffset, HILL_OFFSET_MIN, HILL_OFFSET_MAX);
        double islandOffset = Mth.lerp(inland, RIM_OFFSET, hill);
        if (blend <= 0.0D) {
            return islandOffset;
        }
        // Sea level, then a short underwater drop. The above-water part stays a beach.
        double seaOffset = (this.seaLevel - 128.0D) / 128.0D;
        double beach = Mth.lerp(smootherStep(0.0D, 0.8D, blend), islandOffset, seaOffset);
        double oceanFloor = Math.min(vanillaOffset, seaOffset - 0.12D);
        return Mth.lerp(smootherStep(0.8D, 1.0D, blend), beach, oceanFloor);
    }

    private static double oceanBlend(int x, int z) {
        return ExpandiaRadiusState.oceanBlend(x, z);
    }

    private double maskContinents(FunctionContext context) {
        double vanilla = this.wrapped.compute(context);
        double blend = oceanBlend(context.blockX(), context.blockZ());
        double floor = interiorLandFloor(context.blockX(), context.blockZ());
        // Cap inland continents so the coast spline cannot jump into mountain terrain.
        double land = Mth.clamp(Math.max(vanilla, floor), -0.2D, 0.22D);
        return Mth.lerp(blend, land, OCEAN_CONTINENTS);
    }

    private static double interiorLandFloor(int x, int z) {
        double dist = ExpandiaRadiusState.nearestCenterDistance(x, z);
        int radius = Math.max(1, ExpandiaRadiusState.getIslandRadius());
        double t = smootherStep(radius * 0.62D, radius, dist);
        return Mth.lerp(t, CORE_LAND_FLOOR, COAST_LAND_FLOOR);
    }

    private double maskErosion(FunctionContext context, int radius) {
        double vanilla = this.wrapped.compute(context);
        return Mth.lerp(oceanBlend(context.blockX(), context.blockZ()), vanilla, 0.0D);
    }

    private double maskTemperature(FunctionContext context, int radius) {
        return this.wrapped.compute(context);
    }

    private double maskVegetation(FunctionContext context, int radius) {
        return this.wrapped.compute(context);
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
        double wrappedMin = this.wrapped.minValue();
        if (this.mode == MaskMode.FINAL) {
            return Math.min(-1.0D, wrappedMin);
        }
        return Math.min(OCEAN_CONTINENTS, wrappedMin);
    }

    @Override
    public double maxValue() {
        double wrappedMax = this.wrapped.maxValue();
        if (this.mode == MaskMode.FINAL) {
            return Math.max(64.0D, wrappedMax);
        }
        return Math.max(wrappedMax, CORE_LAND_FLOOR);
    }

    @Override
    public KeyDispatchDataCodec<? extends DensityFunction> codec() {
        return KEY_CODEC;
    }
}
