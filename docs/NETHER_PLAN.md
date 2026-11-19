# Nether, plan A: a taller vanilla Nether with perched lava falls (plan, 2026-11-19)

This is a plan only; no code has changed. It is for 1.21.1 (`mc1211/` plus the shared core). The
background is in `docs/NETHER_RESEARCH.md`.

## What the user asked for

- **Keep the current Nether shape.** Vanilla's cavernous netherrack terrain stays as it is.
- **Raise the roof, and keep it.** It stays a bedrock roof, only higher.
- **Keep the giant lava lakes low down.** The user loves the look of the big lava lakes around
  Y 35.
- **Perched lava lakes.** About once every 500 blocks, a pond or lake of lava sits higher up and
  falls into the big lake below.

This plan deliberately leaves out new biomes, landforms, set pieces, structures and mobs. Those
can follow once this base is right.

---

## 1. The target, in numbers

| | Vanilla 1.21.1 | Plan A (decided 2026-11-19) |
|---|---|---|
| Dimension (`min_y` / `height`) | 0 / 256 | 0 / **320** |
| Generated terrain (`noise.height`) | 0–128 | 0–**256** |
| Bedrock roof | 123–127 | **251–255** |
| Space above the roof | 128 blocks | 64 blocks, so the roof stays usable as a road |
| `logical_height` (portals, chorus fruit) | 128 | **256** (portals can link anywhere under the roof) |
| Lava sea | top at Y 31 (`sea_level` 32) | **unchanged** |
| Biomes | 5, vanilla `multi_noise` | **unchanged**; other mods can still add biomes |
| Bedrock floor | 0–4 | unchanged |

