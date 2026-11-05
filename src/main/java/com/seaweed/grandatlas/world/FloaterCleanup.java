package com.seaweed.grandatlas.world;

import com.mojang.serialization.MapCodec;
import com.seaweed.grandatlas.core.AtlasCore;
import com.seaweed.grandatlas.core.ColumnSample;
import java.util.ArrayDeque;
import java.util.BitSet;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.densityfunction.SamplerContext;
import net.minecraft.world.level.levelgen.feature.Feature;
import org.jspecify.annotations.Nullable;

/**
 * {@code grandatlas:floater_cleanup}: in crag country (roughness over 1, the megarange's crag
 * style) the 3D undercuts can cut a bit of the surface loose, leaving rock floating in the air.
 * This removes it: within the chunk, everything solid that isn't joined to the ground below or to
 * the chunk's sides (which may hold it from next door) is cleared.
 *
 * <p>Runs at local_modifications, before trees: leaves and logs aren't there yet to confuse it.
 */
public record FloaterCleanup() implements Feature {
    public static final MapCodec<FloaterCleanup> CODEC = MapCodec.unit(FloaterCleanup::new);
    private static final int[][] STEPS = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};

    private static volatile @Nullable ServerLevel lastLevel;
    private static volatile @Nullable AtlasCore lastCore;

    @Override
    public MapCodec<FloaterCleanup> codec() {
        return CODEC;
    }

    @Override
    public boolean place(WorldGenLevel level, ChunkGenerator chunkGenerator, RandomSource random, BlockPos origin) {
        AtlasCore core = core(level.getLevel());
        if (core == null) return false;
        int x0 = origin.getX() & ~15, z0 = origin.getZ() & ~15;
        int lo = Integer.MAX_VALUE, hi = Integer.MIN_VALUE;
        boolean crag = false;
        for (int dz = 0; dz < 16; dz++) {
            for (int dx = 0; dx < 16; dx++) {
                ColumnSample c = core.column(x0 + dx, z0 + dz);
                crag |= c.roughness() > 1;
                lo = Math.min(lo, c.surfaceY());
                hi = Math.max(hi, c.surfaceY());
            }
        }
        if (!crag) return false;
        lo -= 72;
        hi += 12;
        int h = hi - lo + 1;
        BitSet solid = new BitSet(16 * 16 * h), joined = new BitSet(16 * 16 * h);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int y = lo; y <= hi; y++) {
            for (int dz = 0; dz < 16; dz++) {
                for (int dx = 0; dx < 16; dx++) {
                    BlockState s = level.getBlockState(pos.set(x0 + dx, y, z0 + dz));
                    if (!s.isAir() && s.getFluidState().isEmpty()) solid.set(index(dx, y - lo, dz));
                }
            }
        }
        ArrayDeque<Integer> queue = new ArrayDeque<>();
        for (int y = 0; y < h; y++) {
            for (int dz = 0; dz < 16; dz++) {
                for (int dx = 0; dx < 16; dx++) {
                    if (y != 0 && dx != 0 && dz != 0 && dx != 15 && dz != 15) continue; // the bottom and the sides
                    int k = index(dx, y, dz);
                    if (solid.get(k) && !joined.get(k)) {
                        joined.set(k);
                        queue.add(k);
                    }
                }
            }
        }
        while (!queue.isEmpty()) {
            int k = queue.poll();
            int dx = k & 15, dz = (k >> 4) & 15, y = k >> 8;
            for (int[] o : STEPS) {
                int nx = dx + o[0], ny = y + o[1], nz = dz + o[2];
                if (nx < 0 || nz < 0 || ny < 0 || nx > 15 || nz > 15 || ny >= h) continue;
                int n = index(nx, ny, nz);
                if (solid.get(n) && !joined.get(n)) {
                    joined.set(n);
                    queue.add(n);
                }
            }
        }
        boolean cleared = false;
        for (int k = solid.nextSetBit(0); k >= 0; k = solid.nextSetBit(k + 1)) {
            if (joined.get(k)) continue;
            level.setBlock(pos.set(x0 + (k & 15), lo + (k >> 8), z0 + ((k >> 4) & 15)), Blocks.AIR.defaultBlockState(), 2);
            cleared = true;
        }
        return cleared;
    }

    private static int index(int dx, int y, int dz) {
        return (y << 8) | (dz << 4) | dx;
    }

    private static @Nullable AtlasCore core(ServerLevel level) {
        if (level == lastLevel) return lastCore;
        AtlasCore core = CoreHandle.coreFrom(level.getChunkSource().randomState().createClimateSampler(SamplerContext.EMPTY_UNCACHED));
        lastCore = core;
        lastLevel = level;
        return core;
    }
}
