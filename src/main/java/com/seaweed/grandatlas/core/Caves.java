package com.seaweed.grandatlas.core;

import static com.seaweed.grandatlas.core.Maths.smoothstep;

import com.seaweed.grandatlas.core.noise.Fractal;
import com.seaweed.grandatlas.core.noise.GradientNoise;

/**
 * Grand Atlas's own caves, on top of vanilla's (design §11; playtest round 2: "caves should be as
 * unique as the overworld, from small cracks to large caverns to pits that drop you 100 blocks").
 * Negative values carve.
 * <ul>
 *   <li><b>Pits</b>: shafts 3-8 blocks across dropping 60-160 blocks from a funnel at the
 *       surface, wandering a little as they go down, with a rounded bottom. On ~7% of 200-block
 *       cells where the ground is well above the sea.</li>
 *   <li><b>Fissures</b>: cracks 1-4 blocks wide along noise contours, open at the surface as
 *       crevasses and narrowing to nothing 20-90 blocks down. Only in crack country (~15% of
 *       land), measured as a true distance to the contour so the width is real blocks.</li>
 *   <li><b>Caverns</b>: chambers tens of blocks tall from low-frequency 3D noise, between near the
 *       floor and 30+ blocks under the surface, in cavern country (~20% of land). These are big
 *       enough to evaluate on the interpolated noise grid; pits and fissures are thin, so they
 *       are evaluated per block from a per-column summary that is mostly "nothing here".</li>
 *   <li><b>Wind-carved rock</b> (not a cave, but carved per block in the same pass, since the
 *       detail is finer than the interpolated noise grid): rocks that stand on sand
 *       ({@link com.seaweed.grandatlas.core.landform.Rocks}) get a sand-blasted foot that leaves
 *       necks and overhangs, grooves where soft layers weather back, tafoni holes in their walls,
 *       and arches where a tunnel line crosses thin rock.</li>
 * </ul>
 */
public final class Caves {
    private static final double PIT_SPACING = 200;
    private static final double PIT_CHANCE = 0.07;
    /** Returned where nothing carves; comfortably above any terrain density near the surface. */
    public static final double NONE = 64;

    private final AtlasCore core;
    private final long pits, crackLines, crackMask, crackDepth, crackWarp, caverns, cavernsFine, cavernMask;
    private final long scour, layers, tafoni, archLines, archMask;
    private final ThreadLocal<ColumnCaves> cache = ThreadLocal.withInitial(ColumnCaves::new);

    Caves(AtlasCore core, long seed) {
        this.core = core;
        pits = GradientNoise.fork(seed, 301);
        crackLines = GradientNoise.fork(seed, 302);
        crackMask = GradientNoise.fork(seed, 303);
        crackDepth = GradientNoise.fork(seed, 304);
        crackWarp = GradientNoise.fork(seed, 305);
        caverns = GradientNoise.fork(seed, 306);
        cavernsFine = GradientNoise.fork(seed, 307);
        cavernMask = GradientNoise.fork(seed, 308);
        scour = GradientNoise.fork(seed, 309);
        layers = GradientNoise.fork(seed, 310);
        tafoni = GradientNoise.fork(seed, 311);
        archLines = GradientNoise.fork(seed, 312);
        archMask = GradientNoise.fork(seed, 313);
    }

    /** Per-column summary for the per-block caves. */
    private static final class Column {
        int x, z;
        boolean filled;
        double surface;
        // fissure: distance (blocks) from this column to the crack line, max width, depth
        double crackDistance, crackWidth, crackDepth;
        // up to 4 nearby pits: centre, radius, top, bottom, seed
        int pitCount;
        final double[] pitX = new double[4], pitZ = new double[4], pitR = new double[4], pitTop = new double[4], pitBottom = new double[4];
        final long[] pitSeed = new long[4];
        // wind-carved rock: height over the sand at its foot (0 if none), the sand's height, how far
        // inside the outline, how deep and high the foot is scoured, the soft-layer stack, and an arch
        double rock, base, rockInside, scourDepth, scourHeight, layerBand, layerPhase;
        double archHalf, archDist, archY;
        final double[] grad = new double[2];
    }

    private static final class ColumnCaves {
        static final int SIZE = 2048;
        final Column[] slots = new Column[SIZE];

        ColumnCaves() {
            for (int i = 0; i < SIZE; i++) slots[i] = new Column();
        }
    }

