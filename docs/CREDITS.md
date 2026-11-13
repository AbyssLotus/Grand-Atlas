# Credits

Grand Atlas places hand-built trees and rock formations made by other people. They are used with
thanks, under the terms each author published. If you are one of these authors and want something
changed or removed, say so and it will be.

## Trees and rock formations in the mod

| Work | Author | Where it is used | Terms as published (checked 2026-11-10) |
|---|---|---|---|
| **1k - Treepack** | H2CO3 (FeH2 on Planet Minecraft) | The mixed trees in every wooded biome: 300 of the pack's trees: those built only of natural blocks, with no planks, wood slabs or stairs. They are re-skinned in each biome's own wood and leaves. | "Everyone is allowed to use those trees for personal projects as well as for commisions and marketplace plrojects." Not to be passed on as schematics: "it's ok to use them in projects". The author asks not to be tagged. |
| **The God Tree-Pack** | Plutouthere (renders by Freekm1234 and Arcaniax) | The landmark god trees, 80-180 blocks tall: 34 of the pack's trees (green, blossom and bare variants). | "Give Credit Where it's due. Yep, that's it." |
| **Landscaping Mega Schematic Pack** | Dannypan | Outcrops, natural arches, spires, pillar mountains, bladed fins, spike clusters and stone rings: 527 of the pack's 11,700 pieces. | "You can use these for private, public or commercial use. Please give credit if using for public or commercial use. You may not remix or republish these schematics as your own, but you may use or remix them for projects you make available with due credit." |
| Trees inside the Landscaping pack's decorated pieces | paleozoey | On top of some outcrops, arches and pillar mountains. | Credited by Dannypan on the pack's page. |

Sources:
- https://www.planetminecraft.com/project/1k-treepack-by-h2co3/
- https://www.planetminecraft.com/project/the-god-tree-pack/
- https://www.planetminecraft.com/project/landscaping-mega-schematic-pack-1-21-11-axiom-blueprints-included/

**How they are stored.** The mod does not contain the authors' schematic files. `tools/assets/Import.java`
converts a selection into the mod's own packed format (`data/grandatlas/atlas_assets/*.bin`), which is
not a schematic and can't be loaded as one. Changes made in conversion: old block ids mapped to current
blocks, leaves set to never decay, fence connections worked out, the insides of large rocks simplified
to one stone, wool blossom on the god trees mapped to cherry and azalea leaves, autumn (terracotta)
variants of the god trees left out, 271 of the 1k pack's trees left out because they use blocks that aren't natural (wool, concrete,
quartz and the like), and 353 more left out because they contain planks, wood slabs or stairs.

## Recommended alongside (separate mod, not bundled)

| Mod | Author | Licence |
|---|---|---|
| **Better Trees** | EMD0123 | Apache-2.0. https://modrinth.com/datapack/better-trees |

Better Trees reshapes the game's own trees. Grand Atlas's pack trees go in first and the game's trees
(Better Trees' versions, when it is installed) fill the ground between them.
