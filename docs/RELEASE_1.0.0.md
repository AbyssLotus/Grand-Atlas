# Grand Atlas 1.0.0: release checklist

First public release: Minecraft 1.21.1, NeoForge 21.1.197 or newer. **Domus Mortus is not in it** (it
ships in a later version; see ROADMAP.md).

## Legal (checked 2026-11-24)

- [x] **Licence:** Grand Atlas License 1.0 (`LICENSE.md`), copyright AbyssLotus (W.74 on Modrinth). It
      ships in the jar as `GRAND_ATLAS_LICENSE.md`, and `neoforge.mods.toml` links it.
- [x] **Third-party content:** three community asset packs (credited in `docs/CREDITS.md`, shipped as
      `GRAND_ATLAS_CREDITS.md`). Each author's terms were re-read on their Planet Minecraft pages on
      2026-11-24 and are unchanged. Grand Atlas's use fits each:
  - *1k - Treepack* (H2CO3): "you can use those trees for whatever project you like, If you get paid
    for it, I don't care". Not to be redistributed as schematics: the mod converts them to its own packed
    format. The author asks not to be tagged when credited.
  - *The God Tree-Pack* (Plutouthere): "Give Credit Where it's due."
  - *Landscaping Mega Schematic Pack* (Dannypan; trees by paleozoey): "You can use these for private,
    public or commercial use. Please give credit… you may use or remix them for projects you make
    available with due credit." Not to be republished as schematics.
- [x] **The authors' original schematics are not published:** `assets-src/` (the original packs, the
      Better Trees jar) is git-ignored and must stay out of the repository and every download.
- [x] **No bundled mods or libraries.** NeoForge is the only dependency; Vanilla Backport, Better Trees
      and TerraBlender biome mods are optional and keep their own licences.
- [x] **Own work:** code, data, textures (hand-drawn or painted by `tools/textures.py`), logo, guide book,
      both mobs (the user's own Blockbench models). No vanilla assets are overridden and no sounds ship.
- [x] **No Terralith or Stardust Labs material**, by design (their licence bans it).
- [x] **Personal details:** the author field is "AbyssLotus (W.74 on Modrinth)" (it was a first name);
      one local folder path removed from ROADMAP.md.
- [x] **AI disclosure:** in MODRINTH.md ("Made with AI"), in the author's own words.

## Build

```
gradlew :mc1211:build -Prelease
```

Gives `mc1211/build/libs/grandatlas-1.0.0+mc1.21.1.jar`. Without `-Prelease` the jar still contains Domus
Mortus. Check the release jar contains no `domus`/`mortus` data before uploading.

Checked 2026-11-24: the self-check passed; the jar (945 entries) has no Domus Mortus data and carries the
licence, credits and logo; a fresh world booted on the release data with no errors (biome mods on, no
Vanilla Backport).

## Modrinth

- Project: name **Grand Atlas**, summary from `gradle.properties` (`mod_description`), icon
  `docs/logo.png`, description `MODRINTH.md` (strip the HTML comment at the top), gallery from
  `docs/images` (optional).
- **License:** Custom, linking `https://github.com/AbyssLotus/Grand-Atlas/blob/main/LICENSE.md`.
- **Environment:** client and server both required (worldgen runs on the server, the guide book, river
  water and aurora on the client).
- **Version:** 1.0.0, Release, NeoForge, Minecraft 1.21.1. Changelog: the 1.0.0 section of CHANGELOG.md.
- **Dependencies:** Vanilla Backport (optional).
- Links: source and issues on GitHub (AbyssLotus/Grand-Atlas).

## GitHub

- Commit, tag `v1.0.0`, push, and attach the release jar to a GitHub release.
- Decide before pushing: the Domus Mortus code, data and design doc (`mortus/`,
  `data/grandatlas/domus/`, the `domus_mortus` dimension files, `docs/UNDERWORLD_DESIGN.md`) are
  uncommitted. The registered code (`ModWorldgen`, commands, the biome source) refers to the `mortus`
  classes, so the public source won't build without them. So either commit them (the unreleased
  dimension's code and design become visible in the repository, though not in the mod), or keep only
  `docs/UNDERWORLD_DESIGN.md` private and commit the rest.
- `mc1211/run-structures/` and `mc1211/run-biomes/` hold copies of other mods' jars for audits and are
  git-ignored. Never commit them.
