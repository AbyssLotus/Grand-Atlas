package com.seaweed.grandatlas.core;

import static com.seaweed.grandatlas.core.Maths.smoothstep;

import com.seaweed.grandatlas.core.noise.Fractal;
import com.seaweed.grandatlas.core.noise.GradientNoise;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * M7b (docs/M7_UNDERGROUND_PLAN.md): cave systems you can follow, from the surface to the abyss.
 * Negative values carve; evaluated per block, from a per-column list of what's near.
 *
 * <ul>
 *   <li><b>Passage networks.</b> A jittered 3D lattice of junctions (96 blocks apart, levels 44
 *       apart, so systems have stacked levels like real caves). Each junction drains to the
 *       neighbour lowest on a smooth "base level" field, the surface rivers' rule turned
 *       underground, so passages run in trees toward sinks; some add a loop to a neighbour, more
 *       in maze country. Links are steered random walks (vanilla's carver steering, aimed at the
 *       next junction), and each has its own width, height and floor and a cross-section:
 *       <b>tubes</b> (round, flat sediment floor), <b>canyons</b> (keyhole: a tube with a tall slot
 *       cut under it), <b>crawls</b> (low and tight), and steep links are <b>shafts</b>. Some
 *       junctions open into <b>chambers</b>. Whole systems come and go with a regional mask, and
 *       each region has its own mix of shapes, so it isn't uniform.</li>
 *   <li><b>Entrances.</b> Some junctions near the surface send a passage up to a mouth on a
 *       hillside, never near water and never below the sea.</li>
 *   <li><b>Worm bores</b> (fantastical, rare): perfectly round, ribbed tunnels 20-36 across that
 *       wind down from near the surface (sometimes opening in it) to the abyss: a random walk
 *       whose curl drifts, so they coil one way, unwind, wander and coil the other way, with
 *       level runs and steep plunges, swellings, and sometimes a fork.</li>
 *   <li><b>Rifts</b> (M7c): cracks 100-400 deep along a winding line, 2-8 wide at their widest,
 *       their centre wandering side to side as they go down. Some open in dry ground; under water
 *       they stay sealed below the cover. One in twelve is a <b>great rift</b>: 25-60 across,
 *       250-450 deep, a kilometre or more long, crossed by natural stone bridges.</li>
 *   <li>Where the randomness comes from: docs/M7_UNDERGROUND_PLAN.md, "where vanilla's caves get
 *       their chaos".</li>
 * </ul>
 *
 * <p>Nothing else carves within a cover of rock under the ground (10 blocks, 26 near water),
 * so passages never open a river bed or a lake floor; only entrance and bore-mouth segments may
 * break the surface.
 */
public final class Passages {
    static final double CELL = 96, LEVEL = 44;
    static final int K_MIN = (int) Math.ceil((AtlasCore.MIN_Y + 40) / LEVEL);
    static final int K_MAX = (int) Math.floor(520 / LEVEL);
    static final int LEVELS = K_MAX - K_MIN + 1;
    /** Below this a node can't come near any ground (which never goes under TERRAIN_FLOOR + 16). */
    private static final double NO_SURFACE_BELOW = AtlasCore.TERRAIN_FLOOR - 30;
    static final double BORE_CELL = 2800, RIFT_CELL = 1200;

    static final int TUBE = 0, CANYON = 1, CRAWL = 2, SHAFT = 3, BORE = 4, CHAMBER = 5, RIFT = 6, GREAT_RIFT = 7;

    private final AtlasCore core;
    private final long nodeSeed, maskSeed, potSeed, styleSeed, roughSeed, boreSeed, riftSeed;
    private final ThreadLocal<Caches> caches = ThreadLocal.withInitial(Caches::new);

    Passages(AtlasCore core, long seed) {
        this.core = core;
        nodeSeed = GradientNoise.fork(seed, 1);
        maskSeed = GradientNoise.fork(seed, 2);
        potSeed = GradientNoise.fork(seed, 3);
        styleSeed = GradientNoise.fork(seed, 4);
        roughSeed = GradientNoise.fork(seed, 5);
        boreSeed = GradientNoise.fork(seed, 6);
        riftSeed = GradientNoise.fork(seed, 7);
    }

    // ---- geometry

    /** One straight piece of a passage, a chamber (a == b), or a piece of a bore. */
    static final class Seg {
        double ax, ay, az, bx, by, bz;
        double ra, rb;      // radius at each end
        double vr;          // vertical radius / radius
        double slot;        // canyon slot depth (0 none)
        double slotW;       // canyon slot half width
        double slotOff;     // canyon slot's sideways offset from the track
        int kind;
        boolean open;       // may break the surface (entrances, bore mouths)
        double ribs;        // bore: arc length at a (for the rings)
        double minX, maxX, minY, maxY, minZ, maxZ;

        void bounds() {
            double r = Math.max(Math.max(ra, rb), slotW + Math.abs(slotOff)) + 2;
            minX = Math.min(ax, bx) - r;
            maxX = Math.max(ax, bx) + r;
            minZ = Math.min(az, bz) - r;
            maxZ = Math.max(az, bz) + r;
            minY = Math.min(ay, by) - Math.max(ra, rb) * vr - slot - 2;
            maxY = Math.max(ay, by) + Math.max(ra, rb) * vr + 2;
        }
    }

    /** The junctions of one 96-block cell, every level. */
    private static final class NodeCell {
        final boolean[] exists = new boolean[LEVELS];
        final double[] x = new double[LEVELS], y = new double[LEVELS], z = new double[LEVELS];
        final long[] hash = new long[LEVELS];
    }

