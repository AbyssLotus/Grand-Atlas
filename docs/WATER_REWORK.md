# Water rework: the problems (2026-11-16)

The 1.0.0 goal: rip the water system out and put it back so stepping and the other odd shapes never
happen. This file lists what is wrong today and why, from the user's reports and a read of the code.
"Seen" = the user has seen it in game; "code" = found by reading the code, not yet confirmed in game.

## 1. Stepping makes unnatural terrain (seen)

Rivers above sea level are a staircase of flat pools (`Drainage.buildPools`). Each pool may drop
2.5-5 blocks, and its upstream end is cut into the slope by that much. The rest of the land is then
shaped from the stepped water:

- **The valley inherits the steps.** A river's flood plain and valley walls are measured from its pool
  level (`riverColumn`: `plain = surface + 1.5`, then the valley lerps up to the land from there). So
  every step in the water becomes a step across the whole valley floor, up to ~340 blocks wide:
  terraces with little cliffs between them.
- **The water level can never rise** (`safe` in `buildEdge`). Where the course crosses higher ground,
  the river cuts a trench through it, and the valley widens with the trench's depth (up to 260 + 80
  blocks). Ridges get sliced by straight-walled gorges and notches that don't look carved by water.
- **Routes come from a coarse grid.** Nodes every 128 blocks at the lowest of five points, and pits are
  breached by forcing a route through the lowest pass up to 15 nodes away. So some rivers cross hills
  a real river would go round, and cut through them instead.
- **Banks are built up to hold the water** where the ground beside it is too low (the guard in
  `carve`, `GUARD_SLOPE`), so hillside rivers get raised ledges, levees and spikes of ground.
- **Special cases pile up:** Drainage.java is ~1,800 lines of fixes (wander, `bowlUntil`, guard
  bands, "beside the fall" notches, free-fall faces). Each fixed one look but adds shapes of its own.

## 2. Waterfalls don't connect, hit cliffs, and spread out (seen)

- **Lip water is vanilla water.** Where river water would face air (a pool lip, a low bank),
  `PerchedWater` swaps it for ordinary flowing water with a tick. That water then does what vanilla
  water does: it falls, and spreads up to 7 blocks on every ledge or slope it lands on. A fall
  down anything less than a sheer face fans out into sheets.
- **Free falls off faces have no channel.** A drop over 40 blocks too steep for an amphitheatre
  leaves the face as it is (pool level NaN), and the water just pours off the lip onto whatever
  the face is. Ledges and the face's skirt catch it and spread it.
- **The water below can be cut away.** Several rivers, lakes and valleys can claim one column, and the
  water goes to whichever cut deepest (`Carve.settle`). A neighbour's valley can take the plunge pool
  or the pool below, so the fall lands on dry ground.
- **Amphitheatres are cookie-cutter bowls** with sheer walls, centred by formula on the course. On
  uneven ground the bowl's rim, the plunge pool and the river leaving it don't always line up.
- **Other falls are built by other systems** that don't know about the rivers: the Great Plateau's
  river and its plunge pool (`GreatPlateau`, `AtlasCore.plungePool`), the tepui falls
  (`FantasyDetail.tepuiFalls`, which drop vanilla water from a spot), and meso sites like terrace
  falls and grottos.

## 3. Too many separate water systems (code)

Each makes its own water and they settle conflicts after the fact:

| System | Where |
|---|---|
| River network: rivers, lakes, falls, fjords, deltas, oxbows | `core/hydro/Drainage` |
| Great Plateau lake, river and plunge pool | `core/macro/GreatPlateau`, `AtlasCore` |
| Meso and micro water: ponds, tarns, lake districts, calderas, sinkholes, terrace falls, grottos | `core/micro/*`, `world/MicroDetail` |
| Tepui falls, set-piece water (hanging lake, rising fall, Hollow) | `world/FantasyDetail`, `core/SetPieces` |
| Underground lakes and seas | `world/DeepWater` |
| Vanilla aquifers and springs | vanilla, `mixin/SpringFeatureMixin` |

