# Meso, micro and detail features (plan)

The next milestone after rivers (user, 2026-11-02): "we already have macro formations; next week we work on micro formations: generations that spice up the surroundings with interesting features."

A **micro feature** is a local terrain, geological or environmental feature layered inside a biome or a larger landform without defining the whole region. One biome holds many of them, and some cross biome borders.

**Read this as a loose outline.** The feature list, the weighted-compatibility idea and the Macro → Meso → Micro → Detail chain come from a ChatGPT brainstorm the user shared, and the user says it adds plenty they didn't ask for. Nothing here is a requirement. The catalogue in §6 is an idea pool, marked by how Grand Atlas would build each item, so that picking from it is quick. What actually gets built is the user's call.

## 1. The hierarchy

The outline suggests a chain, **Macro → Meso → Micro → Detail**, with two examples:

- Great Plateau → alpine valley → waterfall + tarn + cliff → boulders + flowers + fallen logs
- Floating continent → floating jungle mountain → rock arch + waterfall → hanging vines + glowing plants

It fits the scale stack of DESIGN §6.1 as four levels under the province mosaic:

| Level | Size | What it is | Built today |
|---|---|---|---|
| **Macro** | 1.5–8 km | A new foundation (DESIGN §6) | Great Plateau, massif, basin, elevated mesa |
| *Province / landform* | 0.5–2.5 km | The landform mosaic (DESIGN §5.3) | Megarange, canyon plateau, dune sea, rock desert, ... |
| **Meso** | 100–800 blocks | A *site* inside a province: an alpine valley, an oasis hollow, a lake district, a karst field, a petrified forest, a hidden valley under a fallen cave roof. A meso site is a small frame: it sets a zone tag and reweights the micro features inside it. | Only as landform zones (valley, rim, peak) and a plateau's landmarks |
| **Micro** | 8–100 blocks | One feature: a tarn, a sinkhole, an arch, a boulder field, a waterfall, a hoodoo cluster | Rock desert rocks, pits and fissures, river falls and pools |
| **Detail** | 1–8 blocks | Dressing: boulders, flowers, fallen logs, moss, vines, mushrooms | Vanilla's biome decoration only |

Frames go all the way down, as in DESIGN §6.2: a macro reweights the provinces on it, a meso site reweights the micro features in it, and a micro feature brings its own detail set (a waterfall brings mossy stone, ferns and spray-damp ground near its pool).

## 2. Compatibility, not ownership

Features are not owned by biomes. Each one has **weighted compatibility** against the column's context:

| Weight | Multiplier | Waterfall, for example |
|---|---|---|
| Primary | 1.0 | mountains, rainforest, temperate forest, jungle |
| Secondary | 0.35 | taiga, plateau, coast, enchanted forest |
| Rare | 0.07 | desert oasis, badlands |
| Forbidden | 0 | salt pan, deep ocean |