    private static final class Caches {
        final HashMap<Long, NodeCell> nodes = new HashMap<>();
        final HashMap<Long, List<Seg>> edges = new HashMap<>();
        final HashMap<Long, List<Seg>> bores = new HashMap<>();
        final HashMap<Long, List<Seg>> rifts = new HashMap<>();
        // the last chunk's candidate segments
        long chunkKey = Long.MIN_VALUE;
        final ArrayList<Seg> chunk = new ArrayList<>();
        // the chunk's pieces sorted into 4x4-block bins, so a column only scans its own bin
        @SuppressWarnings("unchecked")
        final ArrayList<Seg>[] bins = new ArrayList[16];
        {
            for (int b = 0; b < 16; b++) bins[b] = new ArrayList<>();
        }
        // the last column's candidates
        int cx = Integer.MIN_VALUE, cz = Integer.MIN_VALUE;
        Seg[] col = new Seg[32];
        int colCount;
        double colLo, colHi;
        double lakeLevel, lakeBed; // a cavern lake over this column (NaN level: none)
        // which of the column's first 64 candidates reach each 16-block band of height
        final long[] ybins = new long[(1024 - AtlasCore.MIN_Y) / 16 + 1];

        void trim() {
            if (nodes.size() > 6000) nodes.clear();
            if (edges.size() > 160) edges.clear(); // several hundred pieces a cell: a few MB a thread
            if (bores.size() > 64) bores.clear();
            if (rifts.size() > 128) rifts.clear();
        }
    }

    private static long key(int i, int j) {
        return ((long) i << 32) ^ (j & 0xFFFFFFFFL);
    }

    // ---- junctions

    private NodeCell nodeCell(Caches cc, int i, int j) {
        long key = key(i, j);
        NodeCell cell = cc.nodes.get(key);
        if (cell != null) return cell;
        cell = new NodeCell();
        for (int l = 0; l < LEVELS; l++) {
            int k = K_MIN + l;
            long h = GradientNoise.hash(nodeSeed, i, k, j);
            double x = (i + 0.15 + 0.7 * unit(h, 0)) * CELL;
            double z = (j + 0.15 + 0.7 * unit(h, 1)) * CELL;
            // Levels rise and fall by region (up to ~18) instead of sitting at fixed heights.
            double y = k * LEVEL + (unit(h, 2) - 0.5) * 22 + 18 * GradientNoise.noise2(GradientNoise.fork(potSeed, 9), x / 1300.0, z / 1300.0);
            cell.hash[l] = h;
            cell.x[l] = x;
            cell.y[l] = y;
            cell.z[l] = z;
            if (unit(h, 3) >= presence(x, y, z, k)) continue;
            if (y > NO_SURFACE_BELOW) {
                ColumnSample c = core.column((int) Math.floor(x), (int) Math.floor(z));
                double cover = c.isOcean() ? 40 : c.nearWater() ? 34 : 26;
                if (y > c.height() - cover) continue;
            }
            cell.exists[l] = true;
        }
        cc.nodes.put(key, cell);
        return cell;
    }

    /** How likely a junction is here: systems come and go by region, and thin with depth and height. */
    private double presence(double x, double y, double z, int k) {
        double m = 0.62 * ofbm01(maskSeed, x / 1400.0, z / 1400.0)
                + 0.38 * ofbm01(GradientNoise.fork(maskSeed, k), x / 700.0, z / 700.0);
        double band = y > 160 ? 0.45 : y > 0 ? 0.95 : y > AtlasCore.ABYSS_TOP ? 0.8 : 0.6;
        return smoothstep(0.36, 0.6, m) * band;
    }

    /** The base level passages drain toward: height plus a broad smooth field. */
    private double potential(double x, double y, double z) {
        return y + 90 * GradientNoise.noise2(potSeed, x / 900.0, z / 900.0);
    }

    /** Shape mix of a region: 0 phreatic (tubes), 1 vadose (canyons); and how mazy. */
    private double style(double x, double z) {
        return ofbm01(styleSeed, x / 1100.0, z / 1100.0);
    }

    private double maze(double x, double z) {
        return ofbm01(styleSeed ^ 0x55L, x / 1300.0, z / 1300.0);
    }

    // ---- links (owned by the junction they leave)

