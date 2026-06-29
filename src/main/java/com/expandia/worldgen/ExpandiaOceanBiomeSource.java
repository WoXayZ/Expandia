package com.expandia.worldgen;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;

import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

public final class ExpandiaOceanBiomeSource extends BiomeSource {

    public static final MapCodec<ExpandiaOceanBiomeSource> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            BiomeSource.CODEC.fieldOf("delegate").forGetter(ExpandiaOceanBiomeSource::delegate),
            Biome.CODEC.fieldOf("ocean").forGetter(ExpandiaOceanBiomeSource::ocean)
    ).apply(instance, ExpandiaOceanBiomeSource::new));

    private final BiomeSource delegate;
    private final Holder<Biome> ocean;
    private volatile List<Holder<Biome>> islandBiomes;
    private volatile Holder<Biome> spawnBiome;

    public ExpandiaOceanBiomeSource(BiomeSource delegate, Holder<Biome> ocean) {
        this.delegate = delegate;
        this.ocean = ocean;
    }

    public BiomeSource delegate() {
        return this.delegate;
    }

    public Holder<Biome> ocean() {
        return this.ocean;
    }

    @Override
    protected MapCodec<? extends BiomeSource> codec() {
        return CODEC;
    }

    @Override
    protected Stream<Holder<Biome>> collectPossibleBiomes() {
        return Stream.concat(this.delegate.possibleBiomes().stream(), Stream.of(this.ocean)).distinct();
    }

    @Override
    public Holder<Biome> getNoiseBiome(int quartX, int quartY, int quartZ, Climate.Sampler sampler) {
        int blockX = QuartPos.toBlock(quartX);
        int blockZ = QuartPos.toBlock(quartZ);
        if (!ExpandiaRadiusState.isWithinIslandReach(blockX, blockZ)) {
            return this.ocean;
        }
        List<Holder<Biome>> biomes = islandBiomes();
        if (biomes.isEmpty()) {
            return this.delegate.getNoiseBiome(quartX, quartY, quartZ, sampler);
        }
        int slot = ExpandiaRadiusState.nearestIslandSlot(blockX, blockZ);
        if (slot == 0) {
            return spawnBiome();
        }
        return biomes.get(Math.floorMod(slot, biomes.size()));
    }

    private Holder<Biome> spawnBiome() {
        Holder<Biome> cached = this.spawnBiome;
        if (cached != null) {
            return cached;
        }
        List<Holder<Biome>> biomes = islandBiomes();
        Holder<Biome> chosen = biomes.stream()
                .filter(b -> "minecraft:forest".equals(biomeId(b)))
                .findFirst()
                .or(() -> biomes.stream().filter(b -> b.is(BiomeTags.IS_FOREST)).findFirst())
                .orElseGet(() -> biomes.isEmpty() ? this.ocean : biomes.get(0));
        this.spawnBiome = chosen;
        return chosen;
    }

    private List<Holder<Biome>> islandBiomes() {
        List<Holder<Biome>> cached = this.islandBiomes;
        if (cached != null) {
            return cached;
        }
        Set<Holder<Biome>> possible = this.delegate.possibleBiomes();
        cached = possible.stream()
                .filter(ExpandiaOceanBiomeSource::isIslandBiome)
                .sorted(Comparator.comparingInt(ExpandiaOceanBiomeSource::shuffleKey)
                        .thenComparing(ExpandiaOceanBiomeSource::biomeId))
                .toList();
        this.islandBiomes = cached.isEmpty() ? List.copyOf(possible) : cached;
        return this.islandBiomes;
    }

    private static boolean isIslandBiome(Holder<Biome> biome) {
        if (biome.is(BiomeTags.IS_OCEAN) || biome.is(BiomeTags.IS_RIVER) || biome.is(BiomeTags.IS_BEACH)) {
            return false;
        }
        return biome.unwrapKey().map(key -> {
            Identifier id = key.identifier();
            if (!"minecraft".equals(id.getNamespace())) {
                return true;
            }
            return switch (id.getPath()) {
                case "lush_caves", "dripstone_caves", "deep_dark", "the_void", "stony_shore" -> false;
                default -> true;
            };
        }).orElse(false);
    }

    private static String biomeId(Holder<Biome> biome) {
        return biome.unwrapKey().map(key -> key.identifier().toString()).orElse("");
    }

    private static int shuffleKey(Holder<Biome> biome) {
        return biomeId(biome).hashCode() * 0x9E3779B1;
    }
}
