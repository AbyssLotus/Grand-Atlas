package com.seaweed.grandatlas.core;

import static com.seaweed.grandatlas.core.Maths.smoothstep;

import com.seaweed.grandatlas.core.noise.Fractal;
import com.seaweed.grandatlas.core.noise.GradientNoise;
import java.util.ArrayList;
import java.util.List;

/**
 * M7e set pieces (docs/M7_UNDERGROUND_PLAN.md): the underground's rare showpieces, carved per
 * block so bone and island edges stay sharp.
 * <ul>
 *   <li><b>The Hollow</b>: a cavern in the abyss ~1-1.5 km across and ~300 tall, a dark lake on its
 *       floor, and 12-20 floating stone islands (cones, double-pointed spires, flat mesas; many small
 *       floating rocks) in banded rock, with hanging rock beneath and natural bridges between some;
 *       pools on some islands spill over their edges as waterfalls into the lake far below. Giant
 *       stalactites hang from the dome, hourglass columns join lake and roof, sea stacks rise.</li>
 *   <li><b>The Great Geode</b>: a hollow sphere 300-560 across with walls of amethyst, calcite and
 *       basalt, entered by one great crack; selenite pillars thrust in from its walls, amethyst
 *       crystals stud them, and a lake pools in its bowl.</li>
 *   <li><b>Titan's grave</b>: the skeleton of something 0.9-1.4 km long lying in its own hall: ribs
 *       arching overhead (some broken), a spine with dorsal spikes and side processes you can walk
 *       along the top, a pelvis, fore and hind limbs sprawled on the floor, a tapering tail, scattered
 *       bone, and a hollow skull with teeth, a lower jaw, horns, and eye sockets to walk in through.</li>
 *   <li><b>The Aurora Vault</b>: a domed chamber 300-480 across with a lake hanging from its roof,
 *       its surface facing down, held in a basin with a rock lip. The lake's bed is aurora crystal in
 *       ribbons of green, cyan, blue, violet and rose, with crystal spires hanging into the water, so
 *       its light glows down through the lake. A rising fall, a swaying column of water with an
 *       upward current, climbs from a pool on the floor into the lake; you can ride it up and swim
 *       into the lake from below. On the surface above, the Aurora Well shows where it is: a ring of
 *       glowing crystal spires round a crystal-lined shaft that drops into the hanging lake.</li>
 *   <li><b>The Worldroot</b>: one colossal tree on the surface (a trunk 14-22 across, 95-130 tall,
 *       great boughs, a canopy over 100 across, flowering in patches) whose roots run down hundreds
 *       of blocks to a root hall far below: they cross the hall as bridges and dive into its floor,
 *       veined with glowing amber sap. A gate between the buttress roots leads down a shaft beside
 *       the taproot to the hall.</li>
 *   <li><b>The Hanging Mountains</b>: a cavern 600-900 across in the abyss whose roof hangs a range
 *       of mountains upside down, sharp ridges and peaks reaching down, their tips crusted in packed
 *       and blue ice and hung with icicles, over a still lake that mirrors them.</li>
 * </ul>
 * Each kind sits on its own grid of cells (one chance in two per cell), always 50+ blocks under
 * the ground. Inside a piece, other caves keep out: Grand Atlas's passages may break through its
 * outer walls (they are its entrances) but never through {@linkplain #isProtected protected} rock
 * (bone, islands, the geode's shell, a lake bed).
 */
public final class SetPieces {
    public static final int NONE = 0, HOLLOW = 1, GEODE = 2, TITAN = 3, VAULT = 4, ROOT = 5, PEAKS = 6;
    /** Materials the set-piece dressing lays in solid rock. */
    public static final int ROCK = 0, BONE = 1, AMETHYST = 2, BUDDING = 3, CALCITE = 4, BASALT = 5, TUFF = 6, SELENITE = 7, AURORA = 8,
            WOOD = 9, SAP = 10, PACKED_ICE = 11, BLUE_ICE = 12, LEAVES = 13, BLOSSOM = 14;
    static final double HOLLOW_CELL = 7000, GEODE_CELL = 5200, TITAN_CELL = 6400, VAULT_CELL = 3800, ROOT_CELL = 6600, PEAKS_CELL = 7400;

    static double cellOf(int kind) {
        return kind == HOLLOW ? HOLLOW_CELL : kind == GEODE ? GEODE_CELL : kind == TITAN ? TITAN_CELL : kind == VAULT ? VAULT_CELL
                : kind == ROOT ? ROOT_CELL : PEAKS_CELL;
    }

    private final AtlasCore core;
    private final long seed;
    // The sites are shared by every thread. A cache per thread, in a modpack with C2ME and Distant
    // Horizons, meant dozens of threads each holding up to 20,000 sites (950,000 bone and tree
    // capsules, 123 MB, in Create Atlas on a 16-core machine, measured 2026-11-24). A site is
    // finished before it is published and never changed after, so sharing it is safe.
    private final java.util.concurrent.ConcurrentHashMap<Long, Site> sites = new java.util.concurrent.ConcurrentHashMap<>();
    /** Most sites kept, for all threads together: a few working sets' worth. */
    private static final int SITE_CAP = 30_000;
    // A small table of columns per thread, keyed on the low bits of x and z. 26.3 hands density
    // functions whole columns, so one remembered column was enough there; 1.21.1 samples a 4x4 cell
    // block by block, cycling through sixteen columns, and with one slot every sample rebuilt the
    // column (15% of chunk generation on 1.21.1, profiled 2026-11-11).
    private final ThreadLocal<Col[]> cols = ThreadLocal.withInitial(() -> new Col[64]);

    SetPieces(AtlasCore core, long seed) {
        this.core = core;
        this.seed = seed;
    }

    /** A capsule (a to b, radius ra to rb): islands' cones, bones. */
    static final class Cap {
        double ax, ay, az, bx, by, bz, ra, rb;
        int mat = BONE;
        boolean island; // island rock: banded like the islands
        double minX, maxX, minY, maxY, minZ, maxZ;

        Cap(double ax, double ay, double az, double bx, double by, double bz, double ra, double rb) {
            this.ax = ax; this.ay = ay; this.az = az; this.bx = bx; this.by = by; this.bz = bz; this.ra = ra; this.rb = rb;
            double r = Math.max(ra, rb) + 1;
            minX = Math.min(ax, bx) - r; maxX = Math.max(ax, bx) + r;
            minY = Math.min(ay, by) - r; maxY = Math.max(ay, by) + r;
            minZ = Math.min(az, bz) - r; maxZ = Math.max(az, bz) + r;
        }

        Cap(double ax, double ay, double az, double bx, double by, double bz, double ra, double rb, int mat, boolean island) {
            this(ax, ay, az, bx, by, bz, ra, rb);
            this.mat = mat;
            this.island = island;
        }

        double dist(double x, double y, double z) {
            double ex = bx - ax, ey = by - ay, ez = bz - az, l2 = ex * ex + ey * ey + ez * ez;
            double t = l2 < 1e-6 ? 0 : Math.max(0, Math.min(1, ((x - ax) * ex + (y - ay) * ey + (z - az) * ez) / l2));
            double dx = x - ax - ex * t, dy = y - ay - ey * t, dz = z - az - ez * t;
            return Math.sqrt(dx * dx + dy * dy + dz * dz) - (ra + (rb - ra) * t);
        }
    }

    /** A floating island: centre, radius, cap height, cone depth; a spill pool if poolR > 0. */
    static final class Island {
        double x, y, z, r, cap, depth, px, pz, poolR, level;
        int type; // 0 cone, 1 spire, 2 mesa
        long h;
    }

    static final class Site {
        int kind;
        boolean present;
        double x, z, y0, r, rx, rz, cos, sin, height;
        long h;
        double minX, maxX, minZ, maxZ, minY, maxY;
        // Hollow
        double lake;
        final List<Island> islands = new ArrayList<>();
        // Titan: spine points, bones
        double[] sx, sy, sz;
        final List<Cap> bones = new ArrayList<>();
        double skullX, skullY, skullZ, dirX, dirZ;
        // Geode crack
        double nx, nz;
        // Aurora Vault: lake radius (fraction), pool centre, radius and level, the rising fall
        double lakeR, pX, pZ, pR, pLev, colR, swayA, swayP, swayQ, bandA;
        // the Aurora Well: shaft centre, radius, the ground there; the crystal spires round its mouth
        boolean well;
        double wX, wZ, wR, wTop;
        final List<Cap> outcrops = new ArrayList<>();
        // the Worldroot: the ground at the trunk, trunk radius and height; the tree above ground
        // (trunk, buttresses, boughs), its leaf masses {x, y, z, r}, the taproot (with a shaft round it)
        double tGround, trunkR, treeH;
        final List<Cap> tree = new ArrayList<>(), tap = new ArrayList<>(), gate = new ArrayList<>();
        // rooms off the hollow taproot: {x, y, z, rx, ry, axis x, axis y, axis z}
        final List<double[]> rooms = new ArrayList<>();
        final List<double[]> leaves = new ArrayList<>();
        // the Hanging Mountains: the range's axis
        double rangeA;
    }

    /** Per column: the sites over it, and what each site makes of the column (computed once). */
    private static final class Col {
        int x = Integer.MIN_VALUE, z = Integer.MIN_VALUE;
        // per kind: the cell the column was last in, and the present sites in the 3x3 cells round it
        final int[] nearI = new int[PEAKS + 1], nearJ = new int[PEAKS + 1];
        final Site[][] nearSites = new Site[PEAKS + 1][];
        final Probe probe = new Probe();
        final List<Site> sites = new ArrayList<>(2);
        final List<Cap> caps = new ArrayList<>();
        final List<Island> islands = new ArrayList<>();
        // the Hollow: inside its outline, its roof and floor here
        boolean hIn;
        double hDome, hFloor, hD;
        // Titan: inside the hall, its floor and roof here
        boolean tIn;
        double tFloor, tCeil, tHd, tWidth, tBy, tAlong, tSk;
        // Aurora Vault: inside, under the hanging lake, over the pool; roof (or lake bed) and floor
        boolean vIn, vLake, vPool;
        double vTop, vFloor, vDl;
        boolean vWell; // near the Aurora Well's shaft
        // the Worldroot: inside the root hall (its roof and floor), the ground here; the tree's parts over this column
        boolean rIn, rTree;
        double rTop, rFloor, rGround;
        final List<Cap> treeCaps = new ArrayList<>();
        final List<double[]> leafBlobs = new ArrayList<>();
        // the Hanging Mountains: inside, roof (the mountains' undersides) and floor, how far the peaks hang
        boolean mIn;
        double mTop, mFloor, mHang;
    }

    // ---- sites

    private Site site(int kind, int i, int j) {
        // (mixed: the plain packed key's hash put many sites in the same buckets, which the table then
        // had to search as trees)
        long key = GradientNoise.mix(((long) kind << 58) ^ ((long) i << 29) ^ (j & 0x1FFFFFFFL));
        Site s = sites.get(key);
        if (s != null) return s;
        // Each kind checks every earlier kind round it for overlaps (and those theirs), so the working set
        // is a few thousand sites; a small cache thrashes and rebuilds them without end.
        if (sites.size() > SITE_CAP) sites.clear();
        // Built whole before it is published: the overlap check only asks about earlier kinds, so
        // building never needs this site from the map. Two threads may build the same site at once;
        // both get the same answer (a site is a pure function of the seed and its cell), and the
        // first one in is kept.
        s = makeSite(kind, i, j);
        Site had = sites.putIfAbsent(key, s);
        return had != null ? had : s;
    }

    private Site makeSite(int kind, int i, int j) {
        Site s = new Site();
        s.kind = kind;
        long h = GradientNoise.hash(GradientNoise.fork(seed, kind), i, j);
        s.h = h;
        if (unit(h, 0) > 0.5) return s;
        double cell = cellOf(kind);
        s.x = (i + 0.3 + 0.4 * unit(h, 1)) * cell;
        s.z = (j + 0.3 + 0.4 * unit(h, 2)) * cell;
        double a = unit(h, 3) * Math.PI * 2;
        s.cos = Math.cos(a);
        s.sin = Math.sin(a);
        switch (kind) {
            case HOLLOW -> buildHollow(s, h);
            case GEODE -> buildGeode(s, h);
            case TITAN -> buildTitan(s, h);
            case VAULT -> buildVault(s, h);
            case ROOT -> buildRoot(s, h);
            default -> buildPeaks(s, h);
        }
        // Set pieces never overlap: a geode keeps clear of any Hollow, a Titan's grave of both
        // (asking only about earlier kinds, so this can't go round in a loop).
        if (s.present) {
            for (int other = HOLLOW; other < kind && s.present; other++) {
                double oc = cellOf(other);
                int ci = GradientNoise.floor(s.x / oc), cj = GradientNoise.floor(s.z / oc);
                for (int dj = -1; dj <= 1 && s.present; dj++) {
                    for (int di = -1; di <= 1 && s.present; di++) {
                        Site o = site(other, ci + di, cj + dj);
                        if (o.present && s.maxX + 80 > o.minX && s.minX - 80 < o.maxX && s.maxZ + 80 > o.minZ && s.minZ - 80 < o.maxZ) s.present = false;
                    }
                }
            }
        }
        return s;
    }

