# Roadmap

## Release build

`gradlew :mc1211:build -Prelease -Pmod_version=<version>` builds the release jar. It differs from a test
build in one way: **Domus Mortus is left out** (see 1.2). Test builds (no `-Prelease`) keep it so it can be
built out and playtested.

The switch is in `mc1211/build.gradle` (processResources). It drops the dimension, its dimension type and
noise settings, the 15 Domus biomes, the `mortus_*` features and `domus/layout.bin`. The `mortus/` code
stays in the jar, unused, and `/grandatlas domus` replies "isn't in this build (it comes in 1.2)". The jar
contents were checked on 2026-11-23. On 2026-11-24, for 1.0.0, a fresh world was booted on the release data:
no errors, no Domus Mortus, and the biome mods and Vanilla Backport stand-ins worked.

## 1.2: Domus Mortus

The Greek underworld dimension (docs/UNDERWORLD_DESIGN.md), following the user's painted layout
(tools/domus/bake_layout.py, mortus/MortusMap). Polish pass done in test56 (2026-11-23): regions, trees,
ground variety, and the spawn cave with its river out to the Styx.

Still to do for 1.2:
- the user's structures (Hades' palace on the platform, the Gate, and the rest);
- music (undecided in the design doc);
- the coin and ferryman death mechanic from the design;
- an in-game review of the polish pass.

## Post-release

- **Pandora fold-in** (the user, 2026-11-23). Bring the Pandora Wilds mod (the separate `pandora-wilds` project, 0.2.0,
  1.21.1) into Grand Atlas. It has its five biomes (pandora, basin, highlands, hollows, spires), floating mountains,
  giant trees, stone needles, glow pools, the glowing blocks and plants (86 textures), the Ikran flying mount and the
  wasteland facility. The plan from M6: its floating islands become Grand Atlas terrain (beyond today's Sky Isles), the
  rest comes over as content. None of it is in Grand Atlas yet. The source folder is not under git, so back it up
  before starting.

## Not yet assigned to a version

- **M8 integration: structures** (2026-11-23). `gradlew :mc1211:runStructureAudit` boots a server with the
  CreateAtlas pack's mods (mc1211/run-structures, not committed) and writes `grandatlas/structure_audit.txt`: for each of
  the 247 structures, its candidates in 25 windows over 12 km, how many land in its biomes and how many are built. It is
  placement-only and takes about a minute. (A first version that used locate ran for over an hour.)
  - **Ground check** (world/StructureGround, StructureMixin): a surface structure is no longer built where the ground varies
    more than 14 blocks over 33 (26 if it shapes the ground round itself), in a river or lake, or against a cliff. It turns
    away 25% of surface starts, mostly on highland slopes. Sea, shore, buried and underground structures aren't judged, nor
    the `grandatlas:any_ground` tag (dragon roosts and caves, the mountain ruined portal, the dread citadel, the polar bear den).
  - **Biome tags:** our river, beach, desert, swamp, forest, badlands and mountain biomes join the matching `minecraft:is_*` and `c:is_*` tags.
  - **Climate rebalance** (the user: "re balance the climate"). Biomes.HOT moved from 0.55 to 0.4, desert takes
    humidity under 0, swamps reach up to 112 blocks, snowy plains are wider, and the plains band is wider. Measured
    with `gradlew biomeStats` over 48 km, as % of land:

    | Biome | Before | After |
    |---|---|---|
    | Desert | 1.75 | 4.4 |
    | Badlands, all three | 3.3 | 5.3 |
    | Swamp | 1.3 | 2.6 |
    | Snowy plains | 2.2 | 3.2 |
    | Plains | 3.8 | 4.7 |
    | Dark forest | 8.3 | 6.9 |
    | Forest | 6.9 | 5.0 |

    Forest and taiga still lead. The audit samples 25 windows over 48 km, because climate zones run tens of km.
  - **Biome mods (TerraBlender)**, built 2026-11-23 for the user's new Create pack, "Shark" (temporary name), which has
    BOP 21.1.0.14 and BWG 2.6.3. The code is world/ModdedBiomes and AtlasBiomeSource, read by reflection, so there is no
    dependency on TerraBlender.
    - How it works: as the server starts, every TerraBlender overworld region is read. Each entry is traced to the vanilla
      biome holding that climate spot (its host), and patches about 350 blocks across of that host take the modded biome.
      The odds follow the regions' weights. Only vanilla hosts take modded biomes; ours (salt flats, Glimmer Vale, wonders)
      keep theirs.
    - TerraBlender skips our biome source, so we turn on its surface rules ourselves; BOP and BWG ground blocks show.
    - The pack's own trees stand back in modded patches, since those biomes bring their own.
    - Config in `config/grandatlas-common.toml`: `biomeMods.weight`, default 0.6. At that weight about 34% of the land is
      modded; 1 matches a plain TerraBlender world, where BOP and BWG take over half. `biomeMods.deny` leaves out mods or biomes.
    - Checked by `gradlew :mc1211:runBiomeModsCheck` (mc1211/run-biomes, with BOP, BWG and their dependencies copied from
      the Shark profile; not committed): 46 hosts and 108 modded biomes on the land. One chunk of each was generated in
      full with no failures.
    - Not seen in game.
  - **Still open:**
    - 2 structures can't generate: Idas's fisherman's lodge (lists `minecraft:river` directly) and the dappled-forest camp (a biome we don't have).
    - 8 rare structures (few candidates) had none in their biomes.
    - Existing worlds get biome seams at old chunk borders.
    - Not seen in game.