    /** Pits, fissures and wind-carved rock at a block: negative where it carves. */
    public double fine(int x, int y, int z) {
        Column c = column(x, z);
        if (c.pitCount == 0 && c.crackWidth <= 0 && c.rock <= 0) {
            return NONE;
        }
        double d = c.rock > 0 ? rockCarve(c, x, y, z) : NONE;
        if (c.crackWidth > 0) {
            double below = c.surface - y;
            if (below > -3 && below < c.crackDepth) {
                double width = c.crackWidth * smoothstep(c.crackDepth, c.crackDepth * 0.35, below);
                d = Math.min(d, (c.crackDistance - width) * 0.6);
            }
        }
        for (int i = 0; i < c.pitCount; i++) {
            if (y > c.pitTop[i] || y < c.pitBottom[i] - c.pitR[i]) continue;
            // The shaft wanders a few blocks as it descends.
            double wx = c.pitX[i] + 5 * GradientNoise.noise2(c.pitSeed[i], y / 45.0, 0.5);
            double wz = c.pitZ[i] + 5 * GradientNoise.noise2(c.pitSeed[i] ^ 0x1FL, y / 45.0, 0.5);
            double r = c.pitR[i] * (1 + 1.6 * smoothstep(c.pitTop[i] - 18, c.pitTop[i], y));
            double horizontal = Math.hypot(x - wx, z - wz);
            double dist = y >= c.pitBottom[i] ? horizontal - r : Math.hypot(horizontal, c.pitBottom[i] - y) - r;
            d = Math.min(d, dist * 0.35);
        }
        return d;
    }

    /**
     * Wind-carved rock at a block. Distances are in blocks from the rock's outline
     * ({@code rockInside}); each term is negative where it carves.
     */
    private double rockCarve(Column c, int x, int y, int z) {
        double u = y - c.base;
        if (u < 0.5 || u > c.rock + 1) return NONE;
        double inside = c.rockInside;
        // The sand-blasted foot: up to a few blocks deep within ~5-14 blocks of the sand, so towers
        // stand on necks and walls overhang. Lumpy, not a ruled line.
        double lumps = 0.75 + 0.5 * ofbm01(scour ^ 0x21L, (x + y * 0.7) / 9.0, (z - y * 0.7) / 9.0);
        double foot = c.scourDepth * smoothstep(c.scourHeight, 1, u) * lumps;
        double d = (inside - foot) * 0.3;
        // Soft layers weather back into grooves 1-2 blocks deep, level across the district.
        double t = (y + c.layerPhase) / c.layerBand;
        t -= Math.floor(t);
        double groove = 2 * smoothstep(0.6, 0.7, t) * smoothstep(0.92, 0.82, t);
        d = Math.min(d, (inside - groove) * 0.3);
        // Tafoni: pockets and holes in the walls, deepest at the face.
        if (inside < 9 && u > 2.5 && u < c.rock - 2.5) {
            double n = GradientNoise.noise3(tafoni, x / 7.0, y / 5.0, z / 7.0)
                    + 0.45 * GradientNoise.noise3(tafoni ^ 0x2L, x / 3.2, y / 2.6, z / 3.2);
            d = Math.min(d, (0.42 - n * smoothstep(9, 1.5, inside)) * 2);
        }
        // Arches: a tunnel along a contour line, round-topped and taller than wide.
        if (c.archHalf > 0) {
            double r = Math.hypot(c.archDist / c.archHalf, (y - c.archY) / (c.archHalf * 1.4));
            d = Math.min(d, (r - 1) * c.archHalf * 0.3);
        }
        return d;
    }

    private void fillRock(Column c, int x, int z, ColumnSample sample) {
        c.rock = sample.rock() > 2 ? sample.rock() : 0;
        c.archHalf = 0;
        if (c.rock <= 0) return;
        c.base = sample.height() - sample.rock();
        c.rockInside = sample.rockInside();
        // Never more than 40% of the rock's radius, so a narrow tower keeps a neck.
        c.scourDepth = Math.min(2 + 4.5 * ofbm01(scour, x / 120.0, z / 120.0), 0.4 * sample.rockSize());
        c.scourHeight = 5 + 9 * ofbm01(scour ^ 0x3L, x / 90.0, z / 90.0);
        c.layerBand = 7 + 5 * ofbm01(layers, x / 700.0, z / 700.0);
        c.layerPhase = 40 * GradientNoise.noise2(layers ^ 0x9L, x / 900.0, z / 900.0);
        // Arches: only through rock thin enough (within 15 blocks of a face), on about half the
        // lines, and only where there's room for a bridge of 4+ blocks over them.
        double mask = smoothstep(0.42, 0.58, ofbm01(archMask, x / 450.0, z / 450.0));
        if (mask <= 0 || c.rockInside > 15) return;
        double n = GradientNoise.noise2d(archLines, x / 80.0, z / 80.0, c.grad);
        double dist = Math.abs(n) * 80 / Math.max(0.05, Math.hypot(c.grad[0], c.grad[1]));
        double half = (2.5 + 4.5 * ofbm01(archMask ^ 0x5L, x / 200.0, z / 200.0)) * mask;
        double tall = half * 1.4;
        double room = c.rock - 2 * tall - 6;
        if (half < 1.5 || dist > half + 1 || room <= 0) return;
        c.archHalf = half;
        c.archDist = dist;
        c.archY = c.base + 2 + tall + room * 0.45 * ofbm01(archMask ^ 0x7L, x / 150.0, z / 150.0);
    }

    /** The lowest a pit or fissure opens the ground at this column, or +infinity. */
    public double cutFloor(int x, int z) {
        Column c = column(x, z);
        double low = Double.POSITIVE_INFINITY;
        if (c.crackWidth > 0) low = c.surface - c.crackDepth;
        for (int i = 0; i < c.pitCount; i++) low = Math.min(low, c.pitBottom[i] - c.pitR[i]);
        return low;
    }

