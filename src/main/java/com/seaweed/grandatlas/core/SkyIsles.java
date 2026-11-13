package com.seaweed.grandatlas.core;

import com.seaweed.grandatlas.core.noise.GradientNoise;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The Sky Isles (M6 fantasy, design §6.4's floating mini-continent): a great island hanging
 * hundreds of blocks over the land, ringed by a flock of smaller isles at other heights. Each is
 * a grassy, wooded top over a hanging root of rock that tapers to jagged points beneath. Streams
 * pour off their edges in falls to the ground far below ({@code world/FantasyDetail}).
 *
 * <p>Unlike the macros, which shape the ground's height, an isle is solid over air, so it is its
 * own density term: the world's final density takes the larger of the ground's and this one
 * ({@code grandatlas:sky_isles}, added outside every cut so rivers and caves never hole an isle).
 * Its biome is {@link BiomeKind#SKY_GARDEN} from just under its rock to well over its top.
 *
 * <p>Placed one at most per 2,400-block cell, over land, high enough to clear the ground under it
 * by at least 90 blocks; most cells have none.
 */
public final class SkyIsles {
    public static final double CELL = 2400;
    /** The density this term returns wherever no isle is. */
    public static final double NONE = -100;

    /** One isle: centre, radius, the height of its top, how deep its rock hangs (times its radius), its seed. */
    public record Isle(double x, double z, double r, double top, double depth, long seed, boolean main) {
        public double reach() {
            return r * 1.35 + 4;
        }
    }

    /** A flock: its main isle first. */
    public record Flock(long key, List<Isle> isles, double x, double z, double reach) {}

    private final AtlasCore core;
    private final long seed;
    private final ConcurrentHashMap<Long, Flock> flocks = new ConcurrentHashMap<>();
    private static final Flock EMPTY = new Flock(0, List.of(), 0, 0, 0);

    SkyIsles(AtlasCore core, long seed) {
        this.core = core;
        this.seed = seed;
    }

    /** The cell's flock, or null. */
    public Flock flock(int i, int j) {
        long key = ((long) i << 32) ^ (j & 0xFFFFFFFFL);
        Flock f = flocks.get(key);
        if (f == null) {
            f = build(i, j, key);
            if (flocks.size() > 4096) flocks.clear();
            flocks.put(key, f);
        }
        return f == EMPTY ? null : f;
    }

    private Flock build(int i, int j, long key) {
        long h = GradientNoise.hash(seed, i, j);
        if (unit(h, 0) > 0.3) return EMPTY;
        double cx = (i + 0.3 + 0.4 * unit(h, 1)) * CELL, cz = (j + 0.3 + 0.4 * unit(h, 2)) * CELL;
        if (core.world().at(cx, cz).continentalness() < 0.08) return EMPTY; // over land, not the open sea
        double r = 110 + 190 * unit(h, 3);
        // Clear of the ground under it, mountains included.
        double ground = 0;
        for (int k = 0; k < 9; k++) {
            double a = k * Math.PI * 2 / 8, d = k == 8 ? 0 : r * 1.6;
            ground = Math.max(ground, core.sampleBase(cx + Math.cos(a) * d, cz + Math.sin(a) * d).height());
        }
        double top = Math.max(330 + 170 * unit(h, 4), ground + 90 + 0.6 * r);
        if (top > 820) return EMPTY;
        List<Isle> isles = new ArrayList<>();
        isles.add(new Isle(cx, cz, r, top, 0.55 + 0.25 * unit(h, 5), h, true));
        int n = 4 + (int) (unit(h, 6) * 7);
        double reach = r * 1.35;
        for (int k = 0; k < n; k++) {
            long hk = GradientNoise.mix(h + 0x51AE * (k + 1));
            double a = unit(hk, 0) * Math.PI * 2, d = r * (1.15 + 0.85 * unit(hk, 1));
            double rr = 12 + 34 * Math.pow(unit(hk, 2), 1.4);
            double x = cx + Math.cos(a) * d, z = cz + Math.sin(a) * d;
            boolean clear = true;
            for (Isle o : isles) {
                if (Math.hypot(o.x - x, o.z - z) < (o.r + rr) * 1.25) clear = false;
            }
            if (!clear) continue;
            double t = top - 70 + 130 * unit(hk, 3);
            t = Math.max(t, core.sampleBase(x, z).height() + 60 + rr);
            isles.add(new Isle(x, z, rr, t, 0.6 + 0.5 * unit(hk, 4), hk, false));
            reach = Math.max(reach, d + rr * 1.35 + 4);
        }
        return new Flock(key, List.copyOf(isles), cx, cz, reach + 4);
    }

    /** The isles whose reach covers column (x, z), or an empty list. */
    public List<Isle> near(int x, int z) {
        int ci = GradientNoise.floor(x / CELL), cj = GradientNoise.floor(z / CELL);
        List<Isle> out = null;
        for (int j = cj - 1; j <= cj + 1; j++) {
            for (int i = ci - 1; i <= ci + 1; i++) {
                Flock f = flock(i, j);
                if (f == null || Math.hypot(x - f.x, z - f.z) > f.reach) continue;
                for (Isle isle : f.isles) {
                    if (Math.hypot(x - isle.x, z - isle.z) <= isle.reach()) {
                        if (out == null) out = new ArrayList<>(2);
                        out.add(isle);
                    }
                }
            }
        }
        return out == null ? List.of() : out;
    }

    /** An isle's top and bottom at column (x, z): {surface, bottom}, or null outside its outline. */
    public double[] span(Isle isle, double x, double z) {
        double dx = x - isle.x, dz = z - isle.z, d = Math.sqrt(dx * dx + dz * dz);
        double ux = d > 0 ? dx / d : 1, uz = d > 0 ? dz / d : 0;
        // The outline wobbles with the angle (sampled on a circle: no seam) and frays at a smaller scale.
        double wob = 0.2 * GradientNoise.noise2(seed ^ isle.seed, 1.8 * ux, 1.8 * uz)
                + 0.08 * GradientNoise.noise2(seed ^ isle.seed ^ 0x77, 5 * ux, 5 * uz);
        double q = d / (isle.r * (1 + wob));
        if (q >= 1) return null;
        double surface = isle.top + 5 * GradientNoise.noise2(seed ^ 0x5CA1, x / 45.0, z / 45.0) * (1 - q)
                + 3 * GradientNoise.noise2(seed ^ 0x5CA2, x / 13.0, z / 13.0) - 5 * Math.pow(q, 5);
        // The hanging root: deepest under the middle, tapering to jagged points.
        double root = isle.r * isle.depth * Math.pow(1 - Math.pow(q, 1.6), 0.8);
        double spikes = 30 * Math.max(0, GradientNoise.noise2(seed ^ 0x5CA3 ^ isle.seed, x / 9.0, z / 9.0) - 0.35) * (1 - q);
        double bottom = surface - 5 - root - spikes - 4 * GradientNoise.noise2(seed ^ 0x5CA4, x / 20.0, z / 20.0);
        // Thin at the rim, never a knife-edge: at least 3 blocks of rock where it exists.
        double rim = (1 - q) * isle.r;
        if (rim < 1.5) return null;
        return new double[] {surface, Math.min(bottom, surface - 3)};
    }

    /** The isles' density at a block (positive solid), or {@link #NONE}. */
    public double density(List<Isle> isles, int x, int y, int z) {
        double best = NONE;
        for (Isle isle : isles) {
            if (y > isle.top + 12 || y < isle.top - isle.r * isle.depth - 50) continue;
            double[] s = span(isle, x, z);
            if (s == null) continue;
            double d = Math.min(s[0] - y, y - s[1]);
            best = Math.max(best, d * AtlasCore.GRADIENT * 4);
        }
        return best;
    }

    /** True where the sky garden's biome is: in an isle's rock or the air just over it. */
    public boolean claims(List<Isle> isles, int x, int y, int z) {
        for (Isle isle : isles) {
            if (y > isle.top + 48 || y < isle.top - isle.r * isle.depth - 50) continue;
            double[] s = span(isle, x, z);
            if (s != null && y >= s[1] - 4 && y <= s[0] + 40) return true;
        }
        return false;
    }

    /** The nearest flock's main isle to (x, z), searched ring by ring, or null within about 30 km. */
    public Isle nearest(double x, double z) {
        int ci = GradientNoise.floor(x / CELL), cj = GradientNoise.floor(z / CELL);
        Isle best = null;
        double bestD = Double.MAX_VALUE;
        for (int ring = 0; ring <= 12 && best == null; ring++) {
            for (int j = cj - ring; j <= cj + ring; j++) {
                for (int i = ci - ring; i <= ci + ring; i++) {
                    if (Math.max(Math.abs(i - ci), Math.abs(j - cj)) != ring) continue;
                    Flock f = flock(i, j);
                    if (f == null) continue;
                    double d = Math.hypot(f.x - x, f.z - z);
                    if (d < bestD) {
                        bestD = d;
                        best = f.isles.get(0);
                    }
                }
            }
        }
        return best;
    }

    static double unit(long h, int k) {
        return (GradientNoise.mix(h + k * 0x9E3779B97F4A7C15L) >>> 11) * 0x1.0p-53;
    }
}
