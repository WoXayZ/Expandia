# Expandia

Progression-based world generation for **Minecraft 1.21.1** / **NeoForge 21.1.248** (Java 21).

Players spawn on a tiny circular island at world spawn `(0, 0)`. The rest of the Overworld is
forced to **open ocean**. Each time a listed advancement is completed, **a new island appears**
elsewhere, regenerated live without a restart. The Nether works the same way, except the sea is
lava. Progress is saved per world and shared by every player on the server.

---

## Features

- Spawn island centred on `(0, 0)`, default radius **32** blocks. Every island uses the same size.
- **Overworld**: surrounding water is plain `minecraft:ocean` (no deep/frozen/warm variants, so
  **no icebergs** or stray ocean features).
- **Nether**: same island layout, lava instead of water. Spawn is pinned to `(0, 0)`. Going through
  a portal snaps you onto the nearest island instead of dropping you in the lava sea.
- **Advancement-driven**: each listed advancement, once completed, spawns **exactly one** new island
  (deduplicated per advancement, per world). Overworld advancements grow the Overworld; Nether
  advancements grow the Nether. `minecraft:story/enter_the_nether` and `minecraft:nether/root` never
  grant an island (they only open the dimension).
- Islands are placed on a **sunflower (phyllotaxis) spiral** so they spread out evenly with a sea
  (or lava) channel between neighbours and never overlap.
- **Live reveal**: a newly unlocked island is regenerated on the fly - even if the area had already
  been explored as ocean/lava, those chunks are wiped and rebuilt as the island.
- **Island advancements screen** (default key **K**): search and filter which advancements are still
  locked, and which ones already spawned an island.
- **Multiplayer**: the map is shared. The first player to complete a listed advancement reveals that
  island for everyone. Completing the same advancement later does not spawn a second copy.
- **JSON config + datapack** support, hot-reloadable.
- **Biome-mod friendly**: Expandia only modifies the land-mask density functions and forces the empty
  ocean/lava biome; it never overrides biome selection on land, so it works alongside **Terralith**
  and other biome mods.
- Overworld structures, caves, aquifers and mineral ores are always disabled. Underground stone
  variants (deepslate, granite, diorite, andesite, tuff) still generate. Nether structures are
  stripped the same way.
- Per-world persistence via vanilla `SavedData`.

---

## Requirements

| Dependency | Version |
| --- | --- |
| Minecraft | 1.21.1 |
| NeoForge | 21.1.248+ |
| Java | 21 |
| Gradle | 9.1+ |
| ModDevGradle | 2.0+ |

---

## Building

```bash
# First run only (generates the wrapper jar if it is missing):
gradle wrapper --gradle-version 9.1

# Then build the mod:
./gradlew build
```

The packaged jar lands in `build/libs/expandia-neoforge-1.21.1-<version>+mc1.21.1.jar`.

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
  "baseRadius": 32,
  "transitionWidth": 24,
  "overworld": {
    "advancements": [
      "minecraft:story/mine_stone",
      "minecraft:story/smelt_iron",
      "minecraft:end/kill_dragon"
    ]
  },
  "nether": {
    "advancements": [
      "minecraft:nether/find_fortress",
      "minecraft:nether/obtain_blaze_rod"
    ]
  }
}
```

The shipped default list is much longer (vanilla Story / Adventure / Husbandry / End for the
Overworld, Nether tab for the Nether). See
[`src/main/resources/data/expandia/expandia/progression.json`](src/main/resources/data/expandia/expandia/progression.json).

| Field | Meaning |
| --- | --- |
| `baseRadius` | Radius in blocks of every island (the spawn island and each unlocked one). |
| `transitionWidth` | Width in blocks of the smooth land-to-ocean (or lava) shoreline band. |
| `overworld.advancements` | Advancement ids that spawn an Overworld island. |
| `nether.advancements` | Advancement ids that spawn a Nether island. |

A legacy top-level `advancements` array is still accepted: ids under `minecraft:nether/…` go to the
Nether, everything else to the Overworld.

### For modpack developers

- **Add / remove advancements**: edit the matching list (file) or ship a datapack (below).
  Each id grants exactly one island the first time it is completed on that world.
- **Resize islands**: change `baseRadius` (and `transitionWidth` for the coast band).
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
| `/expandia islands` | all | Show Overworld / Nether island counts and the shared radius. |
| `/expandia unlock <count>` | op (2) | Spawn `<count>` new islands in the player's current dimension. |
| `/expandia reset` | op (2) | Reset both dimensions to the spawn island and regenerate removed islands back to ocean/lava. |
| `/expandia reload` | op (2) | Reload the JSON config and re-apply the land mask. |

---

## Multiplayer

Works on LAN and dedicated servers. Islands belong to the **world**, not to each player.

- The first player to complete a listed advancement reveals that island for everyone.
- If someone else completes the same advancement later, nothing extra spawns.
- New land is in the world for the whole server. Anyone can sail (or strider) there.
- The K screen shows world unlocks, plus which of those advancements *you* have done.

---

## How it works

```
AdvancementEarnEvent -> ProgressionManager -> ExpandiaSavedData (per-world, Overworld + Nether)
                                           -> ExpandiaRadiusState (live island centres)
                                           -> LandMaskDensityFunction (read live)
                                           -> ChunkRegenManager (live reveal of the new island)
