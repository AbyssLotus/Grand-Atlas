package com.seaweed.grandatlas.core.province;

import com.seaweed.grandatlas.core.landform.Landform;
import com.seaweed.grandatlas.core.noise.Fractal;
import com.seaweed.grandatlas.core.noise.GradientNoise;
import com.seaweed.grandatlas.core.world.WorldField;
import java.util.List;
import java.util.Set;

/**
 * The province mosaic (design §5.3, §7): jittered-grid Voronoi cells in a domain-warped
 * space, so boundaries wander like real terrain edges. A finer, weaker warp roughens the
 * edges so escarpments don't run in smooth arcs.
 *
 * <p><b>Landform choice.</b> Each cell weighs every landform by the climate and context at its
 * centre, then re-weights against the final picks of its higher-priority neighbours by
 * {@linkplain Landform#sameNeighbour same-landform affinity} (wonders never repeat, ordinary
 * country may merge into bigger regions, ranges and dune seas attract into chains), a wonder-pair
 * bonus, and a smaller bonus for differing elevation classes. Everything is a function of cell
 * coordinates, so any chunk can resolve any cell. A wonder only repeats next to itself when a
 * cell has no other option ({@code SelfCheck} measures the rate).
 *
 * <p>A mosaic is parameterised by seed and cell size, so a macro landform (M2) can run its own
 * finer sub-mosaic inside its footprint.
 */
public final class ProvinceMosaic {
    /** Cells searched around a point: a 5x5 window. */
    public static final int WINDOW = 25;

    /**
     * Every province in the 5x5 window around a point, nearest first, with warped-space
     * distances. The blender weights only the near ones, but measures each one's distance to
     * the nearest <i>different</i> landform over the whole window. Cells outside it are at
     * least ~2.1 cells away, beyond any taper.
     */
    public static final class Nearest {
        public final Province[] province = new Province[WINDOW];
        public final double[] distance = new double[WINDOW];
        final int[] cellX = new int[WINDOW], cellZ = new int[WINDOW];
    }

    /** What a cell knows before looking at its neighbours: the context at its centre and its priority. */
    private record Initial(WorldField.Point context, double priority) {}

    /** Where cells read their climate and context: the world field, or a macro's adjusted view of it. */
    @FunctionalInterface
    public interface Context {
        WorldField.Point at(double x, double z);
    }

    /** A landform's selection weight in a context; a macro's family filter returns 0 outside the family. */
    @FunctionalInterface
    public interface Weigher {
        double weight(Landform landform, WorldField.Point context);
    }

    private static final Set<Set<String>> WONDER_PAIRS = Set.of(
            Set.of("canyon_plateau", "dune_sea"),
            Set.of("playa", "megarange"),
            Set.of("canyon_plateau", "playa"),
            Set.of("rock_desert", "dune_sea"));

    private final long seed;
    private final double cell;
    /** Warp amplitudes and wavelengths scale with the cell, so a sub-mosaic is the same shape, smaller. */
    private final double warpScale;
    private final Context context;
    private final Weigher weigher;
    private final List<Landform> landforms;
    private final Landform ocean;
    private final Landform fallback;
    private final long warpA, warpB, raggedA, raggedB;
    private final ThreadLocal<Caches> caches = ThreadLocal.withInitial(Caches::new);

    /** The world-level mosaic: world context, each landform's own weight. */
    public ProvinceMosaic(long seed, double cell, WorldField world, List<Landform> landforms, Landform ocean, Landform fallback) {
        this(seed, cell, world::at, Landform::weight, landforms, ocean, fallback);
    }

    public ProvinceMosaic(long seed, double cell, Context context, Weigher weigher, List<Landform> landforms,
                          Landform ocean, Landform fallback) {
        this.seed = seed;
        this.cell = cell;
        this.warpScale = cell / 1600.0;
        this.context = context;
        this.weigher = weigher;
        this.landforms = landforms;
        this.ocean = ocean;
        this.fallback = fallback;
        warpA = GradientNoise.fork(seed, 101);
        warpB = GradientNoise.fork(seed, 102);
        raggedA = GradientNoise.fork(seed, 103);
        raggedB = GradientNoise.fork(seed, 104);
    }

    public static long key(int cx, int cz) {
        return ((long) cx << 32) | (cz & 0xFFFFFFFFL);
    }

    public double cellSize() {
        return cell;
    }

    /** Fills {@code out} with every province of the 5x5 window around (x, z), nearest first. */
    public void nearest(double x, double z, Nearest out) {
        // Three warp scales: broad bends, mid-scale lobes, and ragged edges. The ragged scale
        // stays fixed in blocks, since it's about how an edge looks up close.
        double k = warpScale;
        double u = x + 650 * k * Fractal.fbm(warpA, x / (2600.0 * k), z / (2600.0 * k), 2)
                + 180 * k * GradientNoise.noise2(warpA ^ 0x1234L, x / (600.0 * k), z / (600.0 * k))
                + 36 * GradientNoise.noise2(raggedA, x / 140.0, z / 140.0);
        double v = z + 650 * k * Fractal.fbm(warpB, x / (2600.0 * k), z / (2600.0 * k), 2)
                + 180 * k * GradientNoise.noise2(warpB ^ 0x1234L, x / (600.0 * k), z / (600.0 * k))
                + 36 * GradientNoise.noise2(raggedB, x / 140.0, z / 140.0);
        int cx = GradientNoise.floor(u / cell), cz = GradientNoise.floor(v / cell);
        double[] d = out.distance;
        int n = 0;
        for (int dz = -2; dz <= 2; dz++) {
            for (int dx = -2; dx <= 2; dx++) {
                int gx = cx + dx, gz = cz + dz;
                double px = centerX(gx, gz) - u, pz = centerZ(gx, gz) - v;
                double dist = Math.sqrt(px * px + pz * pz);
                int i = n++;
                while (i > 0 && d[i - 1] > dist) { // insertion sort, nearest first
                    d[i] = d[i - 1];
                    out.cellX[i] = out.cellX[i - 1];
                    out.cellZ[i] = out.cellZ[i - 1];
                    i--;
                }
                d[i] = dist;
                out.cellX[i] = gx;
                out.cellZ[i] = gz;
            }
        }
        for (int i = 0; i < WINDOW; i++) {
            out.province[i] = province(out.cellX[i], out.cellZ[i]);
        }
    }

