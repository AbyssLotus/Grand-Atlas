package com.seaweed.grandatlas.world;

import com.mojang.serialization.MapCodec;
import com.seaweed.grandatlas.core.AtlasCore;
import com.seaweed.grandatlas.core.ColumnSample;
import com.seaweed.grandatlas.core.Waters;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.material.Fluids;
import javax.annotation.Nullable;

/**
 * {@code grandatlas:deep_water} (M7d): the underground's water, from {@link Waters#state}: cavern
 * lakes, underground seas and flooded cave systems. The same rules as {@link PerchedWater}, so
 * the answer is the same whichever chunk generates first:
 * <ul>
 *   <li>Water is river water, which holds still wherever it is put.</li>
 *   <li>Where a block of it would face a dry Grand Atlas cave (beside or below), it is ordinary
 *       water instead, given a fluid tick, and only at the top of the face: it pours out as a
 *       waterfall (a flooded tunnel spilling into a cavern) and no wall of water stands.</li>
 *   <li>Where the core has rock beside or above the water but the world has air (vanilla's caves,
 *       which the core can't see), the hole is filled with the rock around it. Each chunk plugs
 *       only its own blocks, but looks at the water one block over its edges to do so.</li>
 * </ul>
 * Placed once per chunk at the fluid_springs step, after geodes, dungeons and ores have opened
 * their holes, so it plugs those too; from every overworld biome (tools/gen_data.py).
 */
public record DeepWater() implements AtlasFeature {
    public static final MapCodec<DeepWater> CODEC = MapCodec.unit(DeepWater::new);
    private static final int[][] SIDES = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

    @Override
    public MapCodec<DeepWater> codec() {
        return CODEC;
    }

