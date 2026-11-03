package com.expandia.worldgen;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.Fluids;

import java.util.EnumSet;

/**
 * Nether biome features and surface rules are built for the lava sea around y=32.
 * Anything they leave under that sea (nylium, gravel, soul sand, basalt pillars, vines)
 * is replaced so an island reads as dry land sitting on the lava, not inside it.
 */
public final class ExpandiaNetherSanitizer {

    private ExpandiaNetherSanitizer() {
    }

    public static void sanitize(WorldGenLevel level, ChunkAccess chunk) {
        int sea = level.getSeaLevel();
        int minY = chunk.getMinBuildHeight();
        int maxY = chunk.getMaxBuildHeight();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        int originX = chunk.getPos().getMinBlockX();
        int originZ = chunk.getPos().getMinBlockZ();

        for (int localX = 0; localX < 16; localX++) {
            for (int localZ = 0; localZ < 16; localZ++) {
                int x = originX + localX;
                int z = originZ + localZ;
                boolean underLava = false;
                for (int y = maxY - 1; y >= minY; y--) {
                    cursor.set(x, y, z);
                    BlockState block = chunk.getBlockState(cursor);
                    if (isLava(block)) {
                        if (y <= sea) {
                            underLava = true;
                        }
                        continue;
                    }
                    if (y > sea) {
                        continue;
                    }
                    if (!underLava || isFoundation(block)) {
                        continue;
                    }
                    BlockState below = y - 1 >= minY
                            ? chunk.getBlockState(cursor.set(x, y - 1, z))
                            : Blocks.BEDROCK.defaultBlockState();
                    cursor.set(x, y, z);
                    boolean hanging = !block.isSolid() || !below.isSolid() || isLava(below);
                    level.setBlock(cursor, hanging ? Blocks.LAVA.defaultBlockState() : Blocks.NETHERRACK.defaultBlockState(), 2);
                }
            }
        }
        Heightmap.primeHeightmaps(chunk, EnumSet.of(
                Heightmap.Types.MOTION_BLOCKING,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                Heightmap.Types.OCEAN_FLOOR,
                Heightmap.Types.WORLD_SURFACE
        ));
    }

    private static boolean isFoundation(BlockState state) {
        return state.is(Blocks.BEDROCK) || state.is(Blocks.NETHERRACK);
    }

    private static boolean isLava(BlockState state) {
        return state.is(Blocks.LAVA) || state.getFluidState().is(Fluids.LAVA);
    }
}
