# Cave redesign: analysis and design (2026-11-18)

The user, after playing test43: "our caves right now feel wrong. They are in an odd in-between spot
where our large parts (not mega stuff) are smaller than Minecraft's, but our small parts are larger
than vanilla. Sometimes we get some really interesting shapes, but most of the time it is meh. We
need to re-design how caves are generated, how props are picked, and research how real caves work
and how other games handle level design, and how to match it procedurally."

This document is the analysis and the proposed design. **Nothing in it is built yet.** It ends with
the questions the user should answer before work starts.

---

## 1. What is wrong, measured

New tool: `debug/CaveShape` (`gradlew :mc1211:runCaveShape`, writes `run/grandatlas/cave_shape.txt`).
It samples every block in six 112 x 112 x 120 boxes, for vanilla's caves (vanilla's own terrain for
this seed) and ours, and measures the *shape* of the open space, which the volume survey can't see.

| y -60..60, six boxes | vanilla | Grand Atlas |
|---|---|---|
| open | 13.6% | 9.2% |
| **middle points** (medial axis) thinner than r 1.5 | **63%** | 11% |
| middle points r 2.5-6 | 20% | **63%** |
| middle points r 9+ | 0.4% | 0.2% |
| open volume 9+ blocks from any rock (inside big rooms) | **15.6%** | **0%** |
| open volume in air columns 21+ tall | **47%** | 3% |
| open volume in air columns 5-12 tall | 24% | **69%** |
| open volume in runs 36+ wide (E-W) | 52% | 23% |
| open volume in connected spaces of 100k+ blocks | 74% | 43% |

The user's diagnosis, confirmed. Vanilla's spectrum is **wide and heavy-tailed**: masses of tiny
noodles and ripples, and a long tail of huge, tall cheese voids. Ours is **one hump in the middle**:
almost everything is a 5-12 tall, r 3-6 tube or a small room. Nothing is tiny and nothing is big.

In the deep and abyss the numbers look bigger, but only because the boxes catch mega-caverns: the
passages themselves have the same hump (middle points r 1.5-6: 63% deep, 61% abyss), and the
mega-caverns are a separate, rare thing. Between "a tube" and "a mega-cavern" there is nothing.

### Why the code does this

- **Every size is a uniform draw in a narrow band.** Lattice and deep tubes: r 2.8-5, rooms
  4.6-7 (x 1.25-1.5 deeper). Solution tubes r 2.4-4. Even the "crawls" and "squeezes" are r 1.8:
  3-4 blocks across, larger than a vanilla noodle. Uniform draws in narrow bands cannot make a tail.
- **Rooms are squashed spheres** (vertical ratio 0.45-0.6), so a "room" is a wider tube: no height.
- **Rooms and passages are the same scale**, so arriving in a room isn't a reveal: there is no
  compression before release.
- **Shape comes from one recipe** (Karst.walk: a wandering polyline of capsules, roughness noise).
  Every space is the same kind of thing at a different size, so the caves don't read as places.
- **Props are picked per chunk, not per place.** DeepDetail throws ~160 random darts per chunk,
  takes the first 2 (deep) or 3 (abyss) that land on a floor, and picks a vignette uniformly from
  the mood's list. So vignettes are evenly sprinkled noise: no hierarchy (focal vs background), no
  relation to the space (a "lone lantern" can land in a 3-wide crawl), no anti-repetition, and the
  same density in a dead-end crawl as in a great hall. That's why the props feel random and why
  things like the chain boulders felt stuck on.

---

## 2. Research

### 2.1 Real caves

- **Patterns (Palmer, *Origin and morphology of limestone caves*, 1991).** Most solution caves are
  *branchwork*: tributary passages joining downstream into fewer, larger trunks, like a river
  system. Less common: *network mazes* (grids along joints), *anastomotic mazes* (braided tubes on
  one bedding plane), *spongework*, and *ramiform* caves (big irregular rooms with branches,
  from rising acidic water). The pattern follows the water source and the rock, so it varies by
  region, not randomly.
