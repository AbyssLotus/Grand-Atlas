# Backport to 1.21.1: mods that bring newer vanilla content back

Noted 2026-11-11, from the mods' listing pages. Nothing here has been downloaded or tested, and
none of it is approved yet. Check each mod's Versions tab before relying on it.

**Settled 2026-11-11:** Vanilla Backport 1.2.2 was downloaded, opened and run. It has every block and biome in the table below under plain `minecraft:` ids, and needs the library mod Platform. The backport is built on it as an optional companion; details in `BACKPORT_1.21.1_PLAN.md`.

## What Grand Atlas uses that 1.21.1 does not have

| Content | Added in | Where we use it |
|---|---|---|
| Pale Garden biome | 1.21.4 | `BiomeKind.PALE_GARDEN` (1.3% of land), Pale Grove meso site |
| Pale oak log | 1.21.4 | `CaveDressing`, `MicroDetail`, `CountryDetail` |
| Leaf litter | 1.21.5 | `MicroDetail` |
| Bush | 1.21.5 | `AtlasAssets`, `CountryDetail` |
| Short and tall dry grass | 1.21.5 | `AtlasAssets`, `CaveDressing`, `CountryDetail` |
| Firefly bush | 1.21.5 | `CaveDressing` |
| Sulfur Caves biome (and its vanilla surface rule) | 26.2 | `BiomeKind.SULFUR_CAVES`, `CaveGate`, `tools/gen_data.py` |

`Blocks.DYED_TERRACOTTA` (13 uses) is only an API rename, not missing content.

Not used by the mod at all: the 26.3 Dappled Forest and poplar trees. `BiomeKind` has no entry
for it, so **it does not generate in Grand Atlas on 26.3 either.** That is a gap in the current
build, separate from the backport.

## Mods (all list NeoForge 1.21.1)

| Mod | Covers | Licence | Notes |
|---|---|---|---|
| [Vanilla Backport](https://modrinth.com/mod/vanillabackport) (BlackGear) | Pale Garden, Spring to Life, Sulfur Caves (biome, cinnabar, sulfur blocks, Sulfur Cube), and in 1.2: Copper Age, Mounts of Mayhem, Wilderness Bound | VBL (its own licence; read before bundling) | Broadest and most active: 1.2 for NeoForge 1.21.1 is dated 2026-10-18. One mod would cover everything in the table above. I could not read its full feature list, so the exact blocks are unconfirmed. |
| [Ported](https://modrinth.com/mod/ported) | 1.21.2 to 1.21.5: pale garden, creaking, eyeblossom, resin, leaf litter, wildflowers, bush, firefly bush, cactus flower, dry grass, fallen trees | MIT | Needs NeoForge 21.1.197+. Last updated about 10 months ago. Nothing after 1.21.5, so no Sulfur Caves or Dappled Forest. |
| [Backport 26.3 Update](https://www.curseforge.com/minecraft/mc-mods/backport-update) (BloodyDreams) | 26.3 only: Dappled Forest, poplar wood set, abandoned camps, cushions, straw beds, wool and concrete stairs and slabs | MIT | 1.0.9, 2026-08-20, built from 26.3 snapshots. |
| [Sulfur Caves Backport](https://modrinth.com/mod/sulfur-caves-backport) | 26.2 Sulfur Caves biome | not checked | No Sulfur Cube. |
| [Haven Backports](https://github.com/Frosthaven/minecraft-haven-backports) | Sulfur Caves with the Sulfur Cube, other newer content | not checked | Made for one modpack (Haven). |
| [Pale Garden Backport](https://modrinth.com/project/92Kv38mV) | Pale Garden biome, pale oak, Creaking, Creaking Heart | not checked | Needs TerraBlender, which Grand Atlas avoids. |
| Perfect Parity Neo: Pale Garden Awakens | Pale Garden, pale oak, Creaking, eyeblossoms | not checked | Last 1.21.1 build 2025-07-23. |
| [Content Backport](https://www.curseforge.com/minecraft/mc-mods/content-backport) (juanmuscaria) | Pale oak, wildflowers, leaf litter and more | not checked | Its own page calls it quick and unfinished. |
| [Spring to Life Backport](https://github.com/MC-Backports/spring_to_life) | Animal variants, cactus flower, falling leaves, fallen trees | not checked | Does not clearly include leaf litter, bush or dry grass. |

## Things to settle before choosing

- **Block ids.** A backport mod registers blocks under its own namespace or under `minecraft:`.
  Grand Atlas has to look the blocks up by whichever id the chosen mod uses, and fall back to a
  1.21.1 block (dark oak log, short grass, dead bush) when the mod is absent.
- **Biome placement.** These mods add their biomes through their own means (TerraBlender or biome
  modifiers). Grand Atlas owns the biome source, so only the biome's id matters to us; the mod's
  own placement must be off or harmless.
- **Hard or soft dependency.** Soft (fall back when absent) keeps Grand Atlas usable alone; hard is
  simpler.
- **First candidate to test:** Vanilla Backport alone, since it is the only one that covers 1.21.4
  through 26.3 in a single mod.
