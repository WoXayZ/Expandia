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
            case CONTINENTS -> maskContinents(context, radius);
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
        double vanilla = this.wrapped.compute(context);
        double blend = ExpandiaRadiusState.NETHER.oceanBlend(context.blockX(), context.blockZ());
        int y = context.blockY();
        int sea = this.seaLevel;
        double ocean = lavaOcean(y, sea);
        if (blend >= 1.0D) {
            return ocean;
        }

        int roof = sea + 40;
        if (y >= roof + 16) {
            vanilla = -1.0D;
        } else if (y > roof) {
            vanilla = Mth.lerp((double) (y - roof) / 16.0D, vanilla, -1.0D);
        }

        if (blend <= 0.0D) {
            return vanilla;
        }
        return Mth.lerp(blend, vanilla, ocean);
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
        return ExpandiaRadiusState.oceanBlend(x, z);
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
        double dist = ExpandiaRadiusState.nearestCenterDistance(x, z);
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
