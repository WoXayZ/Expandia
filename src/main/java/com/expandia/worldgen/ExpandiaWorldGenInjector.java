package com.expandia.worldgen;

import com.expandia.Expandia;
import com.expandia.ExpandiaConstants;
import com.expandia.progression.ExpandiaProgressionManager;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
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

/**
 * Disables overworld structure generation. Island climate is handled by datapack overrides
 * ({@code data/minecraft/worldgen/density_function/...}) so every spline reference to
 * {@code overworld/continents} sees the masked version.
 */
@EventBusSubscriber(modid = ExpandiaConstants.MOD_ID)
public final class ExpandiaWorldGenInjector {

    private static boolean structuresDisabled;
    private static boolean oceanBiomeForced;

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
        structuresDisabled = false;
        oceanBiomeForced = false;
        ExpandiaProgressionManager.syncRuntimeState(event.getServer());
        inject(event.getServer());
    }

    public static void inject(MinecraftServer server) {
        ServerLevel overworld = server.getLevel(Level.OVERWORLD);
        if (overworld == null) {
            return;
        }
        try {
            injectInto(overworld);
        } catch (Throwable t) {
            Expandia.LOGGER.error("Expandia: could not configure overworld worldgen", t);
        }
    }

    static void injectInto(ServerLevel level) throws Exception {
        forceOceanBiome(level);
        disableStructures(level);
    }

    private static void disableStructures(ServerLevel level) throws Exception {
        if (structuresDisabled) {
            return;
        }
        structuresDisabled = true;

        ChunkGenerator generator = level.getChunkSource().getGenerator();
        if (!(generator instanceof NoiseBasedChunkGenerator noiseGenerator)) {
            Expandia.LOGGER.warn("Expandia: overworld does not use a noise-based chunk generator, skipping structure disable");
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
                "Expandia: island datapack active on overworld (radius={} blocks)",
                ExpandiaRadiusState.getIslandRadius()
        );
    }

    private static void forceOceanBiome(ServerLevel level) throws Exception {
        if (oceanBiomeForced) {
            return;
        }
        oceanBiomeForced = true;

        ChunkGenerator generator = level.getChunkSource().getGenerator();
        BiomeSource current = generator.getBiomeSource();
        if (current instanceof ExpandiaOceanBiomeSource) {
            return;
        }

        Holder<Biome> ocean = level.registryAccess().lookupOrThrow(Registries.BIOME).getOrThrow(Biomes.OCEAN);
        ExpandiaOceanBiomeSource wrapped = new ExpandiaOceanBiomeSource(current, ocean);
        replaceFinalField(generator, ChunkGenerator.class, "biomeSource", wrapped);
        Expandia.LOGGER.info("Expandia: forced minecraft:ocean biome around islands on overworld");
    }

    @SuppressWarnings("sunapi")
    private static void replaceFinalField(Object instance, Class<?> owner, String fieldName, Object value) throws Exception {
        Field field = owner.getDeclaredField(fieldName);
        field.setAccessible(true);
        long offset = UNSAFE.objectFieldOffset(field);
        UNSAFE.putObject(instance, offset, value);
    }
}
