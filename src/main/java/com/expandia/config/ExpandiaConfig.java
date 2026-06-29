package com.expandia.config;

import net.minecraft.resources.Identifier;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

public record ExpandiaConfig(
        int baseRadius,
        int transitionWidth,
        Set<Identifier> advancements
) {

    public ExpandiaConfig {
        advancements = Collections.unmodifiableSet(new LinkedHashSet<>(advancements));
    }

    public static ExpandiaConfig defaults() {
        Set<Identifier> advancements = new LinkedHashSet<>();
        advancements.add(Identifier.parse("minecraft:story/mine_stone"));
        advancements.add(Identifier.parse("minecraft:story/upgrade_tools"));
        advancements.add(Identifier.parse("minecraft:story/smelt_iron"));
        advancements.add(Identifier.parse("minecraft:story/iron_tools"));
        advancements.add(Identifier.parse("minecraft:story/mine_diamond"));
        advancements.add(Identifier.parse("minecraft:story/obtain_armor"));
        advancements.add(Identifier.parse("minecraft:story/lava_bucket"));
        advancements.add(Identifier.parse("minecraft:story/form_obsidian"));
        advancements.add(Identifier.parse("minecraft:story/enter_the_nether"));
        advancements.add(Identifier.parse("minecraft:nether/root"));
        advancements.add(Identifier.parse("minecraft:nether/obtain_blaze_rod"));
        advancements.add(Identifier.parse("minecraft:nether/find_fortress"));
        advancements.add(Identifier.parse("minecraft:story/enchant_item"));
        advancements.add(Identifier.parse("minecraft:story/enter_the_end"));
        advancements.add(Identifier.parse("minecraft:end/root"));
        advancements.add(Identifier.parse("minecraft:end/kill_dragon"));

        return new ExpandiaConfig(96, 24, advancements);
    }

    public boolean isIslandAdvancement(Identifier advancement) {
        return advancements.contains(advancement);
    }
}
