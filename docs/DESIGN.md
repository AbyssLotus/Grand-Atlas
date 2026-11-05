# Grand Atlas — Design

A NeoForge terrain mod for Minecraft 26.3 that handles both terrain and biomes in one place. It builds very large landforms, including real-world wonders and fantasy landscapes, and places other biome mods' biomes onto them. Terrain comes first. There are no mobs and no structures.

Status: **2026-10-29. M0 (foundation), cave damping (§11), M1 (landform framework with four wonders) and M2 (nested macro landforms: Great Plateau, massif, basin, elevated mesa) are built and verified on a 26.3 dev server.** Nothing below is final until we've seen it in game.

---

## 0. The goal

The user, 2026-11-03: "this terrain generation, while based in reality, has the goal of just looking EPIC, like truly a high fantasy world. That means it will need to be able to naturally generate an almost hand-crafted world look."

Realism is the means, not the end. Real geology keeps the world believable (water runs downhill, cliffs have talus, rivers carve valleys), but every place is judged by whether it looks like a level artist composed it: a dramatic silhouette, a focal point, a reason to walk over and look. The pillars below serve that.

- **Composed, not just plausible.** Landforms arrive as set pieces with framing: a waterfall with its pool and amphitheatre, a tower peak rising out of a green plain, a lake under a cliff. Plain filler between them is allowed, and needed, as contrast.
- **Exaggerate the good parts.** Taller falls, sheerer cliffs, deeper gorges, more overhangs than reality would give, where it makes the scene.
- **Hand-made detail at every scale.** Nothing uniform (smooth noise on every outline, profile and material); the close-up should hold up as well as the vista.
- **Variety by region.** Each region has its own character (tame here, aggressive there), so travel keeps surprising.

## 1. Pillars

1. **Scale.** Features are oversized on purpose. Mountains reach 900+, canyons are 300 deep, karst towers are 250 tall, dunes are 150 tall, and fjord walls are 600 high. A landform should feel like a place you travel through, not a decoration.
2. **Blend, but juxtapose.** No seams, no chunk walls, no biome stripes. You should still stand on a peak and see a pillar forest, a salt flat and a glacier all at once, and each one should look like it belongs.
3. **Readable at every scale.** Now and then, a macro landform such as a Great Plateau, a floating mini-continent or a massif lays down a foundation that whole regions of ordinary terrain grow on (§6). A player can be in a forest, in a valley, on a plateau, in a mountain region, all at once.
4. **Real geology underneath, fantasy on top.** Real landforms follow real rules: water runs downhill, canyons are cut by rivers, fjords are flooded glacial valleys, playas sit in closed basins. Fantasy landforms keep that same ground logic, then break one rule each, such as floating, glowing or burning.
5. **Terrain shapes, biomes dress.** A landform decides the shape. Biomes, our own or another mod's, decide what grows on it and what colour it is.
6. **Fast enough to live with.** A world three times vanilla height still has to pregenerate and run Distant Horizons LODs at a pace people will accept.

## 2. Decisions so far

| Topic | Decision |
|---|---|
| Platform | **Minecraft 26.3, NeoForge 26.3.0.x** (newest release as of 2026-10-29), Java 25, Gradle 9.2.1, ModDevGradle 2.0.148. Minecraft is unobfuscated from 26.1 on. |
| Create Vast pack | **Not a target any more.** That pack is 1.21.1 and Create has no 26.x build, so Grand Atlas can't load in it. Grand Atlas is standalone, for 26.3 worlds and packs. |
| Name / id | Grand Atlas, `grandatlas`, package `com.seaweed.grandatlas`. |
| World height | **min_y -128, max_y 1024** (1152 tall, 3× vanilla). Sea level stays 63. |
| Pandora Wilds | **Folded in**: terrain, plants, glow blocks, giant trees, vines and bridges. **Dropped**: the ikran and its nests (mobs), and the wasteland clearing, mining facility and open-pit mine (structures). The Pandora Wilds jar retires. |
| Tectonic | Replaced. Declared `incompatible` in `neoforge.mods.toml`. |
| Terralith | **Replaced** (along with Terralith ReStoned). The Terralith biomes you like get rebuilt as our own originals; see §13. Declared `incompatible`. |
| BOP, BWG | Integrated through TerraBlender (§12). On 26.3 (checked 2026-10-29), **TerraBlender and BOP have builds; BWG doesn't yet** (its newest NeoForge build is 1.21.11). The integration is generic, so BWG works as soon as it ports. |

## 3. World frame

```
1024 ─ build limit
 980 ─ highest peaks cap (≈40 blocks of build room above the tallest summit)
 700 ─ ~ floating islands top out, high megarange shoulders
 600 ─ ~ snow line for temperate biomes (vanilla's altitude temperature drop gives this for free)
 450 ─ high plateaus (canyonlands rim, karst plateau)
 192 ─ vanilla cloud layer (peaks punch through it, giving a cloud sea; one data value moves it, see §14)
 120 ─ lowland / hill boundary
  63 ─ sea level
   0 ─ deepslate begins
 -60 ─ abyssal plains
-110 ─ trench floors, lava aquifers
-128 ─ bedrock
```

Ocean depth matters as much as mountain height. With 190 blocks below sea level, real shelves, slopes, abyssal plains and trenches all fit.

## 4. Architecture

```
                 ┌──────────────── grandatlas.core (pure Java, no Minecraft classes) ────────────────┐
 world seed ───▶ │ Noise kit → Continents → Climate → Province mosaic → Landforms → Blender → Hydrology│
                 │                          (per-column result: ColumnSample, cached per thread)     │
                 └──────────────────────────────────────┬────────────────────────────────────────────┘
                                                        │
          ┌─────────────────────────────┬───────────────┴──────────────┬─────────────────────────────┐
          ▼                             ▼                              ▼                             ▼
 DensityFunction types          BiomeSource                   Surface rule types            Fluid picker
 grandatlas:terrain             grandatlas:atlas              grandatlas:strata …           (lakes/rivers/lava
 grandatlas:climate (router)    (reads the same core)         (registered conditions/rules) above sea level)
          │                             │                              │
          ▼                             ▼                              ▼
 data/minecraft/worldgen/noise_settings/overworld.json   data/minecraft/dimension/overworld.json   dimension_type/overworld.json
```

**Why a pure-Java core.** The terrain maths lives in `grandatlas.core`, which has no Minecraft imports. That gives us:
- **A previewer**: `gradlew preview` renders top-down maps, biome maps and cross-sections as PNGs in seconds, without booting a server. This is the fastest way to iterate on 10,000-block-scale features.
- **Unit tests** for determinism and continuity. One test confirms that a column gives the same answer whichever chunk asks for it; another confirms there are no jumps across any province edge.
- **A thin Minecraft layer** that only adapts the core to `DensityFunction`, `BiomeSource`, surface rules and the fluid picker.

**26.3 density functions.** Minecraft 26.3 compiles density functions into samplers that fill whole volumes. A function declares its value range and which axes it depends on, and the compiler slices and caches accordingly. Ours (`grandatlas:terrain`, `grandatlas:climate`) look up each column once and fill the Y run. Climate channels declare X and Z only, so the compiler treats them as 2D.

**Seeding.** The compile context's `createRandom(id)` is seeded from the world seed. The core seed is `createRandom("grandatlas:core").nextLong()`, and there's one core per seed.

**One core per world, shared by terrain and biomes.** The vanilla overworld is a set of named functions (`minecraft:overworld/final_density`, `/temperature` and so on). We ship our own `grandatlas:overworld/*` set and point the overworld noise settings at it: router, aquifers, spawn target and debug readouts. Vanilla's caves and surface (material) rules stay untouched. The biome source receives the `Climate.Sampler`, unwraps the compiled climate sampler (through the compiler's slice and cache wrappers) and finds the core there. The climate slots return real values, so vanilla's spawn finder still puts players on land. `preliminary_surface_level` returns the core's exact surface height instead of vanilla's top-down density scan, which would otherwise probe all the way down from y 1024.

**Thread safety.** C2ME and Distant Horizons generate chunks on many threads. The core is immutable after seeding. Column caches are per-thread (`ThreadLocal`, small open-addressed tables keyed by column), and river and lake networks are built lazily in a concurrent cache keyed by region.