    private List<Seg> edgeCell(Caches cc, int i, int j) {
        long key = key(i, j);
        List<Seg> list = cc.edges.get(key);
        if (list != null) return list;
        list = new ArrayList<>();
        NodeCell here = nodeCell(cc, i, j);
        NodeCell[][] around = new NodeCell[3][3];
        for (int dj = -1; dj <= 1; dj++) for (int di = -1; di <= 1; di++) around[di + 1][dj + 1] = nodeCell(cc, i + di, j + dj);
        for (int l = 0; l < LEVELS; l++) {
            if (!here.exists[l]) continue;
            double ax = here.x[l], ay = here.y[l], az = here.z[l];
            long h = here.hash[l];
            double pa = potential(ax, ay, az);
            // Drain: the lowest neighbour on the base-level field, the level below costing extra so
            // most passages run along a level and some drop to the next.
            double best = -2;
            int bi = 0, bj = 0, bl = -1;
            for (int dl = 0; dl >= -1; dl--) {
                int nl = l + dl;
                if (nl < 0) continue;
                for (int dj = -1; dj <= 1; dj++) {
                    for (int di = -1; di <= 1; di++) {
                        if (di == 0 && dj == 0 && dl == 0) continue;
                        NodeCell n = around[di + 1][dj + 1];
                        if (!n.exists[nl]) continue;
                        double cost = potential(n.x[nl], n.y[nl], n.z[nl]) - pa + (dl < 0 ? 38 : 0)
                                + 6 * (unit(h ^ (di * 31L + dj * 7L + dl), 7) - 0.5);
                        if (cost < best) {
                            best = cost;
                            bi = di;
                            bj = dj;
                            bl = nl;
                        }
                    }
                }
            }
            if (bl >= 0) {
                NodeCell n = around[bi + 1][bj + 1];
                link(list, ax, ay, az, n.x[bl], n.y[bl], n.z[bl], h, false);
            }
            // Loops: a second link sideways, more in maze country. Only from the lower key, so a
            // pair that both pick each other makes one passage, not two.
            double loopChance = 0.12 + 0.4 * smoothstep(0.45, 0.8, maze(ax, az));
            if (unit(h, 10) < loopChance) {
                int di = (int) Math.floor(unit(h, 11) * 3) - 1, dj = (int) Math.floor(unit(h, 12) * 3) - 1;
                if ((di != 0 || dj != 0) && !(di == bi && dj == bj && bl == l)) {
                    NodeCell n = around[di + 1][dj + 1];
                    if (n.exists[l] && key(i, j) < key(i + di, j + dj)) {
                        link(list, ax, ay, az, n.x[l], n.y[l], n.z[l], h ^ 0x4C00L, false);
                    }
                }
            }
            // Chambers: rooms where passages meet, bigger deeper down, squashed by their own amount
            // (vanilla's rooms: 0.1-0.9), some made of two or three overlapping blobs.
            if (unit(h, 13) < 0.2) {
                double r = (6 + 9 * unit(h, 14)) * (ay < AtlasCore.ABYSS_TOP ? 1.5 : ay < 0 ? 1.25 : 1);
                int blobs = unit(h, 18) < 0.4 ? 2 + (int) (unit(h, 19) * 2) : 1;
                for (int b = 0; b < blobs; b++) {
                    double rb = b == 0 ? r : r * (0.45 + 0.4 * unit(h, 40 + b));
                    double ang = unit(h, 44 + b) * 2 * Math.PI, off = b == 0 ? 0 : r * (0.5 + 0.4 * unit(h, 48 + b));
                    Seg s = new Seg();
                    s.ax = s.bx = ax + Math.cos(ang) * off;
                    s.ay = s.by = ay + rb * 0.25 + (b == 0 ? 0 : (unit(h, 52 + b) - 0.5) * r * 0.5);
                    s.az = s.bz = az + Math.sin(ang) * off;
                    s.ra = s.rb = rb;
                    s.vr = 0.35 + 0.45 * unit(h, 15 + b * 3);
                    s.kind = CHAMBER;
                    s.bounds();
                    list.add(s);
                }
            }
            // Dead ends: a spur that wanders off and tapers to nothing, like vanilla's tunnels.
            if (unit(h, 31) < 0.3) {
                SplittableRandomish r = new SplittableRandomish(h ^ 0x5B0BL);
                double len = 25 + 70 * r.next();
                walk(list, ax, ay, az, Double.NaN, 0, 0, len, h ^ 0x5B0BL, false, true);
            }
            // Entrances: the top junction of a column of levels, if near enough to the ground.
            if (ay > NO_SURFACE_BELOW && (l + 1 >= LEVELS || !here.exists[l + 1]) && unit(h, 16) < 0.3) {
                entrance(list, ax, ay, az, h);
            }
        }
        cc.edges.put(key, list);
        return list;
    }

    /** A passage from a to b. */
    private void link(List<Seg> out, double ax, double ay, double az, double bx, double by, double bz, long h, boolean open) {
        walk(out, ax, ay, az, bx, by, bz, 0, h, open, false);
    }

