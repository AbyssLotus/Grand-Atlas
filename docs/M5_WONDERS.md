# M5 Real-world wonders: tepuis, rift valleys, rainbow mountains, Spotted Lake (0.13.0-test28, 2026-11-13)

The user, 2026-11-13: "also work on Tepuis, Rift Valleys Spotted Lake and Rainbow Mountains". Built and tested on
1.21.1 only; the 26.3 data has not been regenerated.

## Landforms (province level, `core/landform`)

| Landform | Biomes | What it is |
|---|---|---|
| `tepui` | `tepui_summit` on top; jungle, bamboo jungle, sparse jungle below | Hot, wet country. One table mountain per province: radius 90-250, its top 230-400 over a lowland of jungle hills, walls sheer all round (lobed, a few soft clefts), a talus apron. The summit is cracked into a maze, tuff/blackstone/moss/grass. Falls: 1-4 streams let into the summit pour off the rim (`world/FantasyDetail`, ticked water sources). |
| `rift_valley` | savanna/plains floor, savanna plateau or meadow on the steps, windswept hills on cones | Warm, dry to middling country. A floor 240-400 wide along a wavering line through the province, 3-4 fault scarps either side (each a sheer drop of 40-66 with a back-tilted shelf), one or two cones 90-180 tall on the floor. Rivers often run down the floor. |
| `rainbow_mountains` | `rainbow_mountains` on ridges; savanna/plains in the valleys | Dry country of sharp-crested, gullied ridges 40-190 tall. The ridges are striped by `grandatlas:rainbow` (`world/RainbowBands`), a surface rule: 30 bands, 1-6 thick, of red, orange, gold, cream, lime, teal, pink, purple, magenta and brown terracotta, folded gently. About 30 blocks of bands under the surface, stone below. |

Shares in a 192 km square (checkCore): tepui 0.4%, rift valley 0.8%, rainbow mountains 0.7%.
Wonder pairs: rainbow mountains with canyon plateau and rock desert, rift valley with volcanic wastes.

**Province layout gotcha.** Province centres (`Province.x/z`) are in the mosaic's *warped* space, up to ~450 blocks
from the real centre. A landform that lays out something round the centre must work in warped space:
`ProvinceMosaic.warpX/warpZ` (new) give a column's warped position, `unwarp` the reverse. Tepui and RiftValley are
bound to the world mosaic by AtlasCore (`bind`). Tepui also shrinks each mesa to fit half the distance to the nearest
neighbouring centre, less the taper, so it stands whole (some tepui provinces hold none).

**Sheer walls.** Envelope (`Shape.envelope`) must cover a wall sideways too: the tepui takes the rim's farthest reach
over headings within 10 blocks either side, else the walls of a cleft fall between the 4-block grid's corners and
whole grid cells come out as air. No 3D roughness on the summit or under the wall.

## Site (`core/micro/MesoKind.SPOTTED_LAKE`, dressed in `world/MicroDetail.spotted`)

Dry grassland, gentle ground (ordinary country, rift floors, rainbow valleys, playa fans). Three ponds per site
(one 24-32, two 14-22); a pond only holds on level enough ground, so those that fit stand. Each pond's water is
broken into pools on a jittered 8-block grid: cell middles stay water one or two deep over a coloured floor
(lime/green/yellow/cyan/light-blue terracotta and concrete), cell edges are built up with crust (calcite, white
terracotta, smooth sandstone, diorite, packed mud) to the water's level, so each pool is walled off. A dry crust
ring round the shore. Biome `spotted_lake` over the water (pale clear water). Nearest to the tour spawn: 382 -2034.

## The Grand Atlas book (this round)

- The GUI texture (`tools/atlas_art.py`) is redrawn 1:1 at GUI scale in the game's own style (hard pixels, dark
  outline, bevelled leather, quantised paper blotches, red stitching), 400x250 on a 512x256 sheet.
- Places: every find is noted with where it lies (its centre: province, macro, site, set piece, isle, island sea;
  the player's spot for cave biomes). A new one of a kind you already have is noted when it is far enough from the
  others (per kind: 40 for a Spotted Lake basin, 200 for a tepui, 1500 for a landform province...), up to 24 per kind.
  The page has Notes and Places tabs; click a place to copy its coordinates. Ripped maps note the place they mark.
  Saved in the `atlas_progress` attachment (old saves, a bare list, still load). Sync payload version "2".
- New pages: Tepuis, Rift Valleys, Rainbow Mountains (Landforms), Spotted Lakes (Sites), each with a model.

## Gotcha

`tools/gen_data.py` reads `BiomeKind.java` up to the first `;`: a semicolon in a comment inside the enum list
cuts off every biome after it (they silently fall out of the dimension's biome table).
