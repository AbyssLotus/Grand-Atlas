package com.seaweed.grandatlas.myths.labyrinth;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.seaweed.grandatlas.myths.GrandAtlasMyths;
import com.seaweed.grandatlas.myths.block.LabyrinthMarkerBlock;
import com.seaweed.grandatlas.myths.block.MythsBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.blending.Blender;

import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

/**
 * The Labyrinth's CHUNK GENERATOR: the code that fills every new chunk of the dimension.
 *
 * <p>Vanilla's generators (noise, flat, debug) are each a {@link ChunkGenerator} subclass with
 * a CODEC: the recipe that reads its settings from the dimension JSON. This one is registered
 * as {@code grandatlas_myths:labyrinth} (world/MythsWorldgen), so {@code dimension/labyrinth.json}
 * can say {@code "type": "grandatlas_myths:labyrinth"}.
 *
 * <h2>The vertical slice (dimension type: min_y 0, height 32)</h2>
 * <pre>
 *  y 9-15  (air: nothing reaches it, except a lab's tall hall)
 *  y 7-8   ceiling          Labyrinth Stone            (Exit Hatches set into it)
 *  y 6     trim             copper (walls)             hanging lanterns
 *  y 5     wall             tuff bricks
 *  y 4     frieze           chiseled tuff bricks
 *  y 2-3   wall             tuff bricks
 *  y 0-1   floor            Labyrinth Stone            (arrows at junctions)
 * </pre>
 *
 * <h2>One writer, two uses</h2>
 * {@link #writeChunk} describes every block of a chunk through a {@link LabyrinthLab.Sink}. A
 * fresh chunk ({@link #fillFromNoise}) writes into the chunk directly; a chunk whose region has
 * shifted (LabyrinthShift) is re-written in the live world, block by block, with the same code.
 * So the shifted maze is exactly what a fresh one would be.
 */
public final class LabyrinthChunkGenerator extends ChunkGenerator {