**Data-driven landforms (target).** Landform *types* are Java classes with codecs, for example `grandatlas:pillar_forest`. Landform *instances*, such as "Zhangjiajie", "Wulingyuan Mist" or "Guilin Towers", are JSON in a custom datapack registry `grandatlas:landform`, holding rarity, climate window, scale, heights and biome slots. Packs can then tune them or add variants without code. M1 shipped code-only. The registry waits until after M2, because macros add frame parameters (budget, family, offsets) that landform JSON must express, and freezing a format before then would mean migrating it.

## 5. The landform system

Each column (x, z) goes through these layers in order. Every layer is a continuous function of position, which is why there are no seams. Now and then a macro landform (§6) slots in above the province mosaic and gives it a raised or sunken foundation to build on.

### 5.1 Continents (scale 4k to 16k blocks)
- Continentalness comes from domain-warped low-frequency noise. It sets land vs. ocean and coast vs. interior, and on the ocean side it sets the shelf, slope and abyss.
- **Tectonic belts** are long warped ridge lines. Some run along coasts (Andes-like, with fjords on the cold side) and some cross interiors (Himalaya-like). Mountain landforms are strongly biased toward belts, so ranges form chains instead of scattered blobs.
- **Basins** are low-frequency depressions between belts. That's where playas, dune seas and big lakes collect.

### 5.2 Climate
- Temperature and humidity are smooth fields on a 3k to 6k scale.
- **Rain shadow**: humidity drops downwind of high belts (a fixed prevailing wind, sampled a few times upwind at coarse resolution). Deserts and playas then turn up behind mountain ranges, which is where you'd expect them.
- Altitude cooling comes from vanilla's own biome temperature drop with height, so snow lines emerge without special code.

### 5.3 Province mosaic (cells 800 to 2,500 blocks)
- Jittered-grid Voronoi cells with domain warping, so edges wander like real terrain boundaries. Each cell is a **province** and holds one landform.
- Each landform is picked from a weighted table that filters on climate, continental context (coast, interior, belt, basin, ocean) and rarity.
- **Adjacency rules** (see §7): no identical neighbours, contrasting neighbours preferred, and "wonder pairs" occasionally forced.
- Cells come in a few size classes. Megaranges and dune seas are huge; Spotted Lake or a crystal-cave entrance valley is small.

**As built in M1 (`ProvinceMosaic`):**
- **Cells:** 1,600-block cells under a three-scale warp (650 blocks over 2.6 km, 180 over 600, 36 over 140). The middle scale gives lobes; the last roughens the edges, which keeps escarpments from running in smooth arcs.
- **Picking:** a greedy pass in priority order. Each cell weights every landform by the context at its centre, then by its affinity with the *final* picks of its higher-priority neighbours. Recursion only ever climbs in priority, and the province cache memoises it. Two weaker schemes failed the self-check first: judging neighbours by their initial picks left 21% identical neighbours, and three fixed rounds of correction still left 10%.
- **Same-landform affinity** replaces the blanket "no identical neighbours" rule:
  - Wonders: 0, so they never repeat next to themselves (0% measured).
  - Ordinary country: 0.4, so lowlands, hills and highlands merge into regions of one to several cells. This also gives the size variety that a single cell size lacks.
  - Megaranges and dune seas: 2.2, so they attract into chains along belts and into ergs.
- **Wonder pairs** (canyon/dunes, playa/megarange, canyon/playa) get ×2.2. Differing elevation classes get ×1.3.

### 5.4 Landforms
A landform is a Java type that provides:
- `shape(x, z, frame) → ColumnShape`: surface **relief over the frame's datum** (§6.2), plus the parameters the 3D stage needs (overhang strength, pillar cell, island layer and so on). Heights quoted in the catalogue (§10) assume the world-level datum.
- Optional `density(x, y, z)` for genuinely 3D forms: pillars with undercuts, arches, floating islands, sea notches.
- `zones(x, z)`: named sub-areas such as floor, slope, rim, peak, pool and terrace. These drive biome slots and surface rules.
- Biome slots: zone → biome list, our own biomes plus integrated ones (§12).
- Hydrology hooks: where it wants lakes, springs, waterfalls or lava channels (§8).

### 5.5 Detail
- Eroded-looking mountains come from gradient-damped ridged multifractal noise. The slope of the lower octaves suppresses detail in the higher ones, which produces drainage-like ridges and valleys without running an erosion simulation.
- **Strata** use height quantization with "hardness" bands: hard layers form cliffs and soft layers form slopes. Canyons, mesas and Rainbow Mountains all use this.
- Talus aprons at cliff bases come from slope-aware smoothing.

### 5.6 3D stage
Final density is the column shape (a smooth gradient around the surface height), combined with 3D landform density and then **vanilla caves** (cheese, spaghetti, noodle), rescaled to the deeper world. Above `max(column top)` the function returns air right away, so the 600+ empty blocks over a lowland column cost one cache lookup per cell instead of a full noise evaluation.

## 6. Scales and nesting: macro landforms

Most of the world runs on the system in §5: continents, then provinces, then landforms. Now and then, a much larger landform lays down a **new foundation**, and ordinary terrain then generates on top of it and inside it. The model is Breath of the Wild's Great Plateau. It's one enormous landform, but it isn't one uniform biome: it carries its own forests, fields, a snowy mountain, ponds and marshy ground.

So the generator doesn't always ask *"what terrain type is this location?"*. Sometimes it asks *"what large landform is this location part of, and what terrain exists inside or on top of it?"*

### 6.1 The scale stack

| Scale | Size | What it decides | Example |
|---|---|---|---|
| **World** | 10–50 km | Land and sea, tectonic belts, the climate fields | A continent |
| **Macro landform** (rare) | 1.5–8 km | A new foundation: datum, footprint, edge, underside, which children may grow on it | The Great Plateau, a floating mini-continent |
| **Regional terrain** (provinces) | 0.5–2.5 km | The landform mosaic of §5.3 | Rolling highlands, a canyon plateau |
| **Meso sites** | 100–800 blocks | A site inside a province, with its own zone tag and micro-feature weights | An alpine valley, an oasis hollow, a lake district, a karst field |
| **Micro features** | 8–100 blocks | One local feature, weighted by context, never owned by a biome | A tarn, a sinkhole, an arch, a boulder field, a waterfall |
| **Biomes** | — | What grows, colours, fog | Forest, meadow, marsh |
| **Detail** | 1–8 blocks | Dressing brought by the micro feature and the biome | Boulders, flowers, fallen logs, moss |

Meso, micro and detail features are planned in [MICRO_FEATURES.md](MICRO_FEATURES.md): how each kind would be built, and a pool of feature ideas classified by that. The pool, the meso level and weighted compatibility come from a ChatGPT brainstorm the user shared as a very loose outline, so they are suggestions, not decisions.

Each column answers with a **stack**, not a single type. For example: `Great Plateau (inside, 900 blocks from the rim) → Rolling Highlands → valley floor → forest`. A player can read every level at once: standing in a forest, in a valley, on a plateau, in a mountain region.

### 6.2 Frames, not overrides

Layers influence each other without replacing each other. Each layer hands the next one a **frame** instead of a finished height:

- **Datum**: the base elevation the children build on. At world level it's the continental base elevation. Inside a Great Plateau's footprint, it's raised by 250 to 400 blocks.
- **Relief budget**: how far children may rise above the datum and cut below it. This keeps a plateau a plateau: children can't dig it back down to sea level, except in gorges at the edge. It also lets a massif's children be big.
- **Family filter**: which landforms the children may pick. Alpine for a massif, canyonlands for an elevated mesa, a temperate mix for a Great Plateau, lowland and wetland for a basin.
- **Climate modifier**: an offset on the world climate fields inside the footprint. A basin runs drier and warmer; a floating island runs windier and colder. Altitude cooling still applies on top. That's what puts a snowy peak on a plateau of meadows, the way BotW has Mount Hylia.
- **Water datum**: the local base level for rivers and lakes (§8):
  - A plateau drains to its rim and pours over it as waterfalls.
  - A basin has no outlet, so it collects a terminal lake or a playa.
  - A floating island's rivers spill off its edges.
- **Tags**: names pushed onto the column's tag stack, such as `macro:great_plateau` and `macro_zone:rim`. Biomes, surface rules and other mods' features can test them. Grand Atlas makes no structures, but a structure mod or datapack can use these tags to put ruins on a plateau.

