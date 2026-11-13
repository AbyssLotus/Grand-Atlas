# Quality review, 2026-11-09

The user's ask: go back through the mod, look for code that might conflict and features that are bland
("the aurora place has a pretty ceiling but the floor is barren"), and fix on the spot or draft a plan.

**Scope:** first the underground (set pieces, cave biomes, mega-caverns, underground water), the client
aurora, and the plumbing the features share (build test12); then a first pass over the surface (build
test13, item 1 of the plan below).

Everything under "Fixed" is in build `0.12.0-test12` and was checked in freshly generated dev-server
chunks (worlds 110-113, core seed -2953816398943392853), not in a game client.

## Fixed

### Bland spots

| Where | Before | Now |
|---|---|---|
| **Aurora Vault floor** | Bare calcite and tuff under the crystal ceiling. | 10-16 crystal gardens (stands of leaning aurora-crystal spires, every fourth a great one up to ~35 tall), veins of fallen glowing crystal waving across the floor the way the ceiling's ribbons run, shards along the veins and on every spire, little crystal stands, pale boulders, and still puddles gathered in "wet quarters". Colours follow the lake bed straight above, so the floor answers the ceiling. Shards also hang from the dome and the lip round the lake. |
| **The Hollow, island tops and shores** | Moss with glowcaps only. | Meadow drifts of grass and fern, moss carpet, azaleas, boulders. |
| **Glowworm grottos, floors** | Bare moss and clay under the silk. | Glowcap clumps, ferns, moss carpet, boulders; small dripleaf on the clay. |
| **Mega-caverns with no cave biome** | Bare rock (most deep caverns). | Stalactites and stalagmites in the rock they grow from, boulder fields, drifts of gravel, all in broad patches so parts stay clean. |

Code: `core/SetPieces` (`buildVault` gardens, `floorVein`), `world/CaveDressing` (`vaultFloor`,
`dressCavern`, the Hollow and grotto cases).

### Conflicts and bugs

1. **Plants and silk inside underground lakes, each holding a block of air.** Cave dressing runs at
   `underground_decoration`, the underground water at `fluid_springs` after it, and the water left
   anything non-air alone. Under the Hollow's lake alone: ~19,000 moss carpet, ~8,000 grass, ~2,900
   fern, ~1,700 silk. `DeepWater` now floods anything that isn't solid and waterlogs what can hold
   water (dripstone, clusters). Re-checked: 0 plants, 0 air blocks touching water under the lake.
2. **Aurora ribbons jumped up and down as you walked.** They hung from the lake bed's height over the
   camera, and the bed is wavy (the log showed it changing every few blocks). They now hang from the
   lake's underside plus 70% of the deepest water seen, which is one level for the whole lake.
3. **Aurora vanished at the vault's edges.** It only ran with water straight overhead. It now keeps the
   lake it knows while you're in the vault, and looks for the lake round you when you come in from the side.
4. **Aurora log spam:** a line every few blocks walked. Now only on/off.
5. **Memory held after leaving a world.** Eight features each kept the last `ServerLevel` in a static
   field, and `CoreHandle` kept every core ever made. Now one shared `CoreHandle.coreOf(level)`,
   released on `ServerStoppedEvent`. (Also removes a small race: level and core were two separate fields.)
6. **Stale per-column flags in `SetPieces.column`.** `vWell` and friends were only written when a site of
   that kind covered the column, so `nearWell` could answer for the previous column. All cleared per column now.
7. **Aquifer lava and water hanging just over mega-cavern roofs** as a sheet of fluid above the open
   space: cleared up to 8 blocks over the roof.
8. **Rising fall lifted creative-flying players.** It doesn't now.

Earlier the same day (already reported): the aurora never switching on (integer overflow in the
"moved 3 blocks" test), ribbons hidden by water (now drawn with a depth-writing emissive type), veil
blocks no longer generated, clickable `/grandatlas locate` coordinates.

## Plan: not fixed yet

Ordered by how much I think each one costs the "wow". Items marked **(your call)** need a decision.

### 1. Surface review: first pass done (build `0.12.0-test13`)

**Measured** (160-190 block squares generated on the dev server, counting what stands on each ground block):

| Country | Before | After |
|---|---|---|
| Lowland plains | 97% grass; 75% of it with nothing on it; no boulders, 0.6% bushes | bushes 2.9%, tall grass 3.6%, ferns, a few boulders |
| Basin floor grassland | (same sheet) | bushes 3.2%, tall grass 4.0% |
| Red desert | sand 97-99% bare | dry grass and dead bushes on 8.5%, in drifts |
| Dune sea on a basin floor | sand 99% bare | dry tufts on 5%, in drifts |
| Badlands | terracotta 94-97% bare | 88-93% bare (dead bushes and dry grass in patches) |

