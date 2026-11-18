package com.seaweed.grandatlas.world;

import com.mojang.serialization.MapCodec;
import com.seaweed.grandatlas.core.AtlasCore;
import com.seaweed.grandatlas.core.BiomeKind;
import com.seaweed.grandatlas.core.ColumnSample;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.material.Fluids;
import org.jspecify.annotations.Nullable;

/**
 * {@code grandatlas:perched_water}: fills the rivers and lakes the core carves ({@link ColumnSample#water()}:
 * the drainage network's, and a Great Plateau's own) with {@link RiverWater river water}, which holds
 * still wherever it is put and carries the river's current ({@link ColumnSample#flowX()}). Vanilla's
 * aquifers know a single sea level, so the water goes in as a decoration, at the lakes step before
 * ores and plants. Sea-level rivers already hold the aquifer's water; it is swapped for river water
 * too, so they run as well.
 *
 * <p>Wherever a block of it would face open air (a pool's lip over the next pool down, a bank
 * lower than the water) it is ordinary water instead, given a fluid tick: it pours over the lip
 * as a waterfall, or runs down the bank as a trickle, and stops on the river water below. Each
 * block decides from the core's columns, not the world, so the answer is the same whichever
 * chunk generates first. Frozen rivers stay ordinary water, so they ice over.
 *
 * <p>Vanilla's carvers cut their tunnels after the terrain, blind to it, and a tunnel can pass
 * under a river or through its bank. Where the core has ground beside or under the water but the
 * world has air, the hole is filled with the ground around it, as vanilla's own lakes do.
 *
 * <p>Placed once per chunk (no placement modifiers), from every overworld biome via a NeoForge
 * biome modifier (tools/gen_data.py writes both). It writes water only into its own chunk; a
 * plugged bank can be just over the border.
 */
public record PerchedWater() implements Feature {
    public static final MapCodec<PerchedWater> CODEC = MapCodec.unit(PerchedWater::new);
    private static final int[][] SIDES = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

    @Override
    public MapCodec<PerchedWater> codec() {
        return CODEC;
    }

    @Override
    public boolean place(WorldGenLevel level, ChunkGenerator chunkGenerator, RandomSource random, BlockPos origin) {
        AtlasCore core = core(level.getLevel());
        if (core == null) return false;
        int x0 = origin.getX() & ~15, z0 = origin.getZ() & ~15;
        BlockState water = Blocks.WATER.defaultBlockState();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        boolean placed = false;
        for (int dz = 0; dz < 16; dz++) {
            for (int dx = 0; dx < 16; dx++) {
                int x = x0 + dx, z = z0 + dz;
                ColumnSample c = core.column(x, z);
                if (Double.isNaN(c.water())) continue;
                int top = core.waterTop(x, z);
                if (top == Integer.MIN_VALUE) continue;
                boolean skim = AtlasCore.ninths(c.water()) <= AtlasCore.SKIM; // the top block can stand over a bank level with its floor
                // A fjord (an ocean biome on the core's water) is the sea: still vanilla water, as the
                // sea's own, where kelp and seagrass grow.
                boolean frozen = c.biome() == BiomeKind.FROZEN_RIVER || c.biome().name().endsWith("OCEAN") && c.water() < AtlasCore.SEA_LEVEL;
                RiverFlow flow = RiverFlow.of(c.flowX(), c.flowZ());
                BlockState river = RiverWater.state(flow, 8);
                // The top block is drawn at the water's own height (water rework, step 2), so a river's
                // surface slopes smoothly down its course instead of stepping a block at a time.
                BlockState surface = RiverWater.state(flow, RiverWater.surfaceAt(c.water())[1]);
                boolean lip = !frozen && lip(core, x, top, z);
                boolean[] poured = new boolean[SIDES.length]; // one spout per side, at the top of the open face
                for (int y = top; y >= c.surfaceY() - 3; y--) {
                    pos.set(x, y, z);
                    BlockState here = level.getBlockState(pos);
                    boolean sea = here.is(Blocks.WATER) && here.getFluidState().isSource(); // a sea-level river's aquifer water
                    if (lip && y == top - 1 && y < c.height() && !here.isAir() && here.getFluidState().isEmpty() && !open(core, x, y, z, false)) {
                        // The lip of a one-block drop: the bed block under the top water is water too, so
                        // the water below is drawn rising to meet the water above. Left as rock, its face
                        // showed as a pale strip across the river at every drop (the user's screenshot).
                        level.setBlock(pos, river, 2);
                        continue;
                    }
                    if (y < c.height()) {
                        // Under the bed: a carver's tunnel can run under a river (carvers don't see the
                        // density, and come in from chunks around). Plug it, as vanilla's lakes do.
                        if (!here.isAir()) break;
                        level.setBlock(pos, plug(level, pos), 2);
                        continue;
                    }
                    if (!here.isAir() && !sea && !falling(here)) break; // (a waterfall from next door poured in here first)
                    if (frozen) {
                        if (!sea) level.setBlock(pos, water, 2);
                    } else {
                        level.setBlock(pos, y == top ? surface : river, 2);
                        // Where it faces open air (a lip over the pool below, a bank lower than the water) it
                        // pours as a waterfall (water rework, step 4): ordinary water, so it behaves as
                        // players expect and can be turned aside, kept on course by grooves cut where it lands
                        // (the user's idea; on open ledges it fanned out over everything).
                        for (int k = 0; k < SIDES.length; k++) {
                            int[] s = SIDES[k];
                            if (poured[k] || !openSide(core, x, y, z, s, y == top && skim)) continue;
                            poured[k] = true;
                            cascade(level, core, x + s[0], y, z + s[1], s[0], s[1], x0, z0);
                        }
                    }
                    plugBanks(core, level, x, y, z);
                    placed = true;
                }
            }
        }
        return placed;
    }