    /** The lowest ground over a footprint (centre and a ring at reach). */
    private double ground(double x, double z, double reach) {
        double g = core.column((int) x, (int) z).height();
        for (int k = 0; k < 12; k++) {
            double a = k * Math.PI / 6;
            g = Math.min(g, core.column((int) (x + Math.cos(a) * reach), (int) (z + Math.sin(a) * reach)).height());
        }
        return g;
    }

    private void buildHollow(Site s, long h) {
        s.rx = 480 + 260 * unit(h, 4);
        s.rz = s.rx * (0.75 + 0.25 * unit(h, 5));
        s.height = 270 + 60 * unit(h, 6);
        s.y0 = AtlasCore.MIN_Y + 22 + 10 * unit(h, 7); // floor of the dome
        double top = s.y0 + s.height;
        if (top > ground(s.x, s.z, s.rx) - 55) return;
        s.lake = s.y0 + 34;
        s.minX = s.x - s.rx * 1.25; s.maxX = s.x + s.rx * 1.25;
        s.minZ = s.z - s.rx * 1.25; s.maxZ = s.z + s.rx * 1.25;
        s.minY = s.y0 - 12; s.maxY = top + 12;
        int n = 12 + (int) (unit(h, 8) * 9);
        for (int k = 0; k < n + 40; k++) {
            boolean big = k < n;
            Island is = new Island();
            is.h = GradientNoise.fork(h, 100 + k);
            double ang = unit(is.h, 0) * Math.PI * 2, d = Math.sqrt(unit(is.h, 1)) * (big ? 0.62 : 0.75);
            is.x = s.x + Math.cos(ang) * d * s.rx;
            is.z = s.z + Math.sin(ang) * d * s.rz;
            is.r = big ? 26 + 84 * Math.pow(unit(is.h, 2), 1.4) : 4 + 9 * unit(is.h, 2);
            is.cap = big ? 6 + 6 * unit(is.h, 3) : 2 + 2 * unit(is.h, 3);
            is.depth = is.r * (0.9 + 0.8 * unit(is.h, 4));
            double dome = s.y0 + s.height * Math.sqrt(Math.max(0, 1 - d * d)); // the roof over it
            double lo = s.lake + is.depth + 20, hi = dome - is.cap - 30;
            if (hi <= lo) continue;
            is.y = lo + (hi - lo) * (0.25 + 0.6 * unit(is.h, 5));
            // A spill pool on some big islands: a bowl reaching the island's edge, so its water pours over.
            if (big && unit(is.h, 6) < 0.55) {
                double pa = unit(is.h, 7) * Math.PI * 2;
                is.px = is.x + Math.cos(pa) * is.r * 0.72;
                is.pz = is.z + Math.sin(pa) * is.r * 0.72;
                is.poolR = is.r * 0.34;
                is.level = Math.floor(is.y + is.cap * 0.55);
            }
            // Shapes: most are cones, some tall spires pointed both ways, some blunt flat mesas.
            if (big) {
                double ty = unit(is.h, 9);
                is.type = ty < 0.22 ? 1 : ty < 0.45 ? 2 : 0;
                if (is.type == 1) {
                    is.r *= 0.55;
                    is.cap = is.r * 1.5;
                    is.depth = is.r * 2.4;
                    is.poolR = 0;
                } else if (is.type == 2) {
                    is.cap = 3;
                    is.depth = is.r * 0.75;
                }
            }
            s.islands.add(is);
        }
        long dh = GradientNoise.fork(h, 50);
        // Hanging rock under the big islands.
        for (Island is : s.islands) {
            if (is.r < 20 || is.type == 1) continue;
            int m = 3 + (int) (unit(is.h, 20) * 5);
            for (int k = 0; k < m; k++) {
                double a = unit(is.h, 21 + k) * Math.PI * 2, d = Math.sqrt(unit(is.h, 31 + k)) * 0.7;
                double px = is.x + Math.cos(a) * d * is.r, pz = is.z + Math.sin(a) * d * is.r;
                double bottom = is.y - is.depth * Math.pow(Math.max(0, 1 - d), is.type == 2 ? 0.4 : 0.85);
                double len = 10 + 34 * unit(is.h, 41 + k), rad = 2 + 4 * unit(is.h, 51 + k);
                s.bones.add(new Cap(px, bottom + 4, pz, px, bottom - len, pz, rad, 0.6, ROCK, true));
            }
        }
        // Natural bridges between some neighbouring islands, sagging in the middle.
        int bridges = 0;
        for (int i = 0; i < s.islands.size() && bridges < 4; i++) {
            Island a = s.islands.get(i);
            if (a.r < 20) continue;
            for (int j = i + 1; j < s.islands.size() && bridges < 4; j++) {
                Island b = s.islands.get(j);
                if (b.r < 20) continue;
                double dx = b.x - a.x, dz = b.z - a.z, dd = Math.hypot(dx, dz);
                if (dd > a.r + b.r + 80 || Math.abs(a.y - b.y) > 40 || unit(GradientNoise.hash(dh, i, j), 0) > 0.6) continue;
                double ux = dx / dd, uz = dz / dd;
                double ax = a.x + ux * a.r * 0.8, az = a.z + uz * a.r * 0.8, ay = a.y + a.cap * 0.3 - 3;
                double bx = b.x - ux * b.r * 0.8, bz = b.z - uz * b.r * 0.8, by = b.y + b.cap * 0.3 - 3;
                double mx = (ax + bx) / 2, mz = (az + bz) / 2, my = (ay + by) / 2 - 8 - dd * 0.04;
                double rad = 4 + 3 * unit(GradientNoise.hash(dh, i, j), 1);
                s.bones.add(new Cap(ax, ay, az, mx, my, mz, rad + 1.5, rad, ROCK, true));
                s.bones.add(new Cap(mx, my, mz, bx, by, bz, rad, rad + 1.5, ROCK, true));
                bridges++;
            }
        }
        // Giant stalactites from the dome.
        int st = 16 + (int) (unit(dh, 1) * 12);
        for (int k = 0; k < st; k++) {
            double a = unit(dh, 100 + k) * Math.PI * 2, d = Math.sqrt(unit(dh, 200 + k)) * 0.85;
            double px = s.x + Math.cos(a) * d * s.rx, pz = s.z + Math.sin(a) * d * s.rz;
            double roof = s.y0 + s.height * Math.sqrt(Math.max(0, 1 - d * d));
            double len = (30 + 110 * unit(dh, 300 + k)) * (1 - 0.6 * d), rad = 6 + 12 * unit(dh, 400 + k);
            s.bones.add(new Cap(px, roof + 8, pz, px, roof - len, pz, rad, 0.8, ROCK, false));
        }
        // A few colossal hourglass columns from the lake to the roof.
        int cols = 2 + (int) (unit(dh, 2) * 3);
        for (int k = 0; k < cols; k++) {
            double a = unit(dh, 500 + k) * Math.PI * 2, d = Math.sqrt(unit(dh, 600 + k)) * 0.6;
            double px = s.x + Math.cos(a) * d * s.rx, pz = s.z + Math.sin(a) * d * s.rz;
            double roof = s.y0 + s.height * Math.sqrt(Math.max(0, 1 - d * d)), mid = (s.y0 + roof) / 2;
            double big = 18 + 16 * unit(dh, 700 + k), waist = big * (0.35 + 0.2 * unit(dh, 800 + k));
            s.bones.add(new Cap(px, s.y0, pz, px, mid, pz, big, waist, ROCK, false));
            s.bones.add(new Cap(px, mid, pz, px, roof + 8, pz, waist, big, ROCK, false));
        }
        // Sea stacks rising from the lake.
        int stacks = 10 + (int) (unit(dh, 3) * 9);
        for (int k = 0; k < stacks; k++) {
            double a = unit(dh, 900 + k) * Math.PI * 2, d = Math.sqrt(unit(dh, 1000 + k)) * 0.8;
            double px = s.x + Math.cos(a) * d * s.rx, pz = s.z + Math.sin(a) * d * s.rz;
            double hgt = 20 + 50 * unit(dh, 1100 + k), rad = 5 + 9 * unit(dh, 1200 + k);
            s.bones.add(new Cap(px, s.y0, pz, px, s.lake + hgt, pz, rad, 0.8, ROCK, false));
        }
        s.present = true;
    }

    private void buildGeode(Site s, long h) {
        s.r = 150 + 130 * unit(h, 4);
        double lo = AtlasCore.MIN_Y + 30 + s.r, g = ground(s.x, s.z, s.r);
        double hi = Math.min(g - 60 - s.r, -40);
        if (hi < lo) return;
        s.y0 = lo + (hi - lo) * unit(h, 5);
        double a = unit(h, 6) * Math.PI * 2;
        s.nx = Math.cos(a);
        s.nz = Math.sin(a);
        s.minX = s.x - s.r - 70; s.maxX = s.x + s.r + 70;
        s.minZ = s.z - s.r - 70; s.maxZ = s.z + s.r + 70;
        s.minY = s.y0 - s.r - 20; s.maxY = s.y0 + s.r + 20;
        // Selenite pillars thrusting in from the walls (Naica), some of amethyst, mostly from below.
        int pillars = 10 + (int) (unit(h, 7) * 9);
        for (int k = 0; k < pillars; k++) {
            double[] d = direction(GradientNoise.fork(h, 300 + k), unit(h, 100 + k) < 0.65);
            double len = s.r * (0.35 + 0.45 * unit(h, 200 + k)), rad = 4 + 7 * unit(h, 250 + k);
            int mat = unit(h, 280 + k) < 0.3 ? AMETHYST : SELENITE;
            s.bones.add(new Cap(s.x + d[0] * (s.r + 2), s.y0 + d[1] * (s.r + 2), s.z + d[2] * (s.r + 2),
                    s.x + d[0] * (s.r - len), s.y0 + d[1] * (s.r - len), s.z + d[2] * (s.r - len), rad, 1.0, mat, false));
        }
        // Amethyst crystals studding the inner wall.
        for (int k = 0; k < 150; k++) {
            double[] d = direction(GradientNoise.fork(h, 1000 + k), false);
            double len = 6 + 16 * unit(h, 2000 + k), rad = 1.5 + 2.5 * unit(h, 3000 + k);
            s.bones.add(new Cap(s.x + d[0] * (s.r + 1), s.y0 + d[1] * (s.r + 1), s.z + d[2] * (s.r + 1),
                    s.x + d[0] * (s.r - len), s.y0 + d[1] * (s.r - len), s.z + d[2] * (s.r - len), rad, 0.5, AMETHYST, false));
        }
        s.present = true;
    }