    /** Large caverns at a point (on the interpolated grid): negative inside one. */
    public double cavern(ColumnSample c, int x, int y, int z) {
        if (c.nearWater() && y > c.height() - 40) return NONE; // well clear of a river's bed
        double mask = smoothstep(0.55, 0.7, ofbm01(cavernMask, x / 1600.0, z / 1600.0));
        if (mask <= 0) return NONE;
        double envelope = smoothstep(AtlasCore.TERRAIN_FLOOR + 13, AtlasCore.TERRAIN_FLOOR + 33, y) * smoothstep(c.height() - 25, c.height() - 60, y);
        if (envelope <= 0) return NONE;
        double n = GradientNoise.noise3(caverns, x / 150.0, y / 75.0, z / 150.0)
                + 0.35 * GradientNoise.noise3(cavernsFine, x / 55.0, y / 35.0, z / 55.0);
        return (0.42 - n * mask * envelope) * 5;
    }

    private Column column(int x, int z) {
        ColumnCaves cc = cache.get();
        int slot = (int) GradientNoise.mix(((long) x << 32) ^ (z & 0xFFFFFFFFL) ^ 0xC4FEL) & (ColumnCaves.SIZE - 1);
        Column c = cc.slots[slot];
        if (c.filled && c.x == x && c.z == z) return c;
        c.x = x;
        c.z = z;
        c.filled = true;
        ColumnSample sample = core.column(x, z);
        c.surface = sample.height();
        fillCrack(c, x, z, sample);
        fillPits(c, x, z);
        if (sample.nearWater()) c.pitCount = 0; // nor a pit
        fillRock(c, x, z, sample);
        return c;
    }

    private void fillCrack(Column c, int x, int z, ColumnSample sample) {
        c.crackWidth = 0;
        if (sample.isOcean() || sample.nearWater()) return; // a crack would drain the river
        double mask = smoothstep(0.6, 0.72, ofbm01(crackMask, x / 1100.0, z / 1100.0));
        if (mask <= 0) return;
        double wx = x + 40 * GradientNoise.noise2(crackWarp, x / 90.0, z / 90.0);
        double wz = z + 40 * GradientNoise.noise2(crackWarp ^ 0x7L, x / 90.0, z / 90.0);
        double n = GradientNoise.noise2(crackLines, wx / 260.0, wz / 260.0);
        double nx = GradientNoise.noise2(crackLines, (wx + 1) / 260.0, wz / 260.0) - n;
        double nz = GradientNoise.noise2(crackLines, wx / 260.0, (wz + 1) / 260.0) - n;
        double distance = Math.abs(n) / Math.max(1e-5, Math.hypot(nx, nz));
        double width = (1.2 + 2.6 * ofbm01(crackDepth ^ 0x11L, x / 300.0, z / 300.0)) * mask;
        if (distance > width + 1) return;
        c.crackDistance = distance;
        c.crackWidth = width;
        c.crackDepth = 20 + 70 * ofbm01(crackDepth, x / 400.0, z / 400.0);
    }

    private void fillPits(Column c, int x, int z) {
        c.pitCount = 0;
        int gx0 = GradientNoise.floor(x / PIT_SPACING), gz0 = GradientNoise.floor(z / PIT_SPACING);
        for (int dz = -1; dz <= 1 && c.pitCount < 4; dz++) {
            for (int dx = -1; dx <= 1 && c.pitCount < 4; dx++) {
                long h = GradientNoise.hash(pits, gx0 + dx, gz0 + dz);
                if (unit(h, 0) > PIT_CHANCE) continue;
                double px = (gx0 + dx + 0.2 + 0.6 * unit(h, 1)) * PIT_SPACING;
                double pz = (gz0 + dz + 0.2 + 0.6 * unit(h, 2)) * PIT_SPACING;
                double r = 3 + 5 * unit(h, 3);
                if (Math.hypot(x - px, z - pz) > r * 2.6 + 6) continue;
                ColumnSample centre = core.column((int) Math.floor(px), (int) Math.floor(pz));
                if (centre.height() < AtlasCore.SEA_LEVEL + 8) continue;
                int i = c.pitCount++;
                c.pitX[i] = px;
                c.pitZ[i] = pz;
                c.pitR[i] = r;
                c.pitTop[i] = centre.height() + 4;
                c.pitBottom[i] = Math.max(AtlasCore.TERRAIN_FLOOR + 20, centre.height() - (60 + 100 * unit(h, 4)));
                c.pitSeed[i] = h;
            }
        }
    }

    private static double ofbm01(long seed, double x, double z) {
        return Math.max(0, Math.min(1, Fractal.fbm(seed, x, z, 2) * 0.5 + 0.5));
    }

    private static double unit(long hash, int k) {
        return (GradientNoise.mix(hash + k * 0x9E3779B97F4A7C15L) >>> 11) * 0x1.0p-53;
    }
}
