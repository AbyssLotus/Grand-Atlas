package com.seaweed.grandatlas.core;

import static com.seaweed.grandatlas.core.Maths.smoothstep;

import com.seaweed.grandatlas.core.noise.Fractal;
import com.seaweed.grandatlas.core.noise.GradientNoise;

/**
 * M7c (docs/M7_UNDERGROUND_PLAN.md): mega-caverns, underground landscapes. Negative values carve.
 * Big enough for the interpolated 4x8x4 grid, so they join vanilla's deep cave mix
 * ({@code grandatlas:caves} mode {@code caverns}) and can never open the surface: that branch
 * only runs in deep stone.
 *
 * <p>One chance in a 1700-block cell. Each is its own shape (the vanilla-chaos notes: every cave
 * gets its own multipliers):
 * <ul>
 *   <li>An outline from a stretched, rotated ellipse broken into lobes and bays by noise, 220-800
 *       blocks across, most of them toward the small end.</li>
 *   <li>A tilted floor of hills, hollows and rubble mounds (lakes come with M7d), and a roof of
 *       broad uneven vaults with separate spires hanging from it.</li>
 *   <li>Hourglass pillars from floor to ceiling, 4-22 thick, flared where they meet the rock.</li>
 *   <li>Walls with ledges and overhangs from 3D noise, strongest near the edge.</li>
 *   <li>Mostly in the deep (y 0 to -250), some in the abyss, always 45+ blocks under the ground.</li>
 *   <li><b>Lakes</b> (M7d): most caverns have one or two, dug as bowls into the floor. Each fills
 *       to a block under the lowest point of its own rim, so its shores hold it.</li>
 *   <li><b>Underground seas</b> (M7d): one abyss cavern in seven is a sea, 1-3 km across, its
 *       floor one great bowl under water, islands where the hills break the surface.</li>
 * </ul>
 */
public final class MegaCaverns {
    static final double CELL = 1700;
    private static final double CHANCE = 0.38;

    private final AtlasCore core;
    private final long seed;
    private final ThreadLocal<Slots> slots = ThreadLocal.withInitial(Slots::new);
    private final ThreadLocal<java.util.HashMap<Long, Site>> sites = ThreadLocal.withInitial(java.util.HashMap::new);

    MegaCaverns(AtlasCore core, long seed) {
        this.core = core;
        this.seed = seed;
    }

    /** One cavern's parameters. {@code present} false for an empty cell. */
    static final class Site {
        boolean present;
        double x, z, r, stretch, cos, sin, height, y0;
        long h;
        boolean sea;
        // lakes: centre, radius, bowl depth, and water level (NaN: none could hold)
        double[] lx = new double[0], lz = lx, lr = lx, ld = lx, level = lx;
    }

    /** Per column: the cavern's floor and ceiling here, or none. */
    private static final class Col {
        int x, z;
        boolean filled, open;
        double floor, ceil, edge, pillarD, pillarR, pillarFloor, pillarCeil;
        double water = Double.NaN;
        boolean sea;
        long h;
    }

    private static final class Slots {
        static final int SIZE = 1024;
        final Col[] c = new Col[SIZE];

        Slots() {
            for (int i = 0; i < SIZE; i++) c[i] = new Col();
        }
    }