**Cause:** every boulder, fallen log and ground-cover patch on the surface was placed by `MicroDetail`,
which only works inside meso sites. The country between sites had vanilla's plants and nothing else.

**Fix:** `world/CountryDetail` (`grandatlas:country_detail`, local_modifications, wired in
`tools/gen_data.py`), for ordinary ground outside sites, water, coasts, salt flats and wind-carved rock:
- boulder fields in the country's own stone (mossy in wet woods, sandstone, terracotta, granite in
  savanna, grey in the cold and the peaks), thick in fields a few hundred blocks across, lone ones elsewhere;
- rock showing flush through the turf in the stoniest fields;
- fallen trunks in the woods, in the forest's own wood;
- thickets of bushes, ferns and tall grass on grassland;
- dry grass and dead bushes in drifts on sand and terracotta, sandstone slabs through the sand;
- gravel and cobble scree on level bare rock in the mountains;
- dune grass on beaches' dry sand.

**Still open on the surface:**
- **Straight edges between landforms.** At 1280 1280 a dune sea meets a rocky mountain along a
  dead-straight diagonal, sand against stone with no transition; the canyon-plateau edge at about
  1100 2200 shows the same against forest. It needs a transition band (sand drifting up the rock, scree
  running out onto the sand) and probably more warp on that edge. This is the most visible surface flaw I found.
- **Densities are a first guess.** I tuned them from counts and top-down pictures, not from standing
  in the world. Plains from above are still mostly a green sheet; whether that reads as "open" or
  "empty" at eye level is **your call**.
- **Bare rock stays bare:** stone and andesite faces are 91-100% bare by design (SteepRock), and
  mountain rock above the snow line is under snow. Ledge plants, lichen and krummholz would help the
  faces; not attempted.
- **A shore is set far inland.** `ColumnSample.shore()` is non-null hundreds of blocks from any sea
  (with a distance), and on the basin's dune sea the distance is negative ("out to sea") on dry land
  100 blocks up. Anything that tests `shore() != null` as "on the coast" is wrong; I found this because
  my own first test did. Worth an audit of other callers.
- **The old tour in PLAYTEST.md is stale in more places than marked:** stops 2 (544 292) and 48
  (-980 1060) are open ocean now, as is stop 30's beach (-793 -101).
- Not sampled this pass: megarange above the tree line, highlands without snow, rivers and their
  banks, real beaches and cliffs, jungle, swamp, savanna.

### 2. Lake and sea floors are empty
Every underground lake bed is bare rock: the Hollow's lake is three quarters of its floor. Now that
the water floods plants, nothing grows under water at all.
- Add an **underwater dressing pass** after `DeepWater` (same step, registered after it): glow-kelp and
  seagrass beds in smooth patches, sea pickles on the Hollow's stacks, drowned boulders, pale shells of
  crystal in the vault's pool, and something moving light across the Hollow's lake (floating glowcap
  spores, lily-like pads).
- Would want one or two new plants ("vanilla plus" quality, with `_s` maps) so it isn't all vanilla kelp.

### 3. Vanilla structures can cut through set pieces
Mineshafts, ancient cities (their start is filtered by biome, their arms are not), trial chambers and
dungeons can cross a set piece. The dev world showed mineshaft planks and rails in a mega-cavern, which
looks fine, but a mineshaft through the hanging lake or a city arm in the Hollow would not.
- Fix: a mixin on structure start placement that rejects starts whose bounding box meets a set piece's
  box (`SetPieces` already has min/max per site), or a NeoForge structure-modifier if one fits.
- **(your call)** Dungeons inside set pieces: keep (loot in the Hollow) or remove?

### 4. Dungeon and geode density
`monster_room_deep` is 35 tries a chunk and geodes 1 in 4 chunks (scaled for the deeper world). A
180x180x120 box of ordinary cave held 4 spawners and 8 chests, and every box had 150-300 exposed
amethyst blocks. **(your call)**: that may be too many for things meant to feel like finds. I'd halve both.

### 5. Square pools beside cavern lakes
At about 735 -190 -860 (dev seed) vanilla aquifer water sits next to a cavern lake with straight,
aquifer-cell edges. Likely a flooded vanilla cave opening into the cavern at a different water level.
Needs a look in game, then either clearing aquifer water within a margin of the cavern's outline or
letting `DeepWater` own those cells.

