# Expandia

Progression-based world generation for **Minecraft 26.1.2** / **NeoForge 26.1.2.75** (Java 25).

Players spawn on a tiny circular island at world spawn `(0, 0)`. The whole Overworld around it is
forced to **open ocean**, so there is nowhere to go at first. Each time a player completes one of the
configured advancements, **a brand new island appears** elsewhere in the world, regenerated live
without a restart. Progress is saved per world.

---

## Features

- Spawn island centred on `(0, 0)`, default radius **96** blocks.
- Every island shares the same radius; the surrounding water is forced to plain `minecraft:ocean`
  (no deep/frozen/warm variants, so **no icebergs** or stray ocean features).
- **Advancement-driven**: each listed advancement, once completed, spawns **exactly one** new island
  (deduplicated per advancement).
- Islands are placed on a **sunflower (phyllotaxis) spiral** so they spread out evenly with a sea
  channel between neighbours and never overlap.
- **Live reveal**: a newly unlocked island is regenerated on the fly — even if the area had already
  been explored and generated as ocean, those chunks are wiped and rebuilt as the island.
- **JSON config + datapack** support, hot-reloadable.
- **Biome-mod friendly**: Expandia only modifies the land-mask density functions and forces the open
  ocean biome; it never overrides biome selection on land, so it works alongside **Terralith** and
  other biome mods.
- Optional structure generation (off by default).
- Per-world persistence via vanilla `SavedData`.

---

## Requirements

| Dependency | Version |
| --- | --- |
| Minecraft | 26.1.2 |
| NeoForge | 26.1.2.75+ |
| Java | 25 (LTS) |
| Gradle | 9.1+ |
| ModDevGradle | 2.0+ |

> Minecraft 26.1 uses calendar versioning (`year.release.patch`) and ships unobfuscated, so Java 25
> is mandatory and no mapping layer is needed.

---

## Building

```bash
# First run only (generates the wrapper jar if it is missing):
gradle wrapper --gradle-version 9.1

# Then build the mod:
./gradlew build
```

The packaged jar lands in `build/libs/expandia-neoforge-26.1.2-<version>.jar`.

Run the dev client/server:

```bash
./gradlew runClient
./gradlew runServer
```

---

## Configuration

On first launch Expandia writes `config/expandia/progression.json`:

```json
{
  "baseRadius": 96,
  "transitionWidth": 24,
  "generateStructures": false,
  "dimensions": [
    "minecraft:overworld"
  ],
  "advancements": [
    "minecraft:story/mine_stone",
    "minecraft:story/upgrade_tools",
    "minecraft:story/smelt_iron",
    "minecraft:story/iron_tools",
    "minecraft:story/mine_diamond",
    "minecraft:story/obtain_armor",
    "minecraft:story/lava_bucket",
    "minecraft:story/form_obsidian",
    "minecraft:story/enter_the_nether",
    "minecraft:nether/root",
    "minecraft:nether/obtain_blaze_rod",
    "minecraft:nether/find_fortress",
    "minecraft:story/enchant_item",
    "minecraft:story/enter_the_end",
    "minecraft:end/root",
    "minecraft:end/kill_dragon"
  ]
}
```

| Field | Meaning |
| --- | --- |
| `baseRadius` | Radius in blocks of every island (the spawn island and each unlocked one). |
| `transitionWidth` | Width in blocks of the smooth land-to-ocean shoreline band. |
| `generateStructures` | If `false`, overworld structures are disabled on affected dimensions. |
| `dimensions` | Dimensions affected by the land mask. Remove an entry to **disable** Expandia there. |
| `advancements` | List of advancement ids; completing one spawns a new island. Fully data-driven. |

### For modpack developers

- **Add / remove advancements**: edit the `advancements` list (file) or ship a datapack (below).
  Each id grants exactly one island the first time it is completed.
- **Resize islands**: change `baseRadius` (and `transitionWidth` for the coast band).
- **Enable structures**: set `generateStructures` to `true`.
- **Disable a dimension**: remove it from `dimensions` (default is overworld only).
- **Reload without restart**: `/expandia reload` (file) or `/reload` (datapack overlay).

### Datapack support

Datapacks can contribute progression data at
`data/<namespace>/expandia/progression.json`. All such files are merged on `/reload`, then the
on-disk `config/expandia/progression.json` is layered **on top** (the file always wins), letting a
pack ship defaults while server owners still override values.

A ready-to-use example lives in [`examples/datapack/expandia_extra`](examples/datapack/expandia_extra):
it adds the advancements `example:reach_the_shore` and `minecraft:adventure/adventuring_time` to the
island list, so completing either one spawns a new island.

---

## Commands

