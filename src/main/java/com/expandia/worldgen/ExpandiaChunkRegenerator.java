package com.expandia.worldgen;

import com.expandia.Expandia;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Island disc geometry and client chunk resend helpers. */
public final class ExpandiaChunkRegenerator {

    private static final int COAST_BLEND_BLOCKS = 24;

    private ExpandiaChunkRegenerator() {
    }

    public static List<ChunkPos> collectExpansionRing(int innerRadius, int outerRadius) {
        int regenInner = Math.max(0, innerRadius - 8);
        int regenOuter = outerRadius + COAST_BLEND_BLOCKS;
        return collectAnnulusChunks(regenInner, regenOuter);
    }

    /** All chunks covering one island: a disc of {@code radius + coast} around the centre. */
    public static List<ChunkPos> collectIslandDisc(double centerX, double centerZ, int radius) {
        int reach = radius + COAST_BLEND_BLOCKS;
        int minChunkX = ((int) Math.floor(centerX - reach)) >> 4;
        int maxChunkX = ((int) Math.floor(centerX + reach)) >> 4;
        int minChunkZ = ((int) Math.floor(centerZ - reach)) >> 4;
        int maxChunkZ = ((int) Math.floor(centerZ + reach)) >> 4;

        List<ChunkPos> result = new ArrayList<>();
        double reachSq = (double) reach * reach;
        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                ChunkPos pos = new ChunkPos(chunkX, chunkZ);
                double dx = pos.getMiddleBlockX() - centerX;
                double dz = pos.getMiddleBlockZ() - centerZ;
                if (dx * dx + dz * dz <= reachSq) {
                    result.add(pos);
                }
            }
        }
        return result;
    }

    public static List<ChunkPos> collectAnnulusChunks(int innerRadius, int outerRadius) {
        int margin = outerRadius + 16;
        int minChunk = (-margin) >> 4;
        int maxChunk = margin >> 4;

        List<ChunkPos> result = new ArrayList<>();
        for (int chunkX = minChunk; chunkX <= maxChunk; chunkX++) {
            for (int chunkZ = minChunk; chunkZ <= maxChunk; chunkZ++) {
                ChunkPos pos = new ChunkPos(chunkX, chunkZ);
                if (intersectsAnnulus(pos, innerRadius, outerRadius)) {
                    result.add(pos);
                }
            }
        }
        return result;
    }

    public static List<ChunkPos> sortedByBorderDistance(List<ChunkPos> chunks, int targetRadius) {
        return chunks.stream()
                .sorted(Comparator.comparingDouble(pos -> ringDistance(pos, targetRadius)))
                .toList();
    }

    static double ringDistance(ChunkPos pos, int targetRadius) {
        double centreX = pos.getMiddleBlockX() + 0.5D;
        double centreZ = pos.getMiddleBlockZ() + 0.5D;
        double distance = Math.sqrt(centreX * centreX + centreZ * centreZ);
        return Math.abs(distance - targetRadius);
    }

    public static boolean isFullyGenerated(ServerLevel level, ChunkPos pos) {
        ChunkHolder holder = level.getChunkSource().chunkMap.getVisibleChunkIfPresent(pos.pack());
        if (holder == null) {
            return false;
        }

        ChunkAccess chunk = holder.getLatestChunk();
        return chunk != null && !chunk.getPersistedStatus().isBefore(ChunkStatus.FULL);
    }

    public static void pushToClients(ServerLevel level, ChunkPos pos, ServerPlayer viewer) {
        ChunkMap chunkMap = level.getChunkSource().chunkMap;
        LevelChunk chunk = chunkMap.getChunkToSend(pos.pack());
        if (chunk == null) {
            ChunkHolder holder = chunkMap.getVisibleChunkIfPresent(pos.pack());
            if (holder != null) {
                chunk = holder.getTickingChunk();
            }
        }
        if (chunk == null) {
            return;
        }

        ChunkHolder holder = chunkMap.getVisibleChunkIfPresent(pos.pack());
        if (holder != null) {
            holder.broadcastChanges(chunk);
        }

        try {
            chunkMap.waitForLightBeforeSending(pos, 0);
            viewer.connection.send(new ClientboundLevelChunkWithLightPacket(
                    chunk,
                    level.getLightEngine(),
                    null,
                    null
            ));
            viewer.connection.chunkSender.dropChunk(viewer, pos);
            viewer.connection.chunkSender.markChunkPendingToSend(chunk);
            viewer.connection.chunkSender.sendNextChunks(viewer);
            chunkMap.resendBiomesForChunks(List.of(chunk));
            level.getLightEngine().propagateLightSources(pos);
        } catch (Exception e) {
            Expandia.LOGGER.debug("Expandia: could not push chunk {} to {}", pos, viewer.getName().getString(), e);
        }
    }

    private static boolean intersectsAnnulus(ChunkPos pos, int innerRadius, int outerRadius) {
        int minX = pos.getMinBlockX();
        int maxX = pos.getMaxBlockX();
        int minZ = pos.getMinBlockZ();
        int maxZ = pos.getMaxBlockZ();

        double closest = closestDistanceToOrigin(minX, maxX, minZ, maxZ);
        double farthest = farthestDistanceToOrigin(minX, maxX, minZ, maxZ);
        return closest <= outerRadius && farthest >= innerRadius;
    }

    private static double closestDistanceToOrigin(int minX, int maxX, int minZ, int maxZ) {
        double nearestX = clampToSegment(minX, maxX, 0);
        double nearestZ = clampToSegment(minZ, maxZ, 0);
        return Math.sqrt(nearestX * nearestX + nearestZ * nearestZ);
    }

    private static double farthestDistanceToOrigin(int minX, int maxX, int minZ, int maxZ) {
        double dx = Math.max(Math.abs(minX), Math.abs(maxX));
        double dz = Math.max(Math.abs(minZ), Math.abs(maxZ));
        return Math.sqrt(dx * dx + dz * dz);
    }

    private static double clampToSegment(int min, int max, int value) {
        if (value < min) {
            return min;
        }
        if (value > max) {
            return max;
        }
        return value;
    }
}