    /**
     * A passage as a steered random walk (vanilla's carver: the turning speed takes random kicks
     * and decays, so curves are smooth and never repeat). Aimed at (bx, by, bz) it wanders early
     * and homes in late, ending exactly there; with bx = NaN it is a free spur of length
     * {@code freeLen} that tapers to nothing at its end.
     */
    private void walk(List<Seg> out, double ax, double ay, double az, double bx, double by, double bz,
                      double freeLen, long h, boolean open, boolean spur) {
        boolean aimed = !Double.isNaN(bx);
        double dx = aimed ? bx - ax : 0, dy = aimed ? by - ay : 0, dz = aimed ? bz - az : 0;
        double hl = Math.hypot(dx, dz), len = aimed ? Math.sqrt(hl * hl + dy * dy) : freeLen;
        if (len < 1) return;
        SplittableRandomish rnd = new SplittableRandomish(h);
        double depth = aimed ? (ay + by) / 2 : ay;
        double st = style(ax, az);
        int kind;
        double steep = aimed ? Math.abs(dy) / Math.max(1, hl) : 0;
        if (steep > 0.9) {
            kind = SHAFT;
        } else if (rnd.next() < 0.12) {
            kind = CRAWL;
        } else {
            kind = rnd.next() < 0.08 + 0.35 * st ? CANYON : TUBE;
        }
        double base = depth < AtlasCore.ABYSS_TOP ? 4.5 + 4 * rnd.next() : depth < 0 ? 3.2 + 3.5 * rnd.next() : 2.4 + 2.8 * rnd.next();
        if (rnd.next() < 0.1) base *= 1 + 2 * rnd.next() * rnd.next(); // vanilla's "weird thickness": the odd fat one
        if (kind == CRAWL) base = 1.7 + 0.8 * rnd.next();
        if (kind == SHAFT) base = Math.max(2.5, base * 0.9);
        if (spur) base *= 0.8;
        double width = 0.75 + 0.55 * rnd.next();          // per passage, like vanilla's multipliers
        double vr = kind == CRAWL ? 0.62 : (0.55 + 0.4 * rnd.next()) * (kind == CANYON ? 1.1 : 1);
        double floor = 0.45 + 0.35 * rnd.next();            // how far down the flat floor sits
        long noise = GradientNoise.fork(h, 77);
        // A bulge or two: a passage swells into a small room somewhere along it.
        double bulgeAt = rnd.next() < 0.35 ? rnd.next() * len : -1e9, bulge = 0.5 + 0.9 * rnd.next();
        double slotDepth0 = 0.6 + 1.4 * rnd.next();
        double step = kind == SHAFT ? 6 : 5;
        double yaw = aimed ? Math.atan2(dz, dx) + (rnd.next() - 0.5) * 1.4 : rnd.next() * 2 * Math.PI;
        double pitch = aimed ? Math.atan2(dy, Math.max(1, hl)) : (rnd.next() - 0.5) * 0.5;
        double yawV = 0, pitchV = 0;
        double x = ax, y = ay, z = az, travelled = 0, lastR = -1;
        int maxSteps = (int) (len / step * 3) + 4;
        for (int k = 0; k < maxSteps; k++) {
            double nx, ny, nz;
            boolean last = false;
            if (aimed) {
                double rx = bx - x, ry = by - y, rz = bz - z, rd = Math.sqrt(rx * rx + ry * ry + rz * rz);
                if (rd < step * 1.5 || k == maxSteps - 1) {
                    nx = bx;
                    ny = by;
                    nz = bz;
                    last = true;
                } else {
                    // Wander, then home in: the pull toward the target grows along the way.
                    double pull = 0.12 + 0.88 * Math.min(1, travelled / (len * 1.15));
                    yawV = yawV * 0.75 + (rnd.next() - rnd.next()) * rnd.next() * 1.6;
                    pitchV = pitchV * 0.9 + (rnd.next() - rnd.next()) * rnd.next() * 0.5;
                    yaw += yawV * 0.25;
                    pitch += pitchV * 0.1;
                    yaw += angleTo(Math.atan2(rz, rx), yaw) * pull * 0.45;
                    pitch += (Math.atan2(ry, Math.max(1, Math.hypot(rx, rz))) - pitch) * (kind == SHAFT ? 0.8 : pull * 0.5);
                    nx = x + Math.cos(yaw) * Math.cos(pitch) * step;
                    nz = z + Math.sin(yaw) * Math.cos(pitch) * step;
                    ny = y + Math.sin(pitch) * step;
                }
            } else {
                if (travelled >= len) break;
                yawV = yawV * 0.75 + (rnd.next() - rnd.next()) * rnd.next() * 1.6;
                pitchV = pitchV * 0.9 + (rnd.next() - rnd.next()) * rnd.next() * 0.5;
                yaw += yawV * 0.25;
                pitch = pitch * 0.7 + pitchV * 0.1; // vanilla: pitch decays toward level
                nx = x + Math.cos(yaw) * Math.cos(pitch) * step;
                nz = z + Math.sin(yaw) * Math.cos(pitch) * step;
                ny = y + Math.sin(pitch) * step;
            }
            double seg = Math.sqrt((nx - x) * (nx - x) + (ny - y) * (ny - y) + (nz - z) * (nz - z));
            travelled += seg;
            // Width along the way: smooth noise, the bulge, and for spurs vanilla's taper to nothing.
            double along = 0.75 + 0.5 * (GradientNoise.noise2(noise, travelled / 34.0, 0.5) * 0.5 + 0.5);
            double bump = 1 + bulge * Math.exp(-sq((travelled - bulgeAt) / 10));
            double taper = spur ? Math.max(0.25, Math.sin(Math.PI * Math.min(1, travelled / len) * 0.5 + Math.PI * 0.5)) : 1;
            double r = base * width * along * bump * taper;
            if (open && last) r *= 1.15;
            Seg s = new Seg();
            s.ax = x;
            s.ay = y;
            s.az = z;
            s.bx = nx;
            s.by = ny;
            s.bz = nz;
            s.ra = lastR < 0 ? r : lastR;
            s.rb = r;
            s.vr = vr;
            s.kind = kind;
            s.open = open;
            s.ribs = floor; // non-bore pieces keep their floor level here
            if (kind == CANYON) {
                // A V-shaped trench under the tube whose depth and side wander along the passage.
                double dn = GradientNoise.noise2(noise ^ 0x51L, travelled / 45.0, 1.5) * 0.5 + 0.5;
                s.slot = Math.min(16, r * slotDepth0 * (0.25 + 1.5 * dn));
                s.slotW = Math.max(1.1, r * (0.45 + 0.25 * dn));
                s.slotOff = r * 0.45 * GradientNoise.noise2(noise ^ 0x53L, travelled / 28.0, 2.5);
            }
            s.bounds();
            out.add(s);
            x = nx;
            y = ny;
            z = nz;
            lastR = r;
            if (last) break;
        }
    }

    private static double angleTo(double target, double from) {
        double d = target - from;
        while (d > Math.PI) d -= 2 * Math.PI;
        while (d < -Math.PI) d += 2 * Math.PI;
        return d;
    }

    private static double sq(double v) {
        return v * v;
    }

    /** A tiny deterministic generator (SplitMix64), so a passage's walk is the same in every thread. */
    private static final class SplittableRandomish {
        private long state;

        SplittableRandomish(long seed) {
            state = seed;
        }

        double next() {
            state += 0x9E3779B97F4A7C15L;
            return (GradientNoise.mix(state) >>> 11) * 0x1.0p-53;
        }
    }

    /**
     * A passage rising from a junction to a mouth on a hillside: follows a ray out from the
     * junction, climbing ~1 in 3, until it meets the ground. None if it never does within ~90
     * blocks, or if the mouth would be under the sea level, near water, or on flat open ground.
     */
    private void entrance(List<Seg> out, double ax, double ay, double az, long h) {
        double ang = unit(h, 17) * 2 * Math.PI, ux = Math.cos(ang), uz = Math.sin(ang);
        for (double d = 16; d <= 96; d += 8) {
            double x = ax + ux * d, z = az + uz * d, y = ay + d * 0.34;
            ColumnSample c = core.column((int) Math.floor(x), (int) Math.floor(z));
            if (y < c.height() - 2) continue;
            if (c.isOcean() || c.nearWater() || c.height() < AtlasCore.SEA_LEVEL + 4) return;
            // A hillside: the ground must fall away somewhere near, or the mouth is just a hole.
            ColumnSample f = core.column((int) Math.floor(x + ux * 10), (int) Math.floor(z + uz * 10));
            if (c.height() - f.height() < 3) return;
            link(out, ax, ay, az, x, c.height() - 1, z, h ^ 0xE47L, true);
            return;
        }
    }

    // ---- worm bores