    private void buildVault(Site s, long h) {
        s.rx = 150 + 90 * unit(h, 4);
        s.rz = s.rx * (0.8 + 0.2 * unit(h, 5));
        // Low enough that the crystal bed shows from the floor (the user, 2026-11-09: too tall to see it).
        s.height = 58 + 18 * unit(h, 6);
        double g = ground(s.x, s.z, s.rx);
        double lo = AtlasCore.MIN_Y + 40, hi = Math.min(g - 70 - s.height, -60);
        if (hi < lo) return;
        s.y0 = lo + (hi - lo) * unit(h, 7);
        s.lakeR = 0.55 + 0.12 * unit(h, 8);
        // The lake's downward-facing surface: a little under where the dome crosses the lake's edge,
        // so a lip of rock holds it all round.
        s.lake = s.y0 + s.height * Math.sqrt(1 - s.lakeR * s.lakeR) - 4;
        double a = unit(h, 9) * Math.PI * 2, rr = s.rx * 0.22 * unit(h, 10);
        s.pX = s.x + Math.cos(a) * rr;
        s.pZ = s.z + Math.sin(a) * rr;
        s.pR = 15 + 9 * unit(h, 11);
        s.pLev = s.y0 + 3;
        s.colR = 3.2 + 1.4 * unit(h, 12);
        s.swayA = 3 + 4 * unit(h, 13);
        s.swayP = unit(h, 14) * Math.PI * 2;
        s.swayQ = unit(h, 15) * Math.PI * 2;
        s.bandA = unit(h, 16) * Math.PI;
        s.minX = s.x - s.rx - 12; s.maxX = s.x + s.rx + 12;
        s.minZ = s.z - s.rz - 12; s.maxZ = s.z + s.rz + 12;
        s.minY = s.y0 - 14; s.maxY = s.y0 + s.height + 16;
        // Crystal spires hanging from the lake bed into the water.
        for (int k = 0; k < 16; k++) {
            double ra = unit(h, 100 + k) * Math.PI * 2, rd = Math.sqrt(unit(h, 120 + k)) * s.lakeR * 0.85;
            double cx = s.x + Math.cos(ra) * rd * s.rx, cz = s.z + Math.sin(ra) * rd * s.rz;
            double bed = s.y0 + s.height * Math.sqrt(Math.max(0, 1 - rd * rd));
            double len = Math.min(bed - s.lake - 2, 5 + 16 * unit(h, 140 + k));
            if (len < 4) continue;
            s.bones.add(new Cap(cx, bed + 3, cz, cx + (unit(h, 160 + k) - 0.5) * 4, bed - len, cz + (unit(h, 180 + k) - 0.5) * 4,
                    2.2 + 2.2 * unit(h, 200 + k), 0.6, AURORA, false));
        }
        // Rock stalactites from the dome outside the lake.
        for (int k = 0; k < 18; k++) {
            double ra = unit(h, 300 + k) * Math.PI * 2, rd = s.lakeR * 1.2 + (0.88 - s.lakeR * 1.2) * unit(h, 320 + k);
            double cx = s.x + Math.cos(ra) * rd * s.rx, cz = s.z + Math.sin(ra) * rd * s.rz;
            double roof = s.y0 + s.height * Math.sqrt(Math.max(0, 1 - rd * rd));
            double len = (roof - s.y0) * (0.2 + 0.4 * unit(h, 340 + k));
            s.bones.add(new Cap(cx, roof + 4, cz, cx, roof - len, cz, 4 + 6 * unit(h, 360 + k), 0.8, ROCK, false));
        }
        // Crystal gardens on the floor (the user, 2026-11-09: a pretty ceiling over a barren floor):
        // stands of aurora-crystal spires, the tallest in the middle, the rest leaning out round it,
        // coloured by the ribbon of the lake bed straight above so the floor answers the ceiling.
        int gardens = 10 + (int) (unit(h, 600) * 7);
        for (int gi = 0; gi < gardens; gi++) {
            double ga = unit(h, 610 + gi) * Math.PI * 2, gd = Math.sqrt(unit(h, 630 + gi)) * 0.64;
            double gx = s.x + Math.cos(ga) * gd * s.rx, gz = s.z + Math.sin(ga) * gd * s.rz;
            if (Math.hypot(gx - s.pX, gz - s.pZ) < s.pR + 16) continue; // clear of the pool and the rising fall
            boolean grand = gi % 4 == 0; // every fourth a great stand, half-way to the lake
            double spread = (5 + 6 * unit(h, 650 + gi)) * (grand ? 1.5 : 1), tall = (9 + 14 * unit(h, 670 + gi)) * (grand ? 1.6 : 1);
            int n = 5 + (int) (unit(h, 690 + gi) * 8) + (grand ? 6 : 0);
            for (int k = 0; k < n; k++) {
                long sh = GradientNoise.fork(h, 700 + gi * 16 + k);
                double a2 = unit(sh, 0) * Math.PI * 2, r2 = k == 0 ? 0 : spread * Math.sqrt(unit(sh, 1));
                double bx = gx + Math.cos(a2) * r2, bz = gz + Math.sin(a2) * r2;
                double f = vaultFloor(s, bx, bz);
                double len = Math.max(3, tall * (1 - 0.7 * r2 / spread) * (0.7 + 0.3 * unit(sh, 2)));
                double lean = k == 0 ? 0.08 : 0.15 + 0.4 * unit(sh, 3);
                s.bones.add(new Cap(bx, f - 3, bz, bx + Math.cos(a2) * len * lean, f + len, bz + Math.sin(a2) * len * lean,
                        1.0 + 0.1 * len, 0.35, AURORA, false));
            }
        }
        buildWell(s, h);
        s.present = true;
    }

    /** The vault's floor at a point, before the pool is cut into it (as {@link #vaultColumn} has it). */
    private static double vaultFloor(Site s, double x, double z) {
        double u = (x - s.x) / s.rx, v = (z - s.z) / s.rz;
        double d = Math.sqrt(u * u + v * v) * (1 - 0.15 * Fractal.fbm(GradientNoise.fork(s.h, 1), x / 150.0, z / 150.0, 2));
        return s.y0 + 8 * (Fractal.fbm(GradientNoise.fork(s.h, 3), x / 90.0, z / 90.0, 3) * 0.5 + 0.5) + 44 * smoothstep(0.7, 1.0, d);
    }

    /**
     * Veins of fallen crystal in the vault's floor: thin waving ribbons, broken here and there,
     * running the way the lake bed's colours do. 0 off a vein, up to 1 along its middle.
     */
    public double floorVein(int x, int z) {
        Col c = column(x, z);
        Site v = null;
        for (Site s : c.sites) if (s.kind == VAULT) v = s;
        if (v == null || !c.vIn) return 0;
        double along = (x - v.x) * Math.cos(v.bandA + 0.3) + (z - v.z) * Math.sin(v.bandA + 0.3);
        double across = (x - v.x) * -Math.sin(v.bandA + 0.3) + (z - v.z) * Math.cos(v.bandA + 0.3);
        double w = across / 19.0 + 2.2 * Fractal.fbm(GradientNoise.fork(v.h, 60), along / 45.0, across / 45.0, 2);
        double edge = Math.abs(w - Math.rint(w));
        if (edge > 0.06) return 0;
        if (Fractal.fbm(GradientNoise.fork(v.h, 62), x / 38.0, z / 38.0, 2) < -0.2) return 0; // gaps
        return 1 - edge / 0.06;
    }

    /**
     * The Aurora Well: the vault's tell on the surface. A shaft drops from the ground straight into
     * the hanging lake (inside its outline, clear of the rising fall), lined with aurora crystal so it
     * glows from above at night, and a ring of crystal spires breaks the ground round its mouth.
     * Skipped where the ground there is under water.
     */
    private void buildWell(Site s, long h) {
        for (int tries = 0; tries < 8; tries++) {
            double a = unit(h, 400 + tries) * Math.PI * 2, rd = s.lakeR * (0.3 + 0.25 * unit(h, 420 + tries));
            double wx = s.x + Math.cos(a) * rd * s.rx, wz = s.z + Math.sin(a) * rd * s.rz;
            if (Math.hypot(wx - s.pX, wz - s.pZ) < s.pR + 20) continue; // not over the rising fall
            ColumnSample c = core.column((int) wx, (int) wz);
            if (c.isOcean() || !Double.isNaN(c.water())) continue;
            s.well = true;
            s.wX = wx;
            s.wZ = wz;
            s.wR = 3.2 + 1.6 * unit(h, 440);
            s.wTop = c.height();
            s.maxY = Math.max(s.maxY, s.wTop + 24);
            break;
        }
        if (!s.well) return;
        // Spires round the mouth, leaning out, a few big ones among them.
        int n = 12 + (int) (unit(h, 450) * 8);
        for (int k = 0; k < n; k++) {
            double a = unit(h, 460 + k) * Math.PI * 2, big = unit(h, 500 + k) < 0.2 ? 1 : 0;
            double r = s.wR + 3 + (8 + 10 * big) * unit(h, 480 + k);
            double bx = s.wX + Math.cos(a) * r, bz = s.wZ + Math.sin(a) * r;
            double g = core.column((int) bx, (int) bz).height();
            double len = big > 0 ? 11 + 7 * unit(h, 520 + k) : 3 + 7 * unit(h, 540 + k);
            double lean = 0.25 + 0.35 * unit(h, 560 + k);
            s.outcrops.add(new Cap(bx, g - 3, bz, bx + Math.cos(a) * len * lean, g + len, bz + Math.sin(a) * len * lean,
                    big > 0 ? 2.6 + unit(h, 580 + k) : 0.9 + 1.1 * unit(h, 580 + k), 0.4, AURORA, false));
        }
    }

    /** The nearest Titan's skull centre, {x, y, z}, or null. */
    public double[] nearestSkull(double x, double z) {
        int ci = GradientNoise.floor(x / TITAN_CELL), cj = GradientNoise.floor(z / TITAN_CELL);
        double[] best = null;
        double bd = Double.MAX_VALUE;
        for (int j = cj - 3; j <= cj + 3; j++) {
            for (int i = ci - 3; i <= ci + 3; i++) {
                Site s = site(TITAN, i, j);
                if (!s.present) continue;
                double d = Math.hypot(s.skullX - x, s.skullZ - z);
                if (d < bd) {
                    bd = d;
                    best = new double[] {s.skullX, s.skullY, s.skullZ};
                }
            }
        }
        return best;
    }

    /** The nearest Aurora Well's mouth, {x, ground, z}, or null. */
    public double[] nearestWell(double x, double z) {
        int ci = GradientNoise.floor(x / VAULT_CELL), cj = GradientNoise.floor(z / VAULT_CELL);
        double[] best = null;
        double bd = Double.MAX_VALUE;
        for (int j = cj - 3; j <= cj + 3; j++) {
            for (int i = ci - 3; i <= ci + 3; i++) {
                Site s = site(VAULT, i, j);
                if (!s.present || !s.well) continue;
                double d = Math.hypot(s.wX - x, s.wZ - z);
                if (d < bd) {
                    bd = d;
                    best = new double[] {s.wX, s.wTop, s.wZ};
                }
            }
        }
        return best;
    }

    /** True if (x, z) is near an Aurora Well's mouth (its spires reach this far). */
    public boolean nearWell(int x, int z) {
        Col c = column(x, z);
        return c.vWell;
    }

    /** An Aurora Well spire's crystal at this block (over the ground), or false. */
    public boolean wellSpire(int x, int y, int z) {
        Col c = column(x, z);
        if (!c.vWell) return false;
        for (Site s : c.sites) {
            if (s.kind != VAULT || !s.well) continue;
            for (Cap b : s.outcrops) {
                if (y < b.minY || y > b.maxY || x < b.minX || x > b.maxX || z < b.minZ || z > b.maxZ) continue;
                if (b.dist(x + 0.5, y + 0.5, z + 0.5) < 0) return true;
            }
        }
        return false;
    }

    // ---- the Worldroot

