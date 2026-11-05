package com.seaweed.grandatlas.core;

import static com.seaweed.grandatlas.core.Maths.smoothstep;

import com.seaweed.grandatlas.core.noise.Fractal;
import com.seaweed.grandatlas.core.noise.GradientNoise;
import java.util.ArrayList;
import java.util.HashMap;
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
 * </ul>
 * Each kind sits on its own grid of cells (one chance in two per cell), always 50+ blocks under
 * the ground. Inside a piece, other caves keep out: Grand Atlas's passages may break through its
 * outer walls (they are its entrances) but never through {@linkplain #isProtected protected} rock
 * (bone, islands, the geode's shell, a lake bed).
 */
public final class SetPieces {
    public static final int NONE = 0, HOLLOW = 1, GEODE = 2, TITAN = 3;
    /** Materials the set-piece dressing lays in solid rock. */
    public static final int ROCK = 0, BONE = 1, AMETHYST = 2, BUDDING = 3, CALCITE = 4, BASALT = 5, TUFF = 6, SELENITE = 7;
    static final double HOLLOW_CELL = 7000, GEODE_CELL = 5200, TITAN_CELL = 6400;

    private final AtlasCore core;
    private final long seed;
    private final ThreadLocal<HashMap<Long, Site>> sites = ThreadLocal.withInitial(HashMap::new);
    private final ThreadLocal<Col> cols = ThreadLocal.withInitial(Col::new);

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
    }

    /** Per column: the sites over it, and what each site makes of the column (computed once). */
    private static final class Col {
        int x = Integer.MIN_VALUE, z = Integer.MIN_VALUE;
        final List<Site> sites = new ArrayList<>(2);
        final List<Cap> caps = new ArrayList<>();
        final List<Island> islands = new ArrayList<>();
        // the Hollow: inside its outline, its roof and floor here
        boolean hIn;
        double hDome, hFloor, hD;
        // Titan: inside the hall, its floor and roof here
        boolean tIn;
        double tFloor, tCeil;
    }

    // ---- sites

    private Site site(int kind, int i, int j) {
        HashMap<Long, Site> cache = sites.get();
        long key = ((long) kind << 58) ^ ((long) i << 29) ^ (j & 0x1FFFFFFFL);
        Site s = cache.get(key);
        if (s != null) return s;
        if (cache.size() > 256) cache.clear();
        s = new Site();
        s.kind = kind;
        cache.put(key, s);
        long h = GradientNoise.hash(GradientNoise.fork(seed, kind), i, j);
        s.h = h;
        if (unit(h, 0) > 0.5) return s;
        double cell = kind == HOLLOW ? HOLLOW_CELL : kind == GEODE ? GEODE_CELL : TITAN_CELL;
        s.x = (i + 0.3 + 0.4 * unit(h, 1)) * cell;
        s.z = (j + 0.3 + 0.4 * unit(h, 2)) * cell;
        double a = unit(h, 3) * Math.PI * 2;
        s.cos = Math.cos(a);
        s.sin = Math.sin(a);
        switch (kind) {
            case HOLLOW -> buildHollow(s, h);
            case GEODE -> buildGeode(s, h);
            default -> buildTitan(s, h);
        }
        // Set pieces never overlap: a geode keeps clear of any Hollow, a Titan's grave of both
        // (asking only about earlier kinds, so this can't go round in a loop).
        if (s.present) {
            for (int other = HOLLOW; other < kind && s.present; other++) {
                double oc = other == HOLLOW ? HOLLOW_CELL : GEODE_CELL;
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
        // The skull's teeth: a row down from the upper jaw and up from the lower, at the front.
        double fwdX = -s.dirX, fwdZ = -s.dirZ, sdX = -fwdZ, sdZ = fwdX;
        for (int q = 0; q < 13; q++) {
            double ang = (q / 12.0 - 0.5) * 2.2; // round the front of the jaw
            double along = 88 * Math.cos(ang) * 0.95, side = 60 * Math.sin(ang) * 0.95;
            double tx = s.skullX + fwdX * along + sdX * side, tz = s.skullZ + fwdZ * along + sdZ * side;
            s.bones.add(new Cap(tx, s.skullY - 16, tz, tx, s.skullY - 32, tz, 3.2, 0.4));
            s.bones.add(new Cap(tx, s.skullY - 50, tz, tx, s.skullY - 38, tz, 2.6, 0.4));
        }
        // The lower jaw: two long bones under the skull meeting at the chin.
        for (int side = -1; side <= 1; side += 2) {
            s.bones.add(new Cap(s.skullX - fwdX * 20 + sdX * side * 52, s.skullY - 48, s.skullZ - fwdZ * 20 + sdZ * side * 52,
                    s.skullX + fwdX * 90 + sdX * side * 10, s.skullY - 52, s.skullZ + fwdZ * 90 + sdZ * side * 10, 7, 5.5));
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
        Col c = cols.get();
        if (c.x == x && c.z == z) return c;
        c.x = x;
        c.z = z;
        c.sites.clear();
        c.caps.clear();
        for (int kind = HOLLOW; kind <= TITAN; kind++) {
            double cell = kind == HOLLOW ? HOLLOW_CELL : kind == GEODE ? GEODE_CELL : TITAN_CELL;
            int ci = GradientNoise.floor(x / cell), cj = GradientNoise.floor(z / cell);
            for (int dj = -1; dj <= 1; dj++) {
                for (int di = -1; di <= 1; di++) {
                    Site s = site(kind, ci + di, cj + dj);
                    if (!s.present || x < s.minX || x > s.maxX || z < s.minZ || z > s.maxZ) continue;
                    c.sites.add(s);
                    for (Cap b : s.bones) if (x >= b.minX && x <= b.maxX && z >= b.minZ && z <= b.maxZ) c.caps.add(b);
                    if (kind == TITAN) {
                        titanColumn(s, c, x, z);
                    } else if (kind == HOLLOW) {
                        hollowColumn(s, c, x, z);
                    }
                }
            }
        }
        return c;
    }

    /** True if some set piece's volume holds this column at all (cheap). */
    public boolean nearColumn(int x, int z) {
        return !column(x, z).sites.isEmpty();
    }

    // ---- per block

    /** Result of {@link #sample}: inside a piece's volume, open, water, protected, material. */
    public static final class Probe {
        public boolean claimed, open, water, protect, island;
        public int material, kind;
    }

    private final ThreadLocal<Probe> probes = ThreadLocal.withInitial(Probe::new);

    /** What the set pieces make of a block (a shared per-thread object: read it before the next call). */
    public Probe sample(int x, int y, int z) {
        Probe p = probes.get();
        p.claimed = p.open = p.water = p.protect = p.island = false;
        p.material = ROCK;
        p.kind = NONE;
        Col c = column(x, z);
        for (Site s : c.sites) {
            if (y < s.minY || y > s.maxY) continue;
            switch (s.kind) {
                case HOLLOW -> hollow(s, c, x, y, z, p);
                case GEODE -> geode(s, x, y, z, p);
                default -> titan(s, c, x, y, z, p);
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
        c.tIn = hd < width + 12 || sk < 130;
        if (!c.tIn) return;
        c.tFloor = by - 112 + 10 * Fractal.fbm(GradientNoise.fork(s.h, 1), x / 70.0, z / 70.0, 2);
        double ceil = by + 70 - 80 * Math.pow(Math.min(1, hd / width), 2) + 12 * GradientNoise.noise2(GradientNoise.fork(s.h, 2), x / 50.0, z / 50.0);
        if (sk < 130) ceil = Math.max(ceil, s.skullY + 75 - 0.4 * sk);
        c.tCeil = ceil;
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
        // The skull: a hollow shell, eye sockets and a jaw to walk in by.
        double qx = x + 0.5 - s.skullX, qy = y + 0.5 - s.skullY, qz = z + 0.5 - s.skullZ;
        double along = qx * -s.dirX + qz * -s.dirZ, side = qx * -s.dirZ + qz * s.dirX;
        double q = Math.sqrt((along / 95) * (along / 95) + (qy / 60) * (qy / 60) + (side / 66) * (side / 66));
        if (q < 1.0 && q > 1.0 - 8.0 / 62) {
            boolean eye = Math.hypot(along - 52, qy - 18) < 17 && Math.abs(Math.abs(side) - 34) < 22;
            boolean jaw = along > 30 && qy < -18 && Math.abs(side) < 40;
            if (!eye && !jaw) {
                p.protect = true;
                p.material = BONE;
                return;
            }
        }
        if (y > floor && y <= ceil) p.open = true;
    }

    // ---- lookups

    /** The nearest site of a kind to (x, z): {x, y, z, size}, or null within 3 cells. */
    public double[] nearest(int kind, double x, double z) {
        double cell = kind == HOLLOW ? HOLLOW_CELL : kind == GEODE ? GEODE_CELL : TITAN_CELL;
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
                    double y = kind == HOLLOW ? s.lake + 40 : kind == GEODE ? s.y0 : s.y0 + 20;
                    double size = kind == HOLLOW ? 2 * s.rx : kind == GEODE ? 2 * s.r : s.sx.length * 22;
                    best = new double[] {s.x, y, s.z, size};
                }
            }
        }
        return best;
    }

    /** The biggest set piece radius near a point, for mega-caverns to keep clear: true if (x, z) is within reach of one. */
    public boolean crowds(double x, double z, double reach) {
        for (int kind = HOLLOW; kind <= TITAN; kind++) {
            double cell = kind == HOLLOW ? HOLLOW_CELL : kind == GEODE ? GEODE_CELL : TITAN_CELL;
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
