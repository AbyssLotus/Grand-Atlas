# Backport plan: Grand Atlas on Minecraft 1.21.1 (NeoForge)

## Outcome (2026-11-11)

Built: `mc1211/build/libs/grandatlas-0.12.0-test22+mc1.21.1.jar` (NeoForge 21.1.197 or newer, Java 21).
Phases 0 to 7 are done on the dev server. **Nobody has looked at it in a game client yet**, and
phase 8 (the Create Vast pack) is not started: it changes that pack's mod list, which is the user's call.

### How it is laid out

- `mc1211/` is a Gradle subproject. It compiles the terrain core **straight from
  `src/main/java/com/seaweed/grandatlas/core`**, the same files as the 26.3 build, so a core change
  lands on both. Its own `src/` holds only the Minecraft-facing layer (world, mixin, client).
- Textures, the packed tree and rock assets and the credits are shared the same way (see
  `mc1211/build.gradle`). All other resources are written by `python tools/gen_data.py --target 1.21.1`.
- A bare `gradlew runServer` now starts BOTH versions. Use `gradlew :runServer` (26.3) or
  `gradlew :mc1211:runServer` (1.21.1; `mc1211/run`, RCON port 25601, game port 25566).
  `tools/rcon.py` reads the port from `GA_RCON_PORT`.

### What the plan got wrong

The plan expected a compile-fix. 26.3 had rewritten three APIs the mod sits on, so these were
written again for 1.21.1:

| 26.3 | 1.21.1 |
|---|---|
| Density functions compile to samplers that are handed whole volumes, with the world's random source | One point at a time, no random source: `mixin/RandomStateMixin` marks the wiring of a world's router and the functions bind to that seed's core (`world/CoreHandle`); `world/PerColumn` keeps the per-column work |
| Material rules are a registry, with named conditions and a 3D noise switch | No registry: `gen_data.py` inlines every rule into the noise settings; `world/Noise3d` is the 3D noise condition |
| The preliminary surface is a function the mod supplies | Found by scanning: `mixin/NoiseChunkMixin` and `mixin/SurfaceContextMixin` answer from the core |
| Features are records with a codec | Classes with a configuration: `world/AtlasFeature` adapts ours unchanged |
| Noise amplitudes | 1.21.1 scales noise differently; `gen_data.py` converts (`parity_base_amplitude`) so thresholds cut the same patches |

### Measured against 26.3 (dev seed, same world seed)

- **Core seed and chosen spawn are identical** (-2953816398943392853; 1440 70 1920).
- **Block-for-block, six sites, 16 to 32 million blocks each: 0.06% to 0.64% differ.** Source water
  and lava differ by 3 blocks in total. What differs is what each game version places with its own
  randomness: trees and leaves (also Better Trees 1.9.3 against 2.0.0), grass and flowers, ore blobs,
  a few carver tunnels. Compare with `world_diff.py` (scratch) on two region folders.
- **Villages, ancient cities and trial chambers are found at the same coordinates.** Eleven locate
  commands, one searching 7 km, took 46 s in all; none hung.
- **Without Vanilla Backport the world loads and generates**, with each stand-in logged once
  (Pale Garden as Dark Forest, Sulfur Caves as Dripstone Caves, pale oak as dark oak, dry grass as
  dead bush, bush as fern).
- **Speed: slower than 26.3, and most of it is the game.** A fresh 16x16-chunk square: 1.21.1
  60.6 s and 680 ms CPU a chunk; 26.3 26.4 s and 453 ms. 1.21.1 runs most worldgen steps on one
  thread (26.3 runs them in parallel), which is the wall-clock gap. The CPU gap is the
  point-at-a-time sampling. Three fixes brought it down from about 4 times slower in wall time:
  the set pieces' column cache (shared core), `mixin/BeardifierMixin`, `mixin/AquiferMixin`, and
  `world/ForeignRules` (below).

### Vanilla Backport, as found in its 1.2.2 jar

