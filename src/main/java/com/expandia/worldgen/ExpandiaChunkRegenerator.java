package com.expandia.worldgen;

import com.expandia.Expandia;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ThreadedLevelLightEngine;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.UpgradeData;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.WorldGenerationContext;
import net.minecraft.world.level.levelgen.blending.Blender;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public final class ExpandiaChunkRegenerator {

    private ExpandiaChunkRegenerator() {
    }

    public static double regenReach(int radius, int transitionWidth) {
        return radius + transitionWidth + 16.0D;
    }

    public static List<ChunkPos> collectIslandDisc(double centerX, double centerZ, int radius, int transitionWidth) {
        double reach = regenReach(radius, transitionWidth);
        int minChunkX = ((int) Math.floor(centerX - reach)) >> 4;
        int maxChunkX = ((int) Math.floor(centerX + reach)) >> 4;
        int minChunkZ = ((int) Math.floor(centerZ - reach)) >> 4;
        int maxChunkZ = ((int) Math.floor(centerZ + reach)) >> 4;

        List<ChunkPos> result = new ArrayList<>();
        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                ChunkPos pos = new ChunkPos(chunkX, chunkZ);
                if (chunkIntersectsDisc(pos, centerX, centerZ, reach)) {
                    result.add(pos);
                }
            }
        }
        return result;
    }

    public static List<ChunkPos> currentlyLoaded(ServerLevel level, List<ChunkPos> chunks) {
        List<ChunkPos> loaded = new ArrayList<>();
        for (ChunkPos pos : chunks) {
            if (level.getChunkSource().getChunkNow(pos.x, pos.z) != null) {
                loaded.add(pos);
            }
        }
        return loaded;
    }

    static boolean chunkIntersectsDisc(ChunkPos pos, double centerX, double centerZ, double reach) {
        double nearestX = clamp(centerX, pos.getMinBlockX(), pos.getMaxBlockX());
        double nearestZ = clamp(centerZ, pos.getMinBlockZ(), pos.getMaxBlockZ());
        double dx = nearestX - centerX;
        double dz = nearestZ - centerZ;
        return dx * dx + dz * dz <= reach * reach;
    }

    static CompletableFuture<ProtoChunk> generateVanillaAsync(ServerLevel level, ChunkPos pos) {
        if (!(level.getChunkSource().getGenerator() instanceof NoiseBasedChunkGenerator generator)) {
            return CompletableFuture.completedFuture(null);
        }

        Registry<Biome> biomes = level.registryAccess().registryOrThrow(Registries.BIOME);
        ProtoChunk proto = new ProtoChunk(pos, UpgradeData.EMPTY, level, biomes, null);
        Blender blender = Blender.empty();

        return generator.createBiomes(level.getChunkSource().randomState(), blender, level.structureManager(), proto)
                .thenCompose(chunk -> {
                    ((ProtoChunk) chunk).setPersistedStatus(ChunkStatus.BIOMES);
                    return generator.fillFromNoise(
                            blender, level.getChunkSource().randomState(), level.structureManager(), chunk);
                })
                .thenApply(chunk -> {
                    ProtoChunk generated = (ProtoChunk) chunk;
                    generated.setPersistedStatus(ChunkStatus.NOISE);
                    try {
                        generator.buildSurface(
                                generated,
                                new WorldGenerationContext(generator, level),
                                level.getChunkSource().randomState(),
                                level.structureManager(),
                                level.getBiomeManager().withDifferentSource(generated),
                                biomes,
                                blender
                        );
                    } catch (Exception e) {
                        Expandia.LOGGER.debug("Expandia: proto surface failed for {}", pos, e);
                    }
                    return generated;
                })
                .exceptionally(error -> {
                    Expandia.LOGGER.warn("Expandia: failed to rebuild terrain for {}", pos, error);
                    return null;
                });
    }

    static boolean applyGeneratedChunk(ServerLevel level, ChunkPos pos, ProtoChunk proto, int targetSlot) {
        if (proto == null) {
            return stampLoadedChunk(level, pos, targetSlot);
        }
        LevelChunk live = level.getChunkSource().getChunkNow(pos.x, pos.z);
        if (live == null) {
            return false;
        }

        int minY = live.getMinBuildHeight();
        int maxY = live.getMaxBuildHeight();
        boolean nether = Level.NETHER.equals(level.dimension());
        ExpandiaRadiusState.DimState state = ExpandiaRadiusState.of(level.dimension());
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        int copied = 0;
        for (int localX = 0; localX < 16; localX++) {
            for (int localZ = 0; localZ < 16; localZ++) {
                int x = pos.getMinBlockX() + localX;
                int z = pos.getMinBlockZ() + localZ;
                if (!shouldWriteColumn(level, state, x, z, targetSlot, nether)) {
                    continue;
                }
                copied++;
                for (int y = minY; y < maxY; y++) {
                    BlockState stateBlock = proto.getBlockState(cursor.set(x, y, z));
                    live.getSection(live.getSectionIndex(y))
                            .setBlockState(localX, SectionPos.sectionRelative(y), localZ, stateBlock, true);
                }
            }
        }
        if (copied == 0) {
            return false;
        }
        finishLiveChunk(level, live, pos);
        return true;
    }

    static boolean stampLoadedChunk(ServerLevel level, ChunkPos pos, int targetSlot) {
        LevelChunk live = level.getChunkSource().getChunkNow(pos.x, pos.z);
        if (live == null) {
            return false;
        }

        boolean nether = Level.NETHER.equals(level.dimension());
        ExpandiaRadiusState.DimState state = ExpandiaRadiusState.of(level.dimension());
        int sea = level.getSeaLevel();
        int minY = live.getMinBuildHeight();
        int maxY = live.getMaxBuildHeight();
        BlockState bedrock = Blocks.BEDROCK.defaultBlockState();
        BlockState deepslate = Blocks.DEEPSLATE.defaultBlockState();
        BlockState stone = Blocks.STONE.defaultBlockState();
        BlockState dirt = Blocks.DIRT.defaultBlockState();
        BlockState grass = Blocks.GRASS_BLOCK.defaultBlockState();
        BlockState sand = Blocks.SAND.defaultBlockState();
        BlockState water = Blocks.WATER.defaultBlockState();
        BlockState netherrack = Blocks.NETHERRACK.defaultBlockState();
        BlockState lava = Blocks.LAVA.defaultBlockState();
        BlockState air = Blocks.AIR.defaultBlockState();

        int landColumns = 0;
        for (int localX = 0; localX < 16; localX++) {
            for (int localZ = 0; localZ < 16; localZ++) {
                int x = pos.getMinBlockX() + localX;
                int z = pos.getMinBlockZ() + localZ;
                if (!shouldWriteColumn(level, state, x, z, targetSlot, nether)) {
                    continue;
                }
                int top = state.islandSurfaceY(x, z, sea);
                boolean oceanColumn = top == Integer.MIN_VALUE;
                if (!oceanColumn) {
                    landColumns++;
                }
                double blend = state.oceanBlend(x, z);
                BlockState surface;
                if (nether) {
                    surface = netherrack;
                } else {
                    surface = blend < 0.55D ? grass : sand;
                }
                for (int y = minY; y < maxY; y++) {
                    BlockState stateBlock;
                    if (nether) {
                        if (y <= minY + 1) {
                            stateBlock = bedrock;
                        } else if (oceanColumn) {
                            stateBlock = y < sea - 10 ? netherrack : (y <= sea ? lava : air);
                        } else if (y < top) {
                            stateBlock = netherrack;
                        } else if (y == top) {
                            stateBlock = surface;
                        } else if (y <= sea) {
                            stateBlock = lava;
                        } else {
                            stateBlock = air;
                        }
                    } else if (y <= minY + 1) {
                        stateBlock = bedrock;
                    } else if (y < 0) {
                        stateBlock = y < top ? deepslate : (y <= sea ? water : air);
                    } else if (y < top - 3) {
                        stateBlock = stone;
                    } else if (y < top) {
                        stateBlock = dirt;
                    } else if (y == top) {
                        stateBlock = surface;
                    } else if (y <= sea) {
                        stateBlock = water;
                    } else {
                        stateBlock = air;
                    }
                    live.getSection(live.getSectionIndex(y))
                            .setBlockState(localX, SectionPos.sectionRelative(y), localZ, stateBlock, true);
                }
            }
        }
        if (landColumns == 0 && !nether) {
            return false;
        }
        finishLiveChunk(level, live, pos);
        return true;
    }

    static void decorateLoaded(ServerLevel level, ChunkPos pos) {
        LevelChunk live = level.getChunkSource().getChunkNow(pos.x, pos.z);
        if (live == null) {
            return;
        }
        try {
            level.getChunkSource().getGenerator().applyBiomeDecoration(level, live, level.structureManager());
            live.setUnsaved(true);
        } catch (Exception e) {
            Expandia.LOGGER.debug("Expandia: decoration failed for {}", pos, e);
        }
    }

    static void relight(ServerLevel level, ChunkPos pos) {
        LevelChunk live = level.getChunkSource().getChunkNow(pos.x, pos.z);
        if (live == null) {
            return;
        }
        if (!(level.getLightEngine() instanceof ThreadedLevelLightEngine light)) {
            return;
        }
        int minSection = live.getMinSection();
        LevelChunkSection[] sections = live.getSections();
        for (int i = 0; i < sections.length; i++) {
            light.updateSectionStatus(SectionPos.of(pos, i + minSection), sections[i].hasOnlyAir());
        }
        light.lightChunk(live, false);
    }

    public static void pushToClients(ServerLevel level, ChunkPos pos, ServerPlayer viewer) {
        LevelChunk chunk = level.getChunkSource().getChunkNow(pos.x, pos.z);
        if (chunk == null) {
            ChunkHolder holder = level.getChunkSource().chunkMap.getVisibleChunkIfPresent(pos.toLong());
            if (holder != null) {
                chunk = holder.getTickingChunk();
            }
        }
        if (chunk == null) {
            return;
        }

        for (LevelChunkSection section : chunk.getSections()) {
            section.recalcBlockCounts();
        }
        try {
            // Re-send the chunk in place. Forgetting it first unloaded the mesh and
            // looked like a wipe until the new packet arrived.
            viewer.connection.chunkSender.markChunkPendingToSend(chunk);
            viewer.connection.send(new ClientboundLevelChunkWithLightPacket(
                    chunk,
                    level.getLightEngine(),
                    null,
                    null
            ));
        } catch (Exception e) {
            Expandia.LOGGER.debug("Expandia: could not push chunk {} to {}", pos, viewer.getName().getString(), e);
        }
    }

    static void wipeOnDisk(ServerLevel level, List<ChunkPos> chunks) {
        if (chunks.isEmpty()) {
            return;
        }
        ChunkMap chunkMap = level.getChunkSource().chunkMap;
        for (ChunkPos pos : chunks) {
            try {
                chunkMap.write(pos, null);
            } catch (Exception e) {
                Expandia.LOGGER.warn("Expandia: failed to wipe chunk {}", pos, e);
            }
        }
    }

    static void snapPlayersToSurface(ServerLevel level, List<ChunkPos> chunks) {
        for (ServerPlayer player : level.players()) {
            ChunkPos playerChunk = player.chunkPosition();
            boolean inside = false;
            for (ChunkPos pos : chunks) {
                if (pos.x == playerChunk.x && pos.z == playerChunk.z) {
                    inside = true;
                    break;
                }
            }
            if (!inside) {
                continue;
            }
            int x = player.blockPosition().getX();
            int z = player.blockPosition().getZ();
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) + 1;
            player.teleportTo(x + 0.5D, y, z + 0.5D);
        }
    }

    private static void finishLiveChunk(ServerLevel level, LevelChunk live, ChunkPos pos) {
        for (LevelChunkSection section : live.getSections()) {
            section.recalcBlockCounts();
        }
        try {
            live.fillBiomesFromNoise(
                    level.getChunkSource().getGenerator().getBiomeSource(),
                    level.getChunkSource().randomState().sampler()
            );
        } catch (Exception e) {
            Expandia.LOGGER.debug("Expandia: biome fill failed for {}", pos, e);
        }
        Heightmap.primeHeightmaps(live, ChunkStatus.FULL.heightmapsAfter());
        live.clearAllBlockEntities();
        discardNonPlayers(level, pos);
        live.setUnsaved(true);
        live.setLightCorrect(false);
        live.initializeLightSources();
    }

    private static boolean belongsToIsland(ServerLevel level, int x, int z, int targetSlot) {
        ExpandiaRadiusState.DimState state = ExpandiaRadiusState.of(level.dimension());
        if (state.oceanBlend(x, z) >= 1.0D) {
            return false;
        }
        return state.nearestIslandSlot(x, z) == targetSlot;
    }

    private static boolean shouldWriteColumn(
            ServerLevel level, ExpandiaRadiusState.DimState state, int x, int z, int targetSlot, boolean nether) {
        if (belongsToIsland(level, x, z, targetSlot)) {
            return true;
        }
        return nether && !state.isWithinIslandReach(x, z);
    }

    private static void discardNonPlayers(ServerLevel level, ChunkPos pos) {
        AABB box = new AABB(
                pos.getMinBlockX(), level.getMinBuildHeight(), pos.getMinBlockZ(),
                pos.getMaxBlockX() + 1, level.getMaxBuildHeight(), pos.getMaxBlockZ() + 1
        );
        for (Entity entity : level.getEntities((Entity) null, box, entity -> !(entity instanceof Player))) {
            entity.discard();
        }
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
