# Grand Atlas playtest (0.9.0)

## Set up

1. **Update the jar, or import the pack.** If you already have the playtest profile, its `mods` folder now holds `grandatlas-0.9.0.jar`. Otherwise, `dist/grand-atlas-playtest-0.9.0.mrpack` opens in the Modrinth App. It installs Minecraft 26.3 + NeoForge 26.3.0.99 with Grand Atlas, plus:
   - Distant Horizons, to see the terrain from far off.
   - Xaero's World Map and Minimap.
   - Lithium, FerriteCore, ImmediatelyFast and EntityCulling.

   No Chunky: with Distant Horizons on 26.3 it crashes world creation ("Chunky is not loaded").
2. **Give it memory.** In the profile's settings, set Java memory to **8 GB**. The world is 3× vanilla height and Distant Horizons builds far views.
3. **Make a new world.** Chunks the old build generated stay as they were. Singleplayer → Create New World:
   - **Game Mode:** Creative.
   - **Allow Commands:** On.
   - **More → Seed:** `grand-atlas`. Every coordinate below is for this seed. Macros moved in 0.0.3, so the 0.0.2 coordinates no longer apply.

The first time you reach a new part of the world, Grand Atlas works out the drainage of its whole river basin. That can take a second or two on one worldgen thread (a brief hitch); after that it's cached.

## Commands

- `/grandatlas where` shows what you're standing in, largest scale first, e.g. `great_plateau → river → highlands → valley` or `lowlands → lake`. It also gives the height breakdown and the biome.
- `/grandatlas locate <name>` finds the nearest landform or macro. Tab completes the names:
  - Landforms: `megarange`, `canyon_plateau`, `dune_sea`, `rock_desert`, `playa`.
  - Macros: `great_plateau`, `massif`, `basin`, `elevated_mesa`.
  - Sites: `caldera`, `grotto`, `terrace_falls`, `crag_towers`, `lake_district`, and the other meso sites.
  - Island seas: `archipelago`, `atoll_field`, `volcanic_chain`.
- `/grandatlas seed` gives the terrain seed, so `gradlew preview -Pargs="--seed <it>"` can draw a map of your exact world.

Distant terrain fills in over a minute or two after each teleport. Raise Distant Horizons' LOD distance to see whole macros.

## Tour (0.4.1)

Found from the generator itself (the nearest of each to spawn, on seed `grand-atlas`), so they are where they say. Each `tp` puts you over it; fly in Creative. `/grandatlas locate <name>` finds the nearest of most of these from wherever you are.

| # | Where | Command | Look for |
|---|---|---|---|
| 1 | **Archipelago** (26 km across) | `/tp @s -5044 183 -6173` | A shallow sea full of islands of every size, skerries and sounds, the mainland's coasts facing it. |
| 2 | **Atoll field** (16 km across) | `/tp @s 970 183 52704` | Warm deep-blue sea with ring reefs a block under the surface, turquoise lagoons, sandy islets on the rings, passes through some. |
| 3 | **Volcanic chain** (17 km across) | `/tp @s 22060 183 20006` | A line of steep cone islands in deep water, some with summit craters. |
| 4 | **Caldera** | `/tp @s 5987 257 -1903` | Crater Lake: a forested rim round a deep blue lake, banded cliffs, maybe a cinder-cone island. |
| 5 | **Grotto** | `/tp @s 4364 185 3531` | A pool under an overhang, a stream pouring off the rim. |
| 6 | **Terrace falls** | `/tp @s -1936 247 -2083` | Turquoise pools stepping down a wooded slope over white travertine rims. |
| 7 | **Crag towers** | `/tp @s 2051 148 1943` | One to three great rock towers standing alone in open country. |
| 8 | **Lake district** | `/tp @s 1153 116 -5903` | Country crowded with long lakes, rocky islands and bare shores. |
| 9 | **Cinder field** | `/tp @s -1957 187 -4434` | Cinder cones, basalt columns, black ground. |
| 10 | **Hoodoo garden** | `/tp @s 8427 113 -489` | Capped rock pillars in dry country. |
| 11 | **Sinkhole field** | `/tp @s 9213 121 472` | Sinkholes and cenotes. |
| 12 | **Ancient grove** | `/tp @s 3558 154 -467` | Giant old trees. |
| 13 | **Tarn basin** | `/tp @s 5912 329 -1230` | High mountain hollows with small cold lakes. |
| 14 | **Fern gully** | `/tp @s 6040 156 3641` | Ravines full of ferns under the trees. |
| 15 | **Great plateau** (3 km across) | `/tp @s 27682 621 -2255` | Sheer walls 200-400 tall, rivers pouring off the edge. |
| 16 | **Massif** (5 km across) | `/tp @s 17958 965 2622` | A huge mountain mass. |
| 17 | **Basin** (3 km across) | `/tp @s 2092 170 2944` | A sunken basin. |
| 18 | **Elevated mesa** (4 km across) | `/tp @s 7442 540 7016` | A raised mesa country. |
| 19 | **Tallest fall** (37 blocks, lip at 4955 3225) | `/tp @s 4963 108 3154` | The tallest river fall near spawn, into its amphitheatre. |
| 20 | **Delta** | `/tp @s 10139 123 2709` | A river splitting into channels across low marsh islands at the sea. |
| 21 | **Fjord** | `/tp @s 816 183 -2273` | A U-shaped sea trough winding into cold mountains, widest at the sea. |
| 22 | **Sea loch** | `/tp @s -1560 143 -1053` | A narrow sea inlet winding into temperate hills, widening toward the sea. |

## Old tour (stale)

Kept for its notes. These stops were placed before the river, plateau and coast changes of 0.3.6-0.4.1, and many no longer land on their feature.

