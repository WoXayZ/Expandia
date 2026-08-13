package com.expandia.worldgen;

import com.expandia.Expandia;
import com.expandia.ExpandiaConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

@EventBusSubscriber(modid = ExpandiaConstants.MOD_ID)
public final class ExpandiaPortalEvents {

    private ExpandiaPortalEvents() {
    }

    @SubscribeEvent
    public static void onChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (!Level.NETHER.equals(event.getTo()) && !Level.OVERWORLD.equals(event.getTo())) {
            return;
        }
        snapToNearestIsland(player);
    }

    private static void snapToNearestIsland(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        ExpandiaRadiusState.DimState state = ExpandiaRadiusState.of(level.dimension());
        int px = player.getBlockX();
        int pz = player.getBlockZ();
        if (state.isWithinIslandReach(px, pz)) {
            return;
        }

        double[] center = state.nearestCenter(px, pz);
        int x = (int) Math.round(center[0]);
        int z = (int) Math.round(center[1]);
        int y = standY(level, state, x, z);
        BlockPos stand = new BlockPos(x, y, z);
        prepareStand(level, stand);
        try {
            level.getPortalForcer().createPortal(stand, player.getDirection().getAxis().isHorizontal()
                    ? player.getDirection().getAxis()
                    : Direction.Axis.X);
        } catch (Exception e) {
            Expandia.LOGGER.debug("Expandia: could not create arrival portal at {}", stand, e);
        }
        player.teleportTo(x + 0.5D, y + 1.0D, z + 0.5D);
        Expandia.LOGGER.info("Expandia: snapped portal arrival in {} to island ({}, {}, {})",
                level.dimension().location(), x, y, z);
    }

    private static int standY(ServerLevel level, ExpandiaRadiusState.DimState state, int x, int z) {
        int mapped = state.islandSurfaceY(x, z, level.getSeaLevel());
        if (mapped != Integer.MIN_VALUE) {
            return mapped;
        }
        int heightmap = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        return Math.max(level.getSeaLevel() + 1, heightmap);
    }

    private static void prepareStand(ServerLevel level, BlockPos stand) {
        BlockState platform = Level.NETHER.equals(level.dimension())
                ? Blocks.NETHERRACK.defaultBlockState()
                : Blocks.STONE.defaultBlockState();
        if (level.getBlockState(stand).isAir() || !level.getBlockState(stand).isSolid()) {
            level.setBlockAndUpdate(stand, platform);
        }
        level.setBlockAndUpdate(stand.above(), Blocks.AIR.defaultBlockState());
        level.setBlockAndUpdate(stand.above(2), Blocks.AIR.defaultBlockState());
    }
}
