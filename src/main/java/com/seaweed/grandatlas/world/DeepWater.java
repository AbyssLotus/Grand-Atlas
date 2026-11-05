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
import net.minecraft.world.level.levelgen.densityfunction.SamplerContext;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.material.Fluids;
import org.jspecify.annotations.Nullable;

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
public record DeepWater() implements Feature {
    public static final MapCodec<DeepWater> CODEC = MapCodec.unit(DeepWater::new);
    private static final int[][] SIDES = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

    private static volatile @Nullable ServerLevel lastLevel;
    private static volatile @Nullable AtlasCore lastCore;

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
                if (!Double.isNaN(lake)) top = Math.max(top, lake);
                if (!Double.isNaN(table)) top = Math.max(top, table);
            }
        }
        if (top == Double.NEGATIVE_INFINITY) return false;
        int ylo = AtlasCore.MIN_Y + 6, yhi = (int) Math.floor(top) + 1, ny = yhi - ylo + 1;
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
                    if (s == Waters.WATER) {
                        BlockState here = level.getBlockState(pos);
                        if (!here.isAir() && here.getFluidState().isEmpty()) {
                            pouring = false; // the world has rock where the core is open: leave it
                            continue;
                        }
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
                        if (s == Waters.ROCK && touchesWater(st, ny, dx, dz, y - ylo)) {
                            BlockState here = level.getBlockState(pos);
                            if (here.isAir()) level.setBlock(pos, plug(level, pos), 2);
                        }
                    }
                }
            }
        }
        return placed;
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
        if (k + 1 < ny && st[(dz * 18 + dx) * ny + k + 1] == Waters.WATER) return true;
        for (int[] s : SIDES) {
            if (st[((dz + s[1]) * 18 + dx + s[0]) * ny + k] == Waters.WATER) return true;
        }
        return false;
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
        if (level == lastLevel) return lastCore;
        AtlasCore core = CoreHandle.coreFrom(level.getChunkSource().randomState().createClimateSampler(SamplerContext.EMPTY_UNCACHED));
        lastCore = core;
        lastLevel = level;
        return core;
    }
}