    Site site(int i, int j) {
        java.util.HashMap<Long, Site> cache = sites.get();
        long key = ((long) i << 32) ^ (j & 0xFFFFFFFFL);
        Site s = cache.get(key);
        if (s != null) return s;
        if (cache.size() > 512) cache.clear();
        s = new Site();
        cache.put(key, s);
        long h = GradientNoise.hash(seed, i, j);
        if (unit(h, 0) > CHANCE) return s;
        s.h = h;
        s.x = (i + 0.25 + 0.5 * unit(h, 1)) * CELL;
        s.z = (j + 0.25 + 0.5 * unit(h, 2)) * CELL;
        s.r = 110 + 290 * Math.pow(unit(h, 3), 1.7);
        s.stretch = 1 + 0.8 * unit(h, 4);
        boolean abyss = unit(h, 7) < 0.3;
        s.sea = abyss && unit(h, 13) < 1 / 7.0;
        if (s.sea) {
            s.r = 400 + 600 * unit(h, 3);
            s.stretch = 1 + 0.4 * unit(h, 4);
        }
        double a = unit(h, 5) * Math.PI;
        s.cos = Math.cos(a);
        s.sin = Math.sin(a);
        // Most are wide halls; one in five is a cathedral half again as tall.
        s.height = Math.max(55, Math.min(260, s.r * 0.38 * (0.7 + 0.6 * unit(h, 6))));
        if (unit(h, 12) < 0.2) s.height = Math.min(320, s.height * 1.5);
        if (s.sea) s.height = 140 + 100 * unit(h, 6);
        double lo = abyss ? -460 + s.height * 0.4 : -230 + s.height * 0.4;
        double hi = abyss ? -260 : -40 - s.height * 0.65;
        s.y0 = lo + (hi - lo) * unit(h, 8);
        // Keep the roof 45+ blocks under the lowest ground over the footprint.
        double ground = Double.MAX_VALUE;
        for (int k = 0; k < 9; k++) {
            double ang = k * Math.PI / 4, d = k == 8 ? 0 : s.r * s.stretch;
            ColumnSample c = core.column((int) Math.floor(s.x + Math.cos(ang) * d), (int) Math.floor(s.z + Math.sin(ang) * d));
            ground = Math.min(ground, c.height());
        }
        double top = ground - 45 - s.height * 0.65;
        if (s.y0 > top) s.y0 = top;
        if (s.y0 - s.height * 0.4 < AtlasCore.MIN_Y + 40) return s;
        if (core.setPieces().crowds(s.x, s.z, s.r * s.stretch * 1.5 + 60)) return s; // a set piece has this ground
        s.present = true;
        lakes(s);
        return s;
    }

    /**
     * Places a cavern's lakes and finds each one's water level: the lowest floor on a ring just
     * outside its bowl, less a block, so the shore all round stands above the water. Ring points
     * in rock (outside the cavern) count as wall.
     */
    private void lakes(Site s) {
        long h = s.h;
        int n = s.sea ? 1 : unit(h, 20) < 0.4 ? 0 : unit(h, 21) < 0.67 ? 1 : 2;
        s.lx = new double[n];
        s.lz = new double[n];
        s.lr = new double[n];
        s.ld = new double[n];
        s.level = new double[n];
        for (int k = 0; k < n; k++) {
            if (s.sea) {
                s.lx[k] = s.x;
                s.lz[k] = s.z;
                s.lr[k] = s.r * s.stretch * 0.8;
                s.ld[k] = 50 + 70 * unit(h, 22);
            } else {
                double a = unit(h, 23 + k) * 2 * Math.PI, d = 0.6 * unit(h, 25 + k);
                double u = Math.cos(a) * d * s.r * s.stretch, v = Math.sin(a) * d * s.r;
                s.lx[k] = s.x + u * s.cos - v * s.sin;
                s.lz[k] = s.z + u * s.sin + v * s.cos;
                s.lr[k] = 25 + 0.25 * s.r * unit(h, 27 + k);
                s.ld[k] = 8 + 25 * unit(h, 29 + k);
            }
        }
        Col probe = new Col();
        if (s.sea) {
            s.level[0] = Math.floor(s.y0 - 0.15 * s.height); // the sea's floor is shaped to this
            return;
        }
        for (int k = 0; k < n; k++) {
            double low = Double.MAX_VALUE;
            int samples = Math.max(96, (int) (2 * Math.PI * s.lr[k] * 1.06 / 6));
            for (int q = 0; q < samples; q++) {
                double a = q * 2 * Math.PI / samples;
                int x = (int) Math.floor(s.lx[k] + Math.cos(a) * s.lr[k] * 1.06), z = (int) Math.floor(s.lz[k] + Math.sin(a) * s.lr[k] * 1.06);
                probe.open = false;
                shape(probe, s, x, z, false);
                if (probe.open) low = Math.min(low, probe.floor);
            }
            s.level[k] = low == Double.MAX_VALUE ? Double.NaN : Math.floor(low) - 1;
        }
    }

