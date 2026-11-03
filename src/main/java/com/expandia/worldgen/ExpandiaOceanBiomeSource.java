package com.expandia.worldgen;

import com.expandia.Expandia;
import com.google.common.collect.ImmutableSet;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.QuartPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.biome.Climate;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.stream.Stream;

public final class ExpandiaOceanBiomeSource extends BiomeSource {

    public static final MapCodec<ExpandiaOceanBiomeSource> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            BiomeSource.CODEC.fieldOf("delegate").forGetter(ExpandiaOceanBiomeSource::delegate),
            Biome.CODEC.fieldOf("ocean").forGetter(ExpandiaOceanBiomeSource::ocean),
            Codec.BOOL.optionalFieldOf("nether", false).forGetter(ExpandiaOceanBiomeSource::nether)
    ).apply(instance, ExpandiaOceanBiomeSource::new));

    private final BiomeSource delegate;
    private final Holder<Biome> ocean;
    private final boolean nether;
    private volatile HolderLookup.RegistryLookup<Biome> biomeLookup;
    private volatile long worldSeed;
    private volatile List<Holder<Biome>> islandBiomes;
    private volatile Set<Holder<Biome>> possibleBiomeCache;

    public ExpandiaOceanBiomeSource(BiomeSource delegate, Holder<Biome> ocean, boolean nether) {
        this.delegate = delegate;
        this.ocean = ocean;
        this.nether = nether;
    }

    public BiomeSource delegate() {
        return this.delegate;
    }

    public Holder<Biome> ocean() {
        return this.ocean;
    }

    public boolean nether() {
        return this.nether;
    }

    public void bind(HolderLookup.RegistryLookup<Biome> biomes, long seed) {
        this.biomeLookup = biomes;
        this.worldSeed = seed;
        this.islandBiomes = null;
        this.possibleBiomeCache = null;
        List<Holder<Biome>> pool = islandBiomes();
        int modded = 0;
        for (Holder<Biome> biome : pool) {
            if (biome.unwrapKey().map(key -> !"minecraft".equals(key.location().getNamespace())).orElse(false)) {
                modded++;
            }
        }
        Expandia.LOGGER.info(
                "Expandia: {} island biomes ready ({} from other mods)",
                pool.size(), modded);
    }

    @Override
    protected MapCodec<? extends BiomeSource> codec() {
        return CODEC;
    }

    @Override
    protected Stream<Holder<Biome>> collectPossibleBiomes() {
        return possibleBiomes().stream();
    }

    @Override
    public Set<Holder<Biome>> possibleBiomes() {
        Set<Holder<Biome>> cached = this.possibleBiomeCache;
        if (cached != null) {
            return cached;
        }
        ImmutableSet.Builder<Holder<Biome>> builder = ImmutableSet.builder();
        builder.addAll(islandBiomes());
        builder.add(this.ocean);
        builder.addAll(this.delegate.possibleBiomes());
        cached = builder.build();
        this.possibleBiomeCache = cached;
        return cached;
    }

    @Override
    public Holder<Biome> getNoiseBiome(int quartX, int quartY, int quartZ, Climate.Sampler sampler) {
        int blockX = QuartPos.toBlock(quartX) + 2;
        int blockZ = QuartPos.toBlock(quartZ) + 2;
        ExpandiaRadiusState.DimState state = dimState();
        if (!state.isWithinIslandReach(blockX, blockZ)) {
            return this.ocean;
        }
        Holder<Biome> island = biomeAt(blockX, blockZ);
        if (island != null) {
            return island;
        }
        return this.delegate.getNoiseBiome(quartX, quartY, quartZ, sampler);
    }

    /**
     * One biome for the whole island. {@code null} outside the shore, or when the pool is empty.
     */
    public Holder<Biome> biomeAt(int blockX, int blockZ) {
        ExpandiaRadiusState.DimState state = dimState();
        if (!state.isWithinIslandReach(blockX, blockZ)) {
            return null;
        }
        List<Holder<Biome>> biomes = islandBiomes();
        if (biomes.isEmpty()) {
            return null;
        }
        int slot = state.nearestIslandSlot(blockX, blockZ);
        return biomes.get(Math.floorMod(slot, biomes.size()));
    }

    private ExpandiaRadiusState.DimState dimState() {
        return this.nether ? ExpandiaRadiusState.NETHER : ExpandiaRadiusState.OVERWORLD;
    }

    private List<Holder<Biome>> islandBiomes() {
        List<Holder<Biome>> cached = this.islandBiomes;
        if (cached != null) {
            return cached;
        }
        // The delegate only knows the vanilla climate list. Mod biomes (Natura Reimagined
        // via TerraBlender, and anything else tagged is_overworld / is_nether) live in the
        // registry and would never be chosen from possibleBiomes() alone.
        Set<Holder<Biome>> unique = new LinkedHashSet<>();
        for (Holder<Biome> biome : biomeCandidates()) {
            if (isIslandBiome(biome)) {
                unique.add(biome);
            }
        }

        List<Holder<Biome>> pool = new ArrayList<>(unique);
        Holder<Biome> spawn = pickSpawnBiome(pool);
        if (spawn != null) {
            pool.removeIf(biome -> holdersMatch(biome, spawn));
        }
        Collections.shuffle(pool, new Random(this.worldSeed ^ (this.nether ? 0x9E3779B97F4A7C15L : 0xC2B2AE3D27D4EB4FL)));

        List<Holder<Biome>> ordered = new ArrayList<>(pool.size() + 1);
        if (spawn != null) {
            ordered.add(spawn);
        }
        ordered.addAll(pool);
        this.islandBiomes = ordered;
        return ordered;
    }

    private Holder<Biome> pickSpawnBiome(List<Holder<Biome>> pool) {
        ResourceKey<Biome> preferred = this.nether ? Biomes.NETHER_WASTES : Biomes.FOREST;
        for (Holder<Biome> biome : pool) {
            if (biome.is(preferred)) {
                return biome;
            }
        }
        if (!this.nether) {
            for (Holder<Biome> biome : pool) {
                if (biome.is(BiomeTags.IS_FOREST)) {
                    return biome;
                }
            }
        }
        // Only ever return a biome from the pool (all in delegate.possibleBiomes());
        // never fall back to the full registry, which would crash biome decoration.
        return pool.isEmpty() ? this.ocean : pool.get(0);
    }

    private boolean isIslandBiome(Holder<Biome> biome) {
        if (holdersMatch(biome, this.ocean)) {
            return false;
        }
        if (biome.is(Biomes.THE_VOID)) {
            return false;
        }
        if (biome.is(BiomeTags.IS_OCEAN) || biome.is(BiomeTags.IS_RIVER) || biome.is(BiomeTags.IS_END)) {
            return false;
        }
        if (isUndergroundBiome(biome)) {
            return false;
        }
        if (this.nether) {
            return biome.is(BiomeTags.IS_NETHER);
        }
        return biome.is(BiomeTags.IS_OVERWORLD);
    }

    private Iterable<Holder<Biome>> biomeCandidates() {
        LinkedHashSet<Holder<Biome>> all = new LinkedHashSet<>();
        HolderLookup.RegistryLookup<Biome> lookup = this.biomeLookup;
        if (lookup != null) {
            lookup.listElements().forEach(all::add);
        }
        all.addAll(this.delegate.possibleBiomes());
        return all;
    }

    private static boolean isUndergroundBiome(Holder<Biome> biome) {
        return biome.unwrapKey().map(key -> {
            String path = key.location().getPath();
            return path.contains("cave") || path.contains("deep_dark") || path.contains("underground")
                    || "the_void".equals(path);
        }).orElse(false);
    }

    private static boolean holdersMatch(Holder<Biome> left, Holder<Biome> right) {
        if (left == right) {
            return true;
        }
        return left.unwrapKey().isPresent() && right.unwrapKey().isPresent() && left.unwrapKey().equals(right.unwrapKey());
    }
}
