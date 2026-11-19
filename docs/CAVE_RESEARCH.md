# Caves: how they work now, and how to make them great (research, 2026-11-17)

The user asked for caves with more variety (shape, type, interconnection), a bespoke yet chaotic feel,
a more integrated and dynamic system closer to real-world diversity, general improvements, and deep
integration with the water system, with as many underground features as the 50+ above ground.

This document has five parts: what the cave system is today (from the code), what it actually
produces (measured), how real caves work and what that suggests, the user's decisions after reading
it, and the proposed rework in phases (updated to match those decisions). Everything below is for
1.21.1 (`mc1211/` plus the shared core).

---

## 1. How the caves work today

The cave space is the union of six sources. Each is its own system and none knows about the others.

| Source | Where | What it makes | How it's evaluated |
|---|---|---|---|
| **Vanilla noise caves** (cheese, spaghetti, noodles, entrances) | everywhere below ~Y 60, noodles to the floor | blobby caverns with pillars, winding tubes, thin noodles | vanilla density, with the core's `caveDamping` added so mountains stay mostly solid |
| **Passages** (`core/Passages`) | every depth | networks of tubes, canyons, crawls, shafts and chambers on a 96-block 3D lattice with levels 44 apart; entrances to hillsides; worm bores; rifts and great rifts | per block, from a per-column list of nearby segments (`GrandCaves` fine) |
| **Mega-caverns** (`core/MegaCaverns`) | the deep and the abyss, one chance per 1,700-block cell | 220-800-block underground landscapes with floors, roofs, pillars, lakes, and seas (1 in 7 abyss caverns) | on the interpolated grid (`GrandCaves` caverns) |
| **Noise chambers** (`Caves.cavern`) | ~20% of land | chambers tens of blocks tall | interpolated grid |
| **Pits and fissures** (`Caves.fine`) | near the surface | shafts 3-8 wide dropping 60-160; cracks 1-4 wide down 20-90 | per block |
| **Set pieces** (`core/SetPieces`) | rare | the Hollow, Great Geode, Titan's grave, Aurora Vault, Worldroot hall, Hanging Mountains | per block, and they keep other caves out |

On top of the space:

- **Cave biomes** (`AtlasCore.caveBiome`): 12 kinds from depth band, the land above and regional
  noise (ice, sandstone, root, glowworm grotto, sulfur, fungal forest, crystal, abyssal shores,
  deep dark, magma chambers, lush, dripstone), plus one per set piece.
- **Dressing** (`world/CaveDressing`): formations (stalactites, stalagmites, columns) in each biome's
  blocks, roots, glowworm silk, shelf fungi, vines, snow drifts, sand mounds, selenite beams,
  boulders, and the Aurora Vault floor.
- **Water** (`core/Waters`, `world/DeepWater`): mega-cavern lakes and seas; regional **water tables**
  (about a third of the land, flat across ~640 blocks, between Y -320 and -20) that flood every
  passage below them; pours where a flooded tunnel opens into a dry space.

The plan in `docs/M7_UNDERGROUND_PLAN.md` lists about 40 cave biomes, 16 macros, ~30 mesos and ~25
micros. Done so far: the 12 biomes above, 6 set pieces, mega-caverns, seas, rifts and bores, and
formations. Most of the meso and micro catalogue isn't built yet. Passage streams (step pools) were
deferred, and no cave water is linked to the surface rivers.

---

## 2. What it actually produces (measured)

Probe: four 1.3 km boxes on the dev seed, sampled every 4 blocks from Y -508 to 10 blocks under the
ground. It counts the share of rock each Grand Atlas source opens, and how connected the opened
space is (26-neighbour flood fill on the 4-block grid). Vanilla's noise caves aren't in the core,
so they aren't counted; they add to the upper bands.

| Box | Band | Open | Passages | Mega-cavern | Noise chambers | Pits/fissures | Set pieces | Flooded (share of open) |
|---|---|---|---|---|---|---|---|---|
| 0, 0 | deep (0 to -250) | 5.0% | 0.9% | **4.1%** | - | - | - | 1% |
| | abyss (under -250) | 5.7% | 1.0% | **4.7%** | - | - | - | 20% |
| 4573, 3347 | upper (over 0) | 0.96% | 0.84% | - | 0.11% | 0.02% | - | 0% |
| | deep | 1.7% | 1.6% | - | 0.18% | - | - | 20% |
| | abyss | 1.3% | 1.3% | - | - | - | - | 43% |
| -1400, 5100 | upper | 0.51% | 0.48% | - | - | 0.02% | - | 0% |
| | deep | 0.76% | 0.69% | - | 0.07% | - | - | 8% |
| 9000, 6000 | upper | 0.86% | 0.85% | - | - | 0.01% | - | 0% |
| | abyss | 2.5% | 1.8% | - | - | - | 0.76% | 6% |

| Box | Open pieces | Biggest piece | Top 5 pieces | Pieces under 20 samples |
|---|---|---|---|---|
| 0, 0 (has a mega-cavern) | 537 | 85% | 92% | 396 |
| 4573, 3347 | 1,415 | **9.5%** | 26% | 1,162 |
| -1400, 5100 | 959 | **6.0%** | 19% | 702 |
| 9000, 6000 | 2,422 | **34%** | 51% | 1,965 |

What this says:

1. **The designed caves are thin.** Outside mega-caverns, Grand Atlas's own systems open only
   0.5-2% of the rock; passages are nearly all of it. The noise chambers barely register (0-0.2%),
   and pits and fissures are a surface sprinkle. Most of what a player meets in the upper caves is
   still vanilla's noise.
2. **It's fragmented.** Away from a mega-cavern, the biggest connected piece holds 6-34% of the open
   space. There are hundreds to thousands of tiny pieces. (The 4-block grid breaks some crawls, so
   this overstates it a little, but the pattern is clear.) The cause is in `Passages`: whole systems
   come and go with a regional mask (`presence`), links are dropped where the mask fades, and each
   level is largely its own tree. A player follows a passage and it ends.
3. **Mega-caverns dominate where they exist** (4-5% of the deep and abyss in that box, an 85%
   connected piece), and are absent elsewhere. The deep is either a vast landscape or thin tubes,
   with little in between.
4. **Water is separate from the surface.** Flooding is common (up to 43% of the open space in the
   abyss) but comes from flat regional tables. Surface rivers keep a rock cover of 26 blocks so
   passages never touch them. Cenotes and sinkholes are closed bowls, and there are no sinking
   streams, springs, cave rivers or underground waterfalls in passages.
5. **Variety is regional noise on one generator.** Every passage region uses the same four
   cross-sections. What changes is the mix and the biome dressing, not the kind of cave.

---

## 3. How real caves work, and what to take from it

### Caves are made by different processes, and each makes a recognisable kind