    /** The mega-caverns at a point on the interpolated grid: negative inside one. */
    public double carve(int x, int y, int z) {
        Col c = column(x, z);
        if (!c.open) return Caves.NONE;
        double d = Math.max(c.floor - y, y - c.ceil);
        if (d > 24) return Caves.NONE;
        // Ledges and overhangs: rock that juts and recedes, most at the walls.
        double wall = 2.5 + 5 * (1 - c.edge);
        d += wall * GradientNoise.noise3(c.h, x / 20.0, y / 14.0, z / 20.0);
        // Pillars, flaring where they meet the floor and the ceiling.
        if (c.pillarR > 0 && y > c.pillarFloor - 4 && y < c.pillarCeil + 4) {
            double span = Math.max(1, c.pillarCeil - c.pillarFloor);
            double u = (y - c.pillarFloor) / span;
            double flare = 1 + 1.6 * Math.pow(Math.abs(2 * u - 1), 3);
            d = Math.max(d, (c.pillarR * flare - c.pillarD) * 0.8);
        }
        return d * 0.12;
    }

    private Col column(int x, int z) {
        Slots ss = slots.get();
        Col c = ss.c[(int) (GradientNoise.mix(((long) x << 32) ^ (z & 0xFFFFFFFFL) ^ 0x3E6ACAL) & (Slots.SIZE - 1))];
        if (c.filled && c.x == x && c.z == z) return c;
        c.x = x;
        c.z = z;
        c.filled = true;
        c.open = false;
        int ci = GradientNoise.floor(x / CELL), cj = GradientNoise.floor(z / CELL);
        for (int dj = -1; dj <= 1 && !c.open; dj++) {
            for (int di = -1; di <= 1 && !c.open; di++) {
                Site s = site(ci + di, cj + dj);
                if (s.present) {
                    c.water = Double.NaN;
                    shape(c, s, x, z, true);
                }
            }
        }
        return c;
    }