| # | Where | Command | Look for |
|---|---|---|---|
| 1 | Spawn, in a megarange valley | (spawn) | Valley floor with 650-block peaks around. |
| 2 | A summit next to spawn | `/tp @s 544 800 292` | Jagged peaks and cirques. |
| 3 | Canyon plateau rim | `/tp @s -1500 425 1450 facing -1650 240 1600` | Banded walls all the way down. Stone shows only in cave mouths, as in vanilla badlands. |
| 4 | Salt flat | `/tp @s -584 240 2296` | Dead-flat white crust with crack lines. |
| 5 | **Basin** from its south rim | `/tp @s 2092 520 4700 facing 2092 140 2944` | A ring of ranges around a sunken dune sea with rock outcrops, and rock desert at its foot. |
| 6 | **Rock desert** (new) | `/tp @s 26 178 4380 facing 200 160 4180` | Red sand dunes running into a banded sandstone jebel: a sand-blasted alcove at its foot, pocked walls, a hole through its side. Walk around for towers, fins, arches and slot canyons. Pits drop into the sand here and there. |
| 7 | Dune sea under an **elevated mesa** | `/tp @s 4500 220 7017 facing 5600 380 7017` | The mesa's stepped edge drops straight into the dunes. Odd towers and fins stand in the open dunes now. |
| 8 | **Massif** from the east | `/tp @s -6400 260 -2159 facing -8345 700 -2159` | A whole mountain mass rising out of canyon country. |
| 9 | **Great Plateau** waterfall (new) | `/tp @s -5890 400 9420 facing -5893 330 9207` | The plateau's river pouring off the rim and down a ~250-block cliff. |
| 10 | **Great Plateau**, its summit | `/tp @s -6060 520 6950 facing -5883 680 6767` | The plateau's own snow-capped summit over dark forest. |
| 11 | **Great Plateau**, its lake | `/tp @s -6986 470 7880 facing -6986 432 7705` | The lake. Follow its river south-east: it cuts a gorge where hills get in its way and ends at stop 9. |
| 12 | **Cascade to the sea** | `/tp @s -5445 105 5385 facing -5395 110 5425` | A river dropping 70 blocks to the coast as a stepped waterfall in a gorge: each fall pours into a pool and runs on to the next. Follow it upstream (south-east). |
| 12b | **Where the water walls were** | `/tp @s -3754 184 6438` and `/tp @s -5064 229 5631` | Your screenshots of walls of water and spikes. Plain banks and pools now, with nothing built up. |
| 12c | **Tributary falls** (new) | `/tp @s -3735 172 6525 facing -3790 136 6470` | A big river in its valley. A side stream that used to stand beside it as a 20-block wall of water now drops into it as a waterfall down the valley side. |
| 13 | **Confluence** | `/tp @s -4555 200 6065 facing -4700 140 5900` | The same river in a snowy valley, with a tributary cascading in from the side and another joining below. |
| 14 | **Oasis lake** (new) | `/tp @s 2110 135 2270 facing 2200 83 2350` | A lake filling a hollow in the basin's dune sea. Lakes now only form in real hollows (see the changes below). |
| 16 | Pond country | `/tp @s -2759 177 2688 facing -2793 137 2654` | Pond country: ponds in their own hollows with lily pads, reeds and mossy boulders. |
| 17 | Sinkhole field | `/tp @s 6144 160 4214 facing 6107 101 4178` | Cenotes: turquoise pools in sheer white limestone shafts hung with vines. Go down to the water for the undercut walls. |
| 18 | Hoodoo garden | `/tp @s -3613 163 -449 facing -3634 148 -470` | Hoodoo garden: banded terracotta pillars under grey caprock in red badlands. |
| 19 | Cinder field | `/tp @s -1099 257 2030 facing -1163 211 1966` | Cinder field: small volcanic cones, red round the craters, basalt columns and ash in the sand. |
| 20 | Glade | `/tp @s 3683 241 -3619 facing 3641 211 -3661` | Glade: open meadow inside the forest, boulders and fallen logs round it, a fairy ring of mushrooms near the middle. |
| 21 | Erratic field | `/tp @s -1271 125 -550 facing -1292 97 -571` | Erratic field: open plain strewn with great glacial boulders. |
| 22 | Tor hills | `/tp @s 1297 209 329 facing 1267 172 299` | Tor hills: stacks of rounded granite blocks on open hills. |
| 23 | Tarn basin | `/tp @s 5887 361 -1256 facing 5847 332 -1296` | Tarn basin: cold mountain lakes with stone and gravel shores in alpine meadow. |
| 24 | Butte field | `/tp @s -2829 223 -290 facing -2877 189 -338` | Butte field: small banded flat-topped buttes in red badlands, a little Monument Valley. |
| 25 | Ancient grove | `/tp @s 3674 329 -382 facing 3644 308 -412` | **Ancient grove** (new): giant trees with buttress roots and broad crowns in old-growth forest. |
| 26 | Mushroom hollow | `/tp @s -1905 179 -2055 facing -1926 154 -2076` | **Mushroom hollow** (new): giant red and brown mushrooms in dark forest, on mycelium. |
| 27 | Deadfall | `/tp @s -1852 195 -330 facing -1873 162 -351` | **Deadfall** (new): standing dead snags, fallen trunks, leaf litter. |
| 28 | Pale grove | `/tp @s -2684 156 -1984 facing -2726 120 -2026` | **Pale grove** (new): a patch of pale garden inside an ordinary forest. |
| 29 | Fern gully | `/tp @s 9280 201 -1897 facing 9208 149 -1968` | **Fern gully**: a winding ravine about 12 deep with sheer sides, ferns and moss, under the trees. About 9 km out: the nearest. |
| 30 | **Sandy beach** | `/tp @s -793 81 -101 58 14` | A beach with a real profile: a gentle foreshore into the sea, a berm, then the land. |
| 31 | **Dune beach** | `/tp @s -4545 81 -153 -27 13` | A dry coast: dune ridges along the shore behind the beach. About 4.5 km west. |
| 32 | **Shingle beach** | `/tp @s -849 81 -264 90 14` | Gravel with smooth patches of cobble and stone. |
| 33 | **Rocky shore** | `/tp @s 577 81 -1402 -25 5` | Broken ledges and blocks running into the water. |
| 34 | **Sea cliff** | `/tp @s -236 81 -306 -41 -11` | Sheer cliffs with buttresses and gullies, rubble at the foot, the wave-cut platform under the water. Your problem cove is at about -358 -133. |
| 35 | **Sea arch** | `/tp @s -763 70 -2443 100 5` | A rock fin off the cliffs with a tunnel through it at the waterline. Another: `/tp @s 2229 74 -2307 -128 5`. |
| 36 | **Sea cave** | `/tp @s 258 69 -580 -14 6` | A cave running into the cliff at sea level, flooded at its floor. Another: `/tp @s 719 69 -982 -156 6`. |
| 37 | **Sea stacks** | `/tp @s -755 92 -2525 facing -806 80 -2530` | Three tall stacks off the cliffs, next to stop 35's arch. |
| 38 | **Salt marsh** | `/tp @s -817 81 -389 93 18` | Flat marsh grass laced with tidal creeks and salt pans, mud flats in front. |
| 39 | **Waterfalls** (new) | `/tp @s 644 218 -127 37 -7` | A chain of falls down a mountainside near spawn, 45-75 blocks each, each into its own amphitheatre. Follow the river down (south-east). |
| 40 | **Waterfall** | `/tp @s -1022 364 859 -140 -9` | An 86-block fall. Its neighbour: `/tp @s -1077 286 888 -90 -7`. |
| 41 | **Waterfall** | `/tp @s -1549 97 -925 72 -6` | A 67-block fall lower down, in greener country. |
| 42 | **Waterfall** | `/tp @s -2755 94 -1855 -44 -4` | A 52-block fall. |
| 43 | **Grotto** (new) | `/tp @s -1317 271 -1944 facing -1300 255 -1927` | A collapsed cave: a turquoise pool in a round pit, half roofed by an overhanging shelf with a dry sandy ledge under it, dripstone and lichen on its ceiling, vines from the rim, and a spring stream pouring off the shelf's edge into the pool. Others: 5267 3635 and 2701 -6778 (`/tp @s <x> 300 <z>`). |
| 44 | **Terraced cascades** (new) | `/tp @s -1915 100 8372 facing -1921 95 8290` | Turquoise pools stepping down a hillside over curved white travertine rims, each spilling a sheet of water into the next, the chain fanning out as it goes down. About 8.5 km out: the nearest. Others: -4299 7503 and -5266 6897. |
| 45 | **Canyon fall** (new) | `/tp @s -2740 160 -2052` | A 35-block fall in warm dry country, its amphitheatre walls banded red, orange and pale terracotta (Havasu). Look down into the bowl. |
| 46 | **Icy fall** (new) | `/tp @s 3500 400 -7176` | A fall in the cold: spray frozen onto the walls as packed and blue ice, snow further off. |
| 47 | **Basalt punchbowl** (new) | `/tp @s 6448 470 -7983` | A tall fall in wet country into an amphitheatre of upright basalt columns, mossy near the fall (Latourell). |
| 48 | **Crag mountains** (new) | `/tp @s -980 900 1060 facing -860 760 1000` | Crag country in the megarange: blocky towers, ledges jutting over undercuts, arches and notches, sheer tiers. About 17% of megarange ground is crag; the rest keeps its old look. |
| 49 | **Crag towers** (new) | `/tp @s -3700 200 -420 facing -3618 170 -505` | Three lone rock towers rising sheer out of open country (the BotW twin peaks): stepped on one side, sheer on the other, split at the top into two summits. |
| 50 | **Giant waterfall** (new) | `/tp @s -4470 330 4240 facing -4352 380 4277` | A river pouring 420 blocks off a sheer mountain face into a plunge pool at its foot (Angel Falls). Others: -6264 5428 (279), -5866 5533 (269), 7626 4707 (134), 1499 -7093 (117). |
| 51 | **Open ocean floor** (new) | `/tp @s 23248 40 14544` | Dive: the abyssal plain with a long trench winding across it, seamounts rising off it, and branching submarine canyons down the slopes round it. About 27 km out. Night vision (`/effect give @s night_vision infinite`) helps. |
| 52 | **Blue hole** (new) | `/tp @s -10098 70 -11940` | A round sheer pit 35-55 deep in warm shallows. |
| 53 | **Ha Long Bay** (new) | `/tp @s -1700 110 -10950 facing -1450 80 -10900` | A warm bay crowded with karst towers rising sheer out of the water, 25-80 tall, jungle on their round crowns, each undercut by a wave-cut notch at the waterline. About 11 km out. |
| 54 | **Fjords** (new) | `/tp @s 3000 260 -2180 facing 3150 63 -2100` | Cold mountains cut by fjords: wide U-shaped troughs of sea water winding inland from the coast, walls rising steep (70-80 degrees, with ledges) to the snowy peaks. Rivers feeding the fjord heads drop into them. About 3.5 km from spawn. |
| 55 | **Lake district** (new) | `/tp @s 7480 160 -1200` | Low cool country crowded with long glacial lakes, rocky islands and bare stone shores. |
| 56 | **Meanders and an oxbow** (new) | `/tp @s -1000 110 -760 facing -1057 63 -716` | A lowland river looping in big S-bends with bars in its channel, and the crescent oxbow lake of a cut-off loop beside one bend. |
| 57 | **Braided river and estuary** (new) | `/tp @s -7850 110 -6600` | A wide river braided round gravel bars, flaring into an estuary at the sea beside salt marsh. |
| 58 | **Caldera** (new) | `/tp @s -3790 225 11550 facing -3648 142 11550` | Crater Lake: you stand on the forested rim of a broken volcano. Sheer banded cliffs and grey talus drop to a deep blue lake 300 across, a cinder-cone island in it. The flanks behind you run down a kilometre of gullied slope. About 12 km from spawn. A second one: `/tp @s -11651 220 -4440 facing -11651 167 -4301`. |
| 59 | **Archipelago** (new) | `/tp @s -5044 180 -6173` | A shallow sea studded with islands of every size, skerries round them, channels and sounds between, the mainland's coasts facing it across open water. About 8 km from spawn; others at -12089 11055 and 29039 -17570. |
| 15 | **Mountain stream** | `/tp @s 3290 335 -1470 facing 3400 300 -1560` | A stream cascading down its own ravine through snowy peaks. |