**One change to §5 follows from this:** landforms describe **relief over a datum**, not absolute height. A canyon plateau's "rim at 350 to 450" becomes "rim at datum + 200 to 300", with the world-level datum coming from the continents. Put the same landform on a raised macro and it simply sits higher. Nothing in the landform needs to know.

**Sub-mosaics.** The children of a macro run in their own province mosaic: separately seeded cells, scaled to the footprint, so a 3 km plateau gets 3 to 8 provinces instead of one. The family filter and the adjacency rules of §7 apply inside it.

### 6.3 Macro edges

A macro's boundary is its own transition style, alongside the ones in §7:

| Edge | Used by | Look |
|---|---|---|
| **Cliff wall** | Great Plateau, tepui, elevated mesa | A 100–400 block cliff band, noisy and buttressed, with talus at the foot. Two to four ramps or gorges give ways up. |
| **Rim ring** | Basin, caldera | A ring of ridges or mountains around a lower floor, with a few passes |
| **Dome flank** | Massif | Broad foothills rising inward, no single edge |
| **Underside** | Floating mini-continent | A rock root tapering downward under an overhanging lip, with hanging roots and stalactites, thinning toward the edges |

Outside the edge, terrain comes from the outer mosaic; inside, from the sub-mosaic. They only mix within the edge band itself, so the macro reads as one landmark and not a smear.

### 6.4 Macro catalogue

| Macro | Footprint | Foundation | Typical children | Milestone |
|---|---|---|---|---|
| **Great Plateau** | 2–6 km, warped blob | Datum +250–400; cliff wall with 2–4 ramps or gorges | Rolling hills, forest, meadow, a snowy summit from altitude cooling, ponds, marsh, small mesas | M2 |
| **Mountain massif** | 4–8 km | Dome datum, rising +200–500 toward the centre | Sub-ranges, peaks, cirques, tarns, valleys, forests | M2 |
| **Enclosed basin** | 3–8 km | Rim ring 200–500 above a sunken floor | A terminal lake or playa, floodplain, wetlands, dunes; a "lost world" valley if humid | M2 |
| **Elevated mesa** | 2–5 km | Datum +200–350 in strata, stepped cliff edge | Sub-mesas, buttes, canyons with a river, arid to semi-arid transitions | M2 |
| **Floating mini-continent** | 1–3 km | A 3D slab at y 450–700, 60–200 thick at the centre, with an underside root | Mountains, valleys, lakes, rivers that fall off the edge as waterfalls, several biomes. Pandora-style islands can orbit it. | M6 |
| **Tepui** | 0.8–2 km | Sheer 300–500 block cliffs, flat top | Misty forest, sinkholes, waterfalls off the edge (Angel Falls) | M5 |
| **Caldera** | 2–5 km | Rim ring around a deep floor with a crater lake | A central island cone, hot springs, volcanic children | M5 |
| **Rift valley** | 1–2 km wide, 8–20 km long | A linear trench with scarps on both sides | A chain of lakes, volcanoes, savanna | M5 |

**Rarity.**
- Macros sit on a coarse grid of 6 km cells. Each cell hosts one with a probability of about 10 to 20% (weighted by type), jittered, with a minimum spacing between macros.
- The footprint must fit, which mostly means land. Floating islands and sea tepuis are the exceptions.
- Some types have climate filters: an elevated mesa wants arid ground, a tepui wants humid tropics.
- The result: a typical player meets one every several kilometres. It should be significant, a landmark you can see from far off (Distant Horizons helps), never a repeating pattern. Everything else stays on the normal continental and regional system.

**As built (`MacroField`, retuned in playtest round 2):**
- **Grid:** 5 km cells. Each mostly-land cell (continentalness at least 0.25) offers a candidate 85% of the time. The first build used 8 km cells at half occupancy, about one macro per 400 km²; in a tour only the plateau got noticed. The user's model is Breath of the Wild: macros 2–6 km across, with gaps about their own size between them.
- **No overlaps, by shrinking:** a candidate shrinks until its reach clears every higher-priority neighbour candidate's full reach, with twice the footprint warp as margin. It's dropped only if that leaves it under 650 blocks of radius. Every column still sees at most one macro, and the self-check confirms no two reaches overlap.
- **Measured density:** 229 macros in a 240 km square. Mean diameter is 3.4 km, and the mean distance to the nearest neighbour is about 6 km. About 15% of land is inside a macro.
- **Built sizes:**

  | Macro | Across | Lift | Relief scale | Family |
  |---|---|---|---|---|
  | Great Plateau | 2.4–5.6 km | 260–380 | 0.75 | Hills, uplands and fields with a ridge or two (megarange 0.3), plus its landmarks (below) |
  | Massif | 3–4.4 km | dome to 150–300 | 0.9 | Mostly megarange, with uplands and hills |
  | Basin | 2.8–4.6 km | floor sunk 30–60 (never below sea + 4), rim 220–440 | 0.8 | Dry basins: playa, dunes, lowlands. Humid ones: a lowland and hill "lost world" |
  | Elevated mesa | 2–4.2 km | 200–330 | 0.7 | Canyonlands with uplands, dunes, playas |

**Landmarks (0.0.3).** A macro can place features that belong to one site rather than to a landform (`Macro.landmarks`). They are applied to its own terrain before its edge combines it with the outside. In playtest round 2 a plateau's top was "nothing other than plains and forest", with no rivers flowing off it. So every Great Plateau now gets three landmarks, planned once per site and cached:
- **A summit** (its Mount Hylia) at half the radius from the centre. It is 190–340 blocks over the tableland's datum, ridged, and rises *from the datum* rather than stacking on the terrain beneath; stacked on a range, the first one ran into the height cap.
- **A lake** 100–280 blocks across on the far side of the centre, set just under the lowest ground around it.
- **A river** from the lake to the rim, leaving clear of the ramps and away from the summit. Its course is traced over the plateau's own terrain every 16 blocks, with a water level that only ever falls (the lowest ground across the course, minus 3). So it never sits above its banks, and it cuts a gorge through any hill in its way (valley walls at 45°, levees across dips). At the top of the cliff the water meets open air and pours off as a waterfall.

The water itself is placed by `grandatlas:perched_water` (§8).

**Plateau biomes.** The climate table reads absolute height, so a 300-block lift made most plateau tops alpine from edge to edge (snowy slopes and frozen peaks at t ≈ -0.4). A plateau now discounts 60% of its lift when picking biomes (`Macro.altitudeDiscount`). Its top grows its climate's lowland forests and fields, and only the summit reaches the snow.

**Nesting depth.** A macro may host one smaller macro, such as a caldera on a massif or a tepui in a basin. That's at most two macro levels, then regional and local terrain as usual. Bounding the recursion keeps the cost per column predictable.

### 6.5 How it runs

- **Pipeline.** The column pipeline becomes:
  1. Continents, then climate.
  2. **Macro query**: nearby macro seeds from the 3×3 coarse cells around the column, the warped distance to each footprint, the edge weight, and the frame.
  3. The province mosaic: the outer one, or the macro's sub-mosaic.
  4. Landform relief over the frame's datum.
  5. Local landforms.
  6. Combine everything through the edge profile.
- **Cost.** The macro query is a handful of grid lookups and one warped-distance evaluation per column, cached with the column.
- **Floating macros** add a 3D density term only inside their footprint and altitude band. The ground below carries on normally, with an `under_island` zone tag.
- **Tooling.** `/grandatlas where` prints the whole stack. `preview` outlines macros and draws their edge bands. Tests check continuity across macro edges and that children never break their relief budget.

**As built in M2:**
- **Shared sub-mosaics:** there's one per kind, not per site. Sites are at least 4.8 km apart, so they draw from unrelated parts of it, and its warp scales with its smaller cells.
- **Context:** sub-provinces read the world climate through `Point.inland()`, which is always land, well inland, and carries the macro's climate offsets. Family weights come from that context. The Basin tells humid from dry by it.
- **Skipping the outer blend:** when a column is so far inside that the outside can't show (`covers`), the outer blend is skipped, so only the interior pays for one blend.
- **Peaks:** a raised datum plus a range could overshoot the limit, so the hard cap became a smooth curve. Above 820, heights approach 980 exponentially, so summits are never cut flat.
- **Verified in game** (dev seed, Great Plateau at -11322, 19409):
  - The wall is a single sheer cliff about 250 blocks high from the savanna foot (y 126) to the forest top (y 380 to 430).
  - Vanilla springs on the face make small waterfalls.
  - Jungle and sparse jungle grow on top and savanna below.
  - `where` reads `great_plateau → interior → lowlands → plain` on top and `great_plateau → foot → ...` below.
  - The self-check bisects 260+ steep steps across macro edges, and the worst leftover jump is 0.16 blocks.