The context is everything a column already knows: the tag stack (macro, macro zone, landform, zone), the biome, temperature and humidity, altitude, slope, distance to water, rock, and later "on or under floating terrain". On top of the weights come **hard requirements** (a waterfall needs a stream and a drop; a tarn needs a cirque floor or a hollow) and **spacing** (density per km², minimum distance to its own kind, exclusive groups so two big ground-shaping features don't overlap).

A feature is judged once, at its anchor. Its footprint then ignores biome borders, so a boulder field or a sinkhole runs across a forest edge into a meadow.

## 3. How each one is built

| Code | Kind | What it changes | Where it goes | Examples |
|---|---|---|---|---|
| **S** | Shape | Column height, as a 2D stamp blended into the ground (no hard edges) | The core, before rivers, so the drainage sees it: a moraine dams a lake, a sinkhole takes a stream | sinkholes, small mesas, cinder cones, moraines, eskers, craters |
| **C** | Carve | Real 3D density: holes, overhangs, undercuts | Per block, outside the interpolation, as the rock carving and the river cut are | arches, hoodoos, sea caves, root caves, natural bridges, lava tubes |
| **W** | Water | Water bodies and how they run | The drainage network, lakes and river water | tarns, ponds, springs, oxbows, bars, islands |
| **U** | Surface | Which blocks make the ground | Surface rules on a core mask, or a sub-biome for colours | wildflower fields, desert pavement, mud flats, black sand, dead grass |
| **D** | Decoration | Blocks placed on or in the ground | Java features at the features step, like `perched_water` (3×3-chunk write limit) | boulders, fallen logs, stone circles, basalt columns, fossils, giant trees |
| **F** | Floating | Terrain above the ground | A floating density layer (the M6 work: Pandora's islands as terrain) | floating boulders, islands, arches, hanging forests |
| **N** | New content | Needs new blocks, plants or art | Pandora Wilds' glow blocks, plants and giant trees are folded in (DESIGN §2), so many of these reuse them | glowing ponds, crystal blocks, giant flowers |
| **A** | Atmosphere | Not terrain: particles, fog, sound | Client effects, later | mist, spores, sandstorms, aurora |

River water opens a few doors here. It never spreads and its current can point any way, so a suspended pool, a river between floating islands, or a pillar of water with an upward current (an "upward waterfall") can be placed and will stay. A falling sheet can also stop in mid-air, with mist particles, for "waterfalls that evaporate before they reach the ground".

## 4. Placement engine (proposal)

- **Seamless and order-free, like macros.** Each feature type has a cell size (about 96–512 blocks) and a chance per cell. The anchor is jittered in its cell by a hash of (seed, type, cell). Any chunk computes the same features in any order.
- **A `MicroField` in the core**, cached in tiles like the drainage (`TileCache`). Each tile lists its feature instances with their bounding boxes. A column asks only for instances whose box covers it, and only shape and carve kinds are asked during column sampling.
- **Order in the column pipeline:** landform relief → macro → **micro shapes** → height clamp → rivers → **micro carves** (per block) → surface → **decorations and detail** (features step).
- **Footprints blend.** Every shape fades out with a smoothstep over its edge, and its strength fades in with its compatibility. Never a hard threshold (the lesson of the butte seam and the jebel that popped in).
- **Tag stack and commands.** A feature adds itself to the stack (`great_plateau → highlands → valley → tarn`). `/grandatlas where` shows it, and `/grandatlas locate <feature>` finds the nearest one.
- **Data-driven later.** Start in code, as the landforms did. Then a `grandatlas:micro_feature` datapack registry holding each feature's compatibility, requirements, spacing and parameters, so features can be tuned or added without a rebuild.
- **checkCore:** determinism, no seams at footprints, density per km² per type within its target, and nothing placed in a forbidden context.

## 5. First slice (proposal)

Ten features that exercise every kind once, all in vanilla blocks:

1. **Boulder fields** and mossy boulders (D): forests, alpine, plains.
2. **Fallen-log fields** (D): forests.
3. **Clearings** and wildflower clearings (D + U): forests, with trees held back.
4. **Tarns, ponds, kettle lakes and prairie potholes** (W + S): more lakes, the open question from 0.0.6.
5. **Sinkholes and cenotes** (S + C + W): jungle, karst, forest, plains.
6. **Natural arches and hoodoo clusters** (C), reusing the rock desert's carving.
7. **Rock outcrops, lone rocky hills and buttes** (S): plains, savanna, highlands.
8. **Cliff springs and small forest waterfalls** (W): river water can sit in a cliff face.
9. **Basalt columns and cinder cones** (S + D): a first taste of volcanic ground.
10. **Floating boulders** (F): the smallest step into the floating layer.

## 6. Catalogue

The outline's ideas, kept whole so nothing is lost, but an idea pool, not a to-do list. Marks: **✓** built (somewhere in Grand Atlas today), **A** buildable with what exists, **B** needs a new Grand Atlas system first (floating layer, lava, glaciers, underground water, ocean work, meanders), **C** needs new art or client effects. *(meso)* marks things bigger than a micro feature.

### Forest / woodland
- Forest clearings · D U · A
- Dense groves · D · A
- Ancient/giant tree groves · D · A *(meso)*
- Fallen-log fields · D · A
- Root-covered cliffs · D · A
- Mossy boulder fields · D U · A
- Fern gullies · S D · A
- Woodland ponds · W · A
- Seasonal pools · W U · A
- Forest streams · W · ✓
- Small waterfalls · W · ✓ (on steep streams); A for cliff springs
- Creek ravines · S · ✓ (streams cut their own ravines); A for deeper ones
- Exposed root caves · C D · A
- Hollow tree chambers · D · A
- Sinkholes · S C · A
- Rocky outcrops · S · A
- Mushroom rings · D · A
- Wildflower clearings · D U · A
- Natural tree arches · D · A
- Overgrown ruins · D · B (ruin-shaped stonework; structures stay vanilla's)
- Colossal hollow trees · D N · B
- Glowing mushroom groves · D N · B
- Bioluminescent ponds · W N · B
- Floating pollen/spore fields · A · C
- Enchanted springs · W N · B
- Giant root bridges · D · B
- Crystal-infested groves · D N · B
- Trees growing across/around floating rocks · F D · B
- Avatar-style glowing vegetation pockets · D N · B (Pandora's plants)

### Plains / grassland / savanna
- Rolling hills · S · ✓ (hills landform); A as micro hills
- Isolated rocky hills · S · A
- Small mesas · S · ✓ (dune outcrops); A elsewhere
- Dry creek beds · S U · A
- Seasonal streams · W · A
- Ponds · W · A
- Oxbow ponds · W · B (needs meanders)
- Prairie potholes · W S · A
- Wildflower fields · D U · A
- Tall-grass patches · D · A
- Boulder clusters · D · A
- Lone trees · D · A
- Small tree islands · D · A
- Shallow ravines · S · A
- Erosion gullies · S · A
- Termite-mound-like formations · D · A
- Natural stone circles · D · A
- Enormous flowers · D N · B
- Giant grass fields · D N · B
- Floating boulders · F · B (first slice)
- Glowing flower meadows · D N · B
- Crystal grasslands · D N · B
- Wind-carved levitating stone formations · F C · B
- Wandering patches of magical vegetation · D N · B

### Mountains / alpine
- Sharp ridges · S · ✓
- Knife-edge ridges · S · ✓
- Saddles · S · ✓ (emergent); A to shape them on purpose
- Mountain passes · S · ✓ (basin passes); A elsewhere
- Cirques · S · ✓
- Arêtes · S · ✓
- Horn peaks · S · ✓ (needle fields); A as a feature
- Talus slopes · U D · A
- Scree fields · U D · A
- Boulder fields · D · A
- Exposed cliff faces · S U · ✓ (strata)
- Rock spires · S C · ✓ (needles); A elsewhere
- Natural arches · C · A
- Alpine meadows · U D · A
- Alpine lakes/tarns · W S · A
- Hanging valleys · S W · ✓ (tributaries fall into big rivers at the bank); A as shaped valleys
- Snowfields · U · A
- Glaciers · S U · B *(meso)*
- Icefalls · S D · B
- Crevasses · C · B
- Avalanche chutes · S U · A
- Waterfalls · W · ✓
- Mountain springs · W · A
- Cave entrances · C · ✓ (vanilla's, damped)
- Floating peaks · F · B
- Inverted mountains hanging overhead · F · B
- Impossibly thin stone needles · S C · A
- Waterfalls flowing from floating rocks · F W · B
- Enormous crystals protruding from peaks · D N · B
- Glowing mountain caves · C N · B
- Shattered peaks with levitating fragments · F · B
- Mountain-top enchanted forests · D N · B *(meso)*

### Desert / badlands
- Sand dunes · S · ✓
- Star dunes · S · ✓
- Crescent/barchan dunes · S · A
- Giant dune ridges · S · ✓
- Sand sheets · S U · A
- Desert pavement · U · A
- Dry washes · S U · A
- Wadis · S U · A
- Slot canyons · C · ✓ (rock desert)
- Hoodoos · C · ✓ (rock desert towers); A elsewhere
- Rock towers · C · ✓
- Buttes · S · ✓ (canyon plateau)
- Small mesas · S · ✓
- Natural arches · C · ✓ (rock desert)
- Wind-carved rocks · C · ✓
- Yardangs · S C · A
- Desert caves · C · A
- Salt flats · U S · ✓
- Playas · S U · ✓
- Oasis pools · W · ✓ (rare, closed basins only); A as a feature
- Fossil beds · D · A (vanilla bone blocks)
- Petrified forests · D · A *(meso)*
- Glass dunes · U · A (vanilla glass)
- Crystal deserts · D U · A (amethyst, calcite, quartz)
- Enormous fossilized skeletons · D · A (bone blocks)
- Floating sandstone islands · F · B
- Sandfalls · A · C (sand can't fall forever)
- Permanently suspended sandstorms · A · C
- Glowing oasis · W N · B
- Fields of obsidian shards · D · A
- Enormous buried ruins partly exposed by erosion · D · B

### Jungle / tropical rainforest
- Jungle streams · W · ✓
- Waterfall pools · W · ✓
- Steep gullies · S · A
- Jungle ravines · S C · A
- Dense vine pockets · D · A
- Giant tree groves · D · A *(meso)*
- Buttress-root formations · D · A
- Bamboo pockets · D · A
- Swampy depressions · S W U · A
- Jungle ponds · W · A
- Sinkholes · S C · A
- Cenotes · S C W · A
- Limestone caves · C · A
- Karst towers · S C · A *(meso: a karst field)*
- Natural bridges · C · A
- Moss-covered cliffs · U · A
- Colossal flowers · D N · B
- Giant mushrooms · D · A (vanilla mushroom blocks, scaled up)
- Glowing jungle pools · W N · B
- Enormous vines forming bridges · D N · B (Pandora's vine bridges)
- Floating jungle rocks · F · B
- Avatar-style Hallelujah fragments · F · B (Pandora's islands)
- Trees growing between floating islands · F D · B
- Giant carnivorous plant fields · D N · C

### Swamp / wetland / mangrove
- Mud flats · U · A
- Shallow pools · W · A
- Peat islands · S U · A
- Raised bogs · S U · A
- Channels · W · B
- Braided waterways · W · B
- Oxbow lakes · W · B
- Cypress-like tree islands · D · A
- Mangrove root labyrinths · D · A
- Reed beds · D · A
- Dead forests · D · A
- Flooded forests · W D · A *(meso)*
- Small sinkholes · S C · A
- Mud springs · W U · A
- Glowing bogs · W N · B
- Enormous lily pads · D N · B
- Bioluminescent reeds · D N · B
- Fog-producing pools · W A · C
- Floating vegetation islands · W D · A (river water holds still around them)
- Drowned ruins · D · B
- Giant twisted trees · D · A
- Bubbling magical tar/mud pits · W N A · C
- Ghost forests · D · A

### Rivers / lakes
These cross every biome, as the drainage network already does.
- Rapids · W · ✓ (riffles)
- Waterfalls · W · ✓
- Cascades · W · ✓
- Plunge pools · W · ✓
- River islands · W S · B
- Gravel bars · W U · A
- Sandbars · W U · A
- Braided channels · W · B
- Meanders · W · B (courses are routed and smooth, but don't loop yet)
- Oxbow lakes · W · B
- River cliffs · S · A (cut banks on outer bends)
- Floodplains · S · ✓
- Natural levees · S · A (a low ridge shaped into the ground before rivers, not built up after)
- Tributary junctions · W · ✓
- Springs · W · A
- Underground river entrances · W C · B
- River caves · W C · B
- Lake islands · W S · A
- Rocky lake shores · U D · A
- Beaches · U · A
- Marshy lake edges · U D · A
- Glowing rivers · W N · B
- Rivers flowing through floating terrain · F W · B
- Upward waterfalls · W · B (river water with an upward current)
- Suspended pools · W · B (river water doesn't spread)
- Enormous waterfall curtains · W · A (a lip across a whole cliff)
- Rivers disappearing into massive sinkholes · S W · B
- Magical springs that alter nearby vegetation · W D N · B

### Coast / ocean
- Sea cliffs · S · A
- Sea caves · C · A
- Sea arches · C · A
- Sea stacks · S C · A
- Blowholes · C A · C
- Wave-cut platforms · S · A
- Beaches · U · ✓
- Pebble beaches · U · A
- Barrier islands · S · B (ocean work, M3)
- Lagoons · S W · B
- Sand spits · S · B
- Tidal flats · S U · A
- Coastal dunes · S · A
- Estuaries · W S · B (still to do in §8)
- Deltas · W S · B (still to do in §8)
- Mangrove islands · D S · A
- Coral reefs · D · A (vanilla coral)
- Atolls · S D · B
- Underwater canyons · S · B
- Seamounts · S · B
- Hydrothermal vents · D · A (magma blocks and bubble columns)
- Kelp forests · D · A (vanilla kelp)
- Blue holes · S · A
- Enormous coral forests · D · A
- Underwater crystal fields · D N · B
- Submerged ruins · D · B
- Colossal skeletons · D · A (bone blocks)
- Glowing abyssal trenches · S N · B
- Underwater waterfalls · A · C
- Floating islands just offshore · F · B
- Gigantic stone arches spanning ocean channels · C · A

### Snow / tundra / polar
- Snowdrifts · U D · A
- Frozen ponds · W · A
- Frozen waterfalls · D · A (ice columns down a cliff)
- Ice caves · C D · A
- Ice cliffs · S U · A
- Glacial valleys · S · A *(meso)*
- Moraines · S · A
- Eskers · S · A
- Kettle lakes · W S · A
- Permafrost cracks · C · A (a fissure variant)
- Snow-covered boulder fields · D · A
- Frozen rivers · W · ✓
- Icebergs · D · A
- Pressure ridges · S D · B
- Ice shelves · S · B
- Enormous ice crystals · D · A (packed and blue ice)
- Floating ice islands · F · B
- Glowing blue ice caverns · C N · B
- Frozen forests encased in ice · D · A
- Gigantic icicle caves · C D · A
- Aurora-lit vegetation · D N A · C
- Shattered levitating glaciers · F · B

### Volcanic
- Lava tubes · C · A (empty) / B (with lava)
- Lava caves · C · A
- Lava channels · W · B (needs lava placed like river water)
- Lava falls · W · B
- Craters · S · A
- Small calderas · S · A (a big one is the caldera macro)
- Fumaroles · D A · A (blocks) / C (steam)
- Hot springs · W U · A (water colour needs a sub-biome)
- Geysers · D · C (needs an eruption mechanic)
- Obsidian fields · U D · A
- Basalt columns · S D · A
- Ash dunes · S U · A
- Cinder cones · S · A
- Cracked lava fields · U D · A
- Sulfur deposits · U · C (no sulfur block in vanilla)
- Volcanic vents · D · A
- Black-sand beaches · U · C (no black sand in vanilla; blackstone or tuff stand in)
- Enormous lava waterfalls · W · B
- Floating volcanic rocks · F · B
- Glowing obsidian crystals · D N · B
- Permanently burning fissures · C D · A (fire on netherrack in a crack)
- Lava geysers · D · C
- Suspended lava blobs · F · C
- Volcanic caves with their own ecosystems · C D · B *(meso)*

### Caves / underground
Underground features follow the cave zones of DESIGN §11, not the surface biome.
- Caverns · C · ✓
- Narrow fissures · C · ✓
- Vertical shafts · C · ✓ (pits)
- Underground rivers · W C · B
- Underground lakes · W · A (vanilla aquifers make some)
- Aquifers · W · ✓ (vanilla)
- Waterfalls · W · B
- Lava tubes · C · A
- Crystal caves · C D · A (amethyst and calcite)
- Dripstone chambers · D · ✓ (vanilla)
- Cave columns · C · A
- Cave arches · C · A
- Breakdown chambers · C D · A
- Subterranean canyons · C · A
- Sinkholes · S C · A
- Cenotes · S C W · A
- Glowworm-style caves · D · A (glow lichen and glow berries); C for real glowworms
- Geode chambers · D · ✓ (vanilla geodes); A for big ones
- Fossil chambers · D · A
- Root-filled caves · D · A (hanging roots, rooted dirt)
- Ice caves · C D · A
- Magma chambers · C · B
- Colossal crystal forests · D N · B
- Underground jungles · D · B *(meso)*
- Glowing mushroom forests · D N · B
- Enormous underground oceans · C W · B *(meso)*
- Floating cave islands · C F · B
- Inverted forests hanging from ceilings · D N · B
- Underground clouds/fog · A · C
- Giant glowing roots · D N · B
- Gravity-defying waterfalls · W · B (river water)
- Enormous abyssal pits · C · A
- Hidden valleys under collapsed cave roofs · C S · A *(meso)*

### Magical / enchanted
The outline suggests enchanted biomes get their own feature pool instead of reskinned ordinary terrain.
- Crystal clusters · D · A (amethyst) / B (new crystals)
- Crystal spires · D N · B
- Fairy rings · D · A
- Magical springs · W N · B
- Glowing streams · W N · B
- Glowing ponds · W N · B
- Giant flowers · D N · B
- Giant mushrooms · D · A
- Ancient trees · D · A
- Root arches · D · A
- Floating stones · F · B
- Levitation fields · A · C (a levitation effect zone)
- Tiny floating islands · F · B
- Mist pockets · A · C
- Permanent rainbow/mist areas · A · C
- Magical geysers · D A · C
- Rune-covered stones · D N · C
- Gravity anomalies · A · C
- Upside-down waterfalls · W · B (river water)
- Suspended rivers · W F · B
- Shattered floating terrain · F · B
- Enormous glowing trees · D N · B
- Crystalline forests · D N · B
- Vegetation growing upside down under floating terrain · F D · B

### Corrupted / dark fantasy
- Dead tree groves · D · A
- Dead grass patches · U · A
- Cracked earth · U C · A
- Black ponds · W · A (water colour needs a sub-biome)
- Tar pits · W N · C
- Bone fields · D · A
- Dead rivers · W · A (dry beds the drainage already knows)
- Jagged rocks · S C · A
- Sinkholes · S C · A
- Ash fields · U · A
- Thorn forests · D N · B
- Dead root networks · D · A
- Enormous skeletons · D · A (bone blocks)
- Corrupted crystal growths · D N · B
- Red/black rivers · W N · B
- Giant rib cages forming arches · D · A (bone blocks)
- Floating terrain fragments · F · B
- Bottomless-looking fissures · C · ✓ (fissures); A deeper
- Petrified giant trees · D · A
- Terrain seemingly consumed by an expanding corruption · U D · B *(meso)*

### High-fantasy floating terrain
The outline gives floating terrain its biggest pool. All of it waits on the floating layer (M6), except what river water makes possible.
- Small floating rocks · F · B
- Boulder clusters · F · B
- Floating stone arches · F C · B
- Floating waterfalls · F W · B
- Hanging vines · F D · B
- Hanging forests · F D · B
- Hanging roots · F D · B
- Suspended ponds · F W · B
- Small satellite islands · F · B
- Island chains · F · B *(meso)*
- Broken island fragments · F · B
- Thin bridges between islands · F D · B
- Waterfall-fed lakes · F W · B
- Cloud forests · F D A · C
- Inverted terrain under islands · F · B (the macro "underside" of DESIGN §6.3)
- Giant crystals anchoring floating islands · F D N · B
- Floating rivers jumping between islands · F W · B
- Islands connected by enormous tree roots · F D · B
- Waterfalls evaporating into mist before the ground · W A · B (river water stops mid-air) / C (mist)
- Levitating mountain fragments orbiting larger islands · F · C (they'd have to move)

## 6b. Build plan (the user approved the chain on 2026-11-02; built in 0.0.7, extended in 0.0.8-0.0.9)

The user likes the Macro → Meso → Micro → Detail chain and wants it built with real features to see in-world. **Built in 0.0.7 as below** (`core/micro`, `world/MicroDetail`; 0.0.9 adds butte fields, tor hills, tarn basins and erratic fields, with butte, tor and erratic micro features), with these changes: cells are 800 blocks (a multiple of 16, so a chunk lies in one site), sites are judged on `sampleBase` (no rivers, no features) and features apply only in full samples, and every feature's reach stays inside its site. In the dev world the pond and cenote water has 0 open faces. Plan as worked out:

- **Package `core/micro`.** It holds `MesoKind`, `MesoSite` and `MicroFeature` instances, and a tiled `Features` field (jittered cells of about 640 blocks).
  - Name it `features` in `AtlasCore`: the field `micro` is already the micro-relief noise seed.
  - Sites are judged at their centre on a **raw** sample: no rivers and no micro features. This needs a third `sample(x, z, rivers, micro)` mode, or tile building recurses through `sampleBase`.
- **Meso sites:**
  - pond country: humid lowlands, hills and highlands;
  - sinkhole field, with cenotes where it's humid;
  - hoodoo garden: dry country;
  - cinder field;
  - glade: forests, where the inner radius gets the MEADOW or FLOWER_FOREST biome, so there are no trees (removing trees after the fact is unreliable across chunks).
- **Positive shapes** (hoodoos, arch fins, cinder cones) go into `AtlasCore.sample` right after the height clamp (line ~269), before rivers.
  - Thin ones need exactness. Use an envelope: `uncut` = the shape dilated by 6 blocks at full height (the interpolated part), and `height` = the exact shape. `AtlasCore.riverCut` (rename it `exactCut`) then trims to the exact shape per block.
  - Hoodoo necks under the caprock and arch holes are 3D carves added to `GrandCaves` FINE, next to `riverCut`.
- **Depressions** (ponds, sinkholes, cenotes) go after rivers, so they're exact through the cut.
  - Each instance is validated lazily on first use: no river carve over its footprint (call `drainage.carve` at about 9 points), and gentle ground for ponds (ring spread ≤ 6).
  - A pond's level is the minimum of the raw ground over 3 rings × 24 angles (0.9 to 1.3 r), minus 1.
  - A cenote's bell undercut stays above its water + 2, or `PerchedWater`'s bank plugs fill it.
  - Micro water keeps the landform biome: only drainage water makes the River biome.
- **`ColumnSample`** gets `meso` and `micro` fields, so `tags()` and `/grandatlas where` show them; `locate` learns the site kinds.
- **Detail** is a new feature, `grandatlas:micro_detail`, at the `local_modifications` step (after `perched_water`). Each chunk places only its own slice of each object, from the core height, with air filled down under boulders. It covers:
  - mossy boulders and fallen logs;
  - lily pads and sugar cane by ponds;
  - vines, moss and glow lichen in sinkholes;
  - basalt column clusters, and blackstone, tuff, gravel and terracotta ground on cinder cones.
- **checkCore:** determinism, the site count per kind on the dev seed, and no site in a forbidden context.

## 7. Open questions for the user

- Which of these do you actually want? The list is a loose brainstorm, and the first slice in §5 is only a proposal.
- Do you want the meso level and the weighted compatibility at all, or something simpler? Both were the outline's suggestions.
- Vanilla blocks only for the first slice, or bring Pandora's glow blocks and plants in early?
- Should meso sites be visible in `/grandatlas where` and `locate` like macros (`locate tarn`, `locate karst_field`)?

## 8. Additional worldgen requirements and reference features

The following features are additional requirements and design references for Grand Atlas. They are inspired by WWOO, Speleogenesis Overhaul, Larion World Generation, Outflow, and Ecospherical Depths. These are behavioral references only: Grand Atlas should implement its own systems and should not depend on or copy code from those projects.

These additions do not replace or invalidate any earlier section of this document.

**Status (user, 2026-11-03):** this section is a ChatGPT expansion and, like the rest, a loose outline, with one exception: **the Y=-512 floor (8.2) is the user's own idea and a real requirement.** Order agreed: the Macro → Meso → Micro → Detail slice (§6b) first; the underground (Y=-512, connected cave geography, 3D cave biomes) is its own milestone after it (DESIGN §16, M7).

### 8.1 Biome variation and transitions — WWOO reference

- Support **sub-biomes** and internal biome variants so a large biome does not remain visually or geographically uniform throughout its entire area.
- Support **transition regions** between compatible biomes instead of requiring every biome boundary to be abrupt.
- Preserve the ability for geology to create intentionally abrupt boundaries where appropriate, such as a forest ending against a major cliff or elevated desert plateau.
- Allow certain biome combinations to produce distinctive transition environments rather than only interpolating between the two parent biomes.
- Surface materials should react to terrain slope. Steep and near-vertical terrain should expose appropriate rock rather than carrying normal soil/grass surfaces unrealistically down cliffs.
- Geological materials may vary regionally so different areas can develop recognizable geological character.

Example transition concepts:

- Forest -> open woodland -> dry woodland -> scrub -> desert
- Forest + wetland -> flooded woodland
- Jungle + mountains -> tepui-like terrain
- Ocean + mountains -> fjord or cliff coast

### 8.2 Connected underground geography — Speleogenesis reference

Underground generation should be capable of forming coherent geographic systems rather than only independent noise-carved caves.

Desired features include:

- large connected cave networks
- major galleries and secondary passages
- mega-caverns
- long horizontal cave systems
- enormous vertical rifts and shafts
- underground rivers
- underground lakes
- surface-connected cave systems
- coastal and underwater cave entrances
- sea caves and flooded cave passages
- sinkholes and cenotes connecting surface and underground terrain

Mega-caverns should sometimes be large enough to behave as complete underground landscapes, containing combinations of:

- cliffs
- hills
- islands
- rivers
- lakes
- waterfalls
- forests or fungal ecosystems
- structures
- multiple cave biomes
- vertically separated ecosystems

Surface geology and underground geography should be related where appropriate.

Examples:

**Karst region**
-> sinkholes
-> disappearing streams
-> limestone cave network
-> underground river
-> cenotes
-> resurgence spring

**Volcanic region**
-> lava tubes
-> volcanic caverns
-> magma regions
-> collapsed chambers

**Glacial region**
-> ice caves
-> meltwater passages
-> deep glacial fissures

#### HARD REQUIREMENT — minimum build height Y=-512

Grand Atlas MUST expand the world's lower build limit to **Y=-512**.

This is a hard project requirement, not an optional target or future possibility.

The additional underground range must be treated as usable world-generation space. It must NOT simply become hundreds of additional blocks of homogeneous stone below otherwise vanilla-scale caves.

The expanded depth exists to support a substantially larger underground world, including:

- extremely deep cave systems
- multi-level cave networks
- mega-caverns
- underground terrain with mountain-like scale
- enormous vertical rifts
- deep underground rivers
- underground lakes
- subterranean seas
- large aquifers
- crystal caverns
- fungal forests
- subterranean jungles
- volcanic and magma regions
- abyssal environments
- geological strata
- deep cave-biome transitions
- extremely deep sinkholes
- cave systems spanning hundreds of vertical blocks

Systems controlled by Grand Atlas must not assume vanilla's Y=-64 lower boundary. The expanded vertical range must be respected by terrain generation, caves, biome placement, hydrology, geological layers, features, structures where applicable, and other worldgen systems.

A single connected underground system may span hundreds of blocks vertically, for example:

Surface sinkhole
-> upper cave network
-> vertical rift
-> underground river
-> mega-cavern
-> deep waterfall
-> subterranean lake
-> lower cave network
-> abyssal cavern approaching Y=-512

Rare underground spaces should feel less like ordinary Minecraft caves and more like **subterranean landscapes**.

The underground is not leftover space beneath the surface world. It is a second major terrain domain within the same dimension.

### 8.3 Large-scale terrain and river behavior — Larion reference

- Terrain should make meaningful use of Grand Atlas's expanded vertical range rather than simply stretching vanilla terrain upward.
- Support genuinely large oceans separating landmasses.
- Land should occur at many scales: small islands, island chains, archipelagos, large islands, subcontinents, and continents.
- Climate should form coherent large-scale regions rather than appearing entirely as small random blobs.
- Terrain character should vary with erosion-like regional parameters, allowing sharp mountains, hills, rolling terrain, plains, heavily dissected terrain, and other morphologies.
- Rivers should be long-lived geographic systems rather than short disconnected fragments.
- Major rivers should be capable of collecting tributaries and crossing multiple terrain and biome regions.
- River morphology should react to surrounding terrain.

Example river progression:

Mountain source
-> alpine stream
-> V-shaped mountain valley
-> rapids
-> waterfall
-> canyon
-> broad river
-> meandering floodplain
-> delta
-> ocean

- Rivers may occasionally travel underground where the surrounding geology makes that appropriate.
- Terrain should support irregular cliffs, overhangs, distorted ridges, asymmetric mountains, unusual valleys, and other non-extruded silhouettes.

### 8.4 Advanced hydrology — Outflow reference

Water must not be conceptually restricted to sea level.

Natural lakes should be capable of existing at any appropriate elevation, including:

- alpine tarns
- cirque lakes
- high plateau lakes
- valley lakes
- crater lakes
- karst lakes
- lowland lakes

Lakes should have meaningful terrain relationships rather than behaving as flat water inserted into arbitrary holes.

Desired hydrological behavior includes:

- drainage basins
- natural lake basins
- meaningful lake outlets
- rivers entering and leaving lakes
- multiple water elevations within the same region
- lake bathymetry
- shallow shores and deeper basins
- terrain-specific lake shapes
- connected river/lake systems

Elevated hydrology should naturally allow major waterfalls.

Example:

High plateau
-> elevated lake
-> outlet river
-> plateau edge
-> enormous waterfall
-> plunge pool
-> lower river system

A mountain lake at high elevation should not need to be lowered to global sea level simply because it contains water.

### 8.5 Three-dimensional cave biomes — Ecospherical Depths reference

Underground biome generation must support meaningful **X/Y/Z variation**.

Large caverns should be capable of containing multiple environmental regions at different heights and positions rather than assigning one biome to the entire cavern.

Example:

Cavern ceiling
-> hanging vegetation / moss ecosystem

Upper walls
-> crystal environment

Lower walls
-> dripstone environment

Cavern floor
-> underground forest

Lowest basin
-> subterranean lake

Potential underground biome/environment families include:

- crystal / geode caverns
- molten caverns
- lush caverns
- frozen caverns
- deep caverns
- tropical caverns
- submerged caverns
- glacial caverns
- dripstone caverns
- glowworm caves
- giant mushroom forests
- root caverns
- underground jungles
- quartz crystal caves
- abyssal caverns
- underground oceans
- fossil caverns
- volcanic chambers
- salt caves
- sulfur caves
- giant geodes
- ancient underground forests
- fantasy crystal forests

Different underground environments should also be capable of meeting and producing unusual transition zones.

Example:

Frozen cave + geothermal/volcanic region
-> ice cavern containing geothermal pools, meltwater, steam, and nearby lava features

### 8.6 Combined worldgen behavior

These systems should interact with the existing Macro -> Meso -> Micro -> Detail hierarchy rather than behaving as unrelated generators.

A possible generated stack could look like:

Continent
-> Great Plateau macro
-> alpine province
-> mountain cirque
-> alpine forest biome
-> meadow sub-biome
-> tarn
-> river outlet
-> plateau waterfall
-> downstream valley

while beneath the same region:

Karst geology
-> sinkhole
-> cave network
-> underground river
-> mega-cavern
-> ceiling biome
-> wall biome
-> floor biome
-> deep lake
-> lower cave network

The overall design principle is:

**Geography should create and influence other geography.**

Mountains create watersheds. Watersheds create rivers. Rivers create valleys and canyons. Glacial terrain creates cirques and U-shaped valleys. Karst terrain creates sinkholes and underground drainage. Plateaus enable elevated rivers, lakes and waterfalls. Surface geology influences caves. Climate influences biomes. Biomes influence vegetation and surface material. Slope influences exposed geology.

These relationships should help the world feel like one connected landscape rather than a collection of unrelated procedural features.