    private List<Seg> boreCell(Caches cc, int i, int j) {
        long key = key(i, j);
        List<Seg> list = cc.bores.get(key);
        if (list != null) return list;
        list = new ArrayList<>();
        cc.bores.put(key, list);
        long h = GradientNoise.hash(boreSeed, i, j);
        if (unit(h, 0) > 0.4) return list;
        double x0 = (i + 0.2 + 0.6 * unit(h, 1)) * BORE_CELL, z0 = (j + 0.2 + 0.6 * unit(h, 2)) * BORE_CELL;
        ColumnSample top = core.column((int) Math.floor(x0), (int) Math.floor(z0));
        double r = 10 + 8 * unit(h, 5);
        boolean mouth = unit(h, 6) < 0.4 && !top.isOcean() && top.height() > AtlasCore.SEA_LEVEL + 10 && top.height() < 420
                && dryAround(x0, z0, 160);
        double y = mouth ? top.height() + 6 : Math.min(top.height() - 50, 30);
        double floor = AtlasCore.MIN_Y + 45 + 30 * unit(h, 7);
        if (y < floor + 100) return list;
        SplittableRandomish rnd = new SplittableRandomish(h ^ 0xB0BEL);
        double forkAt = rnd.next() < 0.35 ? 300 + 900 * rnd.next() : -1;
        boreWalk(list, x0, y, z0, rnd.next() * 2 * Math.PI, r, floor, mouth ? top.height() - 45 : Double.MAX_VALUE, forkAt, x0, z0, h, 0);
        return list;
    }

    /**
     * One bore (or a fork of one): a random walk whose curl drifts, so it coils one way for a
     * while, unwinds, wanders, and may coil the other way; its descent swings between near-level
     * runs and steep plunges. A soft leash keeps it within ~1.5 km of where it started, so every
     * chunk it reaches looks it up.
     */
    private void boreWalk(List<Seg> out, double x, double y, double z, double yaw, double r, double floor, double openAbove,
                          double forkAt, double hx, double hz, long h, double arc0) {
        SplittableRandomish rnd = new SplittableRandomish(h ^ 0xC011L ^ (long) arc0);
        long curl = GradientNoise.fork(h, 91), dive = GradientNoise.fork(h, 92), girth = GradientNoise.fork(h, 93);
        double ds = 8, yawV = 0, arc = arc0;
        double swellAt = rnd.next() < 0.5 ? arc0 + 200 + 1200 * rnd.next() : -1e9;
        boolean forked = forkAt < 0;
        for (int k = 0; k < 1200 && y > floor; k++) {
            // The curl: a slow noise from hard left to hard right through straight.
            double c = GradientNoise.noise2(curl, arc / 520.0, 0.5);
            double coil = Math.signum(c) * smoothstep(0.15, 0.6, Math.abs(c)) * 0.09;
            yawV = yawV * 0.8 + (rnd.next() - rnd.next()) * rnd.next() * 0.12;
            yaw += coil + yawV;
            double away = Math.hypot(x - hx, z - hz);
            if (away > 1500) yaw += angleTo(Math.atan2(hz - z, hx - x), yaw) * 0.08;
            // Descent: mostly gentle, sometimes level, sometimes a plunge.
            double d = GradientNoise.noise2(dive, arc / 380.0, 1.5) * 0.5 + 0.5;
            double grade = 0.02 + 0.22 * d + 0.5 * smoothstep(0.8, 0.95, d);
            double nx = x + Math.cos(yaw) * ds, nz = z + Math.sin(yaw) * ds, ny = y - grade * ds;
            double rr = r * (0.85 + 0.3 * (GradientNoise.noise2(girth, arc / 260.0, 2.5) * 0.5 + 0.5))
                    * (1 + 0.7 * Math.exp(-sq((arc - swellAt) / 40)));
            Seg s = new Seg();
            s.ax = x;
            s.ay = y;
            s.az = z;
            s.bx = nx;
            s.by = ny;
            s.bz = nz;
            s.ra = s.rb = rr;
            s.vr = 1;
            s.kind = BORE;
            s.ribs = arc;
            s.open = y > openAbove;
            s.bounds();
            out.add(s);
            arc += Math.hypot(nx - x, nz - z);
            x = nx;
            y = ny;
            z = nz;
            if (!forked && arc >= forkAt) {
                forked = true;
                double side = rnd.next() < 0.5 ? 1 : -1;
                boreWalk(out, x, y, z, yaw + side * (0.6 + 0.6 * rnd.next()), r * (0.6 + 0.25 * rnd.next()), floor + 60 * rnd.next(),
                        Double.MAX_VALUE, -1, hx, hz, h ^ 0xF0F0L, arc);
            }
        }
    }

    // ---- rifts (M7c)