- Registers everything under plain `minecraft:` ids, so `world/Palette` looks blocks up by vanilla id.
- Needs the library mod Platform (1.4 or newer).
- Has pale oak, leaf litter, bush, short and tall dry grass, firefly bush, sulfur and cinnabar, and
  the biomes `minecraft:pale_garden`, `minecraft:sulfur_caves`, `minecraft:dappled_forest`.
- Lays the Sulfur Caves' bands itself, by wrapping the overworld surface rule at run time with a
  rule that asks the biome of every block of rock. In this world's 1,536 levels that was 14% of chunk
  generation. `world/ForeignRules` (through `mixin/NoiseBasedChunkGeneratorMixin`) puts its rules
  behind the cave gate; the bands come out the same (73,657 sulfur blocks against 26.3's 73,753 in
  one test box).
- Its Pale Garden has fewer trees and less hanging moss than 26.3's own.

### Found on the way, fixed in both versions

- **26.3 bug: the deep cave biomes' rock replaced bedrock.** 9% of the bottom level under a Sulfur
  Cave was sulfur and cinnabar. The cave rules now stop five levels above the floor (`gen_data.py`).
  The 26.3 jar is rebuilt as test22 for this; its bottom level is all bedrock again.

### Later the same day: test23, from running the Create Atlas pack

Running 160 mods on it (`../create-atlas-pack`) found three things, all fixed in the 1.21.1 build:

- **C2ME hid the core.** It compiles the noise router into its own classes, so finding the core by
  looking through the density functions returned nothing, and every feature, rule, mixin shortcut
  and the chosen spawn switched themselves off without a word. The core is now recorded on the
  world's `RandomState` when its router is wired (`CoreHandle.coreFrom(RandomState)`,
  `world/AtlasRandomState`); the noise chunk takes it from there at construction.
- **Structure searches generated whole columns.** `getBaseHeight` builds a column of terrain per
  call. Integrated API's wide searches ran one `/locate` past the 60-second watchdog. It is now
  answered from the core (`mixin/NoiseBasedChunkGeneratorMixin`): ground, plus river, lake or sea
  for the surface kinds. Not in the 26.3 build.
- **The tight village grid shut out other mods' villages.** It is now set in code
  (`world/VillageDensity`), only when Integrated Villages is absent, and the riverside rule applies
  to `minecraft:` villages only.

With C2ME the fresh 16x16-chunk square takes 19.5 s (60.6 s without it; 26.3 takes 26.4 s).

### Not verified

