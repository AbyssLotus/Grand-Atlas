# M3 + M4: oceans, coasts and the rest of the water (plan)

The user, 2026-11-03: "let's focus on fully implementing M3 and M4, quality is key!"

## Quality bar (every phase)

- **Epic first** (the user's goal, DESIGN.md §0): high fantasy that looks hand-crafted. A feature passes when it looks composed and dramatic, not merely plausible.
- **Seen in-world before it ships:** client screenshots of every new feature from the tour spots, judged against the real place it imitates. Ugly gets reworked, not shipped.
- **Measured:** water containment (open faces), seams (no residual jumps), determinism, and a count per km² that matches the target. Each in `checkCore`.
- **No uniformity:** every shape gets layered smooth noise on its outline, profile and material (the user's rule since 0.1.5). Materials come in smooth patches, never per-block speckle.
- **Restraint:** features are rare enough to stay special; detail density is kept low (the user's feedback on logs and site sizes).
- **One phase at a time,** each its own version with a tour, so the user can judge it before the next one starts.

## Today

The ocean floor is a fixed profile (`WorldField.oceanFloor`): a shelf from 62 to 44, a slope to -40, and an abyssal plain near -50 with gentle hills. The coast is a strip where relief fades, with a beach biome below y 66. Rivers reach the sea at sea level through lowered valleys. Nothing else exists yet.

## Phases

### Phase 1: coastlines (M3), the part every player sees
- **Coast types**, chosen per stretch of coast by the land behind it (relief, rock, climate), with transitions along the shore:
  - **Sandy beaches** of varying width, with dunes behind them where it's dry and windy, and pebble beaches on rockier shores.
  - **Cliff coasts** where high land meets the sea: sheer sea cliffs (rock-coloured, banded where the land is), a wave-cut platform at the foot, and **sea stacks, sea arches and sea caves** off and in them.
  - **Rocky shores:** boulder-strewn, broken ledges.
  - **Low marshy coasts** where it's flat and wet: tidal flats, salt marsh.
- **Bays and headlands:** the coastline gets proper indentations at several scales, not a smooth contour.
- **Quality bar:** a cliff coast reads as one from the sea (stacks standing off it); a beach has a believable profile into the water; no stone stripes or floating sand.

### Phase 2: the sea floor (M3)
- **Shelf** with sand ripples, sandbars and the odd rocky reef; **continental slope** cut by **submarine canyons** that start off river mouths; **abyssal plain** with hills; rare long **trenches**; **seamounts** and flat-topped guyots.
- **Warm seas:** coral reefs on the shelf (vanilla coral), **atolls** with lagoons, **blue holes**.
- **Kelp forests** in cool shallow water.
- **Quality bar:** diving anywhere shows varied ground; a canyon reads from a submarine view; reefs are dense and colourful but bounded.

### Phase 3: rivers meet the sea (M3 + M4)
- **Estuaries:** a river mouth widens into a funnel with mudflats and salt marsh.
- **Deltas** for big rivers on gentle coasts: the river splits into distributary channels across a fan of sand and marsh islands.
- **Barrier islands and lagoons** off low sandy coasts; **sand spits**.
- **Quality bar:** a big river's mouth is a destination, not a gap in the beach; channels stay contained.

### Phase 4: river forms (M4)
- **Meanders:** lowland rivers on flat floodplains wind in loops (sinuosity with the land's flatness), cutting **river cliffs** on outer bends and **point bars** on inner ones.
- **Oxbow lakes:** cut-off loops left beside meandering rivers.
- **Braided channels:** wide gravel rivers below mountains, many channels round shifting **gravel bars**.
- **River islands** in big rivers.
- **Quality bar:** a meandering river seen from above looks like a real floodplain; everything stays contained and order-free.

### Phase 5: lakes (M4)
- **Lake districts** (Canadian Shield): low glaciated rocky country with many lakes of all sizes joined by short rivers and rapids, rocky islands, bare rock shores.
- **More lakes everywhere:** valley lakes behind natural dams, crater lakes in calderas, kettle lakes; lake islands; lake outlets that start rivers.
  - Done 0.3.5: **calderas** (MesoKind.CALDERA, Crater Lake), with cinder-cone islands and their own deep-blue lake biome.
- **Quality bar:** lakes are common enough that the world doesn't feel dry, each with a shape and shore of its own.

### Phase 6: the big set pieces (M3)
- **Fjords** on cold mountainous coasts: deep U-shaped sea inlets winding kilometres inland between sheer walls, with hanging-valley waterfalls.
- **Ha Long Bay** on warm coasts: a bay full of karst towers rising from green water.
- **Quality bar:** each recognisable from a screenshot.

### Waterfalls (M4, the user's request, 2026-11-03)

The user: "water falls are cool, water falls are awesome. I have not seen many waterfalls! Sometimes having a manufactured water fall is great!!!" So two sources of falls: more of them where rivers already drop, and **set-piece falls** built on purpose, each a destination. From the user's reference photos:

- **More falls on the river network:** where a river crosses a cliff band, a plateau edge or a hard rock step, it keeps the drop as one fall into a plunge pool instead of a cut. Falls get a sheer lip, a plunge pool scoured wider than the river, spray-wet mossy rock round it, and an outflow.
- **Set pieces** (rare meso sites, each sited where the terrain suits it, like the forest sites):
  - **Curtain fall** (Skógafoss): a wide river pouring 40-60 blocks off a sheer escarpment into a round basin, the river running on across a flat floor below.
  - **Punchbowl fall** (Latourell): a thin, tall plunge into an amphitheatre of sheer columnar rock, in forest, mossy and ferny round the pool.
  - **Terraced cascades** (Kuang Si): a stream stepping down a hillside over many travertine rims into turquoise pools, each pool spilling over a curved lip into the next.
  - **Ledge cascade** (alpine): water sliding down a staircase of rock ledges on a mountainside.
  - **Boulder falls** (river rapids): a wide river breaking through a field of rounded boulders and slabs in many small falls and chutes.
  - **Canyon fall** (Havasu): a fall over a red canyon wall into a turquoise pool on the canyon floor.
  - **Falls along a rim** (Iguazu, rare): a whole cliff edge with many falls side by side over a forested ledge.
  - **Grotto** (Hamilton Pool): a collapsed cave, a pool under a huge overhanging rim, a thin fall pouring in from the lip above.
- Water that a set piece needs comes from a spring (a stream starting at the top) or from the river network where one passes; either way it must stay contained (the checkCore water tests cover falls too).
- **Quality bar:** each type recognisable from a screenshot next to its reference photo; falls audible and visible from a distance; no water hanging in air.

### Crag mountains (landform revisit, the user's request, 2026-11-03)

The user: our mountains "while real looking could look way better if we gave them a more aggressive vibe, but only sometimes, because it's good to have variety. More overhangs and jaggedness." Reference photos: BotW's twin split towers, jagged ice horns, Avatar's leaning rock pillars, a karst jungle peak, an overhanging Minecraft spire.

- A fourth megarange style, **crags**, next to alpine, dolomite and needles, blended in by the same slow regional fields so only some ranges get it:
  - Summits split into twin and triple towers with sheer clefts between.
  - Strong 3D roughness near the surface: overhanging ledges, undercut faces, arches through thin ridges.
  - Fields of leaning rock pillars on the flanks and foothills.
  - Sharper horns: knife ridges running up to needle tips.
- Also usable outside megaranges: isolated crag peaks in hills and lowlands (the BotW look: a dramatic tower rising out of green plains).
- **Quality bar:** a crag range reads as "aggressive" from a distance and has overhangs you can stand under up close; ordinary ranges keep their current look.

## Later (from the user's playtests)

- **A wooded marsh biome of its own** (user, 0.1.6 playtest: "marsh looks good... maybe some marshy trees would be good, mark this down as its own biome for later"). The salt marsh stays open grass; this is a separate biome with marsh trees.

## Order and rhythm

Phase 1 first (coasts), then 2, 3, 4, 5, 6. Each phase ends with screenshots, a tour, a version in the playtest profile, and the user's verdict before the next starts. The Y=-512 underground (M7) waits until these are done.
