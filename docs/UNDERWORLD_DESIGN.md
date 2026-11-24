# Domus Mortus: a new dimension (design, 2026-11-19)

This is design only; nothing is built. It is for 1.21.1 (`mc1211/`).
- The user's decisions from 2026-11-19 are marked **(decided)**.
- My proposals that still need a yes are marked **(approved)**.
- Open points are gathered in section 11.
- The user wants Domus Mortus built **before** the Nether plan (`docs/NETHER_PLAN.md`).

## The idea in one paragraph

Every death leads to the black river. The ferryman waits in his boat. Pay him a coin and he carries
you, with everything you own, across the water and under a false sky full of glittering stars, into
Domus Mortus:
- the pale Asphodel Meadows, where the shades wander;
- the springs of Forgetting and Memory;
- a ring of fire around the pit of Tartarus;
- far off, the warm light of Elysium.

Can't pay, and he throws you back to the living, but he keeps everything you carried until you come
to claim it. Once inside, there is only one way home: bring Persephone a Flower of Erebus, which
grows only at the very bottom of Tartarus.

Two sources:
- **Greek myth** (public domain): the geography, the rivers, the coin, the rules.
- **Underground ruins under a false starry sky** in big open-world games: only the *feeling*, an
  impossible sky found below ground. No names, places, art or designs from any game are used.

---

## 1. Pillars (how this fits Grand Atlas)

- **Rule of Cool, by feeling.**
  - Awe: the sky, the first sight of the meadows from the river.
  - Melancholy and cozy: Asphodel, Elysium.
  - Clever: Lethe and Mnemosyne.
  - Fear and horror: Tartarus.
- **Epic but grounded.** The realm follows the myth's map, so it reads as one composed place.
- **Darkness as a tool.** The sky gives a faint blue light. Tartarus is truly dark, using the
  caves' darkness technique.
- **Vanilla blocks first.** Copper as the age of bronze (Tartarus' walls and gates), calcite,
  quartz, tuff, deepslate, pale moss and blackstone. New blocks only at vanilla-plus quality, and
  no programmer art.
- **The generator never designs buildings.** It makes terrain, rivers, plants, light and natural
  landmarks. Every building and ruin is a **structure the user builds** (decided), placed on pads
  the generator prepares (section 7).

---

## 2. Death, the coin, and the way home

### 2.1 The rule (decided)

**Every death, in any dimension, sends you to the ferryman.** That includes other mods' dimensions
(the Aether, Twilight Forest and so on; decided). What happens next depends on the coin.

| You have... | What happens |
|---|---|
| **An obol** (anywhere in your inventory) | The ferryman takes it and rows you across. You arrive in Domus Mortus **with everything** you carried: items, XP, effects. Nothing drops where you died. |
| **A Netherite Obol** | The same, but the coin is shown, not spent. You keep it. |
| **No coin** | The ferryman refuses. You wake at your normal respawn point (bed, anchor or world spawn) with **nothing**. Everything you carried is taken into **the Ferryman's Hoard** in Domus Mortus, until you come and claim it. |

**What this means:**
- **Items never scatter on the ground at death.** You either keep them (paid) or they're held
  safe in the Hoard (unpaid). There is no despawn timer and no corpse run. To get them back, you
  must enter Domus Mortus.
- **Hardcore (decided):** the coin is a second chance. Dying with a coin in hardcore sends you to
  Domus Mortus instead of ending the world. This includes the Netherite Obol, so a player who owns
  one always gets another chance (decided). Dying in hardcore *without* a coin is a real hardcore
  death, as vanilla.
- **The `keepInventory` gamerule (decided):** when it's on, an unpaid death keeps the items, as
  vanilla would. Nothing goes into the Hoard, and you still wake at your respawn. Paid deaths work
  the same either way.
- **No coin ritual (decided):** there's no "use the coin to die" item action. You go to Domus Mortus
  by dying, nothing else.
- **Technical:**
  - Cancel the death (NeoForge `LivingDeathEvent`), fade to black, and start the boat scene.
  - When the player can't pay, move their whole inventory into their Hoard record (saved data
    per player), clear it, and respawn them at their usual point.
  - No real vanilla death happens in either case. **(approved)** Count both in the death
    statistic, so stats and advancements stay honest.