ServerStarting       -> WorldGenInjector forces ocean / lava biomes, disables structures
datapack density_function overrides -> expandia:land_mask wraps vanilla continents/erosion/temperature/vegetation
config file + datapack -> ConfigManager
client keybind K -> IslandAdvancementsScreen (synced over the network)
```

- **Worldgen** (`worldgen/`): the datapack overrides `minecraft:overworld`'s climate density
  functions with `expandia:land_mask`, which wraps the vanilla noise. Near an island centre it keeps
  vanilla terrain; past a smooth, wobbly coast band it blends toward ocean so each island is its own
  landmass. The Nether gets a lava-ocean land mask the same way. `ExpandiaOceanBiomeSource` forces
  plain `minecraft:ocean` (or `minecraft:nether_wastes` off-island in the Nether), and
  `ExpandiaWorldGenInjector` installs it, disables structures, fills caves and turns off aquifers /
  noise ore veins at server start. Portal arrivals that would land in the empty sea are snapped to
  the nearest island (`ExpandiaPortalEvents`).
- **Island layout** (`ExpandiaIslandLayout`): island `#0` is the origin; the rest sit on a golden-
  angle spiral. `ExpandiaRadiusState` caches the flat list of centres so the density function can
  find the nearest centre per sample without allocating.
- **Progression** (`progression/`): listens to `AdvancementEvent.AdvancementEarnEvent`; if the
  advancement is configured and not already counted for that dimension, it spawns one new island and
  reveals it.
- **Live reveal** (`ExpandiaChunkRegenManager`): the island disc is unloaded, wiped on disk and
  force-regenerated, then pushed to nearby clients. Reveals are queued and run one at a time, always
  restoring the view distance afterwards.
- **Client** (`client/`): keybind **K** opens the island advancements screen. The server sends the
  current unlock list over `network/`.
- **Config** (`config/`): `ExpandiaConfigManager` loads/merges the JSON file and datapack overlay.
- **Persistence** (`persistence/`): `ExpandiaSavedData` stores the island slots and the advancements
  that already spawned an island, in the overworld data storage (both dimensions).
- **Commands** (`commands/`): the `/expandia` tree.

Because the layout is read live from `ExpandiaRadiusState`, the density function always reflects the
current islands, and `ExpandiaChunkRegenManager` rebuilds the affected discs so new islands appear
(and reset islands sink back to ocean/lava) without a restart.

---

## Compatibility

- **Terralith / biome mods**: Expandia never replaces biome selection or surface rules on land. It
  only masks land vs ocean (or lava). Load order is `AFTER` Terralith.
- **Other dimensions**: the Overworld and the Nether are affected. The End and modded dimensions are
  untouched.

---

## Publishing to CurseForge

1. `./gradlew build`
2. Upload `build/libs/expandia-neoforge-1.21.1-<version>+mc1.21.1.jar`.
3. Set the game version to **1.21.1**, modloader **NeoForge**, and mark Terralith as an optional
   dependency.

Project page: <https://www.curseforge.com/minecraft/mc-mods/expandia>

---

## Project layout

```
src/main/java/com/expandia/
  Expandia.java                 mod entry point
  ExpandiaConstants.java        mod id, ResourceLocation + NBT key helpers
  client/                       island advancements screen + keybind
  network/                      sync of unlocks to the client
  registry/                     density-function type registration
  config/                       JSON + datapack config
  progression/                  advancement listening + radius math
  persistence/                  SavedData (Overworld + Nether)
  worldgen/                     land-mask density function, nether lava ocean, portal snap
  commands/                     /expandia command tree
src/main/templates/META-INF/neoforge.mods.toml   metadata template (filled from gradle.properties)
config/expandia/progression.json                 example on-disk config
examples/datapack/expandia_extra                 example custom advancement integration
```

## Notes / caveats

This mod targets Minecraft 1.21.1 / NeoForge 21.1.248 and relies on a few internal hooks:

- `ExpandiaWorldGenInjector` uses `sun.misc.Unsafe` to swap the biome source, noise settings and
  structure state at runtime, and `ExpandiaChunkRegenManager` reaches into `ChunkMap` / `ServerChunkCache`
  internals to wipe and regenerate island discs live.
- Caves, aquifers and noise ore veins are disabled at runtime; mineral ore features and carvers are
  stripped with biome modifiers. Granite, diorite, andesite and tuff blobs are kept.
- The land mask itself is data-driven through the `expandia:land_mask` density-function type and the
  datapack overrides under `data/minecraft/worldgen/density_function/overworld/`.

## License

MIT.
