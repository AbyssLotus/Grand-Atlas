# Learning to mod with Myths & Legends

A running notebook: one section per step of the Labyrinth (docs/MYTHS_AND_LEGENDS.md, section 5.4).
Each section lists the ideas that step introduced and the files where you can see them. The code
comments go deeper; this is the map.

---

## LB1: an empty mod that loads

**Goal:** the add-on starts next to Grand Atlas and writes three lines to the log.

### The ideas

| Idea | In one line | Where to look |
|---|---|---|
| **Gradle** | The build tool. You ask it to run a task (`build`, `runClient`) and it does every step | `myths/build.gradle` |
| **Subproject** | One repo, several things to build. The add-on is its own project next to Grand Atlas's | `settings.gradle` (root), `myths/` |
| **ModDevGradle (MDG)** | NeoForge's Gradle plugin: downloads Minecraft, makes the run tasks | `plugins { }` in `myths/build.gradle` |
| **`gradle.properties`** | Plain settings. The add-on's copy replaces the root's for the same keys | `myths/gradle.properties` |
| **Mod id** | The permanent namespace of everything we add: `grandatlas_myths:...` | `myths/gradle.properties` |
| **`neoforge.mods.toml`** | The mod's ID card, read before any code runs: id, version, dependencies | `myths/src/main/templates/META-INF/` |
| **Dependencies** | "Requires Grand Atlas 1.0.1 or newer, below 2, and load after it" | same file, bottom |
| **The `@Mod` main class** | The front door. NeoForge builds one instance at startup | `GrandAtlasMyths.java` |
| **Events** | The game sends messages ("setup", "server started"); mods listen | `GrandAtlasMyths.java` |
| **Mod bus vs game bus** | Mod bus = loading the mod (once). Game bus = playing (over and over) | `GrandAtlasMyths.java` |
| **Logging** | `LOG.info(...)` writes to the console and `myths/run/logs/latest.log` | `GrandAtlasMyths.java` |

### Commands

On this Mac the default Java (26) is too new for Gradle, so point Gradle at Java 21 first:

```bash
export JAVA_HOME="$(ls -d ~/.gradle/jdks/eclipse_adoptium-21-aarch64-os_x.2/*/Contents/Home)"
```

Then, from the repo root:

```bash
sh gradlew :myths:build
```

```bash
sh gradlew :myths:runClient
```

(`sh gradlew` because the wrapper file lost its "executable" flag on this Mac. On Windows it's
`gradlew.bat`, or just `gradlew`.)

### What you should see in the log

```
[Myths & Legends] Constructor: the add-on is loading.                       <- startup
[Myths & Legends] Common setup: running on Grand Atlas 1.0.1+mc1.21.1.      <- mod bus
[Myths & Legends] Server started: hello from the Labyrinth's future home.   <- game bus, when you open a world
```

### Try it yourself

1. Change the hello text in `onServerStarted`, run the client again, open a world, and find your
   line in `myths/run/logs/latest.log`.
2. Move the `onServerStarted` listener onto `modBus` instead of `NeoForge.EVENT_BUS`. What happens
   when you run it? (Then put it back.) It's the wrong-bus mistake from the comments, made on
   purpose so you'll recognise it.

---

## LB2: Labyrinth Stone, an unbreakable block (written by you)

**Goal:** a block you can `/give`, place, and not break: not by hand, piston, explosion, Wither or
dragon.

### The ideas

| Idea | In one line | Where to look |
|---|---|---|
| **One object per block type** | 1,000 placed stones share ONE `Block` object; the world just notes "stone here" | — |
| **Registries** | The game's phone book: id → object. Unregistered = doesn't exist | `MythsBlocks.java` |
| **`DeferredRegister`** | List things now; NeoForge registers them at the one moment it's allowed | `MythsBlocks.java` |
| **`DeferredBlock` / `.get()`** | A placeholder that holds the real block once registration has run | `MythsBlocks.java` |
| **Block vs item** | The block is in the world; the `BlockItem` is in your hand. Two registries | `MythsBlocks.java` |
| **`BlockBehaviour.Properties`** | A builder: chain settings (`.destroyTime`, `.sound`, ...) | `MythsBlocks.java`, vanilla `Blocks` |
| **Hardness -1** | Unbreakable, and pistons refuse to push it | `PistonBaseBlock.isPushable` |
| **Lambdas** | `state -> 1` is a tiny function; used for light level | — |
| **Assets vs data** | `assets/` = client (looks, names); `data/` = server (tags, loot, later the dimension) | `resources/` |
| **Namespaced ids** | `namespace:path` → a file at `<assets or data>/<namespace>/<kind>/<path>` | — |
| **Blockstates → models → textures** | State picks a model; the model inherits a shape (`cube_all`) and names textures | `assets/grandatlas_myths/` |
| **Lang files** | Players see translations, never ids. Key: `block.<namespace>.<name>` | `lang/en_us.json` |
| **Tags** | Named lists other code checks. Same path from every mod merges into one list | `data/minecraft/tags/block/` |
| **`"replace": false`** | Add to the list. `true` wipes everyone else's entries | the tag files |

### Debugging tricks learned

- **F3** shows the targeted block's id and tags.
- **F3+T** reloads assets (models, textures, names) without restarting. Java changes need a restart.
- Purple-black checkerboard = a model or texture path is wrong; the log names the missing file.
- Ignore `Missing sound for event: minecraft:item.goat_horn.play`: vanilla's own, every dev run.