    private void buildRoot(Site s, long h) {
        ColumnSample c0 = core.column((int) s.x, (int) s.z);
        if (c0.isOcean() || !Double.isNaN(c0.water())) return;
        double g = c0.height();
        if (g < 64 || g > 210) return;
        for (int k = 0; k < 8; k++) { // fairly level ground for the trunk
            double a = k * Math.PI / 4;
            if (Math.abs(core.column((int) (s.x + Math.cos(a) * 14), (int) (s.z + Math.sin(a) * 14)).height() - g) > 7) return;
        }
        s.tGround = g;
        s.trunkR = 7 + 4 * unit(h, 4);
        s.treeH = 95 + 35 * unit(h, 5);
        s.rx = 170 + 80 * unit(h, 6);
        s.rz = s.rx * (0.8 + 0.2 * unit(h, 7));
        s.height = 100 + 40 * unit(h, 8);
        double lo = AtlasCore.MIN_Y + 40, hi = Math.min(g - 180 - s.height, -120);
        if (hi < lo) return;
        s.y0 = lo + (hi - lo) * unit(h, 9);
        int[] n = {100};
        java.util.function.DoubleSupplier rnd = () -> unit(h, n[0]++);
        double tr = s.trunkR, H = s.treeH;
        // The trunk, leaning a little, in three tapering lengths.
        double la = rnd.getAsDouble() * Math.PI * 2, lean = 4 + 6 * rnd.getAsDouble();
        double[] ys = {g - 6, g + H * 0.3, g + H * 0.6, g + H * 0.88};
        double[] rs = {tr * 1.15, tr * 0.85, tr * 0.6, 1.8};
        for (int k = 0; k < 3; k++) {
            double t0 = (ys[k] - g) / H, t1 = (ys[k + 1] - g) / H;
            s.tree.add(new Cap(s.x + Math.cos(la) * lean * t0 * t0, ys[k], s.z + Math.sin(la) * lean * t0 * t0,
                    s.x + Math.cos(la) * lean * t1 * t1, ys[k + 1], s.z + Math.sin(la) * lean * t1 * t1, rs[k], rs[k + 1], WOOD, false));
        }
        // Buttress roots flaring from the trunk into the ground.
        int nb = 8 + (int) (rnd.getAsDouble() * 4);
        for (int k = 0; k < nb; k++) {
            double a = (k + 0.4 * rnd.getAsDouble()) / nb * Math.PI * 2, top = g + 10 + 10 * rnd.getAsDouble(), out = tr * (2.0 + 0.9 * rnd.getAsDouble());
            s.tree.add(new Cap(s.x + Math.cos(a) * tr * 0.5, top, s.z + Math.sin(a) * tr * 0.5,
                    s.x + Math.cos(a) * out, g - 5, s.z + Math.sin(a) * out, 3.2 + 1.2 * rnd.getAsDouble(), 2.0, WOOD, false));
        }
        // Great boughs, bending upward, each splitting into smaller branches, leaves at their ends.
        int nboughs = 7 + (int) (rnd.getAsDouble() * 4);
        for (int k = 0; k < nboughs; k++) {
            double a = (k + 0.5 * rnd.getAsDouble()) / nboughs * Math.PI * 2;
            double t = 0.42 + 0.4 * rnd.getAsDouble(), by = g + H * t;
            double bx = s.x + Math.cos(la) * lean * t * t, bz = s.z + Math.sin(la) * lean * t * t;
            double len = 30 + 22 * rnd.getAsDouble() * (1.2 - t);
            double mx = bx + Math.cos(a) * len * 0.55, mz = bz + Math.sin(a) * len * 0.55, my = by + len * (0.18 + 0.15 * rnd.getAsDouble());
            double ex = bx + Math.cos(a) * len, ez = bz + Math.sin(a) * len, ey = my + len * (0.25 + 0.25 * rnd.getAsDouble());
            double r0 = 4.2 * (1.15 - t * 0.5);
            s.tree.add(new Cap(bx, by, bz, mx, my, mz, r0, r0 * 0.6, WOOD, false));
            s.tree.add(new Cap(mx, my, mz, ex, ey, ez, r0 * 0.6, 1.2, WOOD, false));
            s.leaves.add(new double[] {ex, ey + 2, ez, 11 + 5 * rnd.getAsDouble()});
            for (int q = 0; q < 3; q++) {
                double sa = a + (rnd.getAsDouble() - 0.5) * 2.2, sl = 12 + 9 * rnd.getAsDouble(), sy = my + (ey - my) * rnd.getAsDouble() * 0.6;
                double sx = mx + (ex - mx) * 0.3, sz = mz + (ez - mz) * 0.3;
                double fx = sx + Math.cos(sa) * sl, fz = sz + Math.sin(sa) * sl, fy = sy + sl * 0.45;
                s.tree.add(new Cap(sx, sy, sz, fx, fy, fz, 1.6, 0.8, WOOD, false));
                s.leaves.add(new double[] {fx, fy + 1, fz, 7 + 4 * rnd.getAsDouble()});
            }
        }
        // The crown over the trunk's top.
        double cx = s.x + Math.cos(la) * lean, cz = s.z + Math.sin(la) * lean;
        for (int k = 0; k < 4; k++) {
            double a = rnd.getAsDouble() * Math.PI * 2, r = 8 * rnd.getAsDouble();
            s.leaves.add(new double[] {cx + Math.cos(a) * r, g + H * 0.9 + 6 * rnd.getAsDouble(), cz + Math.sin(a) * r, 13 + 5 * rnd.getAsDouble()});
        }
        // Roots: a taproot straight down to the hall's floor, and great roots wandering down and out,
        // running level across the hall (bridges) before diving into its floor; branches off them.
        double floorY = s.y0 - 25;
        double px = s.x, py = g - 4, pz = s.z, pr = tr * 0.75; // thick: it is hollow
        while (py > floorY) {
            double ny = Math.max(floorY, py - 18);
            double nx = px + (rnd.getAsDouble() - 0.5) * 2.5, nz = pz + (rnd.getAsDouble() - 0.5) * 2.5;
            double nr = Math.max(4.0, pr * 0.985);
            Cap cap = new Cap(px, py, pz, nx, ny, nz, pr, nr, WOOD, false);
            s.bones.add(cap);
            s.tap.add(cap);
            px = nx; py = ny; pz = nz; pr = nr;
        }
        // Rooms off the hollow taproot every 26-34 blocks, each in its own bulb of wood (as in a
        // living tree in Terraria), joined to the shaft by a short passage.
        for (Cap cap : s.tap) {
            if (rnd.getAsDouble() > 0.62) continue;
            double ay = (cap.ay + cap.by) / 2;
            if (ay > g - 18 || ay < s.y0 + s.height) continue;
            double ax = (cap.ax + cap.bx) / 2, az = (cap.az + cap.bz) / 2, a = rnd.getAsDouble() * Math.PI * 2;
            double rx = 6 + 3 * rnd.getAsDouble(), ry = 4 + rnd.getAsDouble(), off = cap.ra + rx + 1;
            s.rooms.add(new double[] {ax + Math.cos(a) * off, ay, az + Math.sin(a) * off, rx, ry, ax, ay, az});
            s.bones.add(new Cap(ax + Math.cos(a) * off, ay, az + Math.sin(a) * off, ax + Math.cos(a) * off, ay + 0.1, az + Math.sin(a) * off,
                    rx + 2.4, rx + 2.4, WOOD, false)); // the bulb's shell (hollowed below)
        }
        int roots = 12 + (int) (rnd.getAsDouble() * 4);
        for (int k = 0; k < roots; k++) {
            double a = (k + 0.5 * rnd.getAsDouble()) / roots * Math.PI * 2;
            root(s, rnd, s.x + Math.cos(a) * tr * 0.8, g - 3, s.z + Math.sin(a) * tr * 0.8,
                    Math.cos(a) * 0.75, -0.65, Math.sin(a) * 0.75, tr * 0.42, 46, true);
        }
        // The gate: a slanting way in between two buttresses, down to the shaft round the taproot.
        double ga = la + Math.PI + 0.4;
        s.gate.add(new Cap(s.x + Math.cos(ga) * (tr * 2.3 + 3), g + 2, s.z + Math.sin(ga) * (tr * 2.3 + 3),
                s.x + Math.cos(ga) * tr * 0.1, g - 28, s.z + Math.sin(ga) * tr * 0.1, 3.0, 2.6, ROCK, false));
        double reach = Math.max(Math.max(s.rx, s.rz) + 20, 70);
        s.minX = s.x - reach; s.maxX = s.x + reach;
        s.minZ = s.z - reach; s.maxZ = s.z + reach;
        s.minY = s.y0 - 45; s.maxY = g + H + 26;
        s.present = true;
    }

    private void root(Site s, java.util.function.DoubleSupplier rnd, double x, double y, double z, double dx, double dy, double dz,
                      double r, int steps, boolean branches) {
        for (int k = 0; k < steps && r > 1.0; k++) {
            boolean inHall = y < s.y0 + s.height * 0.85 && y > s.y0 + 12 && Math.hypot((x - s.x) / s.rx, (z - s.z) / s.rz) < 0.9;
            dx += (rnd.getAsDouble() - 0.5) * 0.5;
            dz += (rnd.getAsDouble() - 0.5) * 0.5;
            dy += (rnd.getAsDouble() - 0.5) * 0.3 - 0.12;
            double lat = Math.hypot((x - s.x) / s.rx, (z - s.z) / s.rz);
            if (lat > 0.8) { // keep over the hall
                dx -= (x - s.x) / s.rx * 0.6;
                dz -= (z - s.z) / s.rz * 0.6;
            }
            if (inHall) dy *= 0.35; // run level across the hall
            double l = Math.sqrt(dx * dx + dy * dy + dz * dz);
            dx /= l; dy /= l; dz /= l;
            if (!inHall && dy > -0.35) dy = -0.35;
            double seg = 14 + 8 * rnd.getAsDouble();
            double nx = x + dx * seg, ny = y + dy * seg, nz = z + dz * seg, nr = r * (inHall ? 0.985 : 0.97);
            s.bones.add(new Cap(x, y, z, nx, ny, nz, r, nr, WOOD, false));
            if (branches && rnd.getAsDouble() < (inHall ? 0.4 : 0.2)) {
                double ba = rnd.getAsDouble() * Math.PI * 2;
                root(s, rnd, nx, ny, nz, Math.cos(ba), inHall ? 0.05 : -0.3, Math.sin(ba), nr * 0.65, inHall ? 14 : 10, false);
            }
            x = nx; y = ny; z = nz; r = nr;
            if (y < s.y0 - 25) break;
        }
    }

    private void rootColumn(Site s, Col c, int x, int z) {
        c.rGround = core.column(x, z).height();
        double u = (x + 0.5 - s.x) / s.rx, v = (z + 0.5 - s.z) / s.rz;
        double d = Math.sqrt(u * u + v * v) * (1 - 0.15 * Fractal.fbm(GradientNoise.fork(s.h, 1), x / 120.0, z / 120.0, 2));
        c.rIn = d <= 1;
        if (c.rIn) {
            c.rTop = s.y0 + s.height * Math.sqrt(1 - d * d) - 12 * Math.max(0, Fractal.fbm(GradientNoise.fork(s.h, 2), x / 40.0, z / 40.0, 2));
            c.rFloor = s.y0 + 6 * (Fractal.fbm(GradientNoise.fork(s.h, 3), x / 70.0, z / 70.0, 3) * 0.5 + 0.5) + 40 * smoothstep(0.7, 1.0, d);
        }
        c.treeCaps.clear();
        c.leafBlobs.clear();
        for (Cap b : s.tree) if (x >= b.minX && x <= b.maxX && z >= b.minZ && z <= b.maxZ) c.treeCaps.add(b);
        for (Cap b : s.gate) if (x >= b.minX - 1 && x <= b.maxX + 1 && z >= b.minZ - 1 && z <= b.maxZ + 1) c.treeCaps.add(b);
        for (double[] l : s.leaves) if (Math.abs(x + 0.5 - l[0]) < l[3] * 1.3 && Math.abs(z + 0.5 - l[2]) < l[3] * 1.3) c.leafBlobs.add(l);
        c.rTree = !c.treeCaps.isEmpty() || !c.leafBlobs.isEmpty();
    }

    private void worldroot(Site s, Col c, int x, int y, int z, Probe p) {
        if (y >= c.rGround + 1) return; // over the ground: the tree, placed by the dressing (treeAt)
        // Rooms: hollow inside their bulbs, the passage to the shaft open.
        for (double[] r : s.rooms) {
            double dx = (x + 0.5 - r[0]) / r[3], dy = (y + 0.5 - r[1]) / r[4], dz = (z + 0.5 - r[2]) / r[3];
            if (Math.abs(dx) > 1.6 || Math.abs(dz) > 1.6 || Math.abs(dy) > 2.5) continue;
            if (dx * dx + dy * dy + dz * dz < 1) {
                p.claimed = true;
                p.open = true;
                return;
            }
            double ex = r[5] - r[0], ez = r[7] - r[2], l = Math.hypot(ex, ez), t = ((x + 0.5 - r[0]) * ex + (z + 0.5 - r[2]) * ez) / (l * l);
            if (t > 0 && t < 1 && Math.hypot(x + 0.5 - r[0] - ex * t, z + 0.5 - r[2] - ez * t) < 1.6 && y >= r[1] - r[4] + 1 && y <= r[1] - r[4] + 4) {
                p.claimed = true;
                p.open = true;
                return;
            }
        }
        // The gate, and the hollow inside the taproot (a wall of wood 2-3 thick), with doors into the hall.
        for (Cap b : c.treeCaps) {
            if (b.mat != ROCK || y < b.minY || y > b.maxY) continue;
            if (b.dist(x + 0.5, y + 0.5, z + 0.5) < 0) {
                p.claimed = true;
                p.open = true;
                return;
            }
        }
        for (Cap b : s.tap) {
            if (y < b.minY || y > b.maxY || x < b.minX || x > b.maxX || z < b.minZ || z > b.maxZ) continue;
            double d = b.dist(x + 0.5, y + 0.5, z + 0.5);
            boolean door = c.rIn && y > c.rFloor && y < c.rFloor + 5 && d < 0;
            if (d < -2.4 || door) {
                p.claimed = true;
                p.open = true;
                return;
            }
        }
        for (Cap b : c.caps) {
            if (y < b.minY || y > b.maxY) continue;
            double d = b.dist(x + 0.5, y + 0.5, z + 0.5);
            if (d < 0) {
                p.claimed = true;
                p.protect = true;
                // glowing amber sap in veins near the root's skin
                p.material = d > -1.6 && Fractal.fbm3(GradientNoise.fork(s.h, 40), x / 7.0, y / 11.0, z / 7.0, 2) > 0.28 ? SAP : WOOD;
                return;
            }
        }
        if (!c.rIn) return;
        if (y > c.rFloor && y < c.rTop) {
            p.claimed = true;
            p.open = true;
        } else if (y > c.rFloor - 8 && y < c.rTop + 8) {
            p.claimed = true; // the hall's walls: passages may break in
        }
    }

