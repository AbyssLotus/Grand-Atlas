# Grand Atlas: Myths & Legends (design, 2026-11-25)

This is design only; nothing is built. It is for 1.21.1.
- The user's decisions are marked **(decided)**.
- My proposals that still need a yes are marked **(proposed)**.
- Open points are gathered in section 9.

## The idea in one paragraph

Grand Atlas stays what it is: a worldgen mod. Terrain, water, caves, biomes. **Myths & Legends** is
an optional add-on that sits on top of it and holds the mythological content (decided): Domus Mortus,
the Greek underworld (docs/UNDERWORLD_DESIGN.md), and other places, creatures and rules from myth.
A pack that wants only the terrain installs Grand Atlas. A pack that wants the gods, the ferryman
and the monsters installs both.

**Two ground rules (decided, 2026-11-25):**
- **Greek first.** The first release is Greek myth only. The point is to get the formula right with
  the Greeks (section 5.0) before any other mythology is considered.
- **Grand Atlas's current content stays as it is.** The add-on adds to the world. It never changes,
  reshapes or removes anything Grand Atlas already generates: terrain, rivers, caves, biomes, set
  pieces, fantasy sites.

---

## 1. Why an add-on

- **Domus Mortus changes the game, not just the world.** "Every death goes to the ferryman" rewrites
  how death works (UNDERWORLD_DESIGN.md section 10, first risk). Packs with gravestone mods or their
  own death rules can't use Grand Atlas today without that switched off. As an add-on, they just
  don't install it.
- **The release build already splits them.** `-Prelease` strips Domus Mortus's data out of the jar
  (docs/ROADMAP.md). The add-on turns that build switch into a real boundary.
- **Grand Atlas stays light.** The performance target is the average gaming laptop. Custom mobs,
  NPCs, models and sounds are client costs. Players who don't want them don't pay for them.
- **Separate release pace.** Grand Atlas can ship terrain fixes without waiting on palace builds,
  Blockbench models or music, and the add-on can ship content without touching worldgen.

---

## 2. Name and identity

- **Name:** Grand Atlas: Myths & Legends (decided), with the ampersand.
- **(proposed)** Mod id `grandatlas_myths` (mod ids can't hold `&` or spaces), short name
  "Myths & Legends" in menus.
- **(proposed)** Same author, same license (Grand Atlas License 1.1 already covers the licensor's
  other works), its own Modrinth page linked from Grand Atlas's.
- **Sources:** public-domain myth only, as UNDERWORLD_DESIGN.md does. Names, places and rules from the
  myths are fine. Nothing from any game, film or book's take on them: no names, art, designs or
  characters.

---

## 3. How it's built

### 3.1 Same repo, second jar (proposed)

A second Gradle subproject in this repo, `myths/`, next to `mc1211/`:

```
settings.gradle   include 'mc1211', 'myths'
myths/
  build.gradle    depends on project(':mc1211')
  src/main/java/com/seaweed/grandatlas/myths/...
  src/main/resources/META-INF/neoforge.mods.toml   requires grandatlas, same major version
```

Not a separate repo. Domus Mortus leans on Grand Atlas's internals (`world/AtlasFeature`, the
aurora renderer's world-anchored glow, the cave darkness and its Iris mixin, `tools/gen_data.py`).
A separate repo would first need a stable public API for all of that. In one repo the add-on can call
the internals directly, and both jars are built and tested together.

### 3.2 The dependency rule (proposed)

- **Myths depends on Grand Atlas. Grand Atlas never depends on Myths.** Grand Atlas must build, run
  and pass its checks with the add-on absent.
- Versions move together: Myths 1.x requires Grand Atlas 1.x. The `mods.toml` range enforces it.
- **Myths only reads Grand Atlas, never changes it** (follows from the ground rule). To find a spot
  for a myth site it asks Grand Atlas's world map read-only ("where is the tallest peak?", "where is
  a deep lake?") and places its own features there. It doesn't alter Grand Atlas's density functions,
  landforms or feature lists.

### 3.3 Moving Domus Mortus out

**(decided, 2026-11-25)** Domus moves out. This is the one step that edits Grand Atlas code, and the
only exception to the ground rule: it removes Domus from Grand Atlas and changes none of Grand
Atlas's released content. It touches the five files below.

Domus Mortus is small today: `mortus/` is 6 files, about 810 lines. It touches the core in five
places:

| Core file | What it does for Domus |
|---|---|
| `world/ModWorldgen.java` | registers `mortus_terrain`, the `mortus` biome source, and three features |
| `GrandAtlas.java` | starts the `domusCheck` dev run |
| `AtlasCommands.java` | `/grandatlas domus` |
| `debug/DomusCheck.java` | the dev check of each place |
| `client/DomusLook.java` | the dev screenshot run |

The move:
1. `mortus/`, `DomusCheck` and `DomusLook` go to `myths/`, with their own registers.
2. The Domus data (dimension, dimension type, noise settings, the 15 biomes, `mortus_*` features,
   `domus/layout.bin`) moves to the `grandatlas_myths` namespace. `gen_data.py` gets a target for it.
3. The command becomes `/myths domus` (or stays under `/grandatlas` if Myths can add a subcommand;
   see 9.4).
4. The `-Prelease` exclude list in `mc1211/build.gradle` goes away: Grand Atlas simply has no Domus.
5. The `domusCheck` and `domusLook` runs move to `myths/build.gradle`.

**Worlds:** Domus Mortus has never shipped in a release, so no player world has it. Test worlds that
visited it will lose that dimension when the namespace changes. That's acceptable.

### 3.4 Config

`config/grandatlas_myths-common.toml`:
- the "every death goes to the ferryman" switch and the hardcore switch (moved from the Domus design);
- one on/off switch per Overworld myth site (section 5), so a pack can keep the underworld and drop
  the rest, or the other way round.

---

## 4. Domus Mortus

Unchanged from docs/UNDERWORLD_DESIGN.md: the coin, the ferryman, the Hoard, Persephone's price, the
false starry sky, the realm map, Tartarus and the Flower of Erebus. It becomes the add-on's first
release instead of Grand Atlas 1.2.

What's still to do (from the roadmap):
- the coin and ferryman death mechanic;
- the user's structures (Hades' palace, the Gate, the dock and the rest);
- music (still open);
- an in-game review of the test56 polish pass.

