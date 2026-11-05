# M7: the underground (plan)

Agreed approach (user, 2026-11-03): the underground gets the surface's treatment, connected cave systems you can follow, composed to look epic. The floor drops from Y -128 to **Y -512** (the user's hard requirement). It is built in a hierarchy: **macros** (kilometres), **mesos** (100-500 blocks), **micros** (1-50 blocks), then block detail.

**Direction (user, 2026-11-03):** the underground is where Grand Atlas goes properly fantastical. Push the boundaries. Real cave geology is the starting point, not the limit.

**Rule: the deeper, the stranger.** The upper caves are mostly real kinds, so the world above stays believable. The deep mixes the real with the strange. The abyss is mostly fantastical. Going down should feel like leaving the known world.

## Depth bands

| Band | Depth | Character |
|---|---|---|
| Surface shell | top ~40 blocks | Cave mouths, sinkholes, cenotes, pits, fissures, sea caves: the ways in. |
| Upper caves | down to ~Y 0 | Tunnel networks: passages, stream caves, karst galleries. Mostly real. |
| The deep | Y 0 to -250 | Mega-caverns, rifts, underground rivers and lakes. Real and strange mixed. |
| The abyss | Y -250 to -500 | The strangest places: inverted lakes, titan bones, the Hollow. Lava floor near -500. |

Rock changes with depth: stone, deepslate from ~Y 0, then tuff, basalt and blackstone strata in the abyss.

## How shapes are made

1. **Passage networks.** A 3D lattice of junctions ~100 blocks apart, a few layers deep, linked into a connected network (trunks and branches) that slopes toward drainage points, like the surface river network. Each link is a smooth curve; blocks within its radius are carved. Cross-sections: **tubes** (round/oval 4-12), **canyon passages** (keyhole: a tube with a tall slot cut under it), **crawls** (pinches), **collapse chambers** at junctions.
2. **Mega-caverns.** Placed like surface macros: an egg-shaped void 200-800 wide and 60-200 tall. The floor is a terrain map (hills, islands, lakes, rivers); the ceiling is an inverted terrain map (domes, hanging spires); pillars join them where they come close.
3. **Rifts.** Vertical cracks 2-8 wide and 100-400 deep along a winding line, linking the bands; some open at the surface.
4. **Bores.** Perfectly round tunnels 20-40 wide that wind and spiral down through the bands (the worm bores below). Each is a spline with a constant radius.
5. **Background texture.** Vanilla's cave noise, retuned, as small side caves between the designed ones.

Water: the existing non-spreading river water runs in the lowest passages, falls between levels, and fills cavern lakes and the underground sea. Surface water drains in through cenotes and sinkholes. Because it never spreads, it can also do things vanilla water can't: **hang from a ceiling as an inverted lake** or stand as a wall. Water that **falls upward** uses bubble columns. Each column keeps a short list of the passages and caverns near it, so most blocks are "nothing here" at once (speed).

Atmosphere: biomes can set their own fog colour, ambient particles (drifting spores, falling ash, glowing motes) and sounds. So a cavern can feel like its own world, not just look like one.

New blocks (glow threads, selenite, amber, luminous water and so on) need textures. I'd make those myself; any outside pack only with the user's approval.

## Cave biomes

Picked in 3D from the depth band, a regional noise, and the land above. *Italics* mark the fantastical ones.

### Upper caves (to ~Y 0): mostly real

