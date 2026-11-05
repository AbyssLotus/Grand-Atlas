package com.seaweed.grandatlas.world;

import com.mojang.serialization.MapCodec;
import com.seaweed.grandatlas.core.noise.GradientNoise;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.material.MaterialRuleContext;
import net.minecraft.world.level.levelgen.material.rule.MaterialRule;
import net.minecraft.world.level.levelgen.material.rule.RuleEvaluator;
import org.jspecify.annotations.Nullable;

/**
 * {@code grandatlas:strata}: the abyss's rock (docs/M7_UNDERGROUND_PLAN.md). Below about y -250,
 * the deepslate carries layers of tuff, and from about -445 down to the bedrock is a base of
 * blackstone with seams of basalt. The layers are level on average but rise and fall by up to
 * ~45 blocks over a few hundred, and each one thickens and pinches out on its own, so a cave
 * wall shows lenses of rock, not stripes ruled across the world. Everything is smooth 2D noise
 * of the column plus y: no speckle.
 *
 * <p>Runs ahead of vanilla's overworld rule, so it stops short of the bedrock floor (vanilla's
 * bedrock rule must still win there). It also answers for all the plain deepslate from y -160
 * down: there vanilla's rule (bedrock, copper and iron veins, surface, underground) can only
 * ever give deepslate, and running its whole sequence on every block of 350 deep levels was a
 * third of chunk generation. Above -160 (ocean floors reach -112, with their surface layers
 * under them) it returns null and vanilla decides.
 */
public enum Strata implements MaterialRule {
    INSTANCE;

    public static final MapCodec<Strata> CODEC = MapCodec.unit(INSTANCE);

    /** Nothing above this: ground and its surface layers end above, vanilla's ore veins too. */
    private static final int TOP = -160;
    /** Vanilla's bedrock rule covers the bottom five blocks; stay above them. */
    private static final int BEDROCK_TOP = -507;
    private static final int BASE = -445;
    /** Tuff layers: centre (in warped y) and greatest half thickness. Spacing deliberately uneven. */
    private static final int[][] TUFF = {{-262, 4}, {-296, 7}, {-333, 3}, {-361, 9}, {-398, 5}, {-421, 3}};
    /** Basalt seams in the blackstone base. */
    private static final int[][] BASALT = {{-462, 4}, {-487, 3}};

    private static final BlockState TUFF_STATE = Blocks.TUFF.defaultBlockState();
    private static final BlockState BLACKSTONE = Blocks.BLACKSTONE.defaultBlockState();
    private static final BlockState BASALT_STATE = Blocks.BASALT.defaultBlockState();
    private static final BlockState DEEPSLATE = Blocks.DEEPSLATE.defaultBlockState();

    @Override
    public MapCodec<Strata> codec() {
        return CODEC;
    }

    @Override
    public RuleEvaluator compile(MaterialRuleContext context) {
        long seed = context.getOrCreateRandomFactory(Identifier.fromNamespaceAndPath("grandatlas", "strata")).fromHashOf("layers").nextLong();
        return new Evaluator(seed);
    }

    /** Per chunk: the warp and each layer's thickness are cached for the column being walked. */
    private static final class Evaluator implements RuleEvaluator {
        private final long seed;
        private int cx = Integer.MIN_VALUE, cz = Integer.MIN_VALUE;
        private double warp;
        private final double[] tuff = new double[TUFF.length];
        private final double[] basalt = new double[BASALT.length];
        private double baseWarp;

        Evaluator(long seed) {
            this.seed = seed;
        }

        @Override
        public @Nullable BlockState tryApply(int x, int y, int z) {
            if (y > TOP || y < BEDROCK_TOP) return null;
            if (x != cx || z != cz) column(x, z);
            double w = y + warp;
            if (w < BASE + baseWarp) {
                for (int i = 0; i < BASALT.length; i++) {
                    if (Math.abs(w - BASALT[i][0]) < basalt[i]) return BASALT_STATE;
                }
                return BLACKSTONE;
            }
            for (int i = 0; i < TUFF.length; i++) {
                if (Math.abs(w - TUFF[i][0]) < tuff[i]) return TUFF_STATE;
            }
            return DEEPSLATE;
        }

        private void column(int x, int z) {
            cx = x;
            cz = z;
            // Broad folds (a few hundred blocks) with a little finer wander on top.
            warp = 34 * GradientNoise.noise2(seed, x / 420.0, z / 420.0)
                    + 9 * GradientNoise.noise2(GradientNoise.fork(seed, 1), x / 110.0, z / 110.0);
            baseWarp = 10 * GradientNoise.noise2(GradientNoise.fork(seed, 2), x / 160.0, z / 160.0);
            for (int i = 0; i < TUFF.length; i++) tuff[i] = thickness(TUFF[i][1], 10 + i, x, z);
            for (int i = 0; i < BASALT.length; i++) basalt[i] = thickness(BASALT[i][1], 30 + i, x, z);
        }

        /** A layer's half thickness here: full in places, pinched to nothing in others. */
        private double thickness(int half, int salt, int x, int z) {
            double n = GradientNoise.noise2(GradientNoise.fork(seed, salt), x / 260.0, z / 260.0);
            double t = Math.min(1, Math.max(0, 0.55 + 0.9 * n));
            return half * t;
        }
    }
}