- Anything on screen: the aurora ribbons (rewritten for 1.21.1's render event), river water's look,
  the new blocks' models, shaders. The dev client starts and loads resources with no missing model or
  texture; that is all that was checked.
- The Iris compatibility mixin against a 1.21.1 Iris build.
- Place names (still untested on 26.3 too).

---

The plan as drafted before the work follows.

Drafted 2026-11-11. Nothing here is started. Companion note: `BACKPORT_1.21.1_MODS.md`.

## Summary

- The mod is 18,900 lines of Java. **12,830 of them (`core/`, 51 files) import nothing from
  Minecraft or NeoForge** and move to 1.21.1 unchanged. The port is the other 6,300 lines
  (`world/` 31 files, `mixin/` 5, `client/` 1, 3 top-level), the generated data, and re-testing.
- Newer vanilla content comes from **Vanilla Backport** as an optional companion: with it
  installed the 1.21.1 world matches 26.3; without it Grand Atlas still loads and swaps in
  1.21.1 blocks and biomes.
- Estimate: **5 to 6 working days of my time**, then your in-game review.

## Vanilla Backport: what is known and what is not

Known (from its licence text, wiki and file listings):

- Licence (VBL) allows a soft or hard dependency on it, and allows unmodified official releases
  in modpacks. It forbids re-hosting or redistributing it. So Grand Atlas **cannot bundle it
  inside its own jar**; players and the mrpack get it from Modrinth or CurseForge.
- NeoForge 1.21.1 builds exist; the newest listed is 1.2 (2026-10-18).
- The Garden Awakens content it lists: pale oak wood set, pale moss, eyeblossoms, creaking heart,
  resin. Its Pale Garden is "a biome variation of Dark Forest".

Not known, and phase 0 settles each by opening the jar:

- **Block and biome ids.** `minecraft:pale_oak_log` or `vanillabackport:pale_oak_log`. The repo
  has a branch named `Namespace-Update`, so this may have changed between versions.
- **Whether 1.2 really has Sulfur Caves and Wilderness Bound on 1.21.1.** The file listings say
  yes (Sulfur Caves from 1.1.7, Wilderness Bound in 1.2); its wiki roadmap still says no. The
  wiki looks stale.
- **Whether it has dry grass, bush and firefly bush.** Its notes name leaf litter; I could not
  confirm the other three. If they are missing, `Ported` (MIT) has them, or we fall back.
- **How it places its biomes**, and its own dependencies.

## Decisions for you

1. **Download approval.** Phase 0 needs the Vanilla Backport 1.2 NeoForge 1.21.1 jar from its
   official Modrinth page, to inspect and to run on the dev server.
2. **Keep 26.3 alive, or move for good.** Recommended: keep both, with the shared core (phase 1).
   Core work then lands on both versions for free; only `world/` changes are done twice.
3. **Soft or hard dependency on Vanilla Backport.** Recommended: soft, with fallbacks (phase 4).
4. **Better Trees on 1.21.1.** Not checked. If it has no 1.21.1 build, the pack trees carry the
   forests alone and vanilla trees fill the rest.

## Phases

### Phase 0: checks before any code (about 2 hours)

- Open the Vanilla Backport jar: ids of every block and biome in the table in
  `BACKPORT_1.21.1_MODS.md`, its dependencies, its biome placement method, its config.
- Check 1.21.1 builds exist for Better Trees, Iris, Distant Horizons and Lumina.
- Write the answers into this file. If Vanilla Backport lacks something we use, decide per item:
  second mod, or fallback.

### Phase 1: split the project (half a day)

- Gradle multi-project: `core` (plain Java 21 library, with `SelfCheck`), `mc-26.3` (today's
  `world/`, `mixin/`, `client/`, resources), `mc-1.21.1` (new).
- `core` drops to Java 21 so both versions can use it. 1.21.1 runs on Java 21.
- Proof it changed nothing: 26.3 jar builds, "All core checks passed", and a region census on the
  dev seed matches the current build within the usual 0.01 to 0.15% ore and plant noise.

### Phase 2: 1.21.1 skeleton that compiles (about a day)

Copy `world/`, `mixin/`, `client/` into `mc-1.21.1` and fix what the compiler reports. Known items:

| Change | Size |
|---|---|
| `Identifier` back to `ResourceLocation` | 15 uses, 6 files |
| `Blocks.DYED_TERRACOTTA.orange()` back to `Blocks.ORANGE_TERRACOTTA` and friends | 7 uses, 3 files |
| World spawn: `LevelData.RespawnData` to `setSpawn(BlockPos, float)` | `SpawnChooser` |
| Click events in `/grandatlas locate` (older `ClickEvent` constructor) | `AtlasCommands` |
| Item models: 26.3's `assets/grandatlas/items/` goes; 1.21.1 uses `models/item/` | generated |
| Fluid client setup: `RegisterFluidModelsEvent` and `FluidTintSources` do not exist; use the client fluid extensions | `GrandAtlasClient` |
| Codecs, features, material rules, density functions, events | expected to compile nearly as they are (56 `MapCodec` uses to confirm) |

Done when: the dev server starts and generates chunks, with missing blocks stubbed to fallbacks.

### Phase 3: generated data (half a day)

`tools/gen_data.py` gains a target switch, writing into each version's resources.

- `dimension_type/overworld.json` and the 19 custom biome files: 1.21.1 uses the older layout
  (plain `effects` colours and flags, not 26.x environment attributes).
- Height stays -512 to 1024. 1.21.1 allows it.
- Overrides under `data/minecraft` (noise settings, placed features, structures, structure sets,
  village biome tags): check each against the 1.21.1 originals, since vanilla changed some.
- Tags that name biomes or blocks 1.21.1 may lack use `"required": false` entries.
- The Sulfur Caves floor uses vanilla 26.2's `minecraft:overworld/sulfur_cave_bands` rule, which
  1.21.1 lacks. Replace with our own rule (phase 4 supplies the blocks).

### Phase 4: missing content through Vanilla Backport (half a day)

- New `world/Palette`: looks up each newer block by id once at startup and returns the fallback
  if it is absent. Every use of the seven blocks goes through it.

| Content | With Vanilla Backport | Without |
|---|---|---|
| Pale Garden biome | its biome id | Dark Forest |
| Pale oak log | its block | dark oak log |
| Leaf litter | its block | nothing placed |
| Bush | its block | short grass or azalea, by biome |
| Dry grass, short and tall | its block | dead bush, thinned |
| Firefly bush | its block | glow lichen or nothing |
| Sulfur Caves biome and floor | its biome and sulfur blocks | Dripstone Caves |

- Biomes: Grand Atlas owns the biome source, so it only needs the biome id to exist. Vanilla
  Backport's own placement must do nothing in our dimension; phase 0 confirms how it places them.
- Data files cannot name blocks that may be absent (the world would fail to load), so anything
  optional is placed from code, not JSON.

### Phase 5: mixins (half a day)

| Mixin | Work |
|---|---|
| `StructureMixin` | `Structure.generate` has a shorter signature in 1.21.1 (no structure holder, no sampler). Get the core from the `RandomState`, and find "is a village" through the registry tag. |
| `NoiseBasedChunkGeneratorMixin` | Re-check targets; this code changed little since 1.18. |
| `WaterFluidMixin`, `SpringFeatureMixin` | Re-check targets. |
| `IrisBlockIdsMixin` | Targets Iris's own code; redo against the 1.21.1 Iris build. Optional by design. |

Re-run the two tests that broke before: `/locate structure #minecraft:village` returns in seconds,
and no structure piece crosses a guarded set-piece core.

### Phase 6: client (half a day to a day)

- `AuroraRenderer`: 26.3's extract and submit events do not exist. Rewrite on the 1.21.1 level
  render stage event with the same emissive, depth-writing render type. The lake search and
  ribbon maths stay.
- River water rendering and tint.
- Shader check with Iris: aurora visible through water, river water shaded as water.

### Phase 7: verification (a day, plus your review)

- Same dev seed on both versions. The core is identical, so **terrain must match block for block**
  apart from the swapped content. A region diff between the two proves it.
- Censuses: floor, cave, surface, biome shares.
- Locate every set piece and asset type; chosen spawn; place names (still untested on 26.3 too).
- Chunk generation time against the 26.3 figures (255 ms and 355 ms per chunk).
- Run once without Vanilla Backport to prove the fallbacks load.
- Then a jar for you, in a 1.21.1 profile.

### Phase 8: into the Create Vast pack (half a day)

- Remove what Grand Atlas replaces (Tectonic, Terralith, Pandora Wilds as a separate mod).
- Check the pack's other worldgen mods and Lithostitched pins against a dimension Grand Atlas owns.
- Add Vanilla Backport to the lock file and mrpack by its official download link.

## Risks

| Risk | Effect | Answer |
|---|---|---|
| Vanilla Backport lacks some of the 1.21.5 plants | Dry country and cave floors lose detail | Fallbacks, or add `Ported` |
| Vanilla Backport changes its block ids in a later version | Blocks silently fall back | `Palette` logs every fallback at startup |
| Its biome placement interferes with our biome source | Wrong biomes or a crash | Found in phase 0, before any code |
| Lumina, Iris or Distant Horizons behave differently on 1.21.1 | Aurora or water look wrong | Phase 6 check; the renderer is small |
| Two versions drift apart | Features missing on one | Shared core; port `world/` changes in the same session |
| Pack tree assets name a block 1.21.1 lacks | Holes in trees | Importer check against the 1.21.1 block list |
