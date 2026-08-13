package com.expandia.worldgen;

import com.expandia.Expandia;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ProtoChunk;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

public final class ExpandiaChunkRegenManager {

    private static final int MAX_IN_FLIGHT = 2;
    private static final int LIGHT_SETTLE_TICKS = 8;

    private enum Phase { TERRAIN, DECORATE, LIGHT, RESEND, DONE }

    private static final Deque<ExpandiaChunkRegenManager> QUEUE = new ArrayDeque<>();
    private static ExpandiaChunkRegenManager active;

    private final ServerLevel level;
    private final int targetSlot;
    private final List<ChunkPos> loaded;
    private final List<ChunkPos> onDisk;
    private final Deque<ChunkPos> terrainQueue = new ArrayDeque<>();
    private final List<InFlight> inFlight = new ArrayList<>();
    private Phase phase;
    private int cursor;
    private int settleTicks;
    private int rebuilt;

    private ExpandiaChunkRegenManager(ServerLevel level, int targetSlot, List<ChunkPos> loaded, List<ChunkPos> onDisk) {
        this.level = level;
        this.targetSlot = targetSlot;
        this.loaded = loaded;
        this.onDisk = onDisk;
        this.phase = Phase.TERRAIN;
    }

    public static void revealIsland(
            ServerLevel level, double centerX, double centerZ, int radius, int transitionWidth, int targetSlot) {
        List<ChunkPos> disc = ExpandiaChunkRegenerator.collectIslandDisc(centerX, centerZ, radius, transitionWidth);
        List<ChunkPos> loaded = ExpandiaChunkRegenerator.currentlyLoaded(level, disc);
        loaded.sort(Comparator.comparingDouble(pos -> distanceSq(pos, centerX, centerZ)));

        Set<ChunkPos> loadedSet = new HashSet<>(loaded);
        List<ChunkPos> onDisk = new ArrayList<>();
        for (ChunkPos pos : disc) {
            if (!loadedSet.contains(pos)) {
                onDisk.add(pos);
            }
        }

        QUEUE.add(new ExpandiaChunkRegenManager(level, targetSlot, List.copyOf(loaded), List.copyOf(onDisk)));
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
        this.cursor = 0;
        this.settleTicks = 0;
        this.rebuilt = 0;
        this.terrainQueue.addAll(this.loaded);
        ExpandiaChunkRegenerator.wipeOnDisk(this.level, this.onDisk);
        Expandia.LOGGER.info("Expandia: revealing island slot {} ({} loaded, {} wiped on disk)",
                this.targetSlot, this.loaded.size(), this.onDisk.size());
        if (this.loaded.isEmpty()) {
            this.phase = Phase.DONE;
            Expandia.LOGGER.info("Expandia: island reveal finished (nothing loaded; fresh gen on visit)");
        } else {
            this.phase = Phase.TERRAIN;
        }
    }

    private void step() {
        switch (this.phase) {
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
            ProtoChunk proto = task.future.getNow(null);
            if (ExpandiaChunkRegenerator.applyGeneratedChunk(this.level, task.pos, proto, this.targetSlot)) {
                this.rebuilt++;
                ExpandiaChunkRegenerator.relight(this.level, task.pos);
                pushNearby(task.pos);
            }
        }

        while (this.inFlight.size() < MAX_IN_FLIGHT && !this.terrainQueue.isEmpty()) {
            ChunkPos pos = this.terrainQueue.poll();
            this.inFlight.add(new InFlight(pos, ExpandiaChunkRegenerator.generateVanillaAsync(this.level, pos)));
        }

        if (this.terrainQueue.isEmpty() && this.inFlight.isEmpty()) {
            this.phase = Phase.DECORATE;
            this.cursor = 0;
        }
    }

    private void stepDecorate() {
        if (this.cursor < this.loaded.size()) {
            ChunkPos pos = this.loaded.get(this.cursor);
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
        int view = this.level.getServer().getPlayerList().getViewDistance();
        for (ServerPlayer player : this.level.players()) {
            int dist = Math.max(Math.abs(pos.x - player.chunkPosition().x), Math.abs(pos.z - player.chunkPosition().z));
            if (dist <= view) {
                ExpandiaChunkRegenerator.pushToClients(this.level, pos, player);
            }
        }
    }

    private void stepResend() {
        for (ChunkPos pos : this.loaded) {
            pushNearby(pos);
        }
        ExpandiaChunkRegenerator.snapPlayersToSurface(this.level, this.loaded);
        this.phase = Phase.DONE;
        Expandia.LOGGER.info("Expandia: island reveal finished ({} chunk(s) rebuilt)", this.rebuilt);
    }

    private static double distanceSq(ChunkPos pos, double centerX, double centerZ) {
        double dx = pos.getMiddleBlockX() - centerX;
        double dz = pos.getMiddleBlockZ() - centerZ;
        return dx * dx + dz * dz;
    }

    private record InFlight(ChunkPos pos, CompletableFuture<ProtoChunk> future) {
    }
}