    /** Beside water at (x, y, z): where the core has ground but the world has air (a carver's hole), fill it. */
    private static void plugBanks(AtlasCore core, WorldGenLevel level, int x, int y, int z) {
        BlockPos.MutableBlockPos side = new BlockPos.MutableBlockPos();
        for (int[] s : SIDES) {
            if (y >= core.column(x + s[0], z + s[1]).height()) continue;
            side.set(x + s[0], y, z + s[1]);
            if (level.getBlockState(side).isAir()) level.setBlock(side, plug(level, side), 2);
        }
    }

    /** Ground to fill a hole with: the nearest solid block in its column, or stone. */
    private static BlockState plug(WorldGenLevel level, BlockPos at) {
        for (int d : new int[] {-1, 1, -2, 2, -3, 3, -4}) {
            BlockState s = level.getBlockState(at.above(d));
            if (s.isSolid() && s.getFluidState().isEmpty()) return s;
        }
        return Blocks.STONE.defaultBlockState();
    }

    /** True if water beside (x, z) has its top block exactly one below {@code top}: this column's top is the lip of a drop. */
    private static boolean lip(AtlasCore core, int x, int top, int z) {
        for (int[] s : SIDES) {
            ColumnSample n = core.column(x + s[0], z + s[1]);
            if (!Double.isNaN(n.water()) && n.water() > n.height() && Math.floor(n.water()) == top - 1) return true;
        }
        return false;
    }

    /**
     * After trees (top_layer_modification, {@code FallClear}): the leaves and vines grown into a
     * waterfall's way are cleared so it falls clear; the trees keep their place beside it (the user:
     * "trees spawning with their leaves inside the water fall"). Each fall is traced again through the
     * carved world: straight down through the air, and where it meets ground, on out along its groove
     * at the water's level. A spout leaves grew over is put back.
     */
    public static boolean clearFalls(WorldGenLevel level, AtlasCore core, int x0, int z0) {
        boolean cleared = false;
        for (int dz = 0; dz < 16; dz++) {
            for (int dx = 0; dx < 16; dx++) {
                int x = x0 + dx, z = z0 + dz;
                ColumnSample c = core.column(x, z);
                if (Double.isNaN(c.water())) continue;
                int top = core.waterTop(x, z);
                if (top == Integer.MIN_VALUE) continue;
                if (c.biome() == BiomeKind.FROZEN_RIVER || c.biome().name().endsWith("OCEAN") && c.water() < AtlasCore.SEA_LEVEL) continue;
                boolean skim = AtlasCore.ninths(c.water()) <= AtlasCore.SKIM;
                boolean[] poured = new boolean[SIDES.length];
                for (int y = top; y > c.surfaceY(); y--) {
                    for (int k = 0; k < SIDES.length; k++) {
                        int[] s = SIDES[k];
                        if (poured[k] || !openSide(core, x, y, z, s, y == top && skim)) continue;
                        poured[k] = true;
                        cleared |= clearPath(level, core, x + s[0], y, z + s[1], s[0], s[1], x0, z0);
                    }
                }
            }
        }
        return cleared;
    }

    private static boolean leafy(BlockState s) {
        return s.is(net.minecraft.tags.BlockTags.LEAVES) || s.is(Blocks.VINE);
    }

    private static boolean clearPath(WorldGenLevel level, AtlasCore core, int x, int y, int z, int dx, int dz, int x0, int z0) {
        BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
        boolean cleared = false;
        if (leafy(level.getBlockState(at.set(x, y, z)))) { // the spout, grown over
            level.setBlock(at, Blocks.WATER.defaultBlockState(), 2);
            level.scheduleTick(at.immutable(), Fluids.WATER, 0);
            cleared = true;
        }
        int ledges = 0;
        y--;
        for (int steps = 0; steps < 640 && y > level.getMinY(); steps++) {
            if (x < x0 - 16 || x > x0 + 31 || z < z0 - 16 || z > z0 + 31) return cleared;
            ColumnSample c = core.column(x, z);
            if (!Double.isNaN(c.water()) && c.water() > c.height() && y <= Math.floor(c.water())) return cleared; // into the pool
            BlockState s = level.getBlockState(at.set(x, y, z));
            if (leafy(s)) {
                level.setBlock(at, Blocks.AIR.defaultBlockState(), 2);
                cleared = true;
                y--;
                continue;
            }
            if (!s.getFluidState().isEmpty()) return cleared; // into water
            if (s.isAir() || s.canBeReplaced() && !s.isSolid()) {
                y--;
                continue;
            }
            // Ground: the groove's floor. On out along it, at the water's level just over the floor.
            if (++ledges > 24) return cleared;
            x += dx;
            z += dz;
            y++;
            BlockState out = level.getBlockState(at.set(x, y, z));
            if (leafy(out)) {
                level.setBlock(at, Blocks.AIR.defaultBlockState(), 2);
                cleared = true;
            } else if (!(out.isAir() || out.canBeReplaced() && !out.isSolid())) {
                return cleared; // the groove ends
            }
        }
        return cleared;
    }

