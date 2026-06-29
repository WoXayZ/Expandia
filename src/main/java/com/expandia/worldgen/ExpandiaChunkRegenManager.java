package com.expandia.worldgen;

import com.expandia.Expandia;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.storage.IOWorker;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

/** Unloads, wipes and regenerates island discs live. Jobs are queued and run one at a time. */
public final class ExpandiaChunkRegenManager {

    private static final int SHRUNK_VIEW_DISTANCE = 2;
    private static final int MAX_UNLOAD_TICKS = 2_400;
    private static final int UNLOAD_STALL_LIMIT = 100;
    private static final int MAX_GENERATE_TICKS = 600;
    private static final int SETTLE_TICKS = 5;

    private enum Phase { UNLOAD, GENERATE, RESEND, DONE }

    private static final Deque<ExpandiaChunkRegenManager> QUEUE = new ArrayDeque<>();
    private static ExpandiaChunkRegenManager active;

    private final ServerLevel level;
    private final List<ChunkPos> chunks;
    private int restoreViewDistance;
    private boolean shrinkView;
    private Phase phase;
    private int phaseTicks;
    private int settleTicks;
    private int lastRemaining = Integer.MAX_VALUE;
    private int stallTicks;

    private ExpandiaChunkRegenManager(ServerLevel level, List<ChunkPos> chunks) {
        this.level = level;
        this.chunks = chunks;
        this.phase = Phase.UNLOAD;
    }

    public static void revealIsland(ServerLevel level, double centerX, double centerZ, int radius) {
        begin(level, ExpandiaChunkRegenerator.collectIslandDisc(centerX, centerZ, radius));
    }

    public static void begin(ServerLevel level, List<ChunkPos> chunks) {
        if (chunks.isEmpty()) {
            return;
        }
        QUEUE.add(new ExpandiaChunkRegenManager(level, List.copyOf(chunks)));
    }

    public static boolean isRunning() {
        return active != null || !QUEUE.isEmpty();
    }

    public static void tick(ServerLevel level) {
        if (active == null) {
            active = QUEUE.poll();
            if (active == null) {
                return;
            }
            active.start();
        }
        if (active.level != level) {
            return;
        }
        active.step();
        if (active.phase == Phase.DONE) {
            active = null;
        }
    }

    private void start() {
        this.restoreViewDistance = this.level.getServer().getPlayerList().getViewDistance();
        this.shrinkView = this.chunks.stream()
                .anyMatch(pos -> this.level.getChunkSource().hasChunk(pos.x(), pos.z()));
        this.phase = Phase.UNLOAD;
        this.phaseTicks = 0;
        this.settleTicks = 0;
        this.stallTicks = 0;
        this.lastRemaining = Integer.MAX_VALUE;
        if (this.shrinkView) {
            this.level.getChunkSource().setViewDistance(SHRUNK_VIEW_DISTANCE);
        }
        Expandia.LOGGER.info("Expandia: revealing a new island ({} chunks, alreadyLoaded={})",
                this.chunks.size(), this.shrinkView);
    }

    private void step() {
        ExpandiaServerChunkCacheInternals.runDistanceManagerUpdates(this.level);
        this.level.getChunkSource().pollTask();
        this.phaseTicks++;

        switch (this.phase) {
            case UNLOAD -> stepUnload();
            case GENERATE -> stepGenerate();
            case RESEND -> stepResend();
            case DONE -> {
            }
        }
    }

    private void stepUnload() {
        int remaining = this.shrinkView
                ? (int) this.chunks.stream().filter(pos -> !isChunkGone(pos)).count()
                : 0;
        boolean allUnloaded = remaining == 0;

        this.settleTicks = allUnloaded ? this.settleTicks + 1 : 0;

        if (remaining < this.lastRemaining) {
            this.lastRemaining = remaining;
            this.stallTicks = 0;
        } else {
            this.stallTicks++;
        }

        boolean settled = allUnloaded && this.settleTicks >= SETTLE_TICKS;
        boolean stalled = this.stallTicks >= UNLOAD_STALL_LIMIT;
        if (settled || stalled || this.phaseTicks >= MAX_UNLOAD_TICKS) {
            if (!allUnloaded) {
                Expandia.LOGGER.warn("Expandia: {} disc chunk(s) would not unload (a player is likely "
                        + "standing on the island spot); they may keep their old terrain", remaining);
            }
            wipeOnDisk(this.level, this.chunks);
            forceLoad(true);
            this.phase = Phase.GENERATE;
            this.phaseTicks = 0;
        }
    }

    private boolean isChunkGone(ChunkPos pos) {
        return ExpandiaServerChunkCacheInternals.isUnloaded(this.level, pos.pack())
                && !this.level.getChunkSource().hasChunk(pos.x(), pos.z());
    }

    private void stepGenerate() {
        boolean allGenerated = this.chunks.stream()
                .allMatch(pos -> ExpandiaChunkRegenerator.isFullyGenerated(this.level, pos));
        if (allGenerated || this.phaseTicks >= MAX_GENERATE_TICKS) {
            this.phase = Phase.RESEND;
            this.phaseTicks = 0;
        }
    }

    private void stepResend() {
        for (ServerPlayer player : this.level.players()) {
            int playerChunkX = player.chunkPosition().x();
            int playerChunkZ = player.chunkPosition().z();
            for (ChunkPos pos : this.chunks) {
                int dist = Math.max(Math.abs(pos.x() - playerChunkX), Math.abs(pos.z() - playerChunkZ));
                if (dist <= this.restoreViewDistance) {
                    ExpandiaChunkRegenerator.pushToClients(this.level, pos, player);
                }
            }
        }
        finish();
    }

    private void finish() {
        forceLoad(false);
        if (this.shrinkView) {
            this.level.getChunkSource().setViewDistance(this.restoreViewDistance);
        }
        this.phase = Phase.DONE;
        Expandia.LOGGER.info("Expandia: island reveal finished");
    }

    private void forceLoad(boolean add) {
        for (ChunkPos pos : this.chunks) {
            this.level.setChunkForced(pos.x(), pos.z(), add);
        }
    }

    private static void wipeOnDisk(ServerLevel level, List<ChunkPos> chunks) {
        var chunkMap = level.getChunkSource().chunkMap;
        try {
            chunkMap.synchronize(true).join();
        } catch (Exception e) {
            Expandia.LOGGER.warn("Expandia: failed to flush chunk storage before wipe", e);
        }
        for (ChunkPos pos : chunks) {
            try {
                chunkMap.write(pos, IOWorker.STORE_EMPTY).join();
            } catch (Exception e) {
                Expandia.LOGGER.warn("Expandia: failed to wipe chunk {}", pos, e);
            }
        }
        try {
            chunkMap.synchronize(true).join();
        } catch (Exception e) {
            Expandia.LOGGER.warn("Expandia: failed to flush chunk storage after wipe", e);
        }
    }
}