| Command | Permission | Description |
| --- | --- | --- |
| `/expandia islands` | all | Show the current island count and per-island radius. |
| `/expandia unlock <count>` | op (2) | Spawn `<count>` new islands. |
| `/expandia reset` | op (2) | Reset to the spawn island and regenerate removed islands back to ocean. |
| `/expandia reload` | op (2) | Reload the JSON config and re-apply the land mask. |

---

## How it works

```
AdvancementEarnEvent -> ProgressionManager -> ExpandiaSavedData (island count, per world)
                                           -> ExpandiaRadiusState (live island centres)
                                           -> LandMaskDensityFunction (read live)
                                           -> ChunkRegenManager (live reveal of the new island)
ServerStarting       -> WorldGenInjector forces the ocean biome / disables structures
datapack density_function overrides -> expandia:land_mask wraps vanilla continents/erosion/temperature/vegetation
config file + datapack -> ConfigManager
```

- **Worldgen** (`worldgen/`): the datapack overrides `minecraft:overworld`'s climate density
  functions with `expandia:land_mask`, which wraps the vanilla noise. Near an island centre it keeps
  vanilla terrain; past a smooth, wobbly coast band it blends toward warm deep ocean so each island
  is its own landmass. `ExpandiaOceanBiomeSource` forces plain `minecraft:ocean` on open water (no
  icebergs), and `ExpandiaWorldGenInjector` installs it (and optionally disables structures) at
  server start.
- **Island layout** (`ExpandiaIslandLayout`): island `#0` is the origin; the rest sit on a golden-
  angle spiral. `ExpandiaRadiusState` caches the flat list of centres so the density function can
  find the nearest centre per sample without allocating.
- **Progression** (`progression/`): listens to `AdvancementEvent.AdvancementEarnEvent`; if the
  advancement is configured and not already counted, it spawns one new island and reveals it.
- **Live reveal** (`ExpandiaChunkRegenManager`): the island disc is unloaded, wiped on disk and
  force-regenerated, then pushed to nearby clients. Reveals are queued and run one at a time, always
  restoring the view distance afterwards.
- **Config** (`config/`): `ExpandiaConfigManager` loads/merges the JSON file and datapack overlay.
- **Persistence** (`persistence/`): `ExpandiaSavedData` stores the island count and the advancements
  that already spawned an island, in the overworld data storage.
- **Commands** (`commands/`): the `/expandia` tree.

Because the layout is read live from `ExpandiaRadiusState`, the density function always reflects the
current islands, and `ExpandiaChunkRegenManager` rebuilds the affected discs so new islands appear
(and reset islands sink back to ocean) without a restart.

---

## Compatibility

- **Terralith / biome mods**: Expandia never replaces or wraps the biome source and never overrides
  biome selection or surface rules. It only modifies the final-density land mask, so biome mods keep
  full control. Load order is `AFTER` Terralith.
- **Other dimensions**: only dimensions listed in `dimensions` are affected; the Nether/End and modded
  dimensions are untouched unless you add them.

---

## Publishing to CurseForge

1. `./gradlew build`
2. Upload `build/libs/expandia-neoforge-26.1.2-<version>.jar`.
3. Set the game version to **26.1.2**, modloader **NeoForge**, and mark Terralith as an optional
   dependency.

---

## Project layout

```
src/main/java/com/expandia/
  Expandia.java                 mod entry point
  ExpandiaConstants.java        mod id, ResourceLocation + NBT key helpers
  registry/                     density-function type registration
  config/                       JSON + datapack config
  progression/                  advancement listening + radius math
  persistence/                  SavedData
  worldgen/                     land-mask density function + runtime injector
  commands/                     /expandia command tree
src/main/templates/META-INF/neoforge.mods.toml   metadata template (filled from gradle.properties)
config/expandia/progression.json                 example on-disk config
examples/datapack/expandia_extra                 example custom advancement integration
```

## Notes / caveats

This mod targets the very recent, deobfuscated Minecraft 26.1 API and relies on a few internal hooks
that may need minor adjustment when compiling against the exact NeoForge 26.1.2.75 sources:

- `ExpandiaWorldGenInjector` uses `sun.misc.Unsafe` to swap the biome source / structure state at
  runtime, and `ExpandiaChunkRegenManager`/`ExpandiaServerChunkCacheInternals` reach into `ChunkMap`
  and `ServerChunkCache` internals to wipe and regenerate island discs live.
- The land mask itself is data-driven through the `expandia:land_mask` density-function type and the
  datapack overrides under `data/minecraft/worldgen/density_function/overworld/`, so terrain shaping
  keeps working even if a runtime hook needs updating.

## License

MIT.