### Mistakes made on the way (worth remembering)

- A package created directly under `java/` (`Blocks`) instead of inside `com.seaweed.grandatlas.myths`.
- `asssets` and a missing namespace folder: a wrong path gives no error, the file just never loads.

---

## LB3: the empty Labyrinth dimension (JSON by you, command by Claude)

**Goal:** a dark dimension with a Labyrinth Stone floor, and `/myths labyrinth` / `/myths leave`.

| Idea | In one line | Where to look |
|---|---|---|
| **Dimension type** | The rules of physics: sky, light, height, beds | `data/grandatlas_myths/dimension_type/labyrinth.json` |
| **Dimension** | The world itself: a type plus a chunk generator | `data/grandatlas_myths/dimension/labyrinth.json` |
| **Chunk generator** | Fills each new chunk. `minecraft:flat` stacks layers; LB5 replaces it with the maze | same file |
| **Nested JSON is read field by field** | Every name (`type`, `settings`, `layers`, `block`) is looked up exactly; replace values, never the shape | same file |
| **`ResourceKey`** | How Java names a registered thing: registry + id | `MythsCommands.LABYRINTH` |
| **`ServerLevel`** | One loaded world on the server; `server.getLevel(key)` | `MythsCommands.teleport` |
| **Brigadier commands** | A tree of words; `.then` adds a child, `.requires` checks permission, `.executes` runs | `MythsCommands.onRegisterCommands` |
| **`RegisterCommandsEvent`** | Game bus, fired each time a world starts | `GrandAtlasMyths` constructor |

### Debugging tricks learned

- Crash logs: skip the `at ...` lines, read the `Caused by` lines; the last one is usually the cause.
- `/execute in <dimension> run tp @s x y z` tests a dimension before any Java exists.
- IntelliJ **Local History** (right-click a file) brings back any saved version, committed or not.

### Mistakes made on the way

- `data/grandatlas_myth/` (missing s): the namespace IS the folder name.
- `True` instead of `true`: JSON is lowercase only.
- Replacing the whole generator object instead of one value inside it.
- `.then(Commands.literal("Labyrinth"))` closed too early, so `.executes` attached to `/myths`
  itself. And command words are case-sensitive.

---

## LB4–LB9: built by Claude (the learning exercise is paused)

The rest of the Labyrinth was written in one go. Each file opens with a comment explaining it;
these are the new ideas and where they live.

| Idea | Where |
|---|---|
| Guard-clause event handlers; `getGameTime` vs `getDayTime`; `FakePlayer` | `labyrinth/LabyrinthEntry.java` |
| Why teleporting inside BreakEvent is unsafe, and queuing work to the end of the tick | `labyrinth/Labyrinth.java` |
| Persistent player data that survives death (`Player.PERSISTED_NBT_TAG`) | `labyrinth/Labyrinth.java` |
| An infinite maze from coordinates: regions, a recursive backtracker, hashed doors | `labyrinth/LabyrinthMaze.java` |
| A custom chunk generator and its codec; seeds from `RandomState`; heightmaps | `labyrinth/LabyrinthChunkGenerator.java` |
| Registering a codec-based type (`Registries.CHUNK_GENERATOR`) | `world/MythsWorldgen.java` |
| A biome in JSON: fog, ambient sound, monster spawns | `data/grandatlas_myths/worldgen/biome/labyrinth.json` |
| Custom block behaviour: `useWithoutItem`, shapes, `canSurvive` / `updateShape` | `block/ExitHatchBlock.java`, `block/AriadneThreadBlock.java` |
| Custom item behaviour: `inventoryTick`, durability | `item/AriadneThreadItem.java` |
| Weapons from vanilla classes (`AxeItem`, tiers, attributes), spawn eggs, creative tabs | `item/MythsItems.java` |
| Entities: types, attributes, boss bars, extending a vanilla mob | `entity/` |
| Client-only code and renderers (`@EventBusSubscriber(value = Dist.CLIENT)`) | `client/MythsClient.java` |
| Loot tables, including a written book built from components | `data/grandatlas_myths/loot_table/` |
| Game tests: automated checks on a headless server | `dev/LabyrinthGameTests.java`, `gradlew :myths:runGameTestServer` |

### Try it in game (new world, cheats on)

- `/myths labyrinth` to go in, `/myths leave` to go home. Or wait a day and mine stone.
- `/locate` doesn't know the labs (they're part of the maze, not structures). F3 shows the maze
  cell you're in; labs and exits come about every 147 blocks.
- Everything is in the creative tab **Myths & Legends**.

### Added in the second pass (shifting, wayfinding, the halls)

| Idea | Where |
|---|---|
| Saved data on a world (`SavedData`), and data attachments on chunks | `labyrinth/LabyrinthShift.java` |
| Re-writing live chunks safely: diffing, emptying containers, spreading work over ticks | `LabyrinthShift.rebuild` |
| Breadth-first search for distances and arrows | `LabyrinthMaze.nav` |
| A block with a property (`FACING`), and a blockstate that rotates its model | `block/LabyrinthMarkerBlock.java`, `blockstates/labyrinth_marker.json` |
| Building a model from elements instead of a parent | `models/block/labyrinth_marker.json` |
| Client-only visual effects (`animateTick`, particles) | `block/ExitHatchBlock.java` |
| Block entities with contents (lectern books), entities from NBT (posed armour stands) | `labyrinth/LabyrinthLab.java` |