- **Sizes grow downstream.** A branchwork cave's passages get bigger where tributaries join; the
  trunk is the biggest thing. Passage sizes are roughly **lognormal** (field measurements of
  conduit size, e.g. the 2023 Springer modelling paper), and cave lengths follow power laws
  (fractal dimension ~1.07, the Slovenian studies). A lognormal is exactly the heavy tail we lack.
- **Cross-section tells the history.** Phreatic (water-filled) passages are round or elliptical
  tubes, often along one bedding plane (wide and low). Vadose (air-filled, stream at the bottom)
  passages are tall, narrow canyons cut downward. A tube with a canyon cut in its floor is a
  *keyhole*. Breakdown (collapse) makes big rooms with flat, slabby ceilings and boulder-heap floors,
  usually **where passages cross or at bends** (wider unsupported spans). So big rooms happen at
  junctions, not mid-passage.
- **Levels.** Caves form in levels tied to old water tables; shafts and canyons drop between them.
  (We already have this: Solution levels every 26, Deep levels.)
- **Decoration is zoned, not sprinkled.** Speleothems mass where water seeps in (under thin roof,
  along joints); breakdown is dry and bare; stream passages are scoured clean with gravel and
  scallops. A real cave has long plain stretches and then a room that's all formations.

### 2.2 Vanilla Minecraft (1.18+ noise caves)

Three noise families combined (minecraft.wiki, *Custom world generation*): **cheese** (thresholded
3D noise: huge irregular voids, pillars, often 30+ tall), **spaghetti** (long winding tunnels, some
wide), **noodles** (thin, 1-3 wide, squiggly). Because they're independent, every scale is present
everywhere, which gives the wide spectrum measured above. What vanilla lacks is *structure*: no
pacing, no hierarchy, no sense of a route. That's the opening for us: **vanilla's spectrum with
designed structure**.

### 2.3 Other games

- **Deep Rock Galactic** (Ghost Ship's dev blog on cave generation): ~119 **hand-made room
  templates**, each built from intersecting spheres, half-spheres and planes. *Randomizer orbs*
  inside each template are switched on or off to hollow extra space or add a rock formation, so one
  template gives many rooms. *Exit nodes* on the boundary say where tunnels attach. Rooms are placed
  one at a time and **chained by narrow, winding tunnels**; then biome noise displaces the smooth
  geometry (one biome sharp and ridged, another smooth and eroded), then debris and flora. This is
  the closest model to what we want: **designed room archetypes + procedural variation + narrow
  connectors**.
- **Spelunky** (Derek Yu; Game Maker's Toolkit's explainer; Darius Kazemi's generator write-up): a
  grid of rooms, a **guaranteed critical path** carved first, then hand-made templates per room
  role (drop, landing, corridor, off-path) with random chunks inside. Rewards and dangers are
  **off the critical path**. Yu's own criticism: players learn the templates. Lesson: templates by
  *role*, with enough internal randomness and enough templates.
- **Level design craft** (A Pattern Language for Expressive Environments; level-design pattern
  write-ups):
  - **Compression and release:** a tight passage before a big space makes the space feel huge.
    This is the single biggest lever for "epic" without "big".
  - **Landmarks / "weenies":** a visible focal point orients the player and pulls them forward.
    One per space, not ten.
  - **Pacing:** alternate tension and calm; constant intensity numbs, constant calm bores. Lower
    tension before a big moment so it lands.
  - Wide spaces invite exploration; narrow ones build tension. Safe spots are release valves.
  - Reward the detour: the best things are slightly off the main route.

---

## 3. Design principles (testable)

1. **The full spectrum, wider than vanilla's at both ends.** Thinner small parts (crawls 1-2 wide,
   squeezes you crouch through) and bigger large parts (rooms 20-60 wide, 25-60 tall) than now,
   with the mid sizes as connective tissue, not the bulk. Sizes drawn from **lognormal / power-law
   distributions**, never uniform bands.
2. **Places, not tubes.** A cave is a graph of *spaces* (rooms with a type and a role) joined by
   *links* (passages with a type). Each space type has its own shape recipe.
3. **Compression before release.** Every big space is entered through something smaller.
4. **Size follows flow.** In branchwork regions, passages grow downstream and meet in trunks; big
   rooms sit at junctions. The cave reads as a system that formed, which is the "grounded" half of
   the epic goal.
5. **Region character.** Each region has one cave pattern (branchwork, maze, spongework, ramiform,
   lava tubes, rifts...) that sets its rhythm. We already vary mood by region; pattern should vary
   too.
6. **Props by place and role.** Every space gets a dressing plan: at most **one focal piece**
   (placed where you see it as you enter), a few supporting pieces, then ambient dressing by surface
   and genesis. Most spaces are plain. Plain makes the special ones special.
7. **Pacing along routes.** Along any path, intensity rises and falls; a big moment is preceded by
   calm; the same vignette never repeats within a few spaces.
8. **Keep what works.** Moods, darkness, falls, water tables, the levels, karst (not more common),
   the volume bar (75% of vanilla per band), smoothness, and the mega-caverns as the rare top end.

---

## 4. Proposed architecture

Four layers. Each is a separate, testable step.

### Layer 1: the graph (topology)

Per region cell (like Solution's 256 and Deep's 384 cells today), generate a **space graph**:

- Pick the region's **pattern** (branchwork most common; maze, spongework, ramiform, rift, tube
  rarer) from the rock and setting.