## 7. Blending and juxtaposition

This is the core requirement, so it gets its own rules.

**As built in M1.** The styles below aren't looked up from a pair table. They come out of three continuous rules in `AtlasCore.blend`, and the column's transition label just reports which one dominated:
1. Weights fall off as (1 − a/T)² with a = d − d_nearest and T = 260 warped blocks.
2. **Taper**: a landform fades toward a *different* neighbour over its taper width, down to its taper floor. Megaranges fade to 12% over 400 blocks, hills to 40% over 350, dunes to 0 over 350. Same-landform neighbours never taper, so merged regions and chained ranges run on unbroken.
3. **Hardness-weighted sharpening**: the weights are raised to the power 1 + (relief spread / 30) × hardness. Hardness is the weighted mean leaning toward the harder side (canyon plateau 1, megarange and playa 0.3, everything else 0.15). A canyon plateau beside lowland ends in a cliff band a few dozen blocks wide (up to 28 blocks of rise per block, measured), while hills and lowlands blend over hundreds of blocks.

The self-check bisects every step steeper than 12 blocks per block down to 1/256 of a block, and fails if more than 1.5 blocks of jump remain. The worst leftover measured is 0.14, so the steep edges are cliffs, not seams.

**Seamless by construction.** Every column evaluates its 3 to 4 nearest provinces. Each gets a weight from a smooth function of how much farther it is than the nearest one (`d_i - d_min` over a transition width). Weights are continuous, so terrain is continuous: no seams, ever, at any chunk.

**Contrast by transition style.** Plain averaging of two landforms makes mush: a pillar forest averaged with a plateau gives half-height pillars over a 2 km strip. Instead, each pair of neighbours gets a **transition style**, picked from their elevation classes and character:

| Style | When | What you see |
|---|---|---|
| **Soft** | Similar elevation and texture (forest hills ↔ rolling meadows) | A wide, gentle lerp, 150 to 300 blocks. |
| **Escarpment** | High ↔ low (canyon plateau ↔ playa) | A narrow, noisy-edged cliff band with talus and fallen blocks. The high side ends; it doesn't fade. |
| **Valley seam** | Two raised landforms | A river valley runs along the boundary. Rivers really do follow faults, and it gives every border a reason to exist. |
| **Ridge seam** | Two low landforms in a mountain belt | A ridge or arête forms the boundary. |
| **Shoreline** | Land ↔ water landform | A beach, a cliff coast or a mangrove/marsh fringe, depending on climate. |
| **Fade-out** | A 3D feature landform ↔ anything (pillars, towers, islands) | Feature density thins toward the edge: pillars get sparser and shorter, and islands get smaller and lower. They don't shrink uniformly. |

**Bands stay narrow.** Transitions are 64 to 300 blocks across provinces of 800 to 2,500, so each place keeps its identity and the change reads as an edge.

**Adjacency.**
- No two neighbouring provinces share a landform.
- Neighbour picks are biased toward a different elevation class and texture: smooth ↔ spiky, wet ↔ dry, dark ↔ bright.
- **Wonder pairs** are curated dramatic neighbours that get a weight bonus:
  - Volcanic wastes ↔ glacier highlands (Iceland)
  - Canyon plateau ↔ dune sea
  - Rainbow Mountains ↔ playa
  - Karst towers ↔ rice terraces
  - Fjordland ↔ megarange
  - Pandora ↔ elven wood
  - Misty peaks ↔ enchanted valley
- Climate still constrains which landforms can appear at all, so pairs stay plausible. Fantasy landforms carry a weak climate filter and a rarity cap.

**Biome edges.** A column's biome comes from its dominant province, with the edge itself warped by noise so it never runs straight. Optional ecotone biomes (shrubland between desert and forest, for example) fill wide soft transitions. Vanilla's grass and foliage colour blending smooths the rest.

**Sight lines.** Tall landforms are tall partly so you can see the next province from this one. Distant Horizons LODs (already in the pack) are the intended way to view that.

## 8. Hydrology

The hardest system, so it ships in two stages.

