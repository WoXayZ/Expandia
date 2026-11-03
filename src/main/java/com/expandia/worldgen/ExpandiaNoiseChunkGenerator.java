package com.expandia.worldgen;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.DensityFunctions;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.WorldGenerationContext;
import net.minecraft.world.level.levelgen.blending.Blender;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Same noise generator as vanilla, but never blends with neighbour chunks.
 * Blending cuts island shores into straight walls wherever a chunk was missing.
 */
public final class ExpandiaNoiseChunkGenerator extends NoiseBasedChunkGenerator {

    private static final Climate.Sampler BIOME_SAMPLER = new Climate.Sampler(
            DensityFunctions.zero(),
            DensityFunctions.zero(),
            DensityFunctions.zero(),
            DensityFunctions.zero(),
            DensityFunctions.zero(),
            DensityFunctions.zero(),
            List.of()
    );

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
        unifyIslandBiomes(chunk, random.sampler());
        super.buildSurface(chunk, context, random, structureManager, biomeManager, biomes, Blender.empty());
        layBeach(chunk);
        if (getBiomeSource() instanceof ExpandiaOceanBiomeSource islands) {
            ExpandiaSnowNormalizer.apply(chunk, islands, generatorSettings().value().seaLevel());
        }
    }

    /**
     * The shore slope is only a few blocks above the water. Sand there reads as a beach
     * instead of another grass step.
     */
    private void layBeach(ChunkAccess chunk) {
        if (!ExpandiaRadiusState.OVERWORLD.isActive()
                || generatorSettings().value().defaultBlock().is(Blocks.NETHERRACK)) {
            return;
        }
        int sea = generatorSettings().value().seaLevel();
        ChunkPos pos = chunk.getPos();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        BlockState sand = Blocks.SAND.defaultBlockState();
        for (int localX = 0; localX < 16; localX++) {
            for (int localZ = 0; localZ < 16; localZ++) {
                int x = pos.getMinBlockX() + localX;
                int z = pos.getMinBlockZ() + localZ;
                double blend = ExpandiaRadiusState.oceanBlend(x, z);
                if (blend < 0.12D || blend >= 1.0D) {
                    continue;
                }
                int surface = findSurface(chunk, cursor, x, z, sea);
                if (surface == Integer.MIN_VALUE || surface > sea + 4 || surface < sea - 2) {
                    continue;
                }
                for (int i = 0; i < 3; i++) {
                    int y = surface - i;
                    BlockState state = chunk.getBlockState(cursor.set(x, y, z));
                    if (!isShoreSoil(state)) {
                        break;
                    }
                    chunk.setBlockState(cursor, sand, false);
                }
            }
        }
    }

    private static int findSurface(ChunkAccess chunk, BlockPos.MutableBlockPos cursor, int x, int z, int sea) {
        int minY = chunk.getMinBuildHeight();
        for (int y = Math.min(chunk.getMaxBuildHeight() - 1, sea + 48); y > minY; y--) {
            BlockState state = chunk.getBlockState(cursor.set(x, y, z));
            if (state.isAir() || state.is(Blocks.WATER)) {
                continue;
            }
            return y;
        }
        return Integer.MIN_VALUE;
    }

    private static boolean isShoreSoil(BlockState state) {
        return state.is(Blocks.GRASS_BLOCK)
                || state.is(Blocks.DIRT)
                || state.is(Blocks.COARSE_DIRT)
                || state.is(Blocks.ROOTED_DIRT)
                || state.is(Blocks.PODZOL)
                || state.is(Blocks.MUD)
                || state.is(Blocks.SAND)
                || state.is(Blocks.STONE);
    }

    @Override
    public void applyBiomeDecoration(WorldGenLevel level, ChunkAccess chunk, StructureManager structureManager) {
        if (!isNether(level)) {
            unifyIslandBiomes(chunk, BIOME_SAMPLER);
        }
        super.applyBiomeDecoration(level, chunk, structureManager);
        if (isNether(level)) {
            ExpandiaNetherSanitizer.sanitize(level, chunk);
            return;
        }
        if (getBiomeSource() instanceof ExpandiaOceanBiomeSource islands) {
            ExpandiaSnowNormalizer.apply(chunk, islands, generatorSettings().value().seaLevel());
        }
    }

    private void unifyIslandBiomes(ChunkAccess chunk, Climate.Sampler sampler) {
        if (getBiomeSource() instanceof ExpandiaOceanBiomeSource islands && !islands.nether()) {
            chunk.fillBiomesFromNoise(islands, sampler);
        }
    }

    private static boolean isNether(WorldGenLevel level) {
        if (level instanceof ServerLevel serverLevel) {
            return Level.NETHER.equals(serverLevel.dimension());
        }
        if (level instanceof WorldGenRegion region) {
            return Level.NETHER.equals(region.getLevel().dimension());
        }
        return false;
    }
}
