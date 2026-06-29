package com.expandia.worldgen;

import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;

import java.lang.reflect.Method;

final class ExpandiaServerChunkCacheInternals {

    private static final Method RUN_DISTANCE_MANAGER_UPDATES;
    private static final Method GET_UPDATING_CHUNK_IF_PRESENT;

    static {
        try {
            RUN_DISTANCE_MANAGER_UPDATES = ServerChunkCache.class.getDeclaredMethod("runDistanceManagerUpdates");
            RUN_DISTANCE_MANAGER_UPDATES.setAccessible(true);
            GET_UPDATING_CHUNK_IF_PRESENT = ChunkMap.class.getDeclaredMethod("getUpdatingChunkIfPresent", long.class);
            GET_UPDATING_CHUNK_IF_PRESENT.setAccessible(true);
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private ExpandiaServerChunkCacheInternals() {
    }

    static void runDistanceManagerUpdates(ServerLevel level) {
        try {
            ServerChunkCache cache = level.getChunkSource();
            RUN_DISTANCE_MANAGER_UPDATES.invoke(cache);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Failed to update chunk distance manager", e);
        }
    }

    /** {@code true} once the chunk has no live (updating) holder, i.e. it is fully unloaded. */
    static boolean isUnloaded(ServerLevel level, long chunkPos) {
        try {
            return GET_UPDATING_CHUNK_IF_PRESENT.invoke(level.getChunkSource().chunkMap, chunkPos) == null;
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Failed to query chunk holder", e);
        }
    }
}
