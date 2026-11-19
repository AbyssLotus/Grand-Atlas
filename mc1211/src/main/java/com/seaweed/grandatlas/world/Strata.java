package com.seaweed.grandatlas.world;

import com.mojang.serialization.MapCodec;
import com.seaweed.grandatlas.core.noise.GradientNoise;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.world.level.levelgen.SurfaceRules;
import javax.annotation.Nullable;

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
public enum Strata implements SurfaceRules.RuleSource {
    INSTANCE;

    public static final MapCodec<Strata> MAP_CODEC = MapCodec.unit(INSTANCE);
    public static final KeyDispatchDataCodec<Strata> CODEC = KeyDispatchDataCodec.of(MAP_CODEC);

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
    private static final BlockState STONE = Blocks.STONE.defaultBlockState();
    /** Vanilla's ore veins (copper 0..50, iron -60..-8) and its deepslate fade (0..8) lie between these. */
    private static final int VEINS_TOP = 52, VEINS_BOTTOM = -62;

    @Override
    public KeyDispatchDataCodec<? extends SurfaceRules.RuleSource> codec() {
        return CODEC;
    }

    @Override
    public SurfaceRules.SurfaceRule apply(SurfaceRules.Context context) {
        long seed = context.randomState.getOrCreateRandomFactory(ResourceLocation.fromNamespaceAndPath("grandatlas", "strata")).fromHashOf("layers").nextLong();
        return new Evaluator(seed, context, com.seaweed.grandatlas.world.CoreHandle.coreFrom(context.randomState));
    }

    // ---- the walls of the deep and abyss passages, by mood (cave rework R3: the user's Rule of Cool,
    // colour changes with depth, and each region's mood shows in its rock). Vanilla blocks only.
    private static final BlockState CALCITE = Blocks.CALCITE.defaultBlockState();
    private static final BlockState DRIPSTONE = Blocks.DRIPSTONE_BLOCK.defaultBlockState();
    private static final BlockState SMOOTH_BASALT = Blocks.SMOOTH_BASALT.defaultBlockState();
    private static final BlockState GRANITE = Blocks.GRANITE.defaultBlockState();
    private static final BlockState PACKED_MUD = Blocks.PACKED_MUD.defaultBlockState();
    private static final BlockState SCULK = Blocks.SCULK.defaultBlockState();
    private static final BlockState BONE = Blocks.BONE_BLOCK.defaultBlockState();
    private static final BlockState OBSIDIAN = Blocks.OBSIDIAN.defaultBlockState();
    /** Violet cracks that give no light (crying obsidian lit the awe abyss up: the user, 2026-11-18). */
    private static final BlockState AMETHYST = Blocks.AMETHYST_BLOCK.defaultBlockState();
    private static final BlockState MAGMA = Blocks.MAGMA_BLOCK.defaultBlockState();
    private static final BlockState GILDED = Blocks.GILDED_BLACKSTONE.defaultBlockState();
    private static final BlockState SOUL_SOIL = Blocks.SOUL_SOIL.defaultBlockState();
    private static final BlockState DIORITE = Blocks.DIORITE.defaultBlockState();
    private static final BlockState CLAY_BLOCK = Blocks.CLAY.defaultBlockState();
    private static final BlockState ROOTED_DIRT = Blocks.ROOTED_DIRT.defaultBlockState();
    private static final BlockState DIRT = Blocks.DIRT.defaultBlockState();
    private static final BlockState COARSE_DIRT = Blocks.COARSE_DIRT.defaultBlockState();
    private static final BlockState[] CLAY_BANDS = {Blocks.TERRACOTTA.defaultBlockState(), Blocks.BROWN_TERRACOTTA.defaultBlockState(),
            Blocks.WHITE_TERRACOTTA.defaultBlockState(), Blocks.LIGHT_GRAY_TERRACOTTA.defaultBlockState(), Blocks.BROWN_TERRACOTTA.defaultBlockState(),
            Blocks.ORANGE_TERRACOTTA.defaultBlockState()};
    /** How close to a passage's surface the wall rock changes (the carve's distance-like value). */
    private static final double SKIN = 0.9;

