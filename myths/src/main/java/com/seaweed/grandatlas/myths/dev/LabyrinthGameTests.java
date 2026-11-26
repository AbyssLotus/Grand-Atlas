package com.seaweed.grandatlas.myths.dev;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import com.seaweed.grandatlas.myths.GrandAtlasMyths;
import com.seaweed.grandatlas.myths.block.MythsBlocks;
import com.seaweed.grandatlas.myths.item.MythsItems;
import com.seaweed.grandatlas.myths.labyrinth.LabyrinthChunkGenerator;
import com.seaweed.grandatlas.myths.labyrinth.LabyrinthLab;
import com.seaweed.grandatlas.myths.labyrinth.LabyrinthMaze;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.UpgradeData;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayDeque;
import java.util.List;

/**
 * Dev-only checks of the Labyrinth, run by {@code gradlew :myths:runGameTestServer}: a headless
 * server makes a fresh world, runs these, prints the results and stops. They only run when
 * that task enables the {@code grandatlas_myths} game-test namespace, never in a normal game.
 *
 * <p>The game-test server builds its world from a fixed flat preset and drops every datapack
 * dimension, so the Labyrinth isn't a loaded world here. Instead the tests parse
 * data/grandatlas_myths/dimension/labyrinth.json exactly as the game would (same codec, same
 * registries) and run the maze and its writer on chunks in memory.
 */
@GameTestHolder(GrandAtlasMyths.MOD_ID)
@PrefixGameTestTemplate(false)
public final class LabyrinthGameTests {

    private static final long SEED = 20261125L;
    private static final int[][] DIRS = {{0, -1}, {0, 1}, {-1, 0}, {1, 0}}; // NORTH, SOUTH, WEST, EAST

    // ── Helpers ─────────────────────────────────────────────────────────────────────────────

    private static LabyrinthChunkGenerator generator(GameTestHelper helper) {
        var server = helper.getLevel().getServer();
        ResourceLocation file = ResourceLocation.fromNamespaceAndPath(GrandAtlasMyths.MOD_ID, "dimension/labyrinth.json");
        try (var reader = server.getResourceManager().getResourceOrThrow(file).openAsReader()) {
            JsonElement json = JsonParser.parseReader(reader);
            LevelStem stem = LevelStem.CODEC.parse(RegistryOps.create(JsonOps.INSTANCE, server.registryAccess()), json)
                    .getOrThrow(error -> new IllegalStateException("dimension JSON doesn't parse: " + error));
            if (!(stem.generator() instanceof LabyrinthChunkGenerator gen))
                throw new IllegalStateException("dimension JSON doesn't use the maze generator");
            return gen;
        } catch (java.io.IOException e) {
            throw new IllegalStateException("can't read " + file, e);
        }
    }

    /** This world's maze salt, with the region epochs given. */
    private static LabyrinthMaze maze(GameTestHelper helper, LabyrinthMaze.Epochs epochs) {
        RandomState rs = RandomState.create(helper.getLevel().getServer().registryAccess().asGetterLookup(), NoiseGeneratorSettings.OVERWORLD, SEED);
        return new LabyrinthMaze(generator(helper).maze(rs).salt(), epochs);
    }

    private static ProtoChunk emptyChunk(GameTestHelper helper, ChunkPos pos) {
        var biomes = helper.getLevel().getServer().registryAccess().registryOrThrow(Registries.BIOME);
        return new ProtoChunk(pos, UpgradeData.EMPTY, LevelHeightAccessor.create(0, 32), biomes, null);
    }

    /** Write a chunk of maze {@code m} into {@code chunk}, as the generator (full=false) or a rebuild (full=true) would. */
    private static void write(LabyrinthMaze m, ProtoChunk chunk, boolean full) {
        ChunkPos cp = chunk.getPos();
        LabyrinthChunkGenerator.writeChunk(m, cp, (x, y, z, s) -> {
            if (x >= cp.getMinBlockX() && x <= cp.getMaxBlockX() && z >= cp.getMinBlockZ() && z <= cp.getMaxBlockZ())
                chunk.setBlockState(new BlockPos(x, y, z), s, false);
        }, full);
    }

    private static int reachable(LabyrinthMaze m, int regions) {
        int n = LabyrinthMaze.REGION * regions, off = -LabyrinthMaze.REGION * (regions / 2);
        boolean[][] seen = new boolean[n][n];
        ArrayDeque<int[]> queue = new ArrayDeque<>();
        queue.add(new int[]{n / 2, n / 2});
        seen[n / 2][n / 2] = true;
        int count = 0;
        while (!queue.isEmpty()) {
            int[] c = queue.poll();
            count++;
            for (int d = 0; d < 4; d++) {
                int nx = c[0] + DIRS[d][0], nz = c[1] + DIRS[d][1];
                if (nx < 0 || nz < 0 || nx >= n || nz >= n || seen[nx][nz]) continue;
                boolean through = m.passage(c[0] + off, c[1] + off, d)
                        || (m.isLabCell(c[0] + off, c[1] + off) && m.isLabCell(nx + off, nz + off));
                if (!through) continue;
                seen[nx][nz] = true;
                queue.add(new int[]{nx, nz});
            }
        }
        return count;
    }