Example: where a river valley crosses the plateau's river, the plateau's water is simply dropped
(`AtlasCore`: "water the macro set can't stand over a valley cut"). Rivers can end abruptly.

## 4. How the water goes into the world (code)

- Water is a decoration (`PerchedWater`), poured per chunk after the terrain. Bank holes from vanilla
  carvers are plugged, but only inside the chunk; a hole just over the border can stay open.
- **Frozen rivers stay vanilla water** so they ice over. On high ground, vanilla water runs out of
  any gap, so frozen and cold rivers can leak.
- Square aquifer pools beside cavern lakes are still open (QUALITY_REVIEW_2026-11-09, item 5).
- River water is a custom fluid. Shaders needed a mixin; other mods (pumps, boats, physics) may treat
  it differently from vanilla water.

## 5. Shape and feel (code + earlier feedback)

- Lakes form only in true hollows on the 128-block grid, so they're rare and their shores come from
  an 8-block flood grid.
- "Lowland" rivers sit at sea level if nothing on the way to the sea is 70+ higher, so a sea-level
  trench can run far inland.
- Width and meanders are set per 128-block edge and blended. Narrow streams look ruler-drawn on
  slopes.

## 6. Cost (code)

- The first visit to a new basin works out its whole drainage: a 1-2 s hitch.
- Every column checks 7x7 nodes of rivers, and each river scans all its segments. This is one of the
  heaviest parts of worldgen.

---

# The new design (proposal, 2026-11-16)

The user confirmed the list above is complete. The rework, built around four rules:

## Rule 1: water can slope, so nothing has to step

Minecraft water is flat per block, which is why every river above sea level became a staircase. But
the game already draws sloped water: flowing water's level (1-8) sets its surface height, and the
renderer smooths the surface between corners. River water today is always a full source
(`RiverWaterFluid.getAmount` returns 8).

- Give river water a **surface property** (1-8) that sets its drawn height, while it still never
  spreads. A river dropping one block over 20 then shows as a smooth ramp of water: 8, 7, 6 ... 1,
  then full again on the block below. No visible step.
- The whole river gets a **smooth bed and surface along its length**: a continuous profile, falling
  a little every block. No pools.
- Still water stays full and flat (lakes, the sea, plunge pools).

## Rule 2: the terrain follows the river, not the steps

- The valley is cut from the **smooth profile**, so its floor slopes evenly downstream. No terraces.
- Where the land rises in the river's way, the **route goes round it** if it can. Routing on a finer
  grid (32 blocks instead of 128), with depressions filled properly, so rivers follow real valleys.
- When there's no way round, the river still cuts through, but the cut is shaped like a gorge
  (V-shaped, its width tied to the river), not a straight-walled trench.
- **No raised banks.** If a river's water would stand above the ground beside it, the route or the
  profile is wrong, and the fix belongs there.

## Rule 3: falls are designed, and own their water

Falls happen only where the land really drops (a cliff, a plateau rim, a steep knickpoint), as
**set pieces**:

- The channel is cut right up to the lip, and the face under it is notched back, so the water drops
  clear of the cliff.
- The falling water is **river water in a "falling" state**: a column that never spreads. No vanilla
  water pours down faces and fans out over ledges.
- **The plunge pool belongs to its fall.** It's reserved first, and nothing else may cut it.
- Tall falls get tiers only where the face has real ledges. Otherwise it's one clean drop.

## Rule 4: one water system

The river network becomes the only source of water above ground. The other systems register with it
as inputs instead of making their own:

- The Great Plateau's lake and river, and the tepui falls.
- Meso lakes (calderas, tarns, the lake district) and terrace falls.