    /** Per chunk: the warp and each layer's thickness are cached for the column being walked. */
    private static final class Evaluator implements SurfaceRules.SurfaceRule {
        private final long seed;
        private int cx = Integer.MIN_VALUE, cz = Integer.MIN_VALUE;
        private double warp;
        private final double[] tuff = new double[TUFF.length];
        private final double[] basalt = new double[BASALT.length];
        private double baseWarp;

        private final SurfaceRules.Context context;
        private final @Nullable com.seaweed.grandatlas.core.AtlasCore core;
        private int sx = Integer.MIN_VALUE, sz = Integer.MIN_VALUE;
        private com.seaweed.grandatlas.core.ColumnSample skinColumn;

        Evaluator(long seed, SurfaceRules.Context context, @Nullable com.seaweed.grandatlas.core.AtlasCore core) {
            this.seed = seed;
            this.context = context;
            this.core = core;
        }

        /** The wall rock at a passage's surface in the deep and abyss, by mood, or null for the plain rock. */
        private @Nullable BlockState skin(int x, int y, int z) {
            if (x != sx || z != sz) {
                sx = x;
                sz = z;
                skinColumn = core.column(x, z);
            }
            double d = core.passages().carveWalls(skinColumn, x, y, z); // (noodles keep plain rock: cheaper, and they're thin)
            if (d < 0 || d > SKIN) return null;
            com.seaweed.grandatlas.core.Moods.Mood mood = core.moods().at(x, y, z);
            if (mood == null) return null;
            boolean abyss = y < com.seaweed.grandatlas.core.AtlasCore.ABYSS_TOP;
            boolean floor = core.passages().carveWalls(skinColumn, x, y + 1, z) < 0;
            // Patches a few blocks across, smooth (no speckle); a rarer accent on top.
            double n = GradientNoise.noise3(GradientNoise.fork(seed, 50), x / 9.0, y / 6.0, z / 9.0);
            // Accents follow cracks: thin sheets where a noise crosses zero cut the walls in lines that
            // wander and branch (the user: crying obsidian "too random, give it structure like cracks").
            boolean crack = crack(x, y, z);
            // Bone in small clusters, not single blocks.
            boolean bone = GradientNoise.noise3(GradientNoise.fork(seed, 54), x / 3.0, y / 3.0, z / 3.0) > 0.62;
            if (!abyss) {
                return switch (mood) {
                    case AWE -> n > 0.25 ? DRIPSTONE : n > -0.45 ? CALCITE : TUFF_STATE;   // pale and grand
                    case FEAR -> n > 0.1 ? SMOOTH_BASALT : n > -0.35 ? BASALT_STATE : null; // dark, close
                    case COZY -> n > 0.15 ? DRIPSTONE : n > -0.3 ? GRANITE : PACKED_MUD;   // warm browns
                    case CLEVER -> band(x, y, z, CALCITE, TUFF_STATE, DEEPSLATE);         // the layers show how it formed
                    case HORROR -> bone ? BONE : n > 0.05 ? SCULK : null;                  // sculk on deepslate, bone
                };
            }
            return switch (mood) {
                case AWE -> crack ? AMETHYST : n > 0.2 ? OBSIDIAN : BLACKSTONE;            // violet cracks, dark till lit
                case FEAR -> n > 0 ? BASALT_STATE : BLACKSTONE;
                case COZY -> !floor && crack ? MAGMA : n > 0 ? TUFF_STATE : SMOOTH_BASALT;  // ember seams in the walls
                case CLEVER -> crack ? GILDED : band(x, y, z, BLACKSTONE, BASALT_STATE, SMOOTH_BASALT); // gold veins
                case HORROR -> bone ? BONE : n > 0.2 ? SOUL_SOIL : SCULK;
            };
        }

