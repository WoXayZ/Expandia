package com.expandia.worldgen;

import com.expandia.Expandia;
import com.expandia.ExpandiaConstants;
import com.expandia.config.ExpandiaConfigManager;
import com.expandia.progression.ExpandiaProgressionManager;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import sun.misc.Unsafe;

import java.lang.reflect.Field;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Disables overworld structure generation when configured. Island climate and cave removal are
 * handled by datapack overrides ({@code data/minecraft/worldgen/density_function/...}) so every
 * spline reference to {@code overworld/continents} sees the masked version.
 */
@EventBusSubscriber(modid = ExpandiaConstants.MOD_ID)
public final class ExpandiaWorldGenInjector {

    private static final Set<Identifier> STRUCTURES_DISABLED = ConcurrentHashMap.newKeySet();
    private static final Set<Identifier> OCEAN_BIOME_FORCED = ConcurrentHashMap.newKeySet();

    @SuppressWarnings("sunapi")
    private static final Unsafe UNSAFE;

    static {
        try {
            Field theUnsafe = Unsafe.class.getDeclaredField("theUnsafe");
            theUnsafe.setAccessible(true);
            UNSAFE = (Unsafe) theUnsafe.get(null);
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private ExpandiaWorldGenInjector() {
    }

    @SubscribeEvent
    public static void onServerStarting(ServerStartingEvent event) {
        STRUCTURES_DISABLED.clear();
        OCEAN_BIOME_FORCED.clear();
        ExpandiaProgressionManager.syncRuntimeState(event.getServer());
        inject(event.getServer());
    }

    public static void inject(MinecraftServer server) {
        for (ServerLevel level : server.getAllLevels()) {
            Identifier dimensionId = level.dimension().identifier();
            if (!ExpandiaConfigManager.get().isDimensionEnabled(dimensionId)) {
                continue;
            }
            try {
                injectInto(level, dimensionId);
            } catch (Throwable t) {
                Expandia.LOGGER.error("Expandia: could not configure {} worldgen", dimensionId, t);
            }
        }
    }

    static void injectInto(ServerLevel level, Identifier dimensionId) throws Exception {
        forceOceanBiome(level, dimensionId);

        if (ExpandiaConfigManager.get().generateStructures()) {
            Expandia.LOGGER.info(
                    "Expandia: island datapack active on {} (radius={} blocks, structures=true)",
                    dimensionId,
                    ExpandiaRadiusState.getIslandRadius()
            );
            return;
        }

        if (!STRUCTURES_DISABLED.add(dimensionId)) {
            return;
        }

        ChunkGenerator generator = level.getChunkSource().getGenerator();
        if (!(generator instanceof NoiseBasedChunkGenerator noiseGenerator)) {
            Expandia.LOGGER.warn("Expandia: {} does not use a noise-based chunk generator, skipping", dimensionId);
            return;
        }

        RandomState randomState = level.getChunkSource().randomState();
        ChunkGeneratorStructureState structureState = noiseGenerator.createState(
                ExpandiaEmptyStructureSetLookup.INSTANCE,
                randomState,
                level.getSeed()
        );

        ChunkMap chunkMap = level.getChunkSource().chunkMap;
        replaceFinalField(chunkMap, ChunkMap.class, "chunkGeneratorState", structureState);

        Expandia.LOGGER.info(
                "Expandia: island datapack active on {} (radius={} blocks, structures=false)",
                dimensionId,
                ExpandiaRadiusState.getIslandRadius()
        );
    }

    /** Wraps the dimension biome source so open water is always plain {@code minecraft:ocean}. */
    private static void forceOceanBiome(ServerLevel level, Identifier dimensionId) throws Exception {
        if (!OCEAN_BIOME_FORCED.add(dimensionId)) {
            return;
        }

        ChunkGenerator generator = level.getChunkSource().getGenerator();
        BiomeSource current = generator.getBiomeSource();
        if (current instanceof ExpandiaOceanBiomeSource) {
            return;
        }

        Holder<Biome> ocean = level.registryAccess().lookupOrThrow(Registries.BIOME).getOrThrow(Biomes.OCEAN);
        ExpandiaOceanBiomeSource wrapped = new ExpandiaOceanBiomeSource(current, ocean);
        replaceFinalField(generator, ChunkGenerator.class, "biomeSource", wrapped);
        Expandia.LOGGER.info("Expandia: forced minecraft:ocean biome around islands on {}", dimensionId);
    }

    @SuppressWarnings("sunapi")
    private static void replaceFinalField(Object instance, Class<?> owner, String fieldName, Object value) throws Exception {
        Field field = owner.getDeclaredField(fieldName);
        field.setAccessible(true);
        long offset = UNSAFE.objectFieldOffset(field);
        UNSAFE.putObject(instance, offset, value);
    }
}