    // ── The maze ────────────────────────────────────────────────────────────────────────────

    /** Everything is reachable, also when neighbouring regions are at different epochs (the shifting promise). */
    @GameTest(template = "empty")
    public static void mazeConnectedAcrossEpochs(GameTestHelper helper) {
        int n = LabyrinthMaze.REGION * 3;
        int same = reachable(maze(helper, (rx, rz) -> 0), 3);
        int mixed = reachable(maze(helper, (rx, rz) -> Math.floorMod(rx * 7 + rz * 3, 5)), 3);
        GrandAtlasMyths.LOG.info("[Myths check] maze: {} / {} cells reachable at one epoch, {} / {} with mixed epochs", same, n * n, mixed, n * n);
        helper.assertTrue(same == n * n, "one epoch: only " + same + " of " + n * n + " reachable");
        helper.assertTrue(mixed == n * n, "mixed epochs: only " + mixed + " of " + n * n + " reachable");

        // A shift really redraws the inside, and never moves the doors.
        LabyrinthMaze a = maze(helper, (rx, rz) -> 0), b = maze(helper, (rx, rz) -> 1);
        int inside = 0, doors = 0;
        for (int cx = 0; cx < LabyrinthMaze.REGION; cx++) for (int cz = 0; cz < LabyrinthMaze.REGION; cz++) {
            boolean border = cx == LabyrinthMaze.REGION - 1;
            if (a.eastOpen(cx, cz) != b.eastOpen(cx, cz)) { if (border) doors++; else inside++; }
        }
        int[] labA = a.labCentre(0, 0), labB = b.labCentre(0, 0);
        GrandAtlasMyths.LOG.info("[Myths check] shift: {} inner passages changed, {} doors moved; lab {},{} -> {},{}",
                inside, doors, labA[0], labA[1], labB[0], labB[1]);
        helper.assertTrue(inside > 50, "a shift changed only " + inside + " passages");
        helper.assertTrue(doors == 0, doors + " doors moved in a shift");
        helper.succeed();
    }

    /** From every junction, following the arrows (and the only way on between junctions) reaches an exit. */
    @GameTest(template = "empty")
    public static void arrowsLeadToExits(GameTestHelper helper) {
        LabyrinthMaze m = maze(helper, (rx, rz) -> 0);
        int junctions = 0, worst = 0;
        for (int cx = 0; cx < LabyrinthMaze.REGION; cx++) for (int cz = 0; cz < LabyrinthMaze.REGION; cz++) {
            if (m.exitArrow(cx, cz) < 0) continue;
            junctions++;
            int x = cx, z = cz, from = -1, steps = 0;
            while (m.exitSide(x, z) < 0) {
                helper.assertTrue(++steps < 500, "arrows from " + cx + "," + cz + " go round in circles");
                if (m.isLabCell(x, z)) { // in a hall: cross it to the marked doorway and leave by it
                    int[] out = m.labExit(LabyrinthMaze.regionOf(x), LabyrinthMaze.regionOf(z));
                    x = out[0] + DIRS[out[2]][0];
                    z = out[1] + DIRS[out[2]][1];
                    from = out[2] ^ 1;
                    continue;
                }
                int dir = m.exitArrow(x, z);
                if (dir < 0) { // a plain corridor: the one way on that isn't back
                    for (int d = 0; d < 4; d++) if (d != from && m.passage(x, z, d) && !leavesRegion(x, z, d)) dir = d;
                }
                helper.assertTrue(dir >= 0, "a dead end at " + x + "," + z + " following arrows from " + cx + "," + cz);
                x += DIRS[dir][0];
                z += DIRS[dir][1];
                from = dir ^ 1; // the opposite direction (N<->S, W<->E)
            }
            worst = Math.max(worst, steps);
        }
        // How far, on average, is a cell from its nearest exit? (Breadth-first from the exits.)
        int n = LabyrinthMaze.REGION;
        int[][] dist = new int[n][n];
        for (int[] row : dist) java.util.Arrays.fill(row, -1);
        ArrayDeque<int[]> queue = new ArrayDeque<>();
        for (int[] e : m.exits(0, 0)) { dist[e[0]][e[1]] = 0; queue.add(new int[]{e[0], e[1]}); }
        long total = 0;
        int far = 0;
        while (!queue.isEmpty()) {
            int[] c = queue.poll();
            total += dist[c[0]][c[1]];
            far = Math.max(far, dist[c[0]][c[1]]);
            for (int d = 0; d < 4; d++) {
                int nx = c[0] + DIRS[d][0], nz = c[1] + DIRS[d][1];
                if (nx < 0 || nz < 0 || nx >= n || nz >= n || dist[nx][nz] >= 0) continue;
                boolean through = m.passage(c[0], c[1], d) || (m.isLabCell(c[0], c[1]) && m.isLabCell(nx, nz));
                if (!through) continue;
                dist[nx][nz] = dist[c[0]][c[1]] + 1;
                queue.add(new int[]{nx, nz});
            }
        }
        GrandAtlasMyths.LOG.info("[Myths check] arrows: {} junctions in region 0,0 all lead to an exit (longest arrow walk {} cells); "
                + "{} exits; a cell is on average {} cells ({} blocks) from an exit, at worst {}",
                junctions, worst, m.exits(0, 0).length, String.format("%.1f", total / (double) (n * n)),
                String.format("%.0f", total / (double) (n * n) * LabyrinthMaze.CELL), far);
        helper.assertTrue(m.exits(0, 0).length == LabyrinthMaze.EXITS, "only " + m.exits(0, 0).length + " exits");
        helper.succeed();
    }

