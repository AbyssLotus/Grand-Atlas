package com.seaweed.grandatlas.myths.block;

import com.seaweed.grandatlas.myths.GrandAtlasMyths;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Every block the add-on adds, and its item.
 *
 * <p>The maze is built from four unbreakable blocks (stone for floor and ceiling, bricks,
 * frieze and copper trim for the walls), so it has to be walked, not tunnelled. All four
 * are in the Wither's and the dragon's immunity tags (data/minecraft/tags/block/).
 */
public class MythsBlocks {

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(GrandAtlasMyths.MOD_ID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(GrandAtlasMyths.MOD_ID);

    /** The settings every unbreakable maze block shares, like bedrock's. */
    private static BlockBehaviour.Properties unbreakable(SoundType sound) {
        return BlockBehaviour.Properties.of()
                .destroyTime(-1)                  // -1 = can't be mined (pistons refuse it too)
                .explosionResistance(3_600_000f)  // bedrock's value
                .noLootTable()
                .sound(sound)
                // NONE draws nothing on maps, on purpose (the user's call, LB2): the Labyrinth
                // is a dungeon you can't map. Every maze block uses it, or the walls would give
                // the layout away.
                .mapColor(MapColor.NONE);
    }

    // ── The maze ─────────────────────────────────────────────────────────────────────────────

    /** Floor and ceiling. (The user's first block, LB2.) */
    public static final DeferredBlock<Block> LABYRINTH_STONE = BLOCKS.registerSimpleBlock("labyrinth_stone",
            unbreakable(SoundType.STONE));
    public static final DeferredItem<BlockItem> LABYRINTH_STONE_ITEM = ITEMS.registerSimpleBlockItem("labyrinth_stone", LABYRINTH_STONE);

    /** The walls. Looks like tuff bricks. */
    public static final DeferredBlock<Block> LABYRINTH_BRICKS = BLOCKS.registerSimpleBlock("labyrinth_bricks",
            unbreakable(SoundType.TUFF_BRICKS));
    public static final DeferredItem<BlockItem> LABYRINTH_BRICKS_ITEM = ITEMS.registerSimpleBlockItem("labyrinth_bricks", LABYRINTH_BRICKS);

    /** The carved band along every wall at head height. Looks like chiseled tuff bricks. */
    public static final DeferredBlock<Block> LABYRINTH_FRIEZE = BLOCKS.registerSimpleBlock("labyrinth_frieze",
            unbreakable(SoundType.TUFF_BRICKS));
    public static final DeferredItem<BlockItem> LABYRINTH_FRIEZE_ITEM = ITEMS.registerSimpleBlockItem("labyrinth_frieze", LABYRINTH_FRIEZE);

    /** The bronze-age trim along the top of the walls. Looks like weathered copper. */
    public static final DeferredBlock<Block> LABYRINTH_TRIM = BLOCKS.registerSimpleBlock("labyrinth_trim",
            unbreakable(SoundType.COPPER));
    public static final DeferredItem<BlockItem> LABYRINTH_TRIM_ITEM = ITEMS.registerSimpleBlockItem("labyrinth_trim", LABYRINTH_TRIM);

    // ── Ways out and ways back ───────────────────────────────────────────────────────────────

    /** In the ceiling above each exit ladder; use it to go home. Full light and falling sparks, so it can be spotted. */
    public static final DeferredBlock<ExitHatchBlock> EXIT_HATCH = BLOCKS.registerBlock("exit_hatch",
            ExitHatchBlock::new,
            unbreakable(SoundType.COPPER).lightLevel(state -> 15));
    public static final DeferredItem<BlockItem> EXIT_HATCH_ITEM = ITEMS.registerSimpleBlockItem("exit_hatch", EXIT_HATCH);

    /** The workmen's mark: a floor tile with a copper arrow pointing toward the nearest exit. */
    public static final DeferredBlock<LabyrinthMarkerBlock> LABYRINTH_MARKER = BLOCKS.registerBlock("labyrinth_marker",
            LabyrinthMarkerBlock::new,
            unbreakable(SoundType.STONE));
    public static final DeferredItem<BlockItem> LABYRINTH_MARKER_ITEM = ITEMS.registerSimpleBlockItem("labyrinth_marker", LABYRINTH_MARKER);

    /** The glowing trail Ariadne's thread lays on the floor. No item: only the thread places it. */
    public static final DeferredBlock<AriadneThreadBlock> ARIADNE_THREAD = BLOCKS.registerBlock("ariadne_thread",
            AriadneThreadBlock::new,
            BlockBehaviour.Properties.of()
                    .noCollission()
                    .instabreak()
                    .noLootTable()
                    .noOcclusion()
                    .replaceable()
                    .pushReaction(PushReaction.DESTROY)
                    .sound(SoundType.WOOL)
                    .lightLevel(state -> 6)
                    .mapColor(MapColor.WOOL));

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
    }
}
