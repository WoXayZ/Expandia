package com.expandia.config;

import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public record ExpandiaConfig(
        int baseRadius,
        int transitionWidth,
        DimensionPack overworld,
        DimensionPack nether
) {

    public ExpandiaConfig {
        overworld = overworld == null ? DimensionPack.overworldDefaults() : overworld;
        nether = nether == null ? DimensionPack.netherDefaults() : nether;
    }

    public record DimensionPack(List<ResourceLocation> advancements) {
        public DimensionPack {
            List<ResourceLocation> filtered = new ArrayList<>();
            for (ResourceLocation id : advancements) {
                if (!isNetherEntryAdvancement(id)) {
                    filtered.add(id);
                }
            }
            advancements = List.copyOf(filtered);
        }

        public static DimensionPack overworldDefaults() {
            return new DimensionPack(defaultOverworldAdvancements());
        }

        public static DimensionPack netherDefaults() {
            return new DimensionPack(defaultNetherAdvancements());
        }
    }

    public static ExpandiaConfig defaults() {
        return new ExpandiaConfig(32, 24, DimensionPack.overworldDefaults(), DimensionPack.netherDefaults());
    }

    public DimensionPack pack(ResourceKey<Level> dimension) {
        return Level.NETHER.equals(dimension) ? nether : overworld;
    }

    public boolean isNetherAdvancement(ResourceLocation advancement) {
        return nether.advancements().contains(advancement);
    }

    public boolean isOverworldAdvancement(ResourceLocation advancement) {
        return overworld.advancements().contains(advancement);
    }

    public boolean isIslandAdvancement(ResourceLocation advancement) {
        return !isNetherEntryAdvancement(advancement)
                && (isOverworldAdvancement(advancement) || isNetherAdvancement(advancement));
    }

    public ResourceKey<Level> dimensionForAdvancement(ResourceLocation advancement) {
        if (isNetherAdvancement(advancement)) {
            return Level.NETHER;
        }
        return Level.OVERWORLD;
    }

    public static boolean isNetherEntryAdvancement(ResourceLocation advancement) {
        if (advancement == null) {
            return false;
        }
        String key = advancement.toString();
        return "minecraft:story/enter_the_nether".equals(key)
                || "minecraft:nether/root".equals(key);
    }

    public List<ResourceLocation> allAdvancements() {
        List<ResourceLocation> all = new ArrayList<>(overworld.advancements());
        all.addAll(nether.advancements());
        return Collections.unmodifiableList(all);
    }

    private static List<ResourceLocation> ids(String... paths) {
        List<ResourceLocation> list = new ArrayList<>(paths.length);
        for (String path : paths) {
            list.add(ResourceLocation.parse(path));
        }
        return list;
    }

    private static List<ResourceLocation> defaultOverworldAdvancements() {
        return ids(
                "minecraft:story/mine_stone",
                "minecraft:story/upgrade_tools",
                "minecraft:story/smelt_iron",
                "minecraft:story/iron_tools",
                "minecraft:story/mine_diamond",
                "minecraft:story/obtain_armor",
                "minecraft:story/lava_bucket",
                "minecraft:story/form_obsidian",
                "minecraft:story/enchant_item",
                "minecraft:story/enter_the_end",
                "minecraft:adventure/root",
                "minecraft:adventure/sleep_in_bed",
                "minecraft:adventure/kill_a_mob",
                "minecraft:adventure/shoot_arrow",
                "minecraft:adventure/trade",
                "minecraft:adventure/trade_at_world_height",
                "minecraft:adventure/honey_block_slide",
                "minecraft:adventure/walk_on_powder_snow_with_leather_boots",
                "minecraft:adventure/summon_iron_golem",
                "minecraft:adventure/totem_of_undying",
                "minecraft:adventure/throw_trident",
                "minecraft:adventure/very_very_frightening",
                "minecraft:adventure/sniper_duel",
                "minecraft:adventure/ol_betsy",
                "minecraft:adventure/whos_the_pillager_now",
                "minecraft:adventure/two_birds_one_arrow",
                "minecraft:adventure/arbalistic",
                "minecraft:adventure/voluntary_exile",
                "minecraft:adventure/hero_of_the_village",
                "minecraft:adventure/lightning_rod_with_villager_no_fire",
                "minecraft:adventure/spyglass_at_parrot",
                "minecraft:adventure/spyglass_at_ghast",
                "minecraft:adventure/fall_from_world_height",
                "minecraft:adventure/play_jukebox_in_meadows",
                "minecraft:adventure/kill_mob_near_sculk_catalyst",
                "minecraft:adventure/avoid_vibration",
                "minecraft:adventure/trim_with_any_armor_pattern",
                "minecraft:adventure/salvage_sherd",
                "minecraft:adventure/craft_decorated_pot_using_only_sherds",
                "minecraft:adventure/read_power_of_chiseled_bookshelf",
                "minecraft:adventure/brush_armadillo",
                "minecraft:adventure/minecraft_trials_edition",
                "minecraft:adventure/crafters_crafting_crafters",
                "minecraft:adventure/lighten_up",
                "minecraft:adventure/who_needs_rockets",
                "minecraft:adventure/under_lock_and_key",
                "minecraft:adventure/revaulting",
                "minecraft:adventure/blowback",
                "minecraft:adventure/overoverkill",
                "minecraft:husbandry/root",
                "minecraft:husbandry/plant_seed",
                "minecraft:husbandry/breed_an_animal",
                "minecraft:husbandry/tame_an_animal",
                "minecraft:husbandry/fishy_business",
                "minecraft:husbandry/tactical_fishing",
                "minecraft:husbandry/safely_harvest_honey",
                "minecraft:husbandry/wax_on",
                "minecraft:husbandry/wax_off",
                "minecraft:husbandry/make_a_sign_glow",
                "minecraft:husbandry/ride_a_boat_with_a_goat",
                "minecraft:husbandry/axolotl_in_a_bucket",
                "minecraft:husbandry/kill_axolotl_target",
                "minecraft:husbandry/tadpole_in_a_bucket",
                "minecraft:husbandry/froglights",
                "minecraft:husbandry/silk_touch_nest",
                "minecraft:husbandry/obtain_sniffer_egg",
                "minecraft:husbandry/feed_snifflet",
                "minecraft:husbandry/plant_any_sniffer_seed",
                "minecraft:husbandry/whole_pack",
                "minecraft:husbandry/remove_wolf_armor",
                "minecraft:husbandry/repair_wolf_armor",
                "minecraft:husbandry/balanced_diet",
                "minecraft:husbandry/complete_catalogue",
                "minecraft:husbandry/bred_all_animals",
                "minecraft:husbandry/netherite_hoe",
                "minecraft:end/root",
                "minecraft:end/kill_dragon",
                "minecraft:end/enter_end_city",
                "minecraft:end/find_elytra",
                "minecraft:end/levitate"
        );
    }

    private static List<ResourceLocation> defaultNetherAdvancements() {
        return ids(
                "minecraft:nether/find_fortress",
                "minecraft:nether/obtain_blaze_rod",
                "minecraft:nether/brew_potion",
                "minecraft:nether/obtain_crying_obsidian",
                "minecraft:nether/charge_respawn_anchor",
                "minecraft:nether/distract_piglin",
                "minecraft:nether/explore_nether",
                "minecraft:nether/ride_strider",
                "minecraft:nether/ride_strider_in_overworld_lava",
                "minecraft:nether/use_lodestone",
                "minecraft:nether/find_bastion",
                "minecraft:nether/loot_bastion",
                "minecraft:nether/obtain_ancient_debris",
                "minecraft:nether/netherite_armor",
                "minecraft:nether/get_wither_skull",
                "minecraft:nether/return_to_sender",
                "minecraft:nether/fast_travel",
                "minecraft:nether/uneasy_alliance",
                "minecraft:nether/summon_wither",
                "minecraft:nether/create_beacon",
                "minecraft:nether/create_full_beacon",
                "minecraft:nether/all_potions",
                "minecraft:nether/all_effects"
        );
    }
}