---

## 5. Myths in the Overworld (proposed)

Greek only for now (decided). These sites live in the add-on, not in Grand Atlas (decided).

### 5.0 The formula

"Get the formula down with the Greeks" means every Greek site follows the same recipe. Once it works
for all of them, it's the template any later mythology must meet.

Every myth site has:
1. **A place that already fits.** It's found on terrain Grand Atlas already makes (the tallest peak,
   a lone island, a deep lake, solid deep rock). The add-on never reshapes the land to make room.
2. **A story you can read without words.** One look tells you which myth it is: the stone statues,
   the golden trees, the maze.
3. **A guardian.** A boss or keeper from the myth itself.
4. **A prize that changes how you play.** Not just loot: an item with a use drawn from the myth
   (the thread, the Gorgon's head, the golden apple).
5. **A rule from the myth.** Something the place does to you that the myth says it should: don't
   look at the Gorgon, don't take the apple, follow the thread out.
6. **Lore.** Tablets that tell the myth in short pieces.
7. **A user build on a generator pad.** The generator makes the ground; the user builds the
   structure, as with Domus.
8. **A name and a locate.** Named by its own place names, found with `/myths locate <site>`.

A site that can't fill all eight isn't ready to ship.

### 5.1 The sites

Each row says which existing Grand Atlas terrain it uses. None of it is changed.

| Site | What it is | Sits on | Feeling | Generator or user build |
|---|---|---|---|---|
| **Olympus** | The tallest existing peak in a region becomes Olympus; a temple on its summit in permanent golden light, above the clouds | an existing megarange or titan horn peak, as generated | awe | summit pad, light: generator; temple: **user build** |
| **The Labyrinth** | An infinite maze in its own dimension, fallen into by chance while mining (5.4) | its own dimension | dread, clever | maze: generator; Labs and Ladders: **user build** |
| **The Gorgon's Isle** | A lone rocky island; statues of mobs and players turned to stone along the shore; the Gorgon in a cave | an existing island in the island seas | fear | statues: generator; cave lair: **user build** |
| **Garden of the Hesperides** | A golden-leaved garden on a sky isle; golden apple trees guarded by a coiled serpent | an existing sky isle | wonder, reward | trees on the isle's top: generator; shrine: **user build** |
| **Atlantis** | A sunken city on the floor of an inland sea, half-buried, lit by sea lanterns and glow | an existing deep lake or inland sea floor | awe, melancholy | ruins pads: generator; city: **user build** |
| **Oracle's Spring** | A vapour-filled cleft with a spring; drinking gives a cryptic hint pointing to the nearest myth site | an existing mountain gorge | clever | spring, vapour: generator; shrine: **user build** |

The Titans' Graves stay exactly as Grand Atlas makes them. Myths' lore tablets (found at other sites)
can tell their story, but nothing is placed at or changed in the set piece.

### 5.2 Creatures for the Overworld sites (proposed)

All hand-modelled in Blockbench, like Domus's.

| Creature | Where | Role |
|---|---|---|
| Minotaur | the Labyrinth's heart | Boss |
| Gorgon | her isle | Boss; her gaze slows and then stones you if you look at her too long |
| Ladon, the serpent | the Hesperides | Guardian; sleeps unless you take an apple |
| Satyrs, nymphs | groves, springs, glimmer vale | Ambient; some trade |
| Harpies | sea cliffs | Hostile fliers |

### 5.3 Items (proposed)

- **Ariadne's thread:** marks a glowing trail as you walk, so you can find your way back out of the
  Labyrinth.
- **Golden apple of the Hesperides:** a stronger enchanted apple.
- **Gorgon's head:** a trophy and a once-per-day "stone gaze" that freezes mobs in front of you.
- **Lore tablets:** found at every site; together they tell the myths in short pieces.

---

### 5.4 The Labyrinth (first site)

The user, 2026-11-25: the Labyrinth comes first, "as it should be the easiest". It's where the
formula (5.0) gets proven and where the user learns modding (section 10).

**The myth, in short:** Daedalus built the Labyrinth under Knossos for King Minos to hold the
Minotaur, half man, half bull. Theseus went in, killed it, and found his way out by the thread
Ariadne gave him at the entrance.

**The Labyrinth is its own dimension (decided, 2026-11-25).** It is not a site in the Overworld,
so nothing in Grand Atlas's world is touched.

- **Entering (decided):** every block of stone a player breaks has a **0.1% chance** to drop them
  into the Labyrinth, but only **after the first Minecraft day** (24,000 ticks).
- **The maze (decided):** **infinite.** It goes on in every direction, like the Overworld does.
- **Daedalus's Labs (decided):** scattered through the maze, about every 150 blocks.
- **Exit Ladders (decided):** also about every 150 blocks; climbing one takes you home.

**What the numbers mean.** At 0.1% a block, a player has about a 1 in 3 chance of falling in
within 400 stone blocks, and about 2 in 3 within 1,000. A normal mining trip will drop most players
in sooner or later. That seems to be the intent, but see the open questions.

**How it meets the formula:**

| # | Point | The Labyrinth |
|---|---|---|
| 1 | A place that already fits | Its own dimension, so it needs no room in Grand Atlas's world |
| 2 | Readable at a glance | Long straight corridors of tuff and polished tuff, copper trim going green with age, a Greek key (meander) frieze along the walls, low soul-lantern light |
| 3 | A guardian | The Minotaur (where it lives is open) |
| 4 | A prize | **The labrys**, the Minoan double axe (the word "labyrinth" may come from it) |
| 5 | A rule from the myth | **You can't dig through the walls** (proposed): Labyrinth Stone is unbreakable, so the maze has to be walked. **Ariadne's thread** lays a faint glowing trail so you can retrace your steps |
| 6 | Lore | Daedalus's notes, found in his Labs |
| 7 | User builds | Daedalus's Labs and the Exit Ladders (several variants each) |
| 8 | Name and locate | `/locate structure grandatlas_myths:daedalus_lab` and `.../exit_ladder` work, because the Labs and Ladders are structures |

**How it's built (proposed):**
- **The dimension:** a dimension type (no sky, no daylight, fixed height) and a chunk generator of
  our own that makes nothing but the maze.
- **The maze, infinite:** it can't be planned all at once, so every chunk works out its own part of
  the maze from the world seed and its coordinates, and neighbouring chunks always agree on the
  walls between them. The maze is generated as terrain, a few blocks per corridor cell, with no
  memory of the rest.
- **The Labs and the Ladders:** structures, with spacing rules so one appears about every 150
  blocks (about 9 chunks). They're the user's builds, saved with structure blocks.
- **Entering:** a NeoForge event fires when a player breaks a block. If it's stone, the world is
  past day 1, and a random roll hits 1 in 1,000, the player is sent to the Labyrinth.
- **Leaving:** using an Exit Ladder sends the player back to the Overworld (where to is open).

**Building it in steps.** Each step ends with something you can see in game:

| Step | What | What you learn |
|---|---|---|
| **LB1** | The empty `myths/` mod: loads next to Grand Atlas and says hello in the log | what a mod is, the mod id, `neoforge.mods.toml`, the main class, the event bus, Gradle subprojects |
| **LB2** | Labyrinth Stone, an unbreakable block | registries, blocks and items, blockstates, models, textures, language files |
| **LB3** | The empty Labyrinth dimension, reached by a test command | data packs, dimensions and dimension types, commands, moving a player between dimensions |
| **LB4** | The way in: the 0.1% chance on breaking stone, after day 1 | events, server-side logic, game time, randomness |
| **LB5** | The infinite maze | chunk generators, codecs, seeds, a maze worked out from coordinates |
| **LB6** | The real look: tuff, copper, meander frieze, lanterns | block palettes, randomness from the world seed |
| **LB7** | Exit Ladders and Daedalus's Labs | structures, structure sets and spacing, NBT templates, structure blocks |
| **LB8** | Ariadne's thread and the labrys | items, custom behaviour, recipes and loot tables |
| **LB9** | The Minotaur, with a vanilla stand-in until the Blockbench model is ready | entities, attributes, AI goals, renderers, the client/server split |

**Built (2026-11-25, LB1–LB9 first pass).** All in `myths/`; checked by
`gradlew :myths:runGameTestServer` (dev/LabyrinthGameTests: maze connectivity, generated blocks vs
layout, loot). Not yet seen in a real game window. Choices made where the questions below were
still open, all easy to change:

| Question | Built as |
|---|---|
| After 1 day | World age, `getGameTime() >= 24000` (decided) |
| Which stone | `#minecraft:base_stone_overworld`: stone, deepslate, granite, diorite, andesite, tuff |
| Machines | Real players only (`FakePlayer` ignored); creative and spectator ignored too |
| Exit Ladder destination | Back to the exact spot you fell in (saved in the player's persisted data, survives death); the Overworld spawn if unknown |
| Cooldown | None |
| Maze changes? | Yes: regions shift once a day when empty (see below) |
| Minotaur | One per Daedalus's Lab, placed when the lab generates; a stronger ravager stand-in (220 health, boss bar, never despawns) until the Blockbench model exists |
| Walls | Unbreakable (four blocks: stone floor/ceiling, tuff-brick walls, chiseled frieze, copper trim), and invisible on maps (`MapColor.NONE`, the user's call: a dungeon you can't map) |
| Labs | A 3x3-cell room: a loot chest (Ariadne's thread, Daedalus's notes as a 5-page book, metals, rare diamonds), workshop blocks, bookshelves, lanterns, and the Minotaur |

**How the infinite maze works:** 7-block cells grouped into 21x21-cell regions (147 blocks). Each
region is a perfect maze from the seed; each region border gets two doors from a hash of the
border, so neighbours agree and every cell is reachable. Each region has one lab and one exit,
so both come about every 147 blocks. Corridors are 3 wide and 5 tall, walls 4 thick, between a
2-block floor and ceiling (dimension height 32). Monsters spawn from the `grandatlas_myths:labyrinth`
biome (zombies, skeletons, spiders, cave spiders, creepers, silverfish).

**Second pass (2026-11-25): the shifting maze, finding your way, the halls.**

- **It shifts** (the user: "make it shift"). Every Minecraft day the epoch goes up; a region
  (147 x 147 blocks) catches up only when no player is in it or within 32 blocks, so walls never
  move round anyone. Its walls, lab and exits are redrawn; it gets a fresh lab chest and
  Minotaur. The doors between regions never move, so regions at different epochs always join up
  (checked: every cell reachable with mixed epochs). Region epochs are saved with the world;
  every chunk carries a stamp of the epochs it was built from, and stale chunks are re-written
  in place a few per tick with the generator's own code (checked: a rebuilt chunk matches a
  fresh one block for block). Daedalus's notes on the Drafting Room lectern explain it in-world.
- **Finding exits and labs** (the user: "super hard to find"):
  - 5 exits per region (was 1), and 12% of inner walls knocked through for loops and shortcuts.
    Measured: a cell is on average 10 cells (71 blocks) from an exit, at worst 26 (a pure perfect
    maze had walks of 159 cells).
  - **Workmen's marks**: a copper arrow inlaid in the floor at every junction, pointing along the
    maze toward the nearest exit, plus one inside the lab hall's right doorway and one at each
    ladder's foot. Checked: from every junction, following the arrows reaches an exit.
  - **Warm light**: within 9 cells of a lab, the lanterns are warm orange instead of blue.
  - **Exit hatches** give full light and drift sparks down the corridor.
- **The halls** (the user: "try your absolute best"): 17 x 17 and two storeys, vaulted to y 14.
  Copper and verdigris Greek key mosaic floor; four red-and-black Minoan columns; a blue fresco
  band after Knossos; copper bulbs in the upper walls; carved lintels over the doorways; shelves,
  chiseled bookshelves and candles along every solid wall; and Daedalus's Wings hung on chains
  over the middle. Three themes: the **Forge** (blast furnaces, lava, anvil, bronze stock), the
  **Drafting Room** (a copper model of a maze on a table, map tables, and two lecterns holding
  *On Wings* and *On the Thread*) and the **Hall of Automata** (eight bronze figures in chains,
  as the myth says his statues had to be chained). Loot chest in the middle, Minotaur in the hall.

**Placeholders to replace with real art:** every texture is a vanilla one (tuff, copper, string,
netherite axe), and the Minotaur draws as a ravager.

**Still open for the Labyrinth:**
1. ~~"After 1 Minecraft day"~~ **Decided (2026-11-25): the world's age.** Nobody falls in until
   the world has existed for 24,000 ticks; after that, every player can, including new ones.
2. **What counts as stone?** Only `stone`, or deepslate, granite, diorite, andesite and tuff too?
   (proposed: the `#minecraft:base_stone_overworld` tag, which is all of those.)
3. **Only blocks a player breaks by hand,** not Create drills or other machines? (proposed: yes.)
4. **Where does an Exit Ladder take you?** Back to where you fell in, or to your spawn point?
   (proposed: where you fell in.)
5. **Is there a cooldown** after coming home before you can fall in again?
6. **The Minotaur:** one boss in a special place, or roaming the maze?
7. **Unbreakable walls?** (proposed: yes.)
8. **What do the Labs hold?** Loot, lore, the labrys, a workshop to craft something?

## 6. Beyond Greek (not now)

Not planned (decided: Greek first). Only once every Greek site meets the formula (5.0) would another
mythology be considered, and it would have to meet the same eight points. Notes for then:

- **Norse:** frost giant country in the snowy megaranges; a dwarven forge under a volcano; a Bifrost
  arch of light over a fjord.
- **Celtic:** Avalon, an isle hidden in sea mist; fairy rings that grow round Grand Atlas's existing
  standing stones.
- **Egyptian:** a desert necropolis in the widened deserts.

One rule if this grows: **one underworld only.** Domus Mortus is the land of the dead. Other
mythologies bring places in the living world, not rival afterlives.

---

## 7. Phases (proposed)

| # | Phase | Done when |
|---|---|---|
| **L0** | Section 9 answered | Answers in this doc |
| **L1** | The Labyrinth (5.4, steps LB1–LB9), starting with the empty `myths/` mod. This is where the formula (5.0) is proven and where the user learns modding | All eight points met; found, walked, Minotaur beaten |
| **L2** | Move Domus Mortus out: Grand Atlas builds and runs without it | Grand Atlas's checks pass alone; Domus loads from the add-on; `domusCheck` passes; a world made with Grand Atlas alone is identical before and after the move |
| **L3** | Domus Mortus finished (UNDERWORLD_DESIGN.md U1–U7) | Ships with the Labyrinth as Myths & Legends 1.0 |
| **L4** | Olympus, the Gorgon's Isle, the Hesperides, Atlantis, the Oracle | Each recognisable in a screenshot |
| **L5** | The creatures, as models are made | — |
| **L6** | The user's structures placed on their pads | — |

---

## 8. Risks

| Risk | Why | Mitigation |
|---|---|---|
| Two jars drift apart | A Grand Atlas change breaks Myths | One repo, one build; Myths' checks run on every Grand Atlas test build |
| Myths code creeps into Grand Atlas | Easy to add "just one" hook | The dependency rule (3.2); Grand Atlas's checks run with Myths absent |
| Myths quietly changes Grand Atlas's world | A site that "just nudges" the terrain breaks the ground rule | Sites only choose existing places (5.0, point 1); a check compares a Grand Atlas region with and without the add-on: only Myths' own blocks may differ |
| Few places fit | A site that only uses existing terrain may be rare where its kind of place is rare | Measure candidates per site with the structure audit before shipping each |
| User-build workload doubles | Domus already waits on big builds; five more sites add more | Every site works with pads first; build list ordered by phase |
| Model and sound workload | Five more creatures | Creatures come in L5, after the places exist; sites don't need them to be found and walked |

---

## 9. Decisions and what's still open

Decided by the user on 2026-11-25:

1. **It's an add-on, and the underworld goes in it.**
2. **Greek to start.** Get the formula right with the Greeks first.
3. **All current Grand Atlas content stays as it is** and isn't touched. The Overworld myth sites
   live in the add-on.
4. **Name:** Grand Atlas: Myths & Legends.
5. **Domus Mortus's code moves out of Grand Atlas into the add-on** (3.3). That edits five Grand
   Atlas files but changes none of its released content.

Still open:

1. **Mod id:** `grandatlas_myths`? (proposed)
2. **Commands:** `/myths ...`, or subcommands under `/grandatlas`? (proposed: `/myths`, so the
   add-on doesn't reach into Grand Atlas's command tree.)
3. **Which sites to keep** from 5.1, and which to add.
4. **Music** for Domus (carried over from UNDERWORLD_DESIGN.md).

---

## 10. Teaching (decided, hard requirement)

The user, 2026-11-25: the add-on is built so the user **learns Java Minecraft modding as it goes**.
This is a requirement, not a nice-to-have.

- **In conversation:** each new idea is explained in plain words when it first comes up, with a
  pointer to the file where it lives. Only what the current step needs, no lectures in advance.
- **In the code:** an abundance of comments. Every file starts with what it is and where it fits in
  Minecraft. Each part says what it does, the *why*, and the Minecraft or NeoForge idea behind it
  (registries, codecs, data packs, events, client and server). This is the opposite of Grand Atlas
  core's terse style, and applies only to the add-on.
- **Each step ends in game,** so every idea is seen working (5.4, "Building it in steps").
- **The user writes the code (decided, 2026-11-25, after LB1).** From LB2 on, Claude gives what's
  needed to write each step (the ideas, the classes and files involved, the formats, where to look
  in Minecraft's source, the pitfalls) and audits the user's code. Claude fixes things or steps in
  directly only when the user is stuck or asks. LB1 was written by Claude as the worked example.

### 10.1 Feature or structure (the first big idea)

Minecraft has two ways for a mod to put things into a generated world:
- A **feature** runs once per chunk while the chunk is made and places blocks inside it. Trees, ore,
  flowers, and almost everything in Grand Atlas (`world/AtlasFeature`) are features. It's simple and
  fast, but a feature only sees its own chunk (and a little around it), so anything bigger has to be
  worked out the same way, again, from every chunk it touches.
- A **structure** is planned once, at a starting chunk, as a list of **pieces** with boxes. Each
  chunk the structure reaches then builds just the pieces that fall inside it. Villages, strongholds
  and ancient cities are structures. Minecraft also gives structures spacing rules, `/locate`, their
  own mob spawns and loot, and advancements like "enter a stronghold".

The Labyrinth uses both ideas. An infinite maze can't be planned once, so it's made chunk by chunk,
like a feature, with every chunk working out its own walls from the seed. The Labs and Exit Ladders
are buildings, each planned once and placed with spacing, so they're structures.