### 2.2 The obol (decided)
- **Found and crafted.**
- **Recipe:** gold nuggets and iron nuggets. **(approved)** 4 gold nuggets round 1 iron nugget, in
  a plus shape, makes 1 obol.
- **Found:** rare loot (loot modifiers) in ancient cities, trial vaults, trail ruins and desert-well
  archaeology, and a rare skeleton drop.
- **The Netherite Obol (decided):** a permanent coin, made by plating an obol in netherite.
  **(approved)** Make it at the smithing table like other netherite gear: an obol, a netherite
  ingot and a netherite upgrade template. It's never spent, so it's the late-game "I always get
  in" item.
- **Textures:** worn silver for the obol, dark plated netherite for the permanent one. Both hand
  drawn.

### 2.3 The boat ride
- Every paid arrival is a 20–40 second ride. The ferryman's boat glides across the river along a
  set path while the player looks up at the false sky, then lands on the far shore.
- The ferryman (a hooded figure, modelled by hand in Blockbench) stands at the stern. He never
  fights and can't be hurt.
- An unpaid arrival is short: the boat, the ferryman shakes his head, and you fade out.

### 2.4 The Ferryman's Hoard
- **(approved)** It's kept at the ferryman's landing on the far shore: a dock with a strongbox
  (part of the user's dock build).
- Each player's taken items are held separately, so other players can't take them.
- On a paid arrival the ferryman says you have things in his keeping, and you take them back from
  the strongbox.
- Several unpaid deaths stack up in the Hoard and are never lost.

### 2.5 Leaving: Persephone's price (decided)
- **The only way home** while alive in Domus Mortus is to give **Persephone a Flower of Erebus**.
  It grows only on the floor of Tartarus, in its deepest darkness.
- Persephone is an NPC in her palace or garden (a user build). Given the flower, she sends you home
  to your respawn point with everything.
- **Dying inside Domus Mortus** follows the rule in 2.1: you're back at the ferryman.
  - Pay again and you're back inside.
  - Can't pay and you go home, but your things go into the Hoard.
- **So "dying out" is always an escape, at the price of your inventory.** The flower is the way
  out that keeps everything.
- **The Flower of Erebus:** a new plant, black petals with faint starlight inside them, hand drawn
  to vanilla-plus quality. It grows only on Tartarus' floor, a few per pit. Picked, it's an item.
  **(approved)** It regrows slowly, so multiplayer servers don't run dry.
- **Every trip (decided):** each paid stay ends either with a new Flower of Erebus for Persephone
  or with dying out (no coin, items into the Hoard). Her favour never carries over. Each paid death
  means a Tartarus descent if you want to leave with your things.

### 2.6 Lethe and Mnemosyne (approved: new roles)
The way home is now Persephone, so the springs become a risk and a reward:
- **Lethe (Forgetting):** drinking from it or swimming in it wipes your XP and status effects. A
  white cypress always stands by it, as the ancient Orphic gold tablets warn.
- **Mnemosyne (Memory):** drinking from it gives a long buff against forgetting and darkness, for
  example night vision that works in Tartarus' dark. This makes the Tartarus run easier, for players
  who know which spring is which.

### 2.7 Pomegranates (approved)
The user liked the idea but wasn't sure how it could work. A clean version:
- Pomegranate trees grow in Persephone's Groves. Their **seeds** are food.
- **Eating seeds makes you Bound,** for 6 in-game days, after the six seeds Persephone ate in the
  myth.
- **While Bound:**
  - Every death brings you into Domus Mortus **free**, as if you'd paid, keeping everything.
  - You can't be refused by the ferryman, so "dying out" no longer works as an escape. Only the
    flower takes you home.
- **The trade:** free passage now, but no easy way out for a while. Myth-true, simple to code
  (one status effect checked at death), and an interesting choice.

### 2.8 What the player can't do (decided: "not allowed to leave")
- **No portals work inside.** Nether portals won't light; End gateways and other mods' portals are
  refused.
- **No flying out through the sky** (section 3).
- **No sailing away:** the outer sea pushes you back to the Styx (section 4).
- **The only exits** are the flower or dying without a coin. Ops can use `/grandatlas domus`.

---

## 3. The sky: glimmering false stars

**The look:**
- Permanent night.
- A dense field of tiny stars in white, pale blue, violet and rose, all **glimmering**.
- One or two broad nebula bands arching across.
- Now and then a falling star.
- No sun, no moon. The light is faint and blue.

**What sells "underground":**
- At the horizon, colossal cave walls rise into the sky and fade.
- In a few places, inverted mountains of rock hang from the "sky", their tips lit by the stars.

**Players can't leave through it (decided).** The user doesn't mind whether it's real blocks or a
shader, as long as no one gets out.

**(approved)** Both together:
- **A hard ceiling:** a layer of barrier blocks across the top of the dimension, so nothing passes
  (barriers are a vanilla block and invisible in survival). It covers elytra, pillars, ender pearls
  and riptide.
- **The visible stars:** drawn by a custom `DimensionSpecialEffects`, plus the brightest stars and
  the nebula as **world-anchored glowing geometry** just under the ceiling. Shader packs render
  that full-bright, as they do `client/AuroraRenderer`'s aurora, so the sky survives Iris packs
  (Lumina) that would otherwise draw their own sky.
- **Dimension type:** `has_skylight` true with `fixed_time` midnight, so the vanilla night lightmap
  gives the faint blue light, and packs see a normal night.
- **Distant Horizons:** with no rock roof, DH's far LODs show the rivers, the ring of fire and the
  palace across the realm.

---

## 4. The map: a composed realm (decided)

One great realm around 0,0, roughly **6,000–8,000 blocks across**, circled by the river **Styx**.
Beyond the Styx, a dark misty sea goes on forever but holds nothing. Its current pushes swimmers and
boats back toward the Styx, and the far horizon is fog.

### Height (decided)
- `min_y` −320 to the top at 383.
- **Almost everything happens above Y 32.** Tartarus is the part below: a pit dropping to about
  −300.
- The barrier ceiling sits at the top, and the hanging mountains hang down from about 350.

### Layout, from the outside in

| Ring / place | What it is | Feeling | Generator or user build |
|---|---|---|---|
| **The Styx** | A huge black river circling the realm; still water mirroring the stars; mist | awe, dread | generator |
| **The landing** | Where the boat lands: grey ash beach, reeds; the ferryman's dock and the Hoard | melancholy | shore: generator; dock: **user build** |
| **The Gate** | A pass between cliffs on the way inland; a great gate; the three-headed hound's lair | fear | cliffs: generator; gate: **user build** |
| **Asphodel Meadows** | Most of the realm: pale rolling plains of white and grey flowers; grey poplars; shades drifting; ruined cities of the dead half-sunk in the grass | melancholy, awe | terrain and plants: generator; ruins: **user build** |
| **Groves of Persephone** | Black poplar and willow woods; pomegranate trees; Persephone's garden | cozy, uneasy | trees: generator; garden: **user build** |
| **Lethe and Mnemosyne** | Twin spring-fed lakes close together; a white cypress by Lethe | clever | generator |
| **Cocytus** | A frozen, cracked river with cold blue light and sobbing sounds | sorrow | generator |
| **Phlegethon** | A river of fire circling Tartarus; an orange ring glowing across the blue world | awe, danger | generator |
| **Tartarus** | A vast pit inside the fire ring, down to about −300; walls of bronze (copper oxidising downward), chains, total darkness; **the Flower of Erebus on its floor**; the Titans' prison | horror | pit: generator; prison: **user build** |
| **The Black Plateau and the Palace of Hades** | The dark heart; Hades and Persephone's palace | awe | plateau: generator; palace: **user build** |
| **Elysium** | The far side: golden fields, warm fog and light, gentle streams | cozy, reward | generator |
| **Punishment vignettes** | Sisyphus' slope and boulder; Tantalus' pool under fruit trees just out of reach; Ixion's wheel | clever, grim | slope, boulder, pool: generator; wheel: **user build** |

The palace plateau is renamed "the Black Plateau". *Erebus* now names the darkness on Tartarus'
floor, where its flower grows.

### Colour script
- **Overall:** cold. Blue-violet sky, grey-white ground, black water.
- **Accents:** the orange ring of Phlegethon, the gold of Elysium, the pale blue of Cocytus.
- **The darkest place:** Tartarus.

---

## 5. Life

**Decided:**
- Mostly **custom creatures from the myths**, modelled by hand (the user's Blockbench work).
- **All monsters**, vanilla and modded, **can spawn in Tartarus**.
- **No mob can destroy terrain anywhere in Domus Mortus.** No creeper, ghast or wither blast
  damage to blocks, no endermen moving blocks, no breaking doors. Players can still break and
  build as normal.

**(approved)** Elsewhere in the realm, only the custom creatures spawn, so the realm feels other.

| Creature | Role | Behaviour |
|---|---|---|
| **The ferryman** | NPC on the boat and at the dock; keeper of the Hoard | Never fights; can't be hurt |
| **Persephone** | NPC in her palace or garden; takes the flower and sends you home | Can't be hurt |
| **Shades** | The ordinary dead: translucent, drifting, whispering | Passive; some drop lore fragments |
| **Hollow shades** | Shades that drank too much of Lethe | Hostile; found only near Lethe |
| **The three-headed hound** | Guardian of the Gate | Boss |
| **Furies** | Winged hunters in Tartarus and near Phlegethon | Hostile fliers |
| **River life** | Pale eyeless fish in the Styx; embers over Phlegethon | Ambient |

**Technical:** mob griefing is blocked per dimension by events (explosions keep entity damage but
affect no blocks; mob block changes are refused). The `mobGriefing` gamerule stays untouched for
the rest of the world.

---

## 6. Sound and atmosphere

- **Per-biome sound:**
  - Asphodel: wind through grass and far-off whispers.
  - The Styx: slow water and a distant oar.
  - Cocytus: sobbing in the ice.
  - Tartarus: chains and deep rumbles.
  - Elysium: soft birdsong and a lyre.
- **Music:** wanted (the user: "hopefully"). Who makes it is open.
- **Particles:** pale motes in Asphodel, embers by Phlegethon, frost breath at Cocytus, falling
  stars.
- **Fog:** violet-blue and thin; warm gold in Elysium; none in Tartarus (darkness instead).
- **Darkness:** `client/DeepDark` and the Iris mixin extended to Tartarus.

---

## 7. Structures (decided: the user builds them)

The generator makes the **places**: flat pads, plateaus, terraces, approaches. The user builds the
structures, and Grand Atlas ships and places them.

| Build | Where | Needed for |
|---|---|---|
| Ferryman's dock and Hoard strongbox | the landing | arrivals, item recovery |
| Palace of Hades and Persephone | Black Plateau | the centrepiece, Persephone |
| Persephone's garden (if separate from the palace) | the Groves | where the flower is handed in |
| The Gate | the pass inland | the hound's arena |
| Ruined cities of the dead (several variants) | Asphodel | the ruins under the stars |
| Titans' prison, chains, shackled figures | Tartarus floor | endgame, around the flowers |
| Ixion's wheel | vignette | flavour |
| Small shrines and altars | scattered | lore pages, coin caches |

---

## 8. Technical outline

- **Data:**
  - `data/grandatlas/dimension/domus_mortus.json`, `dimension_type/domus_mortus.json`,
    `worldgen/noise_settings/domus_mortus.json`.
  - Biomes: `styx`, `landing_shore`, `asphodel_meadows`, `persephone_grove`, `lethe_springs`,
    `cocytus`, `phlegethon`, `tartarus`, `black_plateau`, `elysium`, `outer_sea`.
  - All written by `tools/gen_data.py`.
- **Core:** a new `MortusCore` beside `AtlasCore`. It is small and Overworld-free, and holds the
  realm map (rings, river paths, region shapes), heights and the pit. It is immutable after seeding,
  with per-thread caches, safe for C2ME and DH.
- **Worldgen:**
  - Density functions `grandatlas:mortus_terrain` and `grandatlas:mortus_caves`.
  - The biome source `grandatlas:mortus`.
  - Features: rivers of water and fire (reusing `PerchedWater` and its grooves), flowers,
    re-skinned trees, the white cypress, the Flower of Erebus, the vignettes, and pads for the
    user's structures.
- **Gameplay:**
  - Items: obol, Netherite Obol, Flower of Erebus, pomegranate seeds.
  - The death hook and the Hoard (per-player saved data).
  - The boat-ride entity.
  - The ferryman and Persephone NPCs.
  - The Bound effect.
  - Spring effects.
  - Blocking portals inside, and the no-griefing rules.
  - Config switches for hardcore and for the "every death" rule, for packs that want it off.
- **Client:**
  - `MortusSky`: stars, glimmer, nebula, falling stars, world-anchored glow geometry.
  - Fog colours.
  - The boat-ride camera.
- **Commands:** `/grandatlas domus` (op: go there or back), `/grandatlas hoard <player>` (op:
  inspect or return held items), `/grandatlas locate <place>`.

---

## 9. Phases (before the Nether plan)

| # | Phase | Done when |
|---|---|---|
| **U0** | Section 11 answered; the user's first build list | Answers in this doc |
| **U1** | **Vertical slice:** the death hook, obols, the Hoard, the boat ride, a patch of shore and Asphodel under the glimmering sky, the barrier ceiling | Die with a coin and arrive; die without one and find your items in the Hoard; the sky works with and without Lumina |
| **U2** | The realm map: Styx ring, rivers, regions, heights, outer sea; preview renders | The map reads as the myth's map from above and in DH |
| **U3** | Tartarus, Phlegethon, darkness, the Flower of Erebus, Persephone and the way home; no griefing; all monsters in Tartarus | A full trip works: arrive, descend, pick the flower, give it, go home |
| **U4** | Lethe, Mnemosyne, Cocytus, Groves and pomegranates, Elysium, the vignettes | Each place recognisable in a screenshot |
| **U5** | Life: shades, then the hound and Furies, as models are made | — |
| **U6** | The user's structures placed on their pads | The palace stands on the Black Plateau |
| **U7** | Headless check (rivers closed, no stray fluid, pads flat, Hoard round trips), performance, ship | — |

---

## 10. Risks

| Risk | Why | Mitigation |
|---|---|---|
| "Every death" changes the whole game | Players and modpacks used to vanilla deaths, gravestone mods, keep-inventory | A config switch; detect gravestone/corpse mods and document the conflict; respect the `keepInventory` gamerule (decided: with it on, unpaid deaths keep items) |
| A Tartarus run on every paid death | The flower is needed every trip (decided), so a coin means a long dungeon each time | Keep the descent varied and readable; the flower regrows; dying out stays as the quick, costly exit |
| Hardcore plus the Netherite Obol | A permanent coin means endless second chances in hardcore | Accepted by the user (decided); the netherite recipe's cost is the gate |
| Shader packs replace the sky | Unknown dimensions may get the pack's Overworld sky | World-anchored glow geometry (proven by the aurora); test in U1 |
| Lost-item disputes on servers | The Hoard must never lose things | Per-player saved data, an op command, and a check in U7 |
| User-build workload | Palace, gate, dock and ruins are big builds | The realm works with pads first; the dock and Persephone's spot are needed by U3 |

---

## 11. Decisions and what's still open

Decided by the user on 2026-11-19 (second round):

1. **Name:** Domus Mortus.
2. **The flower:** needed on **every** trip out.
3. **Netherite Obol in hardcore:** works. A permanent second chance is fine.
4. **The coin ritual:** dropped. Dying is the only way in.
5. **keepInventory on:** unpaid deaths keep their items, and nothing goes to the Hoard.
6. **Other mods' dimensions:** deaths there go to the ferryman too.

7. **Proposals:** all approved (2026-11-19): the obol recipe (4 gold nuggets round 1 iron nugget),
   the Netherite Obol at the smithing table, the Hoard at the dock, the new roles for Lethe and
   Mnemosyne, pomegranates as "Bound for 6 days", the barrier ceiling with world-anchored stars,
   custom creatures only outside Tartarus, both outcomes counted as deaths in stats, and the flower
   regrowing slowly.

Still open:

1. **Music:** the user hasn't decided yet who makes it.