    private List<Seg> riftCell(Caches cc, int i, int j) {
        long key = key(i, j);
        List<Seg> list = cc.rifts.get(key);
        if (list != null) return list;
        list = new ArrayList<>();
        cc.rifts.put(key, list);
        long h = GradientNoise.hash(riftSeed, i, j);
        if (unit(h, 0) > 0.3) return list;
        SplittableRandomish rnd = new SplittableRandomish(h);
        boolean great = rnd.next() < 1 / 12.0;
        double x = (i + 0.2 + 0.6 * rnd.next()) * RIFT_CELL, z = (j + 0.2 + 0.6 * rnd.next()) * RIFT_CELL;
        double len = great ? 900 + 400 * rnd.next() : 300 + 600 * rnd.next(); // within the 3x3 cells a chunk looks up
        double width = great ? 12 + 18 * rnd.next() : 1 + 3 * rnd.next();
        double depth = great ? 250 + 200 * rnd.next() : 100 + 300 * rnd.next();
        boolean wantsOpen = great ? rnd.next() < 0.85 : rnd.next() < 0.35;
        // A buried rift's top sits somewhere in the upper bands; an open one follows the ground.
        double buriedTop = 40 - 300 * rnd.next();
        long wn = GradientNoise.fork(h, 3);
        double yaw = rnd.next() * 2 * Math.PI, yawV = 0, arc = 0, ds = 8;
        double lx = x, lz = z, lTop = Double.NaN, lr = 0;
        boolean lOpen = false;
        int n = (int) (len / ds);
        for (int k = 0; k <= n; k++) {
            if (k > 0) {
                // Cracks run straighter than caves: a weak random walk with a slow drift.
                yawV = yawV * 0.85 + (rnd.next() - rnd.next()) * rnd.next() * 0.18;
                yaw += yawV + 0.02 * GradientNoise.noise2(wn, arc / 300.0, 0.5);
                x += Math.cos(yaw) * ds;
                z += Math.sin(yaw) * ds;
                arc += ds;
            }
            ColumnSample c = core.column((int) Math.floor(x), (int) Math.floor(z));
            boolean dry = !c.isOcean() && !c.nearWater() && c.height() > AtlasCore.SEA_LEVEL + 3;
            boolean open = wantsOpen && dry;
            double top = open ? c.height() + 3 : Math.min(buriedTop, c.height() - (c.isOcean() || c.nearWater() ? 30 : 14));
            // Width along: widest in the middle, tapering to the ends, wobbling on the way.
            double t = (double) k / n;
            double r = width * Math.pow(Math.sin(Math.PI * Math.max(0.02, Math.min(0.98, t))), 0.6)
                    * (0.7 + 0.6 * (GradientNoise.noise2(GradientNoise.fork(h, 4), arc / 90.0, 0.5) * 0.5 + 0.5));
            if (k > 0) {
                Seg s = new Seg();
                s.ax = lx;
                s.az = lz;
                s.bx = x;
                s.bz = z;
                s.ay = lTop;
                s.by = top;
                s.slot = depth;
                s.ra = lr;
                s.rb = r;
                s.vr = 1;
                s.kind = great ? GREAT_RIFT : RIFT;
                s.open = lOpen && open;
                s.ribs = arc - ds;
                s.slotOff = GradientNoise.fork(h, 5); // the seed of its wander, as a double
                s.slotW = r * 0.9 + 4;              // how far the centre may wander sideways
                // Bounds by hand: the crack hangs below its top.
                double reach = Math.max(lr, r) + s.slotW + 2;
                s.minX = Math.min(lx, x) - reach;
                s.maxX = Math.max(lx, x) + reach;
                s.minZ = Math.min(lz, z) - reach;
                s.maxZ = Math.max(lz, z) + reach;
                s.maxY = Math.max(lTop, top) + 2;
                s.minY = Math.min(lTop, top) - depth - 2;
                list.add(s);
            }
            lx = x;
            lz = z;
            lTop = top;
            lr = r;
            lOpen = open;
        }
        return list;
    }

    private boolean dryAround(double x, double z, double reach) {
        for (int k = 0; k < 12; k++) {
            double a = k * Math.PI / 6;
            for (double d = 0; d <= reach; d += reach / 3) {
                ColumnSample c = core.column((int) Math.floor(x + Math.cos(a) * d), (int) Math.floor(z + Math.sin(a) * d));
                if (c.nearWater() || c.isOcean()) return false;
            }
        }
        return true;
    }

    // ---- per block

    /** The passages at a block: negative where they carve. {@code c} is this column's sample. */
    public double carve(ColumnSample c, int x, int y, int z) {
        Caches cc = caches.get();
        if (x != cc.cx || z != cc.cz) column(cc, x, z);
        if (cc.colCount == 0 || y < cc.colLo || y > cc.colHi) return Caves.NONE;
        // No passage through a cavern lake's bed (8 blocks of it kept) or its water.
        if (!Double.isNaN(cc.lakeLevel) && y >= cc.lakeBed - 8 && y <= cc.lakeLevel + 1) return Caves.NONE;
        double cover = c.height() - y;
        boolean shallow = cover < (c.nearWater() || c.isOcean() ? 26 : 10);
        double best = Caves.NONE;
        double bx = x + 0.5, by = y + 0.5, bz = z + 0.5;
        boolean indexed = cc.colCount <= 64;
        long mask = indexed ? cc.ybins[Math.max(0, Math.min(cc.ybins.length - 1, (y - AtlasCore.MIN_Y) >> 4))] : 0;
        for (int i = indexed ? Long.numberOfTrailingZeros(mask) : 0; indexed ? mask != 0 : i < cc.colCount;
                i = indexed ? Long.numberOfTrailingZeros(mask) : i + 1) {
            if (indexed) mask &= mask - 1; // this candidate taken; the loop moves to the next set bit
            Seg s = cc.col[i];
            if (by < s.minY || by > s.maxY) continue;
            if (shallow && !s.open) continue;
            double q = shape(s, bx, by, bz);
            if (q < 1.4) {
                double rough = s.kind == BORE ? 0.03 : s.kind == RIFT || s.kind == GREAT_RIFT ? 0.06 : 0.13;
                q += rough * GradientNoise.noise3(roughSeed, bx / 5.0, by / 4.0, bz / 5.0);
                double rmin = s.kind == RIFT || s.kind == GREAT_RIFT ? Math.max(0.8, Math.min(4, Math.max(s.ra, s.rb) * 0.5))
                        : Math.max(1.2, Math.min(s.ra, s.rb) * Math.min(1, s.vr));
                best = Math.min(best, (q - 1) * rmin * 0.5);
            }
        }
        return best;
    }

