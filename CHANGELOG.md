# Changelog

## 1.0.0 — first public release (Minecraft 1.21.1, NeoForge)

The first release on Modrinth. Grand Atlas replaces the Overworld's terrain and biome placement.

New since the 0.13 test builds:

- **Ten new macro landforms:** great craters, monoliths, fault blocks, shattered plateaus, great
  sinkholes and titan horns, plus four fantasy ones: Spire Crowns, Coiled Peaks, Sundered Lands and Sky
  Pillars. Some hosts carry a second landform on their ground, such as a spire standing on a plateau.
- **Biome mods:** Biomes O' Plenty, Oh The Biomes We've Gone and other TerraBlender biome mods now place
  their biomes on Grand Atlas's land, with their own surfaces and trees. How much land they get is set
  in `config/grandatlas-common.toml` (`biomeMods.weight`, default 0.6; 0 turns them off).
- **Structure mods:** surface structures no longer generate on steep slopes, in rivers or against
  cliffs. Grand Atlas's biomes are added to the common biome tags, so more mods' structures find them.
- **Climate:** deserts, badlands, swamps, snowy plains and plains are more common; forests still lead.
- **Rivers:** streams join where they meet instead of running side by side, and wander gently instead of
  zigzagging. Rock formations no longer float or leave walls of grass under them.
- **Two creatures:** the Potted Monster, a snapping plant in a pot that passes for a houseplant among
  the giant mushrooms of the fungal forests, and the Crystal Eel of the Aurora Vaults.
- **Performance:** much lower memory use in large modpacks (the cave caches are shared and capped).

Also in 1.0.0:

- **Landforms:** megaranges up to y 1024, canyon plateaus, dune seas, rock deserts, salt playas;
  great plateaus, massifs, basins and elevated mesas several kilometres across.
- **Water:** a real drainage network with rivers that reach the sea, waterfalls, lake districts,
  calderas, fjords and island seas; river water with a current; no floating water.
- **Underground to y -512:** cave networks, worm bores, mega-caverns and rifts, underground lakes and
  seas, eight new cave biomes, and set pieces (Aurora Vaults, the Worldroot, the Hollow, Great Geodes,
  Titan's Graves, the Hanging Mountains).
- **Real-world wonders:** tepuis with falls off their rims, rift valleys, rainbow mountains, spotted lakes.
- **High fantasy:** the Misty Peaks, the Volcanic Wastes, the Silverwood, Glimmer Vales, the Sky Isles,
  and fantasy sites (crystal gardens, standing stones, floating rocks, bone fields, petrified forests).
- **The Grand Atlas guide book:** pages that fill in as you explore, 3D models of each place, the
  coordinates of everything you've found, and ripped maps in chests that point the way.
- Named places, a chosen spawn, villages by the water, every vanilla forest biome, and thousands of
  hand-built trees and rock formations from community packs (see Credits).

Needs a new world. Optional: Vanilla Backport for the newer blocks and biomes.
