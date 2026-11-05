package com.seaweed.grandatlas.core.micro;

import static com.seaweed.grandatlas.core.Maths.lerp;
import static com.seaweed.grandatlas.core.Maths.smoothstep;

import com.seaweed.grandatlas.core.ColumnSample;
import com.seaweed.grandatlas.core.hydro.Drainage;
import com.seaweed.grandatlas.core.noise.GradientNoise;
import java.util.SplittableRandom;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The meso and micro layer (docs/MICRO_FEATURES.md): at most one meso site per 800-block cell,
 * each laying out its micro features. Everything follows from (seed, cell), so any chunk finds
 * the same sites in any order; sites are judged on the ground before rivers and features, so
 * building one never needs another.
 *
 * <p>The column pipeline asks twice. {@link #shapes} before the rivers, for what stands up out of
 * the ground (hoodoos, cinder cones), and {@link #hollows} after them, for what is cut into it
 * (ponds, sinkholes, cenotes), which also checks once that no river runs through. The world
 * then asks {@link #carve} per block for what a heightmap can't hold: a hoodoo's neck under its
 * caprock, a sinkhole's undercut.
 */
public final class Features {
    /** One cell, at most one site; a site and its jitter stay inside its cell (radius 290 + 100 < 400). */
    public static final int CELL = 800;
    private static final double JITTER = 100;
    private static final Object NONE = new Object();

    /** The ground before rivers and features. */
    @FunctionalInterface
    public interface Terrain {
        ColumnSample sample(double x, double z);
    }

    /** What the column pipeline gets back. */
    public static final class Column {
        public MesoSite site;
        public MicroFeature micro;
        public double height, envelope, water;
        /** No 3D roughness here: it would hole a pillar or a bank. */
        public boolean calm;
        /** Water in or beside the column: no caves open here. */
        public boolean near;
    }

    private final long seed;
    private final Terrain ground;
    private final Drainage drainage;
    private final ConcurrentHashMap<Long, Object> sites = new ConcurrentHashMap<>();

    public Features(long seed, Terrain ground, Drainage drainage) {
        this.seed = seed;
        this.ground = ground;
        this.drainage = drainage;
    }

    public static int cell(double v) {
        return GradientNoise.floor(v / CELL);
    }

    /** The site in a cell, or null. */
    public MesoSite site(int ci, int cj) {
        long key = ((long) ci << 32) | (cj & 0xFFFFFFFFL);
        Object o = sites.get(key);
        if (o == null) {
            MesoSite built = build(ci, cj);
            o = sites.putIfAbsent(key, built == null ? NONE : built);
            if (o == null) o = built == null ? NONE : built;
        }
        return o == NONE ? null : (MesoSite) o;
    }

    /** The site covering (x, z), or null. */
    public MesoSite siteAt(double x, double z) {
        MesoSite s = site(cell(x), cell(z));
        return s != null && s.covers(x, z) ? s : null;
    }

    /** The nearest site of a kind, searching outward up to {@code rings} cells. */
    public MesoSite find(MesoKind kind, double x, double z, int rings) {
        int ci = cell(x), cj = cell(z);
        MesoSite best = null;
        double bestD = Double.MAX_VALUE;
        for (int ring = 0; ring <= rings; ring++) {
            for (int dj = -ring; dj <= ring; dj++) {
                for (int di = -ring; di <= ring; di++) {
                    if (Math.max(Math.abs(di), Math.abs(dj)) != ring) continue;
                    MesoSite s = site(ci + di, cj + dj);
                    if (s == null || s.kind() != kind) continue;
                    double d = Math.hypot(s.x() - x, s.z() - z);
                    if (d < bestD) {
                        bestD = d;
                        best = s;
                    }
                }
            }
            if (best != null && bestD < (ring - 1) * CELL) break;
        }
        return best;
    }

    private MesoSite build(int ci, int cj) {
        SplittableRandom rnd = new SplittableRandom(GradientNoise.hash(seed, ci, cj));
        double cx = (ci + 0.5) * CELL + (rnd.nextDouble() * 2 - 1) * JITTER;
        double cz = (cj + 0.5) * CELL + (rnd.nextDouble() * 2 - 1) * JITTER;
        ColumnSample c = ground.sample(cx, cz);
        double slope = Math.max(Math.abs(ground.sample(cx + 32, cz).height() - ground.sample(cx - 32, cz).height()),
                Math.abs(ground.sample(cx, cz + 32).height() - ground.sample(cx, cz - 32).height())) / 64;
        MesoKind[] kinds = MesoKind.values();
        double[] weight = new double[kinds.length];
        double total = 0;
        for (int k = 0; k < kinds.length; k++) {
            weight[k] = kinds[k].chance * kinds[k].suitability(c, slope);
            total += weight[k];
        }
        double roll = rnd.nextDouble();
        if (total <= 0 || roll >= 0.7 * Math.min(1, total)) return null;
        rnd.nextDouble(); // was the pick; still drawn so the rest of the stream stays put
        // Each kind draws its own roll from its name and the cell, and the best (an exponential race,
        // weighted) wins. So adding a kind only changes the cells it wins, not every cell.
        MesoKind kind = null;
        double best = Double.MAX_VALUE;
        for (int k = 0; k < kinds.length; k++) {
            if (weight[k] <= 0) continue;
            double u = ((GradientNoise.hash(seed ^ (kinds[k].name().hashCode() * 0x9E3779B97F4A7C15L), ci, cj) >>> 11) + 1) * 0x1.0p-53;
            double score = -Math.log(u) / weight[k];
            if (score < best) {
                best = score;
                kind = kinds[k];
            }
        }
        double radius = lerp(rnd.nextDouble(), kind.minRadius, kind.maxRadius);
        // A site stays inside its cell: columns past the cell's edge never see it, so whatever reached
        // over would stop dead on a straight line.
        radius = Math.min(radius, CELL / 2.0 - Math.max(Math.abs(cx - (ci + 0.5) * CELL), Math.abs(cz - (cj + 0.5) * CELL)) - 2);
        return new MesoSite(kind, cx, cz, radius, rnd.nextLong(), c.biome(), kind.populate(rnd, cx, cz, radius, c, ground));
    }

    // ---- columns

    /** Before rivers: what rises out of the ground. {@code envelope} is at least {@code height}, see AtlasCore.uncutDensity. */
    public void shapes(double x, double z, double h, Column out) {
        out.site = siteAt(x, z);
        out.micro = null;
        out.height = h;
        out.envelope = h;
        out.water = Double.NaN;
        out.calm = false;
        out.near = false;
        if (out.site == null) return;
        for (MicroFeature m : out.site.micros()) {
            double dx = x - m.x, dz = z - m.z, d2 = dx * dx + dz * dz, reach = m.reach();
            if (d2 > reach * reach) continue;
            check(m); // a river through it would cut it in half: then it isn't there
            if (!m.present()) continue;
            // A caldera's island cone only stands in its lake.
            if (m.kind == MicroKind.CINDER_CONE && out.site.kind() == MesoKind.CALDERA && !out.site.micros().get(0).present()) continue;
            double d = Math.sqrt(d2);
            switch (m.kind) {
                case CALDERA -> caldera(m, x, z, d, Math.atan2(dz, dx), out);
                case HOODOO -> {
                    // Sheer sides, so exact: the column is the pillar's full height (its top follows the
                    // ground), and the interpolated envelope covers it 6 blocks wider so the per-block
                    // cut can trim it back to this edge (the 4x8x4 grid would round a 3-block pillar away).
                    double top = h + m.h;
                    if (d <= m.outline(Math.atan2(dz, dx))) {
                        out.height = top;
                        out.micro = m;
                    }
                    if (d <= m.r2 + 6) out.envelope = Math.max(out.envelope, top + 2);
                    out.calm = true;
                }
                case CINDER_CONE -> {
                    // Straight flanks (cinder sits at its angle of repose), a soft foot, a sharp crater rim.
                    double t = Math.max(0, Math.min(1, (m.r - d) / (m.r - m.r2)));
                    double bump = d >= m.r2 ? m.h * (t < 0.2 ? t * t / 0.4 : t - 0.1) / 0.9
                            : m.h - m.extra * (1 - Math.pow(d / m.r2, 1.6));
                    out.height += bump;
                    out.envelope += bump;
                    if (out.micro == null || out.micro.kind == MicroKind.CALDERA && d < m.r) out.micro = m;
                    out.calm = true;
                }
                case TRAVERTINE -> {
                    double[] tr = terrace(m, x, z);
                    if (tr == null) break;
                    // Built up where the ground is too low; the pools themselves are cut in hollows().
                    if (tr[1] > out.height) {
                        out.height = tr[1];
                        out.envelope = Math.max(out.envelope, tr[1] + 1);
                    }
                    out.micro = m;
                    out.calm = true;
                }
                case BASALT_COLUMNS, ERRATIC, TOR, GIANT_TREE, GIANT_MUSHROOM, SNAG -> {
                    if (out.micro == null) out.micro = m; // built whole in the detail pass
                }
                case SPIRE -> {
                    // A tower of four sheer tiers stepping in as it rises, each tier's outline its own,
                    // the upper half split by a cleft into two summits, on a talus apron. Exact like a
                    // butte: the envelope lets the per-block cut keep every tier sheer.
                    double a = Math.atan2(dz, dx), r = m.outline(a);
                    long ts = (long) (m.x * 7 + m.z);
                    // The tiers lean one way: each sits off the last's centre toward the same side, so
                    // that side drops sheer from top to foot and the other steps down. Their tops tilt.
                    double lean = m.extra + 1.3, lx = Math.cos(lean), lz = Math.sin(lean);
                    double top = 0;
                    for (int tier = 0; tier < 4; tier++) {
                        double ox = dx - lx * r * 0.13 * tier, oz = dz - lz * r * 0.13 * tier;
                        double ta = Math.atan2(oz, ox), td = Math.hypot(ox, oz);
                        double tr = m.outline(ta) * (1 - 0.18 * tier) * (1 + 0.1 * Math.sin((3 + tier) * ta + tier * 1.7 + m.ground)
                                + 0.06 * GradientNoise.noise2(ts ^ tier, x / 5.0, z / 5.0));
                        if (td < tr) top = m.h * (0.38 + 0.2 * tier + 0.03 * Math.sin(tier * 2.3 + m.ground)) + 0.07 * m.h * (ox * lx + oz * lz) / r;
                    }
                    if (top > 0) {
                        // The cleft: a ragged V between the two summits.
                        double v = Math.abs(-dx * Math.sin(m.extra) + dz * Math.cos(m.extra) + 2 * GradientNoise.noise2(ts ^ 9, x / 8.0, z / 8.0));
                        double w = 3 + 0.1 * m.r;
                        if (v < w && d < r * 0.8) top = Math.min(top, m.h * (0.5 + 0.25 * v / w));
                        top -= 3 * Math.abs(GradientNoise.noise2(ts, x / 6.0, z / 6.0)); // broken tops
                    } else if (d < r + 9) {
                        top = m.h * 0.12 * Math.pow(1 - (d - r) / 9, 1.5); // talus
                    }
                    if (top > 0) {
                        out.height = Math.max(out.height, h + top);
                        if (d < r) out.micro = m;
                    }
                    if (d <= r + 9) out.envelope = Math.max(out.envelope, h + m.h + 2);
                    out.calm = true;
                }
                case BUTTE -> {
                    // Two sheer tiers (exact, like a hoodoo: the envelope lets the per-block cut trim
                    // them), on a talus apron that slopes off into the ground.
                    double r = m.outline(Math.atan2(dz, dx));
                    double top = d < r * 0.68 ? m.h : d < r ? m.h * 0.55 : d < r + 6 ? m.h * 0.22 * (1 - (d - r) / 6) : 0;
                    if (top > 0) {
                        out.height = Math.max(out.height, h + top);
                        if (d < r) out.micro = m;
                    }
                    if (d <= r + 6) out.envelope = Math.max(out.envelope, h + m.h + 2);
                    out.calm = true;
                }
                default -> {
                }
            }
        }
        out.envelope = Math.max(out.envelope, out.height);
    }

    /**
     * A caldera, absolute inside the rim: from the crest a sheer cliff (the envelope lets the
     * per-block cut keep it sheer), then talus at 40 degrees down under the lake to its floor. Outside,
     * the flanks fall from the crest to the ground, steep at the top and easing out, ribbed with
     * gullies that run down them.
     */
    private void caldera(MicroFeature m, double x, double z, double d, double a, Column out) {
        double rim = m.calderaRim(a), crest = m.calderaCrest(a);
        if (d < rim) {
            double s = rim - d, cliff = m.calderaCliff(a), run = m.calderaRun(a);
            double floor = m.level - m.calderaDepth() + 4 * GradientNoise.noise2(seed ^ 0xCA1L, x / 45.0, z / 45.0);
            double top = s < run ? crest - cliff * s / run
                    : crest - cliff - 0.85 * (s - run) + 1.8 * GradientNoise.noise2(seed ^ 0xCA2L, x / 7.0, z / 7.0);
            out.height = Math.max(floor, top);
            if (s < run + 5) out.envelope = Math.max(out.envelope, crest + 2);
            out.micro = m;
        } else {
            // The rim's rounded top first, then the flanks from its outer edge.
            double band = m.calderaBand(a), u = Math.min(1, (d - rim) / band);
            double t = Math.max(0, Math.min(1, (d - rim - band) / (m.r2 - rim - band))), ground = out.height;
            double lift = Math.max(0, crest - 2.5 * u * u - ground) * Math.pow(1 - t, 1.6);
            // Gullies down the flanks: unevenly spaced (a strong warp across them), some deep, some
            // barely there, starting at different heights.
            double warp = 2.2 * GradientNoise.noise2(seed ^ 0xCA3L, x / 90.0, z / 90.0) + 0.7 * GradientNoise.noise2(seed ^ 0xCA4L, x / 30.0, z / 30.0);
            double rib = Math.abs(Math.sin(a * m.calderaRibs() / 2 + warp));
            double cut = 0.5 + 0.5 * GradientNoise.noise2(seed ^ 0xCA9L, a * m.calderaRibs() / (2 * Math.PI) * 0.45, m.h);
            double start = 0.03 + 0.2 * (0.5 + 0.5 * GradientNoise.noise2(seed ^ 0xCAAL, a * 3, m.h));
            double gully = (1 - rib) * (1 - rib) * 9 * cut * cut * smoothstep(start, start + 0.12, t) * Math.min(1, lift / 12);
            out.height = ground + Math.max(0, lift - gully);
            if (d < rim + 4) out.envelope = Math.max(out.envelope, crest + 2);
            if (d < rim + 6 && out.micro == null) out.micro = m;
        }
        out.calm |= d < rim + 12;
    }

    /** After rivers: what is cut into the ground, and its water. Lowers {@code h} only. */
    public void hollows(double x, double z, double h, Column out) {
        out.height = h;
        if (out.site == null) return;
        for (MicroFeature m : out.site.micros()) {
            if (m.kind != MicroKind.POND && m.kind != MicroKind.SINKHOLE && m.kind != MicroKind.CENOTE && m.kind != MicroKind.GULLY
                    && m.kind != MicroKind.GROTTO && m.kind != MicroKind.TRAVERTINE && m.kind != MicroKind.CALDERA) continue;
            double dx = x - m.x, dz = z - m.z, d2 = dx * dx + dz * dz, reach = m.reach();
            if (d2 > reach * reach) continue;
            check(m);
            if (!m.present()) continue;
            double d = Math.sqrt(d2), r = m.outline(Math.atan2(dz, dx));
            // A ragged shore on top of the lobes: small coves and points, never a clean curve.
            r *= 1 + (m.kind == MicroKind.POND ? 0.14 : 0.07) * GradientNoise.noise2(seed ^ 0x3B1L, x / 7.0, z / 7.0)
                    + (m.kind == MicroKind.POND ? 0.06 : 0.03) * GradientNoise.noise2(seed ^ 0x3B2L, x / 3.0, z / 3.0);
            if (m.kind == MicroKind.CALDERA) {
                // The lake: everything inside the rim is under the crest, and the level under its lowest point.
                if (d < m.calderaRim(Math.atan2(dz, dx)) + 4) {
                    if (out.height < m.level) out.water = m.level;
                    out.near = true;
                    out.calm = true;
                }
            } else if (m.kind == MicroKind.POND) {
                pond(m, x, z, d, r, out);
            } else if (m.kind == MicroKind.GULLY) {
                gully(m, x, z, out);
            } else if (m.kind == MicroKind.GROTTO) {
                grotto(m, x, z, d, r, out);
            } else if (m.kind == MicroKind.TRAVERTINE) {
                double[] tr = terrace(m, x, z);
                if (tr != null) {
                    if (tr[0] < 3) {
                        out.height = Math.min(out.height, tr[1]);
                        if (out.height < tr[2]) out.water = tr[2];
                    }
                    out.micro = m;
                    out.calm = true;
                    out.near = true;
                }
            } else {
                pit(m, d, r, out);
            }
        }
    }

    /**
     * A pond: a bowl down from a block over its water at the shore, then a shore blending back
     * into the land. The level sits a block under the lowest ground around it, so it holds.
     */
    private static void pond(MicroFeature m, double x, double z, double d, double r, Column out) {
        double f = 1 - d / r, level = m.level, depth = Math.min(5, 1.5 + 0.18 * m.r);
        double lower;
        if (f >= 0) {
            lower = level + 1.2 - (depth + 1.2) * smoothstep(0, 0.55, f);
            if (m.r > 18) {
                // A big lake has rocky islands, low humps of rock standing a little out of the water.
                double isle = GradientNoise.noise2((long) (m.x * 31 + m.z), x / 14.0, z / 14.0);
                if (f > 0.3 && isle > 0.5) lower = Math.max(lower, level + 0.6 + 4 * (isle - 0.5));
            }
        } else {
            double s = smoothstep(0, 1, -f / 0.4);
            lower = lerp(s, level + 1.2, Math.max(out.height, level + 1.2));
        }
        lower = Math.min(out.height, lower);
        out.height = lower;
        if (f > 0 && lower < level && Double.isNaN(out.water)) out.water = level;
        if (f > -0.35) {
            out.near = true;
            out.calm = true;
            if (out.micro == null || f > 0) out.micro = m;
        }
    }

    /**
     * A grotto, in plan: the pit (a pool 3 deep over its floor, a sand beach across from the
     * overhang), the stream on the shelf (a groove from a spring pool to the rim, its water just
     * under the ground, pouring off the rim into the pool), and the overhang's cavity, carved under
     * the shelf block by block in {@link #carve}.
     */
    private static void grotto(MicroFeature m, double x, double z, double d, double r, Column out) {
        double level = m.level(), floor = m.floor();
        double ca = Math.cos(m.heading()), sa = Math.sin(m.heading());
        double dx = x - m.x, dz = z - m.z;
        if (d < r) {
            double facing = (dx * ca + dz * sa) / Math.max(1e-6, d); // 1 under the overhang, -1 across from it
            double q = d / r;
            double bed = level - 3 * (1 - q * q) - 0.4;
            double beach = smoothstep(-0.2, -0.6, facing) * smoothstep(0.45, 0.8, q);
            double ground = lerp(beach, bed, level + 0.6 + 0.6 * (q - 0.45));
            out.height = Math.min(out.height, Math.max(floor, ground));
            if (out.height < level) out.water = level;
            out.micro = m;
        } else if (d < r + 3 && Math.hypot(x - m.cavityX(), z - m.cavityZ()) > m.cavityR()) {
            out.height = Math.min(out.height, out.height - 1.5 * (1 - (d - r) / 3)); // the open lip rounds off a little
        }
        // The stream: along the heading from a spring pool out on the shelf to the rim (the real,
        // lobed rim: it must reach the edge to pour off it).
        double rim = m.outline(m.heading());
        double along = dx * ca + dz * sa, across = -dx * sa + dz * ca;
        double spring = r + m.r2 + 10;
        double wobble = 1.6 * Math.sin(along / 5 + m.ground);
        double off = Math.abs(across - wobble), w = 1.5;
        boolean groove = along > rim - 2.5 && along < spring && off < w + 1;
        boolean pool = Math.hypot(along - spring, across) < 2.6;
        if (groove || pool) {
            double wl = m.ground - 0.7, q = pool ? Math.hypot(along - spring, across) / 2.6 : Math.min(1, off / w);
            double bed = m.ground - 0.7 - 1.3 * (1 - q * q);
            out.height = Math.min(out.height, bed + (q >= 1 ? 1.2 : 0));
            if (out.height < wl && d >= r) out.water = wl;
            out.near = true;
        }
        if (Math.hypot(x - m.cavityX(), z - m.cavityZ()) < m.cavityR() + 2 && (out.micro == null || d < r)) out.micro = m;
        out.calm = true;
        out.near |= d < r + 6;
    }

    /**
     * A gully: a winding ravine along its heading, near-sheer sides and a rounded floor, deepest in
     * the middle and running out at both ends.
     */
    private static void gully(MicroFeature m, double x, double z, Column out) {
        double[] near = gullyNearest(m, x, z);
        double d = near[0], t = near[1], len = m.r2;
        double taper = smoothstep(-len / 2, -len / 2 + 0.25 * len, t) * (1 - smoothstep(len / 2 - 0.25 * len, len / 2, t));
        double w = m.r * (0.6 + 0.4 * taper), depth = m.h * taper;
        if (d >= w + 1.5) return;
        double s = Math.min(1, d / w);
        double s3 = s * s * s;
        out.height = Math.min(out.height, out.height - depth * (1 - s3 * s3 * s3)); // walls near sheer, floor rounded
        out.calm = true;
        if (out.micro == null || d < w) out.micro = m;
    }

    /** Distance from (x, z) to a gully's winding centre line, and how far along it that is (-len/2 to len/2). */
    public static double[] gullyNearest(MicroFeature m, double x, double z) {
        double ca = Math.cos(m.extra), sa = Math.sin(m.extra), len = m.r2, amp = 0.1 * len, wave = len / 4.5;
        double best = Double.MAX_VALUE, bestT = 0;
        for (double t = -len / 2; t <= len / 2; t += 1.5) {
            double side = amp * Math.sin(t / wave * Math.PI + m.ground);
            double px = m.x + ca * t - sa * side, pz = m.z + sa * t + ca * side;
            double d2 = (px - x) * (px - x) + (pz - z) * (pz - z);
            if (d2 < best) {
                best = d2;
                bestT = t;
            }
        }
        return new double[] {Math.sqrt(best), bestT};
    }

    /** A sinkhole or cenote: a sheer shaft to a flat floor, with a lip rounding over into it. */
    private static void pit(MicroFeature m, double d, double r, Column out) {
        double floor = m.ground - m.h;
        if (d < r) {
            out.height = Math.min(out.height, floor);
            if (m.wet() && Double.isNaN(out.water)) out.water = m.level;
        } else if (d < r + 3) {
            out.height = Math.min(out.height, out.height - 2.5 * (1 - (d - r) / 3));
        }
        out.calm = true;
        if (m.wet()) out.near = true;
        if (d < r * 1.5 + 2 && (out.micro == null || d < r)) out.micro = m;
    }

    /**
     * Checks a feature once: no river or lake through it. A pond, sinkhole or cenote also needs ground around it
     * that can hold it. A pond takes its level from the lowest ground around it and needs gentle
     * ground; a cenote whose rim is too low for its pool stays a dry sinkhole.
     */
    private void check(MicroFeature m) {
        if (m.state != MicroFeature.UNKNOWN) return;
        Drainage.Carve carve = new Drainage.Carve();
        if (m.kind == MicroKind.CALDERA) {
            checkCaldera(m, carve);
            return;
        }
        double around = m.reach() + 8;
        for (int k = 0; k < 9; k++) {
            double px = k == 0 ? m.x : m.x + around * Math.cos(k * Math.PI / 4);
            double pz = k == 0 ? m.z : m.z + around * Math.sin(k * Math.PI / 4);
            drainage.carve(px, pz, ground.sample(px, pz).height(), carve);
            if (carve.near || !Double.isNaN(carve.water)) {
                m.state = MicroFeature.NO;
                return;
            }
        }
        if (m.kind == MicroKind.GROTTO) {
            checkGrotto(m, carve);
            return;
        }
        if (m.kind == MicroKind.TRAVERTINE) {
            checkTerraces(m, carve);
            return;
        }
        if (m.kind != MicroKind.POND && m.kind != MicroKind.SINKHOLE && m.kind != MicroKind.CENOTE) {
            m.state = MicroFeature.OK;
            return;
        }
        double low = Double.MAX_VALUE, high = -Double.MAX_VALUE;
        if (m.kind == MicroKind.POND) {
            for (double ring : new double[] {0.9, 1.1, 1.3, 1.5}) { // out to 1.5: the ragged shore reaches past the lobes
                for (int k = 0; k < 24; k++) {
                    double a = k * Math.PI / 12, rr = m.outline(a) * ring;
                    double g = cutGround(m.x + rr * Math.cos(a), m.z + rr * Math.sin(a), carve);
                    low = Math.min(low, g);
                    if (ring == 1.1) high = Math.max(high, g);
                }
            }
            if (high - low > 7 + 0.1 * m.r) {
                m.state = MicroFeature.NO;
                return;
            }
            m.level = low - 1;
            m.state = MicroFeature.OK;
            return;
        }
        for (double ring : new double[] {1.15, 1.6}) {
            for (int k = 0; k < 24; k++) {
                double a = k * Math.PI / 12, rr = m.outline(a) * ring + 2;
                low = Math.min(low, cutGround(m.x + rr * Math.cos(a), m.z + rr * Math.sin(a), carve));
            }
        }
        if (low < m.ground - 5) {
            m.state = MicroFeature.NO; // on a slope: half the shaft would open sideways
            return;
        }
        double level = m.ground - m.h + m.extra;
        if (m.kind == MicroKind.CENOTE && low >= level + 3) {
            m.level = level;
            m.state = MicroFeature.OK;
        } else {
            m.state = MicroFeature.DRY;
        }
    }

    /**
     * A caldera covers a whole site, so it looks for rivers all over its middle, out a little past
     * its rim (one cutting the flanks further out runs round the volcano's foot in a gorge). The
     * ground round it must stay under its rim (or the rim is buried) and roughly level out at its
     * foot. Its lake sits well under the lowest point of the crest.
     */
    private void checkCaldera(MicroFeature m, Drainage.Carve carve) {
        for (double ring : new double[] {0, 0.2, 0.4, 0.6, 0.68}) {
            int n = ring == 0 ? 1 : ring < 0.3 ? 16 : 32;
            for (int k = 0; k < n; k++) {
                double a = k * Math.PI * 2 / n, rr = m.r2 * ring;
                double px = m.x + rr * Math.cos(a), pz = m.z + rr * Math.sin(a);
                drainage.carve(px, pz, ground.sample(px, pz).height(), carve);
                if (carve.near || !Double.isNaN(carve.water)) {
                    m.state = MicroFeature.NO;
                    return;
                }
            }
        }
        double crestLow = Double.MAX_VALUE, footLow = Double.MAX_VALUE, footHigh = -Double.MAX_VALUE;
        for (int k = 0; k < 256; k++) {
            double a = k * Math.PI / 128;
            crestLow = Math.min(crestLow, m.calderaCrest(a));
            if ((k & 7) != 0) continue;
            double rr = m.calderaRim(a);
            if (ground.sample(m.x + rr * Math.cos(a), m.z + rr * Math.sin(a)).height() > m.calderaCrest(a) - 12) {
                m.state = MicroFeature.NO;
                return;
            }
            double foot = ground.sample(m.x + m.r2 * Math.cos(a), m.z + m.r2 * Math.sin(a)).height();
            footLow = Math.min(footLow, foot);
            footHigh = Math.max(footHigh, foot);
        }
        if (footHigh - footLow > 0.9 * m.h) {
            m.state = MicroFeature.NO;
            return;
        }
        m.level = Math.floor(crestLow - 10 - 0.15 * m.h) + 0.9;
        m.state = MicroFeature.OK;
    }

    /**
     * A grotto needs level ground round its pit (its rim must hold the pool 3 over the floor) and a
     * heading where the ground stays up for its stream and overhang: of eight, the one whose
     * ground is highest, and only if none of it drops more than 1.5 under the centre.
     */
    private void checkGrotto(MicroFeature m, Drainage.Carve carve) {
        double low = Double.MAX_VALUE;
        for (int k = 0; k < 24; k++) {
            double a = k * Math.PI / 12, rr = m.outline(a) * 1.25 + 3;
            low = Math.min(low, cutGround(m.x + rr * Math.cos(a), m.z + rr * Math.sin(a), carve));
        }
        double level = m.floor() + 3;
        if (low < m.ground - 6 || low < level + 4) {
            m.state = MicroFeature.NO;
            return;
        }
        double bestA = Double.NaN, best = -Double.MAX_VALUE;
        for (int k = 0; k < 8; k++) {
            double a = k * Math.PI / 4, worst = Double.MAX_VALUE;
            for (double s = m.r; s <= m.r + m.r2 + 14; s += 3) {
                worst = Math.min(worst, cutGround(m.x + s * Math.cos(a), m.z + s * Math.sin(a), carve) - m.ground);
            }
            if (worst > best) {
                best = worst;
                bestA = a;
            }
        }
        if (best < -2.5) {
            m.state = MicroFeature.NO;
            return;
        }
        m.heading = bestA;
        m.level = level;
        m.state = MicroFeature.OK;
    }

    /**
     * Terraced cascades: steps the slope's ground into pools. Each pool runs down the fall line until
     * the ground has dropped 2.5-4 below where it began (4 to 18 blocks), and holds its water half a
     * block over the ground at its downstream rim. Where the ground beside a pool is lower than its
     * walls need, travertine is built up to hold it, but never more than 3.5: no more and the slope is
     * too uneven for it.
     */
    private void checkTerraces(MicroFeature m, Drainage.Carve carve) {
        double ca = Math.cos(m.extra), sa = Math.sin(m.extra);
        int n = (int) Math.ceil(m.r2);
        double[] g = new double[n + 1];
        for (int s = 0; s <= n; s++) g[s] = cutGround(m.x + ca * s, m.z + sa * s, carve);
        java.util.SplittableRandom rnd = new java.util.SplittableRandom(Double.doubleToLongBits(m.x * 31 + m.z));
        double[] steps = new double[32], levels = new double[31];
        int count = 0, start = 0;
        steps[0] = 0;
        while (start < n - 3 && count < 30) {
            double drop = 2.5 + 1.5 * rnd.nextDouble();
            int end = start + 4;
            while (end < n && end - start < 18 && g[end] > g[start] - drop) end++;
            end = Math.min(end, n);
            double level = g[end] + 0.5;
            if (count > 0) level = Math.min(level, levels[count - 1] - 1.5);
            levels[count] = level;
            steps[++count] = end;
            start = end;
        }
        if (count < 3) {
            m.state = MicroFeature.NO;
            return;
        }
        // The walls: ground at the pools' sides and ends against what they need.
        double worst = 0;
        for (int k = 0; k < count; k++) {
            double need = Math.floor(levels[k]) + 1.2;
            for (double s = steps[k]; s <= steps[k + 1]; s += 2) {
                double w = m.terraceWidth(s) + 0.8, c = m.terraceCentre(s);
                for (int side = -1; side <= 1; side += 2) {
                    double px = m.x + ca * s - sa * (c + side * w), pz = m.z + sa * s + ca * (c + side * w);
                    worst = Math.max(worst, need - cutGround(px, pz, carve));
                }
            }
        }
        worst = Math.max(worst, Math.floor(levels[count - 1]) + 1.2 - g[(int) steps[count]]);
        if (worst > 3.5) {
            m.state = MicroFeature.NO;
            return;
        }
        m.steps = java.util.Arrays.copyOf(steps, count + 1);
        m.levels = java.util.Arrays.copyOf(levels, count);
        m.state = MicroFeature.OK;
    }

    /**
     * Where (x, z) falls in the terraces: its target height and water. Kinds: 0 outside, 1 a pool,
     * 2 a rim (water spilling over it), 3 a wall (dry, built up). Returns null outside.
     */
    public static double[] terrace(MicroFeature m, double x, double z) {
        double[] steps = m.steps, levels = m.levels;
        if (steps == null) return null;
        double ca = Math.cos(m.extra), sa = Math.sin(m.extra);
        double dx = x - m.x, dz = z - m.z;
        double s = dx * ca + dz * sa, signed = -dx * sa + dz * ca - m.terraceCentre(s), a = Math.abs(signed);
        double w = m.terraceWidth(s), bow = m.terraceBow(signed, w);
        int n = levels.length;
        if (s < steps[0] + bow - 1.6 || s > steps[n] + bow + 1.6 || a > w + 1.6) return null;
        if (a >= w || s < steps[0] + bow || s >= steps[n] + bow) {
            // A wall: beside the pools, above the top one, below the last.
            int k = s < steps[0] + bow ? 0 : n - 1;
            for (int j = 0; j < n; j++) if (s >= steps[j] + bow) k = j;
            return new double[] {3, Math.floor(levels[k]) + 1.2, Double.NaN};
        }
        int k = 0;
        while (k + 1 < n && s >= steps[k + 1] + bow) k++;
        double level = levels[k];
        if (k + 1 < n && s >= steps[k + 1] + bow - 1.4) {
            return new double[] {2, Math.floor(level) - 0.3, level}; // the rim: a block of water over it, pouring off
        }
        double q = a / w, along = (s - steps[k] - bow) / Math.max(1, steps[k + 1] - steps[k]);
        double bed = level - 1.3 - 1.6 * (1 - q * q) * Math.sin(Math.PI * Math.max(0, Math.min(1, along)));
        return new double[] {1, bed, level};
    }

    /** The ground at (x, z) after rivers cut it: a valley nearby can lower a pond's or pit's rim. */
    private double cutGround(double x, double z, Drainage.Carve carve) {
        return drainage.carve(x, z, ground.sample(x, z).height(), carve);
    }

        // ---- per block

    /** Air the column's micro feature cuts at height y (min it with the ground), or +infinity. */
    public static double carve(ColumnSample c, int x, int y, int z) {
        MicroFeature m = c.micro();
        if (m == null) return Double.POSITIVE_INFINITY;
        double dx = x + 0.5 - m.x, dz = z + 0.5 - m.z, d = Math.sqrt(dx * dx + dz * dz);
        switch (m.kind) {
            case HOODOO -> {
                // A flared foot, a shaft narrowing to a neck, then the caprock.
                double foot = c.height() - m.h, rel = y - foot, cap = m.h < 12 ? 2 : 3;
                if (rel < 0 || rel >= m.h - cap) return Double.POSITIVE_INFINITY;
                double split = 0.4 * m.h;
                double radius = rel < split ? m.r * (1.25 - 0.25 * rel / split)
                        : lerp((rel - split) / (m.h - cap - split), m.r, m.r * 0.6);
                return (radius - d) * 0.8;
            }
            case SINKHOLE, CENOTE -> {
                // A bell under the lip: the shaft widens to half again its radius at mid-depth.
                // A cenote's bell stays clear of its pool, or the bank plugs around the water fill it.
                if (!m.present()) return Double.POSITIVE_INFINITY;
                double r = m.outline(Math.atan2(dz, dx));
                if (d < r - 0.5) return Double.POSITIVE_INFINITY;
                double top = m.ground - 4, bottom = m.wet() ? m.level + 2 : m.ground - m.h + 1;
                if (top - bottom < 3 || y < bottom || y > top) return Double.POSITIVE_INFINITY;
                double bell = r * (1 + 0.45 * Math.sin(Math.PI * (y - bottom) / (top - bottom)));
                return (d - bell) * 0.5;
            }
            case GROTTO -> {
                // The overhang: a dome hollowed under the shelf on the heading's side, its floor a dry
                // ledge a block over the pool, narrowing toward its roof.
                if (!m.wet()) return Double.POSITIVE_INFINITY;
                double bottom = m.level() + 1, top = m.shelf();
                if (y < bottom || y > top) return Double.POSITIVE_INFINITY;
                double dc = Math.hypot(x + 0.5 - m.cavityX(), z + 0.5 - m.cavityZ());
                double t = (y - bottom) / Math.max(1, top - bottom);
                double rr = m.cavityR() * Math.sqrt(Math.max(0, 1 - 0.55 * t * t))
                        * (1 + 0.08 * GradientNoise.noise3(0x6A077L, x / 5.0, y / 4.0, z / 5.0));
                return (dc - rr) * 0.5;
            }
            default -> {
                return Double.POSITIVE_INFINITY;
            }
        }
    }
}
