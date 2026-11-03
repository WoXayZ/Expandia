package com.expandia.worldgen;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.neoforged.neoforge.common.world.BiomeModifier;
import net.neoforged.neoforge.common.world.ModifiableBiomeInfo;

public record ExpandiaStripWorldgenModifier(HolderSet<Biome> biomes) implements BiomeModifier {

    public static final MapCodec<ExpandiaStripWorldgenModifier> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Biome.LIST_CODEC.fieldOf("biomes").forGetter(ExpandiaStripWorldgenModifier::biomes)
    ).apply(instance, ExpandiaStripWorldgenModifier::new));

    @Override
    public void modify(Holder<Biome> biome, Phase phase, ModifiableBiomeInfo.BiomeInfo.Builder builder) {
        if (phase != Phase.REMOVE || !this.biomes.contains(biome)) {
            return;
        }
        for (GenerationStep.Carving step : GenerationStep.Carving.values()) {
            builder.getGenerationSettings().getCarvers(step).clear();
        }
        // Snow is applied later from the island biome, not from this chunk's palette.
        // Vanilla freeze_top_layer follows whatever biome ended up in the chunk, which
        // cuts a snow square exactly on the chunk border.
        for (GenerationStep.Decoration step : GenerationStep.Decoration.values()) {
            if (step == GenerationStep.Decoration.VEGETAL_DECORATION) {
                continue;
            }
            builder.getGenerationSettings().getFeatures(step).clear();
        }
    }

    @Override
    public MapCodec<? extends BiomeModifier> codec() {
        return CODEC;
    }
}