| Biome | Where | Looks like |
|---|---|---|
| Lush caves (vanilla) | humid land | Moss, dripleaf, glow berries, azalea. |
| Dripstone caves (vanilla) | dry limestone | Dripstone spikes and columns. |
| Root caves | under **any forest** | Tree roots hanging through the ceiling and gripping pillars, matching the trees above (oak, spruce, birch, jungle, mangrove, cherry, acacia). Under ancient groves, giant gnarled ones. |
| Glowworm grotto | wet limestone, karst | A river through a gallery whose ceiling hangs with glowing threads. |
| Moss halls | humid temperate land | Moss carpets, clay banks, ferns in skylights, dripping water. |
| Ice caves | under glaciers, cold mountains | Blue and packed ice walls, frozen waterfalls, icicles, a blue glow. |
| Sandstone caves | under deserts | Smooth sandstone, wind-cut hollows, sand drifts, buried fossils. |
| Salt caverns | under playas | White salt walls, salt crystals, brine pools. |
| Sea grottos | coasts | Flooded caves open to the sea, sand floors, kelp, light shafts from the water. |
| Copper galleries | wet ore country | Walls streaked green-blue with oxidised copper. |
| *Starlit grotto* | anywhere, rare | A ceiling dotted with thousands of tiny glowing crystals like a night sky, mirrored in a still black pool. |
| *Honeycomb caves* | under flower meadows | Walls of hexagonal wax cells, honey seeping down them, a warm amber light. |
| *Hanging gardens* | under skylights in warm land | Terraces of moss, vines and flowers under a shaft of daylight, water trickling down each tier. |

### The deep (Y 0 to -250): real and strange

| Biome | Where | Looks like |
|---|---|---|
| Fungal forest | humid caverns | Giant mushroom canopies, mycelium, glowing fungi, spore haze. |
| Underground jungle | caverns under jungle | Vines, hanging roots, ferns, dense green growth round a light shaft. |
| Crystal caves | hot, under volcanic or desert land | Selenite beams 10-50 long crossing the space (Naica). |
| Geode halls | scattered | Whole rooms lined in amethyst. |
| Cave marsh | wet caverns | Mud, shallow pools, dripleaf, glow lichen. |
| Flowstone halls | limestone | Rimstone pools stepping down, curtains, pale calcite. |
| Hot spring terraces | under volcanic land | Steaming pools ringed in orange, yellow and green mineral bands (Grand Prismatic, underground). |
| Sulphur caves | near magma | Yellow crystal crusts, steam vents, acid-green pools. |
| *Inverted forest* | big caverns | Trees growing down from the ceiling, trunks in the rock, canopies hanging into the open air. |
| *Petrified forest* | sedimentary rock | A whole forest turned to stone: trunks of calcite and granite, branches you can climb. |
| *Amber halls* | under ancient forests | Walls of glowing amber with things trapped inside: leaves, bones, whole old trees. |
| *Mushroom canopy sea* | humid caverns | Giant mushroom caps so dense they make a floor you walk across, with a dark drop below. |
| *Glowsand desert* | dry caverns | Dunes of faintly glowing sand under a ceiling, a desert with no sky. |
| *Fossil reef* | under old sea floors | An ancient reef left dry: coral-shaped stone that still glows, shell beds, the bones of sea creatures. |
| *Prism caves* | crystal country | Coloured translucent crystal that tints the light passing through it. |

### The abyss (Y -250 to -500): mostly fantastical

| Biome | Where | Looks like |
|---|---|---|
| Deep dark (vanilla) | under high land | Sculk; ancient cities. |
| Magma chambers | under volcanoes | Lava lakes and falls, basalt columns, red glow. |
| Abyssal shores | round an underground sea | Black tuff and basalt beaches, glowing life in the water. |
| *Inverted lakes* | big caverns | Lakes hanging from the ceiling, their surfaces facing down. You can swim up into them. |
| *Rising falls* | wet caverns | Waterfalls that pour upward from floor pools into the ceiling. |
| *Mirror halls* | volcanic rock | Walls of black volcanic glass, polished, reflecting the lava glow. |
| *Frozen fire* | old magma chambers | Lava falls caught mid-fall and turned to obsidian and basalt, their cores still glowing. |
| *Fire under ice* | deep under cold land | A frozen lake over a magma chamber, lava glowing up through blue ice. |
| *Ash wastes* | deepest rock | Grey ash dunes, dead petrified trees, ash drifting down through the air. |
| *Void halls* | very deep | Chambers with no visible floor, stone bridges across darkness, pillars rising from nothing. |
| *Underground sky* | the largest caverns | A ceiling so high and so lit with glowing crystal that it reads as a sky, with fog banks drifting like cloud. |
| *Crystal spine forest* | rare | Fields of tall crystal spires. |
| *The Hollow* (set piece) | rare | A vast cavern with floating stone islands, waterfalls pouring off them into the dark. |

