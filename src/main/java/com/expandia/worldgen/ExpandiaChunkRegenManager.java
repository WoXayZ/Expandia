package com.expandia.worldgen;

import com.expandia.Expandia;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ProtoChunk;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public final class ExpandiaChunkRegenManager {

    private static final int MAX_IN_FLIGHT = 4;
    private static final int LIGHT_SETTLE_TICKS = 8;
    /** Safety only. In-progress chunks are ticketed and rewritten; they are not skipped after a short wait. */
    private static final int MAX_DEFER_TICKS = 1200;
    /** Distance passed to {@code addRegionTicket}. 0 keeps the chunk at FULL; the API subtracts this from 33. */
    private static final int TICKET_DISTANCE = 0;

    private enum Phase { SCAN, TERRAIN, DECORATE, LIGHT, RESEND, DONE }

    private static final Deque<ExpandiaChunkRegenManager> QUEUE = new ArrayDeque<>();
    private static ExpandiaChunkRegenManager active;

    private final ServerLevel level;
    private final int targetSlot;
    private final List<ChunkPos> disc;
    private final List<ChunkPos> held = new ArrayList<>();
    private final List<ChunkPos> rebuiltChunks = new ArrayList<>();
    private final Deque<ChunkPos> terrainQueue = new ArrayDeque<>();
    private final List<InFlight> inFlight = new ArrayList<>();
    private Phase phase;
    private int cursor;
    private int settleTicks;
    private int wiped;
    private int skipped;
    private int pendingReads;
    private int deferredTicks;
    private boolean ticketsHeld;

    private ExpandiaChunkRegenManager(ServerLevel level, int targetSlot, List<ChunkPos> disc) {
        this.level = level;
        this.targetSlot = targetSlot;
        this.disc = disc;
        this.phase = Phase.SCAN;
    }

    public static void revealIsland(
            ServerLevel level, double centerX, double centerZ, int radius, int transitionWidth, int targetSlot) {
        List<ChunkPos> disc = ExpandiaChunkRegenerator.collectIslandDisc(centerX, centerZ, radius, transitionWidth);
        QUEUE.add(new ExpandiaChunkRegenManager(level, targetSlot, List.copyOf(disc)));
    }

    public static boolean isRunning() {
        return active != null || !QUEUE.isEmpty();
    }

    /**
     * The queue is static and outlives an integrated server. A reveal left running
     * when the world closes would otherwise sit in {@code active} forever, and every
     * later world would announce islands that never generate.
     */
    public static void discard() {
        active = null;
        QUEUE.clear();
    }

    public static void tick(ServerLevel level) {
        MinecraftServer server = level.getServer();
        if (active != null && active.level.getServer() != server) {
            active = null;
        }
        QUEUE.removeIf(job -> job.level.getServer() != server);
        if (active == null) {
            active = QUEUE.poll();
            if (active == null) {
                return;
            }
            try {
                active.start();
            } catch (RuntimeException e) {
                Expandia.LOGGER.error("Expandia: island reveal failed to start for slot {}", active.targetSlot, e);
                active = null;
                return;
            }
        }
        if (active.level != level) {
            return;
        }
        if (active.phase == Phase.SCAN) {
            active.stepScan();
        }
        if (active.phase == Phase.DONE) {
            active = null;
            return;
        }
        active.step();
        if (active.phase == Phase.DONE) {
            active = null;
        }
    }

    private void start() {
        this.cursor = 0;
        this.settleTicks = 0;
        this.wiped = 0;
        this.deferredTicks = 0;
        List<ChunkPos> unread = new ArrayList<>();
        for (ChunkPos pos : this.disc) {
            boolean loaded = this.level.getChunkSource().getChunkNow(pos.x, pos.z) != null;
            boolean generating = !ExpandiaServerChunkCacheInternals.isUnloaded(this.level, pos.toLong());
            if (loaded || generating) {
                hold(pos);
                this.terrainQueue.add(pos);
            } else {
                unread.add(pos);
            }
        }
        this.pendingReads = unread.size();
        for (ChunkPos pos : unread) {
            this.level.getChunkSource().chunkMap.read(pos).whenComplete((tag, error) ->
                    this.level.getServer().execute(() -> this.onSavedChunk(pos, error == null && tag != null && tag.isPresent())));
        }
        this.phase = Phase.SCAN;
        Expandia.LOGGER.info(
                "Expandia: revealing island slot {} ({} chunks, {} already loaded)",
                this.targetSlot, this.disc.size(), this.terrainQueue.size());
    }

    private void onSavedChunk(ChunkPos pos, boolean saved) {
        if (this.phase == Phase.DONE) {
            return;
        }
        if (saved) {
            hold(pos);
            this.terrainQueue.add(pos);
        } else {
            this.skipped++;
        }
        this.pendingReads--;
    }

    private void stepScan() {
        if (this.pendingReads > 0) {
            return;
        }
        if (this.terrainQueue.isEmpty()) {
            finish("0 chunk(s) rebuilt, " + this.skipped + " left to generate on visit");
            return;
        }
        Expandia.LOGGER.info(
                "Expandia: rewriting {} saved chunk(s) for slot {} ({} not generated yet)",
                this.terrainQueue.size(), this.targetSlot, this.skipped);
        ExpandiaServerChunkCacheInternals.runDistanceManagerUpdates(this.level);
        this.phase = Phase.TERRAIN;
    }

    private void step() {
        switch (this.phase) {
            case SCAN -> {
            }
            case TERRAIN -> stepTerrain();
            case DECORATE -> stepDecorate();
            case LIGHT -> stepLight();
            case RESEND -> stepResend();
            case DONE -> {
            }
        }
    }

    private void stepTerrain() {
        Iterator<InFlight> iterator = this.inFlight.iterator();
        while (iterator.hasNext()) {
            InFlight task = iterator.next();
            if (!task.future.isDone()) {
                continue;
            }
            iterator.remove();
            if (this.level.getChunkSource().getChunkNow(task.pos.x, task.pos.z) == null) {
                // Still generating or just unloaded. Wiping now would let the in-flight
                // save write the old ocean back, which cuts a ring out of the island.
                this.terrainQueue.add(task.pos);
                continue;
            }
            ProtoChunk proto = task.future.getNow(null);
            if (ExpandiaChunkRegenerator.applyGeneratedChunk(this.level, task.pos, proto, this.targetSlot)) {
                this.rebuiltChunks.add(task.pos);
                ExpandiaChunkRegenerator.relight(this.level, task.pos);
                pushNearby(task.pos);
            }
        }

        int budget = this.terrainQueue.size();
        int waiting = 0;
        while (this.inFlight.size() < MAX_IN_FLIGHT && budget-- > 0 && !this.terrainQueue.isEmpty()) {
            ChunkPos pos = this.terrainQueue.poll();
            if (this.level.getChunkSource().getChunkNow(pos.x, pos.z) != null) {
                this.deferredTicks = 0;
                this.inFlight.add(new InFlight(pos, ExpandiaChunkRegenerator.generateVanillaAsync(this.level, pos)));
                continue;
            }
            // Ticketed but not FULL yet. Wiping here used to save the old ocean
            // back over the island, which left a solid-but-invisible cut.
            this.terrainQueue.add(pos);
            waiting++;
        }

        if (waiting > 0) {
            this.deferredTicks++;
            ExpandiaServerChunkCacheInternals.runDistanceManagerUpdates(this.level);
            if (this.deferredTicks == MAX_DEFER_TICKS) {
                Expandia.LOGGER.warn(
                        "Expandia: {} chunk(s) are still loading for island slot {} after {} ticks",
                        this.terrainQueue.size(), this.targetSlot, this.deferredTicks);
            } else if (this.deferredTicks > MAX_DEFER_TICKS * 2 && this.inFlight.isEmpty()) {
                Expandia.LOGGER.error(
                        "Expandia: stopped waiting on {} chunk(s) that never finished loading for island slot {}",
                        this.terrainQueue.size(), this.targetSlot);
                this.terrainQueue.clear();
            }
        } else if (!this.inFlight.isEmpty()) {
            this.deferredTicks = 0;
        }

        if (this.terrainQueue.isEmpty() && this.inFlight.isEmpty()) {
            this.phase = Phase.DECORATE;
            this.cursor = 0;
        }
    }

    private void stepDecorate() {
        if (this.cursor < this.rebuiltChunks.size()) {
            ChunkPos pos = this.rebuiltChunks.get(this.cursor);
            ExpandiaChunkRegenerator.decorateLoaded(this.level, pos);
            ExpandiaChunkRegenerator.relight(this.level, pos);
            pushNearby(pos);
            this.cursor++;
            return;
        }
        this.phase = Phase.LIGHT;
        this.settleTicks = 0;
    }

    private void stepLight() {
        this.settleTicks++;
        if (this.settleTicks >= LIGHT_SETTLE_TICKS) {
            this.phase = Phase.RESEND;
        }
    }

    private void pushNearby(ChunkPos pos) {
        for (ServerPlayer player : this.level.players()) {
            int view = Math.max(2, player.requestedViewDistance()) + 2;
            int dist = Math.max(Math.abs(pos.x - player.chunkPosition().x), Math.abs(pos.z - player.chunkPosition().z));
            if (dist <= view) {
                ExpandiaChunkRegenerator.pushToClients(this.level, pos, player);
            }
        }
    }

    private void stepResend() {
        for (ChunkPos pos : this.rebuiltChunks) {
            pushNearby(pos);
        }
        ExpandiaChunkRegenerator.snapPlayersToSurface(this.level, this.rebuiltChunks);
        finish(this.rebuiltChunks.size() + " chunk(s) rebuilt, " + this.skipped + " left to generate on visit");
    }

    private void finish(String detail) {
        releaseTickets();
        this.phase = Phase.DONE;
        Expandia.LOGGER.info("Expandia: island reveal finished ({})", detail);
    }

    private void hold(ChunkPos pos) {
        this.level.getChunkSource().addRegionTicket(TicketType.FORCED, pos, TICKET_DISTANCE, pos);
        this.held.add(pos);
        this.ticketsHeld = true;
    }

    private void releaseTickets() {
        if (!this.ticketsHeld) {
            return;
        }
        for (ChunkPos pos : this.held) {
            this.level.getChunkSource().removeRegionTicket(TicketType.FORCED, pos, TICKET_DISTANCE, pos);
        }
        this.held.clear();
        this.ticketsHeld = false;
        ExpandiaServerChunkCacheInternals.runDistanceManagerUpdates(this.level);
    }

    private record InFlight(ChunkPos pos, CompletableFuture<ProtoChunk> future) {
    }
}
