package com.seaweed.grandatlas.myths.labyrinth;

import com.seaweed.grandatlas.myths.GrandAtlasMyths;
import com.seaweed.grandatlas.myths.block.MythsBlocks;
import com.seaweed.grandatlas.myths.entity.Minotaur;
import com.seaweed.grandatlas.myths.entity.MythsEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.network.Filterable;
import net.minecraft.util.RandomSource;
import net.minecraft.world.RandomizableContainer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.item.component.WrittenBookContent;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CandleBlock;
import net.minecraft.world.level.block.ChainBlock;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.ChiseledBookShelfBlock;
import net.minecraft.world.level.block.CopperBulbBlock;
import net.minecraft.world.level.block.GlazedTerracottaBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.LecternBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.entity.LecternBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.storage.loot.LootTable;

import java.util.ArrayList;
import java.util.List;

/**
 * Daedalus's Labs: the halls at the heart of each Labyrinth region.
 *
 * <h2>The hall</h2>
 * A 17 x 17 room ({@link #HALF} each way from the centre), open from the floor (y 2) up to a
 * vaulted roof at y 14, so it rises well above the corridors' 5. In order, from the ground up:
 * <ul>
 *   <li><b>The floor</b>: a mosaic. A copper plate in the middle, a copper ring, then a wide
 *       band of Greek key (meander) pattern in verdigris copper on polished tuff, edged with
 *       tuff bricks.</li>
 *   <li><b>Shelves and alcoves</b> along every stretch of wall that isn't a doorway: chiseled
 *       bookshelves full of books, bookshelves, and barrels with candles burning on them.</li>
 *   <li><b>Four Minoan columns</b>, red with a black capital and a copper band, standing on the
 *       key pattern. (Minoan columns really were red and black, and wider at the top.)</li>
 *   <li><b>Doorways</b> get a carved lintel; above them runs a <b>fresco band</b> of blue glazed
 *       terracotta, after the blue frescoes of Knossos.</li>
 *   <li><b>The upper walls</b>, lit by copper bulbs, with the maze's frieze and trim.</li>
 *   <li><b>The Wings</b>: Daedalus's wings, the ones he made for himself and Icarus, hung on
 *       chains from the vault over the middle of the hall.</li>
 * </ul>
 *
 * <h2>Three themes</h2>
 * Each lab is one of {@link Theme}: the <b>Forge</b> (blast furnaces, lava, anvil, bronze), the
 * <b>Drafting Room</b> (a model of the Labyrinth on a table, lecterns with Daedalus's writings,
 * map tables) or the <b>Hall of Automata</b> (bronze figures in chains, as the myths say his
 * statues had to be chained or they'd walk away). Every lab has its loot chest in the middle
 * and a Minotaur.
 *
 * <h2>How it's placed</h2>
 * {@link #build} lists every block of the hall through a {@link Sink}; the chunk generator's
 * sink keeps the ones inside the chunk it's making, so each chunk builds its share. Things
 * that need a block entity or are entities (the chest, the lectern books, the automata, the
 * Minotaur) are placed by {@link #placeContents}, one chunk at a time, once the chunk exists.
 */
public final class LabyrinthLab {

    /** Blocks from the centre to the inner edge of the hall: the hall is 17 x 17. */
    public static final int HALF = 8;
    /** The hall's floor surface, its open height, and its roof. */
    public static final int FLOOR = 1, TOP = 13, ROOF = 14, ROOF_TOP = 15;

    public enum Theme { FORGE, DRAFTING, AUTOMATA }

    /** Receives one block in world coordinates. */
    @FunctionalInterface
    public interface Sink {
        void set(int x, int y, int z, BlockState state);
    }

    /** The meander band, 5 rows deep (outermost first), repeating every 6 blocks along each side. */
    private static final String[] KEY = {
            "XXXXX.",
            "....X.",
            "XXX.X.",
            "X...X.",
            "XXXXX."};