    /** True if (x, y, z) is inside the Worldroot's hollow taproot (for the dressing: vines to climb). */
    public boolean inTaproot(int x, int y, int z) {
        Col c = column(x, z);
        for (Site s : c.sites) {
            if (s.kind != ROOT) continue;
            for (Cap b : s.tap) {
                if (y < b.minY || y > b.maxY || x < b.minX || x > b.maxX || z < b.minZ || z > b.maxZ) continue;
                if (b.dist(x + 0.5, y + 0.5, z + 0.5) < -2.4) return true;
            }
        }
        return false;
    }

    /** The Worldroot's rooms whose floor centre is in this column: {x, floor y, z} each. */
    public List<int[]> roomFloors(int x, int z) {
        List<int[]> out = new ArrayList<>();
        Col c = column(x, z);
        for (Site s : c.sites) {
            if (s.kind != ROOT) continue;
            for (double[] r : s.rooms) {
                if ((int) Math.floor(r[0]) == x && (int) Math.floor(r[2]) == z) out.add(new int[] {x, (int) Math.ceil(r[1] - r[4]), z, (int) Math.floor(r[1] + r[4])});
            }
        }
        return out;
    }

    /** True if part of the Worldroot's tree (above ground) stands over this column. */
    public boolean nearTree(int x, int z) {
        return column(x, z).rTree;
    }

    /** The Worldroot's tree at a block over the ground: WOOD, SAP, LEAVES, BLOSSOM, or NONE. */
    public int treeAt(int x, int y, int z) {
        Col c = column(x, z);
        if (!c.rTree) return NONE;
        Site s = null;
        for (Site t : c.sites) if (t.kind == ROOT) s = t;
        if (s == null) return NONE;
        for (Cap b : c.treeCaps) {
            if (b.mat == ROCK || y < b.minY || y > b.maxY) continue;
            double d = b.dist(x + 0.5, y + 0.5, z + 0.5);
            if (d < 0) return d > -1.4 && Fractal.fbm3(GradientNoise.fork(s.h, 41), x / 6.0, y / 14.0, z / 6.0, 2) > 0.3 ? SAP : WOOD;
        }
        for (double[] l : c.leafBlobs) {
            double dx = x + 0.5 - l[0], dy = (y + 0.5 - l[1]) * 1.5, dz = z + 0.5 - l[2];
            double r = l[3] * (0.8 + 0.35 * Fractal.fbm3(GradientNoise.fork(s.h, 42), x / 7.0, y / 7.0, z / 7.0, 2));
            if (dx * dx + dy * dy + dz * dz < r * r) {
                return Fractal.fbm3(GradientNoise.fork(s.h, 43), x / 11.0, y / 11.0, z / 11.0, 2) > 0.08 ? BLOSSOM : LEAVES;
            }
        }
        return NONE;
    }

    /** The Worldroot's ground at its trunk and its tree's top, for the dressing: {ground, top}, or null here. */
    public double[] treeSpan(int x, int z) {
        Col c = column(x, z);
        for (Site t : c.sites) if (t.kind == ROOT) return new double[] {t.tGround, t.tGround + t.treeH + 22};
        return null;
    }

    // ---- the Hanging Mountains

    private void buildPeaks(Site s, long h) {
        // Retired (the user, 2026-11-09: "they serve no purpose", bland, need a boat). Kept for a
        // future rework; no site is placed.
        if (s != null) return;
        s.rx = 300 + 150 * unit(h, 4);
        s.rz = s.rx * (0.75 + 0.25 * unit(h, 5));
        s.height = 230 + 50 * unit(h, 6);
        double g = ground(s.x, s.z, s.rx);
        double lo = AtlasCore.MIN_Y + 30, hi = Math.min(g - 90 - s.height, -200);
        if (hi < lo) return;
        s.y0 = lo + (hi - lo) * unit(h, 7);
        s.lake = s.y0 + 20;
        s.rangeA = unit(h, 8) * Math.PI;
        s.minX = s.x - s.rx - 10; s.maxX = s.x + s.rx + 10;
        s.minZ = s.z - s.rz - 10; s.maxZ = s.z + s.rz + 10;
        s.minY = s.y0 - 14; s.maxY = s.y0 + s.height + 14;
        s.present = true;
    }

    private void peaksColumn(Site s, Col c, int x, int z) {
        double u = (x + 0.5 - s.x) / s.rx, v = (z + 0.5 - s.z) / s.rz;
        double d = Math.sqrt(u * u + v * v) * (1 - 0.12 * Fractal.fbm(GradientNoise.fork(s.h, 1), x / 200.0, z / 200.0, 2));
        c.mIn = d <= 1;
        if (!c.mIn) return;
        double dome = s.y0 + s.height * Math.pow(1 - d * d, 0.35);
        double across = (x - s.x) * -Math.sin(s.rangeA) + (z - s.z) * Math.cos(s.rangeA);
        double env = Math.exp(-Math.pow(across / (0.55 * s.rz), 2)) * smoothstep(1.0, 0.55, d);
        // Ridged noise: sharp crests (here, the ridges hanging lowest), the range along its axis.
        double r1 = 1 - Math.abs(GradientNoise.noise2(GradientNoise.fork(s.h, 10), x / 170.0, z / 170.0));
        double r2 = 1 - Math.abs(GradientNoise.noise2(GradientNoise.fork(s.h, 11), x / 64.0, z / 64.0));
        double r3 = 1 - Math.abs(GradientNoise.noise2(GradientNoise.fork(s.h, 12), x / 23.0, z / 23.0));
        double m = 0.62 * r1 * r1 + 0.28 * r2 * r2 * r1 + 0.1 * r3 * r3;
        double hang = (s.height - 40) * 0.95 * Math.pow(m, 1.7) * env;
        double roof = dome - hang;
        double clear = s.lake + 12 + 8 * Fractal.fbm(GradientNoise.fork(s.h, 13), x / 50.0, z / 50.0, 2);
        if (roof < clear) {
            hang -= clear - roof;
            roof = clear;
        }
        c.mTop = roof;
        c.mHang = hang;
        double isl = 26 * Math.max(0, Fractal.fbm(GradientNoise.fork(s.h, 14), x / 70.0, z / 70.0, 3) - 0.38) / 0.62;
        c.mFloor = s.y0 + 4 + 7 * (Fractal.fbm(GradientNoise.fork(s.h, 3), x / 90.0, z / 90.0, 3) * 0.5 + 0.5) + isl
                + 55 * smoothstep(0.8, 1.0, d);
    }

    private void peaks(Site s, Col c, int x, int y, int z, Probe p) {
        if (!c.mIn) return;
        double top = c.mTop, floor = c.mFloor;
        if (y < floor - 8 || y > top + 8) return;
        p.claimed = true;
        if (y <= floor) {
            p.protect = y > floor - 6 && floor < s.lake + 1; // the lake bed
            return;
        }
        if (y > top) {
            // The peaks' tips crusted in ice: blue ice at the lowest, packed ice above.
            if (y <= top + 4 && c.mHang > 60) {
                double tipAbove = top - s.lake;
                p.protect = true;
                p.material = tipAbove < 40 ? BLUE_ICE : tipAbove < 95 ? PACKED_ICE : ROCK;
            }
            return;
        }
        p.open = true;
        p.water = y <= s.lake;
    }

    /** The rising fall's axis at height y: {x, z}. */
    private static double[] riseAxis(Site s, double y) {
        double t = (y - s.pLev) / 23.0;
        return new double[] {s.pX + s.swayA * Math.sin(t + s.swayP), s.pZ + s.swayA * Math.sin(t * 0.77 + s.swayQ)};
    }

    /** Which aurora colour a lake-bed block takes (0 green .. 4 rose): ribbons that wave across the lake. */
    public int auroraBand(int x, int z) {
        Col c = column(x, z);
        Site v = null;
        for (Site s : c.sites) if (s.kind == VAULT) v = s;
        if (v == null) return 0;
        double along = (x - v.x) * Math.cos(v.bandA) + (z - v.z) * Math.sin(v.bandA);
        double w = along / 34.0 + 1.6 * Fractal.fbm(GradientNoise.fork(v.h, 21), x / 70.0, z / 70.0, 2)
                + 0.25 * Fractal.fbm(GradientNoise.fork(v.h, 22), x / 9.0, z / 9.0, 1);
        double t = w - Math.floor(w);
        // green dominant, as auroras are, with cyan, blue, violet and rose edges
        return t < 0.36 ? 0 : t < 0.52 ? 1 : t < 0.66 ? 2 : t < 0.82 ? 3 : 4;
    }

    /** A random unit direction, from below if {@code low}. */
    private static double[] direction(long h, boolean low) {
        double z = low ? -unit(h, 0) : unit(h, 0) * 2 - 1, a = unit(h, 1) * Math.PI * 2, r = Math.sqrt(1 - z * z);
        return new double[] {r * Math.cos(a), z, r * Math.sin(a)};
    }

    private void buildTitan(Site s, long h) {
        double len = 900 + 500 * unit(h, 4);
        s.height = 180;
        double g = ground(s.x, s.z, len * 0.6);
        double spine = Math.min(-120 - 140 * unit(h, 5), g - 130);
        if (spine - 140 < AtlasCore.MIN_Y + 30) return;
        s.y0 = spine;
        int n = (int) (len / 22);
        s.sx = new double[n];
        s.sy = new double[n];
        s.sz = new double[n];
        double yaw = Math.atan2(s.sin, s.cos), yawV = 0;
        double px = s.x - s.cos * len / 2, pz = s.z - s.sin * len / 2;
        long rs = GradientNoise.fork(h, 9);
        for (int k = 0; k < n; k++) {
            s.sx[k] = px;
            s.sz[k] = pz;
            double t = (double) k / (n - 1);
            s.sy[k] = spine - 18 * Math.sin(Math.PI * t) * (unit(rs, k) * 0.2 + 0.9); // sags a little along its length
            yawV = yawV * 0.9 + (unit(rs, 1000 + k) - 0.5) * 0.02;
            yaw += yawV;
            px += Math.cos(yaw) * 22;
            pz += Math.sin(yaw) * 22;
        }
        s.dirX = s.sx[1] - s.sx[0];
        s.dirZ = s.sz[1] - s.sz[0];
        double dl = Math.hypot(s.dirX, s.dirZ);
        s.dirX /= dl;
        s.dirZ /= dl;
        // The skull: ahead of the first vertebra.
        s.skullX = s.sx[0] - s.dirX * 80;
        s.skullZ = s.sz[0] - s.dirZ * 80;
        s.skullY = s.sy[0] - 30;
        // Bones: spine cord and vertebrae with dorsal spikes, ribs over the chest, all tapering to tail and neck.
        for (int k = 0; k < n; k++) {
            double t = (double) k / (n - 1);
            double girth = Math.min(1, t * 5) * (1 - 0.8 * smoothstep(0.6, 1, t)) * 0.75 + 0.25;
            if (k + 1 < n) s.bones.add(new Cap(s.sx[k], s.sy[k], s.sz[k], s.sx[k + 1], s.sy[k + 1], s.sz[k + 1], 5 * girth, 5 * girth));
            s.bones.add(new Cap(s.sx[k], s.sy[k] - 3, s.sz[k], s.sx[k], s.sy[k] + 3, s.sz[k], 11 * girth, 11 * girth));
            s.bones.add(new Cap(s.sx[k], s.sy[k] + 6, s.sz[k], s.sx[k], s.sy[k] + 6 + 26 * girth, s.sz[k], 5 * girth, 0.8));
            {   // transverse processes, out to each side
                double fx = k + 1 < n ? s.sx[k + 1] - s.sx[k] : s.dirX, fz = k + 1 < n ? s.sz[k + 1] - s.sz[k] : s.dirZ;
                double fl = Math.hypot(fx, fz), sx = -fz / fl, szz = fx / fl, w = 15 * girth;
                s.bones.add(new Cap(s.sx[k] - sx * w, s.sy[k] - 2, s.sz[k] - szz * w, s.sx[k] + sx * w, s.sy[k] - 2, s.sz[k] + szz * w, 3.5 * girth, 3.5 * girth));
            }
            if (t > 0.16 && t < 0.62 && k % 2 == 0) {
                double fx = k + 1 < n ? s.sx[k + 1] - s.sx[k] : s.dirX, fz = k + 1 < n ? s.sz[k + 1] - s.sz[k] : s.dirZ;
                double fl = Math.hypot(fx, fz);
                double sideX = -fz / fl, sideZ = fx / fl;
                double rr = 58 + 14 * Math.sin(Math.PI * (t - 0.16) / 0.46);
                for (int side = -1; side <= 1; side += 2) {
                    double lx = s.sx[k], ly = s.sy[k], lz = s.sz[k];
                    // A rib in three is broken off partway.
                    int last = unit(rs, 2000 + k * 2 + (side + 1) / 2) < 0.33 ? 6 + (int) (unit(rs, 3000 + k * 2 + side) * 6) : 14;
                    for (int q = 1; q <= last; q++) {
                        double th = q / 14.0 * Math.PI * 0.86;
                        double ox = side * sideX * rr * Math.sin(th), oz = side * sideZ * rr * Math.sin(th);
                        double oy = rr * Math.cos(th) - rr;
                        double nxp = s.sx[k] + ox, nyp = s.sy[k] + oy, nzp = s.sz[k] + oz;
                        double rad = 8.5 * (1 - 0.6 * q / 14.0);
                        s.bones.add(new Cap(lx, ly, lz, nxp, nyp, nzp, rad + 0.6, rad));
                        lx = nxp;
                        ly = nyp;
                        lz = nzp;
                    }
                }
            }
        }
        titanLimbsAndSkull(s, n, rs);
        double reach = len / 2 + 260;
        s.minX = s.x - reach; s.maxX = s.x + reach;
        s.minZ = s.z - reach; s.maxZ = s.z + reach;
        s.minY = spine - 150; s.maxY = spine + 110;
        s.present = true;
    }

