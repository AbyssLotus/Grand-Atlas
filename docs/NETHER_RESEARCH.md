# The Nether: what others have done, and what Grand Atlas could do (research, 2026-11-19)

Research only. Nothing in the code or data changed. Everything is for Minecraft 1.21.1 NeoForge
(`mc1211/`). Every claim links its source. Where a point is my own reading rather than a source's,
it says so ("my read").

**Licence rule followed.** Stardust Labs' licence (Amplified Nether, Incendium, Terralith, Nullscape,
Structory, Continents) forbids using their work "to configure, test, debug, or augment" any AI
system, even indirectly ([licence text](https://raw.githubusercontent.com/Stardust-Labs-MC/license/main/license.txt)).
So for those projects only public pages and wiki descriptions were read, never their files. No mod
files were downloaded. The one repository looked at was Regions Unexplored (MIT), and only its
file list.

**A gap to know about.** Reddit could not be fetched from here, and CurseForge comments and
moddex.gg had almost no reviews. Player opinion below is therefore thinner than I'd like. It comes
from Mojang's feedback site, GitHub issues, rival mods' pages and press write-ups.

---

## Summary

- **Vanilla's Nether** is a 128-block slab (bedrock floor at 0–4, roof at 123–127) filled with
  lava up to Y 31. It is shaped by one old 3D noise, the same basic shape since Alpha. Five biomes
  sit in flat columns chosen by two noises, and they only change the surface, not the shape.
  Players find it flat, samey and empty once they have their blaze rods and netherite.
- **Mods split three ways.**
  - Height and terrain reshapers: Amplified Nether, Atmospheric Nether, Nether Expanse,
    Altitudo, Topless Nether.
  - Biome and content mods that keep vanilla's shape: BetterNether, Regions Unexplored, Biomes
    O' Plenty, Gardens of the Dead, Cinderscapes, Infernal Expansion Redux, Luminous, Nether
    Depths Upgrade.
  - Incendium sits in the middle: new terrain, 3D biomes, hard structures.
  
  The reshapers clash with each other (Amplified Nether and Incendium are openly incompatible),
  while the biome mods mostly stack.
- **Structures** are where most "reason to go" comes from: YUNG's Better Nether Fortresses
  (already in the user's Create Atlas pack), Better Bastions, Repurposed Structures, MNS,
  Formations Nether, Eternal Nether.
- **What no one has really done well:** a Nether that is *composed*, with sightlines, a sense of
  depth and places that feel different, and still fits on one dimension's height budget. Atmospheric
  Nether (continents split by cliffs and lava rivers) is the closest in spirit. It is discontinued
  and carries a custom licence.
- **Recommendation:** Grand Atlas should own the Nether's terrain (direction A below). Raise it to
  roughly 256–384 blocks, keep a roof but break it in places, and build a few huge composed
  landforms with lava as the main feature. Leave biome injection open for other mods, and keep
  structures coming from vanilla, YUNG and mods the user picks. Direction C's open-sky regions can
  come later as a rare surprise.

---

## 1. Vanilla's Nether (1.21.1) as a baseline

### Height, roof and floor
- The dimension type has `min_y` 0, `height` 256 and `logical_height` 128. It has a ceiling
  (`has_ceiling` true) and no skylight, and `ambient_light` is 0.1. The coordinate scale is 8
  ([1.21.1 dimension_type/the_nether.json](https://raw.githubusercontent.com/misode/mcmeta/1.21.1-data/data/minecraft/dimension_type/the_nether.json)).
- Terrain generates only in Y 0–128 (noise `height` 128). The bedrock roof is a rough 5-layer band
  at 123–127, and the floor is the same at 0–4. Players on the roof can still build 128 blocks up
  ([wiki: Nether](https://minecraft.wiki/w/Nether)).
- `logical_height` only caps where portals and chorus fruit can send you. Portals built higher
  still connect ([wiki: Dimension type](https://minecraft.wiki/w/Dimension_type)).
- `has_ceiling` is "only a logical ceiling". It turns off weather, changes respawn and spawn
  search, and halves map range. It doesn't place any blocks
  ([wiki: Dimension type](https://minecraft.wiki/w/Dimension_type)).
- Limits: `height` must be a multiple of 16, and the top can't exceed 2031
  ([wiki: Dimension type](https://minecraft.wiki/w/Dimension_type)).

### The lava sea
- `sea_level` is 32 with lava as the default fluid, and aquifers are off
  ([noise_settings/nether.json](https://raw.githubusercontent.com/misode/mcmeta/1.21.1-data/data/minecraft/worldgen/noise_settings/nether.json);
  [wiki: Noise settings](https://minecraft.wiki/w/Noise_settings)). Lava seas fill every hollow
  at and below Y 31 ([wiki: Nether](https://minecraft.wiki/w/Nether)).
- Because aquifers are off, every hole below 32 becomes lava. That is why the sea is one flat
  sheet everywhere (my read).

### The density function
- `size_horizontal` 1 and `size_vertical` 2 make each noise cell 4×8 blocks
  ([nether.json](https://raw.githubusercontent.com/misode/mcmeta/1.21.1-data/data/minecraft/worldgen/noise_settings/nether.json)).
- `final_density` is `squeeze(0.64 × interpolated(blend_density(...)))`. Inside it:
  - one `old_blended_noise` (`base_3d_noise`: xz_scale 0.25, y_scale 0.375, xz_factor 80,
    y_factor 60, smear 8)
    ([base_3d_noise.json](https://raw.githubusercontent.com/misode/mcmeta/1.21.1-data/data/minecraft/worldgen/density_function/nether/base_3d_noise.json));
  - a top slide that forces solid rock from Y 104 to 128;
  - a bottom slide that forces solid rock from Y 24 down to −8
    ([nether.json](https://raw.githubusercontent.com/misode/mcmeta/1.21.1-data/data/minecraft/worldgen/noise_settings/nether.json)).
- Every other router entry is 0. There are no continents, erosion, ridges, caves, aquifers or
  ore veins.
- So the whole Nether is one 3D noise, squashed between two slides. This is the "same base shape
  since Alpha" that Sportskeeda points out
  ([Sportskeeda](https://www.sportskeeda.com/minecraft/minecraft-nether-realm-s-terrain-generation-decade)).

### Biomes
- `multi_noise` uses only temperature and vegetation. Both have `y_scale` 0, so biomes are
  vertical columns: a biome fills the whole height
  ([nether.json router](https://raw.githubusercontent.com/misode/mcmeta/1.21.1-data/data/minecraft/worldgen/noise_settings/nether.json)).
- The five biomes and their points
  ([dimension/the_nether.json](https://raw.githubusercontent.com/misode/mcmeta/1.21.1-data/data/minecraft/dimension/the_nether.json)):

  | Biome | temp | humidity | offset |
  |---|---|---|---|
  | Nether Wastes | 0 | 0 | 0 |
  | Soul Sand Valley | 0 | −0.5 | 0 |
  | Crimson Forest | 0.4 | 0 | 0 |
  | Warped Forest | 0 | 0.5 | 0.375 |
  | Basalt Deltas | −0.5 | 0 | 0.175 |

- Shares of the world: Wastes 36%, Crimson 22%, Soul Sand 17%, Deltas 16%, Warped 9%. Biomes
  change only surface blocks and features. "Terrain shape is independent of biome"
  ([wiki: Nether](https://minecraft.wiki/w/Nether)).

### Structures
- Fortresses and bastions share one `random_spread` set: spacing 27, separation 4, weights of
  bastion 3 to fortress 2
  ([structure_set/nether_complexes.json](https://raw.githubusercontent.com/misode/mcmeta/1.21.1-data/data/minecraft/worldgen/structure_set/nether_complexes.json)).
- Fortresses are the only source of blazes and wither skeletons. Bastions appear in every biome
  except Basalt Deltas. Nether fossils appear only in Soul Sand Valley. Ruined portals appear in
  any biome ([wiki: Nether](https://minecraft.wiki/w/Nether)).

### Ambience
- Each biome sets a fog colour, particles (white ash in Deltas, spores in the forests), a looping
  ambient sound, a mood sound, random "additions" sounds and music
  ([basalt_deltas.json](https://raw.githubusercontent.com/misode/mcmeta/1.21.1-data/data/minecraft/worldgen/biome/basalt_deltas.json)).
- The dimension's fog runs from 10 to 96 blocks. Warped Forest has no music
  ([wiki: Nether](https://minecraft.wiki/w/Nether)).

### What players find dull
- **Lava seas give nothing.** They are "extremely sparse" in interest, and you can usually walk
  around them ([Mojang feedback](https://feedback.minecraft.net/hc/en-us/community/posts/360073896691/comments/360013243252)).
  A Reddit poster even proposed removing them
  ([Sportskeeda](https://www.sportskeeda.com/minecraft/news-minecraft-redditor-sparks-debate-remove-lava-nether)).
- **Samey netherrack.** "Blank and kind of boring", compared to the old oceans
  ([Mojang feedback](https://feedback.minecraft.net/hc/en-us/community/posts/360043227771-Recolour-the-Nether)).
  The terrain has had the same shape since Alpha
  ([Sportskeeda](https://www.sportskeeda.com/minecraft/minecraft-nether-realm-s-terrain-generation-decade)).
- **No reason to stay.** Players visit for about five things: farms, travel, lava, blaze drops
  and wither skulls ([Mojang feedback](https://feedback.minecraft.net/hc/en-us/community/posts/360077579592-Height-Content-in-the-Nether)).
  See also [Sportskeeda's rework list](https://www.sportskeeda.com/minecraft/minecraft-nether-features-deserve-rework-improvements).
- **Cramped, confusing navigation.** The 128-block slab with a thick roof and 96-block fog gives
  short sightlines and no landmarks to steer by. Players tunnel at Y ~15 or ride the roof
  (my read, from the numbers above). That the roof-highway habit is real shows in mods built just
  to stop it, such as Amplified Nether Kill
  ([moddex](https://moddex.gg/mod/amplified-nether-kill-mr-6iHmubSB)).

---

## 2. Terrain overhauls

The comparison table is in section 8. Download counts and dates are from the Modrinth API on
2026-11-19.

### Amplified Nether (Stardust Labs)
- **What:** doubles the height to 256, adds "tons of new terrain types", and brings back the
  mountains from the Nether Update trailer. It adds no biomes. It uses 3D biomes so "any biome can
  border any other" ([Modrinth](https://modrinth.com/mod/amplified-nether)).
- **How:** a datapack using 1.18 worldgen (density functions plus a 3D multi-noise biome source)
  ([Modrinth](https://modrinth.com/mod/amplified-nether)).
- **1.21.1:** yes, as a datapack or mod for Fabric, Forge and NeoForge. 2.4M downloads. Licence:
  Stardust Labs (bans AI use).
- **Performance:** no official note, and I found no measurements. My read: doubling the generated
  height roughly doubles the noise work per chunk. The similar Atmospheric Nether tells users to
  pregenerate ([Modrinth](https://modrinth.com/mod/KD9xXJbY)).
- **Complaints and quirks:**
  - The roof is at build limit, so you can't get down from it. A separate mod adds a kill layer
    to fix that ([moddex](https://moddex.gg/mod/amplified-nether-kill-mr-6iHmubSB)).
  - Biomes above Y 128 showed as "Plains" until a fix mod
    ([mado](https://www.mado-hosting.com/minecraft-mods/amplified-nether-fix-biome)).
  - Not compatible with Incendium, and it must start in a fresh Nether
    ([Modrinth](https://modrinth.com/mod/amplified-nether)).
- **Add-on:** Amplified Nether Height (MIT) raises the build height to 384 and the logical height
  to 256 ([Modrinth](https://modrinth.com/datapack/amplified-nether-height)).

### Incendium (Stardust Labs)
- **What:** eight biomes, built from vanilla blocks
  ([wiki mirror](https://mc-wiki.pages.dev/worldgen/incendium/biomes/), via search;
  [Modrinth](https://modrinth.com/mod/incendium)):
  - Ash Barrens
  - Infernal Dunes (steep red-sand mountains)
  - **Inverted Forest** (crimson and warped trees growing *down from the ceiling*)
  - Quartz Flats
  - Toxic Heap
  - Volcanic Deltas
  - Weeping Valley
  - Withered Forest

  It also adds 9+ structures (Forbidden Castle, Sanctum, Nether Reactor, Quartz Kitchen and
  more), bosses and 25+ items. Terrain gets "jagged mountains, twisting caves"
  ([CurseForge](https://www.curseforge.com/minecraft/mc-mods/incendium)). The generation height
  goes up to 192 (search summary of the CurseForge page). 3D biomes allow vertical stacking: an
  Inverted Forest hangs over Quartz Flats.
- **How:** a datapack. No details are published, and its files were not read.
- **1.21.1:** yes, as a datapack or mod on all loaders. 10.7M downloads. It is now named
  "Incendium Legacy". Licence: Stardust Labs (bans AI use).
- **Praise and complaints:** "slightly more difficult, but much more rewarding". The authors admit
  the final boss "might feel impossible to some"
  ([Stardust Labs](https://www.stardustlabs.net/incendium)). Not compatible with Amplified Nether,
  and needs a fresh Nether.

### Atmospheric Nether (TheBrewingMiner)
- **What:** the closest to a *composed* Nether
  ([mod page](https://modrinth.com/mod/KD9xXJbY); [datapack page](https://modrinth.com/datapack/atmospheric-nether)):
  - Height runs −64 to 384, with the roof at 256.
  - Continents of valleys and plateaus, ringed by cliffs that reach the roof.
  - Old volcanoes at continent centres, some holding lava oceans.
  - Lava rivers cut through the ranges and sometimes break the surface.
  - Overworld-style caves, mostly sealed from the surface.
  - 27 biomes: 7 forest, 9 cave, 5 river (including a "River Styx") and 6 continental.
  - A data-driven speleothem feature.
- **Status:** the datapack is discontinued in favour of the mod. The mod lists 1.21–1.21.8 on
  Fabric, Forge and NeoForge. Licence: custom
  ([licence](https://github.com/TheBrewingMiner/license)). Not read.
- **Performance:** "pregenerate… worldgen can cause significant lag". The extra height makes mob
  farms weaker ([mod page](https://modrinth.com/mod/KD9xXJbY)).

### Nether Expanse (cev-api)
- **What:** removes the roof. Terrain tapers out by Y 256 and the dimension is 416 tall. Optional
  sky content above: a sky portal at Y 280, sky structures, "Nether cities" and "Nether ancient
  cities". Has an Incendium-compatible variant
  ([Modrinth](https://modrinth.com/mod/nether-expanse)).
- **How:** a datapack overriding `noise_settings/nether.json`. Keep `noise.height` above the
  terrain cap "so terrain tapers into open sky instead of flattening".
- **1.21.1:** yes, datapack only. Tiny (692 downloads). Licence: all rights reserved.

### Topless Nether, Altitudo, Higher Nether, More Nether
- **Topless Nether:** removes the roof by editing the dimension files. Supports 1.21–1.21.1, now
  archived. MIT ([Modrinth](https://modrinth.com/datapack/topless-nether)).
- **Altitudo:** a NeoForge 1.21.1 world type. The Nether runs −128 to 1023, and the roof, caves
  and ores follow the new bounds. MIT, 7 downloads ([Modrinth](https://modrinth.com/mod/altitudo)).
- **Higher Nether:** height to 384. Not on 1.21.1. GPL-3.0
  ([Modrinth](https://modrinth.com/mod/highernether)).
- **More Nether:** Fabric 1.21, roof at Y 320, incompatible with other Nether generators
  ([metamods](https://metamods.net/en/mods/nether-height-expansion-mod)).

### BetterNether (paulev / BetterX) and BetterNether: New Dawn
- **What:**
  - New biomes, plants, wood sets and mobs (Naga and others), plus dungeons and large "Nether
    Cities".
  - "A vertical and volumetric biome system", meaning floor, wall and ceiling biome layers
    ([Modrinth](https://modrinth.com/mod/betternether)).
  - New Dawn adds the Gloomwood biome, mega lava lakes and 2×2 giant willows, and ships LabPBR
    specular maps ([Modrinth](https://modrinth.com/mod/betternether-neoforge)).
- **How:** BCLib, WunderLib and WorldWeaver libraries. It keeps vanilla's terrain shape and works
  through biomes and features.
- **1.21.1:** the original is Fabric only (13.8M downloads). **New Dawn** is an unofficial
  continuation by Raijin2312 for NeoForge and Fabric, including 1.21.1, with 234k downloads. Both
  are MIT.
- **Praise and complaints:**
  - Early commenters called its art among the best in any mod.
  - A long-standing GitHub issue says its textures use far more colours than vanilla's limited
    palettes, so they clash ([issue #463](https://github.com/paulevsGitch/BetterNether/issues/463)).
  - A rival mod calls it "too cluttered" ([Even Better Nether](https://modrinth.com/mod/even-better-nether)).
  - Performance worries kept it out of the All of Fabric packs (same issue). Old reports point to
    longer Nether load times ([issue #70](https://github.com/paulevsGitch/BetterNether/issues/70)).
  - A "Remastered" fork claims thread-safe generation and fixed lag spikes
    ([CurseForge](https://www.curseforge.com/minecraft/mc-mods/betternether-remastered)).

### Regions Unexplored (Apollo)
- **What:** five Nether biomes ([Sportskeeda guide](https://www.sportskeeda.com/minecraft/minecraft-regions-unexplored-mod-guide);
  [craftdownunder](https://craftdownunder.co/guides/mods/regions-unexplored)):
  - Blackstone Basin
  - Glistering Meadow (glowing pink, a favourite spot for bases)
  - Infernal Holt
  - Mycotoxic Undergrowth
  - Redstone Abyss
- **How:** since 0.6 it uses **Lithostitched** (≥1.7.9) instead of TerraBlender
  ([Modrinth](https://modrinth.com/mod/regions-unexplored)). Its file list includes a Lithostitched
  FastNoise cell config named `inferno_cells`
  ([repo](https://github.com/Apollounknowndev/RegionsUnexplored)). It keeps vanilla's shape.
- **1.21.1:** yes, on NeoForge. 10.2M downloads. MIT.
- **Why it matters here:** it is the model for how a Lithostitched-based biome mod would want to
  slot into a Grand Atlas Nether.

### Biomes O' Plenty (Glitchfiend)
- **What:** Nether biomes include Undergrowth (dense bramble and hellbark, "much more dangerous…
  despite the lack of lava") ([BOP wiki](https://biomesoplenty.fandom.com/wiki/Undergrowth)),
  Visceral Heap (flesh), Crystalline Chasm, Withered Abyss and Erupting Inferno
  (search summary; [changelog](https://www.curseforge.com/minecraft/mc-mods/biomes-o-plenty/files/8384500)).
- **How:** TerraBlender ([Modrinth](https://modrinth.com/mod/biomes-o-plenty)).
- **1.21.1:** yes, NeoForge. 39M downloads. Licence: all rights reserved (not read).

### Oh The Biomes We've Gone
- The BYG sequel. Its 1.21 updates are about Overworld biomes and villages. I found **no Nether
  biomes** in it ([changelog](https://modrinth.com/mod/oh-the-biomes-weve-gone/changelog)). The
  old BYG did have Nether biomes. All rights reserved.

### Cinderscapes
- **What:** four biomes: Ashy Shoals, Blackstone Shales, Luminous Grove and Quartz Cavern
  ([Modrinth](https://modrinth.com/mod/cinderscapes)).
- **Status:** Fabric and Quilt only, no Forge port planned, so **not usable on NeoForge 1.21.1**.
  LGPL-3.0.

### Infernal Expansion → Infernal Expansion Redux
- **Original:** Forge, up to 1.19 at most, Apache-2.0
  ([Modrinth](https://modrinth.com/mod/infernal-expansion)).
- **Redux:** a ground-up rebuild ([Modrinth](https://modrinth.com/mod/infernal-expansion-redux)).
  - Glowstone Canyon returns, with glowstone stalactites and stalagmites.
  - Shimmer Sand deserts with pits hidden by falling Glimmer Gravel.
  - Luminous Fungus that lights up as you approach.
  - Glowsquito and Blindsight mobs.
  - Lists 1.21.1 builds, with NeoForge listed. AGPL-3.0, 31k downloads.

### Smaller biome and content mods (all 1.21.1)
- **Gardens of the Dead:** Soulblight Forest and Whistling Woods. MIT
  ([Modrinth](https://modrinth.com/mod/gardens-of-the-dead)).
- **Luminous: Nether:** Fungal Plains, Ash Forest and ~16 mobs. All rights reserved
  ([Modrinth](https://modrinth.com/mod/luminous-nether)).
- **Nether Depths Upgrade:** nine lava fish, plus lava fishing, an enchant and a potion. MIT
  ([Modrinth](https://modrinth.com/mod/nether-depths-upgrade)).
- **Hearths:** additions to the vanilla biomes. All rights reserved
  ([Modrinth](https://modrinth.com/mod/hearths)).
- **Jaden's Nether Expansion:** reworks all five biomes and plans four more. NeoForge-first.
  CC-BY-NC-ND ([Modrinth](https://modrinth.com/mod/jadens-nether-expansion)).
- **Legacy Nether: Extended:** console-era style. CC-BY-SA
  ([Modrinth](https://modrinth.com/mod/legacy-nether-extended)).

---

## 3. Structures and landmarks

| Mod | What | 1.21.1 NeoForge | Licence |
|---|---|---|---|
| [YUNG's Better Nether Fortresses](https://modrinth.com/mod/yungs-better-nether-fortresses) | Full redesign: bridge networks, a central Keep, Lava Halls running deep underground; optional Create pieces; meant to work with Nether biome mods | yes (already in the user's Create Atlas pack) | LGPL-3.0 |
| [Better Bastions (TONI)](https://www.curseforge.com/minecraft/mc-mods/better-bastions) | Replaces every bastion with a bigger procedural one: 4 layouts, 5 biome styles, decay and garrison settings; follows the vanilla bastion biome tag | yes | not checked |
| [Repurposed Structures](https://modrinth.com/mod/repurposed-structures-forge) | Nether pyramid, crimson and brick outposts, Nether shipwrecks, Nether ruins and more; config datapack | yes | LGPL-3.0 |
| [Hopo Better Ruined Portals](https://modrinth.com/datapack/hopo-better-ruined-portals) | Prettier ruined portals | yes | LGPL-3.0 |
| [Giant Ruined Portals](https://www.mado-hosting.com/minecraft-mods/giant-ruined-portals) | Huge portal ruins | listed for 1.21.1 NeoForge | not checked |
| [MNS – Moog's Nether Structures](https://modrinth.com/mod/mns-moogs-nether-structures) | Vanilla-block structures; server-side only | yes | LGPL-3.0 |
| [Formations Nether](https://modrinth.com/mod/formations-nether) | Many small and medium structures | yes | ARR |
| [Eternal Nether](https://modrinth.com/mod/eternal-nether) | Piglin Manor (Crimson), Citadel (over Warped), Catacombs (a wither-shaped ruin); continues Bygone Nether | yes | MIT |
| [Dungeons and Taverns Fortress Overhaul](https://modrinth.com/mod/dungeons-and-taverns-nether-fortress-overhaul) | Decorated fortress halls, more spawners | yes | ARR |
| [Incendium structures](https://modrinth.com/mod/incendium) | Forbidden Castle, Sanctum, Nether Reactor and more | (bundled with its terrain) | Stardust |

**How they make exploration worth it.** Bigger, layered loot spaces, such as YUNG's Keep and Lava
Halls. Biome-specific set pieces you can recognise, such as Eternal Nether's manor in Crimson and
citadel over Warped. Bosses and unique gear, as in Incendium.

**How they fit terrain mods.** Most place through biome tags (Better Bastions says so outright), so
they follow whatever biomes exist. The main friction is **terrain height**. Jigsaw structures that
look for "ground" in a slab expect a floor between 32 and 128. A Nether with tall cliffs, open
voids or a high roof needs structures placed with height and fit rules (my read). Grand Atlas has
already met this with Overworld structures (village-by-river and locate fixes).

---

## 4. Life and atmosphere

### Mobs and plants
- Lava-sea life: Nether Depths Upgrade's lava fish and lava fishing
  ([Modrinth](https://modrinth.com/mod/nether-depths-upgrade)).
- Light-reactive ecosystems: Infernal Expansion Redux's Glowsquito drains light from glowstone,
  and the Blindsight eats Glowsquitos
  ([Modrinth](https://modrinth.com/mod/infernal-expansion-redux)).
- Ceiling life: Incendium's Inverted Forest; BetterNether's volumetric floor, wall and ceiling
  biomes.

### Ambience
- Vanilla's tools, all data-driven per biome: fog colour, ambient particles, a loop, mood and
  additions sounds, music
  ([basalt_deltas.json](https://raw.githubusercontent.com/misode/mcmeta/1.21.1-data/data/minecraft/worldgen/biome/basalt_deltas.json)).
- AmbientSounds (LGPL-3.0, 1.21.1 NeoForge, already in the Create Atlas pack) layers its own
  environmental audio ([Modrinth](https://modrinth.com/mod/ambientsounds)).
- Sound Physics Remastered adds reverb and absorption, which would make big caverns sound big
  ([Modrinth](https://modrinth.com/mod/sound-physics-remastered)).

### Fog, dark and shaders
- Many players simply remove Nether fog: No Fog has 1.5M downloads and Simple Fog Control 1.2M
  ([search listing](https://modrinth.com/mod/clear-fog); [Simple Fog](https://modrinth.com/mod/simplefog)).
  Fog that only hides things is unpopular, which matches the user's own test35 verdict ("less
  creepy and more just foggy", `docs/CAVE_RESEARCH.md`).
- Shader packs draw their own Nether fog:
  - Aeon Realism has a dedicated Nether fog that follows nearby biomes.
  - SolAS has "Nether smoke" and LPV fog.
  - DreadFog and LumaSky Beyond have per-dimension fog
    ([search summary](https://www.curseforge.com/minecraft/shaders/lumasky-beyond)).
  - So Grand Atlas' vanilla fog colours may be overridden under Iris.
- Grand Atlas already found that Lumina Event Horizon lights from raw light levels and ignores the
  lightmap. That is why `IrisDeepDarkMixin` scales Iris's `screenBrightness`
  (`docs/CAVE_RESEARCH.md`, test36). The Nether's `ambient_light` 0.1 is a dimension-wide floor.
  How Lumina treats it is untested.
- Lighting trick worth noting: the Nether has no skylight, so **only block light** shapes what you
  see. Lava, glowstone, shroomlight and magma placement *is* the lighting design (my read).

---

## 5. Lava, fluids, the sea and the roof

- **Lava rivers and falls:** Atmospheric Nether's lava rivers carve through ranges and break the
  surface in places ([Modrinth](https://modrinth.com/mod/KD9xXJbY)). New Dawn adds mega lava
  lakes. Vanilla's "hidden lava" springs make small falls ([wiki: Nether](https://minecraft.wiki/w/Nether)).
- **Basalt deltas:** vanilla's delta feature (lava and magma pools in basalt) and Incendium's
  rougher Volcanic Deltas, with "massive magma veins and jagged peaks"
  ([wiki mirror](https://mc-wiki.pages.dev/worldgen/incendium/biomes/), via search).
- **The lava sea:**
  - Atmospheric Nether keeps seas but puts them in volcanoes and basins, so they are places, not
    a flat floor.
  - Hades' Asphodel makes lava the *main* space: "archipelagos surrounded by lava", plus a barge
    that seems to rush down a lava river
    ([Gorinstein thread](https://threadreaderapp.com/thread/1311777010239193088)).
  - Mojang feedback asked for things *under* the lava, such as inverted glowstone and obsidian
    clusters ([feedback](https://feedback.minecraft.net/hc/en-us/community/posts/360057780292-Inverted-obsidian-glowstone-cluster-like-structures-under-lava-oceans)).
- **The roof:** what has been tried:

  | Approach | Example | Trade-off |
  |---|---|---|
  | Raise it | Amplified Nether (256), Atmospheric (256 roof inside 384), Amplified Nether Height (384), Altitudo (1023) | More noise work and bigger saves; weaker mob farms; the roof-highway habit breaks |
  | Remove it | Topless Nether; Nether Expanse (terrain tapers into open sky by 256, dimension 416) | The Nether gets a "sky", but vanilla's Nether effects draw no sky, only fog colour (`effects: the_nether` in [dimension_type](https://raw.githubusercontent.com/misode/mcmeta/1.21.1-data/data/minecraft/dimension_type/the_nether.json)); `has_ceiling` still affects spawns and maps |
  | Upside-down sections | Incendium Inverted Forest; BetterNether ceiling biomes | Cheap and strange; needs 3D biomes |
  | See-through | none found | — |

  I found no mod with a see-through roof. Mixing open-sky windows into a raised roof is untried
  territory (my read).

---

## 6. Other games' hells and underworlds

| Game | What it does well | Lesson for a procedural Nether |
|---|---|---|
| **Terraria – Underworld** ([wiki](https://terraria.wiki.gg/wiki/The_Underworld)) | One wide cavern under the whole world: an ash roof, lava pools, ash forests in the outer sixths, obsidian and hellstone tower ruins in the middle; layered backgrounds with lava lakes, falls and mountains | **Zones by broad region** (forest edges, ruin core). Distant parallax layers sell scale; in Minecraft, Distant Horizons can play that role |
| **Hades – Asphodel** ([Gorinstein](https://threadreaderapp.com/thread/1311777010239193088); [Kotaku](https://kotaku.com/hades-level-design-is-less-random-than-it-seems-1845254545)) | Each region has its own design rules; Asphodel is "vast and horizontal", islands in lava, a break from the hallways before it | **Change the shape language per region**, not just the blocks. Pace cramped areas against open ones |
| **Diablo** ([Boris the Brave](https://boristhebrave.com/2019/07/14/dungeon-generation-in-diablo-1)) | Layout first (walkable, doors), then tiles; required rooms placed among random ones | **Plan the big structure, then dress it.** Grand Atlas already does this with macro → meso → micro |
| **Doom Eternal** ([PC Gamer](https://pcgamer.com/in-doom-eternal-the-doom-slayer-is-faster-stronger-and-more-brutal-than-ever); [GamesRadar](https://gamesradar.com/i-think-doom-eternals-worlds-are-more-artificial-than-ever-and-thats-a-good-thing)) | Corridor → arena rhythm; more verticality, but critics said organic vertical layouts made the path hard to read | **Verticality needs readable landmarks**, or it turns into confusion: the Nether's real problem |
| **Dark Souls – Lost Izalith** ([Source Gaming](https://sourcegaming.info/2024/08/17/center-stage-blighttown-dark-souls/); [PC Gamer](https://www.pcgamer.com/time-has-redeemed-dark-souls-2)) | Called "dull, samey marches" that "abandon the verticality"; recycled enemies; "oversaturated lava" (though some love the look) | **The cautionary tale.** A flat lava lake plus repeated parts is exactly vanilla's sin. Lava must be framed, not spread |
| **Elden Ring – Siofra, Nokron, Deeproot** ([gamepressure](https://www.gamepressure.com/editorials/elden-ring-the-location-that-made-fall-in-love/za53b); [wiki](https://eldenring.wiki.gg/wiki/Eternal_Cities); [PC Gamer](https://pcgamer.com/some-of-elden-rings-best-moments-are-buried-deep-underground)) | A **false starry sky** underground; huge open caverns under ruins; reached by a surprise descent | **One impossible sky** beats a bigger cave. The reveal after a descent is the moment. Grand Atlas' Aurora Vault already plays this card |
| **Deep Rock Galactic – Magma Core** ([wiki](https://deeprockgalactic.wiki.gg/wiki/Biome_Features); [dev post](https://devtrackers.gg/deep-rock-galactic/p/8cd5ca9c-below-decks-at-ghost-ship-cave-generation-in-deep-rock-galactic)) | Hand-made room shapes placed and joined by a random system, with biomes sculpting surfaces; Magma Core adds geysers, earthquakes, hot rock and two-level plateaus | **A library of authored room shapes plus random joins**. This is Grand Atlas' cave room library, carried over to Nether-scale chambers |

**Shared lessons:**
- Change the shape per region, not just the palette.
- Give one landmark you can see from far away per region.
- Alternate tight and open spaces.
- Frame lava: falls, rivers, islands, glowing cracks. Don't just flood with it.
- Save the impossible for rare reveals.

---

## 7. Fit with Grand Atlas

What Grand Atlas already has that carries straight over:
- The density core with column caches.
- Macro → meso → micro features.
- Mega-caverns, rifts and great rifts.
- Cave room libraries and moods.
- Darkness and the Iris mixin.
- Lava falls, abyss levels and set pieces.
- The "existing blocks first" palette work.
- Lithostitched awareness.

The Nether is a closed box with no skylight. That suits darkness as a tool and set-piece reveals
(my read).

**Compatibility points that apply to every direction:**
- **Create packs:** nothing in Create needs special Nether terrain. YUNG's Better Nether
  Fortresses (with optional Create pieces) is already in the Create Atlas pack, along with C2ME,
  Distant Horizons, Iris, Sodium and AmbientSounds (from `create-atlas-pack/lock/lock.json`). No
  Nether biome mod is in it now.
- **Distant Horizons:** DH can turn off LOD rendering per dimension type, and the Nether is the
  example given ([DH release](https://modrinth.com/mod/distanthorizons/version/yeEW8nev)). With a
  solid roof, distant LODs show little. A raised, broken roof or open regions are where DH would
  finally *add* something: far lava lakes, cliffs, landmarks (my read, untested).
- **Iris and Lumina:** packs may replace the fog. Darkness has to go through the existing
  `screenBrightness` route, and `ambient_light` may matter. Needs a test.
- **Lithostitched:** if Grand Atlas keeps a `multi_noise` biome source for the Nether, Lithostitched
  biome mods (Regions Unexplored) and TerraBlender mods (BOP) can still add biomes. If it uses a
  fully custom biome picker, they're shut out unless it offers a hook.
- **Other Nether terrain mods** (Amplified, Incendium, Atmospheric, Nether Expanse) replace
  `noise_settings/nether.json`, and only one wins. Grand Atlas should detect them, or document the
  conflict.

### Direction A: "Grand Nether" (own the terrain, raised and broken roof)
- **Shape:**
  - Raise the dimension (for example 0–320 or −64–320) and keep a bedrock roof.
  - Give the roof holes into a dark upper void, and have some mega-caverns reach it.
  - Composed macro landforms: lava seas held in basins and calderas; cliff-ringed plateaus and
    continents in the Atmospheric Nether spirit; soul-sand canyons; basalt badlands; fungal
    forests on terraces; great rifts.
  - 3D biomes by height layer: floor, mid, ceiling, with ceiling forests à la Incendium.
- **Pros:**
  - Biggest "awe" payoff.
  - Reuses the cave core.
  - Fixes navigation with long sightlines and landmarks.
  - Gives DH something to draw.
- **Cons:**
  - Most work.
  - Clashes with other Nether terrain mods.
  - More generation cost (pregeneration expected).
  - Mob farms, portal linking and the roof highway change, and players will notice.
  - Structures need fit rules.
- **Compatibility:** keep `multi_noise` so Lithostitched and TerraBlender biome mods can add
  biomes. YUNG fortresses and bastion mods follow biome tags, so make sure Grand Atlas biomes carry
  the vanilla Nether tags.

### Direction B: "Vanilla-plus Nether" (keep the slab, compose within it)
- **Shape:**
  - Keep 0–128, or nudge it to 192 like Incendium.
  - Replace the single noise with Grand Atlas' layered shapes: lava seas in basins, rivers and
    falls, pillars, and a few big chambers.
  - Lean on set pieces, palette and ambience.
- **Pros:**
  - Safest for performance, farms, portals and other mods.
  - Fastest to ship.
- **Cons:**
  - The cramped ceiling, the players' top complaint, stays.
  - Less "epic". Hard to get sightlines in 128 blocks.

### Direction C: "Layered underworld" (open sky and upside-down tiers)
- **Shape:**
  - Remove the roof, as Nether Expanse does.
  - Stack tiers: the lava floor, a cavern world, then hanging ceiling islands or upside-down
    lands under an open dark "ash sky". Optionally a false sky, Elden Ring style.
- **Pros:** the most surprising and memorable.
- **Cons:**
  - Breaks `has_ceiling` assumptions.
  - Needs sky rendering work: vanilla Nether effects draw no sky.
  - Players will fly and pillar up, and big open voids cost DH and shaders.
  - The furthest from "grounded".

**My recommendation:** A, with one C idea used rarely. Make a few regions where the roof opens
into a vast dark void or a false sky as a set piece, not the default. Start from B-sized
milestones inside A: first the new density and basins, then the roof breaks, then 3D biomes. That
way each step can be playtested.

---

## 8. Comparison of terrain mods

| Mod | Height / roof | Lava | Caverns | Biomes | Technique | 1.21.1 NeoForge | Perf. reputation | Licence |
|---|---|---|---|---|---|---|---|---|
| Vanilla | 0–128, roof 123–127 | flat sea ≤31 | one noise | 5 column biomes | 3D blended noise + slides | — | — | — |
| Amplified Nether | 0–256, roof at top | vanilla rule | new terrain types, mountains | vanilla 5, 3D | datapack density + 3D biomes | yes | heavier (inferred) | Stardust (no AI) |
| Incendium | gen to ~192 | volcanic deltas | twisting caves | 8, 3D (inverted forest) | datapack | yes | not reported | Stardust (no AI) |
| Atmospheric Nether | −64–384, roof 256 | volcano seas, lava rivers | Overworld-style caves | 27 | datapack / mod | 1.21–1.21.8 | "pregenerate", laggy gen | custom |
| Nether Expanse | open sky, terrain ≤256 | vanilla | vanilla | vanilla | datapack | yes | not reported | ARR |
| Altitudo | −128–1023 | follows | follows | vanilla | world type | yes | unknown (7 dl) | MIT |
| BetterNether / New Dawn | vanilla | mega lava lakes (ND) | vanilla | many, volumetric | BCLib / WorldWeaver | New Dawn: yes | mixed (old lag reports) | MIT |
| Regions Unexplored | vanilla | vanilla | vanilla | 5 | Lithostitched (≥0.6) | yes | fine (no complaints found) | MIT |
| Biomes O' Plenty | vanilla | Erupting Inferno | vanilla | ~5 | TerraBlender | yes | fine | ARR |
| OTBWG | — | — | — | none found | — | yes (Overworld only) | — | ARR |
| Cinderscapes | vanilla | — | Quartz Cavern | 4 | Fabric | **no** | — | LGPL-3.0 |
| Infernal Exp. Redux | vanilla | — | — | Glowstone Canyon | multi-loader | listed | unknown | AGPL-3.0 |
| Gardens of the Dead | vanilla | — | — | 2 | — | yes | — | MIT |
| Luminous: Nether | vanilla | — | — | 2 | — | yes | — | ARR |
| Nether Depths Upgrade | vanilla | lava fish | — | — | — | yes | — | MIT |

---

## 9. What to steal (as ideas)

- **Lava as places, not a floor:** seas held in calderas and basins, island chains (Asphodel),
  rivers cutting through ranges (Atmospheric), and falls from roof breaks.
- **Continents split by cliffs that reach the roof.** It gives regions identity and landmarks
  (Atmospheric Nether).
- **3D biomes by height:** ceiling forests hanging over different floors (Incendium Inverted
  Forest, BetterNether volumetric biomes).
- **A raised roof with breaks:** look up through a hole into dark void, or rarely a false sky
  (Elden Ring's Nokron).
- **Change the shape per region, not only the blocks** (Hades).
- **One landmark you can see far off per region:** a pillar, a lava fall, a basalt spire, a
  fortress silhouette (lesson from Doom Eternal's readability critique).
- **A library of authored chambers joined at random** (Deep Rock Galactic), using the existing
  cave room library.
- **Light-reactive life and plants:** glow when approached, mobs that drain light (Infernal
  Expansion Redux). Fits "darkness as a tool".
- **Something under the lava:** inverted glowstone and obsidian clusters, submerged ruins (Mojang
  feedback). Pairs with lava-fishing mods.
- **Hazards that move:** geysers, ash falls, unstable ground (Magma Core).
- **Structure fit:** bastions and fortresses on chosen ground, such as bridges over lava basins or
  a keep on a plateau. Placement rules only; the structures themselves come from vanilla, YUNG and
  chosen mods.
- **Sound for scale:** cavern reverb (Sound Physics Remastered) and per-region loops.

---

## 10. Open questions for the user

1. **Roof:** keep a roof (raised and broken), keep vanilla's 128, or open it to the sky in some
   regions?
2. **Height budget:** how tall? 192 (Incendium-like), 256 (Amplified), 320–384 (Atmospheric), or
   more? Taller means slower generation and weaker mob farms.
3. **Lava sea:** keep one global sea level, or lava only in basins and seas at different heights?
4. **Biome mods:** should other mods' Nether biomes (Regions Unexplored, BOP) be able to slot in,
   or is the Grand Atlas Nether a closed, curated set?
5. **Which Nether structure mods** do you want designed for? YUNG's Better Nether Fortresses is in
   the pack already. Add Better Bastions, Repurposed Structures, Eternal Nether, MNS?
6. **Hand-built set pieces:** do you want Nether set pieces like the Overworld's (Aurora Vault
   style), and what feelings for each (awe, dread, cozy, clever, horror)?
7. **Darkness:** should parts of the Nether be truly dark (block light only, and lower
   `ambient_light`), or keep vanilla's dim glow? It needs a check with Lumina.
8. **Difficulty:** should the Nether get harder by region (Incendium style), or stay at vanilla
   difficulty with the challenge coming from terrain?
9. **Navigation:** is breaking the roof-highway habit OK, or should the roof stay a safe road?
10. **New blocks:** any appetite for a few "vanilla plus" Nether blocks (ash, hot rock), or
    vanilla only?
11. **Distant Horizons in the Nether:** do you play with DH on in the Nether, so that sightlines
    and far landmarks are worth designing for?