        /**
         * On a crack: within a thin band either side of where one of two noises crosses zero. Each is
         * stretched differently, so the lines meet and fork; a third noise breaks them into lengths.
         */
        private boolean crack(int x, int y, int z) {
            double c1 = Math.abs(GradientNoise.noise3(GradientNoise.fork(seed, 52), x / 18.0, y / 11.0, z / 18.0));
            double c2 = Math.abs(GradientNoise.noise3(GradientNoise.fork(seed, 53), x / 9.0, y / 14.0, z / 9.0));
            double gaps = GradientNoise.noise3(GradientNoise.fork(seed, 55), x / 24.0, y / 24.0, z / 24.0);
            return gaps > -0.25 && (c1 < 0.03 || c2 < 0.022 && gaps > 0.15);
        }

        /** Level bands of rock a few blocks thick that rise and fall gently: bedding you can read. */
        private BlockState band(int x, int y, int z, BlockState p, BlockState q, BlockState r) {
            double w = y + 3 * GradientNoise.noise2(GradientNoise.fork(seed, 51), x / 40.0, z / 40.0);
            int b = Math.floorMod((int) Math.floor(w / 3), 5);
            return b < 2 ? p : b < 4 ? q : r;
        }

        // ---- the upper caves' rock (core/Passages rockProvince): chalk, clay or soil walls
        private int ux = Integer.MIN_VALUE, uz = Integer.MIN_VALUE, uProv;
        private com.seaweed.grandatlas.core.ColumnSample uCol;

        private @Nullable BlockState upperSkin(int x, int y, int z) {
            if (x != ux || z != uz) {
                ux = x;
                uz = z;
                uProv = core.passages().rockProvince(x, z);
                uCol = uProv == 0 ? null : core.column(x, z);
            }
            if (uProv == 0) return null;
            double top = uCol.height();
            if (y > top - 6) return null;                                                     // the ground's own layers stay
            if (uProv == com.seaweed.grandatlas.core.Passages.ROCK_SOIL && top - y > 34) return null; // soil only near the surface
            double d = core.passages().carveWalls(uCol, x, y, z);
            if (d < 0 || d > SKIN) return null;
            boolean floor = core.passages().carveWalls(uCol, x, y + 1, z) < 0;
            double n = GradientNoise.noise3(GradientNoise.fork(seed, 60), x / 8.0, y / 5.0, z / 8.0);
            return switch (uProv) {
                case com.seaweed.grandatlas.core.Passages.ROCK_CHALK -> n > 0.3 ? DIORITE : CALCITE;
                case com.seaweed.grandatlas.core.Passages.ROCK_CLAY -> floor ? (n > 0.2 ? CLAY_BLOCK : PACKED_MUD) : clayBand(x, y, z);
                default -> n > 0.25 ? ROOTED_DIRT : n > -0.35 ? DIRT : COARSE_DIRT;
            };
        }

        /** Banded clay: terracotta in layers a block or two thick that wander a little, as clay beds do. */
        private BlockState clayBand(int x, int y, int z) {
            double w = y + 2.5 * GradientNoise.noise2(GradientNoise.fork(seed, 61), x / 30.0, z / 30.0);
            int b = Math.floorMod((int) Math.floor(w / 1.7), 6);
            return CLAY_BANDS[b];
        }

        @Override
        public @Nullable BlockState tryApply(int x, int y, int z) {
            if (y < BEDROCK_TOP) return null;
            if (core != null && y < com.seaweed.grandatlas.core.Moods.TOP) {
                BlockState skin = skin(x, y, z);
                if (skin != null) return skin;
            } else if (core != null && y < context.getMinSurfaceLevel() + 40) {
                BlockState skin = upperSkin(x, y, z);
                if (skin != null) return skin;
            }
            if (y > TOP) {
                // Plain rock well under the ground, outside the levels where vanilla's rule can give
                // anything but the default: its ore veins run from -60 to 50 and its deepslate fades
                // out from 0 to 8, and everything else it does is at the surface. Answering here saves
                // running its whole sequence on every block of a mountain's inside (profiled
                // 2026-11-10: the material rules were 40% of chunk generation).
                if (y > VEINS_TOP) return y < context.getMinSurfaceLevel() ? STONE : null;
                if (y < VEINS_BOTTOM) return y < context.getMinSurfaceLevel() ? DEEPSLATE : null;
                return null;
            }
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