### 6. The Aurora effect is client guesswork
The renderer finds the lake by scanning blocks, and draws ribbons on a grid round the camera whether or
not the lake is there (hidden in rock outside it).
- Send the vault's centre, lake outline and level from the server (a small payload on entering the
  biome), so ribbons follow the real lake, show from the far side of the vault, and can rise up the
  Aurora Well's shaft to be seen from the surface at night.
- Use the Iris API to skip the shadow pass, if we make Iris a requirement as you suggested.
- Ribbons write depth now, so where two cross one can cut the other. Sorting the quads back to front
  would fix it if it shows.

### 7. Hanging Mountains: retired but still in the code
`buildPeaks` returns at once, but ~200 lines, a biome, a material rule and dressing remain. Either
rebuild it with a purpose (your note: "they serve no purpose") or delete it all. My suggestion for a
purpose: the peaks' tips hold ice-locked chambers you reach by climbing frozen falls from the lake
(loot plus a view straight down), which gives the place a route and a reward.

### 8. Other sparse cave floors
- **Sandstone caves:** only sand mounds, dead bushes and the odd bone. Add wind-cut pedestals, drifts
  against walls, pottery-sherd scatter, a few larger fossils.
- **Abyssal shores:** froglight patches and dark sand. Add tide pools, shell beds, drowned columns.
- **Great Geode:** not re-checked this pass (floor is a lake plus crystal).

### 9. Smaller code items
- `SetPieces.veilLength` and the aurora veil blocks are unused now (blocks stay registered so old
  worlds load). Remove with a block alias when the save format next changes.
- `SelfCheck` has no set-piece checks. Add: no site overlaps another kind; the hanging lake's bed and
  lip are protected everywhere; garden spires stay below the lake; no set-piece water above its rim.
- `CaveDressing` has grown to one long `dress` method with a case per biome. Split per biome before
  the next round of biomes.
- A little aquifer lava still shows at the edges of mega-caverns where the cavern's carve is weak
  (21 blocks in a 160-block square, down from 28).

## Not checked
- Nothing here was seen in a game client. The pictures were top-down and side renders of saved chunks.
- Shader behaviour of the new floor (light colours from the veins, reflections in the puddles).
- Chunk generation time after the cavern dressing (it adds a cavern test per 4x4x4 cell in chunks over a mega-cavern).

## Round 3 (2026-11-10, build `0.12.0-test14`): "fix/add as much as possible, fill out what we have"

Checked in generated dev-server chunks (worlds 120-121), not in a game client.

| Item from the plan | What was done |
|---|---|
| Lake and sea floors empty (2) | `DeepWater.beds`: seagrass meadows, kelp stands in water 4+ deep, glowing sea-pickle colonies, drowned boulders, each by its own smooth field. Under the Hollow's lake in a 160-block square: ~1,600 kelp stands, ~3,000 seagrass, ~470 pickle colonies. Applies to every cavern lake, underground sea and flooded passage. |
| Surface lakes and rivers (new) | `CountryDetail.freshwater` and `reeds`: seagrass on the beds, lily pads in drifts on still shallows, sugar cane in clumps on level banks. Not in meso-site ponds, the sea, or frozen country. |
| Structures through set pieces (3) | `mixin/StructureMixin` + `SetPieces.guarded`. **Changed in test15 on the user's word** ("crazy structure overlap is one of the cool parts of Minecraft"; a mineshaft into an Aurora Vault is good, into the ceiling lake is not): structures may now run into set pieces freely. Only pieces that would cross a guarded part are left out: the vault's hanging lake, well shaft and rising fall, the Worldroot's trunk and taproot, Titan's skull. The rest of the structure stays. Dungeons are features and were never blocked. |
| Sandstone caves, abyssal shores (8) | Sandstone: drifts of sand blown over the rock, dry tufts in them, fallen sandstone blocks, bone piles. Abyssal shores: tide pools a block deep (some lit by sea pickles), dark boulder fields. |
| Straight edges between landforms (surface) | Two causes, both fixed. (a) `AtlasCore.coastShore` made any land "past the shore line" a coast biome at any height: the bare stone-and-snow "mountain" beside the dunes at 1280 1280 was **stony shore biome a hundred blocks up**. Now only ground under sea level + 20. That area is meadow, badlands and rock now. (b) `CountryDetail.tongue`: within ~22 blocks of where sand country meets other country, each side reaches into the other in smooth-noise tongues. |
| Bare rock (surface) | Moss over rock in patches in humid woods, ferns and carpet on it. Rock in dry and alpine country is still bare apart from scree. |

`grandatlas:country_detail` moved to the `surface_structures` step so it always runs after steep_rock and micro_detail.