Why these numbers:
- **The roof follows on its own.** Vanilla's roof rule is a gradient from `below_top 5` to
  `below_top 0`
  ([nether.json](https://raw.githubusercontent.com/misode/mcmeta/1.21.1-data/data/minecraft/worldgen/noise_settings/nether.json)),
  so raising `noise.height` to 256 moves the roof to 251–255. Phase N1 must confirm that
  "top" here means the noise top and not the dimension top.
- **The roof road stays.** A 320 dimension keeps 64 blocks above the roof, so the roof-highway
  habit still works. Height has to be a multiple of 16 ([wiki](https://minecraft.wiki/w/Dimension_type)).
- **Why 256 and not more:** it doubles the open space but keeps generation cost and save size
  sensible. Going higher (320 terrain, 384 dimension) is a one-number change later.
- **The lava lakes are untouched.** The user's "Y 35" is the vanilla sea seen from just above it
  (the top lava block is at 31). The user confirmed on 2026-11-19: keep vanilla's sea.

---

## 2. Keeping the shape while raising the roof

Vanilla's terrain is one 3D noise (`nether/base_3d_noise`, an `old_blended_noise`), with:
- a slide that forces rock from Y 104 up to 128 (the roof mass);
- a slide that forces rock below Y 24 (the floor).

There is nothing else: no gradient by height, no caves, no aquifers
([nether.json](https://raw.githubusercontent.com/misode/mcmeta/1.21.1-data/data/minecraft/worldgen/noise_settings/nether.json)).
That means "the same shape, taller" can be built in three ways, all in data with no Java:

| Variant | What changes | What it looks like (expected) |
|---|---|---|
| **Extend** | Same noise scale; the top slide moves to 232–256 | Two vanilla Nethers stacked: more ledges, bridges and floating shelves at many heights; caverns no taller than vanilla's |
| **Stretch ×2** | Noise `y_scale` halved, so every shape is twice as tall | The vanilla Nether blown up vertically: cathedral caverns, tall pillars, long cliff faces, the fewest ledges |
| **Stretch ×1.5** | In between | Taller caverns and some stacking |

- Every variant keeps the floor slide, so lava stays held in hollows at 31 and the big lakes keep
  their size and number. My read: the noise has no height gradient, so how open the ground is at Y
  31 doesn't change.
- **Recommendation:** start with **Stretch ×1.5**. It reads most like "the vanilla Nether, but
  grand" and gives the tall faces the falls need. Phase N0 renders all three so the user can choose
  by eye.

Biomes stay vanilla: the `multi_noise` source in `dimension/the_nether.json` is not overridden.
- Biome mods that inject through Lithostitched (Regions Unexplored) or TerraBlender (BOP) keep
  working.
- Vanilla's biome features (glowstone, basalt pillars, deltas, forests) come along unchanged,
  apart from the height-range audit in N1.

---

## 3. Perched lava lakes and their falls

### What a player should see
From the shore of a great lava lake, a glowing lake sits on a ledge high up the cavern wall. Its
lava spills through a notch in the rim and falls in one bright ribbon into the lake below. One of
these about every 500 blocks gives the Nether a landmark you can steer by: you can see the glow
from far off.

### Where they go
- **One site per 512-block cell** (the nearest chunk-friendly size to 500). The site is jittered
  inside the cell, keeping 64 blocks clear of the cell's edges, so neighbours are never closer than
  about 130 blocks and average about 500 apart.
- **A site needs a big lava lake below.** On a coarse grid (every 8 blocks) across the cell, the
  terrain noise is sampled at Y 31 to find open lava at least ~40 blocks across. The site is the
  lake's edge where the terrain rises highest.
- **The perched lake's height** is 40–120 blocks above the sea (Y ~70–150): low falls are common,
  grand falls rarer. A tall Stretch makes the grand ones possible.
- **A cell with no good lake below gets nothing.** That keeps it "about once every 500", never
  forced.
- Each cell's site is worked out once and cached, thread-safe in the same way as `core/Deep`'s
  cells, because C2ME and Distant Horizons generate on many threads.

### How it's built
1. **Ground (density, Java).**
   - A new density function, `grandatlas:nether_perch`, wraps vanilla's `final_density`. Near a site
     it adds a ledge: an oval shelf 10–24 blocks across and 6–10 thick, tied back into the wall with
     a tapered buttress so it never floats.
   - It carves a bowl 3–6 deep into the shelf, with one low notch in the rim facing the lake.
   - Away from sites it returns vanilla's value untouched, so the cost is near zero.
2. **Lava (a feature).**
   - A new `grandatlas:nether_perch` feature fills the bowl with lava sources up to the rim and
     dresses the rim with blackstone, basalt and magma. It places the spout at the notch.
   - It traces the fall's path down to the sea. It clears the air where the path is blocked, and
     cuts a one-block groove where the fall would land on a ledge, so it keeps falling instead of
     fanning out. This is the same idea as `PerchedWater.cascade` and its groove.
   - Each chunk places only its own slice, from the cached site data, so it stays inside the
     feature's 3×3-chunk write limit (`docs/MICRO_FEATURES.md`).
3. **Physics to respect.**
   - In the Nether, lava spreads about 7 blocks and moves a block every 10 ticks, three times
     faster than in the Overworld ([wiki: Lava](https://minecraft.wiki/w/Lava)). Falls will reach
     the lake in seconds, but any ledge the path touches will flood wide. That is why the groove and
     the clear path matter more here than for water.
   - Lava sets fire to nearby flammable blocks ([wiki: Lava](https://minecraft.wiki/w/Lava)).
     Nether woods don't burn, and netherrack is meant to burn forever, so this is fine.
4. **Options for later** (not in the first cut):
   - Sometimes a two-step cascade: the lake, then a small step-pool on a lower ledge, then the sea.
   - Rarely, a big lake fed by two falls.
   - A "frozen fall" (lava turned to basalt and obsidian) as an echo of the caves' frozen fire
     cascade.

---

## 4. Phases

Each phase ends with something the user can look at.

### N0: Look before building (preview)
- Build the three terrain variants (Extend, Stretch ×1.5, Stretch ×2) as data only, in a dev
  world.
- From a headless dev server (the same route as `debug/DeepCheck`), write north–south vertical
  slices and a few screenshots of each.
- **The user picks the variant.** The sea level (31, or 35) is settled at the same time.

### N1: Raise the roof
- **Overrides:**
  - `data/minecraft/dimension_type/the_nether.json`: height 320, logical height 256; everything
    else as vanilla.
  - `data/minecraft/worldgen/noise_settings/nether.json`: noise height 256, the chosen variant's
    `final_density`, `sea_level` 32. The surface rule stays vanilla's.
  - Generated by `tools/gen_data.py` like the Overworld's files.
- **Checks:**
  - The bedrock roof lands at 251–255 and the floor at 0–4.
  - Lava sits only at ≤31.
- **Audit every vanilla Nether placed feature's height range and count.** Ores, ancient debris,
  glowstone, springs, fire and fossils are each placed by absolute or top-relative ranges:
  - Ranges tied to the top may now spread over twice the height and thin out.
  - Ranges tied to the bottom stay as they are.
  - Fix them in data so each block appears as often as in vanilla. Ancient debris must stay as rare
    and as deep as vanilla's.
- **Structures:**
  - Bastions start at absolute Y 33
    ([bastion_remnant.json](https://raw.githubusercontent.com/misode/mcmeta/1.21.1-data/data/minecraft/worldgen/structure/bastion_remnant.json)),
    so they stay by the lakes.
  - Check fortresses (vanilla places them around Y 48–70 in code, to be confirmed), YUNG's Better
    Nether Fortresses (in the Create Atlas pack) and ruined portals in the taller terrain.
- **Make sure the Overworld hooks stay out of the Nether** (checked in code on 2026-11-19, re-test
  in game):
  - The core attaches per `RandomState` only when Grand Atlas' functions are in the router, so
    `CoreHandle.coreOf` gives null in the Nether.
  - The deep-lava fluid picker only acts when `min_y` < −64.
  - `DeepDark` only acts in the Overworld.

### N2: Perched lava lakes
- Add the `nether_perch` density function and feature (section 3). Register them in
  `ModWorldgen`, and put the core math in `src/.../core` with the other shared code.
- Add a `/grandatlas` command to locate the nearest perch, for testing.

### N3: Headless check (`NetherCheck`, like `DeepCheck`)
- Over a large area (for example 4,096 × 4,096), record:
  - perches found per cell (target: most cells have one);
  - for each perch: lake filled, spout present, and the fall reaching the sea after 200 ticks;
  - lava hanging over air anywhere else (target: 0);
  - roof and floor intact.
- Time chunk generation against vanilla, with C2ME on.

### N4: In-game look with the user's setup
- Use the Create Atlas pack, Distant Horizons, Iris with Lumina Event Horizon, and AmbientSounds.
- Check:
  - how the lava falls read from far away;
  - whether DH draws anything useful under a roof (DH can turn LODs off per dimension);
  - whether Lumina's fog hides the lakes;
  - pregeneration speed.

### N5: Ship
- Version bump, a changelog entry, a short note in `README.md`/`MODRINTH.md`, and a "reset your
  Nether" warning.

---

## 5. Compatibility and risks

| Topic | What happens | What to do |
|---|---|---|
| **Existing worlds** | Nether chunks already generated stay at 128 with a seam to new ones | Say in the changelog: use a new world or reset the Nether (every Nether terrain mod asks the same) |
| **Other Nether terrain mods** (Amplified Nether, Incendium, Atmospheric Nether, Nether Expanse) | They replace the same `nether.json`; only one wins | Detect at load and log which won. A config switch turns the Grand Atlas Nether off |
| **Nether biome mods** (Regions Unexplored, BOP) | Still inject, since the biome source is untouched | Test one in N4 |
| **Mob farms** | More open space to spawn into, so farms that rely on the slab work less well (my read) | Note it in the changelog |
| **Portals** | Logical height 256 lets portals link high up | Expected; note it |
| **Distant Horizons** | LODs under a roof add little | Leave on and judge in N4; DH can be turned off per dimension |
| **Performance** | Twice the generated height means roughly twice the noise work per chunk (my read); perches cost almost nothing away from sites | Measure in N3; pregeneration advice in the README |
| **Lava floods** | A fall that touches a ledge spreads 7 blocks in the Nether | The groove and the clear path; N3 counts stray lava |

---

## 6. Decisions

Decided by the user on 2026-11-19:

1. **Roof:** bedrock roof at 251–255, terrain up to 256, in a 320-tall dimension (0–319). The
   64 blocks above the roof keep it usable as a road. `logical_height` 256.
2. **Lava sea:** vanilla's, unchanged (`sea_level` 32, top lava block at Y 31).
3. **Perch spacing:** one site per 512-block cell.
4. **Not started yet:** the user asked for the design documents only. No phase has begun.

Still open:

- **Terrain variant** (Extend, Stretch ×1.5 or Stretch ×2): chosen by eye in N0. The leaning is
  Stretch ×1.5.
