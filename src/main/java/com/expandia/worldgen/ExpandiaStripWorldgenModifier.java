package com.expandia.worldgen;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.neoforged.neoforge.common.world.BiomeModifier;
import net.neoforged.neoforge.common.world.ModifiableBiomeInfo;

import java.util.EnumSet;
import java.util.Set;

public record ExpandiaStripWorldgenModifier(HolderSet<Biome> biomes) implements BiomeModifier {

    public static final MapCodec<ExpandiaStripWorldgenModifier> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Biome.LIST_CODEC.fieldOf("biomes").forGetter(ExpandiaStripWorldgenModifier::biomes)
    ).apply(instance, ExpandiaStripWorldgenModifier::new));

    private static final Set<GenerationStep.Decoration> KEEP = EnumSet.of(
            GenerationStep.Decoration.VEGETAL_DECORATION,
            GenerationStep.Decoration.TOP_LAYER_MODIFICATION
    );

    @Override
    public void modify(Holder<Biome> biome, Phase phase, ModifiableBiomeInfo.BiomeInfo.Builder builder) {
        if (phase != Phase.REMOVE || !this.biomes.contains(biome)) {
            return;
        }
        for (GenerationStep.Carving step : GenerationStep.Carving.values()) {
            builder.getGenerationSettings().getCarvers(step).clear();
        }
        for (GenerationStep.Decoration step : GenerationStep.Decoration.values()) {
            if (!KEEP.contains(step)) {
                builder.getGenerationSettings().getFeatures(step).clear();
            }
        }
    }

    @Override
    public MapCodec<? extends BiomeModifier> codec() {
        return CODEC;
    }
}