    private void shape(Col c, Site s, int x, int z, boolean water) {
        double dx = x - s.x, dz = z - s.z;
        double reach = s.r * s.stretch * 1.5;
        if (dx * dx + dz * dz > reach * reach) return;
        double u = (dx * s.cos + dz * s.sin) / (s.r * s.stretch), v = (-dx * s.sin + dz * s.cos) / s.r;
        long h = s.h;
        // Lobes and bays: the ellipse broken up by noise at about the cavern's own scale.
        double m = 1 - (u * u + v * v) + 0.55 * Fractal.fbm(GradientNoise.fork(h, 1), x / (s.r * 0.9), z / (s.r * 0.9), 3);
        if (m <= 0) return;
        double edge = smoothstep(0, 0.35, m);
        double H = s.height;
        // Height varies a lot across the cavern: cathedral domes where the roof soars, low sagging
        // sections between (a flat-roofed slab looked bland in the first slice).
        double dome = Math.max(0, Math.min(1, Fractal.fbm(GradientNoise.fork(h, 2), x / (s.r * 0.55), z / (s.r * 0.55), 2) * 0.7 + 0.5));
        double hc = H * Math.pow(edge, 0.6) * (0.4 + 1.0 * dome);
        // The floor: tilted as a whole one way or another, rolling hills on that, and rubble mounds
        // where the roof came down.
        double tilt = 0.15 * H * (u * Math.cos(unit(h, 9) * 6.3) + v * Math.sin(unit(h, 9) * 6.3)) * (unit(h, 10) - 0.3);
        double hills = 0.4 * H * Fractal.fbm(GradientNoise.fork(h, 3), x / 120.0, z / 120.0, 3);
        double mounds = 0.3 * H * Math.pow(Math.max(0, Fractal.fbm(GradientNoise.fork(h, 8), x / 45.0, z / 45.0, 2) - 0.35) / 0.65, 2);
        double floor = s.y0 - 0.35 * hc + (hills + mounds) * edge + tilt;
        if (s.sea) floor = seaFloor(s, x, z, m, floor);
        // Lake bowls dug into the floor.
        for (int k = 0; k < s.lx.length && !s.sea; k++) {
            double d = Math.hypot(x - s.lx[k], z - s.lz[k]) / s.lr[k];
            if (d < 1) floor -= s.ld[k] * sq(1 - d * d);
        }
        // The roof: broad uneven vaults, higher in some places, sagging in others.
        double vaults = 0.2 * H * Fractal.fbm(GradientNoise.fork(h, 4), x / 240.0, z / 240.0, 2)
                + 0.15 * H * GradientNoise.noise2(GradientNoise.fork(h, 11), x / 70.0, z / 70.0);
        double ceil = s.y0 + 0.65 * hc + vaults * edge;
        // Spires: separate hanging cones, scattered (half the 70-block cells), each its own length
        // and girth, and fewer rising cones from the floor. A ridged noise here made an egg-carton
        // roof of evenly spaced teeth.
        double spire = 0, rise = 0;
        int si = GradientNoise.floor(x / 70.0), sj = GradientNoise.floor(z / 70.0);
        for (int b = -1; b <= 1; b++) {
            for (int a = -1; a <= 1; a++) {
                long sh = GradientNoise.hash(GradientNoise.fork(h, 5), si + a, sj + b);
                double kind = unit(sh, 0);
                if (kind > 0.8) continue;
                double sx = (si + a + 0.15 + 0.7 * unit(sh, 1)) * 70, sz = (sj + b + 0.15 + 0.7 * unit(sh, 2)) * 70;
                double rad = 6 + 16 * unit(sh, 3), d = Math.hypot(x - sx, z - sz);
                if (d >= rad) continue;
                double shape = Math.pow(1 - d / rad, 1.6);
                if (kind < 0.5) spire = Math.max(spire, (0.2 + 0.6 * unit(sh, 4) * unit(sh, 4)) * H * shape);
                else rise = Math.max(rise, (0.1 + 0.3 * unit(sh, 4)) * H * shape);
            }
        }
        ceil -= spire * edge;
        floor += rise * edge;
        // Never within 40 blocks of the ground right above (48 here, since the wall noise below can
        // lift the roof ~7): the roof dips under a valley.
        ceil = Math.min(ceil, core.column(x, z).height() - 48);
        if (ceil - floor < 6) return; // a choke: rock here
        c.open = true;
        c.floor = floor;
        c.ceil = ceil;
        c.sea = s.sea;
        if (water && s.sea) {
            if (floor < s.level[0]) c.water = s.level[0];
        } else if (water) {
            for (int k = 0; k < s.lx.length; k++) {
                if (!Double.isNaN(s.level[k]) && floor < s.level[k] && Math.hypot(x - s.lx[k], z - s.lz[k]) < s.lr[k] * 1.06) {
                    c.water = s.level[k];
                    break;
                }
            }
        }
        c.edge = edge;
        c.h = GradientNoise.fork(h, 6);
        // The nearest pillar (jittered points every 72 blocks, a quarter of them standing, 4-22 thick).
        c.pillarR = 0;
        int pi = GradientNoise.floor(x / 72.0), pj = GradientNoise.floor(z / 72.0);
        double best = Double.MAX_VALUE;
        for (int b = -1; b <= 1; b++) {
            for (int a = -1; a <= 1; a++) {
                long ph = GradientNoise.hash(GradientNoise.fork(h, 7), pi + a, pj + b);
                if (unit(ph, 0) > 0.25) continue;
                double px = (pi + a + 0.2 + 0.6 * unit(ph, 1)) * 72, pz = (pj + b + 0.2 + 0.6 * unit(ph, 2)) * 72;
                double d = Math.hypot(x - px, z - pz), r = 4 + 18 * unit(ph, 3) * unit(ph, 4);
                if (d - r < best) {
                    best = d - r;
                    c.pillarD = d;
                    c.pillarR = r * edge;
                }
            }
        }
        c.pillarFloor = floor;
        c.pillarCeil = ceil;
    }