## Macros (kilometres)

| Macro | What |
|---|---|
| Mega-cavern | An underground landscape: hills, cliffs, lakes, rivers, forests of mushrooms or roots. |
| Underground sea | A cavern 1-3 km across in the abyss, mostly water, with islands and shores. Sometimes a volcano island in the middle. |
| Karst underworld | Under karst country: a dense maze of galleries, streams and sinkholes. |
| Great rift | A canyon kilometres long and 300+ deep, crossed by natural arches, waterfalls down its walls. |
| Magma province | Under volcanic land: magma chambers, lava rivers, basalt halls. |
| Glacier underworld | Under cold mountains: ice-cave networks with frozen falls and blue halls. |
| Labyrinth | A region of dense small passages, easy to get lost in. |
| Abyss shaft | A pit 50-150 wide dropping from the surface to about -400 (Sótano de las Golondrinas). |
| *The Hollow* | The floating-island cavern. |
| *Titan's grave* | The skeleton of something kilometres long: its ribs arch over a cavern as a vaulted hall, its spine is a ridge you can walk, its skull is a cavern of its own. |
| *The Worldroot* | One colossal tree on the surface whose roots descend hundreds of blocks: roots as walkways and bridges through a cavern, glowing sap. |
| *The Great Geode* | A hollow crystal sphere 300-600 across, entered through a crack in its shell. |
| *Hanging mountains* | A cavern whose ceiling hangs a range of mountains upside down, ice at their tips, over a lake that mirrors them. |
| *Underground sky* | A cavern so big it has its own lit sky and drifting fog, a landscape under it. |
| *Wyrm warren* | A network of giant worm bores spiralling down through every band, glassy walls, side chambers. |
| *Drowned forest* | A flooded abyss cavern full of petrified trees, the water glowing faintly. |

## Mesos (100-500 blocks)

**Real:**
- **Grand gallery:** a long hall 30-80 tall.
- **Collapse chamber:** boulder hills under a high dome.
- **Cave lake chamber:** a still lake, maybe with an island.
- **Waterfall shaft:** a river dropping down a vertical shaft into a pool.
- **Geode or crystal chamber.**
- **Fungal grove:** a clutch of giant mushrooms.
- **Column hall:** a forest of stalagmites and columns.
- **Flowstone cascade:** rimstone pools stepping down a slope (an underground Kuang Si).
- **Cenote connection:** a sinkhole from the surface opening into a flooded cave below.
- **Ice palace:** blue ice and frozen falls.
- **Lava lake chamber.**
- **Fossil wall:** a cliff with a giant skeleton in it.
- **Pillar forest:** huge floor-to-ceiling columns.
- **Flooded maze:** water-filled passages for diving.
- **Skylight cavern:** daylight through a hole in the roof, trees growing in the light.

**Fantastical:**
- **Inverted lake chamber:** a lake on the ceiling, with a rising fall feeding it from a pool below.
- **Rising falls shaft:** water pouring up a shaft to the level above.
- **Amber vault:** a chamber of amber with one great thing trapped in its centre.
- **Prismatic springs:** stepped hot pools in rings of colour.
- **Frozen fire cascade:** a petrified lava fall down a cliff.
- **Petrified grove:** stone trees round a clearing.
- **Bone bridge:** a giant rib spanning a chasm.
- **Starlit pool:** the starlit ceiling over a mirror lake.
- **Floating boulder field:** stones hanging in the air at different heights, some big enough to stand on.
- **Crystal bridge:** a single crystal beam spanning a rift.
- **Hanging garden terraces.**
- **Canopy crossing:** a route across giant mushroom caps over a drop.
- **Spiral shaft:** a perfect helix carved down through the rock.
- **Lava moat island:** a stone island in a lava lake, something growing on it.
- **Mirror hall.**
- **Luminous pool:** water that glows from beneath.
- **Root cathedral:** the Worldroot's roots vaulting a chamber like a nave.

## Micros (1-50 blocks)