**Still open:** dungeon and geode density (your call); square aquifer pools beside cavern lakes; server-synced
aurora; Hanging Mountains rebuild or delete; the Great Geode's floor; a thin straight strip of desert sand
along the canyon plateau's foot at about 1100 2200 (a height band of desert biome, only partly broken by the
tongues); ledge plants on dry and alpine rock faces; rocky shores (gravel 98% bare); in-game review of every density.

## Round 4 (2026-11-10, build `0.12.0-test17`): trees and rock formations from approved packs

- **Trees:** 653 trees from H2CO3's 1k Treepack mixed through every wooded biome in the biome's wood and
  leaf, weighted toward large ones; 35 god trees (Plutouthere), 80-180 tall, about one per square
  kilometre of gentle country; Better Trees alongside for the game's own trees.
- **Rock formations (Dannypan):** outcrops everywhere (dressed per biome, thick where the country is stony),
  natural arches, and districts of spires, pillar mountains (warm wet wooded hills), bladed fins and spike
  clusters (dry, red and alpine country), with floating stone rings in pillar country only.
- **Found on the way:** vanilla springs broke out of every placed rock and ran across the country as sheets of
  water; one in eight is kept above the ground (`mixin/SpringFeatureMixin`).
- **Speed:** unchanged, about 255 ms CPU per chunk in plain country.
- Credits and terms: [CREDITS.md](CREDITS.md). Find things with `/grandatlas locate god_tree`, `pillar_mountains`,
  `spires`, `rock_arch`, `rock_fins`, `spike_field`, `stone_ring`, `outcrop`.
- **Not checked:** in a game client; desert and snowy variants; how often districts occur over a large area; what
  the pack's plank- and stone-bearing trees look like up close.

## Round 5 (2026-11-10, build `0.12.0-test21`): bland, missing, broken, and "bespoke"

The ask: find what is bland, missing or broken, and what could be reworked so the world feels made by
hand. Checked on the dev server; nothing here has been seen in a game client.

### Broken, fixed
- **`/locate structure #minecraft:village` froze the server for over a minute and the watchdog killed it.**
  The riverside rule rejected villages only after each was fully assembled. It now decides before
  assembly (eight searches take ten seconds in total). A cartographer's map search would have hit the same.
- **Villages were 4-6 km apart in forest country:** the biome rebalance cut the plains they need. Village
  styles may now stand in the wooded biomes of their climate, the grid is one try per 10 chunks, and the
  river may be up to ~84 blocks away. From the new spawn the nearest is 144 blocks.
- **`/grandatlas locate god_tree` could point at a tree that is never built** (one beside water).

### Missing, added
- **A chosen spawn** (`world/SpawnChooser`). The game's choice on the dev seed was a bare beach at the
  water's edge. New worlds now begin in gentle green country beside a river or lake with high ground in
  view, as near the origin as such a place exists (1440 70 1920 on the dev seed).
- **Place names** (`world/PlaceNames`). Every set piece, macro landform, meso site and god tree has a name
  made from its seed, shown on arriving: a title for the great places ("The Vault of Corwyn", "The Halmer
  Plateau"), a line over the hotbar for the small ones ("Fenlor Grove"). Not seen working: it needs a player.

### Reworked for a hand-made feel
- **Landmarks are sited.** A god tree or a natural arch takes, of seven spots in its cell, the one that
  stands proudest of the ground round it on gentle ground: a hilltop or a spur, not wherever the dice fell.
- **A god tree makes its own floor:** podzol and moss in the deep shade, ferns and mushrooms on it, and a
  wavering fairy ring at the edge of the shade (petals under a blossoming tree, mushrooms and ferns under
  a green one). First version only worked on grass; widened to any soil, not re-checked after that.

### Seen, not changed
- **Snow on god trees.** In cold biomes the crown stands above the snow line, so from above it is a white
  cap. It is the game's own rule and may look right from the ground: your call.

### Ideas not built (in the order I'd do them)
1. **Trails.** Worn paths (coarse dirt, path blocks, gravel) between a village and the nearest landmark, ford
   or pass. Nothing says "someone lives here" like a path that goes somewhere.
2. **Waystone cairns** pointing at the nearest named place (needs your builds: docs/STRUCTURES_TO_BUILD.md #1).
3. **Named places on a map item or in `/grandatlas where`**, and the name on signs at villages nearby.
4. **Framed views:** a natural arch turned so it faces the nearest god tree, peak or lake.
5. **Ruins at the named places** (needs builds), and lore books whose text uses the generated names.
6. **Biome borders:** an ecotone a few blocks wide between forest kinds (mixed species), so the regional
   patches don't change tree in one step. Not looked at up close yet.
7. **Seasonal or weather character per region** (mist in pillar country, wind in windswept biomes).