- **Rivers and rocks, test61** (2026-11-23, from the user's playtest): streams no longer cross without merging
  (crossing diagonal flows join in their square), faint routing troughs gather parallel streams on flat ground,
  small streams wander gently instead of zigzagging, and every river runs smoothly through its nodes. Rock
  formations: no more stacked-grass walls under them (soil, then their own rock), the fill under an overhang
  reaches the ground, and formations skip ground too steep for them. Elevated mesas are warm, dry country only,
  arid on top. The containment check shows 2 open water faces (0 before); not yet chased.
- **M2 expansion** (2026-11-23, test58): ten new macro kinds, six grounded (great crater, monolith, fault block,
  shattered plateau, great sinkhole, titan horn) and four fantasy (Spire Crown, Coiled Peak, Sundered Land, Sky
  Pillar), see DESIGN.md §6.4. test59 adds composites: 35% of hosts carry a guest macro on their ground. Checked by the self-check and previews, and screenshotted
  in the dev client; not yet seen in game by the user. It moves macros in every existing world, so worlds made before
  test58 will meet chunk-border cliffs where new chunks generate near a macro.
- **Structures** (docs/STRUCTURES_TO_BUILD.md): 12 builds waiting on the user, cairns first.
- **Open surface items** (docs/QUALITY_REVIEW_2026-11-09.md): `shore()` audit, stale PLAYTEST tour stops, and
  densities to review in game. Done in test57 (2026-11-23): ledge plants on bare rock and dressed stony shores
  (world/RockFaceLife), not yet seen in game by the user. The straight landform edge at 1100 2200 no longer
  shows in the current terrain.
- **Open underground items:** square aquifer pools beside cavern lakes, server-synced aurora, Hanging
  Mountains rebuild or delete, the Great Geode's floor, dungeon and geode density.
- **Cave re-check:** the playtest checklist (2026-11-19) had 9 misses: abyss particles and sounds, mountain
  andesite, chalk caves, cenote side passages, green cave mouths, and the spiral galleries (3 items). test52
  fixed them, mostly by adding `/grandatlas locate` targets, but none has been re-checked in game.
- **Mobs** (Potted Monster, Crystal Eel): work, uncommitted. Crystal Eels spawn in the Aurora Vault only (2026-11-23).

## Dropped

- **The Nether** (2026-11-23): every Nether plan is off. docs/NETHER_PLAN.md and docs/NETHER_RESEARCH.md stay for reference.