    private static final ResourceKey<LootTable> LAB_LOOT = ResourceKey.create(Registries.LOOT_TABLE,
            ResourceLocation.fromNamespaceAndPath(GrandAtlasMyths.MOD_ID, "chests/daedalus_lab"));
    /** Entity tag on the automata, so a shifting region can clear them away. */
    public static final String AUTOMATON_TAG = GrandAtlasMyths.MOD_ID + ".automaton";

    private LabyrinthLab() {}

    // ── Blocks ──────────────────────────────────────────────────────────────────────────────

    public static void build(LabyrinthMaze m, int rx, int rz, Sink sink) {
        int[] c = m.labCentre(rx, rz);
        int x0 = c[0], z0 = c[1];
        Theme theme = Theme.values()[m.labTheme(rx, rz)];
        Placer p = (dx, y, dz, s) -> sink.set(x0 + dx, y, z0 + dz, s);

        // The shell: open the hall up to the vault, then the roof and the ring of upper walls.
        BlockState stone = MythsBlocks.LABYRINTH_STONE.get().defaultBlockState();
        BlockState bricks = MythsBlocks.LABYRINTH_BRICKS.get().defaultBlockState();
        BlockState frieze = MythsBlocks.LABYRINTH_FRIEZE.get().defaultBlockState();
        BlockState trim = MythsBlocks.LABYRINTH_TRIM.get().defaultBlockState();
        for (int dx = -HALF - 1; dx <= HALF + 1; dx++) {
            for (int dz = -HALF - 1; dz <= HALF + 1; dz++) {
                int r = Math.max(Math.abs(dx), Math.abs(dz));
                if (r <= HALF) {
                    p.set(dx, FLOOR, dz, floorAt(dx, dz, r));
                    for (int y = FLOOR + 1; y <= TOP; y++) p.set(dx, y, dz, Blocks.AIR.defaultBlockState());
                    p.set(dx, ROOF, dz, stone);
                    p.set(dx, ROOF_TOP, dz, stone);
                } else {
                    // r == HALF + 1: the ring just outside the hall. Below y 7 it's the maze's own
                    // wall (or a doorway); above, the hall's upper wall.
                    boolean doorway = m.isOpen(x0 + dx, z0 + dz);
                    if (doorway) p.set(dx, 6, dz, frieze); // a carved lintel over each doorway
                    Direction inward = inward(dx, dz);
                    BlockState fresco = Blocks.LIGHT_BLUE_GLAZED_TERRACOTTA.defaultBlockState()
                            .setValue(GlazedTerracottaBlock.FACING, Math.floorMod(dx + dz, 2) == 0 ? inward : inward.getClockWise());
                    p.set(dx, 7, dz, fresco);
                    p.set(dx, 8, dz, fresco);
                    for (int y = 9; y <= 11; y++) p.set(dx, y, dz, bricks);
                    p.set(dx, 12, dz, frieze);
                    p.set(dx, 13, dz, trim);
                    p.set(dx, ROOF, dz, stone);
                    p.set(dx, ROOF_TOP, dz, stone);
                    // Copper bulbs set into the upper wall, four per side.
                    int along = Math.abs(dx) == HALF + 1 ? dz : dx;
                    if (Math.abs(dx) != Math.abs(dz) && (along == -6 || along == -2 || along == 2 || along == 6)) {
                        p.set(dx, 10, dz, Blocks.WAXED_COPPER_BULB.defaultBlockState().setValue(CopperBulbBlock.LIT, true));
                    }
                }
            }
        }

        // Shelves and alcoves along the walls, wherever the wall behind is solid.
        for (int along = -6; along <= 6; along++) {
            for (Direction side : Direction.Plane.HORIZONTAL) {
                int dx = side.getStepX() * HALF + (side.getStepX() == 0 ? along : 0);
                int dz = side.getStepZ() * HALF + (side.getStepZ() == 0 ? along : 0);
                if (!solidBehind(m, x0 + dx, z0 + dz, side)) continue;
                Direction face = side.getOpposite(); // shelves face into the hall
                long h = m.labHash(rx, rz, 31 * along + side.ordinal());
                switch (Math.floorMod(along, 4)) {
                    case 0 -> {
                        p.set(dx, 2, dz, Blocks.BOOKSHELF.defaultBlockState());
                        p.set(dx, 3, dz, chiseledShelf(face, h));
                    }
                    case 1 -> {
                        p.set(dx, 2, dz, chiseledShelf(face, h));
                        p.set(dx, 3, dz, Blocks.BOOKSHELF.defaultBlockState());
                    }
                    case 2 -> {
                        p.set(dx, 2, dz, Blocks.BARREL.defaultBlockState().setValue(BlockStateProperties.FACING, Direction.UP));
                        p.set(dx, 3, dz, Blocks.CANDLE.defaultBlockState().setValue(CandleBlock.CANDLES, 1 + (int) Math.floorMod(h, 4)).setValue(CandleBlock.LIT, true));
                    }
                    default -> {
                        p.set(dx, 2, dz, Blocks.BOOKSHELF.defaultBlockState());
                        p.set(dx, 3, dz, Blocks.BOOKSHELF.defaultBlockState());
                    }
                }
            }
        }

        // Four Minoan columns.
        for (int sx = -1; sx <= 1; sx += 2) for (int sz = -1; sz <= 1; sz += 2) column(p, 5 * sx, 5 * sz);

        // The Wings, hung over the middle of the hall, spanning east to west.
        int[][] wing = {{1, 9}, {2, 9}, {3, 10}, {4, 10}, {5, 11}, {6, 12}}; // {dx, lowest y}; all reach y 12
        for (int[] w : wing) {
            for (int y = w[1]; y <= 12; y++) {
                BlockState feather = (w[0] >= 5 || y == w[1] ? Blocks.LIGHT_GRAY_WOOL : Blocks.WHITE_WOOL).defaultBlockState();
                p.set(w[0], y, 0, feather);
                p.set(-w[0], y, 0, feather);
            }
        }
        for (int y = 10; y <= 12; y++) p.set(0, y, 0, Blocks.BIRCH_FENCE.defaultBlockState()); // the frame's spine
        BlockState chain = Blocks.CHAIN.defaultBlockState().setValue(ChainBlock.AXIS, Direction.Axis.Y);
        p.set(0, 13, 0, chain);
        p.set(3, 13, 0, chain);
        p.set(-3, 13, 0, chain);

        // Lanterns hung from the vault in the four corners.
        BlockState lantern = Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true);
        for (int sx = -1; sx <= 1; sx += 2) for (int sz = -1; sz <= 1; sz += 2) {
            p.set(7 * sx, 13, 7 * sz, chain);
            p.set(7 * sx, 12, 7 * sz, chain);
            p.set(7 * sx, 11, 7 * sz, lantern);
        }

