package com.expandia.worldgen;

import com.mojang.serialization.Lifecycle;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.levelgen.structure.StructureSet;

import java.util.Optional;
import java.util.stream.Stream;

/** Holder lookup that exposes no structure sets, preventing overworld structures from generating. */
public final class ExpandiaEmptyStructureSetLookup implements HolderLookup.RegistryLookup<StructureSet> {

    public static final ExpandiaEmptyStructureSetLookup INSTANCE = new ExpandiaEmptyStructureSetLookup();

    private ExpandiaEmptyStructureSetLookup() {
    }

    @Override
    public ResourceKey<? extends Registry<? extends StructureSet>> key() {
        return Registries.STRUCTURE_SET;
    }

    @Override
    public Lifecycle registryLifecycle() {
        return Lifecycle.stable();
    }

    @Override
    public Stream<Holder.Reference<StructureSet>> listElements() {
        return Stream.empty();
    }

    @Override
    public Stream<HolderSet.Named<StructureSet>> listTags() {
        return Stream.empty();
    }

    @Override
    public Optional<Holder.Reference<StructureSet>> get(ResourceKey<StructureSet> key) {
        return Optional.empty();
    }

    @Override
    public Optional<HolderSet.Named<StructureSet>> get(TagKey<StructureSet> tag) {
        return Optional.empty();
    }
}