- Place **spaces** (nodes) by pattern: branchwork puts big rooms at confluences and grows a trunk;
  maze puts many small rooms on a grid of joints; ramiform puts a few big irregular rooms with
  short branches.
- Size each space from a **lognormal** whose median and spread come from the pattern and depth.
  Target: a few big rooms per region, many small ones.
- Join with **links**, keeping a **main route** (entrance to deeper levels) plus loops and
  off-route branches (dead ends with rewards).
- Cross-region links at cell edges (as Solution's gates do today) so regions join seamlessly.

### Layer 2: space archetypes (shape)

Each space type is a **template built from primitives** (DRG-style: spheres, ellipsoids, capsules,
slabs/planes), with randomizer parts switched per instance, then displaced by smooth noise. First
set (about 15-20), each tied to real morphology:

| archetype | shape | where |
|---|---|---|
| breakdown hall | wide, flat slab ceiling, boulder-heap floor, 20-50 wide | junctions |
| dome / chimney room | round, tall (height > width), an aven going up | under thin roof, wet |
| canyon hall | long, 4-8 wide, 25-60 tall, ledges | vadose stretches |
| bedding-plane gallery | very wide (30-60), low (3-6), pillars | phreatic, one layer |
| keyhole junction | tube above, slot below | between levels |
| shaft / pit | vertical, falls | level drops (exists) |
| sump / lake room | floored by water, low arch onward | under the water table |
| ramiform chamber | big irregular blob with lobes | ramiform regions |
| balcony room | a passage opening high in a big room's wall | joins big rooms to links |
| spongework | many interlinked pockets 1-4 wide | spongework regions |
| lava tube | round, smooth, skylights | volcanic (later) |

Each archetype also says **where its focal piece goes** (centre of the floor, the far wall, the
ceiling) and what its entrances look like.

### Layer 3: links (passages)

Link types, each with its own size distribution and cross-section:

| link | size | notes |
|---|---|---|
| crawl | 2 high (or 1.5 with a slab ceiling: crouch only), 2-3 wide | a 1-high crawl can only be crossed swimming, so only under water |
| squeeze | 2 wide, 2 high, short | the compression before a reveal |
| tube | r 1.5-4 | phreatic, round or elliptical |
| canyon | 2-4 wide, 6-25 tall | vadose, meanders |
| keyhole | tube + slot | |
| trunk | r 6-14 | branchwork main stems, growing downstream |
| noodle | r 0.8-1.5, winding | the fine texture vanilla has and we lack; optional side holes |

Link sizes are chosen **by the graph** (tributary vs trunk; leading into a big room = small).

### Layer 4: dressing (props)

Replace "2-3 random darts per chunk" with **per-space dressing plans**, decided when the space is
generated (deterministic from its seed, so the feature step just reads the plan):

- **Focal piece** (0 or 1 per space; most spaces get none): chosen by archetype x mood x depth,
  placed at the archetype's focal spot (seen on entering). Big rooms get the big set pieces.
- **Supporting pieces** (0-3): along walls and edges, consistent with the focal one.
- **Ambient dressing**: by surface (floor, wall, ceiling) and genesis (wet: flowstone, dripstone;
  dry breakdown: rubble, bare; stream: gravel, scoured), zoned in patches, not speckled.
- **Rarity tiers** and a **repetition guard** (a vignette doesn't recur within N spaces).
- **Pacing:** along the main route the plan alternates calm and intense spaces.
- **Size fit:** each vignette states the minimum space it needs; no lanterns in crawls, no spires
  in canyons.

This keeps all 46 current vignettes as material: they get re-sorted into focal / supporting /
ambient, and each gets an archetype list and a size requirement.

---

## 5. Acceptance (the shape survey is the test)

Against vanilla, measured by `runCaveShape` (outside mega-caverns):

- middle points r < 1.5: **at least 40%** (vanilla 63%, us 11%)
- open volume 9+ from rock: **at least 15%** (vanilla 15.6%, us 0%)
- open volume in air columns 21+ tall: **at least 35%** (vanilla 47%, us 3%)
- volume per band: still **at least 75% of vanilla** (CaveSurvey)
- karst no more common; connectivity surface-to-abyss stays above 90%
- props: no more than ~1 focal piece per space; median distance between repeats of the same
  vignette at least N spaces (a new DeepCheck census)

And the user's eye in game: compression and reveal, places that read as places.

---

## 6. Phases

1. **Prototype the graph and archetypes in the core**, outside the game, with the probes and an
   updated Cave Survey 3D artifact so the user can look at shapes before anything ships.
2. **Swap the deep network (core/Deep) over** to the new generator (deepest, least risk to the
   surface), shape survey and DeepCheck as the checks.
3. **Solution caves and lattice** next, keeping karst as it is.
4. **Dressing plans** replace DeepDetail's darts.
5. Tune against the acceptance numbers, then playtest.

## 7. Questions for the user

1. **Crawls:** should some passages be truly 1 block high (only passable by swimming or crawling
   via mods), or is 1.5-2 high (crouch) the smallest?
2. **How big is "big"** for a normal (non-mega) room: up to ~40 across and 30 tall, or up to ~60/60?
3. **Templates:** are hand-designed room archetypes (like Deep Rock Galactic) OK, as long as each
   has lots of random variation? The risk is players recognising them.
4. **Vanilla noodles:** do we want the thin, winding noodle texture everywhere (vanilla-like), or
   only in some regions?
5. **Scope of the rewrite:** the deep and abyss first (recommended), or all bands at once?

---

Sources: Palmer 1991 (*GSA Bulletin*); Springer *Environmental Earth Sciences* 2023 conduit-size
modelling (link.springer.com/10.1007/s12665-023-11035-6); cave-length fractal studies (dlib.si);
minecraft.wiki *Tutorial: Custom world generation*; Ghost Ship Games, "Below Decks: cave generation
in Deep Rock Galactic" (via devtrackers.gg); Game Maker's Toolkit, "How and Why Spelunky Makes its
Own Levels"; Darius Kazemi, *Spelunky Generator Lessons* (tinysubversions.com); *A Pattern Language
for Expressive Environments* (mycours.es); *Level design patterns in 2D games* (cxong.github.io).

---

## 8. Progress

### 2026-11-18, test45: rooms, noodles and passage sizes in all bands

The user's answers: crouch height is the smallest; normal rooms up to 40 across and 30 tall, no
spheres; a library of predefined rooms randomized and smashed together; noodles everywhere; all bands.

- **Room library** (core/Rooms): 140 templates, 14 archetypes x 10, reviewed by the user in the Room
  Library artifact (spongework dropped; bridges, fins, avens, terraces rebuilt on ramiform-style
  lobes; domes and bowls lowered; crossroads made irregular). Rooms are `Passages.ROOM` pieces whose
  carve is the room's signed distance field. One room in four has a second smashed in; big rooms
  (over 26 wide) take a second big one half the time, sometimes a third: halls wider than one template.
  Big rooms stand taller for their width (up to 1.9x their template's ratio, capped at 30).
- **Where rooms go:** deep junctions by mood (awe: domes, bridges, canyons, avens, pillars, ramiform;
  cozy: galleries, terraces, bowls; clever: fissures, keyholes, fins; fear and horror: breakdown,
  fins, bowls); solution junctions (breakdown/crossroads/ramiform/pillars/dome where three passages
  meet, the rest elsewhere); lattice junctions (any). Widths lognormal (median 18-24, spread 0.45,
  9-40). Big rooms are entered through a crouch-height squeeze 70% of the time.
- **Passages:** lognormal widths (median 2.8-3.8, spread 0.4); in the deep, widths grow downstream
  with the junctions draining through (branchwork trunks). Crawls are crouch height (r 1.45, vr 0.8).
- **Noodles** (Passages.noodle): two smooth noises both near zero, 1-3 wide, gated by region (~70%
  of the world), never within the rock cover, never under a pool or flooded passage.

Measured (CaveShape, y -60..60; vanilla in brackets): middle points thinner than r 1.5: 56% (63%);
open volume in air columns 21+ tall: 29% (47%); open volume 9+ from rock: 0.9% (15.6%).
CaveSurvey: every band meets the 75% bar (shell 106%, upper 96%, low 148%, deep 111%, abyss 109%
of the reference, before the taller-rooms change, which only adds). DeepCheck: falls all sourced,
0 hanging water. Core self-check passes.

**Open:** the big end. With rooms capped at 40 x 30, at most ~12% of even the largest room's air is
9+ blocks from rock; vanilla's 15.6% comes from cheese caves 30-60 tall. Matching it needs a rare
"great hall" class above the cap (the user's decision), or accepting mega-caverns as the big end.
Dressing plans (layer 4) not started.

### 2026-11-18, test46: great halls

The user said yes to a rare class above the 40 x 30 cap. One room in ten (where the rock gives at
least 28 of height) is a **great hall**: 40-60 across, 24-45 tall, of the archetypes that read at
that scale (breakdown, dome, pillars, ramiform, bridge, canyon, fins, bowl, terraces), with at most
one smaller room smashed into it. Room interiors 9+ blocks from rock: 0.2% -> 3.0% of room air.
CaveShape y -60..60: air columns 21+ tall 32% (vanilla 47%), open 9.4%; deep and abyss unchanged in
kind. Core self-check passes.

Pools: a dry room never carves the ~2-block shell round a watered piece at or under its water (a room
cutting a plunge pool's wall or floor poured the pool into the room: 10-24 hanging sources per
DeepCheck run). After: 0. The falls' source finder now searches up to 30 under the shaft top; a wide
shaft whose top opens straight into a room can still go without a source (1 in 5 in the last run).

### 2026-11-18, test47: deeper is richer; andesite in the mountains

- **Deep riches** (tools/gen_data.py DEEP_RICHES, biome modifier grandatlas:deep_riches): extra iron,
  gold, redstone, lapis, copper and diamond in the deep, about 2.5x that in the abyss (the user:
  "more danger but also more loot, think Terraria"). DeepCheck census, ore in the rock per level
  (45 chunks): 111 above -64, 153 deep, 192 abyss; showing on cave walls per 1000 wall blocks: 9.9,
  11.1, 14.5.
- **Andesite, diorite, granite**: vanilla's upper blobs (one chunk in six, y 64-128) now two a chunk
  over y 64-640, so mountain caves have them (the user never saw andesite: most caves are outside
  vanilla's band).

### 2026-11-18, test49: loose ends and the rooms' dressing plans (layer 4)

- Lattice passages: lognormal widths too (median 2.8 / 3.2 / 4.2 by depth); crawls at crouch height
  with a floor of r 1.4 so the wobble can't pinch them shut.
- Falls with no wall to pour from (the shaft opens straight into a room) pour from a sealed roof
  block over the shaft instead.
- Water-table edges (core/Waters) are sealed: caves stop at a plug of rock under the water where the
  table changes within 3 blocks, instead of pouring at every level they cross. Every dry piece (not
  karst's own) leaves the shell round a pool or flooded passage alone. DeepCheck: 0 hanging sources
  in most runs; one run showed a 10-block sheet of held river water (doesn't flow; open).
- Committed the cave work through test47 + these fixes (3b65cf2, branch cave-rework). The Potted
  Monster / Crystal Eel mob work is left uncommitted for its own session.
- **Dressing plans:** world/DeepDetail dresses each library room: background dressing for every room
  meeting a chunk, by archetype (breakdown/fins: rubble and gravel; dome/aven/pillars/bridge:
  flowstone floor and drips; canyon/keyhole: a gravel stream bed; gallery/bowl/terraces: clay or
  calcite flats; others: a few drips), in noise patches; a focal piece at the template's focal spot
  (great halls always, rooms over 26 wide 60%, others 30%), from the mood's focal list (deep, abyss,
  or a natural list above -64), dealt out so rooms in the same 192-block square never repeat a kind;
  one to three supporting pieces near it. The old darts now dress passages only (1 deep, 2 abyss a
  chunk) and skip rooms.

### 2026-11-19, test50: R6 story and life, R7 the spiral gallery

- **R7, a gallery spiralling round a void** (core/Passages `SPIRAL` piece, `spiralSdf`; placed by
  core/Deep `spiral`): an open shaft two deep levels tall (~90-115 blocks) with a ledge 5-7 wide
  spiralling down its wall, each turn roofed by the turn above; radius and height wander with
  noise and columns stand along the void's edge now and then, so it's a cave's spiral, not a
  staircase. One deep cell in eight, one in three where the mood is awe or clever. Entered at the
  top turn from a junction, left from the void's floor toward the lower level. Inside the drum only
  the spiral carves (passages and noodles crossing it cut holes in the ledge; from outside they
  now open into the gallery as side entrances); the bottom 6 blocks stay open for the way out.
  Never placed where a water table over the drum reaches above its floor. Decoration skips it.
  Checks: core probe 12/12 walkable top to bottom; DeepCheck walks the two nearest spirals in the
  generated world (shape, core and world): both WALKABLE, ~7,000-8,400 standing spots each.
- **R6, found places** (world/DeepDetail `story`): half the great halls and 15% of rooms over 22
  wide hold one instead of a focal piece, by mood: a **colossus** (spine, rib arches, skull, tail,
  half sunk; soul soil in the abyss), a **lost expedition** (cold campfire, bedrolls, barrels, a
  crafting table, rails to nowhere, a loot chest: mineshaft above the deep, dungeon in the deep,
  ancient city in the abyss; a skull and webs in fear), a **fungal grove** (mycelium, giant red and
  brown mushrooms; crimson and warped under weeping vines in horror), a **root cathedral**
  (mangrove-root columns roof to floor, rooted dirt and hanging roots, moss). No architecture.
- New client check `deepLook` (client/DeepLook): screenshots the nearest spiral and found place
  with night vision (run/screenshots/deep_*.png). Dev server watchdog off in run/server.properties
  (the deep check's walks can take longer than 60 s on fresh ground).
- Volume: every band still over the bar (deep 130%, abyss 125%). Core self-check passes.