    @Override
    public boolean place(WorldGenLevel level, ChunkGenerator chunkGenerator, RandomSource random, BlockPos origin) {
        AtlasCore core = core(level.getLevel());
        if (core == null) return false;
        int x0 = origin.getX() & ~15, z0 = origin.getZ() & ~15;
        // The highest water anywhere in the chunk and one block round it.
        double top = Double.NEGATIVE_INFINITY;
        for (int dz = -1; dz <= 16; dz++) {
            for (int dx = -1; dx <= 16; dx++) {
                int x = x0 + dx, z = z0 + dz;
                double lake = core.megaCaverns().water(x, z), table = core.waters().table(x, z);
                double piece = core.setPieces().waterTop(x, z);
                if (!Double.isNaN(lake)) top = Math.max(top, lake);
                if (!Double.isNaN(piece)) top = Math.max(top, piece);
                if (!Double.isNaN(table)) top = Math.max(top, table);
                double cave = core.passages().waterTop(x, z); // karst cave rivers, pools and sumps
                if (!Double.isNaN(cave)) top = Math.max(top, cave);
            }
        }
        if (top == Double.NEGATIVE_INFINITY) return swallet(level, core, x0, z0);
        // (In a karst system, up through its passages' air too, for the loose water in it.)
        int over = core.karst().near(x0 + 8, z0 + 8) != null ? 16 : 0;
        int ylo = AtlasCore.MIN_Y + 6, yhi = (int) Math.floor(top) + 1 + over, ny = yhi - ylo + 1;
        // The core's verdict for every block of the 18x18 area, up to a block over the water.
        byte[] st = new byte[18 * 18 * ny];
        for (int dz = 0; dz < 18; dz++) {
            for (int dx = 0; dx < 18; dx++) {
                int x = x0 + dx - 1, z = z0 + dz - 1;
                ColumnSample c = core.column(x, z);
                int base = (dz * 18 + dx) * ny;
                for (int y = ylo; y <= yhi; y++) st[base + y - ylo] = (byte) core.waters().state(c, x, y, z);
            }
        }
        BlockState water = Blocks.WATER.defaultBlockState();
        BlockState river = RiverWater.BLOCK.get().defaultBlockState().setValue(RiverWater.FLOW, RiverFlow.of(0, 0));
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        boolean placed = false;
        for (int dz = 1; dz <= 16; dz++) {
            for (int dx = 1; dx <= 16; dx++) {
                int x = x0 + dx - 1, z = z0 + dz - 1, base = (dz * 18 + dx) * ny;
                boolean pouring = false;
                for (int y = yhi; y >= ylo; y--) {
                    int s = st[base + y - ylo];
                    pos.set(x, y, z);
                    if (s == Waters.HELD || s == Waters.RISING) {
                        // Held water (a hanging lake) and a rising fall: river water that never pours,
                        // round whatever crystal the dressing grew in it (waterlogged, so left alone).
                        BlockState here = level.getBlockState(pos);
                        pouring = false;
                        if (drown(level, pos, here)) continue;
                        if (!here.isAir() && !(here.getBlock() instanceof net.minecraft.world.level.block.LiquidBlock) && here.isSolid()) continue;
                        level.setBlock(pos, RiverWater.BLOCK.get().defaultBlockState()
                                .setValue(RiverWater.FLOW, s == Waters.RISING ? RiverFlow.UP : RiverFlow.HELD), 2);
                        placed = true;
                    } else if (s == Waters.WATER) {
                        BlockState here = level.getBlockState(pos);
                        if (!here.isAir() && !(here.getBlock() instanceof net.minecraft.world.level.block.LiquidBlock)
                                && here.getFluidState().is(net.minecraft.tags.FluidTags.WATER)) {
                            pouring = false; // something waterlogged the dressing grew there (a crystal in a pool)
                            continue;
                        }
                        if (drown(level, pos, here)) {
                            pouring = false;
                            continue;
                        }
                        if (!here.isAir() && here.getFluidState().isEmpty() && here.isSolid()) {
                            pouring = false; // the world has rock where the core is open: leave it
                            continue;
                        }
                        // (Anything else dry here is a plant, carpet, vine or strand of silk that the
                        // cave dressing or a vanilla feature grew before the water came: it's replaced
                        // by the water. Left alone, each kept a block of air inside the lake.)
                        if (open(st, ny, dx, dz, y - ylo)) {
                            // Only the top of an open face is a source; under it the face is left
                            // empty and the source pours down it.
                            if (pouring) {
                                level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
                            } else {
                                level.setBlock(pos, water, 2);
                                level.scheduleTick(pos.immutable(), Fluids.WATER, 0);
                            }
                            pouring = true;
                        } else {
                            level.setBlock(pos, river, 2);
                            pouring = false;
                        }
                        placed = true;
                    } else {
                        pouring = false;
                        if (s == Waters.AIR) {
                            // Loose water standing in a karst passage's open air (a puddle the cave
                            // dressing left on a ledge that isn't there, a spring opening into the void):
                            // it hung in the chamber and poured as a curtain (the karst check, 2026-11-17).
                            BlockState here = level.getBlockState(pos);
                            if (here.is(Blocks.WATER) && core.passages().karstLevelAt(x, y, z) != -1) {
                                level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
                                placed = true;
                            }
                            continue;
                        }
                        if (s == Waters.ROCK && touchesWater(st, ny, dx, dz, y - ylo)) {
                            BlockState here = level.getBlockState(pos);
                            if (here.isAir()) level.setBlock(pos, plug(level, pos), 2);
                        }
                    }
                }
            }
        }
        if (placed) beds(level, st, ny, ylo, x0, z0);
        // Last: the swallet's water, after the loose water in the passages was cleared (its shaft is
        // passage air, and the clearing took the fall away).
        return swallet(level, core, x0, z0) | placed;
    }

    /**
     * A sinking stream's swallet (core/Karst): the river's water that stands over or beside the shaft
     * at the end of its channel becomes ordinary water, so the stream pours down the shaft into the
     * cave river below as a waterfall (river water never pours: it stood as a wall over the hole).
     */
    private static boolean swallet(WorldGenLevel level, AtlasCore core, int x0, int z0) {
        com.seaweed.grandatlas.core.Karst.KarstSystem sys = core.karst().near(x0 + 8, z0 + 8);
        if (sys == null || sys.swallet == null) return false;
        double sx = sys.swallet[0], sz = sys.swallet[1], top = sys.swallet[2];
        if (sx < x0 - 11 || sx > x0 + 27 || sz < z0 - 11 || sz > z0 + 27) return false;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(), at = new BlockPos.MutableBlockPos();
        BlockState water = Blocks.WATER.defaultBlockState();
        boolean placed = false;
        for (int dz = 0; dz < 16; dz++) {
            for (int dx = 0; dx < 16; dx++) {
                int x = x0 + dx, z = z0 + dz;
                if (Math.hypot(x + 0.5 - sx, z + 0.5 - sz) > 10) continue;
                for (int y = (int) Math.floor(top) + 1; y >= (int) Math.floor(top) - 6; y--) {
                    BlockState here = level.getBlockState(pos.set(x, y, z));
                    if (!here.getFluidState().is(net.minecraft.tags.FluidTags.WATER) || here.is(Blocks.WATER)) continue;
                    boolean open = level.getBlockState(at.set(x, y - 1, z)).isAir();
                    for (int[] s : SIDES) open |= level.getBlockState(at.set(x + s[0], y, z + s[1])).isAir();
                    if (!open) continue;
                    level.setBlock(pos, water, 2);
                    level.scheduleTick(pos.immutable(), Fluids.WATER, 0);
                    placed = true;
                }
            }
        }
        return placed;
    }

