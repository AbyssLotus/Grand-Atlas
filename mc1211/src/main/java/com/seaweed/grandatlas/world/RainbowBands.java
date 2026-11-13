package com.seaweed.grandatlas.world;

import com.mojang.serialization.MapCodec;
import com.seaweed.grandatlas.core.noise.GradientNoise;
import javax.annotation.Nullable;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.SurfaceRules;

/**
 * {@code grandatlas:rainbow}: the striped rock of the rainbow mountains (landform/RainbowMountains,
 * after Zhangye Danxia and Vinicunca). A fixed sequence of bands, each 1 to 6 blocks thick, of red,
 * orange, gold, cream, pale green, teal and lavender terracotta, repeating every 96 blocks. The
 * bands are level on average, but fold gently over a few hundred blocks and wander a little over a
 * few dozen, so a ridge's flank shows stripes that rise and dip along it. Smooth 2D noise of the
 * column plus y, never speckle. The surface rules give it only to the rainbow_mountains biome.
 */
public enum RainbowBands implements SurfaceRules.RuleSource {
    INSTANCE;

    public static final MapCodec<RainbowBands> MAP_CODEC = MapCodec.unit(INSTANCE);
    public static final KeyDispatchDataCodec<RainbowBands> CODEC = KeyDispatchDataCodec.of(MAP_CODEC);

    /** One repeat of the sequence, a block per entry: the bands and their thicknesses. */
    private static final BlockState[] SEQUENCE = build();

    private static BlockState[] build() {
        Object[][] bands = {
                {Blocks.RED_TERRACOTTA, 5}, {Blocks.ORANGE_TERRACOTTA, 3}, {Blocks.WHITE_TERRACOTTA, 1}, {Blocks.YELLOW_TERRACOTTA, 4},
                {Blocks.TERRACOTTA, 2}, {Blocks.LIME_TERRACOTTA, 3}, {Blocks.WHITE_TERRACOTTA, 2}, {Blocks.RED_TERRACOTTA, 2},
                {Blocks.PINK_TERRACOTTA, 3}, {Blocks.ORANGE_TERRACOTTA, 5}, {Blocks.CYAN_TERRACOTTA, 2}, {Blocks.LIGHT_GRAY_TERRACOTTA, 1},
                {Blocks.YELLOW_TERRACOTTA, 3}, {Blocks.RED_TERRACOTTA, 6}, {Blocks.PURPLE_TERRACOTTA, 2}, {Blocks.WHITE_TERRACOTTA, 1},
                {Blocks.ORANGE_TERRACOTTA, 3}, {Blocks.LIME_TERRACOTTA, 2}, {Blocks.YELLOW_TERRACOTTA, 2}, {Blocks.BROWN_TERRACOTTA, 3},
                {Blocks.RED_TERRACOTTA, 4}, {Blocks.LIGHT_BLUE_TERRACOTTA, 1}, {Blocks.CYAN_TERRACOTTA, 2}, {Blocks.WHITE_TERRACOTTA, 2},
                {Blocks.ORANGE_TERRACOTTA, 4}, {Blocks.MAGENTA_TERRACOTTA, 2}, {Blocks.YELLOW_TERRACOTTA, 3}, {Blocks.PINK_TERRACOTTA, 2},
                {Blocks.RED_TERRACOTTA, 5}, {Blocks.TERRACOTTA, 3},
        };
        int n = 0;
        for (Object[] b : bands) n += (Integer) b[1];
        BlockState[] out = new BlockState[n];
        int i = 0;
        for (Object[] b : bands) {
            for (int k = 0; k < (Integer) b[1]; k++) out[i++] = ((Block) b[0]).defaultBlockState();
        }
        return out;
    }

    @Override
    public KeyDispatchDataCodec<? extends SurfaceRules.RuleSource> codec() {
        return CODEC;
    }

    @Override
    public SurfaceRules.SurfaceRule apply(SurfaceRules.Context context) {
        long seed = context.randomState.getOrCreateRandomFactory(ResourceLocation.fromNamespaceAndPath("grandatlas", "rainbow")).fromHashOf("bands").nextLong();
        return new Evaluator(seed);
    }

    private static final class Evaluator implements SurfaceRules.SurfaceRule {
        private final long seed;
        private int cx = Integer.MIN_VALUE, cz = Integer.MIN_VALUE;
        private double warp;

        Evaluator(long seed) {
            this.seed = seed;
        }

        @Override
        public @Nullable BlockState tryApply(int x, int y, int z) {
            if (x != cx || z != cz) {
                cx = x;
                cz = z;
                warp = 14 * GradientNoise.noise2(seed, x / 380.0, z / 380.0)
                        + 3 * GradientNoise.noise2(GradientNoise.fork(seed, 1), x / 70.0, z / 70.0);
            }
            int i = Math.floorMod((int) Math.floor(y + warp), SEQUENCE.length);
            return SEQUENCE[i];
        }
    }
}