    /** River water pouring down: a waterfall's column. */
    private static boolean falling(BlockState s) {
        return s.is(RiverWater.BLOCK.get()) && s.getValue(RiverWater.FLOW) == RiverFlow.DOWN;
    }

    /** True if side {@code s} of the block at (x, y, z) faces air: no ground, river water or sea there. */
    private static boolean openSide(AtlasCore core, int x, int y, int z, int[] s, boolean skim) {
        ColumnSample n = core.column(x + s[0], z + s[1]);
        boolean ground = y < n.height() || skim && n.surfaceY() == y - 1;
        boolean wet = !Double.isNaN(n.water()) && y <= Math.floor(n.water()) + 1 && n.water() > n.height();
        return !ground && !wet && y >= AtlasCore.SEA_LEVEL;
    }

    /**
     * A waterfall from (x, y, z), poured from the water beside it toward (dx, dz): a spout of ordinary
     * water there, which falls and runs as water does once the chunk is loaded. Its way down is traced
     * now (straight down through the air until water, or where the core will put water, since the pool
     * below may be filled later from its own chunk) and wherever it would land on ground, a groove one
     * block deep is cut, running out along (dx, dz) to the next drop: the water stays in it and goes
     * over the edge instead of fanning out across the ledge. It stops after 24 groove blocks, or at the
     * edge of the area a feature may write to.
     */
    private static void cascade(WorldGenLevel level, AtlasCore core, int x, int y, int z, int dx, int dz, int x0, int z0) {
        cascade(level, core, x, y, z, dx, dz, x0, z0, true);
    }

    /** {@link #cascade} for anywhere (the test command: no core, no write area). */
    public static void pour(net.minecraft.world.level.LevelAccessor level, int x, int y, int z, int dx, int dz) {
        cascade(level, null, x, y, z, dx, dz, 0, 0, false);
    }

    private static void cascade(net.minecraft.world.level.LevelAccessor level, AtlasCore core, int x, int y, int z, int dx, int dz, int x0, int z0, boolean bounded) {
        BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
        BlockState spout = level.getBlockState(at.set(x, y, z));
        if (!(spout.isAir() || spout.canBeReplaced() && !spout.isSolid() && spout.getFluidState().isEmpty())) return;
        level.setBlock(at, Blocks.WATER.defaultBlockState(), 2);
        level.scheduleTick(at.immutable(), Fluids.WATER, 0);
        int ledges = 0;
        y--;
        for (int steps = 0; steps < 640 && y > level.getMinY(); steps++) {
            if (bounded && (x < x0 - 16 || x > x0 + 31 || z < z0 - 16 || z > z0 + 31)) return;
            if (core != null) {
                ColumnSample c = core.column(x, z);
                if (!Double.isNaN(c.water()) && c.water() > c.height() && y <= Math.floor(c.water())) return; // into the pool
            }
            BlockState s = level.getBlockState(at.set(x, y, z));
            if (!s.getFluidState().isEmpty()) return; // into water
            if (s.isAir() || s.canBeReplaced() && !s.isSolid()) {
                y--; // falling
                continue;
            }
            // Ground: a groove block here (the water runs in it, a block under the ground either side),
            // then on out along the flow at the same level, falling again where the ground drops away.
            if (++ledges > 24 || s.is(net.minecraft.tags.BlockTags.LOGS) || s.getDestroySpeed(level, at) < 0) return;
            level.setBlock(at, Blocks.AIR.defaultBlockState(), 2);
            x += dx;
            z += dz;
        }
    }

    /** True if a side of the block at (x, y, z) would face air: no ground, river water or sea there. */
    private static boolean open(AtlasCore core, int x, int y, int z, boolean skim) {
        for (int[] s : SIDES) {
            ColumnSample n = core.column(x + s[0], z + s[1]);
            boolean ground = y < n.height() || skim && n.surfaceY() == y - 1; // a thin skim holds over a bank level with its floor
            // Water one block lower next door isn't an opening: the surface just runs on down to it.
            boolean wet = !Double.isNaN(n.water()) && y <= Math.floor(n.water()) + 1 && n.water() > n.height();
            if (!ground && !wet && y >= AtlasCore.SEA_LEVEL) return true;
        }
        return false;
    }

    private static @Nullable AtlasCore core(ServerLevel level) {
        return CoreHandle.coreOf(level);
    }
}