    private static final long BED_SEED = 0xBED5EEDL;

    private static double unit(long h) {
        return (com.seaweed.grandatlas.core.noise.GradientNoise.mix(h) >>> 11) * 0x1.0p-53;
    }

    /**
     * Life on the lake and sea floors (the quality review, 2026-11-09: every underground lake bed
     * was bare rock, and the Hollow's lake is three quarters of its floor): meadows of seagrass,
     * stands of kelp in the deeper water, colonies of sea pickles glowing on the bed, and the odd
     * drowned boulder. Each gathers by its own smooth field, so a lake has weedy bays, lit
     * shallows and clean stretches. Only in the core's own still water (not hanging lakes or
     * rising falls), on a solid bed, and only in this chunk.
     */
    private static void beds(WorldGenLevel level, byte[] st, int ny, int ylo, int x0, int z0) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int dz = 1; dz <= 16; dz++) {
            for (int dx = 1; dx <= 16; dx++) {
                int x = x0 + dx - 1, z = z0 + dz - 1, base = (dz * 18 + dx) * ny;
                double grass = com.seaweed.grandatlas.core.noise.GradientNoise.noise2(BED_SEED, x / 19.0, z / 19.0);
                double kelp = com.seaweed.grandatlas.core.noise.GradientNoise.noise2(BED_SEED ^ 1, x / 27.0, z / 27.0);
                double pickles = com.seaweed.grandatlas.core.noise.GradientNoise.noise2(BED_SEED ^ 2, x / 13.0, z / 13.0);
                for (int k = 1; k < ny; k++) {
                    if (st[base + k] != Waters.WATER || st[base + k - 1] == Waters.WATER) continue; // the lowest water of a run
                    int y = ylo + k;
                    if (!still(level.getBlockState(pos.set(x, y, z)))) continue;
                    BlockState bed = level.getBlockState(pos.set(x, y - 1, z));
                    if (!bed.isSolid() || !bed.getFluidState().isEmpty()) continue;
                    if (bed.is(Blocks.GRASS_BLOCK) || bed.is(Blocks.PODZOL) || bed.is(Blocks.MYCELIUM)) {
                        // The surface rules grassed a cave floor that the water then covered: a bed of
                        // gravel, or dirt.
                        bed = unit(com.seaweed.grandatlas.core.noise.GradientNoise.hash(BED_SEED ^ 7, x, y, z)) < 0.6 ? Blocks.GRAVEL.defaultBlockState() : Blocks.DIRT.defaultBlockState();
                        level.setBlock(pos, bed, 2);
                    }
                    int depth = 1;
                    while (k + depth < ny && st[base + k + depth] == Waters.WATER && still(level.getBlockState(pos.set(x, y + depth, z)))) depth++;
                    long h = com.seaweed.grandatlas.core.noise.GradientNoise.hash(BED_SEED, x, y, z);
                    double roll = unit(h);
                    if (kelp > 0.3 && depth >= 4 && roll < 0.15 + 0.7 * (kelp - 0.3)) {
                        int len = Math.min(depth - 1, 2 + (int) (unit(h ^ 1) * unit(h ^ 2) * 22));
                        for (int j = 0; j < len; j++) {
                            level.setBlock(pos.set(x, y + j, z), j == len - 1
                                    ? Blocks.KELP.defaultBlockState().setValue(net.minecraft.world.level.block.KelpBlock.AGE, 25)
                                    : Blocks.KELP_PLANT.defaultBlockState(), 2);
                        }
                    } else if (pickles > 0.38 && roll < 0.12 + 0.6 * (pickles - 0.38)) {
                        level.setBlock(pos.set(x, y, z), Blocks.SEA_PICKLE.defaultBlockState()
                                .setValue(net.minecraft.world.level.block.SeaPickleBlock.PICKLES, 1 + (int) (unit(h ^ 3) * unit(h ^ 4) * 4))
                                .setValue(net.minecraft.world.level.block.SeaPickleBlock.WATERLOGGED, true), 2);
                    } else if (grass > 0.2 && roll < 0.2 + 0.9 * (grass - 0.2)) {
                        if (depth >= 2 && unit(h ^ 5) < 0.3) {
                            level.setBlock(pos.set(x, y, z), Blocks.TALL_SEAGRASS.defaultBlockState()
                                    .setValue(net.minecraft.world.level.block.TallSeagrassBlock.HALF, net.minecraft.world.level.block.state.properties.DoubleBlockHalf.LOWER), 2);
                            level.setBlock(pos.set(x, y + 1, z), Blocks.TALL_SEAGRASS.defaultBlockState()
                                    .setValue(net.minecraft.world.level.block.TallSeagrassBlock.HALF, net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER), 2);
                        } else {
                            level.setBlock(pos.set(x, y, z), Blocks.SEAGRASS.defaultBlockState(), 2);
                        }
                    } else if (roll > 0.996 && depth >= 2) {
                        level.setBlock(pos.set(x, y, z), bed.is(net.minecraft.tags.BlockTags.BASE_STONE_OVERWORLD) ? bed : Blocks.COBBLED_DEEPSLATE.defaultBlockState(), 2);
                        if (unit(h ^ 6) < 0.5 && depth >= 3) level.setBlock(pos.set(x, y + 1, z), Blocks.MOSSY_COBBLESTONE.defaultBlockState(), 2);
                    }
                }
            }
        }
    }

    /** Plain still water the core placed: a full liquid block, river water with no current or vanilla water. */
    private static boolean still(BlockState s) {
        return s.getBlock() instanceof net.minecraft.world.level.block.LiquidBlock && s.getFluidState().is(net.minecraft.tags.FluidTags.WATER) && s.getFluidState().isSource();
    }

    /**
     * Something that can hold water (pointed dripstone, a cluster, a wall) standing dry where the
     * water goes: waterlogged, so no pocket of air is left round it. True if that settled the block.
     */
    private static boolean drown(WorldGenLevel level, BlockPos pos, BlockState here) {
        if (here.isAir() || !here.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.WATERLOGGED)) return false;
        if (!here.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.WATERLOGGED)) {
            level.setBlock(pos, here.setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.WATERLOGGED, true), 2);
        }
        return true;
    }

    /** A dry Grand Atlas cave beside or under this water. */
    private static boolean open(byte[] st, int ny, int dx, int dz, int k) {
        if (k > 0 && st[(dz * 18 + dx) * ny + k - 1] == Waters.AIR) return true;
        for (int[] s : SIDES) {
            if (st[((dz + s[1]) * 18 + dx + s[0]) * ny + k] == Waters.AIR) return true;
        }
        return false;
    }

    /** Water beside or above this block. */
    private static boolean touchesWater(byte[] st, int ny, int dx, int dz, int k) {
        if (k + 1 < ny && wet(st[(dz * 18 + dx) * ny + k + 1])) return true;
        if (k > 0 && st[(dz * 18 + dx) * ny + k - 1] == Waters.HELD) return true; // the bed over a hanging lake
        for (int[] s : SIDES) {
            if (wet(st[((dz + s[1]) * 18 + dx + s[0]) * ny + k])) return true;
        }
        return false;
    }

    private static boolean wet(byte s) {
        return s == Waters.WATER || s == Waters.HELD || s == Waters.RISING;
    }

    /** Rock to fill a hole with: the nearest solid block in its column, or deepslate. */
    private static BlockState plug(WorldGenLevel level, BlockPos at) {
        for (int d : new int[] {-1, 1, -2, 2, -3, 3, -4}) {
            BlockState s = level.getBlockState(at.above(d));
            if (s.isSolid() && s.getFluidState().isEmpty()) return s;
        }
        return Blocks.DEEPSLATE.defaultBlockState();
    }

    private static @Nullable AtlasCore core(ServerLevel level) {
        return CoreHandle.coreOf(level);
    }
}