**As built (0.0.4, redesigned in 0.0.6): one watershed network for all land** (`core/hydro/Drainage`; playtest round 3: "there was almost no water besides oceans... small rivers flow into big ones and big ones into oceans, like real watersheds"). It does Stage B's drainage graph first and serves Stage A's rivers to the sea from the same graph. 0.0.6 rebuilt how the rivers sit in the land after round 3b ("still bad flow, and now spikey terrain"), following how vanilla, TerraForged, Tectonic and Dynamic Waters do it: rivers only ever lower the ground, lowland rivers sit at sea level, highland streams follow the real valley floor, and lakes fill real basins.
- **Nodes** every 128 blocks, each at the lowest of five points in its cell, carrying the terrain height there (before rivers) and its rain: 0.1 in deserts up to 1 where it's wet, from humidity.
- **Flow:** each node drains to its lowest neighbour. Routing adds a pull toward the sea of 0.08 blocks per block of distance to the coast (`CoastDistance`: shortest-path distance on a 1 km grid, which has no pits). So water follows valleys locally and heads for the coast over kilometres.
- **Pits are breached.** A flood from each pit finds the lowest pass to ground lower than the pit within 15 nodes, and the route over it overrides the flow. Pits are processed from the lowest up and the first route through a node wins. So a breach always ends lower than it started, and nothing can loop. A pit with no way out is a closed basin and gets a lake.
- **Catchment** is all the rain upstream, summed by walking up the tree once per node (a big river's basin is tens of thousands of nodes, about a second the first time). A stream starts at 6 nodes' worth of rain. Half-width runs from 1.6 + 1.7·√(catchment/6), capped at 42; depth up to 8.
- **Lowland rivers (0.0.6)** run at sea level, as vanilla's and TerraForged's do. A node is lowland if nothing on its way to the sea stands more than 70 blocks above it. Its river's valley is lowered to sea level, the aquifer fills it, and placement swaps that water for river water so it runs.
- **Water level** at a node is the lowest of its own ground minus 2.5 and every level upstream. So it never rises downstream, and a river crossing a rise cuts a gorge. The level where rivers meet at a node is also capped by the lowest ground and banks on every route into it: a routed path can dip under the node's own ground, and a river held at the node's level stood over that plain (0.0.6). Along each stretch a "safe" level stays 2.5 under the ground on the course and on both banks, stepping down toward the next node.
- **Courses (0.0.6)** follow the valley floor: Dijkstra on an 8-block grid through an 80-block corridor between the nodes, each step costing more the higher it climbs over the corridor's lowest ground, then two rounds of corner cutting and a point every 6 blocks. (0.0.4's splines through the nodes ran along hillsides and needed banks built up to hold them.)
- **Confluences (0.0.6):** a tributary keeps its own width (only the main stem widens into the junction), and it is down to the junction's level where it first reaches the bigger river's channel and banks, or the junction's lake. Before, it dropped at the junction node, inside the main river, and its last pools stood alongside the main river as a 20-block wall of water.
- **Pools (0.0.5).** The water itself never lies on a slope. The safe level is stepped into flat pools that spill into each other. A pool runs on while the safe level stays within one drop of where it started, and sits at the lowest safe level inside it, so the ground at its lip holds it. Every pool is at least 6 blocks long. The drop grows with the slope ahead: 1 block on gentle rivers (riffles), 12× the gradient on steeper ones, at most 16. So a long descent is a cascade of falls with pools between, and only a true cliff makes one bigger fall. A lip has a sill, and before a fall of 6+ the river pinches to under half its width, so it pours as a chute. Below a fall is a plunge pool up to 1.6× the stream's width + 4 wider and 6 deeper. Steep reaches run up to a third narrower. Beside a fall, the lower pool's dry edges and banks keep the upper pool's level for as far downstream as they are from the water, so the cut is a notch and never opens under the upper pool (0.0.6).
- **Shapes only ever lower the ground (0.0.6):** to the bed, a flood plain 2 + 0.4·half-width wide just over the water, then a valley 10 + 2.5·half-width wide (at most 90) blending back into the land (TerraForged's way, narrower: our rivers are a few hundred blocks apart, not kilometres). 0.0.5 raised banks and levees and probed neighbours to hold water; that made the spikes of round 3b, and it's all gone.
- **Where bodies overlap (0.0.6)** each offers its own cut, and the lowest wins. The water at a column belongs to whichever body cut it to (within a block of) its final depth, so a river whose channel another's valley cut away doesn't hang its water over that valley. A lake fills whatever is cut inside its shore. And a column left dry inside a river's channel or flood plain, or a lake's shore, is never cut below that body's own shape by another's valley: the guard that keeps a river perched on a bigger valley's rim in its banks. Nothing goes above the land.
- **Lakes (0.0.6) fill real basins:** a priority flood over an 8-block grid, out to 144 blocks from the node, finds the lowest point of the rim, and the lake fills to 1.5 under it. Its shore is a smooth field over the flooded cells, so it follows the land instead of being a disc. A lake needs a hollow at least 3 deep: a dip in a plain sat level with the land and held the rivers through it up at the ground. Lakes form in closed basins and where a river (two streams' worth of flow) passes a hollow; lowland nodes have none.
- **The cut is exact to the block (0.0.6).** The world interpolates density on a 4×8×4 grid, which rounded the banks off a block under the water. Now the interpolated terrain is the ground before rivers (`uncut`), and the cut goes in per block with the fine caves (`AtlasCore.riverCut`, outside the interpolation).
- **No cave opens under water** (0.0.5): no 3D roughness near water, vanilla caves damped within 16 blocks under a bed and its banks, pits and fissures kept out, caverns 40 below. Vanilla's carvers can't be told: placement plugs any hole they cut beside or under water (below).
- **Measured.** `checkCore` sweeps 16 river spots on the dev seed: 32,579 water columns, 29 open faces (a dry neighbour's ground under the water), 160 drops of 2+ at pool lips, 20 elsewhere (limits: 2 per 1,000). In the dev world, the two worst areas of round 3b (a 72-block cascade to the sea, and a confluence with walls of water) have 0 river water blocks open to the air, against 279 + 162 and 97 before the exact cut and the plugs. On the dev seed (24 km around spawn), every sampled river reaches the sea, with no loops and no water running uphill (`checkCore` checks 256).
- **Tiles:** nodes, routes and catchments live in 32×32-node tiles (4 km) shared across threads. Routes read a 31-node margin, so tiles agree wherever they meet. The first visit to a new river basin computes its drainage, a second or two on one worldgen thread.

The plan below is kept for what's still to do: deltas and estuaries, canyons that host their rivers on purpose, a fluid picker for lava rivers, and lake districts.

**Stage A (M3): sea-level rivers.** Meandering channels from domain-warped distance fields carve valleys down to sea level. Width and depth grow with a flow class taken from distance inland and continental position. Through plateaus they become canyons; that is how the Grand Canyon landform gets its river. Deltas and estuaries form where they meet the coast. These are cheap and globally consistent.

**Stage B (M4): water above sea level.**
- A **drainage graph** per region (2 km regions, 32-block nodes, built from the smooth continental height with a one-region margin) gives highland rivers that flow downhill, merge, and end in the sea or a lake. Depressions fill to make **lakes**: Canadian Shield lake districts, cirque tarns, turquoise glacial lakes and interdune lakes.
- A **custom global fluid picker** returns the local water level (lake level, stepped river level) or lava level (lava channels) per column. Lakes and rivers above sea level are then placed during the noise stage. They're consistent across chunks and need no lake features.
- **Waterfalls** form where the stepped level drops at a rock sill: the terrain stage leaves a lip with a notch, and the generator schedules fluid ticks at the step.
- **Known risk**: catchments that extend past the region margin can leave a slight width mismatch at region borders. Mitigations: wide margins, plus flow accumulation clamped to a smooth function of the continental field.

**River water (0.0.6).** Vanilla water above sea level runs out of every gap, which is why 0.0.5's rivers leaked off every step. `grandatlas:river_water` (`world/RiverWater`) is water to the game: NeoForge's water type (swimming, drowning, boats), the `#minecraft:water` tag (fish, plants), a water bucket when scooped up, vanilla's water model and biome tint, and vanilla water counts it as its own (a mixin on `WaterFluid.isSame`, so the two meet without a face and falls stop on it). But it is always a source and never spreads. It carries the river's current (`flow`, eight directions from the course), which pushes swimmers, mobs, items and boats downstream and runs the surface texture that way; a running river is heard at its surface. Dug open, it turns into ordinary water there and flows out. `RiverWater.current(level, pos)` gives the current at any block for physics mods (Sable has no 26.x build yet; a bridge can read it).

**Plunge pools (0.1.3):** where a Great Plateau's river pours off the rim, a pool is dug at the cliff foot (AtlasCore.plungePool): found by walking out from the crest until the ground stops falling, its level a block under the lowest river-cut ground round it, unless a drainage river already takes the fall.

**Placement:** `grandatlas:perched_water`, once per chunk in every overworld biome (a NeoForge biome modifier, at the lakes step), fills each column's water from the core (`ColumnSample.water`, `flowX/flowZ`), the drainage network's and a Great Plateau's own. Each block decides from the core's columns, not the world, so the answer doesn't depend on which chunk generates first. It is river water where the water is held, and ordinary water (with a fluid tick) where a block would face open air: a lip pours as a waterfall into the pool below, and a bank lower than the water runs as a trickle. Sea-level rivers swap the aquifer's water for river water. Frozen rivers stay ordinary water, so they ice over. Where the core has ground beside or under the water but the world has air (a carver's tunnel), the hole is filled with the ground around it, as vanilla's own lakes do. **Highland River** (`grandatlas:river`) is our copy of vanilla's river biome at temperature 0.8, since vanilla cools biomes with height and its own river iced over at y 480.

**Oceans (M3).** Continental shelf, slope, abyssal plain, trenches, seamounts, warm-water atolls, sea stacks and cliff coasts. Ha Long Bay and the fjords are landforms on top of this.

## 9. Biomes and surface

- **`grandatlas:atlas` biome source.** It's 3D. Above ground, the biome comes from the column's landform zone (rim, floor, peak and so on). Below ground, it comes from cave zones (§11). Vanilla cave biomes stay where vanilla structures need them: deep dark for ancient cities, and lush and dripstone caves.
- **Vanilla structure coverage.** Our biome choices must still include the vanilla biomes (or tag members) that structures need: villages, monuments, mansions, trail ruins, ancient cities and so on. Our own biomes join the right `has_structure/*` tags. A test checks that every vanilla structure can still find a valid biome.
- **Surface rules.** We override `minecraft:overworld` noise settings, keeping vanilla's surface rules plus our own. Our rules are custom Java condition and rule types where JSON would be clumsy:
  - `grandatlas:strata`: warped, tilted coloured bands for Rainbow Mountains and canyon walls.
  - `grandatlas:cell_pattern`: Voronoi-based patterns for Spotted Lake pools, salt-crust polygons, cracked lava crust and rice-terrace paddies.
  - `grandatlas:zone`: tests the current column's landform zone directly, which is cheaper and more exact than a biome check.
- **Atmosphere** is per biome: fog colour, water colour (turquoise glacial lakes, emerald Ha Long sea, glowing enchanted rivers), sky and ambient particles (Pandora's wood sprites, mist, ash).

## 10. Landform catalogue

Heights are surface y. "3D" means the landform needs real density (overhangs or floating parts), not just a heightmap.

### Real world

| Landform | Shape and scale | Key techniques | Milestone |
|---|---|---|---|
| **Megarange** (large mountains) | 400 to 950. Knife-edge ridges, horns, glaciers and snowfields, deep valleys at 200 to 350, with **cirques** carved into the flanks (bowl, headwall, tarn, arêtes, and horns where three cirques meet). | Gradient-damped ridged noise along tectonic belts. Cirques are Voronoi bowls in the 450 to 750 band. | M1 |
| **Canyon plateau** (Grand Canyon) | Rim 350 to 450, river at ~70. A dendritic side-canyon network, buttes and mesas left standing, stepped cliff and slope walls in coloured strata. | Hardness-band quantization; canyons cut by the Stage A river plus branch noise. | M1 |
| **Dune sea** | Transverse dunes, barchans, and star megadunes up to 150 tall. Gentle windward side, steep slip face. Rare interdune lakes. | Anisotropic, asymmetric wave noise; star dunes are radial ridges on cell centres. | M1 |
| **Rock desert** (Wadi Rum, Dune: Awakening; built 0.0.3) | Red sand between wind-carved sandstone: jebels 140–380 across and 70–180 tall with domed, terraced tops and slot canyons; towers and spires up to 140; fins 10–22 thick. Holes (tafoni), arches, sand-blasted feet, overhangs and grooves along soft layers. Each district varies from open sand to a maze. It pairs with dune seas, whose dunes run into it, and the odd tower or fin stands in the dune seas too. | 2D rock field (`Rocks`) for the shapes, then 3D carving per block (in the fine caves pass) from each column's rock height and distance inside its outline. Rocks fade in with district density instead of switching on. Rock is the badlands biome; the floor is our Red Desert. | M1+ |
| **Playa / basin and range** | A dead-flat salt pan in a closed basin with alluvial fans and bare ranges around it. Polygon salt crust; rare shallow flood. | Flat clamp, fan cones at range mouths, cell-pattern surface. | M1 |
| **Fjordland** | U-shaped flooded troughs down to -60, sheer walls 400 to 700, branching arms, hanging valleys with waterfalls. Cold coasts only. | Branching warped valleys from the coast inland; U profile below sea level. | M3 |
| **Ha Long Bay** | Hundreds of limestone towers and islets, 40 to 200 tall, in shallow emerald sea. Tidal undercut notches, sea caves, arches. | 3D tower cells with a notch band at sea level; shallow-shelf ocean. | M3 |
| **Coasts and oceans** | Shelf, slope, abyss, trenches, seamounts, atolls, sea stacks. | Continental field profile plus features. | M3 |
| **Canadian Shield** | Low scoured granite hills (70 to 140), thousands of lakes, eskers, exposed bedrock, boreal forest. | Stage B lakes; bedrock surface rules. | M4 |
| **Canadian Rockies** | A megarange variant with turquoise glacial lakes in cirques and valley heads (Moraine Lake). | Megarange plus tarns; turquoise water colour. | M4 |
| **Zhangjiajie** | A forest of 100 to 300-tall sandstone pillars, 8 to 40 wide, over a deep valley floor. Flat pine-topped summits, ledge vegetation, heavy mist. | 3D Worley pillar cells with radius noise and undercuts; mist fog biome. | M5 |
| **Karst** | Tower karst (isolated steep towers), cone karst (clustered cones with doline sinkholes), cenotes, natural arches. Cave systems underneath. | 3D cells, doline depressions, cave link to the glowworm caves. | M5 |
| **Rainbow Mountains** | Rolling hills (150 to 350) with folded, tilted bands of red, orange, yellow, green, purple and cream. | `grandatlas:strata` with 3D-warped band offsets over terracotta colours. | M5 |
| **Spotted Lake** | A small basin with dozens of round mineral pools (yellow, green, blue) separated by walkable salt rims. | Cell pattern. Each pool gets a micro-biome for water colour, since pools are 8+ blocks and biome resolution is 4. | M5 |
| **Rice terraces** (Yuanyang) | Subtropical hills (250 to 600) carved into thousands of contour-following paddies, flooded with shallow mirror water and draped in morning mist. | Height quantization on slopes with a 1-block rim; paddy water from a post-pass feature on flat step interiors. | M5 |

### Fantasy

| Landform | Shape and scale | Key techniques | Milestone |
|---|---|---|---|
| **Pandora** (folded in) | A bioluminescent jungle under floating "Hallelujah" mountains. Islands now generate as **terrain in the noise stage** (300 to 700, up to ~150 wide). Vine bridges, anchor vines, boulders and colossal trees stay as features. | 3D island density; ports `FloatingMountainsFeature` bridge logic; Pandora blocks and biomes. | M6 |
| **Elven wood** | Soft valleys and gentle hills under colossal silver-barked, golden-leaved trees (canopy 60 to 120). Clear streams, flower meadows, faint night glow, mist. | Gentle terrain; a giant tree feature adapted from Pandora's `GiantTreeFeature`. (Our own design and names, no LOTR names.) | M6 |
| **Volcanic wastes** | Stratovolcanoes and shield volcanoes, calderas with lava lakes, black basalt plains, **lava rivers** running down channels from the vents, cooled flow fields, ash, fumaroles, obsidian. | Volcano cones from cell centres; lava channels via the fluid picker (Stage B). | M6 |
| **Misty peaks** | Granite needles and spires (Huangshan style) rising from a sea of cloud, with waterfalls dropping hundreds of blocks off cliff faces. | Needle cells on a raised plateau; valley fog biome; cliff spring features. | M6 |
| **Enchanted valley** | A lush, sheltered valley with a glowing river (custom water colour, glowing riverbed blocks, drifting motes), giant flowers and light shafts. | Valley carve plus the river; custom glow blocks. | M6 |

### Underground

| Landform | Notes | Milestone |
|---|---|---|
| **Glowworm caves** (Waitomo) | Large limestone galleries with an underground river; the ceiling is hung with glowing silk threads (a new block, low blue-green light). Placed under karst and humid temperate land. | M7 |
| **Crystal caves** (Naica) | Hot, deep geode caverns crossed by giant crystal beams 10 to 50 long, in new selenite-style blocks. Under volcanic and desert land. | M7 |
| **Deep world** | Vanilla cave networks rescaled to -128, with lava aquifers at -110. | M0 / M7 |

Room is left for more. The designyourway list and your Terralith favourites (§13) feed the backlog. The macro landforms that host whole sub-regions (Great Plateau, massif, basin, floating mini-continent and others) are catalogued in §6.4.

## 11. Caves

Vanilla's cave density functions stay; they're well tuned and players know them. They're rescaled for the deeper floor. Cave biomes are picked by 3D zone noise plus the landform above: karst gets glowworm caves, volcanic gets crystal and magma caves, and humid land gets lush caves. Deep dark stays under high, eroded land so ancient cities keep generating.

**Cave damping (built 2026-10-29).** Left alone, vanilla's caves hollowed out 8 to 25% of a 600-block mountain's body, because they were tuned for ground that stops near y 256. `grandatlas:cave_damping` (the core's `caveDamping`) adds a positive bias in exactly three places: the surface entrances, the deep cave mix and the noodles. The terrain term itself is never biased, so the surface doesn't move.

Measured in the dev world (open fraction of rock under ground above y 150, excluding the top 16 blocks):

| Zone | Before | After |
|---|---|---|
| Deep cave layer, below y 0 | 11–14% | 11–14% (untouched) |
| Shell, 16–63 blocks under any surface | 11.7% | 4.6% |
| Mountain interior, 64–255 blocks deep | 8–12% | 0–0.2% |
| Mountain interior by altitude, y 200–699 | 5–19% | 0.1–2%, as rare caverns |

**Playtest round 2: "WAY too solid".** The damping maximum went from 0.5 to 0.12, then to 0.06 (at 0.12 mountain interiors were still only 2–4% open). Grand Atlas added caves of its own (`Caves`, the `grandatlas:caves` density function):
- **Pits:** shafts 6–16 across dropping 60–160 blocks from a funnel at the surface.
- **Fissures:** cracks 1–4 wide and 20–90 deep in crack country (~15% of land).
- **Caverns:** chambers tens of blocks tall in cavern country (~20% of land).

Pits and fissures are carved per block, outside the interpolated grid. Their floors lower the surface estimate, so their walls count as surface: banded in badlands, and dry air to the aquifers.

Openness falls steeply with bias. A bias of 0.1 roughly halves it, and 0.5 closes almost everything at mountain altitudes. Openness also depends on altitude, so any retune has to be measured in a world, not reasoned from one curve. Later landforms can change the bias locally. Karst and the glowworm and crystal caves of M7 will open it up again; massif interiors may stay solid.

## 12. Integration with other biome mods

- **26.3 availability (2026-10-29):** TerraBlender, BOP, Distant Horizons, C2ME and Lithostitched have 26.3 NeoForge builds. BWG and Create don't.
- **TerraBlender mods (BOP, BWG once it ports, and others).** At startup we read every TerraBlender overworld region and collect each modded biome with the climate point its author gave it. We sort them into our slot categories (lowland forest, mountain slope, peak, desert, wetland, beach, river, ocean and so on) by climate point plus `c:` conventional tags. Those biomes then fill **ordinary** provinces and the generic zones of wonder landforms. A config weight sets how often modded biomes win over ours; per-biome allow and deny lists live in config.
- **Surface rules.** TerraBlender merges mod surface rules into whatever noise settings sit at the `minecraft:overworld` key. We override that same key on purpose, so BWG and BOP surfaces keep working. *(Verify in M8.)*
- **Lithostitched biome injectors** target multi-noise biome sources and won't fire on ours. Pandora's injector goes away with the fold-in, and nothing else in the pack is known to rely on one.
- **Distant Horizons.** It generates LODs through our generator. M0 checks that it handles -128 to 1024 and measures speed.

## 13. Terralith: taking what you like

**Licence constraint (Stardust Labs licence, bundled in the jar).** Copying any part of Terralith's code or data into another mod is forbidden, and using any Stardust Labs work in an AI project is explicitly banned. So:
- **I must not open Terralith's data files** (biome JSON, surface rules, noise settings) to derive anything.
- You pick what you liked from playing it. Name it, describe it, or send screenshots. I build an **original** version from your description and from the real-world inspiration. Names are our own.
- (The jar's `neoforge.mods.toml` says `MIT`, but the bundled `license.txt` is explicit and stricter; we follow the stricter one.)

## 14. Performance and client

- **Target**: per-chunk generation no slower than Tectonic at equal height. Benchmark with a fixed-seed Chunky pregen (radius 1,000) on the dev server, with and without C2ME.
- **Levers**: the column cache, the early-out above the column top, coarse-resolution climate and rain shadow (64-block grid, bilinear), and lazily built hydrology regions.
- **Noise cells** stay at vanilla's 4×8. Pillar edges might want 4×4 vertical; measure before changing.
- **Clouds** at 192 sit mid-mountain, giving a cloud sea. In 26.3 cloud height is plain data, the dimension type's `minecraft:visual/cloud_height` attribute, so changing it is one value in `tools/gen_data.py` with no client code. Default: leave at 192.
- **Fog / view**: very large terrain wants Distant Horizons. Mist biomes use biome fog colour; true volumetric mist would need shaders and is out of scope.

## 15. Tooling and testing

| Tool | Purpose |
|---|---|
| `gradlew preview` | Renders the core at any scale to PNG: hillshaded height plus landform colours, a biome map, water, and an x or z cross-section. 16,000 × 16,000 blocks at 1:16 in seconds. |
| `gradlew test` | Determinism, chunk-independence, continuity across province edges, height bounds, and structure-biome coverage. |
| `/grandatlas seed` | Prints the live world's core seed; `gradlew preview -Pargs="--seed <it>"` then renders that exact world. |
| `/grandatlas where` | Prints the tag stack (landform → zone), province, transition, datum + relief, climate and biome at your position. |
| `/grandatlas locate <landform>` | The nearest province holding that landform. |
| `/grandatlas map` | (planned) The preview render in game. |
| Dev server | `gradlew runServer --offline` with RCON; ported `tools/rcon.py` and `tools/render_region.py` from Pandora Wilds to render real region files to PNG. |

## 16. Milestones

| # | Milestone | Done when |
|---|---|---|
| **M0 ✓** | **Foundation.** Scaffold, height override, `grandatlas:terrain` and `grandatlas:climate` density functions, `grandatlas:atlas` biome source (vanilla biomes), seeding, column cache, previewer, dev server. | A dev world generates at -128 to 1024 with a placeholder continent and mountain field, and `preview` renders it. |
| **M1 ✓** | **Landform framework, built ready for nesting.** Continents, climate, province mosaic, blender with transition styles, adjacency, biome slots, landform registry. Landforms are relief over a datum, frames are passed down, and every column carries a tag stack. First landforms: megarange with cirques, canyon plateau, dune sea, playa. | Preview and in-game renders show the four meeting each other with no seams and clear identities. |
| **M2 ✓** | **Macro landforms (§6).** Macro grid and query, frames, sub-mosaics, macro edges, `where` stack. First macros: Great Plateau, mountain massif, enclosed basin, elevated mesa. | A Great Plateau is recognisable from its foot and from on top, with several provinces and biomes on it and no seams at its cliff wall. |
| **M2b** | **Meso, micro and detail features** ([MICRO_FEATURES.md](MICRO_FEATURES.md)). A `MicroField` in the core (jittered cells, tiled), placement by context (tag stack, climate), shapes before rivers, carves per block, decorations at the features step; a first slice the user picks from the idea pool. **First slice built in 0.0.7:** five meso kinds (pond country, sinkhole field, hoodoo garden, cinder field, glade) and six micro kinds, with detail. | The first slice generates in a dev world with no seams at footprints, each feature is recognisable in a screenshot, `where` and `locate` know them, and `checkCore` covers determinism, density and forbidden contexts. |
| **M3** | **Oceans, coasts and sea-level rivers.** Shelf, slope, abyss, trenches, fjords, Ha Long Bay, Stage A rivers (rivers to the sea built in 0.0.4 through the drainage network, §8; deltas and estuaries still to do). | Rivers reach the sea everywhere, and fjords and Ha Long render right. |
| **M4** | **Hydrology Stage B.** Drainage graph, lakes, stepped rivers, waterfalls, fluid picker; Canadian Shield and Rockies. Macro water datums: plateau rivers pour off the rim (started in 0.0.3, one lake and river per Great Plateau), basins hold terminal lakes. The drainage graph, river lakes, closed-basin lakes, cascades and waterfalls are built (0.0.4, §8); the fluid picker, lake districts and the Shield and Rockies aren't. | Lakes above sea level and waterfalls generate seamlessly. |
| **M5** | **Real-world wonders.** Zhangjiajie, karst, Rainbow Mountains, Spotted Lake, rice terraces; the tepui, caldera and rift valley macros. | Each one is recognisable from a screenshot. |
| **M6** | **Fantasy.** Pandora fold-in (islands as terrain), elven wood, volcanic wastes with lava rivers, misty peaks, enchanted valley; the floating mini-continent macro. | The same. |
| **M7** | **Underground, down to Y=-512** (the user's requirement, 2026-11-03; see MICRO_FEATURES.md §8). The floor moves from -128 to -512 and the underground becomes a second terrain domain: connected cave networks, mega-caverns, underground rivers, lakes and seas, karst linking sinkholes to caves, 3D cave biomes that vary with height. Plus glowworm and crystal caves. | The same. |
| **M8** | **Integration.** TerraBlender harvest (BOP now, BWG when it ports), config, structure coverage, DH check, your Terralith-inspired picks. | A 26.3 test profile generates with BOP biomes on our landforms. |
| **M9** | **Performance and release.** Profiling, a pregen benchmark, README, release jar. | Meets the §14 target. |

## 17. Risks and open questions

1. **Stage B hydrology** is the riskiest system (region-border consistency, waterfall lips). It's isolated behind the fluid picker so the rest doesn't depend on it.
2. **Owning the biome source** means we answer for every structure's biome needs. That's covered by the structure-coverage test.
3. **TerraBlender surface rule merging** at the overridden `minecraft:overworld` key is unverified until M8.
4. **Performance at 3× height**: mitigated by the early-out and caches; measured from M0 on.
5. **Existing worlds.** A Grand Atlas world has to be new. Old chunks don't regenerate, and borders against them would be cliffs.
6. ~~Caves inside 600-block mountains~~: fixed with cave damping (§11).
7. **Macro landforms vs. hydrology**: plateau rivers need waterfalls off the cliff wall and basins need terminal lakes, so the full macro experience waits on Stage B (M4). M2 macros ship dry, with lakes and rivers arriving in M4.
   - **Rarity is a judgement call.** About one macro per 400 km²; the nearest Great Plateau to spawn on the dev seed is 22 km away. Tune `HOST_CHANCE` after playing.
   - **Cirques read as craters at map scale**, especially on massifs. Judge them in a client before reshaping.
8. **Canyon networks loop.** M1 cuts canyons along noise contours, which close into loops. Real canyons are dendritic and drain to one river. Fix in M3, where Stage A rivers drive the main canyons and side canyons branch from them.
9. **Strata benches are subtle in game.** The wall stepping (a gentle bench for 60% of each band, then a cliff) barely shows at canyon scale. Steeper contrast, or wider bands, after an in-game look.
10. **Pandora island scale as terrain**: islands up to 150 wide in the noise stage are fine, but the vine bridges stay features and keep the 3×3-chunk write limit. The fold-in keeps Pandora's per-chunk-fill trick for them.

## 18. Playtest log

**Round 1 (2026-10-30, 0.0.1 → 0.0.2).** The user walked the tour in a client. What came up, and the fix:

| Seen | Cause | Fix |
|---|---|---|
| Flat coastal shelf with a sand stripe | Relief faded and height flattened over a coastal band hundreds of blocks wide; beach biome assigned anywhere below y 67 in that band | Relief fades over cont 0–0.03, flattening and beaches only in the last ~0.012 (a ~20-block strip) |
| Water hanging in walls; shipwreck inland | Preliminary surface was the exact height; aquifers add 8 and treat that as ground, so local water sat up to 8 above it. Sub-sea pockets inland counted as ocean | Conservative preliminary surface (lowest nearby grid column − 6, snapped to 8); continentalness 4 octaves; ocean biome only seaward (cont < 0.01) |
| Canyon wall a "steep ramp" | Strata bench/face slope ratio only 3.5:1 | Flat benches for 78% of each band, then one near-vertical cliff; wandering bench edges; the canyon plateau's outer edge steps too |
| Bare stone on canyon walls | Surface rules only paint above vanilla's 16-block interpolation of the preliminary surface, which overshot on tall walls | `chunk_surface_level` uses the per-4-block-cell estimate directly (top-surface stone ~1%) |
| Too smooth | Strong slope damping in the ridge fractal; no small-scale relief | Damping 0.12 on megaranges, a crag layer, ridged 3D roughness, micro relief on all land |
| Sandy mountain in the dunes | Star dunes 160–220 blocks | 80–120 blocks, narrow sharp arms |
| Salt flat cut through by a ridge | Pan mask independent of the range lines | Pans only along basin axes, clear of ranges |
| Loved the sandy basin; wanted mesa outcrops (Dune: Awakening) | — | Rock outcrops in dune seas: 45–110-block stepped mini mesas, badlands-banded |

The self-check caught a bug from M1 while these were being made: a canyon butte switched on at a hard threshold (`cut > 0.2`), which made a 14-block seam. It now fades in.

**Round 2 (2026-10-30, 0.0.2 → 0.0.3).** Two passes of notes from the tour: the general judging list, then stop-by-stop.

| Seen | Cause | Fix |
|---|---|---|
| Mountains huge but "sweepy and smooth", tips always rounded, every slope a steep ramp | One profile everywhere; strong slope damping; the cap compressed summits | Per-range styles (profile exponent 1.3–2.4, dolomite strata bands, needle fields), knife ridges, crags, ridge damping 0.12, amplitude kept under the cap |
| A mesa dropped back to normal ground before the desert it overlooked | 350-block dune taper, wide plateau edge ramp | Dunes taper over 120 blocks; the canyon plateau's edge staircase is 110 blocks wide |
| Only noticed the plateau; macros too rare | 8 km cells at half occupancy (one per ~400 km²) | 5 km cells, 85% occupancy, shrink-to-fit (§6.4) |
| Salt flat not flat enough | Micro relief on the pan | No micro relief on pans |
| Canyon walls blend stone into the terracotta | Cliff faces 12+ blocks below their column's top got the dripstone caves biome (61% of the bare stone in 0.0.2), and the rim's savanna-plateau steps had no bands | Cave biomes start under the surface estimate (the lowest ground nearby), not the column's own top; the rim is wooded badlands. Walls are now 6 stone faces in 428k; the rest is in pit and fissure walls, which count as surface too |
| Caves "WAY too solid" | Damping 0.5 | Damping 0.06 and Grand Atlas's own pits, fissures and caverns (§11) |
| Loved the mesa outcrops in the dunes, "could do with more holes"; wanted Wadi Rum / Dune: Awakening rock over large areas, with dunes flowing in | — | The rock desert landform and its 3D carving (§10); rocks in dune seas; outcrops carved too |
| Great Plateau wall towering, but the top is only plains and forest: no rivers off it, no mountains | Low relief scale (0.55) and a family of fields and hills; nothing site-specific | Relief 0.75, more hills and uplands, a summit, a lake and a river that falls off the rim (§6.4, §8) |

The self-check caught one bug from these: rock existence was a hard threshold on a district density that varies, so a whole jebel popped in at a density contour (a 28-block seam). Rocks now grow out of the sand over the last 0.08 of density.

**Round 3 (2026-10-30, 0.0.3 → 0.0.4).** Before testing 0.0.3, the user asked for rivers: "there was almost no water besides oceans... that is even worse than regular Minecraft. Plenty of rivers, but small rivers need to flow into big ones and big ones into oceans. Think like real watershed systems." Built: the drainage network of §8. Verified in a client: a confluence in a snowy valley, a forest lake on a river, a mountain stream in its ravine, and a river mouth pouring down a coastal escarpment into the sea. The self-check found one thing to fix on the way: with rivers, its world-wide checks (points 5,000 km apart) built the drainage of every basin they touched, so those checks now use the terrain before rivers.

**Round 3b (2026-10-30, 0.0.4 → 0.0.5).** The user's first look at 0.0.4: "floating water and non-smooth/realistic streams... if something needs to fall far it should either snake to be gradual, waterfall into a small lake and then continue, or look more natural." Screenshots showed tilted sheets of still water down mountainsides, staircases of water on hills, and walls of water standing over valleys. Measured from the core in the reported spots: up to 14,322 blocks of open water face in a 270×220 area. Fixed with the pools and containment of §8 (waterfalls into plunge pools, the user's second option), down to 0 open faces in all four spots. Looking at the result in the dev client, the user showed a 30-block full-width fall into a narrow gorge; falls were then capped at 16 with chute lips and wide plunge pools. On the way, the containment check caught a lip that pinched the river while the pool below started full width: a 7-block jump in the channel edge that skipped the neighbour check.

**Round 3c (2026-11-02, 0.0.5 → 0.0.6).** The user, on 0.0.5: "still bad flow, and now spiky terrain. Go look at how other mods and vanilla handle this." Studied vanilla (sea-level rivers cut by lowering, lakes that validate their whole boundary), TerraForged (rivers only lower, valleys lerped toward the river level), Tectonic (sea-level rivers) and Dynamic Waters (a fine routing grid, pools and falls cut into slopes, a non-spreading river fluid). The user chose a full redesign, and asked mid-way for river currents that work with Sable (Sable has no 26.x build, so there is a generic current plus `RiverWater.current` for a later bridge). Built: §8 as it stands now and its river water.

| Seen | Cause | Fix |
|---|---|---|
| Spiky terrain along rivers | 0.0.5's raised banks, levees and neighbour probes | Lowering only. Bodies combine by the deepest cut, guards keep one body's valley out of another's banks |
| Water still standing over valleys at confluences | A tributary only dropped to the main river at the junction node, inside the main channel, and widened to its width | Tributaries keep their width and drop where they reach the main river's banks: one fall at a lip |
| River water over a plain | A path between nodes dipped under the downstream node's level, and the water was held at that level | The level rivers meet at is capped by the lowest ground on every route in |
| Lakes level with the land | A dip in a plain made a lake at ground level that held rivers up | Lakes fill real basins at least 3 deep |
| Vanilla water leaking from every step | Water above sea level spreads | `grandatlas:river_water` never spreads; ordinary water only where a block faces air |
| Banks a block under the water in the world, holes under beds | The 4×8×4 density interpolation rounded the cut; vanilla's carvers tunnel blind | The cut goes in per block; placement plugs carver holes beside and under water |

Measured in the dev world: in the two worst areas from round 3b, 0 river water blocks open to the air (279 + 162 and 97 before the last two fixes). A log floats downstream (north-west, as its blocks say) at up to 18 blocks a second; items have almost no drag in water, which is vanilla's own behaviour. A boat drifts at 2.4 blocks a second. A dug-open bank turns the river water beside it into ordinary water that flows out. Lakes are rarer than in 0.0.5 (4 in a 12 km square, against about 40): real hollows are rare in this terrain. Valley-floor lakes or lake districts are the open question for the user.