    /** Normalised distance from a segment's surface (1 on it, under 1 inside). */
    private static double shape(Seg s, double x, double y, double z) {
        double ex = s.bx - s.ax, ey = s.by - s.ay, ez = s.bz - s.az;
        double l2 = ex * ex + ey * ey + ez * ez;
        double t = l2 < 1e-6 ? 0 : Math.max(0, Math.min(1, ((x - s.ax) * ex + (y - s.ay) * ey + (z - s.az) * ez) / l2));
        double ox = x - (s.ax + ex * t), oy = y - (s.ay + ey * t), oz = z - (s.az + ez * t);
        double r = s.ra + (s.rb - s.ra) * t;
        switch (s.kind) {
            case BORE -> {
                // Round, with faint rings like a worm's segments.
                double arc = s.ribs + t * Math.sqrt(l2);
                double rr = r * (1 + 0.05 * Math.sin(arc / 2.3));
                return Math.sqrt(ox * ox + oy * oy + oz * oz) / rr;
            }
            case RIFT, GREAT_RIFT -> {
                double hx = s.bx - s.ax, hz = s.bz - s.az, h2 = hx * hx + hz * hz;
                double th = h2 < 1e-6 ? 0 : Math.max(0, Math.min(1, ((x - s.ax) * hx + (z - s.az) * hz) / h2));
                double cx = x - (s.ax + hx * th), cz = z - (s.az + hz * th);
                double sd = (h2 < 1e-6 ? 1 : Math.signum(hx * cz - hz * cx)) * Math.sqrt(cx * cx + cz * cz);
                double top = s.ay + (s.by - s.ay) * th, bot = top - s.slot;
                if (y > top || y < bot) return 2;
                double u = (y - bot) / s.slot;
                // A lens: pinched at the bottom, widest high up; an open crack stays wide to the top.
                double prof = s.open ? Math.pow(Math.min(1, u * 1.6), 0.45) : Math.pow(Math.sin(Math.PI * u), 0.5);
                double hw = Math.max(0.6, (s.ra + (s.rb - s.ra) * th) * prof);
                // The centre wanders as the crack goes down: broad swings and a jagged edge.
                long w = (long) s.slotOff;
                double arc = s.ribs + th * Math.sqrt(h2);
                double off = (s.slotW - 4) * GradientNoise.noise2(w, arc / 60.0, y / 38.0)
                        + 1.6 * GradientNoise.noise2(w ^ 0x77L, arc / 9.0, y / 7.0);
                double q = Math.abs(sd - off) / hw;
                if (s.kind == GREAT_RIFT) {
                    // Natural bridges: where a slow noise along the rift peaks, a slab of rock spans
                    // it, thicker where it meets the walls (an arch's underside).
                    double b = GradientNoise.noise2(w ^ 0xB81DL, arc / 110.0, 0.5);
                    if (b > 0.45) {
                        double by = top - 25 - 120 * (GradientNoise.noise2(w ^ 0xB82DL, arc / 400.0, 1.5) * 0.5 + 0.5);
                        double thick = 3 + 5 * Math.min(1, q * q);
                        if (Math.abs(y - by) < thick * smoothstep(0.45, 0.6, b)) return 2;
                    }
                }
                return q;
            }
            case SHAFT -> {
                return Math.sqrt(ox * ox + oy * oy + oz * oz) / r;
            }
            default -> {
                // Cross-sections stand in a vertical plane: across from the track (horizontally)
                // and up or down from the axis at that spot. Measured from the nearest point in 3D
                // instead, a sloping passage's slot reached straight down only at each piece's
                // ends, which hung a row of drips under every canyon (the user's 3D survey).
                double hx = s.bx - s.ax, hz = s.bz - s.az, h2 = hx * hx + hz * hz;
                double th = h2 < 1e-6 ? 0 : Math.max(0, Math.min(1, ((x - s.ax) * hx + (z - s.az) * hz) / h2));
                double cx = x - (s.ax + hx * th), cz = z - (s.az + hz * th);
                // Which side of the track (for the canyon's wandering trench).
                double side = h2 < 1e-6 ? 1 : Math.signum(hx * cz - hz * cx);
                oy = y - (s.ay + ey * th);
                r = s.ra + (s.rb - s.ra) * th;
                double rv = r * s.vr;
                double h = Math.sqrt(cx * cx + cz * cz);
                double tube = Math.hypot(h / r, oy / rv);
                // A flat floor of fill: caves are rarely round underfoot. Each passage has its own.
                double floor = s.kind == CHAMBER ? 0.55 : s.ribs > 0 ? s.ribs : 0.6;
                double q = Math.max(tube, -oy / (rv * floor));
                if (s.slot > 0 && oy < 0) {
                    // The trench: a V below the floor, narrowing as it deepens, off to one side.
                    double bottom = rv * floor + s.slot;
                    double dn = Math.min(1, -oy / bottom);
                    double across = Math.abs(side * h - s.slotOff) / (s.slotW * (1 - 0.65 * dn) + 0.4);
                    q = Math.min(q, Math.max(across, -oy / bottom));
                }
                return q;
            }
        }
    }

    /** Fill the column's candidate list from the chunk's. */
    private void column(Caches cc, int x, int z) {
        long ck = key(x >> 4, z >> 4);
        if (cc.chunkKey != ck) chunk(cc, x >> 4, z >> 4);
        cc.cx = x;
        cc.cz = z;
        cc.colCount = 0;
        cc.lakeLevel = core.megaCaverns().water(x, z);
        cc.lakeBed = Double.isNaN(cc.lakeLevel) ? 0 : core.megaCaverns().floor(x, z);
        cc.colLo = Double.MAX_VALUE;
        cc.colHi = -Double.MAX_VALUE;
        double px = x + 0.5, pz = z + 0.5;
        for (Seg s : cc.bins[((z & 15) >> 2) * 4 + ((x & 15) >> 2)]) {
            if (px < s.minX || px > s.maxX || pz < s.minZ || pz > s.maxZ) continue;
            double reach = Math.max(s.ra, s.rb) + 1.5;
            if (s.kind == RIFT || s.kind == GREAT_RIFT) reach = Math.max(s.ra, s.rb) + s.slotW + 1.5;
            else if (s.slot > 0) reach = Math.max(reach, s.slotW + Math.abs(s.slotOff) + 1.5);
            if (dist2(s, px, pz) > reach * reach) continue;
            if (cc.colCount == cc.col.length) cc.col = java.util.Arrays.copyOf(cc.col, cc.col.length * 2);
            cc.col[cc.colCount++] = s;
            cc.colLo = Math.min(cc.colLo, s.minY);
            cc.colHi = Math.max(cc.colHi, s.maxY);
        }
        java.util.Arrays.fill(cc.ybins, 0L);
        for (int i = 0; i < Math.min(64, cc.colCount); i++) {
            Seg s = cc.col[i];
            int b0 = Math.max(0, (int) Math.floor((s.minY - AtlasCore.MIN_Y) / 16));
            int b1 = Math.min(cc.ybins.length - 1, (int) Math.floor((s.maxY - AtlasCore.MIN_Y) / 16));
            for (int b = b0; b <= b1; b++) cc.ybins[b] |= 1L << i;
        }
    }