**Real:**
- Stalactites, stalagmites and columns at scales vanilla doesn't reach (up to 30 tall).
- Rimstone pools, flowstone curtains.
- Crystal beams and clusters.
- Glowworm threads, glowing fungi, glow lichen.
- Giant mushrooms, hanging roots, cave vines.
- Ice stalactites, frozen waterfalls.
- Basalt columns, lava falls, obsidian crusts.
- Boulder chokes, breakdown piles.
- Natural bridges and arches.
- Skylights, springs, seeps.
- Fossils, exposed ore veins.
- Mud pots, brine pools.

**Fantastical:**
- Floating stones, single rocks hanging in the air.
- Upside-down trees, small ones on cavern ceilings.
- Upward drips: thin threads of water rising.
- Amber nodules with things inside.
- Bone arches and half-buried giant skulls.
- Obsidian teardrops: frozen drops of lava.
- Glow-tipped basalt columns.
- Spore lanterns: glowing bulbs hanging from fungi.
- Crystal flowers.
- Glowing veins running through the walls.
- Luminous puddles.
- Drifting motes, spores and ash (biome particles).

## Structures

There are two kinds, handled differently.

- **Natural set pieces are terrain, not structures.** Titan's ribs, the Worldroot, the Great Geode, amber vaults, floating stones and bone bridges are all built from shapes (curves, spheres, splines) like the calderas, falls and giant trees already are. None of them is a building.
- **Buildings and ruins are never designed by the generator.** Generated architecture looked bad before (the Pandora Wilds facility and pit mine were dropped). Buildings come from three places:
  1. **Vanilla:** ancient cities, trial chambers, mineshafts, strongholds. The terrain already passes through vanilla's beardifier, so structures get ground filled under them and space cleared round them. M7 work:
     - The new caves must not cut through structure boxes.
     - Ancient cities and trial chambers get new depths through data overrides. **Decided (user, 2026-11-03): ancient cities move into the abyss**, around Y -350, in deep dark country. Trial chambers go in the deep. This lands with M7a, since those depths don't exist until the floor drops.
     - Ancient cities can be placed on purpose, for example on a mega-cavern floor, not at random.
  2. **Structure mods the user picks** (the user approves each one). The terrain must sit right with them.
  3. **Custom builds:** built by hand in game and saved as structure files. The mod only handles placement: where they go and how they fit a cavern, a cliff or a shore.

Checks: a scan for structures that are cut open, floating or flooded; the tour finds them.

## Notes: where vanilla's caves get their chaos

From the 26.3 sources (`levelgen/carver/CaveWorldCarver`, the overworld cave density functions), after the user's 3D-survey notes (2026-11-03): the first M7b passages were too regular. Every link had the same S-bend, the bores were clean helices, and the canyon slot made a fin under every tunnel.

**Carvers (worm caves).**
- **Correlated random walk.** Each step turns by a turning speed. That speed takes a random kick, (r1 - r2) * r3 * 4, and decays (x0.75 for yaw, x0.9 for pitch). The result is smooth curves that never repeat.
- **Pitch.** It decays toward level (x0.7), except in one tunnel in six, which keeps diving (x0.92).
- **Thickness.** It follows sin(pi * step / length): fat in the middle, tapering to nothing at both ends. One tunnel in ten is 1-4x thicker ("weird thickness bias").
- **Forks.** A tunnel forks once at a random point between 1/4 and 3/4 of its length: two children at +-90 degrees, 0.5-1x as thick.
- **Rooms.** One cave in four starts from a room (vertical squash 0.1-0.9) with 1-4 tunnels at random headings.
- **Per-cave multipliers.** Width 0.7-1.4, height 0.8-1.3, and a floor level from -1 to -0.4 (how flat the floor is).
- **Gaps.** One step in four carves nothing, so the overlapping ellipsoids leave lumpy walls.
- **Clumping.** Caves per chunk come from a very-biased-to-bottom 0-14 count with a 15% chance, so caves clump.