    /**
     * An underground sea's floor. The level comes first and the floor is shaped to it (a ring-rim
     * level on a hilly floor filled only its hollows: "more like a puddle or a couple ponds"):
     * all the cavern's interior is a basin 50-120 deep, shelving up to the level near the walls
     * and rising 12 above it at the shore, so the shore holds the sea all round; islands stand
     * where an island noise peaks, and the cavern's rising cones become sea stacks.
     */
    private static double seaFloor(Site s, int x, int z, double m, double floor) {
        double level = s.level[0], depth = s.ld[0];
        long h = s.h;
        double island = Fractal.fbm(GradientNoise.fork(h, 40), x / 260.0, z / 260.0, 3) + 0.5 * Fractal.fbm(GradientNoise.fork(h, 41), x / 80.0, z / 80.0, 2);
        double shore = level + 12 * smoothstep(0.36, 0.2, m);
        if (m < 0.36) return Math.max(Math.min(floor, level + 40), shore);
        double basin = level - 2 - depth * smoothstep(0.36, 0.95, m) * (0.7 + 0.3 * (GradientNoise.noise2(GradientNoise.fork(h, 42), x / 150.0, z / 150.0) * 0.5 + 0.5));
        // An island: the floor climbs out of the deep wherever the noise is high enough.
        double rise = smoothstep(0.38, 0.62, island);
        return basin + rise * (level + 4 + 28 * Math.max(0, island - 0.62) - basin);
    }

    /** 0 outside any mega-cavern, 1 in one (within 8 blocks of its floor and roof), 2 in a sea's. */
    public int inside(int x, int y, int z) {
        Col c = column(x, z);
        if (!c.open || y < c.floor - 8 || y > c.ceil + 8) return 0;
        return c.sea ? 2 : 1;
    }

    /** The water level of a cavern lake or sea in this column, or NaN. */
    public double water(int x, int z) {
        return column(x, z).water;
    }

    /** This column's open span in a mega-cavern, {floor, roof}, or null outside one. */
    public double[] span(int x, int z) {
        Col c = column(x, z);
        return c.open ? new double[] {c.floor, c.ceil} : null;
    }

    /** The cavern floor in this column (meaningful where {@link #water} is set). */
    public double floor(int x, int z) {
        return column(x, z).floor;
    }

    private static double sq(double v) {
        return v * v;
    }

    /** The nearest underground sea to (x, z): {x, z, across, water level}, or null within ~6 cells. */
    public double[] nearestSea(double x, double z) {
        int ci = GradientNoise.floor(x / CELL), cj = GradientNoise.floor(z / CELL);
        double[] best = null;
        double bestD = Double.MAX_VALUE;
        for (int j = cj - 6; j <= cj + 6; j++) {
            for (int i = ci - 6; i <= ci + 6; i++) {
                Site s = site(i, j);
                if (!s.present || !s.sea || s.level.length == 0) continue;
                double d = Math.hypot(s.x - x, s.z - z);
                if (d < bestD) {
                    bestD = d;
                    best = new double[] {s.x, s.z, 2 * s.r * s.stretch, s.level[0]};
                }
            }
        }
        return best;
    }

    /** The nearest mega-cavern to (x, z): {x, z, across, y of its middle}, or null within ~3 cells. */
    public double[] nearest(double x, double z) {
        int ci = GradientNoise.floor(x / CELL), cj = GradientNoise.floor(z / CELL);
        double[] best = null;
        double bestD = Double.MAX_VALUE;
        for (int j = cj - 3; j <= cj + 3; j++) {
            for (int i = ci - 3; i <= ci + 3; i++) {
                Site s = site(i, j);
                if (!s.present) continue;
                double d = Math.hypot(s.x - x, s.z - z);
                if (d < bestD) {
                    bestD = d;
                    best = new double[] {s.x, s.z, 2 * s.r * s.stretch, s.y0};
                }
            }
        }
        return best;
    }

    private static double unit(long hash, int k) {
        return (GradientNoise.mix(hash + k * 0x9E3779B97F4A7C15L) >>> 11) * 0x1.0p-53;
    }
}