    private static boolean leavesRegion(int cx, int cz, int d) {
        int nx = cx + DIRS[d][0], nz = cz + DIRS[d][1];
        return LabyrinthMaze.regionOf(nx) != LabyrinthMaze.regionOf(cx) || LabyrinthMaze.regionOf(nz) != LabyrinthMaze.regionOf(cz);
    }

    // ── The blocks ──────────────────────────────────────────────────────────────────────────

    /** Generated chunks match the layout: walls, floor, arrows, exits, and the lab hall. */
    @GameTest(template = "empty")
    public static void blocksMatchLayout(GameTestHelper helper) {
        LabyrinthMaze m = maze(helper, (rx, rz) -> 0);
        int rx = 3, rz = -2;
        int[] lab = m.labCentre(rx, rz);
        ChunkPos labChunk = new ChunkPos(new BlockPos(lab[0], 0, lab[1]));
        int mismatches = 0, columns = 0, arrows = 0;
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
            ProtoChunk chunk = emptyChunk(helper, new ChunkPos(labChunk.x + dx, labChunk.z + dz));
            write(m, chunk, false);
            ChunkPos cp = chunk.getPos();
            for (int x = cp.getMinBlockX(); x <= cp.getMaxBlockX(); x++) for (int z = cp.getMinBlockZ(); z <= cp.getMaxBlockZ(); z++) {
                if (Math.max(Math.abs(x - lab[0]), Math.abs(z - lab[1])) <= LabyrinthLab.HALF + 1) continue; // the hall: checked below
                columns++;
                boolean wall = chunk.getBlockState(new BlockPos(x, 3, z)).is(MythsBlocks.LABYRINTH_BRICKS.get());
                if (m.isOpen(x, z) == wall) mismatches++;
                var floor = chunk.getBlockState(new BlockPos(x, 1, z));
                if (floor.is(MythsBlocks.LABYRINTH_MARKER.get())) arrows++;
                else if (!floor.is(MythsBlocks.LABYRINTH_STONE.get())) mismatches++;
                if (!chunk.getBlockState(new BlockPos(x, 8, z)).is(MythsBlocks.LABYRINTH_STONE.get())) mismatches++;
            }
        }
        GrandAtlasMyths.LOG.info("[Myths check] blocks: {} mismatches in {} columns, {} arrows", mismatches, columns, arrows);
        helper.assertTrue(mismatches == 0, mismatches + " columns don't match the layout");

        // The hall: the mosaic, the wings and their chain, the vault, open space where the corridor ceiling was.
        ProtoChunk centre = emptyChunk(helper, labChunk);
        write(m, centre, false);
        int x0 = lab[0], z0 = lab[1];
        Object[][] expected = {
                {new BlockPos(x0, 1, z0), Blocks.WAXED_CHISELED_COPPER},
                {new BlockPos(x0, 13, z0), Blocks.CHAIN},
                {new BlockPos(x0 + 1, 12, z0), Blocks.WHITE_WOOL},
                {new BlockPos(x0, 14, z0), MythsBlocks.LABYRINTH_STONE.get()},
                {new BlockPos(x0, 8, z0 + 2), Blocks.AIR}};
        for (Object[] e : expected) {
            BlockPos pos = (BlockPos) e[0];
            if (!new ChunkPos(pos).equals(labChunk)) continue;
            var block = (net.minecraft.world.level.block.Block) e[1];
            helper.assertTrue(centre.getBlockState(pos).is(block), "expected " + block + " at " + pos + ", found " + centre.getBlockState(pos));
        }
        GrandAtlasMyths.LOG.info("[Myths check] lab at {} {}: theme {}", x0, z0, LabyrinthLab.Theme.values()[m.labTheme(rx, rz)]);

