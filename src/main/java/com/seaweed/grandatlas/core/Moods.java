package com.seaweed.grandatlas.core;

import com.seaweed.grandatlas.core.noise.GradientNoise;

/**
 * Moods of the deep and the abyss (cave rework R2, docs/CAVE_RESEARCH.md: the user's Rule of Cool,
 * "every corner should evoke a feeling"). Each region of the underground below y -64 has one mood,
 * which picks its passages' shapes (core/Deep), its walls' rock (world/Strata) and later its
 * features. Regions are warped cells about 300 blocks across in the deep and 240 in the abyss; no
 * region shares a mood with a region beside it, so a trick doesn't repeat next door.
 */
public final class Moods {
    public enum Mood {
        /** Wonder at scale and height: tall halls, shafts, pale rock. */
        AWE,
        /** Unease: tight, low, twisting, dark rock. */
        FEAR,
        /** Shelter: low wide rooms, soft shapes, warm rock. */
        COZY,
        /** Something clever: loops, crossings, stacked levels, banded rock that shows how it formed. */
        CLEVER,
        /** Dread: narrow, dead ends, pits, sculk and bone. */
        HORROR;

        public String label() {
            return name().charAt(0) + name().substring(1).toLowerCase();
        }
    }

    private static final Mood[] ALL = Mood.values();
    static final double DEEP_CELL = 300, ABYSS_CELL = 240;
    /** The top of the deep, where moods begin. */
    public static final int TOP = -64;

    private final long seed;

    Moods(long seed) {
        this.seed = seed;
    }

    /** The mood at (x, y, z), or null above the deep. */
    public Mood at(double x, double y, double z) {
        if (y > TOP) return null;
        boolean abyss = y < AtlasCore.ABYSS_TOP;
        double cell = abyss ? ABYSS_CELL : DEEP_CELL;
        long s = abyss ? seed ^ 0xAB155L : seed;
        // Warped, so the borders wind instead of running straight.
        double wx = x + 70 * GradientNoise.noise2(s, x / 260.0, z / 260.0);
        double wz = z + 70 * GradientNoise.noise2(s ^ 0x2L, x / 260.0, z / 260.0);
        int i = GradientNoise.floor(wx / cell), j = GradientNoise.floor(wz / cell);
        // 2i + j (mod 5) differs between any two cells that touch, sides or corners; a shuffle per
        // block of 5 x 5 cells keeps it from reading as a pattern.
        int idx = Math.floorMod(2 * i + j, 5);
        long h = GradientNoise.hash(s, Math.floorDiv(i, 5), Math.floorDiv(j, 5));
        int[] perm = {0, 1, 2, 3, 4};
        for (int k = 4; k > 0; k--) {
            int r = (int) Math.floorMod(GradientNoise.mix(h + k), (long) (k + 1));
            int t = perm[k];
            perm[k] = perm[r];
            perm[r] = t;
        }
        return ALL[perm[idx]];
    }
}