Lakes come out of the filled depressions themselves, so more natural lakes form, with real outlets.
Underground water (`DeepWater`) stays separate but checks against the network. Frozen rivers become
river water under ice, so they no longer leak. Aquifers stay out of the river's area.

## Order of work

1. Sloped river water (the fluid, its states, the renderer): test on a straight channel.
2. The smooth profile and valley cut, replacing pools. The biggest step: the stepping goes away here.
3. Finer routing and depression filling, giving rivers that go round hills and natural lakes.
4. Designed falls, with owned plunge pools and the non-spreading falling state.
5. Fold the plateau, tepui and meso water into the network.
6. Clean up: frozen rivers, aquifers near rivers, chunk-border plugs, speed.

---

# Progress

- **Step 1, sloped river water (done, 2026-11-16):** `RiverWater.SURFACE` (1-8 ninths), seen in game with
  `/grandatlas watertest`.
- **Step 2, smooth profile (done in code, 2026-11-16):** reaches between falls slope (`Drainage.buildPools`
  `surf`/`poolEnd`); PerchedWater draws each top block at its height. Fixes after the user's first look:
  - the bed under a one-block drop's lip is water, so no pale strip of rock shows at each drop;
  - the bank guard (`carve`) only holds a bank 1.5 over its own water (`HOLD`): fins next to rivers
    fell 90% (2,150 to 215 columns along six test rivers, `gradlew finProbe`);
  - no water read at a clamped spot (it ran backwards for a block at reach joins);
  - rapids boulders only where the water runs past them on both sides (they dammed narrow streams).
  Tools: `gradlew riverViews` (long profiles, before/after), `gradlew finProbe` (fins, or `at x z x z`
  to explain a line of columns).

