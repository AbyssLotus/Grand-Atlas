package com.seaweed.grandatlas.myths;

// ─────────────────────────────────────────────────────────────────────────────
// IMPORTS
//   `import` makes a class from somewhere else usable here by its short name.
//   - com.mojang.logging / org.slf4j: Minecraft's logging, for writing to the log.
//   - net.neoforged.*: NeoForge, the mod loader. Its classes are how a mod talks
//     to the game.
//   Minecraft's own classes live under net.minecraft.* (none needed yet).
// ─────────────────────────────────────────────────────────────────────────────
import com.mojang.logging.LogUtils;
import com.seaweed.grandatlas.myths.block.MythsBlocks;
import com.seaweed.grandatlas.myths.command.MythsCommands;
import com.seaweed.grandatlas.myths.entity.MythsEntities;
import com.seaweed.grandatlas.myths.item.MythsItems;
import com.seaweed.grandatlas.myths.labyrinth.Labyrinth;
import com.seaweed.grandatlas.myths.labyrinth.LabyrinthEntry;
import com.seaweed.grandatlas.myths.labyrinth.LabyrinthShift;
import com.seaweed.grandatlas.myths.world.MythsWorldgen;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import org.slf4j.Logger;

/**
 * The add-on's MAIN CLASS: the front door NeoForge uses to start the mod.
 *
 * <h2>How a mod starts</h2>
 * <ol>
 *   <li>NeoForge reads {@code neoforge.mods.toml} in our jar and learns the mod id,
 *       {@code grandatlas_myths}.</li>
 *   <li>It scans the jar for a class marked {@code @Mod("grandatlas_myths")}. That's
 *       this one.</li>
 *   <li>It creates ONE instance of this class by calling its constructor (the
 *       {@code public GrandAtlasMyths(...)} method below). This happens very early,
 *       while the game is still loading and before any world exists.</li>
 * </ol>
 *
 * <p>So the constructor is where a mod <em>signs up</em> for things: "here are my
 * blocks", "tell me when the server starts", "tell me when a block is broken". It
 * shouldn't <em>do</em> much itself; the game isn't ready yet.
 *
 * <h2>Events and the two event buses</h2>
 * Minecraft doesn't call mod code directly. Instead, NeoForge sends out
 * <b>events</b>: little messages like "setup is happening" or "a player broke a
 * block". A mod <b>listens</b> for the events it cares about. The messages travel on
 * two separate <b>event buses</b>:
 * <ul>
 *   <li><b>The mod bus</b> ({@code modBus} below). Events about <em>loading the
 *       mod</em>: registering blocks and items, setup, config. These happen once,
 *       while the game starts. Each mod gets its own mod bus.</li>
 *   <li><b>The game bus</b> ({@code NeoForge.EVENT_BUS}). Events about <em>playing</em>:
 *       a server starting, a player joining, a block broken, a mob spawning. These
 *       happen over and over while the game runs. All mods share one game bus.</li>
 * </ul>
 * Putting a listener on the wrong bus is one of the most common modding mistakes.
 * It compiles fine. In old Forge it then silently never fired; NeoForge checks, and
 * stops the game at startup with an error naming the event. The Labyrinth's "0.1% chance per stone
 * block" (step LB4) will be a game-bus listener.
 *
 * <p>This is step <b>LB1</b> (docs/MYTHS_AND_LEGENDS.md, section 5.4): the mod loads
 * next to Grand Atlas and says hello in the log, once on each bus.
 */
// @Mod is an ANNOTATION: a label on the class that NeoForge looks for. The id in it
// must match modId in neoforge.mods.toml exactly.
@Mod(GrandAtlasMyths.MOD_ID)
public class GrandAtlasMyths {

    /**
     * The mod id, kept in one constant so a typo can't creep in. Every id the add-on
     * creates (blocks, items, the dimension) will be "grandatlas_myths:something".
     *
     * <p>{@code public static final} means: anyone may read it ({@code public}), it
     * belongs to the class itself rather than one object ({@code static}), and it
     * never changes ({@code final}).
     */
    public static final String MOD_ID = "grandatlas_myths";

