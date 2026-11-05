package com.seaweed.grandatlas.core;

import com.seaweed.grandatlas.core.noise.GradientNoise;
import java.util.HashMap;

/**
 * M7d (docs/M7_UNDERGROUND_PLAN.md): the underground's water.
 * <ul>
 *   <li><b>Lakes and seas</b> in mega-caverns: {@link MegaCaverns#water}.</li>
 *   <li><b>Flooded cave systems</b> (here): about a third of the land sits over a water table, flat
 *       across a region of ~640 blocks (with wavy edges), somewhere between y -320 and -20 and
 *       always 45+ blocks under the ground. Every passage, bore and rift below it is full of water:
 *       sumps, flooded mazes, drowned shafts. Where a flooded tunnel opens into a cavern, or into a
 *       region with a lower table, its water pours out.</li>
 * </ul>
 * The water itself is placed by {@code world/DeepWater}.
 */
public final class Waters {
    static final double CELL = 640;
    private static final double CHANCE = 0.35;

    private final AtlasCore core;
    private final long seed;
    private final ThreadLocal<HashMap<Long, Double>> cells = ThreadLocal.withInitial(HashMap::new);

    Waters(AtlasCore core, long seed) {
        this.core = core;
        this.seed = seed;
    }

    /** The water table at (x, z): passages at or below this y are flooded. NaN where there is none. */
    public double table(int x, int z) {
        double wx = x + 160 * GradientNoise.noise2(seed, x / 500.0, z / 500.0);
        double wz = z + 160 * GradientNoise.noise2(GradientNoise.fork(seed, 1), x / 500.0, z / 500.0);
        int i = GradientNoise.floor(wx / CELL), j = GradientNoise.floor(wz / CELL);
        HashMap<Long, Double> cache = cells.get();
        long key = ((long) i << 32) ^ (j & 0xFFFFFFFFL);
        Double v = cache.get(key);
        if (v != null) return v;
        if (cache.size() > 4096) cache.clear();
        long h = GradientNoise.hash(seed, i, j);
        double level = Double.NaN;
        if (unit(h, 0) < CHANCE) {
            double ground = Double.MAX_VALUE;
            for (int k = 0; k < 5; k++) {
                double px = (i + 0.5 + (k == 1 ? 0.45 : k == 2 ? -0.45 : 0)) * CELL, pz = (j + 0.5 + (k == 3 ? 0.45 : k == 4 ? -0.45 : 0)) * CELL;
                ground = Math.min(ground, core.column((int) px, (int) pz).height());
            }
            level = Math.floor(Math.min(-320 + 300 * unit(h, 1), ground - 45));
        }
        cache.put(key, level);
        return level;
    }

    public static final int ROCK = 0, AIR = 1, WATER = 2;

    /**
     * What the core makes of a block underground: rock, open (a dry cave), or water. Only Grand
     * Atlas's own caves count as open; vanilla's are rock here, so water beside one is plugged.
     */
    public int state(ColumnSample c, int x, int y, int z) {
        SetPieces sp = core.setPieces();
        if (sp.nearColumn(x, z)) {
            SetPieces.Probe p = sp.sample(x, y, z);
            if (p.claimed) {
                if (p.open) return p.water ? WATER : AIR;
                if (p.protect) return ROCK;
                return core.passages().carve(c, x, y, z) < 0 ? AIR : ROCK; // a passage breaking in: an entrance
            }
        }
        MegaCaverns mc = core.megaCaverns();
        if (mc.carve(x, y, z) < 0) {
            double lake = mc.water(x, z);
            return !Double.isNaN(lake) && y <= lake ? WATER : AIR;
        }
        if (core.passages().carve(c, x, y, z) >= 0) return ROCK;
        double t = table(x, z);
        return !Double.isNaN(t) && y <= t ? WATER : AIR;
    }

    private static double unit(long hash, int k) {
        return (GradientNoise.mix(hash + k * 0x9E3779B97F4A7C15L) >>> 11) * 0x1.0p-53;
    }
}