## What to judge

Rough notes are fine. "Too big", "wall looks fake at X Z" or "loved this" are all useful.

- **Rivers:** do they flow now? Any water standing in the open or terrain spikes left anywhere? Do small streams feel like they feed big rivers, and big rivers like they're heading for the sea? Do valleys and gorges look carved by them?
- **Currents:** swim, drop an item or float a boat in a river. Too strong or too weak? (Items race off fast: vanilla gives floating items almost no drag.)
- **Lakes:** they're much rarer now (only real hollows fill). Is that fine, or do you want more?
- **Rock desert:** is it the Wadi Rum / Dune: Awakening look? Enough holes? Too many? Big enough areas, and do the dunes flow into it?
- **Plateau top:** does it feel like its own region now? Are the summit, lake and river enough, or does it want more (ruins-shaped clearings, a second stream, cliffs inside)?
- **Waterfall:** does it read as a waterfall from a distance? Is the river too straight or too narrow?
- **Canyon walls:** any bare stone left outside cave mouths?
- **Caves:** pits, cracks and caverns. Too many or too few?
- **Biomes:** plateau tops now grow their own climate's forests instead of snow. Anything else growing in the wrong place?
- **Performance:** chunk generation while flying, and Distant Horizons.

When something stands out, take a screenshot (F2), note the coordinates (F3) and paste the output of `/grandatlas where`.

## Performance

Distant Horizons generates terrain far past your view, with this mod's generator, on as many threads as you give it. Settings that leave room for the game:

- Vanilla render distance **12** (Distant Horizons draws the rest).
- Distant Horizons CPU load: **4-6 generation threads** (of your 16), or its "Balanced"/"Low Impact" CPU preset.
- **10-12 GB** RAM for this profile (Modrinth App: the profile's settings, Java and memory).

## Changes in 0.9.0 (M7e, first round: cave biomes and new blocks)

Use a new world for these.

- **Cave biomes.** Each has its own rock, floors, formations, fog and drifting particles.
  - **Root caves** under any forest.
  - **Ice caves** under cold land, down to about y -80, ice all the way through.
  - **Sandstone caves** under deserts, down to about y -80.
  - **Fungal forests, crystal caves and vanilla's sulfur caves** as rare random pockets anywhere in the deep and the abyss (fungal forests also fill humid mega-caverns).
  - **Magma chambers** under volcanoes and hot country.
  - **Abyssal shores** round the underground seas.
  - **Glowworm grottos** (new): damp caves in humid country, silk hanging from every ceiling.
- **Formations** in each biome's own blocks: stalactites, stalagmites and columns.
- **Biome decoration:**
  - Roots in the wood of the trees above.
  - Icicles, snow drifts and powder snow.
  - Sand mounds and bone.
  - Shelf fungi with weeping and twisting vines.
  - Selenite beams and crystal clusters on the walls.
  - Cinder-moss carpets.
- **Ten new blocks:**
  - glowworm silk
  - selenite, selenite shard
  - glowcap, glowcap block (bone meal grows a giant glowcap)
  - icicle
  - hoarfrost
  - abyssal sand
  - cinder moss and its carpet
- **New block textures.** Painted in vanilla's style (tools/textures.py), each with a LabPBR specular map for shader packs. Hoarfrost, glowcap blocks and cinder moss pick one of three variants and a turn per block, so big surfaces show no grid.
- The new blocks are in the Natural Blocks creative tab.
- The new blocks haven't been seen in the game client yet: tell me how they look in the world and with shaders.

## Changes in 0.8.0 (M7d: underground water)

Use a new world for these.

- **Cavern lakes.** Six in ten mega-caverns have one or two lakes: bowls in the floor, each filled to a block under the lowest point of its own rim, so its shores hold it. Rising cones and pillars in them are islands and stacks.
- **Underground seas.** One abyss cavern in seven is a sea, 1-3 km across.
  - The sea level comes first and the floor is shaped to it: a basin 50-120 deep, shores 12 above the water all round, islands, sea stacks and great pillars out of the water. (Your note: the first version was "more like a puddle or a couple ponds".)
  - `/grandatlas locate underground_sea`. There's one 1 km from spawn: `/tp @s -621 -290 1102` (water at y -319).
- **Flooded cave systems.** About a third of the land has a water table somewhere between y -320 and -20, flat across regions of ~640 blocks. Every passage, worm bore and rift below it is under water: sumps, drowned shafts, flooded rifts.
- **Waterfalls.** Where a flooded tunnel opens into a dry cavern, its water pours out.
- **Containment.** It uses the same rules as the surface rivers:
  - An open face pours from its top instead of standing as a wall.
  - Vanilla caves, dungeons and geodes that open beside the water are plugged.
  - No cave is cut through a lake bed.
  - On the dev server: 7.4 million blocks of underground water, 0 hanging.
- **Speed.** About 7% of generation in water-heavy areas, less elsewhere.
- **Not yet:** streams running along passage floors (they need step pools to stay contained), and the fantastical water (inverted lakes, rising falls), planned with M7e.

## Changes in 0.7.0 (M7c: mega-caverns and rifts)

Use a new world for these (old chunks keep their old underground).

- **Mega-caverns:** underground landscapes, about one per 7 km², in the deep or the abyss.
  - **Size:** 220-800 blocks across, and the odd one bigger. One in five is a cathedral, half again as tall.
  - **Outline:** lobed, with bays.
  - **Floor:** tilted, hilly, with rubble mounds.
  - **Roof:** rises into domes and sags between them, with scattered spires of all sizes hanging from it. Cones rise from the floor, and hourglass pillars join floor and roof. The walls have ledges and overhangs.
  - **Cover:** always 40+ blocks of rock under the ground; the roof dips under valleys.
  - **Find one:** `/grandatlas locate mega_cavern`. On your seed there's a big one at `/tp @s 5625 -300 -1241`, about 1,150 across.
- **Rifts:** cracks 100-400 deep winding a few hundred metres.
  - **Shape:** the centre wanders as they go down, so walls lean and overhang.
  - **Openings:** a third open to the sky in dry ground; under water they stay sealed.
  - **Great rifts:** one in twelve is 25-60 across, 250-450 deep and over a kilometre long, with natural stone bridges. `/grandatlas locate rift` or `great_rift`; there's one near `/tp @s -4173 120 -1974`.
- Both were approved in the 3D cave survey.
- **What fills them:** caverns come out as open air, with only small water and lava pools from vanilla's aquifers. Proper underground lakes, rivers and seas are M7d.
- **Speed:** mega-caverns are 0.3% of generation time, the passage network with rifts 1.8%.

## Changes in 0.6.0 (M7b: cave systems)

**Make a new world** if yours was made before 0.6.0; old chunks keep their old caves.

- **Cave networks, surface to abyss.**
  - Junctions about 96 blocks apart on stacked levels about 44 blocks apart, rising and falling by region.
  - Passages link them, mostly draining "downhill" like the rivers, with loops in maze country.
  - Whole systems come and go by region: riddled areas, solid areas.
- **Passages wander like vanilla's carver tunnels** (your 3D-survey notes: too regular):
  - Each passage is a steered random walk, so no two bend alike.
  - Width changes along it, with swellings and the odd fat one, and each passage has its own height and floor.
  - Most are tubes with flat floors. Some are low crawls, steep links are shafts, and a few are canyons with a V-trench in the floor that wanders side to side and deepens and fades.
- **Chambers and spurs.** Lumpy chambers, some of two or three blobs, sit at some junctions. Dead-end spurs taper away to nothing.
- **Cave mouths.** Some top-level passages climb out to a mouth on a hillside. Never near water, never below sea level.
- **Worm bores (rare, fantastical).** Round ribbed tunnels 20-36 across that wind down to the abyss: they coil one way, unwind, run level, plunge, swell into chambers, and about a third fork. About 4 in 10 open in a hillside.
  - `/grandatlas locate worm_bore` finds the nearest.
  - On your seed there's an open one at `/tp @s 4001 140 -6440`.
- **Water safety.** Passages stay at least 10 blocks under the ground (26 near rivers, lakes and the sea), so they never open a river bed or drain a lake. Only mouths break the surface.
- **Amount.** The networks open about 1-1.5% of the deep, on top of vanilla's background caves.
- **Speed.** Within measuring noise of 0.5.0 on the same areas.
- Still to come: mega-caverns and rifts (M7c), underground rivers and seas (M7d), cave biomes and set pieces (M7e).

## Changes in 0.5.0 (M7a: the world goes down to -512)

**Make a new world.** The floor moved, so a world made before 0.5.0 would have nothing under its old chunks below y -128.

- **The floor is y -512** (was -128); the top stays at 1024. The surface is unchanged: same heights, rivers and coasts on the same seed.
- **Rock by depth:** stone, deepslate from y 0, and from about -250 the abyss. Its deepslate carries layers of tuff that rise and fall over a few hundred blocks and thicken and pinch out. From about -445 down is a base of blackstone with basalt seams, then bedrock. Lava fills open caves below about -502.
- **Ores re-spread for the depth:**
  - Diamonds through the abyss (most around -300).
  - Gold peaks around -134; lapis and redstone run deeper.
  - Rich redstone still at the floor.
  - Iron reaches the bottom.
  - Geodes, fossils, dungeons and springs keep vanilla's density at every depth.
  - Blackstone and basalt take deepslate ores.
- **Deep structures moved down:** ancient cities at about y -350, in deep dark that now sits in the abyss under high, rugged land. Trial chambers sit at about -160 to -60. Lush and dripstone caves stay above -250.
- **Caves:** vanilla's thin noodle tunnels now reach the floor, so the deep isn't solid. This is still only the background: the real underground (passage networks, mega-caverns, rifts, cave biomes) is M7b onward, in `docs/M7_UNDERGROUND_PLAN.md`.
- **Speed:** generation takes about 40-50% more CPU per chunk than 0.4.1, close to the extra 33% of world. If Distant Horizons falls behind, give it one more thread or a lower CPU preset.

## Changes in 0.4.1 (new landforms, and your 0.4.0 notes)

- **Atoll fields** (new): in warm sea, a scatter of ring reefs a block under the surface round turquoise lagoons, sandy islets on the rings, passes through some, all rising out of deep blue water. Sizes from a little reef ring to big long ones.
- **Volcanic island chains** (new): a line of 3-7 steep cone islands, 150-400 tall, strung out from a coast or across open sea, deep water close round each, some with summit craters.
- **Sea lochs** (new): in temperate hill country, the odd river's lower course is a narrow sea inlet winding a few km inland, widening toward the sea (one river in about seven that qualify). Fjords and lochs now both widen toward their mouths and taper to their heads instead of stopping square.
- **Deltas** (new): a lowland river at least ~13 wide reaching the sea splits into 3-5 channels fanning out across low marsh islands.
- **Rivers 3x wider** (you: "almost all 1 wide streams and that's just not it"): still growing with their catchment the real way, but three times real width, so a stream starts about 3 wide, 8 at 1 km2, 24 at 10, and the biggest reach 60-70. Median river width is now 5.
- **No sand mountains:** a mountain rising inside a dune sea or rock desert was sand to its top; above ~170-200 it now takes the climate's biomes (banded badlands, stone peaks), and dry high slopes are dry hills, not green meadow. Desert above y 250 fell by 90% (what's left is salt pans in high basins).
- **Free falls without the wonky bowls:** a river pouring 100-400 blocks off a sheer face (a plateau's edge) now lands in a wide plunge pool. The small sheer amphitheatres under them, side by side at a plateau's foot, left thin pillars and stepped blocks between them.
- **New tour** (above): every stop found from the generator, so it lands on its feature. `/grandatlas locate` now also finds `archipelago`, `atoll_field` and `volcanic_chain`.
- Land is now 53% of the ground with the new island seas.

## Changes in 0.4.0 (archipelagos)

- **Archipelagos** (new): on about a quarter of 24 km cells where a coast runs, a region 13-24 km across (often drawn out along one heading) becomes a shallow sea studded with islands: a few big ones with hills and coves, many small ones, rocky skerries, with deeper channels and sounds between and deeper water toward the edge. They make their own room: the land inside is pulled down to sea, so the mainland's coast faces each one across open water. Built into the continent field itself, so every coast (beaches, cliffs, stacks, sea caves), the shelves, the rivers and the biomes treat the islands as ordinary land: warm archipelagos get warm seas and their islands' biomes, cold ones cold.
- The world has a little less land for it (58% of the ground, from 67%).

## Changes in 0.3.9 (water from real height maps)

Measured against 26 real places (AWS Open Data terrain tiles: the Mississippi, Appalachians, Rockies, Alps, Maine, the Outer Banks, Hawaii and more).

- **Rivers true to life in width:** a channel's width now follows its catchment the way real ones do (about 2.7 A^0.47 metres for A km2): a 1-block creek where a stream starts, 3 at 1 km2, 8 at 10, 23 at 100. They were about ten times too wide, every stream a canal from its source. Median river width went from 9 blocks to 2; the biggest are still ~25-30.
- **Meanders sized to the river:** loops every 10-14 widths across a belt 5-10 widths wide (a creek wandered in 70-block bends before); oxbows only beside rivers 6+ wide. Plunge pools under falls are a few times the stream's width, not 24 across under a creek.
- **Valleys:** rivers now cut down below the land they drain (3 blocks for a fresh creek, up to 30), and their valleys reach out with their size (75-260 blocks), steepest just above the stream and easing onto the ridges, as real hill valleys do; lowland rivers keep flat floodplains under bluffs. Ground beside a river now rises 4 / 7 / 12 / 15 blocks at 30 / 60 / 120 / 250 out (it was 3 / 3 / 5 / 8; the Appalachians are 1 / 4 / 10 / 22). Where valleys meet, the ridge between them parts their watersheds.
- **Continental shelves:** the sea used to be ~100 deep within 1 km of every shore. Now it shelves gently: 16-18 deep at 1 km, ~30 at 2 km, then the slope down to the ocean floor (real Maine is 20 / 33 at 1 / 2 km). Beaches go into the water gently (1-4 deep 100 out, not 7-13). Shelf width varies by region.
- Checked: all core checks pass, and river water leaks went from 65 open faces to 5; in a generated world the thin creeks hold (no river water with an open side).

## Changes in 0.3.8 (your second round: plateaus, biomes, rivers)

- **Sheer plateau cliffs:** a Great Plateau's walls are 9 blocks wide instead of 34 (straight up on a 200-400 block drop), and it has one or two ramps up instead of two to four.
- **Rivers no longer stop dead in a wall:** the river carve skipped land where the continent field reads as ocean, so a plateau standing there had its rivers cut off along a line, the valley ending in a sheer 20-block wall. Rivers now run wherever there's land.
- **No more water steps on plateaus:** the plateau's own straight river (from before the river network) met the network's rivers at a different level and stood walls of water between them. It's retired; the network's rivers cross plateaus and pour off their edges. The plateau lake stays only where no network river comes near it.
- **Biome edges:** temperature and humidity are smooth over kilometres, so biome edges ran dead straight. The biome table now sees them roughened at three scales: edges wander, tongues reach into each other, the odd patch stands off the edge.
- **Biomes stay grounded:** dune seas, rock deserts and canyon badlands only in warm country now (they were allowed into cool country and inside cold basins, sand next to taiga and snow), and peaks over 0.35 temperature are bare stone, not snow. Desert next to snow on flat ground fell from 1,116 spots to 125 in a 24 km square.
- **The live map** (new): `gradlew mapServer`, or ask me, opens a map of the generator in a browser: terrain, biomes and 5-block contour lines; click a spot for what's there and a /tp to it.

## Changes in 0.3.7 (speed)

- **Faster terrain:** the rain shadow (a quarter of all terrain time in a profile) is now worked out on a 64-block lattice and cached. Columns are about 15% cheaper. Humidity shifts very slightly, so make a new world.
- **Your lag was mostly Distant Horizons' settings:** 16 generation threads (all your cores) at full time, a 256-chunk LOD distance and a vanilla render distance of 23 on 8 GB. It left the game's own server thread no CPU, so it fell 5-15 seconds behind. See "Performance" below.

## Changes in 0.3.6 (fixes from your playtest)

- **Floating walls of water in a valley** (your -7162 7140): a fjord had started up on a 410-high plateau and cut its trough 350 blocks down through it to the sea, under the plateau's own river. Fjords no longer start on plateaus or massifs, and water a plateau sets can't stand over a valley cut under it.
- **Two falls stacked in one bowl** (a 420-block fall landing 40 blocks from the lip of the next): no fall starts inside the last one's amphitheatre now. Plunge pools are at most 10 deep (they reached 50 under the giant falls).
- **Waterfalls that don't fall:** the boulder rapids were dropping boulders into falls' spouts and damming them. No boulders at a lip or just above a fall now.
- **Standing walls of water at drops** (your oasis): every block of a pool's open edge was a water source, and sources don't fall. Now only the top block is a source and the water pours down from it.
- **The oasis lake stood 7 over its shore:** lakes now check their real rim block by block, including the band just past the shore, and fill only to under its lowest point. That oasis is now 6 lower and holds.
- **Grooves scored across open ground:** vanilla's thin noodle caves were breaking the surface. They now stay at least 4 blocks under the ground (cave mouths still open).
- **Currents turn in from the banks:** near a river's edge the current now runs diagonally toward the middle, so you, mobs and boats get carried off the bank instead of pinned against it. Mid-channel it still runs straight downstream.
- **The criss-cross stripes on steep slopes:** a steep slope stepped into a regular grid of grass tops and dirt risers. Ground steeper than about 55 degrees is now bare stone with andesite patches, with coarse dirt and gravel in a ragged band at its edge.
- Checked: no water anywhere in a 16 km square stands more than 14 blocks over its own ground (the plateau slot and the double bowl did); all core checks pass; in a generated world the fixes are there.

## Changes in 0.3.5 (calderas: M4 phase 5, crater lakes)

- **Calderas** (new set piece, Crater Lake): in open hill and lowland country, now and then, the stump of a great volcano 600 across. Its rim stands 60-95 over the land round it, with a few peaks on it and a rounded top you can walk round. Inside, banded cliffs (grey andesite and tuff, dark basalt, the odd pale or rust layer) drop 20-45 blocks onto grey talus, which runs on down under a deep lake 30-45 below the rim. The lake has its own biome, **Crater Lake**, for its deep blue water. Seven in ten have a cinder-cone island. The outer flanks fall away steep at the top and easing out, cut by uneven gullies. About one per 100 km² on the dev seed; in the hardest cold the lake freezes.
- **Fix:** a meso site's radius could reach past its 800-block cell, so a big lake district's edge could stop on a straight line where the next cell began. Sites now always fit their cell.

## Changes in 0.3.4 (oxbow lakes)

- **Oxbow lakes:** beside some of a lowland river's full meander bends, the crescent lake of a loop it has since cut off, its horns pointing back at the river. About 90 within 8 km of spawn. One at -1057 -716, next to a river looping in big S-bends (`/tp @s -1000 110 -760 facing -1057 63 -716`).

## Changes in 0.3.3 (river islands and braided rivers: M4 phase 4, part 2)

- **River islands:** wide rivers get the odd long island mid-channel, drawn out along the flow.
- **Braided rivers:** in some regions wide rivers braid: many low gravel bars just out of the water, the river running between them. Example: the big river running into the sea at about -7800 -6650 (`/tp @s -7850 110 -6600`), which also shows an estuary and salt marsh.
- Oxbow lakes are still to come.

## Changes in 0.3.2 (rivers meet the sea: M3 phase 3)

- **Estuaries:** a lowland river's last reach before the sea flares into a funnel up to about three times its width.
- **Barrier islands:** off low sandy coasts in places, a long low island of sand and dunes runs along the shore 60-130 blocks out, broken by tidal inlets, with a shallow lagoon behind it. They come out shorter and more broken than I'd like on the dev seed's hilly coasts; tell me if you want more of them. One near spawn at about 960 -544.
- Deltas (rivers splitting into channels across a fan of islands) are still to come.

## Changes in 0.3.1 (lake districts: M4 phase 5)

- **Lake districts** (the Canadian Shield), a new site in cool, wet, gentle country: 14-28 lakes of every size, the big ones drawn out long along one heading as glaciers scoured them, with rocky islands and bare stone, gravel and cobble shores. Nearest on the dev seed: about 7.5 km out at 7480 -1200 (`/tp @s 7480 140 -1200`).
- Big lakes anywhere (over ~15 across, in pond country too) are now elongated and get the odd rocky island.

## Changes in 0.3.0 (meandering rivers: M4 phase 4, part 1)

- **Rivers wander:** every river short of a mountain torrent now swings gently from side to side (routed on an 8-block grid they ran in ruler-straight diagonals), and on flat ground they meander in full: loops about 14 of the river's widths long, swinging out up to 30% of that, their rhythm wandering. Reaches still meet cleanly at their junctions, and the water level and banks follow the new course, so it stays contained (checked: no leaks beyond the usual).
- Oxbow lakes and braided gravel rivers (the rest of phase 4) are still to come.

## Changes in 0.2.9 (fjord water)

- A fjord's water is now still vanilla sea water (it was river water with a current), so kelp and seagrass grow in it and it freezes over in the cold like the sea. Checked in a generated world.

## Changes in 0.2.8 (fjords: M3 phase 6, part 2)

- **Fjords:** where a river reaches the sea through cold mountains (mountains standing over it on two sides, within ~3 km of the coast), its lower course is a drowned glacial valley: a wide flat-floored U-shaped trough 18-36 under the sea, its width varying along it, its walls leaning back 70-80 degrees with ledges, up to the peaks. The trough runs on to the sea from wherever it starts, and the water is the sea's (an ocean biome, no current). Rivers above the fjord head step down into it, so the falls on them are its hanging-valley waterfalls.
- **Fix:** last night's sloped banks beside river pools (0.1.8) reached too far: a river's bank protection was holding whole valley walls up 60 blocks away. It now only acts within 12 blocks of a bank.

## Changes in 0.2.7 (Ha Long Bay: M3 phase 6, part 1)

- **Karst bays** on warm coasts (a slow regional field): dozens of karst towers rise out of the water across the bay, out to 400 blocks from shore, 25-80 tall and 14-36 across, sheer-sided with round jungle-topped crowns, each undercut by a wave-cut notch at the waterline. About 290 towers within 12 km of spawn on the dev seed.
- Fjords (the other phase 6 set piece) are still to come.

## Changes in 0.2.6 (the sea floor: M3 phase 2)

- **Shelf:** sandbars along the shore, patches of sand ripples (their spacing and heading wander), and patches of broken rocky reef standing a few blocks off the sand (vanilla coral grows on reefs in warm seas, kelp in cool ones).
- **Slope:** submarine canyons, few and broad, branching down the slopes like real canyon systems.
- **Abyss:** hills as before, rare long trenches (to about -95), and seamounts on about a third of 2.4 km cells: volcanic cones 60-120 tall; a third of them guyots planed flat 20-35 under the surface. In warm seas half of them are atolls: a reef ring just under the surface round a lagoon 7-12 deep (rare: warm open ocean is rare near spawn on the dev seed).
- **Blue holes:** round sheer pits 35-55 deep in warm shallows.
- Checked from the terrain code (maps of the floor, seams, heights); not yet in a generated world.

## Changes in 0.2.5 (boulder rapids)

- **Boulder rapids** (your photo of a river breaking through rounded rocks): where a river's water drops fast (1.2+ blocks over 6 downstream, its step pools), great rounded boulders of andesite and stone stand in the channel and out of the water, mossy where it's mild and damp, the water running between them. Follow any mountain stream down (stop 39's river, or stop 12).

## Changes in 0.2.4 (giant waterfalls)

- **Free falls:** where a river reaches a near-vertical mountain face too tall for an amphitheatre at its lip, the face is now left as it is and the river pours off the lip down it, 100-420 blocks, into a plunge pool and amphitheatre at the foot. Before, these became a staircase of tiny steps cut into the face. Where a crest stands just before the drop, the river's channel runs on through it to the edge. Five on the dev seed within 8 km.
- Checked in a generated world: water runs down the whole 420-block face into the pool (it spreads into a broad veil over the face's ledges).

## Changes in 0.2.3 (crag mountains)

Your "more aggressive, but only sometimes" mountains.

- **Crags, a new megarange style** next to alpine, dolomite and needles, on a slow regional field (about 17% of megarange ground):
  - Summits split into towers by short, straight, sheer clefts.
  - Blocky tiers on the high slopes, sharper than the dolomites'.
  - Sharper tips.
  - Leaning slabs of rock on the flanks (your Avatar pillars): blades rising to an end that leans out over its foot.
  - Stronger 3D rock: ledges that jut over undercuts and deep vertical cracks, so faces overhang.
- **Crag towers**, a new site in open country: one to three great rock towers, 45-150 tall, sheer out of the plain, stepped on one side and sheer on the other, split by a cleft into two summits.
- **No floating rock:** the crags' undercuts could cut a bit of surface loose, so a cleanup pass in crag country removes anything not joined to the ground (0 floating blocks in a generated test area).

## Changes in 0.2.2 (waterfall styles)

- **Each river fall's amphitheatre now takes a style from its climate:**
  - **Mossy stone** (most): as in 0.2.0.
  - **Basalt punchbowl** (Latourell): tall falls (30+) in wet country, on about 3 in 5: walls of upright basalt columns, mossy near the fall.
  - **Red canyon** (Havasu): warm dry country: walls banded red, orange, white and plain terracotta.
  - **Icy** (your frozen Skógafoss): in the cold, the spray frozen onto the walls as packed and blue ice, snow further off, ice and snow on the floor. Cold falls had no dressing before.
- Checked in a generated world: each style's blocks are there at its fall. Stops 45-47 are approximate (the fall's lip); look around for the bowl.

## Changes in 0.2.1 (waterfalls, part 2: set pieces)

- **Grotto** (your Hamilton Pool photo): a new site. A round pit 18-28 across and 17-24 deep holds a turquoise pool with a sand beach. On one side an overhanging shelf of rock roofs a dome-shaped cavity with a dry ledge under it: dripstone, glow lichen and vines hang from its ceiling. A spring on the shelf feeds a short stream that pours off the rim into the pool. Sited on level, humid ground; the stream's direction is wherever the ground stays high enough to hold it.
- **Terraced cascades** (your Kuang Si photo): a new site on wooded hillsides. A chain of wide turquoise pools steps down the slope, 2.5-4 blocks per step, each held by a curved, wavy white travertine (calcite) rim that the water spills over in a sheet. The chain wanders across the slope and fans out as it goes down; where the ground is too low to hold a pool, travertine is built up (never more than 3.5 blocks). Moss and ferns on the dry walls.
- Both checked in a generated world (water held, no open sides on the terraces; the dressing is there). Not yet seen in a game client: the screenshot tool can't capture while your screen is off.

## Changes in 0.2.0 (waterfalls, part 1: on the rivers)

You asked for many more waterfalls. This is the first part: falls on the river network. Designed set-piece falls come next (docs/M3_M4_PLAN.md, "Waterfalls").

- **Where a river's course drops 10+ blocks within 40, it now drops in one fall** (on 4 in 5 such places) instead of stepping down. About one fall per km² on the dev seed, 10-90 blocks tall (median ~33).
- **Each fall has an amphitheatre:** a sheer-walled bowl stretched down the valley, its rim lobed and ragged, carved back round the lip so the river pours off a spout of rock. A deep plunge pool lies under the fall, a rocky floor rings it, talus rises against the walls, and the river leaves by the bowl's open side.
- **Dressing:** the walls go mossy where the spray reaches them (thickest by the fall, in smooth patches), vines hang down them, and the floor gets moss, ferns and the odd mossy boulder. Cold places keep bare stone.
- Falls that would need a bowl more than 90 deep (a near-vertical mountain face) stay as steps for now; very tall cliff falls are planned as set pieces.

## Changes in 0.1.9 (your step-pool spacing idea)

- **Mountain-stream pools are spaced by the room the slope gives them.** Before, every pool had to run at least 5-13 blocks and lay flat at its lowest point, so on a steep slope its upstream end was cut 10+ blocks into the hillside: the box with sheer walls. Now each pool may only fall 2.5-5 blocks along its length. On gentle ground pools run long (up to ~48 blocks); on steep ground they are short, a staircase of small falls. Lengths are random within that room. A real cliff still makes one big waterfall.
- Measured on the dev seed's mountain streams: the deepest 1% of cuts beside the water went from about 10 blocks to about 7.
- Plunge pools (the wide pool under a fall) only under falls of 4.5+ blocks now, so the small steps don't each get a bulge.

## Changes in 0.1.8 (your second coast look)

- **Shingle:** stone and cobble are back, laid out in smooth noise patches over the gravel instead of speckled.
- **Thin ridges in the sea along cliff coasts** (your "ruins this coast line"): the terrain is blended from samples 4 blocks apart, and next to a sheer cliff that blend reached a few blocks past where the cliff stops, leaving thin sloped ridges standing in the water. Every column in a margin round cliffs and sea stacks is now cut exactly.
- **Trees in the water:** ground standing out past the shoreline (rubble, little islets) took the land's biome, so forests (flower forest near the sea caves) grew trees in the shallows. All of it is shore biome now, and site boulders, logs and big trees never place in the sea.
- **Ocean bands:** the edges between ocean biomes (frozen, cold, normal, deep and shallow) follow noise now instead of clean curves, since each one has its own water colour.
- **Sheer walls beside mountain-stream pools:** a lower reach's valley cut right up to an upper pool's protected bank, leaving a thin wall of rock. It now rises to the bank at a steep slope. (These were there before 0.1.7 too.)
- **Fern gully** (stop 29): it was there, but only 4-8 blocks deep and wide, so it vanished under the trees. It's a real ravine now, 9-16 deep with near-sheer sides.

## Changes in 0.1.7 (your coast notes)

The tour stops above moved a little with the coastline, so use the new commands.

- **Shingle:** no cobble (reversed in 0.1.8: you wanted the cobble kept, just in a noise pattern).
- **Sea cliff problem spot:** the shore line could fold back on itself, leaving islets and a ragged notch in the cliff; the same thing stood thin spires off cliff faces (the pillar by the sea cave). The coastline noise is now kept from folding, and the cliff line is ragged at buttress scale only.
- **Cliffs:** the sections where the upper face leaned back came out as dirt staircases, so cliffs are sheer all the way now.
- **Sea stacks:** the wave-cut platform sat at the water's surface and showed as a flat grey stone plain; it's under the water now. Stack tops are flat instead of stepped domes (those came out striped with snow).
- **Marsh:** the tidal flats and creek mouths are marsh biome now, not ocean, so ocean ruins and wrecks no longer stand on them; the flats are under a block of water. Marsh with trees is noted as its own biome for later.
- **Mountain streams:** each pool has its own shape now: it bows out to one side, one bank lies further out than the other, the plunge pool below a fall varies in length, and the edge is ragged close to.
- **Lakes on flat ground:** the ragged shore could leave ground a hair under the water level dry beside shallow water; lakes now fill it.

## Changes in 0.1.6 (coasts: M3 phase 1)

Stops 30-38. Your first look at the coasts.

- **Four kinds of coast** instead of a sand stripe, chosen along the shore by slow noise and the land behind (high land gives cliffs, low wet land gives marsh), blending into each other:
  - **Beaches** with a sloping foreshore and a berm; **dunes** behind them where it's dry; **shingle** (new `grandatlas:pebble_beach` biome) on stony stretches.
  - **Sea cliffs** 15-50 blocks tall, sheer, with buttresses and gullies, some sections leaning back, rubble at the foot and a wave-cut platform just under the water.
  - **Rocky shores** of broken ledges.
  - **Salt marsh** (new `grandatlas:salt_marsh` biome; mangrove swamp where it's hot): open grass with mud patches, winding tidal creeks, salt pans and mud flats.
- **Bays and headlands:** the coastline wanders at several scales, with narrow rocky headlands on hard coasts.
- **Sea stacks, arches and sea caves** off and in cliff and rocky coasts.
- Mountains meet the sea as a steep slope over the shore instead of a sudden wall, and river water no longer runs on over the sea as a river biome.
- Fixed: on steep cascades, a pool edge could skip a whole pool and leave a dry trench beside high water.

## Changes in 0.1.5 (water that isn't uniform; the plateau lake and fall)

- **Every body of water is less regular** (smooth noise, so still seamless):
  - Rivers swell and pinch by up to about 40% of their width.
  - Pool edges wander a few blocks instead of crossing the channel square.
  - River beds undulate.
  - Lake and pond shores are ragged, with small coves and points.
  - Cenotes and the plunge pool aren't circles any more.
- **The plateau lake** isn't a circle: a lobed, drawn-out outline, sitting in the lowest of several hollows on the plateau.
- **The plateau waterfall falls clear.** Where the river leaves, the wall is a sheer face, and the lip is undercut 4 blocks deep, so the water drops straight into its plunge pool instead of hitting the wall. The pool comes right up to the face and no longer cuts into the cliff.

## Changes in 0.1.4 (Great Plateau walls)

- **Rare rim features,** about one per 1000 blocks of wall: a **cove** biting 40-90 blocks in, a **peninsula** pushing 50-120 out, a ragged **rocky outcrop**, or a **spire** standing just off the wall. The tour plateau has coves at -4933 6376 and -5647 9127, peninsulas at -5530 5734 and -6689 9407, outcrops at -4679 7106 and -8257 8635.
- **The lip varies** along the wall within about 5 blocks: in places it juts out over the face, in others the top rounds over.
- **Caves that meet the wall are meant now:** vanilla caves are kept out of the face (no half-blocked holes), and the wall has its own cave mouths, arched with flat floors, running 12-28 blocks in, about one every 400 blocks of wall.
- Less rough texture on the face, so mouths and edges read cleanly.

## Changes in 0.1.3 (the plateau waterfall)

- **A plunge pool under the Great Plateau's waterfall.** The plateau's river used to pour off the rim onto bare ground. Now it lands in a pool at the cliff foot, 30-50 blocks across and up to 7 deep, held just under the lowest ground round it. If a river already runs past the foot, the fall lands in that instead. Tour stop 9 looks at one (pool at -5866 111 9242).

## Changes in 0.1.2 (your river screenshot)

- **River valleys widen with how deep they cut,** so their sides slope (30 degrees at most) instead of standing sheer where a river drops fast past high ground. In mountain country that cut sheer steps along rivers to a third.
- **Pools vary:** each pool on a stream draws its own length (5-13 blocks) and drop, so a long descent isn't a run of identical steps.
- Ponds and pits now measure their rims after rivers have cut them, so a widened valley can't open them.

## Changes in 0.1.1 (your quick look)

- Far fewer fallen logs: a third to a half as many in every site, deadfall keeping the most.
- Smaller glades (90-160 blocks across) and pale groves (120-220).

## Changes in 0.1.0 (forests)

Five new kinds of site, all in forests:
- **Ancient groves:** two to four giant trees, 25-40 blocks tall with trunks 3-6 across, buttress roots, great branches and broad crowns, in old-growth pine or birch forest. Podzol, moss and leaf litter under them, roots snaking over the ground.
- **Mushroom hollows:** giant red and brown mushrooms (8-19 tall) in a patch of dark forest, on mycelium and podzol, small mushrooms everywhere.
- **Deadfall:** dead snags still standing (bark at the foot, weathered bare wood above, broken branch stubs), fallen trunks everywhere, podzol, coarse dirt and leaf litter.
- **Pale groves:** a patch of the pale garden inside an ordinary forest.
- **Fern gullies:** winding shallow ravines through the woods, floored with moss, ferns and tall ferns.
- **Glades** now have a fairy ring of mushrooms.

Also: which kind of site a place gets is now decided per kind, so adding new kinds later only changes the places a new kind wins. This release moved some sites once; the tour has the new spots.

## Changes in 0.0.9 (four more kinds of site)

- **Butte fields:** dry country with a scatter of small flat-topped buttes in banded terracotta, each with a stepped side and a talus apron. A little Monument Valley.
- **Tor hills:** open hills crowned with granite tors, one to three stacks of rounded, squarish blocks.
- **Tarn basins:** small cold lakes in high mountain hollows, with bare stone and gravel shores, in open alpine meadow (snowy where it's cold).
- **Erratic fields:** open plain or tundra strewn with great boulders the glaciers left, a quarter sunk in the ground and mossy on top.
- Rock is coloured in broad smooth patches, never per-block speckle (the lesson of the cinder cones).
- `locate` knows them: `butte_field`, `tor_hills`, `tarn_basin`, `erratic_field`.

## Changes in 0.0.8 (your first look at the sites)

You liked the glade and the ponds; the other three were reworked:
- **Hoodoos** stand in a patch of red badlands, so they're banded terracotta (Bryce Canyon) with a grey caprock instead of grass and dirt. They're thicker.
- **Cenotes** are wider with their pools 8-14 blocks under the rim, in sight: white limestone walls, more vines, turquoise water.
- **Cinder cones** have straight flanks and a sharp crater rim, and their cinders come in broad patches (red only round the crater) instead of a speckle.
- No feature sits on a river any more: a cone a river cut in half looked like a cliff.

## Changes in 0.0.7 (meso and micro features)

The Macro → Meso → Micro → Detail chain is in. A **meso site** is a place 140-600 blocks across inside a province, with its own character; it lays out **micro features**, and those bring their **detail**.

- **Five kinds of site.** Pond country (humid, gentle land), sinkhole fields (karst; cenotes where it's wet), hoodoo gardens (dry country), cinder fields (small volcanic fields), glades (clearings in forests). Each is judged by its place, not owned by a biome.
- **Micro features:** ponds, sinkholes, cenotes, hoodoos, cinder cones, basalt column clusters. Pillars and pit walls are exact to the block; ponds and cenotes hold their water, and none sits on a river.
- **Detail:** lily pads, reeds and grass round ponds; vines, moss and glow lichen in sinkholes; cinders on cones; boulders and fallen logs over the sites.
- **`/grandatlas where`** shows the site and feature in the stack, and **`/grandatlas locate`** finds the nearest site of a kind: `pond_country`, `sinkhole_field`, `hoodoo_garden`, `cinder_field`, `glade`.
- Roughly one site every 1.5 km², more where the land suits them.
- **Make a new world:** old chunks have no sites.

## Changes in 0.0.6 (your "still bad flow, and now spikey terrain" report)

Rebuilt after looking at how vanilla, TerraForged, Tectonic and Dynamic Waters do rivers.

- **No more spikes.** Rivers and lakes only ever cut the ground down. 0.0.5 built banks and levees up to hold water, and those were the spikes.
- **Rivers follow the valley floor.** Each stretch is routed along the lowest ground between its ends, then smoothed, instead of a curve that ran along hillsides. Near the coast, rivers run at sea level in their own valleys.
- **River water.** Rivers and lakes are filled with a new water that stays where it is put instead of spreading. Above sea level, vanilla water ran out of every gap. It is still water in every way you'll notice: you can swim in it, boat on it, fish in it, scoop it up as a water bucket, and it looks the same. Where it meets open air it turns into ordinary water, so a lip pours as a waterfall into the pool below. Dig into a bank and it flows out like any water.
- **Currents.** River water flows downstream. It pushes you, mobs, items and boats along (a boat drifts about 2-3 blocks a second), the surface texture runs downstream, and you can hear running rivers.
- **Tributaries fall into big rivers** as a waterfall where they reach the bigger river's banks, instead of running alongside it as a wall of water.
- **Lakes fill real hollows,** up to just under the lowest point of their rim. They're shaped by the land, not round. They are rarer: a dip in a plain no longer makes a lake that holds the rivers up at ground level.
- **Exact banks.** The world smooths terrain on a 4-block grid, which rounded river banks a block below the water. River cuts are now exact to the block. Where a vanilla cave tunnel crosses under a river or through its bank, the hole is filled in.
- In the two worst spots from your screenshots, no river water is open to the air.
- **Make a new world:** chunks from 0.0.5 keep their spikes.

## Changes in 0.0.5 (your floating water report)

- **No more floating water.** Water never stands in the open: every dry block beside water is at least a block above it. Where the natural bank is too low, a small bank or levee forms. At the spots in your screenshots the open water faces went from up to 14,000 blocks to none.
- **Streams step down in pools instead of sheets.** On a slope a river is a chain of flat pools. Each one spills over a rock sill into the next: a riffle on gentle ground, and on steep ground a waterfall of up to 16 blocks pouring from a narrow chute into a wide plunge pool, then on to the next. Only true cliffs make bigger falls. Steep streams run narrower.
- **Rivers don't drain into caves.** No cave, pit or crack opens under a river or lake or beside it (one cave mouth under a gorge was pouring a river into the cave below).
- **Lakes sit at their river's level.** No more raised slab of lake beside the river. Lakes only form where the river leaves them gently.
- **Make a new world:** chunks from 0.0.4 keep their floating water.

## Changes in 0.0.4 (your river request)

- **Watershed rivers everywhere.** Rain falls on every part of the land (a tenth as much in deserts) and drains downhill to the sea. Springs start where enough water gathers, streams join into rivers, and rivers widen as tributaries come in: 3-7 blocks wide at the head, up to 80 near the coast. All the rivers checked reach the sea, and none runs uphill or loops.
- **They follow the land.** Rivers keep to valleys. Where one has to cross a rise it cuts a gorge, and where it leaves high ground it cascades or falls. Big rivers run on floodplains between valley walls.
- **Lakes.** Where a river passes through a hollow it pools into a lake and flows on out. The rare basin with no way out keeps its water as a lake.
- **River biome.** Grand Atlas's own River, with gravel and sand beds. It stays unfrozen at height (frozen rivers only in cold climates).

## Changes in 0.0.3 (from your second playtest)

- **Rock desert:** a new landform after Wadi Rum and Dune: Awakening. Red sand runs between wind-carved sandstone: jebels with slot canyons, towers, spires and fins. All of it is carved in 3D: holes, arches, undercut feet and overhangs, and grooves along soft layers. It forms large districts that range from open sand to a maze, and pairs with dune seas so the dunes run into it.
- **More holes in the dune outcrops,** and the odd tower or fin in open dune seas.
- **Great Plateau tops:** a landmark summit, a lake, and a river from the lake to the rim that pours off as a waterfall. Relief is higher, with more hills and uplands and the odd ridge. Plateau tops grow forests and fields of their climate instead of turning alpine from edge to edge.
- **Canyon walls:** the bare stone came from cliff faces being given a cave biome (dripstone caves), which skips the terracotta bands. That's fixed, and the rim's steps are banded too. Pit and fissure walls in badlands are banded as well.
- **Caves:** vanilla caves are damped half as much again (0.06), on top of 0.0.2's new pits, cracks and caverns.
- From the first half of your notes (already in your 0.0.2 build): jagged, varied mountains; macros every 5-6 km; flatter salt flats; mesa edges that drop straight into the desert.

## Known gaps (not bugs)

- Deltas, estuaries, braided rivers, marshes and lake districts are still to come; rivers don't yet follow canyon floors on purpose.
- Lakes are rare (only real hollows fill). Lake districts, tarns and valley-floor lakes are still to come.
- Grand Atlas's own biomes so far: Salt Flat, Red Desert, River. Everything else is vanilla.
- No real-world wonders beyond these yet, and no fantasy landforms (M5 and M6).
