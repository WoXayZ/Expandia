package com.expandia.registry;

import com.expandia.ExpandiaConstants;
import com.expandia.worldgen.ExpandiaLandMaskDensityFunction;
import com.expandia.worldgen.ExpandiaOceanBiomeSource;
import com.expandia.worldgen.ExpandiaStripWorldgenModifier;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.world.BiomeModifier;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

public final class ExpandiaRegistries {

    private ExpandiaRegistries() {
    }

    public static final DeferredRegister<MapCodec<? extends DensityFunction>> DENSITY_FUNCTION_TYPES =
            DeferredRegister.create(Registries.DENSITY_FUNCTION_TYPE, ExpandiaConstants.MOD_ID);

    public static final DeferredRegister<MapCodec<? extends BiomeSource>> BIOME_SOURCE_TYPES =
            DeferredRegister.create(Registries.BIOME_SOURCE, ExpandiaConstants.MOD_ID);

    public static final DeferredRegister<MapCodec<? extends BiomeModifier>> BIOME_MODIFIER_SERIALIZERS =
            DeferredRegister.create(NeoForgeRegistries.Keys.BIOME_MODIFIER_SERIALIZERS, ExpandiaConstants.MOD_ID);

    public static final DeferredHolder<MapCodec<? extends DensityFunction>, MapCodec<ExpandiaLandMaskDensityFunction>> LAND_MASK =
            DENSITY_FUNCTION_TYPES.register("land_mask", () -> ExpandiaLandMaskDensityFunction.CODEC);

    public static final DeferredHolder<MapCodec<? extends BiomeSource>, MapCodec<ExpandiaOceanBiomeSource>> OCEAN_BIOME_SOURCE =
            BIOME_SOURCE_TYPES.register("ocean", () -> ExpandiaOceanBiomeSource.CODEC);

    public static final DeferredHolder<MapCodec<? extends BiomeModifier>, MapCodec<ExpandiaStripWorldgenModifier>> STRIP_WORLDGEN =
            BIOME_MODIFIER_SERIALIZERS.register("strip_worldgen", () -> ExpandiaStripWorldgenModifier.CODEC);

    public static void register(IEventBus modEventBus) {
        DENSITY_FUNCTION_TYPES.register(modEventBus);
        BIOME_SOURCE_TYPES.register(modEventBus);
        BIOME_MODIFIER_SERIALIZERS.register(modEventBus);
    }
}
