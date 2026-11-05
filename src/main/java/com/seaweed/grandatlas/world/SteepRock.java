package com.seaweed.grandatlas.world;

import com.mojang.serialization.MapCodec;
import com.seaweed.grandatlas.core.AtlasCore;
import com.seaweed.grandatlas.core.ColumnSample;
import com.seaweed.grandatlas.core.noise.GradientNoise;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.densityfunction.SamplerContext;
import net.minecraft.world.level.levelgen.feature.Feature;
import org.jspecify.annotations.Nullable;

/**
 * {@code grandatlas:steep_rock}: bare rock on steep ground. The core's ground is a smooth
 * heightmap, so on a steep slope every column steps two or three blocks and the surface rules
 * put grass on each step's top and dirt down its riser: a regular diagonal grid of grass, dirt
 * and stone stripes (the user: "this weird criss-crossy pattern shows up everywhere"). Past
 * about 55 degrees soil doesn't hold, so there the top blocks are stone with andesite patches,
 * and a band either side of that, ragged with smooth noise, is coarse dirt and gravel.
 *
 * <p>Only grass and dirt are replaced (sand, terracotta, snow, mud and moss keep their own
 * looks), from the core's columns, so it is the same whichever chunk generates first. Runs at
 * local_modifications, before trees: none grow on the rock.
 */
public record SteepRock() implements Feature {
    public static final MapCodec<SteepRock> CODEC = MapCodec.unit(SteepRock::new);
    private static final long SEED = 0x57EE9L;

    private static volatile @Nullable ServerLevel lastLevel;
    private static volatile @Nullable AtlasCore lastCore;

    @Override
    public MapCodec<SteepRock> codec() {
        return CODEC;
    }

    @Override
    public boolean place(WorldGenLevel level, ChunkGenerator chunkGenerator, RandomSource random, BlockPos origin) {
        AtlasCore core = core(level.getLevel());
        if (core == null) return false;
        int x0 = origin.getX() & ~15, z0 = origin.getZ() & ~15;
        boolean placed = false;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int dz = 0; dz < 16; dz++) {
            for (int dx = 0; dx < 16; dx++) {
                int x = x0 + dx, z = z0 + dz;
                ColumnSample c = core.column(x, z);
                if (!Double.isNaN(c.water()) && c.water() > c.height()) continue;
                double slope = Math.max(Math.abs(core.column(x + 1, z).height() - core.column(x - 1, z).height()),
                        Math.abs(core.column(x, z + 1).height() - core.column(x, z - 1).height())) / 2;
                double edge = 0.45 * GradientNoise.noise2(SEED, x / 14.0, z / 14.0);
                boolean rock = slope > 1.45 + edge;
                if (!rock && !(slope > 0.95 + edge && GradientNoise.noise2(SEED ^ 2, x / 6.0, z / 6.0) > 0.05)) continue;
                int top = Integer.MIN_VALUE;
                for (int y = c.surfaceY() + 3; y >= c.surfaceY() - 4; y--) {
                    if (soil(level.getBlockState(pos.set(x, y, z)))) {
                        top = y;
                        break;
                    }
                }
                if (top == Integer.MIN_VALUE) continue;
                double patch = GradientNoise.noise2(SEED ^ 1, x / 9.0, z / 9.0);
                Block b = rock ? (patch > 0.25 ? Blocks.ANDESITE : Blocks.STONE)
                        : patch > 0.1 ? Blocks.GRAVEL : Blocks.COARSE_DIRT;
                for (int y = top; y > top - 5; y--) {
                    if (!soil(level.getBlockState(pos.set(x, y, z)))) break;
                    // Gravel over a hole would fall: stone there instead.
                    Block put = b == Blocks.GRAVEL && level.getBlockState(pos.set(x, y - 1, z)).isAir() ? Blocks.STONE : b;
                    level.setBlock(pos.set(x, y, z), put.defaultBlockState(), 2);
                    if (!rock) break; // loose ground is only the top block; the dirt under it stays
                }
                BlockState above = level.getBlockState(pos.set(x, top + 1, z));
                if (!above.isAir() && above.canBeReplaced() && above.getFluidState().isEmpty()) {
                    level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
                }
                placed = true;
            }
        }
        return placed;
    }

    private static boolean soil(BlockState s) {
        return s.is(Blocks.GRASS_BLOCK) || s.is(Blocks.DIRT) || s.is(Blocks.COARSE_DIRT) || s.is(Blocks.PODZOL)
                || s.is(Blocks.ROOTED_DIRT) || s.is(Blocks.MYCELIUM);
    }

    private static @Nullable AtlasCore core(ServerLevel level) {
        if (level == lastLevel) return lastCore;
        AtlasCore core = CoreHandle.coreFrom(level.getChunkSource().randomState().createClimateSampler(SamplerContext.EMPTY_UNCACHED));
        lastCore = core;
        lastLevel = level;
        return core;
    }
}
