package com.expandia.worldgen;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SnowyDirtBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;

/**
 * Vanilla snow follows the biome stored in the chunk and the height at each column,
 * so one side of an island is white and the other stays grass. Every column of an
 * island uses the biome at that island's center.
 */
public final class ExpandiaSnowNormalizer {

    private static final float SNOW_TEMPERATURE = 0.15F;

    private ExpandiaSnowNormalizer() {
    }

    public static void apply(ChunkAccess chunk, ExpandiaOceanBiomeSource biomes, int seaLevel) {
        if (!ExpandiaRadiusState.OVERWORLD.isActive() || biomes.nether()) {
            return;
        }
        ChunkPos pos = chunk.getPos();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        BlockState air = Blocks.AIR.defaultBlockState();
        BlockState snowLayer = Blocks.SNOW.defaultBlockState();
        BlockState grass = Blocks.GRASS_BLOCK.defaultBlockState();
        BlockState sand = Blocks.SAND.defaultBlockState();
        int maxY = Math.min(chunk.getMaxBuildHeight() - 1, seaLevel + 160);
        int minY = Math.max(chunk.getMinBuildHeight(), seaLevel - 16);

        for (int localX = 0; localX < 16; localX++) {
            for (int localZ = 0; localZ < 16; localZ++) {
                int x = pos.getMinBlockX() + localX;
                int z = pos.getMinBlockZ() + localZ;
                boolean snow = snowAt(biomes, x, z);
                if (!snow) {
                    wipeSnow(chunk, cursor, x, z, maxY, minY, seaLevel, air, grass, sand);
                    continue;
                }
                int ground = Integer.MIN_VALUE;
                BlockState groundState = air;
                for (int y = maxY; y >= minY; y--) {
                    BlockState state = chunk.getBlockState(cursor.set(x, y, z));
                    if (state.isAir() || state.is(Blocks.SNOW) || isCover(state)) {
                        continue;
                    }
                    if (!state.getFluidState().isEmpty()) {
                        break;
                    }
                    ground = y;
                    groundState = state;
                    break;
                }
                if (ground == Integer.MIN_VALUE) {
                    continue;
                }
                if (groundState.is(Blocks.SNOW_BLOCK) || groundState.is(Blocks.POWDER_SNOW) || groundState.is(Blocks.ICE) || groundState.is(Blocks.PACKED_ICE)) {
                    continue;
                }
                int aboveY = ground + 1;
                if (aboveY > maxY) {
                    continue;
                }
                BlockState above = chunk.getBlockState(cursor.set(x, aboveY, z));
                if (!above.isAir() && !above.is(Blocks.SNOW) && !isCover(above)) {
                    continue;
                }
                chunk.setBlockState(cursor, snowLayer, false);
                markSnowy(chunk, cursor, x, ground, z, true);
            }
        }
    }

    private static void wipeSnow(
            ChunkAccess chunk,
            BlockPos.MutableBlockPos cursor,
            int x,
            int z,
            int maxY,
            int minY,
            int seaLevel,
            BlockState air,
            BlockState grass,
            BlockState sand) {
        double blend = ExpandiaRadiusState.oceanBlend(x, z);
        for (int y = maxY; y >= minY; y--) {
            BlockState state = chunk.getBlockState(cursor.set(x, y, z));
            if (state.is(Blocks.SNOW)) {
                chunk.setBlockState(cursor, air, false);
                continue;
            }
            if (state.is(Blocks.SNOW_BLOCK) || state.is(Blocks.POWDER_SNOW)) {
                BlockState replacement = blend >= 0.12D && y <= seaLevel + 4 ? sand : grass;
                chunk.setBlockState(cursor, replacement, false);
                continue;
            }
            if (state.hasProperty(SnowyDirtBlock.SNOWY) && state.getValue(SnowyDirtBlock.SNOWY)) {
                chunk.setBlockState(cursor, state.setValue(SnowyDirtBlock.SNOWY, false), false);
            }
        }
    }

    private static boolean snowAt(ExpandiaOceanBiomeSource biomes, int x, int z) {
        ExpandiaRadiusState.DimState overworld = ExpandiaRadiusState.OVERWORLD;
        if (!overworld.isWithinIslandReach(x, z)) {
            return false;
        }
        double[] center = overworld.nearestCenter(x, z);
        Holder<Biome> island = biomes.biomeAt(Mth.floor(center[0]), Mth.floor(center[1]));
        return island != null && island.value().getBaseTemperature() < SNOW_TEMPERATURE;
    }

    private static boolean isCover(BlockState state) {
        return !state.isSolid() && state.canBeReplaced() && state.getFluidState().isEmpty();
    }

    private static void markSnowy(ChunkAccess chunk, BlockPos.MutableBlockPos cursor, int x, int y, int z, boolean snowy) {
        BlockState state = chunk.getBlockState(cursor.set(x, y, z));
        if (state.hasProperty(SnowyDirtBlock.SNOWY) && state.getValue(SnowyDirtBlock.SNOWY) != snowy) {
            chunk.setBlockState(cursor, state.setValue(SnowyDirtBlock.SNOWY, snowy), false);
        }
    }
}