| Kind | Made by | Looks like | Where in Grand Atlas |
|---|---|---|---|
| **Branchwork** (karst) | sinkholes and sinking streams feeding tributary passages that join downstream into bigger ones | trees of passages: narrow vadose canyons upstream, big phreatic tubes downstream | limestone hill country, karst landforms, sinkhole fields |
| **Network maze** | flood water through a grid of fractures | angular grid of narrow passages | under rift valleys and fractured plateaus |
| **Anastomotic maze** | flood water along a gentle bedding plane | braided, flat, low tubes on one level | gently layered lowlands |
| **Spongework** | diffuse seepage, mixing waters | pore-like 3D maze of small rooms | coastal karst (with flank-margin caves) |
| **Ramiform** (hypogene) | rising warm or sulfuric water | huge rooms with branches sprouting outward; gypsum, boxwork, aragonite (Carlsbad, Lechuguilla) | under volcanic and hot-spring country, deep rift zones |
| **Lava tubes** | flowing lava crusting over | long sinuous tubes with skylights, benches (lava ledges), lava stalactites, tube-over-tube levels | Volcanic Wastes, calderas, cinder fields |
| **Sea caves and anchialine caves** | waves; sea water in karst | caves at the waterline; flooded caves with blue holes | coasts, archipelagos, Ha Long karst |
| **Fissure / tectonic caves** | rock pulling apart | tall narrow cracks, chockstones | rifts (already), faulted mountains |
| **Talus caves** | voids between fallen blocks | jumbled boulder mazes | under cliffs, scree, crag country |
| **Glacier and ice caves** | meltwater under ice; cold traps | blue ice tunnels, frozen falls, ice floors | megarange glaciers, cold mountains |
| **Quartzite caves** | slow dissolution of sandstone | broad low galleries in table mountains (Venezuela's tepuis) | **tepuis** (the landform exists already) |
| **Salt and gypsum caves** | fast dissolution | smooth white canyons, salt crystals | playas, salt flats |

Palmer's study of ~500 caves found **branchwork** the most common pattern, network mazes about 17%,
single passages 14%, ramiform/spongework 5%, anastomotic 3%. His key point: **how the water gets in
decides the pattern**, more than the rock does. Sinkholes give branchwork; floods give mazes; rising
water gives ramiform caves.

### Caves are part of the river system

- **Levels follow the rivers.** Cave passages form at the water table, which sits at the level of the
  nearest river. When the river cuts down quickly and then holds steady, a new level forms below the
  last. Mammoth Cave's levels match episodes of the Green River's incision, one a 15 m drop about
  1.3 million years ago. **Upper levels are old and dry; the lowest still carries water.**
- **Sinking streams and springs.** In karst, surface streams sink into the ground (at swallets or
  ponors) and run underground, then come back out at springs (resurgences) in valley sides lower
  down. Valleys above go dry.
- **Vadose above, phreatic below.** Above the water table, water cuts downward: canyons, shafts and
  waterfalls. At it, water fills the whole passage: round tubes, sumps and loops.
- **The cave's life zones** follow the light: entrance (plants), twilight (mosses, ferns), dark zone.

### What this suggests for Grand Atlas

1. **Pick the kind of cave from how water gets in**, the way Palmer does, and drive it from the land
   above. Grand Atlas already knows a lot about every column: the landform, the province, the river
   network, sinkhole fields, karst, volcanoes and rock.
2. **Tie cave levels and water to the surface rivers**, so caves and rivers are one hydrology. That's
   the integration the user asked for, and the rivers now have reliable levels to hang it on.
3. **Make a region's caves feel authored**: one kind of system with its own rules and set of
   features. Keep the chaos at the passage scale (random walks, varying widths, breakdown).

---

## 4. The user's decisions (2026-11-17)

The user read this document (the doc only, nothing in game) and gave these notes and answers. They
override anything in section 5 that disagrees.

### Notes as they read

| Note | What it means for the rework |
|---|---|
| "our caves are very thin, we need to change that" | Open-space targets go up a lot (below), and passages get wider and taller. |
| "almost zero variety compared to real life" | Every province gets its own generator, and no ground is left without one (the baseline provinces below). |
| Worried about too few surface caves leading underground | Entrances become a design target with their own kinds and densities, and every entrance leads into a real system. |
| Too many rivers going underground, "maybe a 65-35 split" | Sinking streams are capped: at most 35% of the eligible reaches in karst country sink, and 65% stay on the surface (my reading of the split). |
| In-game debug "has been kinda bad"; make it readable and add visual debug | A new phase **C0** before everything else (below). |
| Lots of karst features, but "almost nothing will spawn under plains/regular forests" | Two fixes: the general features spawn in every province, and new baseline provinces with their own features cover plains, forests and the rest. **Karst stays as common as it is now** (the user, later the same day). |
| Does this still follow the **vertical fantasy rule** (more Rule-of-Cool the deeper you go)? | Yes, and it's now written down as a rule for every phase (below). |

### Answers to the four questions

1. **Vanilla noise caves: as little as possible.** "Our caves should be just as cool as a baseline
   with them being even better whenever we can." Vanilla's cheese, spaghetti and noodle caves are
   switched off once the baseline provinces exist (C1). Grand Atlas's own systems fill the whole
   underground.
2. **Sinking streams: unsure, because rivers are already sparse** (long but few). So: capped at 35%
   of eligible reaches and only in karst. Main rivers never sink, only tributaries, and every sinking
   reach comes back out at a spring. Separately, **more rivers** goes on the surface list (the user's
   suggestion: "could be fixed by upping river count"), recorded in `docs/WATER_REWORK.md`.
3. **Mazes: full size is fine.** "A maze is a maze." Players who get lost can mine their way out
   ("if they don't hit lava").
4. **The deep and the abyss:** they get the same connected feel, with the whole system linking up
   from the surface down. Deeper down it should be **more open than closed**, with a steady stream
   of Rule-of-Cool areas that link up well and can sometimes stack.

### Rules that follow

**Thick caves (targets for the probe).** Today Grand Atlas's own systems open 0.5-2% of the rock
outside mega-caverns, with vanilla adding to the upper caves.

**The hard requirement (the user):** by the end of the rework, Grand Atlas's caves, with vanilla's
switched off, must open **at least 75% as much space as vanilla's caves do**, judged band by band
and not just as one world total. Vanilla's caves aren't in the core, so C0 measures them in a
running server: vanilla's own terrain for the same seed, sampled in the same boxes as section 2.
The bar is 75% of that, in every band. The deep and abyss have no vanilla
counterpart below -64, so there the bar is vanilla's figure for Y -64 to 0.

**Measured 2026-11-17** (`/grandatlas survey caves`, dev world seed 2108563594, the four boxes,
every 4 blocks; open = cave space 10+ blocks under the ground, flooded or dry):

| Band | Vanilla | Grand Atlas today | Bar (75%) | Grand Atlas as a share of vanilla |
|---|---|---|---|---|
| Shell (top 40 under the ground) | 7.67% | 0.40% | **5.75%** | 5% |
| Upper (over Y 0) | 10.89% | 0.81% | **8.17%** | 7% |
| Low (Y -64 to 0) | 8.36% | 1.44% | **6.27%** | 17% |
| Deep (to -250) | (none) | 2.65% | **6.27%** (vanilla's low band) | 32% |
| Abyss (under -250) | (none) | 2.55% | **6.27%** (vanilla's low band) | 31% |

Vanilla's own boxes ranged from 6.0% to 9.8% in the shell and 8.9% to 12.9% above Y 0. Grand
Atlas needs about **10x more cave space above Y 0** and about 2.5x more in the deep and abyss. The
targets, as the bar or higher:

| Band | Target (Grand Atlas only, vanilla off) |
|---|---|
| Shell | 6-8% (entrances, near-surface caves, light zones) |
| Upper (over Y 0) | 9-12% in cave country, never under 8.2% averaged over a region |
| Deep (0 to -250) | 7-10% |
| Abyss (under -250) | 12-20%: more open than closed in the open regions |

Passages get wider and taller to match: main tubes 5-12 wide, canyons 15-40 tall, crawls kept rare
and short.

**No empty ground.** Every column has a cave province under it. Besides the special provinces in
section 5, three **baseline provinces** cover ordinary country:

| Baseline province | Under | Character |
|---|---|---|
| **Solution caves** | rolling hills, plains, mixed forests (the most common) | lighter branchwork: stream passages, breakdown rooms, column halls, side chambers |
| **Chalk and clay galleries** | flat plains, river lowlands | low wide galleries, flint seams, mud formations, clay banks, occasional flooded rooms |
| **Root and soil caves** | dense and ancient forests, jungles | roots hanging through the roof, shallow piping caves near the surface, root cathedrals deeper |

Karst country keeps the share of the land it has now (no increase). The baseline provinces have to
carry most of the volume, so they are full cave systems, not filler. The general meso sites and
micro features (section 5, C4) can appear in any province, dressed in that province's blocks.

**Entrances (a design target).** Every province lists its own kinds of entrance, and every entrance
joins the connected system rather than a dead-end pocket.

| Kind | Where |
|---|---|
| Hillside and valley-wall mouths | any hill or valley side |
| Collapse windows (a roof fallen in, the passage open to the sky) | any |
| Rock shelters (deep overhangs that lead on) | valley walls, cliffs |
| Sinkholes and dolines dropping into a shaft | karst, solution caves |
| Cenotes into flooded passages | karst |
| River-bank caves and springs | along rivers |
| Lava tube skylights | volcanic country |
| Sea caves | coasts |
| Ravine and gorge floors cutting into a system | rifts, gorges |

Target densities: about one entrance every 150-250 blocks in cave country, and one every 300-500
blocks elsewhere.

**The vertical fantasy rule.** The deeper you go, the more Rule-of-Cool it gets. Every province,
meso and micro is tagged with the bands it may appear in.

| Band | Feel | What lives there |
|---|---|---|
| Upper (surface to Y 0) | **real**: grounded speleology, natural chaos | the real cave kinds of section 3, rivers, entrances, light zones |
| Deep (0 to -250) | **heightened real**: real phenomena pushed to epic size, first fantasy touches | giant galleries, crystal and gypsum halls at huge scale, deep cave rivers and lakes, fossil walls, amber, glowing life |
| Abyss (under -250) | **Rule of Cool**: open, connected, fantastical | linked mega-caverns, seas, set pieces, rising falls, floating boulders, inverted lakes |

**One connected underground.** Systems link downward on purpose: karst shafts and waterfall shafts
drop into deep galleries; rifts, great rifts and worm bores tie the deep to the abyss. In the abyss,
mega-caverns become more common and are joined by grand passages and rifts, so a player can travel
from one to the next. A Rule-of-Cool area (a mega-cavern, set piece or abyss meso) comes up every
300-600 blocks of travel. Sometimes they **stack**: a vault over a sea, a hanging lake over a
cavern, a rising fall linking two.

---

## 5. Proposed rework

### Principle: underground provinces

The surface has a **province mosaic**: each province is one landform, with its own rules, its own
meso sites and micro features, and blended edges. The underground should work the same way.

**Speleo-provinces** are chosen from the landform and conditions above. Each one sets:

- the **system generator** (branchwork, maze, ramiform, lava tube, ...) and its parameters;
- the **cross-sections** it uses and how often;
- its **levels** (from the river network, or its own);
- its **water** (cave rivers, sumps, flooded mazes, dry);
- its **meso sites and micro features**;
- its **biome palette** and rock.

| Province | Under | Generator | Character |
|---|---|---|---|
| Karst branchwork | hills, karst, sinkhole fields, lake district | tributary trees draining to springs, levels from rivers | vadose canyons and shafts upstream, tubes and cave rivers downstream, flowstone, rimstone |
| Fracture maze | rift valleys, fractured plateaus, crag country | grid of narrow passages on 1-2 levels | angular, disorienting, chockstones, shafts |
| Bedding maze | gentle lowlands | braided low tubes on one level | crawls opening into low wide rooms, mud floors |
| Hypogene / ramiform | volcanic wastes, hot springs, deep under rifts | giant rooms with branching side passages | gypsum and selenite, boxwork, aragonite frost, warm pools; a natural home for crystal caves |
| Lava tubes | volcanic wastes, calderas, cinder fields | long sinuous tubes downslope from vents, stacked | skylights, benches, lavacicles, ropy floors, tube-in-tube, cold-trap ice |
| Quartzite galleries | tepuis | broad low galleries, deep shafts from the summit | ochre sandstone, cave rivers falling out of the walls (fits the tepui falls) |
| Glacier caves | megarange snowfields, ice | meltwater tunnels under ice | blue ice, moulins, frozen falls |
| Coastal and anchialine | coasts, archipelagos, karst seas | sea caves, flooded spongework, blue holes | half-flooded, light from below, sea life |
| Salt and gypsum | playas, salt flats | smooth winding canyons | white walls, salt crystals, brine pools |
| Talus | under cliffs and scree | boulder mazes | jumbled blocks, crawl gaps |
| Solution caves (baseline) | rolling hills, plains, mixed forests | lighter branchwork with rooms | stream passages, breakdown rooms, column halls |
| Chalk and clay galleries (baseline) | flat plains, river lowlands | low wide galleries on one or two levels | flint seams, mud formations, clay banks |
| Root and soil caves (baseline) | dense and ancient forests, jungles | shallow piping caves, root halls deeper | hanging roots, root cathedrals |
| (deep and abyss provinces) | by depth | linked mega-caverns, grand passages, set pieces | more open than closed, Rule of Cool (section 4) |

Provinces blend at their edges the way surface provinces do, so a karst system can run into a
fracture maze.

### Phase C0: readable in-game debug, and visual debug

The user's note: in-game debug "has been kinda bad". Today it is `/grandatlas where` (one long chat
dump), `/grandatlas locate ...` and `seed`. It comes first because every later phase is checked in
game with it.

- **Debug screen lines (F3).** A short Grand Atlas block on the F3 screen, updated as you move:
  landform / province / site; cave province and band; in a cave: system, level, water table; the
  nearest river and its water level; biome and cave biome.
- **A readable `where`.** Grouped into short coloured sections (surface, water, underground) with
  hover text for detail and clickable coordinates, in place of one long line.
- **Visual debug, toggled by command** (`/grandatlas show <layer>`, off by default, client side):
  - `caves`: the cave system's graph as lines through the rock, coloured by level, entrances marked;
  - `water`: the water table as a translucent sheet, cave rivers and swallet-to-spring links as lines;
  - `provinces`: surface and cave province edges as coloured walls at chunk borders;
  - `rivers`: river courses, falls and lakes from `Drainage`.
  In single player the client reads the core directly; on a server it gets a small payload of what's
  near the player.
- **`locate` for every new cave site**, with the distance and direction, like the surface sites.
- **The vanilla baseline measurement** (section 4): a server run with Grand Atlas's caves off counts
  what vanilla's caves open in each band. That gives the 75% bar, and the cave probe reports every
  later build against it.

**C0 as built (2026-11-17):**

| Piece | Where | Use |
|---|---|---|
| F3 lines | `debug/DebugReport`, `client/DebugView` | open F3 (operators / cheats on): a Grand Atlas block on the right, above the targeted block, sections S(urface), W(ater), U(nderground) |
| `/grandatlas where` | same rows | coloured sections; hover a row's ⓘ for detail; click the coordinates to copy |
| `/grandatlas show caves\|water\|provinces\|off` | `debug/AtlasDebug` | toggles layers drawn through the rock, refreshed every 16 blocks you move; the command prints the colour legend |
| `/grandatlas survey caves` and `gradlew :mc1211:runCaveSurvey` | `debug/CaveSurvey` | vanilla's cave volume against Grand Atlas's, in the four boxes; writes `grandatlas/cave_survey.txt` in the server folder |

Checked in game 2026-11-17 with `gradlew :mc1211:runDebugCheck` (`client/DebugCheck`: joins
join-test, runs each tool itself, screenshots to `run/screenshots/ga_*.png`, quits). The layers are
drawn just after the weather: later in the frame the camera's rotation is gone, and earlier the
clouds paint over them.

The server works out everything (it owns the core) and sends it to the client, so it works the same
in single player and on a server. Not yet: `locate` for cave sites (none exist until C1).

### The karst slice (C1 + C2 for karst only), as built 2026-11-17

`core/Karst.java`: one branchwork system under every sinkhole field (karst stays as rare as it
was: 3 systems in a 23 km square on the dev seed). Its pieces are ordinary passage pieces, so they
carve, flood and show in the debug view like the rest.

| Part | How |
|---|---|
| Region | about 2.2 times the sinkhole field's radius (260-460 blocks) |
| Spring and base level | the nearest river; the cave river's water is the spring's water level |
| Cave river level | a tree of wide passages (radius 3.3-7.5, wider downstream with flow) draining to the spring, in flat pools that step up 2 blocks every 140 blocks upstream, about a block deep; under thin rock the passages lower their roofs into canals, and where even that won't fit they dive under as sumps |
| Spring | the cave river dives under its roof (a sump to 6.5 under the water) and rises to a mouth in the river's bed |
| Dry levels | every 24 blocks over the river, up to 22 under the median ground; each its own tree, rooted near the spring side, opening as an old dry spring on the valley side, joined down by 2-4 shafts and steep canyons |
| Flooded level | 20 under the cave river, joined up to it by drowned shafts |
| Junctions | chambers where three or more meet (on the cave river's level, flooded: cave lakes), loops, dead ends |
| Sinkholes | each drops a shaft into the system (cenotes keep their pools for now: their level is only known after generation starts) |
| Sinking stream | at most 35% of systems; a tributary of 48 nodes' catchment or less within 0.9 of the region's radius; underground 120-360 blocks, or until just before a junction (at least 100); needs 4+ blocks of drop. The river network leaves that stretch dry (valley without channel or water: `Drainage.Sinks`); world/DeepWater turns the river water at the swallet into ordinary water so it pours down the shaft |

What the core measures (`KarstCheck` probe, every 4 blocks, karst pieces only):

| System | Ground over its river | Levels | Open | Flooded | Largest connected piece |
|---|---|---|---|---|---|
| 9214, 472 | hilly | river + 2 dry + flooded | 1.0% top 40 blocks, 2.9% under that | 25% | 90% |
| 6069, 4308 (sinking stream) | 5-15 blocks (lowland) | river + flooded | 2.5% top 40 blocks | 70% | 51% |

Today's passages: largest connected piece 6-34% away from mega-caverns. Lowland karst is mostly
flooded caves (as in real lowland karst); hilly karst gets the dry levels. Core self-checks all pass
(river containment unchanged: 5 open faces in 96,854 water columns).

Debug: the F3 province row names the karst system (spring, swallet, levels); the Cave row says which
karst level; `show water` draws the cave water (green-blue) and posts at the swallet (white) and the
spring (cyan); `locate karst` and `locate swallet`. Dev: `-Dgrandatlas.karstForceSink=true` gives
every system a sinking stream if one fits, and prints why not; `gradlew :mc1211:runKarstCheck`
screenshots one.

In-game check, 2026-11-17 (`runKarstCheck`, system 6068, 4308 with its forced sinking stream;
the check also writes block dumps, `run/grandatlas/karst_dump_*.txt`):

| Found | Cause | Fix |
|---|---|---|
| Curtains of water hanging in the cave river's chambers | Puddles from the cave dressing left floating, and the aquifer filling the karst passages (they are part of the density) at its own levels | DeepWater clears loose water standing in karst passage air (scanning 16 over the water in karst chunks); `AquiferMixin` puts no aquifer fluid in a karst system's height band under the ground |
| The swallet was a 5-block pit with a grass floor, the stream ending short of it | The shaft was aimed 3 over the junction's floor (the pool lies 3 under the stream); the passages from it are skipped under thin wet ground; PerchedWater plugged the shaft as a carver's hole under the bed | The shaft drops into the pool (flooded at its foot); short open passages join it to the tree; the channel runs 3 past the swallet point; PerchedWater leaves a swallet's shaft open; DeepWater turns the river water there into a fall last, after its clearing |
| Grass under the cave lake | The surface rules grass shallow cave floors | DeepWater's beds turn grass under water into gravel or dirt |

Now the stream pours down the shaft as a fall about 5 blocks wide into the cave river, and the
chamber holds a still cave lake with seagrass. Left: some of the stream spills a few blocks over its
east bank at the swallet; grass stays on dry cave ledges near the surface (the surface rules, not
karst); the jungle canopy hides the swallet and spring in the check's screenshots.

Open after the slice: cenote connections; the 75% volume bar (karst alone can't reach it, being
rare: the baseline provinces must); dry levels in lowland karst (there is no rock over the river).

### The solution caves and vanilla off, as built 2026-11-17

`core/Solution.java`: the baseline province under all land no karst system holds (chalk and clay
and root and soil come later and will take their share of it). Vanilla's caves are off: the noise
caves through `world/CaveDamping` (its bias is 10 everywhere; dev `-Dgrandatlas.vanillaCaves=true`
brings them back) and the carvers through a `neoforge:remove_carvers` biome modifier
(`no_vanilla_carvers.json`, also in tools/gen_data.py).

| Part | How |
|---|---|
| Levels | fixed heights for the whole world, every 26 blocks from y -52, wobbling 9 by region; a junction needs 16 of rock over it, at most 14 levels in one column |
| Cells | 256 across, each one system: per level a spanning tree of junctions about 43 apart (a tenth left out), links up to 96, 24% loops, 18% dead ends |
| Joining cells | gates: points on each shared cell edge, per level, worked out from the edge alone so both cells route to them |
| Passages | tubes (radius 3.4-6, wider under y 0), keyhole canyons (55% near the surface, 35% deeper), wide low galleries (15%), short crawls (5%); the roof kept 8 under the ground |
| Rooms | at junctions (half of those where three meet): column halls (radius 13-22, low), breakdown rooms (two or three blobs), side chambers |
| Between levels | two to four shafts or steep canyons a cell |
| Entrances | one or two hillside mouths a cell from the top level, never near water, under the sea or on flat ground |

Measured (in game, `runCaveSurvey`, vanilla's caves off, the four boxes):

| Band | Vanilla | Grand Atlas before | Grand Atlas now | Bar | |
|---|---|---|---|---|---|
| Shell | 7.67% | 0.40% | **5.99%** | 5.75% | meets (78% of vanilla) |
| Upper | 10.89% | 0.81% | **8.70%** | 8.17% | meets (80%) |
| Low | 8.36% | 1.44% | **9.48%** | 6.27% | meets (113%) |
| Deep | | 2.65% | 2.66% | 6.27% | short (deep and abyss linking, next) |
| Abyss | | 2.55% | 2.55% | 6.27% | short |

One of the four boxes (0, 0) is all sea, where no province reaches yet; the other three run 8-10%
open above y 0. Connectivity (core probe, 600-block boxes, every 2 blocks): the biggest connected
piece holds 96% of the open space (before: 6-34%). The carve costs about 3 ms more a chunk (7.3 ms
against 4.1). Core self-checks pass (0 passages opened under water). Dev:
`-Dgrandatlas.noSolution=true` leaves them out; `gradlew :mc1211:runSolutionCheck` screenshots a
room, a gallery and an entrance.

Open: caves under the sea (the coastal province); the deep and abyss bands; the margins in the shell
and upper bands are thin (78% and 80% against 75%); below the sea level vanilla's aquifer still
decides which passages flood.

### Shaping, the deep and the abyss, as built 2026-11-17

**Shaping** (the user: "the shaping is a bit bland"), solution and deep caves only: walls rough at
two scales (lumps and scallops); passages vary in height (0.7-1.3x) and width (0.6-1.4x) along
their length and their floors climb and dip between junctions; flat bedding-plane roofs in
galleries, some tubes and breakdown rooms; real columns in the column halls (wider at foot and
head); avens rising 9-25 from 28% of junctions and pits dropping 6-15 from 12%.

**`core/Deep.java`**: one network under the whole world, built like the solution caves (384-block
cells, world-fixed levels, gates on the cell edges).

| Part | How |
|---|---|
| The deep | four levels at y -95, -140, -185, -230 (wobbling 12): galleries radius 6-10 (vast low galleries 1.5x), tall canyons, halls on columns up to radius 35, breakdown rooms, domed chambers |
| The abyss | four levels at y -285, -340, -395, -450: grand passages radius 9-15, halls up to radius 52, closer junctions and more loops (more open than closed) |
| Down from above | 30% of the deep's top junctions link up to the nearest solution-cave junction of the lowest level |
| Mega-caverns | each cell runs a grand passage from its junction nearest a mega-cavern into it, on the level nearest the cavern's middle |

Measured (core probe of the survey, every 4 blocks, vanilla's caves off):

| Band | Grand Atlas | Bar | Target |
|---|---|---|---|
| Shell | 6.21% | 5.75% | 6-8% |
| Upper | 9.14% | 8.17% | 9-12% |
| Low | 10.05% | 6.27% | |
| Deep | **12.62%** (was 2.66) | 6.27% | 7-10% (a little over) |
| Abyss | **17.46%** (was 2.55) | 6.27% | 12-20% |

**Every band now meets the user's bar.** Connectivity from y -500 to the surface (a 800-block box,
every 3 blocks): the biggest connected piece holds 97.9% of the open space. The passage self-check's
ceiling on the deep's open share went from 6% to 25% (the rework's own targets). Dev:
`-Dgrandatlas.noDeep=true` leaves the network out. Installed as 0.13.0-test31.

Open: caves under the sea; chalk-and-clay and root-and-soil provinces; cenote connections; the
deep's dressing (crystal and gypsum halls, cave rivers and lakes: C2 and C5); the C3 generators.

### Caves under the sea, as built 2026-11-17

The solution caves now run under the sea floor too (drowned solution caves; `Solution.country`:
land, sea floor deeper than 6 under the sea level, or neither for shores and karst). Under the sea a
junction keeps 34 of rock over it and a roof 28 (the passages' own rule keeps 26 of cover under
water); canyons are rarer there (28% against 35-55%), as drowned caves were mostly phreatic.

**Blue holes**: in about a third of sea cells, one top-level junction opens straight up through a
round shaft (radius 4-9) to the sea floor, which is deeper than 12 under the sea level there, away
from any river's or lake's water. They are the ways in; the sea fills the caves (vanilla's aquifer
holds the sea level in open space under the ocean). 18 in a 2 km square of sea on the dev seed.

Measured (core probe): box 0, 0 (all sea) went from 0.03 / 0.22 / 0.82% (shell / upper / low) to
2.83 / 16.5 / 13.2%; all four boxes: shell 6.92%, upper 9.19%, low 13.05%, deep 12.63%, abyss
17.46%, every band over the bar. The shell under the sea stays low because of the rock kept over the
caves. Core self-checks pass. Built as 0.13.0-test32; not yet seen in game (whether the aquifer
floods every drowned passage, with no air pockets, is the thing to check).

### Leaks and the "TNT" look, fixed 2026-11-17 (test33)

The user's screenshots (y -287 to -455): water pools, curtains and lava spills in the deep network,
and caves that "look like they were dug using TNT". Also: **Rule of Cool is not Rule of Big** (the
deep and abyss went big instead of cool; the user will narrow down what Rule of Cool means through
yes/no questions before the abyss is reworked).

- **No aquifer fluid under the ground** (`AquiferMixin`, from y -502 up to 4 under the ground): the
  aquifer picked water or lava at its own levels inside the passages. Underground water is now
  the core's only; the sea and rivers over the ground and the lava under -502 are untouched.
- **Drowned caves get the core's water**: a solution cell with any junction point on the sea floor
  is wet; every piece in it is filled to the sea level (`Seg.water`, placed by DeepWater). Wet and
  dry cells don't join under the sea level (no gate), and wet cells send no shafts into the deep.
- **Walls**: the roughness is flattened along the bedding (x/z 11, y 3.2) with a faint small
  scale, ledges and notches along the walls instead of pockmarks.

All bands still over the bar (core probe); core self-checks pass.

### Rule of Cool, as the user defined it (2026-11-17)

From 24 yes/no answers. **Rule of Cool is not Rule of Big.** It means:

1. **Every corner makes you feel something**: awe, fear, coziness, wonder at something clever, or
   chills and horror. A place that makes you feel nothing has failed, however big it is.
2. **Keep players on their toes the whole time** in the deep, and all the more in the abyss.
   Special places may be rare, but ordinary passages still need *something* (they needn't be
   impressive).
3. **Surprise over spectacle**: underground, things are found, not seen from afar. Stumbling on
   something is the fun.
4. **Small can be as cool as big.** Big is the easy way out; use size for contrast, not by default.
5. **Contrast works** (a tight crawl into a huge space), **but never the same trick too often.**
6. **Environmental storytelling is always cool**: remains, ruins, signs that something lived or
   died here. Huge fossils and remains are in.
7. **Physical sense is preferred, not required.** Impossible-but-beautiful helps but isn't the core.
8. **Darkness is a tool.** Glow only faintly; the abyss may be *darker* than normal, a black mist
   or fog that makes it spooky. Pitch-black places where the dark is the point: yes.
9. **Colour changes with depth.**
10. **Smooth, not designed**: no perfect symmetry or spirals (it's a cave), but very smooth shapes
    (not "dug using TNT").
11. **Yes**: hanging things (stalactite forests, roots), vertical drama (shafts, falls hundreds of
    blocks tall), passages doing impossible things (spiralling round a void: worth trying),
    underground waterfalls and rivers, **lava** ("lava is dope": falls, magma rivers), cave
    ecosystems, past civilisations (ruins).
12. **No or minimal**: new strange materials (work with the blocks we have); inverted or floating
    water (keep to the existing inverted lakes, or fewer).
13. **The abyss should feel like another world**, not just deeper caves.

### Deep and abyss rework plan (from the definition)

| Step | What | Answers it serves |
|---|---|---|
| R1 | **Smooth and right-sized**: walls smooth (the banding faint), floors without terraces; halls and passages scaled back, size kept for contrast (a crawl before a hall, never every time) | 4, 5, 10 |
| R2 | **Moods by region**: each stretch of the deep and abyss draws a mood (awe, fear, cozy, clever, horror) that picks its shapes, blocks, light, sounds and features; neighbours differ so no trick repeats | 1, 5 |
| R3 | **Darkness and colour**: palettes by depth band; the abyss darker than normal with a black fog (client fog by depth and mood), pitch-black zones, faint glow only | 8, 9 |
| R4 | **Something every few dozen blocks**: small features in ordinary passages (formations, a bone, a drip pool, a side crack, a root curtain, a ledge with a view), denser in the abyss | 2, 3 |
| R5 | **Vertical drama and fire**: water and lava falls down tall shafts, magma rivers, plunge pools, hanging forests | 11 |
| R6 | **Story and life**: remains, ruins, fungus and root ecosystems as small found places | 6, 11 |
| R7 | **The odd impossible passage**: a gallery spiralling round a void, rare | 7, 11 |
| R8 | **Another world**: the abyss's palette, fog, sounds and life all its own | 13 |

Inverted and floating water stays as it is (no more). New blocks only if the blocks we have can't do it.

### R1-R3, as built 2026-11-17 (test34)

**R1, smooth and right-sized.** Wall roughness halved again (banding 0.07, small scale 0.015);
rooms have flat floors across most of their width (`Seg.ribs` 0.3 for chambers: no bowl of
terraces). The deep's passages went from radius 6-10 to 4.6-7 (abyss 9-15 to 5.5-8.5), halls from
up to 35/52 to 10-18 (abyss x1.3); the volume comes back from density instead (8 junctions a cell
side, every level). A tight squeeze (radius ~2) leads into 35% of the halls, domes and low rooms.

**R2, moods** (`core/Moods.java`): every region below y -64 has one of AWE, FEAR, COZY, CLEVER,
HORROR; warped cells ~300 across in the deep, ~240 in the abyss; `2i + j mod 5` with a shuffle per
5 x 5 block, so touching regions never share one. In `core/Deep` the mood sets:

| Mood | Passages | Rooms | Extras |
|---|---|---|---|
| Awe | 1.1x, tall canyons (vr 1.2-1.6) | domes, halls on columns (50%) | avens (40%), more level shafts |
| Fear | 0.65x, crawls 20%, canyons 40% | few small breakdown rooms | dead ends 35%, pits 15%, few loops |
| Cozy | 0.9x, wide low galleries 45% | low flat-roofed rooms, halls (45%) | few dead ends, no pits |
| Clever | 0.85x | halls, breakdown rooms | loops 55%, more level shafts |
| Horror | 0.6x, crawls 25% | breakdown rooms (15%) | dead ends 50%, pits 30%, few loops |

F3 shows the mood ("Mood" row).

**R3, colour and darkness.** `world/Strata` gives the rock within ~1-2 blocks of a passage's surface
below y -64 a palette by mood and band (vanilla blocks, smooth patches):

| Mood | Deep | Abyss |
|---|---|---|
| Awe | calcite, dripstone, tuff | obsidian, blackstone, a little crying obsidian |
| Fear | smooth basalt, basalt | basalt, blackstone |
| Cozy | dripstone, granite, packed mud | tuff, smooth basalt, magma in the walls (never floors) |
| Clever | bands of calcite, tuff, deepslate | bands of blackstone, basalt, smooth basalt; gilded blackstone |
| Horror | sculk, bone | sculk, soul soil, bone |

`client/DeepFog`: from y -64 the fog closes in and turns black, to ~90 blocks at the abyss's top
and ~36 in the abyss, with drifting pitch-black pockets (~16) below -120. By camera height only
(works on any server); moods don't change the fog yet (the client doesn't know them).

Measured (core probe): shell 6.75%, upper 8.95%, low 12.63%, deep 6.56%, abyss 7.01%, every band
over the bar (deep and abyss with thin margins, by design: dense, not big). Connected surface to
abyss: 93%. The palette pass costs ~6.7 ms a chunk. Core self-checks pass. Not seen in game.

### Fog and cracks, fixed 2026-11-17 (test35)

- **No fog at any depth** (the user): Distant Horizons (`enableVanillaFog = false` in the pack)
  pushes the vanilla fog out of sight at the end of `FogRenderer.setupFog`, after NeoForge's fog
  event, so DeepFog's distances never showed. Now `mixin/FogRendererMixin` (client, priority 2000)
  sets them at the same point after DH. The colour still goes through the fog-colour event. Shader
  packs (Iris) may draw their own fog.
- **Crying obsidian "too random, give it structure like cracks"**: the abyss accents (crying
  obsidian in awe, magma in cozy walls, gilded blackstone in clever) now follow cracks, thin bands
  where one of two differently stretched noises crosses zero, broken into lengths by a third, so they
  run across the walls in lines that meet and fork. Bone (horror) comes in small clusters.

### True darkness instead of fog, 2026-11-18 (test36)

The user on test35's fog: "less creepy and more just foggy"; wanted visibility cut off and opened up
only by light, dynamically (torches, dynamic-lights mods). Chose **pitch black with no light** (not a
5-block bubble, which would need a patch to Sodium's terrain shader) and plays with Iris shader packs
sometimes.

- The distance fog is gone (client/DeepFog and mixin/FogRendererMixin removed).
- `mixin/LightTextureMixin` + `client/DeepDark`: just before the lightmap is uploaded, every entry
  (sky light by block light) is darkened by its light level when the camera is below y -64, fully
  from -110: light 0 black, 1-3 barely there (factor (L/9)^1.8), about 9 and up as normal. Torches,
  lava, glowing blocks (crying obsidian cracks, magma seams) and dynamic-lights mods open it up,
  because they all raise light levels. Night vision and spectators see as normal. The fog colour
  goes black, so the edge of sight is dark.
- Works with Sodium and Distant Horizons. Under an Iris pack it depends on whether the pack samples
  the vanilla lightmap; to check.

### Darkness under shader packs, 2026-11-18 (test37)

With an Iris pack on (the user's: Lumina Event Horizon) the darkness vanished: packs light from the
raw light levels and never sample the lightmap. Lumina may not be patched (the user's rule), so
`mixin/compat/IrisDeepDarkMixin` (optional, @Pseudo) scales Iris's `screenBrightness` uniform (the
Brightness setting, read in `CommonUniforms.lambda$generalCommonUniforms$7`) toward 0 in the deep.
Lumina's minimum light where there's no skylight is `(0.005625 + brightness * 0.043) * CAVE_LIGHTING%`,
so it drops to near nothing and block light alone shows the rock. For truly black (all caves, not
just the deep) the user can set Lumina's Cave Lighting to 0. Not seen in game.

### R4, something every few dozen blocks, 2026-11-18 (test38)

`world/DeepDetail` (feature `grandatlas:deep_detail`, vegetal_decoration, every overworld biome):
per chunk, points drawn inside the core's passages (6 in the deep, 9 in the abyss) drop to the floor
and get a vignette by the region's mood, vanilla blocks only:

| Mood | Deep | Abyss |
|---|---|---|
| Awe | stalactites over stalagmites, some joined into columns | obsidian spires crying at the tip |
| Fear | cobwebs in corners; remains (a skull, bones) | remains (wither skulls too); a lone soul lantern on a chain |
| Cozy | a drip pool under its stalactite; moss nooks with glow lichen and glow berries | a small lava hearth in the floor |
| Clever | amethyst hollows; a fossil spine and ribs set into the wall | a boulder balanced on a basalt stalk |
| Horror | sculk round a sensor, now and then a shrieker (can't summon a warden) | bone heaps with skulls; soul fire on soul soil |

Not seen in game yet.

### R4 variety and R5, 2026-11-18 (test40, test41)

**R4, three times the kinds** (the user meant variety, not count; the count is back to 2 a chunk in
the deep and 3 in the abyss): 47 vignettes, four to six a mood in each band, picked evenly. New ones:
awe - flowstone curtains, rimstone pools, opened geodes, calcite columns, basalt organ pipes, obsidian
arches, weeping walls; fear - collapse rubble, a camp gone wrong, hanging chains, an iron-barred cage,
chained remains, a maw of dripstone teeth; cozy - mushroom groves, a spring seeping from the wall,
spore-blossom grottos, root curtains, hot springs over magma, warped and crimson gardens, lava
trickling from the wall; clever - an ore seam, stepping stones, a boulder hung on a chain, a basalt
causeway, floating stones, a tinted-glass window; horror - sculk veins round a catalyst, hidden
silverfish stone, a rare spawner den, a candle ritual, wither roses, a giant ribcage.

**R5, vertical drama and fire** (test41):
- **Falls down tall shafts** (`core/Deep`): up to two a cell, a shaft from a junction down two or three
  levels (90-165 blocks), likelier and wider in awe (radius 4.5-7), lava mostly in the abyss. Water
  falls land in a plunge pool (a basin and a bowl, the core's water two blocks deep); lava falls on a
  floor of magma. `world/DeepDetail` sets an arc of the shaft's wall 6 under the top junction to
  water or lava, which pours down the wall.
- **Magma rivers**: in the abyss, wide tubes and canyons of the deep network where a broad noise runs
  high carry a two-block channel of lava in their floor, so a river runs on through piece after piece.
- **Hanging forests**: 40% of chunks, in a room 9+ tall, a dense patch of stalactites (cave vines
  in cozy regions, weeping vines in the abyss's horror).

All bands still over the bar (deep 6.58%, abyss 7.04%); core self-checks pass. Not seen in game.

### R5 checked on a headless server, 2026-11-18 (test42)

`gradlew :mc1211:runDeepCheck` (debug/DeepCheck; the user allowed booting a dev server): finds falls
on fresh ground (`grandatlas.deepAt`, change it for ground not yet generated), forces the chunks
round them with non-blocking tickets (`setChunkForced` blocks the server thread, and a cold
region's first chunk overran the 60 s watchdog), lets fluids run 500 ticks, and writes
`run/grandatlas/deep_check.txt`: each fall's sources, fluid down the shaft, the shaft's middle
column block by block, the pool; fluid hanging over air away from falls; the vignettes' blocks.

What it found and what changed:
- Water falls work: sources in the wall, water down the full shaft, pools of 220-1,190 blocks.
- No fluid hangs over air anywhere else (0 in every run).
- Falls below the regional water table (core/Waters) were drowned sumps: now no fall starts within
  14 of the table, a table between top and foot is the plunge pool, and lava doesn't fall into it.
- The source finder missed wide or rough shafts and walls wearing the abyss's mood rock (obsidian,
  sculk, soul soil): it now heads out from the middle to the first wall block of any solid kind.
- Lava falls flow about a block every 30 ticks, so a 100-block fall takes a few minutes to reach
  the floor.
- The vignettes, hanging forests and magma rivers all appear (skulls, campfires, chains, cages,
  spore blossoms, amethyst, spawners, candles, wither roses, ~1,000 pointed dripstone in 45 chunks).

### Lava curtains, 2026-11-18 (test43)

The user: lava curtains "with a small gap so people can pass through". A new abyss vignette (awe,
fear and cozy lists): lava pours from the roof straight across the passage (across the way the open
air runs furthest, wall to wall, 5-20 wide) with a two-block gap in the middle. Each lava column's
source is a roof block held on every side but its underside; it lands in a one-block trench of still
lava cut into the floor under it, so the lava doesn't spread into the gap, whose floor stays rock.
The headless check sees them (lava sources pouring from a roof, 4-28 in 45 chunks). Falls' sources
now try 6, 9, 12 and 15 under the top junction until one holds (one shaft had a room opening the
rock at 6); every fall in the last run got its source.

### Phase C1: speleo-provinces and a connected backbone

- Pick the province per region from the land above (landform, province, climate, karst/sinkhole
  sites), blended like the surface mosaic. No ground is left without one: the baseline provinces
  cover ordinary country, and karst keeps its current share (section 4).
- Thicker caves: wider and taller passages, and the open-space targets of section 4.
- Entrances by design: each province's kinds and densities (section 4), each joined to its system.
- Links downward: shafts and rifts from upper systems into the deep, so the underground is one
  network.
- **Fix fragmentation first.** Each province's generator produces **connected** systems by
  construction: every system is one graph from its sources to its outlets, links aren't dropped by a
  fading mask, and systems are spaced rather than faded in and out. Levels connect through shafts and
  canyons where the old water table dropped.
- **Measure it** with the connectivity probe (biggest piece, pieces, entrance spacing, longest
  route). Targets: within a province, the biggest system holds most of the open space; an entrance
  every few hundred blocks in cave country; routes of 1-5 km you can follow.
- **Switch off vanilla's noise caves** (the user: as little as possible) once the baseline provinces
  are in, so Grand Atlas's own systems fill the whole underground.

### Phase C2: caves and rivers as one hydrology

This builds on the water rework.

- **Cave levels at river levels.** In karst provinces, passage levels sit at the water table of the
  nearest river (from `Drainage`), with older dry levels above at the heights of earlier valley
  floors. The lowest level carries water.
- **Sinking streams and springs.** In karst country, some river reaches sink into a swallet. Their
  valley goes dry downstream (a blind valley), the water runs in a cave river, and it comes out at
  a spring in a valley wall lower down, feeding the surface network again. The river network gets
  "underground" edges. **Capped** (section 4): at most 35% of eligible reaches, tributaries only,
  never a main river, and always back out at a spring.
- **Cave rivers** in the lowest passages, built with the same machinery as surface rivers: sloped
  river water, smooth profiles, and falls as ordinary water kept on course by grooves. Underground
  waterfalls down shafts; plunge pools; **sumps** where a passage dips under the water table (a
  flooded loop to dive through).
- **Cenotes and sinkholes connect.** A cenote opens into the flooded passage below at the water
  table; a dry sinkhole drops into a vadose shaft.
- **Water tables from the hydrology**, not flat regional sheets: the table follows the river levels,
  sloping gently toward springs.
- **Underground lakes** in chambers fill to their spill point, with the surface lake rules (fitted
  to inflows, shore checks).

### Phase C3: new cave generators

Build the province generators one at a time, most distinctive first:

1. **Karst branchwork** (the core of C1 and C2).
2. **Lava tubes** under the Volcanic Wastes.
3. **Hypogene giant rooms** with branching passages.
4. **Fracture and bedding mazes.**
5. **Quartzite galleries** in tepuis.
6. **Glacier caves**, **coastal/anchialine**, **salt**, **talus**.

Each generator owns its chaos: random walks with drift (as passages do now), widths and heights
varying along each passage, breakdown where rooms grow too wide, and its own rules for junctions.

### Phase C4: underground meso sites and micro features (target: 50+)

Placed per province from cells, the way surface mesos and micros are, so each region gets a
composed set of places.

**Meso sites (rooms and places, 30-300 blocks):**

| # | Site | Province |
|---|---|---|
| 1 | Grand gallery: a long hall 30-80 tall | karst |
| 2 | Breakdown chamber: boulder hills under a high dome | karst, any |
| 3 | Cave lake chamber, maybe with an island | karst, any |
| 4 | Waterfall shaft: a cave river dropping into a pool | karst, quartzite |
| 5 | Rimstone cascade: terraced pools down a slope | karst |
| 6 | Column hall: a forest of columns | karst |
| 7 | Sump and dive: a flooded loop between two dry passages | karst, coastal |
| 8 | Swallet: a river pouring into the ground | karst (surface link) |
| 9 | Spring cave: a river flowing out to daylight | karst (surface link) |
| 10 | Cenote connection: a sinkhole into a flooded cave | karst |
| 11 | Skylight cavern: daylight through a hole, trees in the light | lava, karst |
| 12 | Lava tube skylight with a cold-trap ice floor | lava |
| 13 | Tube-in-tube: two lava tubes stacked, a hole between | lava |
| 14 | Lava lake chamber | lava, abyss |
| 15 | Boxwork maze: honeycomb fins on walls and ceiling | hypogene |
| 16 | Gypsum flower hall | hypogene |
| 17 | Crystal pocket (selenite beams) | hypogene |
| 18 | Warm pool terraces (hot springs underground) | hypogene |
| 19 | Fracture canyon: a tall narrow crack with chockstones | maze, fissure |
| 20 | Maze knot: a dense tangle of small passages | maze |
| 21 | Mud hall: a low wide room with mud banks | bedding maze |
| 22 | Moulin: a meltwater shaft into the ice | glacier |
| 23 | Ice palace: blue ice and frozen falls | glacier, ice caves |
| 24 | Blue hole: a flooded shaft from the sea floor | coastal |
| 25 | Sea cave arch at the waterline | coastal |
| 26 | Salt canyon with brine pools | salt |
| 27 | Talus warren under a cliff | talus |
| 28 | Quartzite shaft from a tepui summit to its base | quartzite |
| 29 | Fossil wall: a giant skeleton in a cliff | deep |
| 30 | Pillar forest | deep |
| 31 | Root cathedral (under ancient groves) | root caves |
| 32 | Fungal grove | fungal |
| 33 | Amber vault | deep |
| 34 | Inverted lake chamber (as the Aurora Vault, smaller) | abyss |
| 35 | Rising fall shaft | abyss |
| 36 | Frozen fire cascade: a lava fall turned to stone | abyss |
| 37 | Floating boulder field | abyss |
| 38 | Bone bridge over a chasm | abyss |
| 39 | Collapse window: a passage open to the sky where the roof fell in | any (entrance) |
| 40 | Rock shelter: a deep overhang in a valley wall that leads on | any (entrance) |
| 41 | Stream cave: a passage carrying a brook under its valley | solution caves |
| 42 | Natural tunnel: a stream flowing straight through a hill | solution caves, karst |
| 43 | Flint gallery: chalk walls banded with flint | chalk and clay |
| 44 | Clay hall: mud formations, clay banks, a still pool | chalk and clay |
| 45 | Root-hung grotto just under a forest floor | root and soil |
| 46 | Heart-tree hollow: a vast root mass around a buried trunk | root and soil (deep) |

**Any province** (dressed in the local blocks): grand gallery, breakdown chamber, cave lake chamber,
column hall, waterfall shaft, sump, skylight cavern, crystal pocket, collapse window, rock shelter.

**By band (the vertical fantasy rule):** 1-28 and 39-45 are upper and deep (real). In the deep they
grow to epic size: galleries 80+ tall, lakes with islands, crystal halls. 29-33 and 46 are deep
(heightened real). 34-38 and the set pieces are the abyss (Rule of Cool), and these may stack.

**Micro features (1-30 blocks):** soda straws and helictites; flowstone curtains (bacon strips);
rimstone dams; cave pearls in pools; moonmilk; aragonite frost; gypsum flowers and needles; boxwork
fins; lava stalactites (lavacicles); lava benches; ropy lava floors; breakdown boulder piles;
chockstones jammed in cracks; natural bridges; mud cracks; scallops on walls (they show which way the
water flowed); sediment banks; driftwood and debris lines from floods; spring seeps; drip pools;
glowworm threads; cave crickets' twilight moss; root curtains; ice stalagmites; frozen waterfalls;
salt crystals; obsidian teardrops; glowing veins; spore lanterns; crystal flowers; luminous
puddles; flint nodules; clay mud formations; hanging root curtains through the roof. That is 35+,
and with the 46 mesos, more than 80 in all. Micros carry band tags too: drip pools and straws above,
glowing veins and spore lanterns deeper, luminous puddles and crystal flowers in the abyss.

### Phase C5: biomes and life zones

- Biomes come from the province plus depth, not from regional noise alone, so the palette matches
  the cave kind: flowstone halls in karst, gypsum and crystal in hypogene, basalt and ice in lava
  tubes.
- **Light zones** near entrances and skylights: the entrance zone grows plants from the biome above,
  the twilight zone moss and ferns, then the dark zone. This makes caves feel connected to the land.
- Fill out the plan's list (about 28 more biomes), most striking first.

### Phase C6: general improvements

- **Performance budget.** Measure the cave cost per chunk now, then keep each new generator within
  budget: per-column summaries ("nothing here" fast), per-province caches, and the interpolated grid
  for big shapes. (The river work went 2.3x faster the same way.)
- **A probe suite** like the water's: connectivity, open share per band and province, entrances per
  km², route length, flooded share, and a survey of cut or flooded structures.
- **The 3D survey artifact** the user used for M7b-c, extended to show provinces, levels and water.
- **Structures** keep working (mineshafts, trial chambers, ancient cities): caves keep out of their
  boxes, as now.

### Order and size

| Phase | Gives | Size |
|---|---|---|
| C0 debug | readable F3 lines and `where`, visual debug layers | small to medium, first |
| C1 provinces + connected backbone | the biggest felt difference: thick caves you can follow, regional character, entrances, vanilla off | large |
| C2 hydrology | cave rivers, swallets, springs, sumps, cenotes: the water integration | large, builds on C1 |
| C3 generators | lava tubes first, then hypogene, mazes, quartzite... | one generator at a time |
| C4 mesos and micros | the 50+ features | ongoing, in batches |
| C5 biomes and light zones | palette and life | medium |
| C6 performance and probes | throughout | small, continuous |

Recommended start (updated for section 4):

1. **C0, the debug tools**, so everything after can be checked in game.
2. **A vertical slice of C1 and C2 in karst country:** a thick branchwork system under a karst
   province, with entrances, levels from the nearest river, one capped sinking stream with its
   spring, a cave river with a waterfall shaft and a sump.
3. **The solution-caves baseline province**, then vanilla's noise caves off, so plains and forests
   aren't empty.

Then the other baseline provinces, the deep and abyss linking, and the generators of C3 in order.
The rework isn't finished until every band meets at least 75% of vanilla's cave volume, with
vanilla's caves off.

---

## Sources

- Palmer, A.N. (1991), *Origin and morphology of limestone caves*, GSA Bulletin:
  [digitalcommons.usf.edu/kip_articles/4140](https://digitalcommons.usf.edu/kip_articles/4140);
  pattern frequencies as cited in
  [Journal of Cave and Karst Studies v65n3](https://legacy.caves.org/pub/journal/PDF/V65/v65n3-Dom.pdf).
- Palmer, A.N. (1987), cave levels and base level, *NSS Bulletin*:
  [legacy.caves.org/pub/journal/PDF/V49/v49n2-Palmer.htm](https://legacy.caves.org/pub/journal/PDF/V49/v49n2-Palmer.htm);
  Mammoth Cave base-level drop, [GSA 2018 abstract](https://gsa.confex.com/gsa/2018AM/webprogram/Paper321444.html).
- Lava tube features: [USGS Bulletin 1673](https://pubs.usgs.gov/bul/1673/report.pdf);
  [Britannica: volcanic and tectonic caves](https://www.britannica.com/science/cave/Volcanic-and-tectonic-caves).
- The rest (cave kinds, vadose and phreatic forms, speleothems, life zones) is standard speleology.

## Feedback fixes and the redesign call, 2026-11-18 (test44)

- Chain-hung boulders removed (the user: "look stupid"). Deep clever vignettes are now amethyst,
  fossil, stepping stones and ore seams.
- Glow cut outside cozy: the awe abyss's wall cracks are amethyst block (violet, no light) instead
  of crying obsidian (2,227 blocks per 45 chunks before, 0 after). Spires, arches and the weeping
  wall are plain obsidian; calcite columns carry dripstone, not glow lichen; amethyst vignettes and
  geodes grow small and medium buds (light 1-2), not clusters.
- Ores: DeepCheck now counts ores showing on cave walls. Below -64 they showed 2.4 (deep) and 1.0
  (abyss) per 1000 wall blocks against 9.5 above. Two causes: the mood skins weren't ore-replaceable
  (added to deepslate_ore_replaceables) and the M7a re-spread had thinned the deep ores (iron,
  gold, lapis, redstone counts raised). After: 5.3 deep, 2.9 abyss.
- New `debug/CaveShape` (`runCaveShape`): the shape spectrum of the open space, vanilla against
  ours. It confirmed the user's "in-between" complaint; the redesign is in docs/CAVE_REDESIGN.md.