    /** Pelvis and limbs lying sprawled, teeth and horns on the skull, and bone scattered over the floor. */
    private void titanLimbsAndSkull(Site s, int n, long rs) {
        double floorY = s.y0 - 108;
        for (int[] at : new int[][] {{(int) Math.round(0.2 * (n - 1)), 0}, {(int) Math.round(0.66 * (n - 1)), 1}}) {
            int k = at[0];
            boolean hind = at[1] == 1;
            double fx = s.sx[Math.min(n - 1, k + 1)] - s.sx[k], fz = s.sz[Math.min(n - 1, k + 1)] - s.sz[k], fl = Math.hypot(fx, fz);
            double ux = fx / fl, uz = fz / fl, sx = -uz, sz = ux; // u: toward the tail
            if (hind) { // the pelvis: two wings down and out from the spine
                for (int side = -1; side <= 1; side += 2) {
                    s.bones.add(new Cap(s.sx[k], s.sy[k] - 4, s.sz[k], s.sx[k] + side * sx * 32, s.sy[k] - 30, s.sz[k] + side * sz * 32, 13, 8));
                }
            }
            double size = hind ? 1.0 : 0.8;
            for (int side = -1; side <= 1; side += 2) {
                double jx = s.sx[k] + side * sx * 28, jy = s.sy[k] - 30, jz = s.sz[k] + side * sz * 28;
                double kx = jx + side * sx * 50 * size - ux * 35 * size, ky = floorY + 12, kz = jz + side * sz * 50 * size - uz * 35 * size;
                double fx2 = kx + side * sx * 12 + (hind ? ux : -ux) * 48 * size, fy2 = floorY + 4, fz2 = kz + side * sz * 12 + (hind ? uz : -uz) * 48 * size;
                s.bones.add(new Cap(jx, jy, jz, kx, ky, kz, 10 * size, 8 * size));
                s.bones.add(new Cap(kx, ky, kz, fx2, fy2, fz2, 8 * size, 6 * size));
                for (int toe = -1; toe <= 1; toe++) { // toes, or claws on the fore feet
                    double tx = fx2 - ux * 24 * size + side * sx * toe * 9, tz = fz2 - uz * 24 * size + side * sz * toe * 9;
                    s.bones.add(new Cap(fx2, fy2, fz2, tx, floorY + 2, tz, 3.2 * size, hind ? 1.6 : 0.6));
                }
            }
        }
        // The skull's teeth: a row down each side of the upper jaw's edge and up from the lower jaw.
        double fwdX = -s.dirX, fwdZ = -s.dirZ, sdX = -fwdZ, sdZ = fwdX;
        for (int q = 0; q < 9; q++) {
            double along = 44 + q * 9, t = (along + 70) / 190.0, hw = skullHalfW(t) - 4, bot = skullBottom(t);
            for (int side = -1; side <= 1; side += 2) {
                double tx = s.skullX + fwdX * along + sdX * side * hw, tz = s.skullZ + fwdZ * along + sdZ * side * hw;
                double len = q == 1 ? 18 : 9 + 3 * Math.sin(q * 1.7); // a fang at the front
                s.bones.add(new Cap(tx, s.skullY + bot + 2, tz, tx, s.skullY + bot - len, tz, 3.0, 0.4));
                s.bones.add(new Cap(tx, s.skullY - 48, tz, tx, s.skullY - 40 + (q == 2 ? 6 : 0), tz, 2.4, 0.4));
            }
        }
        // Heavy brows over the eye sockets, and a rim of bone round each socket, so the eyes read
        // from outside the skull.
        for (int side = -1; side <= 1; side += 2) {
            double t0 = (10 + 70) / 190.0, sx = skullHalfW(t0) * 0.93;
            s.bones.add(new Cap(s.skullX + fwdX * -10 + sdX * side * (sx + 1), s.skullY + 40, s.skullZ + fwdZ * -10 + sdZ * side * (sx + 1),
                    s.skullX + fwdX * 32 + sdX * side * (sx - 4), s.skullY + 38, s.skullZ + fwdZ * 32 + sdZ * side * (sx - 4), 6.5, 4.5));
            double pa = 0, pyy = 0;
            for (int q = 0; q <= 14; q++) {
                double a = q / 14.0 * Math.PI * 2, al = 10 + 17 * Math.cos(a), yy = 25 + 13 * Math.sin(a);
                if (q > 0) {
                    s.bones.add(new Cap(s.skullX + fwdX * pa + sdX * side * (sx + 1.5), s.skullY + pyy, s.skullZ + fwdZ * pa + sdZ * side * (sx + 1.5),
                            s.skullX + fwdX * al + sdX * side * (sx + 1.5), s.skullY + yy, s.skullZ + fwdZ * al + sdZ * side * (sx + 1.5), 2.6, 2.6));
                }
                pa = al;
                pyy = yy;
            }
        }
        // The lower jaw: two long bones under the skull meeting at the chin.
        for (int side = -1; side <= 1; side += 2) {
            s.bones.add(new Cap(s.skullX - fwdX * 20 + sdX * side * 52, s.skullY - 48, s.skullZ - fwdZ * 20 + sdZ * side * 52,
                    s.skullX + fwdX * 116 + sdX * side * 8, s.skullY - 50, s.skullZ + fwdZ * 116 + sdZ * side * 8, 7, 5.5));
        }
        // Two great horns sweeping back and up from the crown, curling at the tips.
        for (int side = -1; side <= 1; side += 2) {
            double hx = s.skullX - fwdX * 30 + sdX * side * 40, hy = s.skullY + 40, hz = s.skullZ - fwdZ * 30 + sdZ * side * 40;
            double back = 0, rise = 0, out = 0;
            for (int q = 1; q <= 9; q++) {
                double t = q / 9.0;
                double nb = 140 * t, nr = 70 * Math.sin(Math.PI * t * 0.8), no = 30 * t;
                double ax = hx - fwdX * back + sdX * side * out, ay = hy + rise, az = hz - fwdZ * back + sdZ * side * out;
                double bx = hx - fwdX * nb + sdX * side * no, by = hy + nr, bz = hz - fwdZ * nb + sdZ * side * no;
                s.bones.add(new Cap(ax, ay, az, bx, by, bz, 11 * (1 - 0.8 * (q - 1) / 9.0), 11 * (1 - 0.8 * q / 9.0)));
                back = nb;
                rise = nr;
                out = no;
            }
        }
        // Bone scattered over the floor of the hall.
        for (int q = 0; q < 60; q++) {
            int k = (int) (unit(rs, 5000 + q) * (n - 1));
            double fx = s.sx[Math.min(n - 1, k + 1)] - s.sx[k], fz = s.sz[Math.min(n - 1, k + 1)] - s.sz[k], fl = Math.hypot(fx, fz);
            double off = (unit(rs, 5100 + q) * 2 - 1) * 130, a = unit(rs, 5200 + q) * Math.PI * 2, len = 6 + 16 * unit(rs, 5300 + q);
            double px = s.sx[k] + (-fz / fl) * off, pz = s.sz[k] + (fx / fl) * off;
            s.bones.add(new Cap(px, floorY + 2, pz, px + Math.cos(a) * len, floorY + 2 + unit(rs, 5400 + q) * 4, pz + Math.sin(a) * len,
                    1.5 + 2 * unit(rs, 5500 + q), 1.2));
        }
    }

    // ---- per column

    private Col column(int x, int z) {
        Col[] table = cols.get();
        int slot = (x & 7) << 3 | (z & 7);
        Col c = table[slot];
        if (c == null) table[slot] = c = new Col();
        if (c.x == x && c.z == z) return c;
        c.x = x;
        c.z = z;
        c.sites.clear();
        c.caps.clear();
        // Every kind's flags start clear: they are set only by a site of that kind over this column,
        // and a stale one (vWell, say, read by nearWell) would be the last column's.
        c.rTree = c.vWell = c.vIn = c.vLake = c.vPool = c.hIn = c.tIn = c.rIn = c.mIn = false;
        for (int kind = HOLLOW; kind <= PEAKS; kind++) {
            double cell = cellOf(kind);
            int ci = GradientNoise.floor(x / cell), cj = GradientNoise.floor(z / cell);
            // The present sites of this kind in the 3x3 cells round the column, remembered while the
            // column stays in the same cell: asking the site table 54 times a column (six kinds,
            // nine cells) was a tenth of chunk generation (profiled 2026-11-10).
            if (c.nearI[kind] != ci || c.nearJ[kind] != cj || c.nearSites[kind] == null) {
                c.nearI[kind] = ci;
                c.nearJ[kind] = cj;
                List<Site> found = new ArrayList<>(2);
                for (int dj = -1; dj <= 1; dj++) {
                    for (int di = -1; di <= 1; di++) {
                        Site s = site(kind, ci + di, cj + dj);
                        if (s.present) found.add(s);
                    }
                }
                c.nearSites[kind] = found.toArray(new Site[0]);
            }
            {
                for (Site s : c.nearSites[kind]) {
                    if (x < s.minX || x > s.maxX || z < s.minZ || z > s.maxZ) continue;
                    c.sites.add(s);
                    for (Cap b : s.bones) if (x >= b.minX && x <= b.maxX && z >= b.minZ && z <= b.maxZ) c.caps.add(b);
                    if (kind == ROOT) {
                        rootColumn(s, c, x, z);
                    } else if (kind == PEAKS) {
                        peaksColumn(s, c, x, z);
                    } else if (kind == VAULT) {
                        vaultColumn(s, c, x, z);
                    } else if (kind == TITAN) {
                        titanColumn(s, c, x, z);
                    } else if (kind == HOLLOW) {
                        hollowColumn(s, c, x, z);
                    }
                }
            }
        }
        return c;
    }