- **Step 3, routing and natural lakes (done in code, 2026-11-16):**
  - *Straight walls near rivers* were not the cut-off distance: each pool had its own random bow and
    bank offset, and the valley wall jumped with it at every pool's edge (48 to 82 blocks out in one
    block). Now smooth noise along the course. Then, on hillsides by bends, the nearest point jumped
    across the bend to a stretch at another level: the valley is now the lowest any stretch of the
    course gives (`otherReaches`), never under a stretch's own bank. Fjords got the same, and their
    ledges fade out at the foot and top of the wall (they stood the wall up to 26 off the water).
    Delta fans rise to the river's own valley at their edge and fade in. Carved cliffs (6+ in one
    block where the land before rivers was smooth) round the user's spot: 978 to 0.
  - *Sea-level trench:* `LOWLAND_RISE` 70 to 20. The trench at -404 2049 (40-60 deep to sea level) is
    now a valley 6-10 deep.
  - *Network nodes* at the lowest of a 5x5 grid instead of 5 points: on 20 rivers, gorges (water 20+
    under the land, fjords left out) 27% to 13% of the course, mean cut 17 to 13 blocks.
  - *Tributaries* come down to the junction once their own bank and the slope between are in reach
    (a stream running alongside the river it joins held its bank up over the river's channel).
  - *Natural lakes:* basins the routing breached can hold lakes; the shore check converges (it lowered
    every gentle basin away); rivers within 3 nodes upstream stand at the lake's level (backwater); a
    lake's shore band can't be cut by another river except one carrying water at its level; a basin is
    refused if its lake would stand 10+ over any inflow. Lakes went from 0 to 1 in a 12 km square.
  - *Shallow edges:* a sloping surface crosses every whole level, leaving skims of 1-3 ninths over a
    bank level with the block's floor. They stay river water (`AtlasCore.SKIM`), not vanilla water that
    spread across the bank.
  - Containment: 9 open faces, 0 walls of water (as before the step).

- **Step 3, round 2 (2026-11-16, after the user's second look):**
  - *Rivers rare and never over 5 wide:* only 8.5% of land nodes had a river and the biggest in a 16 km
    square drained under 400 nodes (basins are small: the coast is near everywhere, and catchment counts
    rain, so dry land counts little; a weaker sea pull changed nothing). Streams now start at 4 nodes of
    rain (`SOURCE` 6 to 4: 48% more river nodes) and the width grows faster with size
    (`WIDTH_K * km2^WIDTH_EXP`, 4.62 and 0.55: creeks 2-3 wide, ~6 at 0.4 km2, ~15 at 2.5, ~26 at 7).
  - *Lake not linked to its outflow* (3045 2788): lowered to its shore, the lake dried out round its own
    node, so its outflow started beside it. A lake must cover its node. Then its outflow was dammed by
    the lake's own shore rules: the outflow is now exempt from both the shore band and the lake's bank
    guard, and holds the lake's level until its course leaves the lake for the last time.
  - *Stream starting uphill* (3148 3132): it was backwatered by that lake, held at its level through
    flat ground. Backwater now never holds a river over its node's ground less a bank.
  - *Junctions:* a river never starts higher than one flowing into it.
  - Containment: 0 open faces, 0 walls of water.

- **Step 3, round 3 (2026-11-16, third look: inflow dammed, leaks, spotty streams):**
  - *Inflow dammed / leaks:* the lake sat level with flat ground (the shore check had been loosened to
    accept water 0.4 under the bank), and its inflow could not be raised to it (backwater is capped at
    the inflow's ground less a bank), so the shore rule dammed it. A lake needs a block of bank all
    round again, and is refused over what any inflow can reach. Leaks round the spots: 0.
  - *Spotty streams:* two causes. A bed a hair under the water still filled the water's block with
    rock (beds now under the water's top block), and creeks were narrowed to about a block wide, which
    the block grid missed in places (`MIN_HALF` 1.5 after all narrowing). Dry cross-sections along 20
    rivers: 54 to 11 (the rest are fall faces, step 4).
  - **Basin lakes are back to none**: with lakes held to a full bank and to what their inflows can reach,
    the breached-pit lakes all fail. Real lakes need the routing to fill basins (below).

- **Lakes rebuilt into the routing (2026-11-16):**
  - `fitToBasin`: a basin's lake is lowered until every river within backwater's reach upstream (12
    nodes) reaches it: a node it covers is under it, a node it doesn't must stand `LAKE_BANK` (1) over
    it. Rebuilt lower rather than refused; refused if the basin is too big to check (800 nodes).
  - Points under a lake take its level; rivers upstream are held at it (`lakeBelow`), under their
    node's ground less `LAKE_BANK`.
  - The shore check asks for containment: no shore ground under the water's top block, and where the
    shore is in that block, the water in it a skim (`SKIM_LEVEL` 0.3). A block of bank all round could
    never hold on a gentle basin.
  - A ring (`Lake.ring`, 3 cells) round each lake: no other river's valley may cut it under the lake's
    level plus a bank (they cut just past the shore). The lake's own outflow is let through, its wet
    channel only. A lake reaching the node it drains to is refused (the river below ran along its shore).
  - 13 lakes in a 48 km square on the dev seed; round the two in the 24 km square: 0 open faces.
  - Still open: a lake's outflow can drop faster than sloped water can show (13 blocks in a short
    run below the 9380 -1961 lake): walls of water inside the river, for designed falls (step 4).
  - Separately, walls of water at some tributary junctions with no lake (-2306 -3393: two streams
    joining at 99.3 and 94.7 side by side).

- **Step 4, round 1: waterfalls that don't spread (2026-11-16):**
  - Every drop (a lip over lower water, a bank lower than the water) pours from one spout of ordinary
    water at the top of the open face (`PerchedWater.cascade`). Its way down is traced at generation:
    straight down through the air to water, and wherever it would land on ground a groove one block
    deep is cut, running out along the flow to the next drop. The water stays in the groove and goes
    over the edge instead of fanning out (the user's idea), and stays ordinary water, so players can
    turn it aside as they'd expect.
  - A falling river-water state (`RiverFlow.DOWN`, held, never spreads) was built first and is kept
    in the enum, unused: a held column hangs in the air if a player blocks it.
  - `/grandatlas watertest` also builds a test fall: a channel over a 6-block cliff onto a 3-wide ledge,
    then 8 blocks into a pool.
  - The smooth surface no longer averages past a drop of 3+ (`SMOOTH_CLIP`): it lowered the water well
    before a cliff's edge, so the drop left there was too small for a fall. `FALL_CHANCE` 0.8 to 1.
  - Where falls form: in a 12 km square, 39 river stretches drop 10+ within 40 blocks; 19 have a fall.
    The other 20 are steep rapids (0.3-0.6 a block), drawn as sloped water. Turning long steep rapids
    into chains of falls with pools (as the old pools did) is the user's call.

- **Step 4, round 2 (2026-11-16):**
  - *Leaves in falls:* `FallClear` (world/FallClear.java, top_layer_modification, after trees) traces
    each fall again through the carved world and clears leaves and vines in its way; a spout leaves
    grew over is put back. Trees keep their place beside the fall.
  - *Overlapping rivers at junctions* (-2306 -3393, -2332 -3396, -2782 -3626): three causes. A smaller
    tributary dropped to the junction's level beside a bigger one still high (it now follows the bigger
    one's water alongside, `surfaceNear`); a bigger tributary stayed high beside a smaller one lower
    all the way (it now comes down to the smaller one's start where they run alongside); a river pulled
    down by its own inflow arrived lower than the one it joined started (`startLevel` looks 3 nodes up).
    Walls of water at the three spots: 19+10+6 to 2+1+0.
  - Regenerating the data also brought the 26.3 version's data up to date (its dimension lacked the
    fantasy biomes; the shattered crystal's fourth variant).

- **Step 4, round 3 (2026-11-16). From now on only the 1.21.1 version (`mc1211/`) is changed; 26.3 is dropped.**
  - *Plants and leaves clogging rivers:* a neighbouring chunk's decoration could put grass, flowers or
    overhanging leaves in a channel before its water was in, and the water stopped at them. The water
    now replaces plants, saplings, leaves (waterlogged too) and vines in its channel, and clears plant
    tops left standing just over it.
  - *Leaves still in falls:* a neighbour's trees can be placed after a chunk's clearing ran. Each
    chunk's `FallClear` now covers the falls of the 3x3 chunks round it, so whichever chunk places
    leaves last clears them in its own pass.
  - New hand-drawn art: the aurora crystal block (blue, three variants; the other colours recoloured
    from it) and amber sap (two of its three variants).

- **Steps 5 and 6 (2026-11-16, 1.21.1 only):**
  - *Speed:* 600k columns near rivers on the dev seed: 9.2 s to 4.0 s. `otherReaches` (38% of the time)
    now reads riverColumn's own projections (`Carve.segU/segD2`) and per-course-point values worked out
    once per edge (`vHalf, vSteep, vBow, vSkew, vSurf, vWidth`); the valley's slope is `x * sqrt(x)`
    (power 1.5, was pow 1.6); rivers clearly out of reach are rejected on every fourth course point;
    carve() reads its routes tile once per tile; nodes take 13 terrain samples, not 25; the 25
    archipelago cells are remembered per cell. Terrain output unchanged by the first four (same
    cliff counts); the node sampling moved rivers.
  - *Great Plateau lakes* seed the network (`Nodes.seed`): a stream starts at a node in one whatever
    the rain, and the bowl fills as a network lake capped at the plateau's level, on a 240-block
    grid. The plateau's own water gives way wherever a network lake is near (`Carve.lakeNear`), and
    stays as the fallback where none forms. 16 of 29 seed nodes in a 60 km square carry one.
  - *Lakes over several nodes:* `lakeOver` (a node under a lake built from another takes its level,
    and its river holds it until it's out); the basin walk starts from every node under the lake; the
    "reaches the node it drains to" rule applies to the first node outside it.
  - *Lake fixes:* the flood starts from the bowl's bottom (from part way up a slope it took lower
    ground for the rim); the shore check looks only at the field's outer edge; a lake is refused if
    a river not of its basin runs through its ring lower (`foreignRiver`); the outflow holds the level
    to the field's outer edge. 71 lakes in a 48 km square (13 before), 0-1 open faces round each.
  - *Falls:* a face from 1.5:1 is a free fall (was 2.5:1), and runs its whole height (was the first
    40 blocks, a chute of sloping water below); the core cuts a groove a block deep down the face
    along the course, so the water keeps to it across chunks; the fall tracer follows a drop already
    there before cutting its own groove.
  - *Tepui falls and sky-isle springs* are traced the same way (`PerchedWater.guide`), with a catch
    basin (a pit three across, a block deep, rim made good) where they land on open ground.
  - *Frozen rivers* on high ground: ice over river water (was ordinary water all the way down).
  - Not needed: aquifer pools by cavern lakes (the aquifer mixin already clears the caverns' band),
    bank plugs across chunk borders (they already cross).
  - Containment: 5 open faces, 2 walls in 96,854 columns (a fall's lip).

# For the terrain pass (seen in game 2026-11-16, not water)

- **A second layer of mountain over a mountain**, a ~3-block gap of air between (3534 387 3433, 3480 345
  3466): looks like the 3D overhang/crag noise or the floater layer leaving a shell over the surface.
- **Caves chopping mountains** without blending into them (the same area): cave carving opens the
  slope with hard edges.
- **More rivers** (the user, 2026-11-17): rivers are long but sparse. Raise the river count, which
  also leaves room for the capped sinking streams of the cave rework (docs/CAVE_RESEARCH.md,
  section 4).

# Still open after step 3

- **Lakes (now none on rivers).** Most basins were already cut through by the routing (breach) before a lake
  is considered, and a basin whose lake would stand far over the rivers coming in is refused. The
  real fix is filling basins in the routing itself (priority-flood): the lake's level becomes the
  water level the river network carries, so inflows arrive at it.
- **Worldgen cost near rivers** is up about 40% (columns near rivers: 2.1 s to 3.0 s per 250k), from
  `otherReaches`. Leaving stretches out, even fading them, brings cliffs back; needs another idea
  (e.g. only stretches across a bend, found once per edge).
- **Fjord walls** are 70-80 degrees by design but very sheer at the foot (`pow(wall, 0.8)`).
- **Cinder field craters** cut sheer 38-deep holes on playa (-438 1917); not water, but seen nearby.

# Saved for step 3 (seen in game, 2026-11-16): handled above

- **Sheer straight walls near rivers** (`-338 203 2616`, `-346 203 2619` on the dev seed): long dead-straight
  vertical cuts along hillsides beside rivers. The user wondered if the "how far back cliffs go" decision is
  per chunk. Suspected: each river shapes only the columns inside its bounding box (`Edge.minX..maxZ`, padded
  by `3*half1 + valleyWidth + MAX_WIDEN + 16`) and within its distance cut-off (`riverColumn`'s first
  `return`). Where its valley would still be cutting there, the cut stops on that line: an axis-aligned
  sheer wall. Fix: let the valley reach h before either limit (size the pad and cut-off from the real
  valley width, including the `1.7 * (h - floor)` widening), or fade the cut to nothing toward them.
- **Sea-level trench inland** (`-404 150 2049`): a "lowland" river held at sea level cuts 35-60 blocks
  through ground at 100-125, and the steep walls take stone surfaces: a broad white staircase valley.
  The lowland rule (nothing on the way to the sea 70+ higher) needs rethinking with the finer routing.
- **A river perched on a narrow ridge** over a deep valley (`-1453 5159`): routing.