    /**
     * Our logger. Anything written with it goes to the game's log (the console and
     * {@code myths/run/logs/latest.log} in dev), tagged with this class's name, so
     * you can find our lines among thousands of others. Use LOG.info for normal
     * messages, LOG.warn for problems that aren't fatal, and LOG.error for real errors.
     */
    public static final Logger LOG = LogUtils.getLogger();

    /**
     * The constructor: NeoForge calls it once, early in startup.
     *
     * <p>NeoForge hands us two things just by naming them as parameters:
     * <ul>
     *   <li>{@code modBus}: this mod's own event bus (see above).</li>
     *   <li>{@code container}: NeoForge's record of this mod (its id, version and
     *       config). Not needed yet; it'll be used for the config file.</li>
     * </ul>
     */
    public GrandAtlasMyths(IEventBus modBus, ModContainer container) {
        LOG.info("[Myths & Legends] Constructor: the add-on is loading.");

        // Sign up for an event on the MOD bus. "this::onCommonSetup" means "when
        // FMLCommonSetupEvent happens, call this object's onCommonSetup method".
        // (That :: syntax is a METHOD REFERENCE: passing a method as a value.)
        modBus.addListener(this::onCommonSetup);

        // Sign up for an event on the GAME bus. ServerStartedEvent fires each time a
        // server finishes starting. Opening a single-player world counts: it runs a
        // small server inside your game.
        NeoForge.EVENT_BUS.addListener(this::onServerStarted);

        // MOD bus: the registries (blocks, items, the creative tab, entities, the maze generator).
        MythsBlocks.register(modBus);
        MythsItems.register(modBus);
        MythsEntities.register(modBus);
        MythsWorldgen.register(modBus);
        // The shifting maze: its chunk stamps (a mod-bus registry) and its game-bus listeners.
        LabyrinthShift.register(modBus);

        // GAME bus: the game asks for commands each time a world starts. A static method,
        // so the reference is Class::method (no `this`).
        NeoForge.EVENT_BUS.addListener(MythsCommands::onRegisterCommands);
        // GAME bus: the 0.1% chance of falling into the Labyrinth while mining stone...
        NeoForge.EVENT_BUS.addListener(LabyrinthEntry::onBlockBreak);
        // ...and the end-of-tick hook that does the queued trips in and out.
        NeoForge.EVENT_BUS.addListener(Labyrinth::onServerTick);
    }

    /**
     * MOD BUS: "common setup". It runs once, after every mod has registered its
     * blocks and items, on both the client and a dedicated server (that's what
     * "common" means). A good place for one-off setup that needs the registries
     * filled in.
     *
     * <p>Here it checks that Grand Atlas really is loaded, using {@link ModList}:
     * NeoForge's list of every mod in the game. neoforge.mods.toml already makes
     * Grand Atlas required, so this should always find it; it's here to show how
     * one mod can look up another.
     */
    private void onCommonSetup(FMLCommonSetupEvent event) {
        String grandAtlasVersion = ModList.get()
                .getModContainerById("grandatlas")               // find Grand Atlas, if loaded...
                .map(mod -> mod.getModInfo().getVersion().toString()) // ...and read its version
                .orElse("NOT LOADED");                            // or say so if it's missing
        LOG.info("[Myths & Legends] Common setup: running on Grand Atlas {}.", grandAtlasVersion);
    }

    /**
     * GAME BUS: a server has finished starting, so a world is open and playable.
     * {@code event.getServer()} is the running server. It's the gateway to every
     * world (dimension), every player and every command, and later steps will use
     * it constantly.
     */
    private void onServerStarted(ServerStartedEvent event) {
        LOG.info("[Myths & Legends] Server started: hello from the Labyrinth's future home. {} dimension(s) loaded.",
                event.getServer().levelKeys().size());

    }
}