    /** The province of a cell (cached per thread). */
    public Province province(int cx, int cz) {
        Caches c = caches.get();
        long key = key(cx, cz);
        int slot = (int) GradientNoise.mix(key ^ 0x5DEECE66DL) & (Caches.SIZE - 1);
        if (c.provinceKeys[slot] == key && c.provinces[slot] != null) {
            return c.provinces[slot];
        }
        Province p = resolve(cx, cz);
        c.provinceKeys[slot] = key;
        c.provinces[slot] = p;
        return p;
    }

    /** Searches outward ring by ring for the nearest cell holding {@code target}. */
    public Province find(Landform target, double x, double z, int maxRings) {
        int cx = GradientNoise.floor(x / cell), cz = GradientNoise.floor(z / cell);
        for (int r = 0; r <= maxRings; r++) {
            Province best = null;
            double bestD = Double.MAX_VALUE;
            for (int dz = -r; dz <= r; dz++) {
                for (int dx = -r; dx <= r; dx++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != r) continue;
                    Province p = province(cx + dx, cz + dz);
                    if (p.landform() != target) continue;
                    double d = Math.hypot(p.x() - x, p.z() - z);
                    if (d < bestD) {
                        bestD = d;
                        best = p;
                    }
                }
            }
            if (best != null) return best;
        }
        return null;
    }

    /**
     * Greedy colouring in priority order: a cell re-picks against the <i>final</i> landforms of
     * its higher-priority neighbours. The recursion only ever climbs in priority, so it always
     * ends, and the province cache memoises it. A cell's answer depends only on its
     * surroundings, never on which chunk asked first. (Judging neighbours by their initial picks
     * left ~21% identical neighbours, and three fixed rounds of correction still left ~10%.)
     */
    private Province resolve(int cx, int cz) {
        return new Province(cx, cz, centerX(cx, cz), centerZ(cx, cz), pick(cx, cz), GradientNoise.hash(seed, cx, 7, cz));
    }

    private Landform pick(int cx, int cz) {
        Initial self = initial(cx, cz);
        if (self.context().isOcean()) {
            return ocean;
        }
        Landform[] higher = new Landform[8];
        int count = 0;
        for (int dz = -1; dz <= 1; dz++) {
            for (int dx = -1; dx <= 1; dx++) {
                if ((dx != 0 || dz != 0) && initial(cx + dx, cz + dz).priority() > self.priority()) {
                    higher[count++] = province(cx + dx, cz + dz).landform();
                }
            }
        }
        double[] weights = new double[landforms.size()];
        double total = 0;
        for (int i = 0; i < weights.length; i++) {
            Landform candidate = landforms.get(i);
            double w = weigher.weight(candidate, self.context());
            for (int k = 0; k < count && w > 0; k++) {
                w *= affinity(candidate, higher[k]);
            }
            weights[i] = w;
            total += w;
        }
        return total > 0 ? choose(weights, total, unit(cx, cz, 2)) : fallback;
    }

    private Initial initial(int cx, int cz) {
        Caches c = caches.get();
        long key = key(cx, cz);
        int slot = (int) GradientNoise.mix(key) & (Caches.SIZE - 1);
        if (c.initialKeys[slot] == key && c.initials[slot] != null) {
            return c.initials[slot];
        }
        Initial init = new Initial(context.at(centerX(cx, cz), centerZ(cx, cz)), unit(cx, cz, 3));
        c.initialKeys[slot] = key;
        c.initials[slot] = init;
        return init;
    }

    private static double affinity(Landform candidate, Landform neighbour) {
        if (candidate == neighbour) {
            return candidate.sameNeighbour();
        }
        if (WONDER_PAIRS.contains(Set.of(candidate.id(), neighbour.id()))) {
            return 2.2;
        }
        return candidate.elevation() != neighbour.elevation() ? 1.3 : 1.0;
    }

    private Landform choose(double[] weights, double total, double u) {
        double target = u * total;
        for (int i = 0; i < weights.length; i++) {
            target -= weights[i];
            if (target < 0) return landforms.get(i);
        }
        return fallback;
    }

    private double centerX(int cx, int cz) {
        return (cx + 0.15 + 0.7 * unit(cx, cz, 10)) * cell;
    }

    private double centerZ(int cx, int cz) {
        return (cz + 0.15 + 0.7 * unit(cx, cz, 11)) * cell;
    }

    private double unit(int cx, int cz, int salt) {
        return (GradientNoise.hash(seed + salt * 0x9E3779B97F4A7C15L, cx, cz) >>> 11) * 0x1.0p-53;
    }

    private static final class Caches {
        static final int SIZE = 2048;
        final long[] provinceKeys = new long[SIZE];
        final Province[] provinces = new Province[SIZE];
        final long[] initialKeys = new long[SIZE];
        final Initial[] initials = new Initial[SIZE];
    }
}
