# M6 Fantasy: first slice (0.13.0-test25, 2026-11-12)

The user, 2026-11-12: "from now on 1.21.1 is our main version. Go ahead and work on the fantasy biomes!" and then
"dont forget the Fantesy Features as well (macro meso micro)". Built and tested on 1.21.1 only; the 26.3 data
(`python tools/gen_data.py` without `--target`) has not been regenerated, so a 26.3 build will not load the new biomes yet.

## Landforms (province level, `core/landform`)

| Landform | Biomes | What it is |
|---|---|---|
| `misty_peaks` | `misty_peaks`, `cloud_forest` | A raised upland cut by steep gorges, with granite needles 80-320 tall standing out of it. The needles are sheer through the new `Shape.envelope`: the interpolated ground keeps the full height round a needle's foot and the exact height trims it per block. |
| `volcanic_wastes` | `volcanic_wastes`, `volcano` | Basalt plains with old flow lobes; stratovolcanoes 220-450 tall (one per 1,100-block cell, 80%) with gullied flanks, a crater lava lake and 3-5 lava channels down the flanks. Lava is placed by `world/FantasyDetail`. |
| `silverwood` | `silverwood` | Soft hills and folds under colossal birch-barked trees whose oak leaves the biome tints gold. A `SILVER` layer in `world/AtlasAssets` (150-block cells) plants god trees thickly, re-skinned birch + oak leaves; the biome's own trees are birch-log fancy oaks and 2x2 giants. |
| `glimmer_vale` | `glimmer_vale`, `glimmer_river` | A broad winding valley between steep ridges; flower forest with azaleas and moss; its rivers are turquoise with glowing sea pickles. |

Wonder pairs: silverwood + glimmer vale, misty peaks + megarange, volcanic wastes + megarange.
Shares in a 192 km square: misty peaks 1.6%, volcanic wastes 2.5%, silverwood 1.2%, glimmer vale 1.2%.

## Macro: the Sky Isles (`core/SkyIsles`, `world/SkyIsleDensity`)

A great isle (radius 110-300) over land, top at 330-820 and at least 90 over the ground, with 4-10 smaller isles
round it at other heights. Solid over air, so it is its own density term, `grandatlas:sky_isles`, taken as the
max with the final density *outside* every cut (no river, cave or slide holes it). Biome `sky_garden` from under
the rock to 40 over the top. `world/FantasyDetail` adds rim springs that pour off as falls (water source + fluid
tick) and glow berries / hanging roots underneath. About 30% of 2,400-block cells. `/grandatlas locate sky_isles`.

## Meso sites and micro features (`core/micro`, built in `world/FantasyBuilds`)

| Site | Micro | Where |
|---|---|---|
| `crystal_garden` | `crystal_spire`: leaning aurora-crystal spires, shards at the foot | glimmer vale, misty peaks; rare in ordinary country |
| `standing_stones` | `monolith`: a ring of rune-cut stones, one sometimes fallen, an altar with lit candles | silverwood; open ordinary country |
| `floating_rocks` | `float_rock`: floating boulders with turf, glow berries and roots hanging | the enchanted landforms; rare elsewhere |
| `bone_field` | `rib_cage`: giant rib cages lying on their backs, skull at the head | volcanic wastes, dry country |
| `petrified_forest` | `petrified_trunk`: tuff/dripstone trunks round calcite, standing or fallen | volcanic wastes, dry country |

Cinder fields also appear in the volcanic wastes. Place names: each fantasy province, sky isle flock and new site is named (`world/PlaceNames`).

## Also fixed this round

- **Aquifer leaks into mega-caverns** (the user's screenshot at -5909 -136 -22711): `mixin/AquiferMixin` now
  places no aquifer fluid in the height band (±24) of any mega-cavern within 32 blocks of the chunk. Only newly
  generated chunks change.
- Crater lava fills the whole bowl under the rim's lowest point (filling only the middle left a lava wall).
- Biome plant lists must merge into one order: our own features go first, nothing vanilla is inserted out of place.

## Tour (world seed 20261112, core seed 2218211791946337851, spawn -96 75 384)

See the session summary; the Create Atlas server (`G:\CreateAtlas`) was switched to this world (`level-name=fantasy-tour`);
the old world is untouched in `world/`, the previous settings in `server.properties.before_tour`.

## Not done yet

Pandora fold-in as islands-in-the-noise beyond the sky isles, giant flowers, lava rivers that flow, 26.3 data, more
fantasy sites (dark/corrupted set), in-client look at everything (only checked by block probes and RCON).