**Noise caves.**
- **Spaghetti.** Places where two noise fields both cross zero form tubes along the intersection of two surfaces, so no shape repeats.
- **Variation.** A separate noise sets each tunnel's thickness, a roughness noise bumps the walls, and an elevation noise sets each tunnel's height.
- **Cheese.** 3D noise below a threshold, layered by a vertically squashed noise, gives big irregular caverns with pillars left standing.

**What M7b takes from this.**
- **Passage links** are steered random walks toward their target junction, wandering early and homing in late, instead of a fixed bend.
- **Width** varies along each passage by noise, with occasional bulges, one passage in ten much thicker, and a random width, height and floor level per passage.
- **Dead-end spurs** branch off and taper to nothing.
- **Chambers** have random squash, and some are made of 2-3 overlapping blobs.
- **Cave levels** undulate regionally instead of sitting at fixed heights.
- **Canyons are rarer.** Their slot is a V-shaped trench whose depth and side offset wander along the passage, not a constant fin.
- **Worm bores** are random walks with a curl that drifts: they spiral one way, unwind, wander, spiral the other way. Their descent swings between near-level runs and steep plunges, they swell into chambers, and some fork.

## Build order

1. **M7a, deepen the world:** floor to -512, bedrock and lava floor, strata, ores re-spread, vanilla caves retuned, a speed check. **Done in 0.5.0 (2026-11-03):**
   - Strata: world/Strata.java, a material rule.
   - Ores and cave features: tools/gen_data.py ORES and CAVE_BANDS.
   - Structures: ancient cities at -350, trial chambers at -160..-60.
   - Deep dark: -475..-250.
   - Noodles: they now reach the floor.
   - Speed: +40-50% CPU per chunk vs 0.4.1.
2. **M7b, passage networks, bores and entrances.** **Done in 0.6.0 (2026-11-03):**
   - The network lives in core/Passages.java, carved per block through GrandCaves FINE.
   - The user approved the shapes in a 3D survey, after two rounds: the canyon drips, then the regularity (see the vanilla notes above).
3. **M7c, mega-caverns and rifts.** **Done in 0.7.0 (2026-11-03):**
   - Mega-caverns: core/MegaCaverns.java, evaluated on the interpolated grid through GrandCaves CAVERNS.
   - Rifts: kinds RIFT and GREAT_RIFT in core/Passages.java.
   - The user approved both in the 3D survey.
   - First roof was an egg carton of evenly spaced teeth (ridged noise); now scattered cones.
   - The "wall" the user saw was the survey block's edge; the viewer now leaves cut edges open.
4. **M7d, underground water:** rivers, falls, lakes, the sea, inverted lakes and rising falls. **Done in 0.8.0 (2026-11-04):** lakes, seas, flooded systems, pours.
   - Code: MegaCaverns lakes and seaFloor, core/Waters.java (table, state), world/DeepWater.java (fluid_springs step).
   - Approved in the 3D survey.
   - **Still to do:** passage streams (step pools), inverted lakes and rising falls (moved to M7e).
5. **M7e round 1 done in 0.9.0 (2026-11-04).**
   - Cave biomes: root, ice, sandstone, fungal, crystal, magma, abyssal shores, glowworm grotto, plus vanilla sulfur.
   - Rules: AtlasCore.caveBiome, material rules (gen_data CAVE_RULES), world/CaveDressing (formations, decoration, selenite beams).
   - Ten new blocks: world/ModBlocks; textures and LabPBR maps from tools/textures.py, approved on the New Blocks page.
   - User notes: fungal/sulfur/crystal rare but random anywhere; ice/sandstone needed more of their biome; "bland" fixed with formations and decoration; repetitive tiling fixed with variants and random turns; every block needs a specular map.
   - **Next:** the set pieces (the Hollow, Titan's grave, the Great Geode, the Worldroot, inverted lakes, rising falls) and more biomes.
5. **M7e, cave biomes, mesos and micros:** the lists above, most striking first (the Hollow, Titan's grave, inverted lakes, crystal caves).
6. Throughout: a depth view on the live map (a horizontal slice at a chosen Y).

## Still to confirm

- ~~Top stays at Y 1024; only the floor drops.~~ Confirmed (user, 2026-11-03).
- Vanilla's cave noise stays only as small side caves.