    /**
     * True if a box meets a part of a set piece that nothing may be built through. Structures are
     * welcome in set pieces (the user, 2026-11-10: "crazy structure overlap is one of the cool parts
     * of Minecraft", a mineshaft that leads into an Aurora Vault is good), all but these parts:
     * <ul>
     *   <li>the Aurora Vault's hanging lake, the Aurora Well's shaft down into it, and the rising
     *       fall's column (a corridor through them would stand dry inside the water);</li>
     *   <li>the Worldroot's trunk and its taproot shaft;</li>
     *   <li>Titan's skull.</li>
     * </ul>
     * The Hollow, the Great Geode, the vault's floor and walls, the root hall and the rest of the
     * grave are open to them.
     */
    public boolean guarded(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        for (int kind : new int[] {TITAN, VAULT, ROOT}) {
            double cell = cellOf(kind);
            int i0 = GradientNoise.floor(minX / cell) - 1, i1 = GradientNoise.floor(maxX / cell) + 1;
            int j0 = GradientNoise.floor(minZ / cell) - 1, j1 = GradientNoise.floor(maxZ / cell) + 1;
            for (int j = j0; j <= j1; j++) {
                for (int i = i0; i <= i1; i++) {
                    Site s = site(kind, i, j);
                    if (!s.present || maxX < s.minX || minX > s.maxX || maxZ < s.minZ || minZ > s.maxZ || maxY < s.minY || minY > s.maxY) continue;
                    if (kind == VAULT) {
                        double lx = s.lakeR * s.rx * 1.2 + 6, lz = s.lakeR * s.rz * 1.2 + 6;
                        if (hits(minX, minY, minZ, maxX, maxY, maxZ, s.x - lx, s.lake - 8, s.z - lz, s.x + lx, s.y0 + s.height + 16, s.z + lz)) return true;
                        double pr = s.pR + s.swayA + 8;
                        if (hits(minX, minY, minZ, maxX, maxY, maxZ, s.pX - pr, s.pLev - 10, s.pZ - pr, s.pX + pr, s.lake, s.pZ + pr)) return true;
                        if (s.well && hits(minX, minY, minZ, maxX, maxY, maxZ, s.wX - s.wR - 8, s.lake, s.wZ - s.wR - 8, s.wX + s.wR + 8, s.wTop + 24, s.wZ + s.wR + 8)) return true;
                    } else if (kind == ROOT) {
                        double r = s.trunkR + 20;
                        if (hits(minX, minY, minZ, maxX, maxY, maxZ, s.x - r, s.minY, s.z - r, s.x + r, s.maxY, s.z + r)) return true;
                    } else if (hits(minX, minY, minZ, maxX, maxY, maxZ, s.skullX - 75, s.skullY - 45, s.skullZ - 75, s.skullX + 75, s.skullY + 65, s.skullZ + 75)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static boolean hits(int minX, int minY, int minZ, int maxX, int maxY, int maxZ, double ax, double ay, double az, double bx, double by, double bz) {
        return maxX >= ax && minX <= bx && maxY >= ay && minY <= by && maxZ >= az && minZ <= bz;
    }

    /**
     * The set piece a block is inside, as a key that is the same everywhere in that piece and
     * different for every piece (its kind in the low three bits), or 0 outside them all. For naming
     * places ({@code world/PlaceNames}).
     */
    public long placeKey(int x, int y, int z) {
        Probe p = sample(x, y, z);
        if (!p.claimed) return 0;
        int kind = p.kind;
        for (Site s : column(x, z).sites) {
            if (s.kind == kind && y >= s.minY && y <= s.maxY) return (GradientNoise.mix(s.h) & ~7L) | kind;
        }
        return 0;
    }

    /** True if some set piece's volume holds this column at all (cheap). */
    public boolean nearColumn(int x, int z) {
        return !column(x, z).sites.isEmpty();
    }

    /**
     * The highest set-piece water over this column (the Hollow's lake and its islands' spill pools,
     * the Geode's bowl lake), or NaN if none: how high DeepWater has to look.
     */
    public double waterTop(int x, int z) {
        Col c = column(x, z);
        double top = Double.NaN;
        for (Site s : c.sites) {
            double w = Double.NaN;
            if (s.kind == HOLLOW && c.hIn) {
                w = s.lake;
                for (Island is : c.islands) {
                    if (is.poolR > 0 && Math.hypot(x + 0.5 - is.px, z + 0.5 - is.pz) < is.poolR) w = Math.max(w, is.level);
                }
            } else if (s.kind == GEODE && Math.hypot(x + 0.5 - s.x, z + 0.5 - s.z) < s.r + 10) {
                w = s.y0 - s.r * 0.72;
            } else if (s.kind == PEAKS && c.mIn) {
                w = s.lake;
            } else if (s.kind == VAULT && c.vIn) {
                w = c.vLake ? c.vTop + 1 : Math.max(s.pLev, c.vTop);
            }
            if (!Double.isNaN(w)) top = Double.isNaN(top) ? w : Math.max(top, w);
        }
        return top;
    }

    // ---- per block

    /** Result of {@link #sample}: inside a piece's volume, open, water, protected, material. */
    public static final class Probe {
        public boolean claimed, open, water, protect, island;
        /** Water held up with nothing under it (the hanging lake), and the rising fall's column. */
        public boolean hang, rise;
        public int material, kind;
    }

    private final ThreadLocal<Probe> probes = ThreadLocal.withInitial(Probe::new);

    /** What the set pieces make of a block (a shared per-thread object: read it before the next call). */
    public Probe sample(int x, int y, int z) {
        Col c = column(x, z);
        Probe p = c.probe; // one per thread, kept with the column so a sample costs one thread-local read, not two
        p.claimed = p.open = p.water = p.protect = p.island = p.hang = p.rise = false;
        p.material = ROCK;
        p.kind = NONE;
        for (Site s : c.sites) {
            if (y < s.minY || y > s.maxY) continue;
            switch (s.kind) {
                case HOLLOW -> hollow(s, c, x, y, z, p);
                case GEODE -> geode(s, x, y, z, p);
                case TITAN -> titan(s, c, x, y, z, p);
                case VAULT -> vault(s, c, x, y, z, p);
                case ROOT -> worldroot(s, c, x, y, z, p);
                default -> peaks(s, c, x, y, z, p);
            }
            if (p.claimed) {
                p.kind = s.kind;
                return p;
            }
        }
        return p;
    }

    /** Negative where a set piece carves the block, {@link Caves#NONE} elsewhere. */
    public double carve(int x, int y, int z) {
        Probe p = sample(x, y, z);
        return p.open ? -2 : Caves.NONE;
    }

    private void vaultColumn(Site s, Col c, int x, int z) {
        c.vWell = s.well && Math.hypot(x + 0.5 - s.wX, z + 0.5 - s.wZ) < s.wR + 30;
        double u = (x + 0.5 - s.x) / s.rx, v = (z + 0.5 - s.z) / s.rz;
        double lobes = 0.15 * Fractal.fbm(GradientNoise.fork(s.h, 1), x / 150.0, z / 150.0, 2);
        double d = Math.sqrt(u * u + v * v) * (1 - lobes);
        c.vIn = d <= 1.0;
        c.vLake = c.vPool = false;
        if (!c.vIn) return;
        double dome = s.y0 + s.height * Math.sqrt(1 - d * d);
        double dl = d / s.lakeR * (1 - 0.12 * Fractal.fbm(GradientNoise.fork(s.h, 4), x / 60.0, z / 60.0, 2));
        c.vDl = dl;
        if (dl < 1) {
            c.vLake = true;
            c.vTop = Math.max(dome, s.lake + 3) + 2.5 * Fractal.fbm(GradientNoise.fork(s.h, 5), x / 22.0, z / 22.0, 2);
        } else {
            // The lip that holds the lake, then the dome on out to the walls, sagging here and there.
            double lip = s.lake - 5 - 2 * Fractal.fbm(GradientNoise.fork(s.h, 7), x / 14.0, z / 14.0, 1);
            double roof = lip + (dome - lip) * smoothstep(1.0, 1.45, dl);
            c.vTop = Math.min(dome, roof) - 10 * Math.max(0, Fractal.fbm(GradientNoise.fork(s.h, 6), x / 45.0, z / 45.0, 2)) * smoothstep(1.3, 1.7, dl);
        }
        double floor = s.y0 + 8 * (Fractal.fbm(GradientNoise.fork(s.h, 3), x / 90.0, z / 90.0, 3) * 0.5 + 0.5) + 44 * smoothstep(0.7, 1.0, d);
        double dp = Math.hypot(x + 0.5 - s.pX, z + 0.5 - s.pZ) / s.pR * (1 - 0.15 * Fractal.fbm(GradientNoise.fork(s.h, 8), x / 12.0, z / 12.0, 1));
        if (dp < 1.25) floor = Math.min(floor, s.pLev + 1 - 8 * Math.max(0, 1 - dp * dp) + 3 * smoothstep(0.85, 1.25, dp));
        c.vPool = dp < 1.0;
        c.vFloor = floor;
    }

    private void vault(Site s, Col c, int x, int y, int z, Probe p) {
        if (!c.vIn) return;
        double top = c.vTop, floor = c.vFloor;
        if (c.vWell && c.vLake && y > top && y <= s.wTop + 12) {
            // The Aurora Well: open from the lake bed to the sky, wavering a little, flaring at the mouth,
            // lined with crystal (and calcite between) where it runs through the rock.
            double ax = s.wX + 2 * Math.sin(y / 19.0 + s.swayP), az = s.wZ + 2 * Math.sin(y / 23.0 + s.swayQ);
            double d = Math.hypot(x + 0.5 - ax, z + 0.5 - az);
            double r = s.wR * (1 + 0.7 * smoothstep(s.wTop - 9, s.wTop + 1, y));
            if (d < r) {
                p.claimed = true;
                p.open = true; // the lake itself carries on under the shaft
                return;
            }
            if (d < r + 2.2 && y > top && y < s.wTop - 2) {
                p.claimed = true;
                p.protect = true;
                p.material = Fractal.fbm3(GradientNoise.fork(s.h, 30), x / 6.0, y / 6.0, z / 6.0, 2) > -0.15 ? AURORA : CALCITE;
                return;
            }
            if (y > top + 9) return;
        }
        if (y < floor - 8 || y > top + 9) return;
        p.claimed = true;
        if (y <= floor) {
            p.protect = c.vPool && y > floor - 6; // the pool's bed
            return;
        }
        if (y > top) {
            if (c.vLake) {
                p.protect = true; // the lake's bed: never breached, or the lake would hang over a hole
                if (y <= top + 3) p.material = AURORA;
            } else if (c.vDl < 1.35 && y <= top + 5) {
                p.protect = true; // the lip holding it
            }
            return;
        }
        if (top - floor < 2) return; // the walls closing in
        for (Cap b : c.caps) {
            if (y < b.minY || y > b.maxY) continue;
            if (b.dist(x + 0.5, y + 0.5, z + 0.5) < 0) {
                p.protect = true;
                p.material = b.mat;
                return;
            }
        }
        p.open = true;
        if (y >= s.pLev - 1 && y <= s.lake + 1) {
            double[] a = riseAxis(s, y);
            double r = s.colR * (1 + 0.15 * Math.sin(y / 5.0 + s.swayP)) * (1 + 1.6 * smoothstep(s.lake - 16, s.lake + 1, y));
            if (Math.hypot(x + 0.5 - a[0], z + 0.5 - a[1]) < r) {
                p.water = true;
                p.rise = true;
                return;
            }
        }
        if (c.vLake && y > s.lake) {
            p.water = true;
            p.hang = true;
        } else if (c.vPool && y <= s.pLev) {
            p.water = true;
        }
    }

    private void hollowColumn(Site s, Col c, int x, int z) {
        double u = (x - s.x) / s.rx, v = (z - s.z) / s.rz;
        double lobes = 0.22 * Fractal.fbm(GradientNoise.fork(s.h, 1), x / 420.0, z / 420.0, 2);
        double d = Math.sqrt(u * u + v * v) * (1 - lobes);
        c.hIn = d <= 1.02;
        c.islands.clear();
        if (!c.hIn) return;
        c.hD = d;
        c.hDome = s.y0 + s.height * Math.sqrt(Math.max(0, 1 - d * d))
                - 22 * Math.max(0, Fractal.fbm(GradientNoise.fork(s.h, 2), x / 60.0, z / 60.0, 2)) * smoothstep(1, 0.3, d);
        c.hFloor = s.y0 + 12 + 26 * (Fractal.fbm(GradientNoise.fork(s.h, 3), x / 160.0, z / 160.0, 3) * 0.5 + 0.5)
                + 60 * smoothstep(0.78, 1.0, d); // shores rise to the walls
        for (Island is : s.islands) {
            if (Math.hypot(x + 0.5 - is.x, z + 0.5 - is.z) < is.r * 1.2) c.islands.add(is);
        }
    }

    private void hollow(Site s, Col c, int x, int y, int z, Probe p) {
        if (!c.hIn) return;
        double dome = c.hDome, floor = c.hFloor;
        if (y < floor - 10 || y > dome + 6) return;
        p.claimed = true;
        if (y <= floor) {
            p.protect = y > floor - 9 && floor < s.lake + 1; // the lake's bed
            return;
        }
        if (y > dome) return;
        // Floating islands: solid inside one (mossy top, tapering cone below).
        for (Island is : c.islands) {
            double dx = x + 0.5 - is.x, dz = z + 0.5 - is.z, di = Math.sqrt(dx * dx + dz * dz);
            double edge = is.r * (1 + 0.18 * GradientNoise.noise2(is.h, x / 17.0, z / 17.0));
            if (di >= edge || y > is.y + is.cap + 6 || y < is.y - is.depth - 6) continue;
            double t = di / edge;
            double top, bottom;
            if (is.type == 1) { // spire: pointed both ways
                top = is.y + is.cap * Math.pow(Math.max(0, 1 - t), 1.2);
                bottom = is.y - is.depth * Math.pow(Math.max(0, 1 - t), 1.1);
            } else if (is.type == 2) { // mesa: flat top, sheer sides, blunt below
                top = is.y + is.cap + 1.5 * GradientNoise.noise2(is.h ^ 0x7, x / 9.0, z / 9.0);
                bottom = is.y - is.depth * Math.pow(Math.max(0, 1 - t), 0.4) - 3 * GradientNoise.noise2(is.h ^ 0x8, x / 11.0, z / 11.0);
            } else {
                top = is.y + is.cap * Math.sqrt(Math.max(0, 1 - t * t)) + 2.5 * GradientNoise.noise2(is.h ^ 0x7, x / 9.0, z / 9.0);
                bottom = is.y - is.depth * Math.pow(Math.max(0, 1 - t), 0.85) - 4 * GradientNoise.noise2(is.h ^ 0x8, x / 11.0, z / 11.0);
            }
            if (is.poolR > 0) {
                double dp = Math.hypot(x + 0.5 - is.px, z + 0.5 - is.pz);
                if (dp < is.poolR) top -= 5 * (1 - (dp / is.poolR) * (dp / is.poolR)); // the bowl
                if (dp < is.poolR && y > top && y <= is.level) {
                    p.open = true;
                    p.water = true;
                    return;
                }
            }
            if (y > bottom && y <= top) {
                p.protect = true;
                p.island = true;
                p.material = strata(x, y, z);
                return;
            }
        }
        for (Cap b : c.caps) {
            if (y < b.minY || y > b.maxY) continue;
            if (b.dist(x + 0.5, y + 0.5, z + 0.5) < 0) {
                p.protect = true;
                p.island = b.island;
                p.material = b.island ? strata(x, y, z) : b.mat == BONE ? ROCK : b.mat;
                return;
            }
        }
        p.open = true;
        p.water = y <= s.lake;
    }

    /** The islands' layered rock: bands of deepslate, tuff and calcite that wave a little. */
    private int strata(int x, int y, int z) {
        double w = y + 4 * GradientNoise.noise2(seed ^ 0x5751L, x / 40.0, z / 40.0);
        int band = Math.floorMod((int) Math.floor(w / 5), 5);
        return band == 1 || band == 3 ? TUFF : band == 4 ? CALCITE : ROCK;
    }

    private void geode(Site s, int x, int y, int z, Probe p) {
        double dx = x + 0.5 - s.x, dy = y + 0.5 - s.y0, dz = z + 0.5 - s.z;
        double r = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (r > s.r + 62) return;
        double lump = r < s.r - 30 ? 0 : 9 * Fractal.fbm3(GradientNoise.fork(s.h, 1), x / 70.0, y / 70.0, z / 70.0, 2);
        double inner = s.r + lump;
        // The crack: a wedge through the shell on one side, and on out as a fissure.
        double across = dx * -s.nz + dz * s.nx, out = dx * s.nx + dz * s.nz;
        double crackW = (4 + 10 * smoothstep(s.r + 60, s.r * 0.6, out)) * smoothstep(-s.r * 0.6, s.r * 0.2, dy);
        boolean crack = out > s.r * 0.5 && out < s.r + 60 && Math.abs(across + 6 * GradientNoise.noise2(s.h, y / 20.0, out / 25.0)) < crackW
                && Math.abs(dy) < s.r * 0.55;
        if (r > inner + 14 && !(crack && r < s.r + 60)) return;
        p.claimed = true;
        if (r < inner || crack) {
            if (r < inner) {
                Col c = column(x, z);
                for (Cap b : c.caps) {
                    if (y < b.minY || y > b.maxY) continue;
                    if (b.dist(x + 0.5, y + 0.5, z + 0.5) < 0) {
                        p.protect = true;
                        p.material = b.mat;
                        return;
                    }
                }
            }
            p.open = true;
            p.water = r < inner && y < s.y0 - s.r * 0.72; // a lake pooled in the bowl
            return;
        }
        p.protect = true;
        double depth = r - inner;
        if (depth < 4) {
            p.material = unit(GradientNoise.hash(s.h, x, y, z), 0) < 0.08 ? BUDDING : AMETHYST;
        } else if (depth < 8.5) {
            p.material = CALCITE;
        } else {
            p.material = BASALT;
        }
    }

    private void titanColumn(Site s, Col c, int x, int z) {
        // The hall: round the spine, high and vaulted, its floor 110 under the spine.
        double best = Double.MAX_VALUE, bt = 0, by = s.y0;
        int n = s.sx.length;
        for (int k = 0; k < n; k += 1) {
            double d2 = (x - s.sx[k]) * (x - s.sx[k]) + (z - s.sz[k]) * (z - s.sz[k]);
            if (d2 < best) {
                best = d2;
                bt = (double) k / (n - 1);
                by = s.sy[k];
            }
        }
        double hd = Math.sqrt(best);
        double sk = Math.hypot(x - s.skullX, z - s.skullZ);
        double width = 150 * (0.6 + 0.4 * Math.sin(Math.PI * Math.min(1, bt * 1.1 + 0.05)));
        c.tIn = hd < width + 28 || sk < 130;
        if (!c.tIn) return;
        c.tHd = hd;
        c.tWidth = width;
        c.tBy = by;
        c.tAlong = bt * (n - 1) * 22;
        c.tSk = sk;
        c.tFloor = by - 112 + 10 * Fractal.fbm(GradientNoise.fork(s.h, 1), x / 70.0, z / 70.0, 2);
        double ceil = by + 70 - 80 * Math.pow(Math.min(1, hd / width), 2) + 12 * GradientNoise.noise2(GradientNoise.fork(s.h, 2), x / 50.0, z / 50.0);
        if (sk < 130) ceil = Math.max(ceil, s.skullY + 75 - 0.4 * sk);
        c.tCeil = ceil;
    }

    // The Titan's skull, in its own frame (along: forward from the cranium's middle toward the snout).
    private static double skullHalfW(double t) {
        return 64 * (1 - 0.62 * smoothstep(0.3, 1, t)) * (0.85 + 0.15 * Math.sin(Math.PI * Math.min(1, t * 2)));
    }

    private static double skullTop(double t) {
        return 50 * (1 - 0.55 * smoothstep(0.25, 1, t));
    }

    private static double skullBottom(double t) {
        return -34 + 14 * smoothstep(0.3, 1, t);
    }

    /** 0 inside the hollow, (0, 1) in the bone shell, 1+ outside: a superellipse cross-section, boxy and angular. */
    private static double skullShell(double along, double qy, double side) {
        double t = (along + 70) / 190.0;
        if (t < -0.02 || t > 1.0) return 2;
        double hw = skullHalfW(t), top = skullTop(t), bot = skullBottom(t);
        double cy = (top + bot) / 2, hh = (top - bot) / 2;
        double back = along < -40 ? Math.sqrt(Math.max(0.05, 1 - Math.pow((along + 40) / 32.0, 2))) : 1; // rounded at the back
        double pw = 2.6;
        double e = Math.pow(Math.pow(Math.abs(side) / (hw * back), pw) + Math.pow(Math.abs(qy - cy) / (hh * back), pw), 1 / pw);
        double inner = 1 - 7.0 / Math.min(hw, hh);
        if (e >= 1) return 1 + (e - 1);
        return e > inner ? 0.5 : 0;
    }

    private void titan(Site s, Col c, int x, int y, int z, Probe p) {
        if (!c.tIn) return;
        double floor = c.tFloor, ceil = c.tCeil;
        if (y < floor - 4 || y > ceil + 4) return;
        p.claimed = true;
        // Bone.
        for (Cap b : c.caps) {
            if (y < b.minY || y > b.maxY) continue;
            if (b.dist(x + 0.5, y + 0.5, z + 0.5) < 0) {
                p.protect = true;
                p.material = b.mat;
                return;
            }
        }
        // The skull: a long, angular hollow shell (a broad cranium narrowing to a snout), eye sockets
        // in its sides under heavy brows, nostrils at the snout, and a mouth to walk in by.
        double qx = x + 0.5 - s.skullX, qy = y + 0.5 - s.skullY, qz = z + 0.5 - s.skullZ;
        double along = qx * -s.dirX + qz * -s.dirZ, side = qx * -s.dirZ + qz * s.dirX;
        double sq = skullShell(along, qy, side);
        if (sq < 1.0 && sq > 0) {
            double t = (along + 70) / 190.0;
            boolean eye = Math.hypot((along - 10) / 16.0, (qy - 25) / 12.0) < 1 && Math.abs(side) > 30;
            boolean nostril = along > 104 && along < 118 && Math.abs(Math.abs(side) - 9) < 4.5 && qy > 0;
            boolean mouth = along > 40 && qy < skullBottom(t) + 9 && Math.abs(side) < skullHalfW(t) - 6;
            if (!eye && !nostril && !mouth) {
                p.protect = true;
                p.material = BONE;
                return;
            }
        }
        if (y <= floor || y > ceil) return;
        // The hall's walls: not sheer, but a garbled sine running up them (bulges, ledges and alcoves),
        // rounded off into the floor at the bottom.
        if (c.tSk >= 130) {
            double top = c.tBy + 70, tt = Math.max(0, Math.min(1, (y - floor) / Math.max(1, top - floor)));
            double round = Math.sqrt(Math.max(0, 1 - Math.pow(1 - Math.min(1, tt / 0.3), 2)));
            double garble = 10 * Math.sin(y / 9.0 + 2.5 * GradientNoise.noise2(GradientNoise.fork(s.h, 50), c.tAlong / 40.0, 0.5))
                    + 6 * Math.sin(y / 4.3 + c.tAlong / 17.0)
                    + 8 * GradientNoise.noise2(GradientNoise.fork(s.h, 51), c.tAlong / 30.0, y / 14.0);
            double wall = c.tWidth * (0.55 + 0.45 * round) + garble;
            if (c.tHd > wall) return;
        }
        p.open = true;
    }

    // ---- lookups

    /** The nearest site of a kind to (x, z): {x, y, z, size}, or null within 3 cells. */
    public double[] nearest(int kind, double x, double z) {
        double cell = cellOf(kind);
        int ci = GradientNoise.floor(x / cell), cj = GradientNoise.floor(z / cell);
        double[] best = null;
        double bd = Double.MAX_VALUE;
        for (int j = cj - 3; j <= cj + 3; j++) {
            for (int i = ci - 3; i <= ci + 3; i++) {
                Site s = site(kind, i, j);
                if (!s.present) continue;
                double d = Math.hypot(s.x - x, s.z - z);
                if (d < bd) {
                    bd = d;
                    double y = kind == HOLLOW ? s.lake + 40 : kind == GEODE ? s.y0 : kind == VAULT ? s.y0 + 12
                            : kind == ROOT ? s.tGround + 1 : kind == PEAKS ? s.lake + 8 : s.y0 + 20;
                    double size = kind == HOLLOW || kind == VAULT || kind == PEAKS ? 2 * s.rx : kind == ROOT ? 2 * s.trunkR
                            : kind == GEODE ? 2 * s.r : s.sx.length * 22;
                    best = new double[] {s.x, y, s.z, size};
                }
            }
        }
        return best;
    }

    /** The biggest set piece radius near a point, for mega-caverns to keep clear: true if (x, z) is within reach of one. */
    public boolean crowds(double x, double z, double reach) {
        for (int kind = HOLLOW; kind <= PEAKS; kind++) {
            double cell = cellOf(kind);
            int ci = GradientNoise.floor(x / cell), cj = GradientNoise.floor(z / cell);
            for (int dj = -1; dj <= 1; dj++) {
                for (int di = -1; di <= 1; di++) {
                    Site s = site(kind, ci + di, cj + dj);
                    if (!s.present) continue;
                    if (x + reach > s.minX && x - reach < s.maxX && z + reach > s.minZ && z - reach < s.maxZ) return true;
                }
            }
        }
        return false;
    }

    private static double unit(long hash, int k) {
        return (GradientNoise.mix(hash + k * 0x9E3779B97F4A7C15L) >>> 11) * 0x1.0p-53;
    }
}
