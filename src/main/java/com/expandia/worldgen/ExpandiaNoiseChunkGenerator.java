package com.expandia.worldgen;

import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.WorldGenerationContext;
import net.minecraft.world.level.levelgen.blending.Blender;

import java.util.concurrent.CompletableFuture;

/**
 * Same noise generator as vanilla, but never blends with missing neighbour chunks.
 * At (0, 0) that blending otherwise cuts the spawn island along an axis.
 */
public final class ExpandiaNoiseChunkGenerator extends NoiseBasedChunkGenerator {

    public ExpandiaNoiseChunkGenerator(BiomeSource biomeSource, Holder<NoiseGeneratorSettings> settings) {
        super(biomeSource, settings);
    }

    @Override
    public CompletableFuture<ChunkAccess> createBiomes(
            RandomState randomState, Blender blender, StructureManager structureManager, ChunkAccess chunk) {
        return super.createBiomes(randomState, Blender.empty(), structureManager, chunk);
    }

    @Override
    public CompletableFuture<ChunkAccess> fillFromNoise(
            Blender blender, RandomState randomState, StructureManager structureManager, ChunkAccess chunk) {
        return super.fillFromNoise(Blender.empty(), randomState, structureManager, chunk);
    }

    @Override
    public void buildSurface(
            ChunkAccess chunk,
            WorldGenerationContext context,
            RandomState random,
            StructureManager structureManager,
            BiomeManager biomeManager,
            Registry<Biome> biomes,
            Blender blender) {
        super.buildSurface(chunk, context, random, structureManager, biomeManager, biomes, Blender.empty());
    }
}