    /** Squared horizontal distance from a point to a segment's track. */
    private static double dist2(Seg s, double x, double z) {
        double ex = s.bx - s.ax, ez = s.bz - s.az, l2 = ex * ex + ez * ez;
        double t = l2 < 1e-6 ? 0 : Math.max(0, Math.min(1, ((x - s.ax) * ex + (z - s.az) * ez) / l2));
        double dx = x - (s.ax + ex * t), dz = z - (s.az + ez * t);
        return dx * dx + dz * dz;
    }

    private void chunk(Caches cc, int chx, int chz) {
        cc.trim();
        cc.chunkKey = key(chx, chz);
        cc.chunk.clear();
        double x0 = chx * 16, z0 = chz * 16, x1 = x0 + 16, z1 = z0 + 16;
        int i0 = GradientNoise.floor(x0 / CELL), j0 = GradientNoise.floor(z0 / CELL);
        for (int j = j0 - 2; j <= j0 + 2; j++) {
            for (int i = i0 - 2; i <= i0 + 2; i++) {
                for (Seg s : edgeCell(cc, i, j)) {
                    if (s.maxX >= x0 && s.minX <= x1 && s.maxZ >= z0 && s.minZ <= z1) cc.chunk.add(s);
                }
            }
        }
        int ri = GradientNoise.floor(x0 / RIFT_CELL), rj = GradientNoise.floor(z0 / RIFT_CELL);
        for (int j = rj - 1; j <= rj + 1; j++) {
            for (int i = ri - 1; i <= ri + 1; i++) {
                for (Seg s : riftCell(cc, i, j)) {
                    if (s.maxX >= x0 && s.minX <= x1 && s.maxZ >= z0 && s.minZ <= z1) cc.chunk.add(s);
                }
            }
        }
        int bi = GradientNoise.floor(x0 / BORE_CELL), bj = GradientNoise.floor(z0 / BORE_CELL);
        for (int j = bj - 1; j <= bj + 1; j++) {
            for (int i = bi - 1; i <= bi + 1; i++) {
                for (Seg s : boreCell(cc, i, j)) {
                    if (s.maxX >= x0 && s.minX <= x1 && s.maxZ >= z0 && s.minZ <= z1) cc.chunk.add(s);
                }
            }
        }
        for (ArrayList<Seg> b : cc.bins) b.clear();
        for (Seg s : cc.chunk) {
            int bx0 = (int) Math.max(0, Math.floor((s.minX - x0) / 4)), bx1 = (int) Math.min(3, Math.floor((s.maxX - x0) / 4));
            int bz0 = (int) Math.max(0, Math.floor((s.minZ - z0) / 4)), bz1 = (int) Math.min(3, Math.floor((s.maxZ - z0) / 4));
            for (int bz = bz0; bz <= bz1; bz++) for (int bx = bx0; bx <= bx1; bx++) cc.bins[bz * 4 + bx].add(s);
        }
        cc.cx = Integer.MIN_VALUE;
    }

    /** The nearest rift (or great rift) to (x, z), within ~4 cells: {x, z of its middle, top y, great ? 1 : 0}, or null. */
    public double[] nearestRift(double x, double z, boolean greatOnly) {
        Caches cc = caches.get();
        int ci = GradientNoise.floor(x / RIFT_CELL), cj = GradientNoise.floor(z / RIFT_CELL);
        double[] best = null;
        double bestD = Double.MAX_VALUE;
        for (int r = 0; r <= (greatOnly ? 12 : 4) && best == null; r++) {
            for (int j = cj - r; j <= cj + r; j++) {
                for (int i = ci - r; i <= ci + r; i++) {
                    if (Math.max(Math.abs(i - ci), Math.abs(j - cj)) != r) continue;
                    List<Seg> list = riftCell(cc, i, j);
                    if (list.isEmpty()) continue;
                    Seg mid = list.get(list.size() / 2);
                    if (greatOnly && mid.kind != GREAT_RIFT) continue;
                    double d = Math.hypot(mid.ax - x, mid.az - z);
                    if (d < bestD) {
                        bestD = d;
                        best = new double[] {mid.ax, mid.az, mid.ay, mid.kind == GREAT_RIFT ? 1 : 0};
                    }
                }
            }
        }
        return best;
    }

    /** The nearest worm bore's start to (x, z), within ~3 cells: {x, z, opens at the surface ? 1 : 0}, or null. */
    public double[] nearestBore(double x, double z) {
        Caches cc = caches.get();
        int ci = GradientNoise.floor(x / BORE_CELL), cj = GradientNoise.floor(z / BORE_CELL);
        double[] best = null;
        double bestD = Double.MAX_VALUE;
        for (int j = cj - 3; j <= cj + 3; j++) {
            for (int i = ci - 3; i <= ci + 3; i++) {
                List<Seg> b = boreCell(cc, i, j);
                if (b.isEmpty()) continue;
                Seg s = b.get(0);
                double d = Math.hypot(s.ax - x, s.az - z);
                if (d < bestD) {
                    bestD = d;
                    best = new double[] {s.ax, s.az, s.open ? 1 : 0, s.ay};
                }
            }
        }
        return best;
    }

    private static double ofbm01(long seed, double x, double z) {
        return Math.max(0, Math.min(1, Fractal.fbm(seed, x, z, 2) * 0.5 + 0.5));
    }

    private static double unit(long hash, int k) {
        return (GradientNoise.mix(hash + k * 0x9E3779B97F4A7C15L) >>> 11) * 0x1.0p-53;
    }
}