    /** Reads {@code "biome_source"} from the dimension JSON; the maze itself has no settings. */
    public static final MapCodec<LabyrinthChunkGenerator> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            BiomeSource.CODEC.fieldOf("biome_source").forGetter(LabyrinthChunkGenerator::getBiomeSource)
    ).apply(i, LabyrinthChunkGenerator::new));

    public static final int FLOOR_TOP = 1, CORRIDOR_TOP = 6, CEILING_TOP = 8, FRIEZE_Y = 4, WRITE_TOP = 15;

    /** Lanterns turn warm (orange, not blue) this many steps from a lab: follow them to it. */
    public static final int WARM_TRAIL = 9;

    private final AtomicReference<LabyrinthMaze> maze = new AtomicReference<>();

    public LabyrinthChunkGenerator(BiomeSource biomeSource) {
        super(biomeSource);
    }

    @Override
    protected MapCodec<? extends ChunkGenerator> codec() {
        return CODEC;
    }

    /**
     * The maze for this world. The salt comes from {@link RandomState}, which every world-gen call
     * carries: a named random factory gives numbers derived from the world seed. Each region's
     * epoch comes from LabyrinthShift.
     */
    public LabyrinthMaze maze(RandomState randomState) {
        long salt = randomState.getOrCreateRandomFactory(
                ResourceLocation.fromNamespaceAndPath(GrandAtlasMyths.MOD_ID, "maze")).at(0, 0, 0).nextLong();
        LabyrinthMaze current = maze.get();
        if (current == null || current.salt() != salt) {
            current = new LabyrinthMaze(salt, LabyrinthShift::epochFor);
            maze.set(current);
        }
        return current;
    }

    // ── Writing a chunk ─────────────────────────────────────────────────────────────────────

    /**
     * Every block of chunk {@code cp}, through {@code sink} (world coordinates; the sink may get
     * blocks just outside the chunk and should ignore them). With {@code full}, open air is
     * written too, as a rebuild needs; a fresh chunk starts as air and skips it.
     */
    public static void writeChunk(LabyrinthMaze m, ChunkPos cp, LabyrinthLab.Sink sink, boolean full) {
        BlockState air = Blocks.AIR.defaultBlockState();
        BlockState stone = MythsBlocks.LABYRINTH_STONE.get().defaultBlockState();
        BlockState bricks = MythsBlocks.LABYRINTH_BRICKS.get().defaultBlockState();
        BlockState frieze = MythsBlocks.LABYRINTH_FRIEZE.get().defaultBlockState();
        BlockState trim = MythsBlocks.LABYRINTH_TRIM.get().defaultBlockState();

        // 1. The structure: floor, walls, ceiling, column by column.
        for (int x = cp.getMinBlockX(); x <= cp.getMaxBlockX(); x++) {
            for (int z = cp.getMinBlockZ(); z <= cp.getMaxBlockZ(); z++) {
                boolean open = m.isOpen(x, z);
                for (int y = 0; y <= (full ? WRITE_TOP : CEILING_TOP); y++) {
                    BlockState state;
                    if (y <= FLOOR_TOP || (y > CORRIDOR_TOP && y <= CEILING_TOP)) state = stone;
                    else if (y > CEILING_TOP || open) state = air;
                    else if (y == FRIEZE_Y) state = frieze;
                    else if (y == CORRIDOR_TOP) state = trim;
                    else state = bricks;
                    if (state == air && !full) continue;
                    sink.set(x, y, z, state);
                }
            }
        }

        // 2. Cell details: lanterns, cobwebs, arrows, exits.
        int c0x = Math.floorDiv(cp.getMinBlockX(), LabyrinthMaze.CELL), c1x = Math.floorDiv(cp.getMaxBlockX(), LabyrinthMaze.CELL);
        int c0z = Math.floorDiv(cp.getMinBlockZ(), LabyrinthMaze.CELL), c1z = Math.floorDiv(cp.getMaxBlockZ(), LabyrinthMaze.CELL);
        for (int cx = c0x; cx <= c1x; cx++) for (int cz = c0z; cz <= c1z; cz++) decorateCell(m, cx, cz, sink);

        // 3. The labs of every region this chunk touches (their builder places only what it's given).
        for (int[] r : regionsTouching(cp)) {
            int[] c = m.labCentre(r[0], r[1]);
            int reach = LabyrinthLab.HALF + 1;
            if (c[0] + reach < cp.getMinBlockX() || c[0] - reach > cp.getMaxBlockX()
                    || c[1] + reach < cp.getMinBlockZ() || c[1] - reach > cp.getMaxBlockZ()) continue;
            LabyrinthLab.build(m, r[0], r[1], sink);
        }
    }

    /** Lanterns, cobwebs, a junction arrow, or an exit, for one cell. */
    private static void decorateCell(LabyrinthMaze m, int cx, int cz, LabyrinthLab.Sink sink) {
        if (m.isLabCell(cx, cz)) return; // the lab is built as a whole
        int bx = cx * LabyrinthMaze.CELL + 3, bz = cz * LabyrinthMaze.CELL + 3; // the cell's centre column

        int exitSide = m.exitSide(cx, cz);
        if (exitSide >= 0) {
            placeExit(bx, bz, exitSide, sink);
            return;
        }

        // The way out: an arrow in the floor at every junction, pointing toward the nearest exit.
        int arrow = m.exitArrow(cx, cz);
        if (arrow >= 0) {
            sink.set(bx, FLOOR_TOP, bz, MythsBlocks.LABYRINTH_MARKER.get().defaultBlockState()
                    .setValue(LabyrinthMarkerBlock.FACING, direction(arrow)));
        }

        long h = m.cellHash(cx, cz, 0x11);
        int toLab = m.labDistance(cx, cz);
        if (toLab <= WARM_TRAIL) {
            // The way to the lab: every cell near it has a warm lantern.
            sink.set(bx, CORRIDOR_TOP, bz, Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true));
        } else if (Math.floorMod(h, 6) == 0) {
            sink.set(bx, CORRIDOR_TOP, bz, Blocks.SOUL_LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true));
        }
        if (Math.floorMod(h >>> 8, 9) == 0) {
            int ox = (h & 0x10000) == 0 ? -1 : 1, oz = (h & 0x20000) == 0 ? -1 : 1;
            sink.set(bx + ox, CORRIDOR_TOP, bz + oz, Blocks.COBWEB.defaultBlockState());
        }
    }

    /**
     * An exit: a ladder up the closed side of the cell, an Exit Hatch in the ceiling above it,
     * and a lantern at the foot so it stands out down the corridor.
     */
    private static void placeExit(int bx, int bz, int side, LabyrinthLab.Sink sink) {
        Direction wall = direction(side);
        int lx = bx + wall.getStepX(), lz = bz + wall.getStepZ(); // against the wall's inner face
        BlockState ladder = Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, wall.getOpposite());
        for (int y = FLOOR_TOP + 1; y <= CORRIDOR_TOP; y++) sink.set(lx, y, lz, ladder);
        sink.set(lx, CORRIDOR_TOP + 1, lz, MythsBlocks.EXIT_HATCH.get().defaultBlockState());
        sink.set(bx - wall.getStepX(), FLOOR_TOP, bz - wall.getStepZ(), MythsBlocks.LABYRINTH_MARKER.get().defaultBlockState()
                .setValue(LabyrinthMarkerBlock.FACING, wall)); // the last arrow points at the ladder
    }

    private static Direction direction(int mazeDir) {
        return switch (mazeDir) {
            case LabyrinthMaze.NORTH -> Direction.NORTH;
            case LabyrinthMaze.SOUTH -> Direction.SOUTH;
            case LabyrinthMaze.WEST -> Direction.WEST;
            default -> Direction.EAST;
        };
    }

    /** The regions (rx, rz) whose cells overlap chunk {@code cp}: one to four. */
    public static List<int[]> regionsTouching(ChunkPos cp) {
        int r0x = LabyrinthMaze.regionOf(Math.floorDiv(cp.getMinBlockX(), LabyrinthMaze.CELL));
        int r1x = LabyrinthMaze.regionOf(Math.floorDiv(cp.getMaxBlockX(), LabyrinthMaze.CELL));
        int r0z = LabyrinthMaze.regionOf(Math.floorDiv(cp.getMinBlockZ(), LabyrinthMaze.CELL));
        int r1z = LabyrinthMaze.regionOf(Math.floorDiv(cp.getMaxBlockZ(), LabyrinthMaze.CELL));
        List<int[]> out = new java.util.ArrayList<>(4);
        for (int rx = r0x; rx <= r1x; rx++) for (int rz = r0z; rz <= r1z; rz++) out.add(new int[]{rx, rz});
        return out;
    }

    // ── The generator contract ──────────────────────────────────────────────────────────────

    @Override
    public CompletableFuture<ChunkAccess> fillFromNoise(Blender blender, RandomState randomState,
                                                        StructureManager structures, ChunkAccess chunk) {
        ChunkPos cp = chunk.getPos();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        writeChunk(maze(randomState), cp, (x, y, z, state) -> {
            if (x < cp.getMinBlockX() || x > cp.getMaxBlockX() || z < cp.getMinBlockZ() || z > cp.getMaxBlockZ()) return;
            chunk.setBlockState(pos.set(x, y, z), state, false);
        }, false);
        // Heightmaps tell the game where the "surface" is (mob spawning picks heights below it).
        Heightmap.primeHeightmaps(chunk, EnumSet.of(Heightmap.Types.OCEAN_FLOOR_WG, Heightmap.Types.WORLD_SURFACE_WG));
        return CompletableFuture.completedFuture(chunk);
    }

    /**
     * Runs once per chunk with a real (if limited) world view, after its blocks exist: places
     * the lab parts (chest, lecterns, automata, the Minotaur) that fall inside this chunk.
     */
    @Override
    public void spawnOriginalMobs(WorldGenRegion region) {
        ChunkPos cp = region.getCenter();
        LabyrinthMaze m = maze(region.getLevel().getChunkSource().randomState());
        for (int[] r : regionsTouching(cp)) LabyrinthLab.placeContents(region, m, r[0], r[1], cp, region.getRandom());
    }

    @Override
    public int getBaseHeight(int x, int z, Heightmap.Types type, LevelHeightAccessor level, RandomState randomState) {
        return CEILING_TOP + 1;
    }

    @Override
    public NoiseColumn getBaseColumn(int x, int z, LevelHeightAccessor level, RandomState randomState) {
        boolean open = maze(randomState).isOpen(x, z);
        BlockState[] states = new BlockState[level.getHeight()];
        for (int y = 0; y < states.length; y++) {
            boolean solid = y <= FLOOR_TOP || (y > CORRIDOR_TOP && y <= CEILING_TOP) || (!open && y <= CORRIDOR_TOP);
            states[y] = solid ? MythsBlocks.LABYRINTH_STONE.get().defaultBlockState() : Blocks.AIR.defaultBlockState();
        }
        return new NoiseColumn(level.getMinBuildHeight(), states);
    }

    @Override
    public void addDebugScreenInfo(List<String> lines, RandomState randomState, BlockPos pos) {
        int cx = Math.floorDiv(pos.getX(), LabyrinthMaze.CELL), cz = Math.floorDiv(pos.getZ(), LabyrinthMaze.CELL);
        int rx = LabyrinthMaze.regionOf(cx), rz = LabyrinthMaze.regionOf(cz);
        lines.add("Labyrinth cell " + cx + ", " + cz + " | region " + rx + ", " + rz + " (epoch " + LabyrinthShift.epochFor(rx, rz) + ")");
    }

    @Override
    public void applyCarvers(WorldGenRegion region, long seed, RandomState randomState, BiomeManager biomes,
                             StructureManager structures, ChunkAccess chunk, GenerationStep.Carving step) {
        // No caves: the maze is the only shape.
    }

    @Override
    public void buildSurface(WorldGenRegion region, StructureManager structures, RandomState randomState, ChunkAccess chunk) {
        // Nothing: fillFromNoise already placed the final blocks.
    }

    @Override
    public int getMinY() {
        return 0;
    }

    @Override
    public int getGenDepth() {
        return 32;
    }

    @Override
    public int getSeaLevel() {
        return -63;
    }
}