        // The workmen's mark inside the doorway that leads on toward the nearest exit.
        int[] out = m.labExit(rx, rz);
        if (out != null) {
            int cx = out[0] * LabyrinthMaze.CELL + 3, cz = out[1] * LabyrinthMaze.CELL + 3;
            Direction dir = switch (out[2]) {
                case LabyrinthMaze.NORTH -> Direction.NORTH;
                case LabyrinthMaze.SOUTH -> Direction.SOUTH;
                case LabyrinthMaze.WEST -> Direction.WEST;
                default -> Direction.EAST;
            };
            sink.set(cx + dir.getStepX(), FLOOR, cz + dir.getStepZ(), MythsBlocks.LABYRINTH_MARKER.get().defaultBlockState()
                    .setValue(com.seaweed.grandatlas.myths.block.LabyrinthMarkerBlock.FACING, dir));
        }

        switch (theme) {
            case FORGE -> forge(p);
            case DRAFTING -> drafting(m, rx, rz, p);
            case AUTOMATA -> automata(p);
        }
    }

    /** The mosaic floor. */
    private static BlockState floorAt(int dx, int dz, int r) {
        if (r <= 1) return Blocks.WAXED_CHISELED_COPPER.defaultBlockState();
        if (r == 2) return Blocks.WAXED_CUT_COPPER.defaultBlockState();
        if (r == HALF) return Blocks.TUFF_BRICKS.defaultBlockState();
        int depth = (HALF - 1) - r; // 0 at the outer edge of the band, 4 at the inner
        int along = Math.abs(dz) == r ? dx : dz;
        boolean key = KEY[depth].charAt(Math.floorMod(along + HALF, 6)) == 'X';
        return (key ? Blocks.WAXED_OXIDIZED_CUT_COPPER : Blocks.POLISHED_TUFF).defaultBlockState();
    }

    /** A red Minoan column with a black capital, a copper band and a square abacus. */
    private static void column(Placer p, int dx, int dz) {
        p.set(dx, 2, dz, Blocks.POLISHED_BLACKSTONE.defaultBlockState());
        for (int y = 3; y <= 9; y++) p.set(dx, y, dz, Blocks.RED_TERRACOTTA.defaultBlockState());
        p.set(dx, 10, dz, Blocks.WAXED_CUT_COPPER.defaultBlockState());
        p.set(dx, 11, dz, Blocks.POLISHED_BLACKSTONE.defaultBlockState());
        for (int ax = -1; ax <= 1; ax++) for (int az = -1; az <= 1; az++)
            p.set(dx + ax, 12, dz + az, Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState());
    }

    /** The Forge: where Daedalus worked bronze. */
    private static void forge(Placer p) {
        dais(p);
        BlockState furnace = Blocks.BLAST_FURNACE.defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, Direction.EAST);
        p.set(-4, 2, -1, furnace);
        p.set(-4, 2, 1, furnace);
        p.set(-4, 2, 0, Blocks.LAVA_CAULDRON.defaultBlockState());
        p.set(-5, 2, 0, Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState());
        p.set(-5, 3, 0, Blocks.CAMPFIRE.defaultBlockState()); // a lit hearth above the lava, smoking
        p.set(3, 2, 0, Blocks.ANVIL.defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, Direction.NORTH));
        p.set(3, 2, 2, Blocks.SMITHING_TABLE.defaultBlockState());
        p.set(3, 2, -2, Blocks.GRINDSTONE.defaultBlockState()
                .setValue(BlockStateProperties.ATTACH_FACE, net.minecraft.world.level.block.state.properties.AttachFace.FLOOR));
        p.set(2, 2, 3, Blocks.BARREL.defaultBlockState().setValue(BlockStateProperties.FACING, Direction.UP));
        p.set(-2, 2, 3, Blocks.BARREL.defaultBlockState().setValue(BlockStateProperties.FACING, Direction.UP));
        // Bronze stock: copper blocks at every stage of weathering.
        p.set(-2, 2, -3, Blocks.WAXED_COPPER_BLOCK.defaultBlockState());
        p.set(-2, 3, -3, Blocks.WAXED_EXPOSED_COPPER.defaultBlockState());
        p.set(-1, 2, -3, Blocks.WAXED_WEATHERED_COPPER.defaultBlockState());
        p.set(-3, 2, -3, Blocks.WAXED_OXIDIZED_COPPER.defaultBlockState());
        p.set(2, 2, -3, Blocks.CAULDRON.defaultBlockState());
    }

    /** The Drafting Room: a model of the Labyrinth, Daedalus's writings and his map tables. */
    private static void drafting(LabyrinthMaze m, int rx, int rz, Placer p) {
        // The model's table, 7 x 7, and on it a little 3x3-cell maze of copper slabs.
        for (int dx = -3; dx <= 3; dx++) for (int dz = -3; dz <= 3; dz++)
            p.set(dx, 2, dz, Blocks.POLISHED_TUFF.defaultBlockState());
        boolean[][] wall = miniMaze(m.labHash(rx, rz, 7));
        BlockState slab = Blocks.WAXED_WEATHERED_CUT_COPPER_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM);
        for (int i = 0; i < 7; i++) for (int j = 0; j < 7; j++)
            if (wall[i][j]) p.set(i - 3, 3, j - 3, slab);
        // Map tables with candles; lecterns are placed with their books in placeContents.
        for (int sx = -1; sx <= 1; sx += 2) {
            p.set(5 * sx, 2, 0, Blocks.CARTOGRAPHY_TABLE.defaultBlockState());
            p.set(5 * sx, 3, 0, Blocks.CANDLE.defaultBlockState().setValue(CandleBlock.CANDLES, 3).setValue(CandleBlock.LIT, true));
            p.set(5 * sx, 2, 2, Blocks.BARREL.defaultBlockState().setValue(BlockStateProperties.FACING, Direction.UP));
            p.set(5 * sx, 2, -2, Blocks.LOOM.defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, sx > 0 ? Direction.WEST : Direction.EAST));
        }
        p.set(0, 2, -5, lectern(Direction.SOUTH, false));
        p.set(0, 2, 5, lectern(Direction.NORTH, false));
    }

    /**
     * A tiny perfect maze of 3x3 cells drawn on a 7x7 grid (cells at odd indexes, walls between),
     * with a way in at the north edge and the centre cell left free for the chest.
     */
    private static boolean[][] miniMaze(long seed) {
        boolean[][] wall = new boolean[7][7];
        for (int i = 0; i < 7; i++) for (int j = 0; j < 7; j++) wall[i][j] = i % 2 == 0 || j % 2 == 0;
        java.util.Random random = new java.util.Random(seed);
        boolean[][] seen = new boolean[3][3];
        java.util.ArrayDeque<int[]> stack = new java.util.ArrayDeque<>();
        stack.push(new int[]{1, 1});
        seen[1][1] = true;
        int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        while (!stack.isEmpty()) {
            int[] c = stack.peek();
            List<int[]> options = new ArrayList<>();
            for (int[] d : dirs) {
                int nx = c[0] + d[0], nz = c[1] + d[1];
                if (nx >= 0 && nz >= 0 && nx < 3 && nz < 3 && !seen[nx][nz]) options.add(d);
            }
            if (options.isEmpty()) { stack.pop(); continue; }
            int[] d = options.get(random.nextInt(options.size()));
            int nx = c[0] + d[0], nz = c[1] + d[1];
            wall[1 + 2 * c[0] + d[0]][1 + 2 * c[1] + d[1]] = false; // knock through
            seen[nx][nz] = true;
            stack.push(new int[]{nx, nz});
        }
        for (int i = 0; i < 7; i++) for (int j = 0; j < 7; j++) if (i % 2 == 1 && j % 2 == 1) wall[i][j] = false;
        wall[1 + 2 * random.nextInt(3)][0] = false; // the way in, on the north edge
        return wall;
    }

    /** The Hall of Automata: a ring of chained bronze figures round a dais (figures in placeContents). */
    private static void automata(Placer p) {
        dais(p);
        BlockState chain = Blocks.CHAIN.defaultBlockState().setValue(ChainBlock.AXIS, Direction.Axis.Y);
        for (int[] spot : AUTOMATON_SPOTS) {
            p.set(spot[0], 2, spot[1], Blocks.POLISHED_BLACKSTONE.defaultBlockState()); // plinth
            // The chains that hold it: up to the vault, or up to the Wings for the two under them.
            int top = spot[1] == 0 ? 9 : 13;
            for (int y = 5; y <= top; y++) p.set(spot[0], y, spot[1], chain);
        }
    }

    private static final int[][] AUTOMATON_SPOTS = {{4, 0}, {-4, 0}, {0, 4}, {0, -4}, {3, 3}, {-3, 3}, {3, -3}, {-3, -3}};

    /** A 3x3 dais of polished blackstone in the middle, for the chest. */
    private static void dais(Placer p) {
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++)
            p.set(dx, 2, dz, Blocks.POLISHED_BLACKSTONE.defaultBlockState());
    }

    private static BlockState lectern(Direction facing, boolean hasBook) {
        return Blocks.LECTERN.defaultBlockState().setValue(LecternBlock.FACING, facing).setValue(LecternBlock.HAS_BOOK, hasBook);
    }

    /** A chiseled bookshelf facing {@code face}, with some of its six slots holding books. */
    private static BlockState chiseledShelf(Direction face, long h) {
        BlockState s = Blocks.CHISELED_BOOKSHELF.defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, face);
        for (int i = 0; i < 6; i++)
            s = s.setValue(ChiseledBookShelfBlock.SLOT_OCCUPIED_PROPERTIES.get(i), ((h >>> (i * 3)) & 7) < 5);
        return s;
    }

    /** Is the maze wall directly behind wall-side spot (x, z), and beside it, solid (not a doorway)? */
    private static boolean solidBehind(LabyrinthMaze m, int x, int z, Direction side) {
        int bx = x + side.getStepX(), bz = z + side.getStepZ();
        Direction along = side.getClockWise();
        for (int k = -1; k <= 1; k++)
            if (m.isOpen(bx + along.getStepX() * k, bz + along.getStepZ() * k)) return false;
        return true;
    }

    /** For a spot on the ring just outside the hall, the direction pointing into it. */
    private static Direction inward(int dx, int dz) {
        if (Math.abs(dx) >= Math.abs(dz)) return dx > 0 ? Direction.WEST : Direction.EAST;
        return dz > 0 ? Direction.NORTH : Direction.SOUTH;
    }

    @FunctionalInterface
    private interface Placer {
        void set(int dx, int y, int dz, BlockState state);
    }

    // ── Block entities and entities ─────────────────────────────────────────────────────────

    /**
     * Place the parts of region (rx, rz)'s lab that need block entities or are entities, but
     * only those inside chunk {@code only}. Called once per chunk: at generation (WorldGenRegion)
     * and after a shift rebuilds the chunk (the ServerLevel).
     */
    public static void placeContents(ServerLevelAccessor level, LabyrinthMaze m, int rx, int rz, ChunkPos only, RandomSource random) {
        int[] c = m.labCentre(rx, rz);
        int x0 = c[0], z0 = c[1];
        Theme theme = Theme.values()[m.labTheme(rx, rz)];

        // The loot chest, on the dais or the model's centre cell.
        BlockPos chest = new BlockPos(x0, 3, z0);
        if (in(only, chest)) {
            level.setBlock(chest, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.SOUTH), 2);
            // The loot table is rolled the first time someone opens it, so every chest differs.
            RandomizableContainer.setBlockEntityLootTable(level, random, chest, LAB_LOOT);
        }

        if (theme == Theme.DRAFTING) {
            placeLectern(level, only, new BlockPos(x0, 2, z0 - 5), Direction.SOUTH, onWings());
            placeLectern(level, only, new BlockPos(x0, 2, z0 + 5), Direction.NORTH, onTheThread());
        }

        if (theme == Theme.AUTOMATA) {
            for (int[] spot : AUTOMATON_SPOTS) {
                BlockPos at = new BlockPos(x0 + spot[0], 3, z0 + spot[1]);
                if (!in(only, at)) continue;
                Entity figure = automaton(level, at, x0 + 0.5, z0 + 0.5);
                if (figure != null) level.addFreshEntity(figure);
            }
        }

        // The Minotaur, in the hall's north-east quarter.
        BlockPos beast = new BlockPos(x0 + 2, 2, z0 - 6);
        if (in(only, beast)) {
            Minotaur minotaur = MythsEntities.MINOTAUR.get().create(level.getLevel());
            if (minotaur != null) {
                minotaur.moveTo(beast.getX() + 0.5, beast.getY(), beast.getZ() + 0.5, random.nextFloat() * 360f, 0f);
                minotaur.finalizeSpawn(level, level.getCurrentDifficultyAt(beast), MobSpawnType.STRUCTURE, null);
                minotaur.setPersistenceRequired();
                level.addFreshEntityWithPassengers(minotaur);
            }
        }
    }

    private static boolean in(ChunkPos chunk, BlockPos pos) {
        return pos.getX() >= chunk.getMinBlockX() && pos.getX() <= chunk.getMaxBlockX()
                && pos.getZ() >= chunk.getMinBlockZ() && pos.getZ() <= chunk.getMaxBlockZ();
    }

    private static void placeLectern(ServerLevelAccessor level, ChunkPos only, BlockPos pos, Direction facing, ItemStack book) {
        if (!in(only, pos)) return;
        level.setBlock(pos, lectern(facing, true), 2);
        if (level.getBlockEntity(pos) instanceof LecternBlockEntity lectern) lectern.setBook(book);
    }

    /** A bronze automaton: an armour stand in bronze-dyed leather, arms raised, facing the centre. */
    private static Entity automaton(ServerLevelAccessor level, BlockPos at, double faceX, double faceZ) {
        CompoundTag tag = new CompoundTag();
        tag.putString("id", "minecraft:armor_stand");
        tag.putBoolean("ShowArms", true);
        tag.putBoolean("NoBasePlate", true);
        CompoundTag pose = new CompoundTag();
        pose.put("RightArm", rotations(-110f, 0f, 10f));
        pose.put("LeftArm", rotations(-110f, 0f, -10f));
        pose.put("Head", rotations(-15f, 0f, 0f));
        tag.put("Pose", pose);
        Entity entity = EntityType.create(tag, level.getLevel()).orElse(null);
        if (!(entity instanceof net.minecraft.world.entity.decoration.ArmorStand stand)) return null;
        float yaw = (float) (Math.toDegrees(Math.atan2(faceZ - (at.getZ() + 0.5), faceX - (at.getX() + 0.5))) - 90.0);
        stand.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, yaw, 0f);
        stand.setYHeadRot(yaw);
        stand.setYBodyRot(yaw);
        stand.setItemSlot(EquipmentSlot.HEAD, bronze(Items.LEATHER_HELMET));
        stand.setItemSlot(EquipmentSlot.CHEST, bronze(Items.LEATHER_CHESTPLATE));
        stand.setItemSlot(EquipmentSlot.LEGS, bronze(Items.LEATHER_LEGGINGS));
        stand.setItemSlot(EquipmentSlot.FEET, bronze(Items.LEATHER_BOOTS));
        stand.addTag(AUTOMATON_TAG);
        return stand;
    }

    private static ListTag rotations(float x, float y, float z) {
        ListTag list = new ListTag();
        list.add(FloatTag.valueOf(x));
        list.add(FloatTag.valueOf(y));
        list.add(FloatTag.valueOf(z));
        return list;
    }

    private static ItemStack bronze(net.minecraft.world.item.Item item) {
        ItemStack stack = new ItemStack(item);
        stack.set(DataComponents.DYED_COLOR, new DyedItemColor(0xA0652D, false));
        return stack;
    }

    // ── Daedalus's writings (on the Drafting Room's lecterns) ───────────────────────────────

    private static ItemStack onWings() {
        return book("On Wings", List.of(
                "Feathers, laid small to large, as a pan-pipe rises. Bound with thread at the middle, with wax at the ends. Bent a little, to curve like a bird's.",
                "The King keeps us here. He watches the sea and the roads. He does not watch the sky.",
                "To my son: not too low, or the sea's damp will weigh the feathers. Not too high, or the sun will soften the wax. Keep to the middle way.",
                "I have hung them where I can see them. I have not yet found the courage to try them again."));
    }

    private static ItemStack onTheThread() {
        return book("On the Thread", List.of(
                "Every hall of this place turns back on itself. I made it so. A man may walk until he dies and swear he walked straight.",
                "And now the walls move. I did not make them so. Something in the stone remembers my drawings, and redraws them when no one is looking.",
                "Only the doors between the great quarters stay where I put them. Learn them, and you can always go on.",
                "The green arrows in the floor are the workmen's marks. They point to the hatches. Trust them over your own eyes."));
    }

    private static ItemStack book(String title, List<String> pages) {
        ItemStack stack = new ItemStack(Items.WRITTEN_BOOK);
        List<Filterable<Component>> content = pages.stream().map(t -> Filterable.passThrough((Component) Component.literal(t))).toList();
        stack.set(DataComponents.WRITTEN_BOOK_CONTENT, new WrittenBookContent(Filterable.passThrough(title), "Daedalus", 0, content, true));
        return stack;
    }
}