        // The exits: a ladder against a solid wall, the hatch above it.
        for (int[] exit : m.exits(rx, rz)) {
            Direction wall = switch (exit[2]) {
                case 0 -> Direction.NORTH;
                case 1 -> Direction.SOUTH;
                case 2 -> Direction.WEST;
                default -> Direction.EAST;
            };
            BlockPos ladderTop = new BlockPos(exit[0] * LabyrinthMaze.CELL + 3, 6, exit[1] * LabyrinthMaze.CELL + 3).relative(wall);
            ProtoChunk chunk = emptyChunk(helper, new ChunkPos(ladderTop));
            write(m, chunk, false);
            helper.assertTrue(chunk.getBlockState(ladderTop).is(Blocks.LADDER), "no ladder at " + ladderTop);
            helper.assertTrue(chunk.getBlockState(ladderTop).getValue(LadderBlock.FACING) == wall.getOpposite(), "ladder at " + ladderTop + " faces the wrong way");
            helper.assertTrue(chunk.getBlockState(ladderTop.above()).is(MythsBlocks.EXIT_HATCH.get()), "no hatch above " + ladderTop);
            BlockPos behind = ladderTop.relative(wall);
            helper.assertTrue(!m.isOpen(behind.getX(), behind.getZ()), "the ladder at " + ladderTop + " leans on nothing");
        }
        helper.succeed();
    }

    /**
     * A rebuild after a shift gives exactly what a fresh chunk would: no wall, lantern, ladder,
     * hatch, hall or furniture of the old layout is left behind.
     */
    @GameTest(template = "empty")
    public static void rebuildMatchesFresh(GameTestHelper helper) {
        LabyrinthMaze before = maze(helper, (rx, rz) -> 0), after = maze(helper, (rx, rz) -> 1);
        int[] lab = before.labCentre(0, 0); // a chunk that held a lab before the shift
        ChunkPos cp = new ChunkPos(new BlockPos(lab[0], 0, lab[1]));
        ProtoChunk rebuilt = emptyChunk(helper, cp), fresh = emptyChunk(helper, cp);
        write(before, rebuilt, false);
        write(after, rebuilt, true);
        write(after, fresh, false);
        int diffs = 0;
        for (int x = cp.getMinBlockX(); x <= cp.getMaxBlockX(); x++) for (int z = cp.getMinBlockZ(); z <= cp.getMaxBlockZ(); z++)
            for (int y = 0; y <= LabyrinthChunkGenerator.WRITE_TOP; y++) {
                BlockPos pos = new BlockPos(x, y, z);
                if (rebuilt.getBlockState(pos) != fresh.getBlockState(pos)) diffs++;
            }
        GrandAtlasMyths.LOG.info("[Myths check] rebuild of chunk {}: {} blocks differ from a fresh chunk", cp, diffs);
        helper.assertTrue(diffs == 0, diffs + " blocks differ after a rebuild");
        helper.succeed();
    }

    // ── Loot ────────────────────────────────────────────────────────────────────────────────

    /** The loot table parses and gives what the design promises. */
    @GameTest(template = "empty")
    public static void lootTables(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        LootTable lab = level.getServer().reloadableRegistries().getLootTable(ResourceKey.create(Registries.LOOT_TABLE,
                ResourceLocation.fromNamespaceAndPath(GrandAtlasMyths.MOD_ID, "chests/daedalus_lab")));
        List<ItemStack> items = lab.getRandomItems(new LootParams.Builder(level)
                .withParameter(LootContextParams.ORIGIN, Vec3.ZERO).create(LootContextParamSets.CHEST));
        boolean thread = items.stream().anyMatch(s -> s.is(MythsItems.ARIADNE_THREAD.get()));
        ItemStack book = items.stream().filter(s -> s.is(Items.WRITTEN_BOOK)).findFirst().orElse(ItemStack.EMPTY);
        var content = book.get(DataComponents.WRITTEN_BOOK_CONTENT);
        GrandAtlasMyths.LOG.info("[Myths check] lab chest: {}", items);
        helper.assertTrue(thread, "lab chest gave no thread");
        helper.assertTrue(content != null && content.pages().size() == 5, "lab chest gave no 5-page book: " + content);
        helper.succeed();
    }
}
