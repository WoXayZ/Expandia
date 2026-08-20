package com.expandia.worldgen;

import com.expandia.Expandia;
import com.expandia.ExpandiaConstants;
import com.expandia.progression.ExpandiaProgressionManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.DensityFunctions;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.NoiseRouter;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.storage.PrimaryLevelData;
import net.minecraft.world.level.storage.WorldData;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import sun.misc.Unsafe;

import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

@EventBusSubscriber(modid = ExpandiaConstants.MOD_ID)
public final class ExpandiaWorldGenInjector {

    private static final Set<ResourceKey<Level>> INJECTED = ConcurrentHashMap.newKeySet();

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
        INJECTED.clear();
        ExpandiaProgressionManager.syncRuntimeState(event.getServer());
        inject(event.getServer());
    }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        inject(event.getServer());
        pinNetherSpawn(event.getServer());
        ExpandiaProgressionManager.repairNetherSpawn(event.getServer());
    }

    public static void inject(MinecraftServer server) {
        injectLevel(server.getLevel(Level.OVERWORLD));
        injectLevel(server.getLevel(Level.NETHER));
    }

    private static void injectLevel(@Nullable ServerLevel level) {
        if (level == null) {
            return;
        }
        try {
            injectInto(level);
        } catch (Throwable t) {
            Expandia.LOGGER.error("Expandia: could not configure worldgen for {}", level.dimension().location(), t);
        }
    }

    static void injectInto(ServerLevel level) throws Exception {
        if (!INJECTED.add(level.dimension())) {
            return;
        }

        disableWorldOptionStructures(level.getServer());
        wrapBiomeSource(level);
        try {
            configureNoise(level);
        } catch (Exception e) {
            Expandia.LOGGER.error("Expandia: could not configure noise for {}", level.dimension().location(), e);
        }
        try {
            disableStructures(level);
        } catch (Exception e) {
            Expandia.LOGGER.error("Expandia: could not disable structures for {}", level.dimension().location(), e);
        }
        try {
            useEmptyBlenderGenerator(level);
        } catch (Exception e) {
            Expandia.LOGGER.error("Expandia: could not replace chunk generator for {}", level.dimension().location(), e);
        }
    }

    private static void pinNetherSpawn(MinecraftServer server) {
        ServerLevel nether = server.getLevel(Level.NETHER);
        if (nether == null) {
            return;
        }
        int y = Math.max(nether.getSeaLevel() + 8, 40);
        nether.setDefaultSpawnPos(new BlockPos(0, y, 0), 0.0F);
        Expandia.LOGGER.info("Expandia: nether spawn pinned to (0, {}, 0)", y);
    }

    private static void disableWorldOptionStructures(MinecraftServer server) throws Exception {
        WorldData worldData = server.getWorldData();
        WorldOptions options = worldData.worldGenOptions();
        if (!options.generateStructures()) {
            return;
        }
        if (worldData instanceof PrimaryLevelData primary) {
            replaceFinalField(primary, PrimaryLevelData.class, "worldOptions", options.withStructures(false));
            Expandia.LOGGER.info("Expandia: world option generateStructures forced to false");
        }
    }

    private static void configureNoise(ServerLevel level) throws Exception {
        ChunkGenerator generator = level.getChunkSource().getGenerator();
        if (!(generator instanceof NoiseBasedChunkGenerator noiseGenerator)) {
            return;
        }

        NoiseGeneratorSettings old = noiseGenerator.generatorSettings().value();
        NoiseRouter router = old.noiseRouter();
        boolean nether = Level.NETHER.equals(level.dimension());
        if (nether && router.finalDensity() instanceof ExpandiaLandMaskDensityFunction) {
            return;
        }
        boolean continentsMasked = router.continents() instanceof ExpandiaLandMaskDensityFunction;
        if (!nether && continentsMasked && !old.aquifersEnabled() && !old.oreVeinsEnabled()) {
            return;
        }

        int sea = old.seaLevel();
        DensityFunction continents = maskDensity(router.continents(), ExpandiaLandMaskDensityFunction.MaskMode.CONTINENTS, sea);
        DensityFunction erosion = maskDensity(router.erosion(), ExpandiaLandMaskDensityFunction.MaskMode.EROSION, sea);
        DensityFunction temperature = maskDensity(router.temperature(), ExpandiaLandMaskDensityFunction.MaskMode.TEMPERATURE, sea);
        DensityFunction vegetation = maskDensity(router.vegetation(), ExpandiaLandMaskDensityFunction.MaskMode.VEGETATION, sea);

        DensityFunction finalDensity;
        DensityFunction initialDensity;
        if (nether) {
            finalDensity = maskDensity(router.finalDensity(), ExpandiaLandMaskDensityFunction.MaskMode.FINAL, sea);
            initialDensity = maskDensity(
                    router.initialDensityWithoutJaggedness(), ExpandiaLandMaskDensityFunction.MaskMode.FINAL, sea);
        } else {
            initialDensity = router.initialDensityWithoutJaggedness();
            finalDensity = DensityFunctions.max(router.finalDensity(), initialDensity);
        }

        NoiseRouter rewritten = new NoiseRouter(
                router.barrierNoise(),
                router.fluidLevelFloodednessNoise(),
                router.fluidLevelSpreadNoise(),
                router.lavaNoise(),
                temperature,
                vegetation,
                continents,
                erosion,
                router.depth(),
                router.ridges(),
                initialDensity,
                finalDensity,
                DensityFunctions.zero(),
                DensityFunctions.zero(),
                DensityFunctions.zero()
        );

        NoiseGeneratorSettings neo = new NoiseGeneratorSettings(
                old.noiseSettings(),
                old.defaultBlock(),
                old.defaultFluid(),
                rewritten,
                old.surfaceRule(),
                old.spawnTarget(),
                old.seaLevel(),
                old.disableMobGeneration(),
                false,
                false,
                old.useLegacyRandomSource()
        );

        replaceFinalField(noiseGenerator, NoiseBasedChunkGenerator.class, "settings", Holder.direct(neo));

        RandomState randomState = RandomState.create(
                neo,
                level.registryAccess().lookupOrThrow(Registries.NOISE),
                level.getSeed()
        );
        replaceTypedField(level.getChunkSource().chunkMap, ChunkMap.class, RandomState.class, randomState);

        Expandia.LOGGER.info("Expandia: configured {} noise (aquifers/ore veins off{})",
                level.dimension().location(),
                nether ? ", lava-ocean land mask" : ", caves filled");
    }

    private static void disableStructures(ServerLevel level) throws Exception {
        ChunkGenerator generator = level.getChunkSource().getGenerator();
        RandomState randomState = level.getChunkSource().randomState();
        ChunkGeneratorStructureState structureState = ChunkGeneratorStructureState.createForFlat(
                randomState,
                level.getSeed(),
                generator.getBiomeSource(),
                Stream.empty()
        );

        replaceTypedField(level.getChunkSource().chunkMap, ChunkMap.class, ChunkGeneratorStructureState.class, structureState);

        Expandia.LOGGER.info("Expandia: disabled all structures in {}", level.dimension().location());
    }

    private static void useEmptyBlenderGenerator(ServerLevel level) throws Exception {
        ChunkGenerator current = level.getChunkSource().getGenerator();
        if (!(current instanceof NoiseBasedChunkGenerator noise) || current instanceof ExpandiaNoiseChunkGenerator) {
            return;
        }
        ChunkGenerator replacement = new ExpandiaNoiseChunkGenerator(noise.getBiomeSource(), noise.generatorSettings());
        // ServerChunkCache has no ChunkGenerator field in 1.21.1 (it delegates to chunkMap.generator()),
        // so only the ChunkMap holds the instance. Replacing it there is enough and getGenerator() follows.
        replaceTypedField(level.getChunkSource().chunkMap, ChunkMap.class, ChunkGenerator.class, replacement);
        Expandia.LOGGER.info("Expandia: {} generator uses empty blender (no spawn-square chopping)",
                level.dimension().location());
    }

    private static DensityFunction maskDensity(
            DensityFunction function, ExpandiaLandMaskDensityFunction.MaskMode mode, int seaLevel) {
        if (function instanceof ExpandiaLandMaskDensityFunction existing && existing.mode() == mode) {
            return function;
        }
        return new ExpandiaLandMaskDensityFunction(function, mode, seaLevel);
    }

    private static void wrapBiomeSource(ServerLevel level) throws Exception {
        ChunkGenerator generator = level.getChunkSource().getGenerator();
        BiomeSource current = generator.getBiomeSource();
        if (current instanceof ExpandiaOceanBiomeSource existing) {
            existing.bind(level.registryAccess().lookupOrThrow(Registries.BIOME), level.getSeed());
            return;
        }

        boolean nether = Level.NETHER.equals(level.dimension());
        Holder<Biome> empty = level.registryAccess().lookupOrThrow(Registries.BIOME)
                .getOrThrow(nether ? Biomes.NETHER_WASTES : Biomes.OCEAN);
        ExpandiaOceanBiomeSource wrapped = new ExpandiaOceanBiomeSource(current, empty, nether);
        wrapped.bind(level.registryAccess().lookupOrThrow(Registries.BIOME), level.getSeed());
        replaceFinalField(generator, ChunkGenerator.class, "biomeSource", wrapped);
        Expandia.LOGGER.info("Expandia: wrapped biome source for {} (empty={})",
                level.dimension().location(),
                nether ? "minecraft:nether_wastes" : "minecraft:ocean");
    }

    @SuppressWarnings("sunapi")
    private static void replaceFinalField(Object instance, Class<?> owner, String fieldName, Object value) throws Exception {
        replaceField(instance, owner.getDeclaredField(fieldName), value);
    }

    @SuppressWarnings("sunapi")
    private static void replaceTypedField(Object instance, Class<?> owner, Class<?> type, Object value) throws Exception {
        Field match = null;
        Class<?> search = owner;
        while (search != null && match == null) {
            for (Field field : search.getDeclaredFields()) {
                if (type.equals(field.getType())) {
                    match = field;
                    break;
                }
            }
            search = search.getSuperclass();
        }
        if (match == null) {
            throw new NoSuchFieldException(owner.getName() + " has no field of type " + type.getName());
        }
        replaceField(instance, match, value);
    }

    @SuppressWarnings("sunapi")
    private static void replaceField(Object instance, Field field, Object value) {
        field.setAccessible(true);
        long offset = UNSAFE.objectFieldOffset(field);
        UNSAFE.putObject(instance, offset, value);
    }
}
