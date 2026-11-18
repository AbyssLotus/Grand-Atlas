package com.seaweed.grandatlas.core;

import com.seaweed.grandatlas.core.hydro.Drainage;
import com.seaweed.grandatlas.core.micro.MesoKind;
import com.seaweed.grandatlas.core.micro.MesoSite;
import com.seaweed.grandatlas.core.micro.MicroFeature;
import com.seaweed.grandatlas.core.micro.MicroKind;
import com.seaweed.grandatlas.core.noise.GradientNoise;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.DoubleUnaryOperator;

/**
 * Karst branchwork caves (the cave rework, phases C1 and C2 as a vertical slice: docs/CAVE_RESEARCH.md).
 *
 * <p>Under every sinkhole field (the karst country there already is: its rate is unchanged, the user's
 * rule) lies one cave system, reaching about twice the field's radius. It is one connected tree, built
 * the way real branchwork caves form (Palmer): water gets in at sinkholes and sinking streams, and
 * the passages join downstream like a river's tributaries, toward a spring.
 * <ul>
 *   <li><b>Levels from the river.</b> The spring is on the nearest river, and the lowest level lies at
 *       its water: a cave river in long flat pools that step up a couple of blocks every ~140 blocks
 *       upstream. Two older, dry levels lie about 24 and 48 blocks over it, as the valley's earlier
 *       floors, each its own tree, joined to the level below by shafts and steep canyons. Their roots
 *       open as old, dry springs on the valley side.</li>
 *   <li><b>The spring:</b> the cave river dives under its roof (a sump, flooded to the roof) and rises
 *       to a mouth in the river's bed.</li>
 *   <li><b>A sinking stream</b> in at most 35% of systems (the user's cap), only a small tributary, never
 *       a main river, with no other river joining before it comes back out: it pours down a shaft into
 *       the cave river at a swallet, its valley runs on dry ({@link Drainage.Sinks}), and it comes back
 *       out at the spring.</li>
 *   <li><b>The sinkholes</b> drop into the system as shafts. Junctions open into chambers (on the
 *       lowest level, flooded chambers: cave lakes), with loops and dead ends.</li>
 * </ul>
 * The pieces are ordinary passage pieces ({@link Passages.Seg}), so they carve, dress, flood and show
 * in the debug view like every other passage. Built once per system, the first time anything asks
 * (only from the terrain before rivers and the river network, so building one from inside river
 * carving can't loop back into it).
 */
public final class Karst {
    /** Height between levels, and the spacing of junctions. */
    static final double LEVEL_GAP = 24, SPACING = 44, MAX_LINK = 96;
    /** Cave-river pools: their length and how much each steps up from the one below. */
    static final double POOL_LEN = 140, POOL_STEP = 2;
    /** How deep the cave river runs over its floor. */
    static final double WATER_DEPTH = 1.3;
    /** The user's cap: at most this share of systems with a sinking stream. */
    static final double SINK_CHANCE = 0.35;
    /** Only small streams sink (catchment in nodes of 128 x 128 blocks). */
    static final double SINK_MAX_CATCHMENT = 48;
    static final int BIN = 64;
    /** Dev only (probes): every system tries for a sinking stream, and says why one fails. */
    static final boolean FORCE_SINK = Boolean.getBoolean("grandatlas.karstForceSink");

    private final AtlasCore core;
    private final long seed;
    private final ConcurrentHashMap<Long, Object> systems = new ConcurrentHashMap<>();
    private static final Object NONE = new Object();

    Karst(AtlasCore core, long seed) {
        this.core = core;
        this.seed = seed;
    }

    /** One system, as built. Read only after building. */
    public static final class KarstSystem {
        public double cx, cz, radius;
        /** The cave river's level: the spring's water. NaN for a dry system (no river near). */
        public double base = Double.NaN;
        /** {x, z} of the spring (the cave river's mouth), or null. */
        public double[] spring;
        /** {x, z, river level} where the stream sinks, or null. */
        public double[] swallet;
        public int levels;
        public int pieces;
        final List<Passages.Seg> segs = new ArrayList<>();
        final HashMap<Long, List<Passages.Seg>> bins = new HashMap<>();
        final HashMap<Long, float[]> dry = new HashMap<>();
        double minX = Double.MAX_VALUE, maxX = -Double.MAX_VALUE, minZ = Double.MAX_VALUE, maxZ = -Double.MAX_VALUE;

        public boolean covers(double x, double z) {
            return Math.hypot(x - cx, z - cz) < radius;
        }
    }

    // ---- lookups

    /** The system under the sinkhole field in meso cell (ci, cj), or null. */
    public KarstSystem system(int ci, int cj) {
        long key = ((long) ci << 32) ^ (cj & 0xFFFFFFFFL);
        Object o = systems.get(key);
        if (o == null) {
            MesoSite site = core.features().site(ci, cj);
            Object built = site != null && site.kind() == MesoKind.SINKHOLE_FIELD ? build(site) : NONE;
            Object prev = systems.putIfAbsent(key, built);
            o = prev != null ? prev : built;
        }
        return o == NONE ? null : (KarstSystem) o;
    }

    /** The system whose region holds (x, z), or null. */
    public KarstSystem near(double x, double z) {
        int ci = cellOf(x), cj = cellOf(z);
        for (int dj = -1; dj <= 1; dj++) {
            for (int di = -1; di <= 1; di++) {
                KarstSystem s = system(ci + di, cj + dj);
                if (s != null && s.covers(x, z)) return s;
            }
        }
        return null;
    }

    /** The nearest system with a sinking stream within {@code rings} meso cells, or null. */
    public KarstSystem nearestSink(double x, double z, int rings) {
        int ci = cellOf(x), cj = cellOf(z);
        KarstSystem best = null;
        double bestD = Double.MAX_VALUE;
        for (int dj = -rings; dj <= rings; dj++) {
            for (int di = -rings; di <= rings; di++) {
                KarstSystem s = system(ci + di, cj + dj);
                if (s == null || s.swallet == null) continue;
                double d = Math.hypot(s.cx - x, s.cz - z);
                if (d < bestD) {
                    bestD = d;
                    best = s;
                }
            }
        }
        return best;
    }

    /** The nearest system at all within {@code rings} meso cells, or null. */
    public KarstSystem nearest(double x, double z, int rings) {
        int ci = cellOf(x), cj = cellOf(z);
        KarstSystem best = null;
        double bestD = Double.MAX_VALUE;
        for (int dj = -rings; dj <= rings; dj++) {
            for (int di = -rings; di <= rings; di++) {
                KarstSystem s = system(ci + di, cj + dj);
                if (s == null) continue;
                double d = Math.hypot(s.cx - x, s.cz - z);
                if (d < bestD) {
                    bestD = d;
                    best = s;
                }
            }
        }
        return best;
    }

    private static int cellOf(double v) {
        return GradientNoise.floor(v / com.seaweed.grandatlas.core.micro.Features.CELL);
    }

    /** Every karst piece whose bounds meet the box, added to {@code out} once each. */
    void collect(double x0, double z0, double x1, double z1, List<Passages.Seg> out) {
        int ci0 = cellOf(x0 - 640), ci1 = cellOf(x1 + 640), cj0 = cellOf(z0 - 640), cj1 = cellOf(z1 + 640);
        Set<Passages.Seg> seen = null;
        for (int cj = cj0; cj <= cj1; cj++) {
            for (int ci = ci0; ci <= ci1; ci++) {
                KarstSystem s = system(ci, cj);
                if (s == null || s.maxX < x0 || s.minX > x1 || s.maxZ < z0 || s.minZ > z1) continue;
                for (int bj = GradientNoise.floor(z0 / BIN); bj <= GradientNoise.floor(z1 / BIN); bj++) {
                    for (int bi = GradientNoise.floor(x0 / BIN); bi <= GradientNoise.floor(x1 / BIN); bi++) {
                        List<Passages.Seg> list = s.bins.get(((long) bi << 32) ^ (bj & 0xFFFFFFFFL));
                        if (list == null) continue;
                        for (Passages.Seg g : list) {
                            if (g.maxX < x0 || g.minX > x1 || g.maxZ < z0 || g.minZ > z1) continue;
                            if (seen == null) seen = Collections.newSetFromMap(new IdentityHashMap<>());
                            if (seen.add(g)) out.add(g);
                        }
                    }
                }
            }
        }
    }

    /** For the river network ({@link Drainage.Sinks}): the stretch of node (i, j)'s course that has gone underground. */
    public float[] dry(int i, int j) {
        Drainage.Node n = core.drainage().node(i, j);
        int ci = cellOf(n.x()), cj = cellOf(n.z());
        long key = ((long) i << 32) ^ (j & 0xFFFFFFFFL);
        for (int dj = -1; dj <= 1; dj++) {
            for (int di = -1; di <= 1; di++) {
                KarstSystem s = system(ci + di, cj + dj);
                if (s == null) continue;
                float[] d = s.dry.get(key);
                if (d != null) return d;
            }
        }
        return null;
    }

    // ---- building

    private double ground(double x, double z) {
        return core.sampleBase(x, z).height();
    }

    private static final class Node {
        final double x, z;
        double y;
        int level;
        int parent = -1;
        double dist;      // along the tree to its root
        int flow = 1;     // sources above it, itself counted
        int degree;
        boolean wetOk;    // near the river: may carve where the ground is wet (sump, swallet)
        Node(double x, double y, double z, int level) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.level = level;
        }
    }

    /** A course point found near the system: {x, z, surface, arc along its edge, node i, node j}. */
    private record Point(double x, double z, double surf, double arc, Drainage.Node node) {}

    private KarstSystem build(MesoSite site) {
        KarstSystem sys = new KarstSystem();
        long h = GradientNoise.mix(seed ^ site.seed());
        Rng rnd = new Rng(h);
        sys.cx = site.x();
        sys.cz = site.z();
        sys.radius = Math.max(260, Math.min(460, site.radius() * 2.2));
        double R = sys.radius;
        Drainage d = core.drainage();

        // ---- the rivers round it
        List<Point> courses = new ArrayList<>();
        int ri0 = Drainage.cell(sys.cx - R - 300), ri1 = Drainage.cell(sys.cx + R + 300);
        int rj0 = Drainage.cell(sys.cz - R - 300), rj1 = Drainage.cell(sys.cz + R + 300);
        for (int j = rj0; j <= rj1; j++) {
            for (int i = ri0; i <= ri1; i++) {
                Drainage.Node n = d.node(i, j);
                if (!n.river()) continue;
                double[][] line = d.courseLine(n);
                double arc = 0;
                for (int k = 0; k < line.length; k++) {
                    if (k > 0) arc += Math.hypot(line[k][0] - line[k - 1][0], line[k][1] - line[k - 1][1]);
                    courses.add(new Point(line[k][0], line[k][1], line[k][2], arc, n));
                }
            }
        }

        // ---- a sinking stream, or the nearest river's bank, gives the spring
        List<double[]> dryStretches = new ArrayList<>(); // {i, j, from, to}
        double[] spring = null, swallet = null;
        double base = Double.NaN;
        if (rnd.next() < SINK_CHANCE || FORCE_SINK) {
            Object[] plan = planSink(d, courses, sys, rnd);
            if (plan != null) {
                swallet = (double[]) plan[0];
                spring = (double[]) plan[1];
                base = spring[2];
                @SuppressWarnings("unchecked")
                List<double[]> st = (List<double[]>) plan[2];
                dryStretches.addAll(st);
            }
        }
        if (spring == null) {
            Point best = null;
            double bd = R + 220;
            for (Point p : courses) {
                double dd = Math.hypot(p.x - sys.cx, p.z - sys.cz);
                if (dd < bd && !Double.isNaN(p.surf)) {
                    bd = dd;
                    best = p;
                }
            }
            if (best != null) {
                spring = new double[] {best.x, best.z, best.surf};
                base = best.surf;
            }
        }
        double floorY;
        if (Double.isNaN(base)) {
            // No river near: a dry system, its lowest level well under the lowest ground round it.
            double low = Double.MAX_VALUE;
            for (int k = 0; k < 16; k++) {
                double a = k * Math.PI / 8;
                low = Math.min(low, ground(sys.cx + Math.cos(a) * R, sys.cz + Math.sin(a) * R));
            }
            floorY = low - 22;
        } else {
            floorY = base;
        }
        sys.base = base;
        sys.spring = spring == null ? null : new double[] {spring[0], spring[1]};
        sys.swallet = swallet;
        final double W = floorY;
        final double cap = swallet != null ? Math.max(W, swallet[2] - 3) : Double.MAX_VALUE;
        boolean wet = !Double.isNaN(base);

        // ---- the levels: the cave river at the water; dry levels over it where the rock allows (karst
        // country is gentle, often only ~10 blocks over its river, so there may be none); a flooded
        // level under it, the deep loops a spring rises from.
        List<Double> gs = new ArrayList<>();
        for (int gj = -3; gj <= 3; gj++) {
            for (int gi = -3; gi <= 3; gi++) {
                double x = sys.cx + gi * R / 3.5, z = sys.cz + gj * R / 3.5;
                if (Math.hypot(x - sys.cx, z - sys.cz) <= R) gs.add(ground(x, z));
            }
        }
        Collections.sort(gs);
        double gMed = gs.get(gs.size() / 2);
        List<Double> levelY = new ArrayList<>();
        levelY.add(W);
        for (double y = W + LEVEL_GAP; y <= gMed - 22 && levelY.size() < 4; y += LEVEL_GAP) levelY.add(y);
        List<List<Node>> levels = new ArrayList<>();
        for (int lv = 0; lv < levelY.size(); lv++) {
            List<Node> nodes = junctions(sys, courses, h, lv, levelY.get(lv), lv == 0 && wet ? 5 : -1, lv == 0 ? 30 : 24);
            if (nodes.size() < 4) break;
            levels.add(nodes);
        }
        if (levels.isEmpty()) return finish(sys);
        sys.levels = levels.size();

        // ---- the lowest level: a tree draining to the spring
        List<Node> l0 = levels.get(0);
        Node root;
        if (spring != null) {
            root = new Node(spring[0], W, spring[1], 0);
            root.wetOk = true;
        } else {
            root = l0.get(0);
            for (Node n : l0) if (Math.hypot(n.x - sys.cx, n.z - sys.cz) > Math.hypot(root.x - sys.cx, root.z - sys.cz)) root = n;
            l0.remove(root);
        }
        Node sink = null;
        if (swallet != null) {
            sink = new Node(swallet[0], W, swallet[1], 0);
            sink.wetOk = true;
            l0.add(sink);
        }
        List<Node> tree0 = new ArrayList<>();
        tree0.add(root);
        tree0.addAll(l0);
        prim(tree0, 0, rnd, spring != null ? 180 : MAX_LINK);
        flows(tree0);
        // Water and floor: flat pools stepping up upstream.
        for (Node n : tree0) {
            if (n == root) continue;
            n.y = wet ? pool(n.dist, W, cap) + 0.3 : W + 0.012 * n.dist;
        }
        root.y = W - 1.0;

        // ---- the passages of level 0
        List<Passages.Seg> out = sys.segs;
        for (int k = 1; k < tree0.size(); k++) {
            Node c = tree0.get(k);
            if (c.parent < 0) continue;
            Node p = tree0.get(c.parent);
            double r = Math.min(7.5, 3.3 + 1.15 * Math.log(1 + c.flow));
            if (p == root && spring != null) {
                // The spring: dive under the roof (the sump), then rise to the mouth in the river's bed.
                double dx = p.x - c.x, dz = p.z - c.z, dl = Math.max(1, Math.hypot(dx, dz));
                double mx = p.x - dx / dl * 14, mz = p.z - dz / dl * 14;
                double rs = 2.7;
                Style sump = Style.tube(rs, 0.9, 0.5).water(W).wetOk().open();
                walk(out, c.x, c.y, c.z, mx, W - 6.5, mz, rnd.nextLong(), sump, null);
                walk(out, mx, W - 6.5, mz, p.x, W - 1.0, p.z, rnd.nextLong(), sump, null);
                continue;
            }
            boolean nearWet = c.wetOk || p.wetOk;
            double dc = c.dist, dp = p.dist;
            Style st = Style.tube(r, 0.85 + 0.15 * rnd.next(), 0.42);
            if (wet) {
                DoubleUnaryOperator yAt = t -> pool(dc + (dp - dc) * t, W, cap) + 0.3;
                DoubleUnaryOperator waterAt = t -> pool(dc + (dp - dc) * t, W, cap);
                st = st.levelled(yAt, waterAt).roofUnder(this::roofCap);
            }
            if (nearWet) st = st.wetOk();
            walk(out, c.x, c.y, c.z, p.x, p.y, p.z, rnd.nextLong(), st, null);
        }

        // ---- the older levels: dry trees, joined down by shafts, their roots old springs
        List<List<Node>> trees = new ArrayList<>();
        trees.add(tree0);
        for (int lv = 1; lv < levels.size(); lv++) {
            List<Node> nodes = levels.get(lv);
            // The root: the junction nearest the spring's side (or the far side for a dry system).
            double tx = spring != null ? spring[0] : root.x, tz = spring != null ? spring[1] : root.z;
            Node r0 = nodes.get(0);
            for (Node n : nodes) if (Math.hypot(n.x - tx, n.z - tz) < Math.hypot(r0.x - tx, r0.z - tz)) r0 = n;
            nodes.remove(r0);
            nodes.add(0, r0);
            prim(nodes, 0, rnd, MAX_LINK);
            flows(nodes);
            trees.add(nodes);
            for (int k = 1; k < nodes.size(); k++) {
                Node c = nodes.get(k);
                if (c.parent < 0) continue;
                Node p = nodes.get(c.parent);
                double r = Math.min(6.0, 2.5 + 0.95 * Math.log(1 + c.flow));
                Style st = (c.flow <= 2 || rnd.next() < 0.25 ? Style.canyon(r, 1.0, 0.5) : Style.tube(r, 0.75 + 0.2 * rnd.next(), 0.5)).at(lv);
                walk(out, c.x, c.y, c.z, p.x, p.y, p.z, rnd.nextLong(), st, null);
            }
            // The old spring: a dry passage out to the valley side.
            entrance(out, r0, tx, tz, rnd);
            // Down to the level below: shafts and steep canyons from a few junctions, the farthest first.
            List<Node> below = trees.get(lv - 1);
            List<Node> order = new ArrayList<>(nodes);
            order.sort((a, b) -> Double.compare(b.dist, a.dist));
            int links = 2 + (int) (rnd.next() * 3);
            for (int k = 0; k < Math.min(links, order.size()); k++) {
                Node a = order.get(k * Math.max(1, order.size() / (links + 1)));
                Node b = nearest(below, a.x, a.z, null);
                if (b == null) continue;
                double horiz = Math.hypot(a.x - b.x, a.z - b.z);
                Style st = (horiz < 20 ? Style.shaft(2.6 + 1.6 * rnd.next()) : Style.canyon(2.8 + 1.5 * rnd.next(), 1.1, 0.5)).at(lv);
                walk(out, a.x, a.y, a.z, b.x, b.y + 2, b.z, rnd.nextLong(), st, null);
            }
        }

        // ---- the flooded level: phreatic loops under the cave river, joined up to it by drowned shafts
        if (wet) {
            List<Node> deep = junctions(sys, courses, h ^ 0xDEE9L, 9, W - 20, -1, 20);
            if (deep.size() >= 4) {
                Node d0 = nearest(deep, root.x, root.z, null);
                deep.remove(d0);
                deep.add(0, d0);
                prim(deep, 0, rnd, MAX_LINK);
                for (int k = 1; k < deep.size(); k++) {
                    Node c = deep.get(k);
                    if (c.parent < 0) continue;
                    Node p = deep.get(c.parent);
                    walk(out, c.x, c.y, c.z, p.x, p.y, p.z, rnd.nextLong(), Style.tube(2.4 + 1.6 * rnd.next(), 0.8, 0.5).water(W).at(-2), null);
                }
                int links = 2 + (int) (rnd.next() * 2);
                for (int k = 0; k < links; k++) {
                    Node a = deep.get((int) (rnd.next() * deep.size()));
                    Node b = nearest(tree0, a.x, a.z, root);
                    if (b == null) continue;
                    walk(out, a.x, a.y, a.z, b.x, b.y - 2, b.z, rnd.nextLong(), Style.shaft(2.4 + rnd.next()).water(W).at(-2), null);
                }
            }
        }

        // ---- loops, chambers and dead ends on every level
        for (int lv = 0; lv < trees.size(); lv++) {
            List<Node> nodes = trees.get(lv);
            boolean lowest = lv == 0;
            for (int k = 0; k < nodes.size(); k++) {
                Node a = nodes.get(k);
                if (a == root && spring != null) continue;
                long nh = GradientNoise.hash(h ^ 0x100L, (int) Math.floor(a.x), lv, (int) Math.floor(a.z));
                // A loop to a junction nearby that isn't its parent or child.
                if (unit(nh, 0) < 0.14) {
                    Node b = null;
                    double bd = 72;
                    for (Node o : nodes) {
                        if (o == a || o == root || (o.parent >= 0 && nodes.get(o.parent) == a) || (a.parent >= 0 && nodes.get(a.parent) == o)) continue;
                        double dd = Math.hypot(o.x - a.x, o.z - a.z);
                        if (dd < bd && dd > 20) {
                            bd = dd;
                            b = o;
                        }
                    }
                    if (b != null) {
                        Style st = Style.tube(2.4 + 1.6 * unit(nh, 1), 0.8, 0.5).at(lv);
                        if (lowest && wet) {
                            double level = Math.min(pool(a.dist, W, cap), pool(b.dist, W, cap));
                            final double lvW = level;
                            st = st.levelled(t -> lvW + 0.3, t -> lvW);
                            walk(out, a.x, level + 0.3, a.z, b.x, level + 0.3, b.z, nh ^ 0x7L, st, null);
                        } else {
                            walk(out, a.x, a.y, a.z, b.x, b.y, b.z, nh ^ 0x7L, st, null);
                        }
                    }
                }
                // A chamber where three or more passages meet, and now and then elsewhere.
                if (a.degree >= 3 ? unit(nh, 2) < 0.55 : unit(nh, 2) < 0.12) {
                    double r = (lowest ? 7 : 6) + (lowest ? 7 : 5) * unit(nh, 3);
                    double vr = 0.45 + 0.25 * unit(nh, 4);
                    Passages.Seg s = new Passages.Seg();
                    s.ax = s.bx = a.x;
                    s.az = s.bz = a.z;
                    double floorAt = lowest && wet ? pool(a.dist, W, cap) - WATER_DEPTH - 0.4 : a.y - 2.5;
                    if (lowest && wet) {
                        double room = roofCap(a.x, a.z) - floorAt; // floor to the highest roof the rock allows
                        vr = Math.min(vr, room / (r * 1.55));
                        if (vr < 0.22) continue;
                    }
                    s.ay = s.by = floorAt + r * vr * 0.55;
                    s.ra = s.rb = r;
                    s.vr = vr;
                    s.kind = Passages.CHAMBER;
                    if (lowest && wet) s.water = pool(a.dist, W, cap);
                    s.karst = lv;
                    s.wetOk = a.wetOk;
                    s.bounds();
                    out.add(s);
                }
                // A dead end wandering off and narrowing.
                if (unit(nh, 5) < 0.2) {
                    double ang = unit(nh, 6) * Math.PI * 2, len = 25 + 45 * unit(nh, 7);
                    double bx = a.x + Math.cos(ang) * len, bz = a.z + Math.sin(ang) * len;
                    double by = lowest ? a.y + 3 + 4 * unit(nh, 8) : a.y + (unit(nh, 8) - 0.4) * 10;
                    walk(out, a.x, a.y, a.z, bx, by, bz, nh ^ 0x5B0BL, Style.tube(2.2 + 1.3 * unit(nh, 9), 0.75, 0.5).tapered().at(lv), null);
                }
            }
        }

        // ---- the ways in: the sinkholes' shafts, and the swallet
        for (MicroFeature m : site.micros()) {
            if (m.kind != MicroKind.SINKHOLE) continue; // cenotes keep their pools (whose level is only known later)
            double top = m.ground - m.h + 2;
            Node best = null;
            double bd = Double.MAX_VALUE;
            for (List<Node> nodes : trees) {
                for (Node n : nodes) {
                    if (n.y > top - 10 || (n == root && spring != null)) continue;
                    double dd = Math.hypot(n.x - m.x, n.z - m.z) + 0.3 * (top - n.y);
                    if (dd < bd) {
                        bd = dd;
                        best = n;
                    }
                }
            }
            if (best == null) continue;
            Style st = Style.shaft(Math.max(2.2, Math.min(3.6, m.r * 0.45))).open();
            walk(out, m.x, top, m.z, best.x, best.y + 3, best.z, GradientNoise.hash(h, (int) m.x, (int) m.z), st, null);
        }
        if (sink != null && swallet != null) {
            // The stream pours down a shaft from its bed into the cave river (world/DeepWater turns the
            // river water over it into a waterfall).
            double sw = wet ? pool(sink.dist, W, cap) : Double.NaN;
            Style st = Style.shaft(3.2).wetOk().open();
            if (!Double.isNaN(sw)) st = st.water(sw); // its foot stands in the pool
            walk(out, swallet[0], swallet[2] - 0.6, swallet[1], sink.x, sink.y - 0.5, sink.z, h ^ 0x5A11L, st, null);
            for (Node o : tree0) {
                boolean joined = o != sink && (o.parent >= 0 && tree0.get(o.parent) == sink
                        || sink.parent >= 0 && tree0.get(sink.parent) == o);
                if (!joined) continue;
                double dx = o.x - sink.x, dz = o.z - sink.z, dl = Math.max(1, Math.hypot(dx, dz)), f = Math.min(1, 9 / dl);
                Style stub = Style.tube(2.6, 0.8, 0.5).wetOk().open();
                if (!Double.isNaN(sw)) stub = stub.water(sw);
                walk(out, sink.x, sink.y, sink.z, sink.x + dx * f, sink.y + (o.y - sink.y) * f, sink.z + dz * f, h ^ 0x5A12L ^ (long) o.x, stub, null);
            }
        }

        for (double[] ds : dryStretches) sys.dry.put(((long) (int) ds[0] << 32) ^ ((int) ds[1] & 0xFFFFFFFFL), new float[] {(float) ds[2], (float) ds[3]});
        return finish(sys);
    }

    /**
     * The junctions of one level at height y: a jittered grid across the system (with gaps), kept where
     * the rock over them is thick enough and clear of the rivers' valleys. {@code riverRoom} >= 0: the
     * cave river's level, kept where the ground stands that far over it (its passages fit their roofs
     * under the rock: {@link #roofCap}); otherwise 14 blocks of rock over the passage.
     */
    private List<Node> junctions(KarstSystem sys, List<Point> courses, long h, int lv, double y0, double riverRoom, double clear) {
        List<Node> nodes = new ArrayList<>();
        double R = sys.radius;
        int span = (int) Math.ceil(R / SPACING);
        for (int gj = -span; gj <= span; gj++) {
            for (int gi = -span; gi <= span; gi++) {
                long gh = GradientNoise.hash(h ^ (lv * 0x51L), gi, gj);
                double x = sys.cx + (gi + 0.8 * (unit(gh, 0) - 0.5)) * SPACING;
                double z = sys.cz + (gj + 0.8 * (unit(gh, 1) - 0.5)) * SPACING;
                double rr = Math.hypot(x - sys.cx, z - sys.cz);
                double edge = R * (0.82 + 0.18 * GradientNoise.noise2(h ^ 0xED6EL, Math.atan2(z - sys.cz, x - sys.cx) * 2, lv));
                if (rr > edge || unit(gh, 2) < 0.12) continue; // gaps: not a lattice
                double y = y0 + (riverRoom >= 0 ? 0 : (unit(gh, 3) - 0.5) * 8);
                double g = ground(x, z);
                if (riverRoom >= 0 ? g - y0 < riverRoom : g - (y + 8) < 14) continue;
                if (nearCourse(courses, x, z, clear)) continue;
                nodes.add(new Node(x, y, z, lv));
            }
        }
        return nodes;
    }

    /** The highest a cave-river passage's roof may reach here: 7 blocks of rock under the ground before rivers. */
    private double roofCap(double x, double z) {
        return ground(x, z) - 7;
    }

    private static double pool(double dist, double W, double cap) {
        return Math.min(cap, W + POOL_STEP * Math.floor(dist / POOL_LEN));
    }

    private KarstSystem finish(KarstSystem sys) {
        for (Passages.Seg s : sys.segs) {
            s.province = Passages.KARST;
            sys.minX = Math.min(sys.minX, s.minX);
            sys.maxX = Math.max(sys.maxX, s.maxX);
            sys.minZ = Math.min(sys.minZ, s.minZ);
            sys.maxZ = Math.max(sys.maxZ, s.maxZ);
            for (int bj = GradientNoise.floor(s.minZ / BIN); bj <= GradientNoise.floor(s.maxZ / BIN); bj++) {
                for (int bi = GradientNoise.floor(s.minX / BIN); bi <= GradientNoise.floor(s.maxX / BIN); bi++) {
                    sys.bins.computeIfAbsent(((long) bi << 32) ^ (bj & 0xFFFFFFFFL), k -> new ArrayList<>()).add(s);
                }
            }
        }
        sys.pieces = sys.segs.size();
        return sys;
    }

    /**
     * A sinking stream: a small tributary crossing the system's middle, its water going underground at
     * the point nearest the middle and coming back out 140-380 blocks downstream, before any other
     * river joins it. {swallet {x, z, level}, spring {x, z, level}, dry stretches}, or null.
     */
    private Object[] planSink(Drainage d, List<Point> courses, KarstSystem sys, Rng rnd) {
        Point best = null;
        double bd = sys.radius * 0.9;
        for (Point p : courses) {
            Drainage.Node n = p.node;
            if (n.catchment() > SINK_MAX_CATCHMENT || Double.isNaN(p.surf) || p.surf <= AtlasCore.SEA_LEVEL + 2) continue;
            double dd = Math.hypot(p.x - sys.cx, p.z - sys.cz);
            if (dd < bd) {
                bd = dd;
                best = p;
            }
        }
        if (best == null) { if (FORCE_SINK) System.out.println("[karst] no sink at " + (int) sys.cx + " " + (int) sys.cz + ": no small stream within 0.7 R"); return null; }
        double want = 120 + 240 * rnd.next();
        List<double[]> stretches = new ArrayList<>();
        Drainage.Node n = best.node;
        double from = best.arc, travelled = 0;
        double start = from + 3; // the channel (and its water) reaches into the shaft
        double[] spring = null;
        for (int hop = 0; hop < 6 && spring == null; hop++) {
            double[][] line = d.courseLine(n);
            if (line.length < 2) { if (FORCE_SINK) System.out.println("[karst] no sink at " + (int) sys.cx + " " + (int) sys.cz + ": short course"); return null; }
            double arc = 0, endArc = 0;
            for (int k = 1; k < line.length; k++) endArc += Math.hypot(line[k][0] - line[k - 1][0], line[k][1] - line[k - 1][1]);
            for (int k = 1; k < line.length; k++) {
                double seg = Math.hypot(line[k][0] - line[k - 1][0], line[k][1] - line[k - 1][1]);
                double a0 = arc;
                arc += seg;
                if (arc <= from) continue;
                travelled += arc - Math.max(a0, from);
                if (travelled >= want && arc < endArc - 6) {
                    if (Float.isNaN((float) line[k][2])) { if (FORCE_SINK) System.out.println("[karst] no sink at " + (int) sys.cx + " " + (int) sys.cz + ": spring on a fall face"); return null; } // on a fall's face
                    spring = new double[] {line[k][0], line[k][1], line[k][2]};
                    stretches.add(new double[] {n.i(), n.j(), start, arc});
                    break;
                }
            }
            if (spring != null) break;
            // On to the next reach, unless another river joins there or a lake lies on it: then the
            // stream comes back out just before, if it has been under long enough.
            Drainage.Node next = d.next(n);
            if (next == null || !next.river() || !Double.isNaN(d.lakeLevel(next)) || inflows(d, next) > 1) {
                int k = line.length - 1;
                double back = 0;
                while (k > 1 && back < 10) {
                    back += Math.hypot(line[k][0] - line[k - 1][0], line[k][1] - line[k - 1][1]);
                    k--;
                }
                double stop = endArc - back;
                if (travelled - back >= 100 && stop > from + 20 && !Double.isNaN(line[k][2])) {
                    spring = new double[] {line[k][0], line[k][1], line[k][2]};
                    stretches.add(new double[] {n.i(), n.j(), start, stop});
                    break;
                }
                if (FORCE_SINK) System.out.println("[karst] no sink at " + (int) sys.cx + " " + (int) sys.cz + ": junction, lake or end too soon");
                return null;
            }
            stretches.add(new double[] {n.i(), n.j(), start, endArc + 1});
            n = next;
            from = 0;
            start = -2;
        }
        if (spring == null) { if (FORCE_SINK) System.out.println("[karst] no sink at " + (int) sys.cx + " " + (int) sys.cz + ": ran out of reaches"); return null; }
        if (Math.hypot(spring[0] - sys.cx, spring[1] - sys.cz) > sys.radius + 140) { if (FORCE_SINK) System.out.println("[karst] no sink at " + (int) sys.cx + " " + (int) sys.cz + ": spring too far"); return null; }
        if (best.surf - spring[2] < 4) { if (FORCE_SINK) System.out.println("[karst] no sink at " + (int) sys.cx + " " + (int) sys.cz + ": drop under 4"); return null; } // needs a fall for the shaft
        return new Object[] {new double[] {best.x, best.z, best.surf}, spring, stretches};
    }

    private static int inflows(Drainage d, Drainage.Node n) {
        int count = 0;
        for (int dj = -1; dj <= 1; dj++) {
            for (int di = -1; di <= 1; di++) {
                if (di == 0 && dj == 0) continue;
                Drainage.Node m = d.node(n.i() + di, n.j() + dj);
                if (!m.river()) continue;
                Drainage.Node to = d.next(m);
                if (to != null && to.i() == n.i() && to.j() == n.j()) count++;
            }
        }
        return count;
    }

    private static boolean nearCourse(List<Point> courses, double x, double z, double r) {
        for (Point p : courses) if (Math.abs(p.x - x) < r && Math.abs(p.z - z) < r && Math.hypot(p.x - x, p.z - z) < r) return true;
        return false;
    }

    private static Node nearest(List<Node> nodes, double x, double z, Node not) {
        Node best = null;
        double bd = Double.MAX_VALUE;
        for (Node n : nodes) {
            if (n == not) continue;
            double dd = Math.hypot(n.x - x, n.z - z);
            if (dd < bd) {
                bd = dd;
                best = n;
            }
        }
        return best;
    }

    /** A spanning tree from node {@code rootIndex}, links no longer than {@code maxLink} (the root's may be longer), weights jittered. */
    private static void prim(List<Node> nodes, int rootIndex, Rng rnd, double rootLink) {
        int n = nodes.size();
        boolean[] in = new boolean[n];
        double[] best = new double[n];
        int[] from = new int[n];
        double[] jit = new double[n];
        for (int k = 0; k < n; k++) {
            best[k] = Double.MAX_VALUE;
            from[k] = -1;
            jit[k] = 0.8 + 0.5 * rnd.next();
        }
        best[rootIndex] = 0;
        Node r = nodes.get(rootIndex);
        r.dist = 0;
        for (int it = 0; it < n; it++) {
            int u = -1;
            for (int k = 0; k < n; k++) if (!in[k] && best[k] < Double.MAX_VALUE && (u < 0 || best[k] < best[u])) u = k;
            if (u < 0) break;
            in[u] = true;
            Node a = nodes.get(u);
            if (from[u] >= 0) {
                a.parent = from[u];
                Node p = nodes.get(from[u]);
                a.dist = p.dist + Math.hypot(a.x - p.x, a.z - p.z);
                a.degree++;
                p.degree++;
            }
            double limit = u == rootIndex ? rootLink : MAX_LINK;
            for (int k = 0; k < n; k++) {
                if (in[k]) continue;
                Node b = nodes.get(k);
                double dd = Math.hypot(a.x - b.x, a.z - b.z);
                if (dd > limit) continue;
                double w = dd * jit[k] * (u == rootIndex ? 1 : 1);
                if (w < best[k]) {
                    best[k] = w;
                    from[k] = u;
                }
            }
        }
    }

    /** Sources above each junction, leaves counting one. */
    private static void flows(List<Node> nodes) {
        Integer[] order = new Integer[nodes.size()];
        for (int k = 0; k < order.length; k++) order[k] = k;
        java.util.Arrays.sort(order, (a, b) -> Double.compare(nodes.get(b).dist, nodes.get(a).dist));
        for (Node n : nodes) n.flow = 1;
        for (int k : order) {
            Node n = nodes.get(k);
            if (n.parent >= 0) nodes.get(n.parent).flow += n.flow;
        }
    }

    /** A dry passage from a level's root rising to the ground toward (tx, tz): the old spring, now an entrance. */
    private void entrance(List<Passages.Seg> out, Node from, double tx, double tz, Rng rnd) {
        double ang = Math.atan2(tz - from.z, tx - from.x) + (rnd.next() - 0.5) * 0.8, ux = Math.cos(ang), uz = Math.sin(ang);
        for (double dd = 16; dd <= 120; dd += 8) {
            double x = from.x + ux * dd, z = from.z + uz * dd, y = from.y + dd * 0.22;
            double g = ground(x, z);
            if (y < g - 3) continue;
            walk(out, from.x, from.y, from.z, x, g - 1, z, rnd.nextLong(), Style.tube(3.2 + rnd.next() * 1.5, 0.85, 0.5).open().at(from.level), null);
            return;
        }
    }

    // ---- passages

    /** How a passage looks: its cross-section, size, water and permissions. */
    static final class Style {
        int kind;
        double r, vr, floor;
        boolean wetOk, open, taper, vary;
        double bedding;
        int level;
        double water = Double.NaN;
        DoubleUnaryOperator yAt, waterAt;
        java.util.function.DoubleBinaryOperator roof;

        Style roofUnder(java.util.function.DoubleBinaryOperator cap) {
            roof = cap;
            return this;
        }

        static Style tube(double r, double vr, double floor) {
            Style s = new Style();
            s.kind = Passages.TUBE;
            s.r = r;
            s.vr = vr;
            s.floor = floor;
            return s;
        }

        static Style canyon(double r, double vr, double floor) {
            Style s = tube(r, vr, floor);
            s.kind = Passages.CANYON;
            return s;
        }

        static Style shaft(double r) {
            Style s = tube(r, 1, 0.6);
            s.kind = Passages.SHAFT;
            return s;
        }

        Style water(double w) {
            water = w;
            return this;
        }

        Style wetOk() {
            wetOk = true;
            return this;
        }

        Style open() {
            open = true;
            return this;
        }

        Style at(int lv) {
            level = lv;
            return this;
        }

        Style tapered() {
            taper = true;
            return this;
        }

        /** Height and width wander more along the passage, and its floor climbs and dips (solution caves). */
        Style varied() {
            vary = true;
            return this;
        }

        /** A flat roof at this fraction of the height over the axis. */
        Style bedding(double f) {
            bedding = f;
            return this;
        }

        Style levelled(DoubleUnaryOperator y, DoubleUnaryOperator w) {
            yAt = y;
            waterAt = w;
            return this;
        }
    }

    /**
     * A passage from a to b as a steered random walk (as {@code Passages.walk}): it wanders early and
     * homes in late. A levelled style sets each piece's height (and water) from how far along it is,
     * so the cave river's pools stay flat.
     */
    static void walk(List<Passages.Seg> out, double ax, double ay, double az, double bx, double by, double bz,
                      long h, Style st, Object unused) {
        double dx = bx - ax, dy = by - ay, dz = bz - az;
        double hl = Math.hypot(dx, dz), len = Math.sqrt(hl * hl + dy * dy);
        if (len < 1) return;
        Rng rnd = new Rng(h);
        long noise = GradientNoise.fork(h, 77);
        int kind = st.kind == Passages.TUBE || st.kind == Passages.CANYON ? (Math.abs(dy) / Math.max(1, hl) > 0.9 ? Passages.SHAFT : st.kind) : st.kind;
        double step = kind == Passages.SHAFT ? 6 : 5;
        double yaw = Math.atan2(dz, dx) + (rnd.next() - 0.5) * 1.2;
        double pitch = Math.atan2(dy, Math.max(1, hl));
        double yawV = 0, pitchV = 0;
        double x = ax, y = ay, z = az, travelled = 0, lastR = -1;
        double slotDepth0 = 0.6 + 1.4 * rnd.next();
        double bulgeAt = rnd.next() < 0.3 ? rnd.next() * len : -1e9, bulge = 0.4 + 0.7 * rnd.next();
        int maxSteps = (int) (len / step * 3) + 4;
        for (int k = 0; k < maxSteps; k++) {
            double nx, ny, nz;
            boolean last = false;
            double rx = bx - x, ry = by - y, rz = bz - z, rd = Math.sqrt(rx * rx + ry * ry + rz * rz);
            if (rd < step * 1.5 || k == maxSteps - 1) {
                nx = bx;
                ny = by;
                nz = bz;
                last = true;
            } else {
                double pull = 0.15 + 0.85 * Math.min(1, travelled / (len * 1.1));
                yawV = yawV * 0.75 + (rnd.next() - rnd.next()) * rnd.next() * 1.6;
                pitchV = pitchV * 0.9 + (rnd.next() - rnd.next()) * rnd.next() * 0.5;
                yaw += yawV * 0.25;
                pitch += pitchV * 0.1;
                double da = Math.atan2(rz, rx) - yaw;
                while (da > Math.PI) da -= 2 * Math.PI;
                while (da < -Math.PI) da += 2 * Math.PI;
                yaw += da * pull * 0.45;
                pitch += (Math.atan2(ry, Math.max(1, Math.hypot(rx, rz))) - pitch) * (kind == Passages.SHAFT ? 0.8 : pull * 0.5);
                nx = x + Math.cos(yaw) * Math.cos(pitch) * step;
                nz = z + Math.sin(yaw) * Math.cos(pitch) * step;
                ny = y + Math.sin(pitch) * step;
            }
            double seg = Math.sqrt((nx - x) * (nx - x) + (ny - y) * (ny - y) + (nz - z) * (nz - z));
            double t0 = Math.min(1, travelled / len);
            travelled += seg;
            double t1 = Math.min(1, travelled / len);
            double along = st.vary ? 0.6 + 0.8 * (GradientNoise.noise2(noise, travelled / 30.0, 0.5) * 0.5 + 0.5)
                    : 0.8 + 0.4 * (GradientNoise.noise2(noise, travelled / 34.0, 0.5) * 0.5 + 0.5);
            double bump = 1 + bulge * Math.exp(-sq((travelled - bulgeAt) / 10));
            double taper = st.taper ? Math.max(0.3, 1 - 0.7 * t1) : 1;
            double r = st.r * along * bump * taper;
            Passages.Seg s = new Passages.Seg();
            s.ax = x;
            s.az = z;
            s.bx = nx;
            s.bz = nz;
            if (st.yAt != null) {
                // Levelled: flat within a pool, stepping where the pool changes; the floor a little under
                // the water whatever the passage's size.
                double tm = (t0 + t1) / 2;
                s.water = st.waterAt.applyAsDouble(tm);
                s.ay = s.by = s.water - WATER_DEPTH + Math.max(lastR < 0 ? r : lastR, r) * st.vr * st.floor;
                ny = s.by;
            } else if (st.vary) {
                // The floor climbs and dips between the ends, which stay put.
                s.ay = y + 3.5 * GradientNoise.noise2(noise ^ 0x6F1L, (travelled - seg) / 26.0, 4.5) * Math.sin(Math.PI * t0);
                s.by = ny + 3.5 * GradientNoise.noise2(noise ^ 0x6F1L, travelled / 26.0, 4.5) * Math.sin(Math.PI * t1);
                s.water = st.water;
            } else {
                s.ay = y;
                s.by = ny;
                s.water = st.water;
            }
            s.ra = lastR < 0 ? r : lastR;
            s.rb = r;
            s.vr = st.vary ? st.vr * (0.7 + 0.6 * (GradientNoise.noise2(noise ^ 0x77L, travelled / 40.0, 3.5) * 0.5 + 0.5)) : st.vr;
            s.roof = st.bedding;
            if (st.roof != null && !Double.isNaN(s.water)) {
                // Under thin rock the passage stays wide but lowers its roof: a low canal over the
                // water. Where even that won't fit it dives under (a sump), its roof under the water.
                double roof = st.roof.applyAsDouble((s.ax + s.bx) / 2, (s.az + s.bz) / 2);
                double rr = Math.max(s.ra, s.rb);
                double fl = s.ay - rr * s.vr * st.floor; // the floor stays put
                double vr = Math.min(st.vr, (roof - fl) / (rr * (1 + st.floor)));
                if (vr >= 0.3) {
                    s.vr = vr;
                    s.ay = s.by = fl + rr * vr * st.floor;
                } else {
                    s.vr = 0.45;
                    s.ay = s.by = s.water - 0.6 - rr * 0.45;
                }
            }
            s.kind = kind;
            s.open = st.open;
            s.wetOk = st.wetOk;
            s.ribs = st.floor;
            s.karst = st.level;
            if (kind == Passages.CANYON) {
                double dn = GradientNoise.noise2(noise ^ 0x51L, travelled / 45.0, 1.5) * 0.5 + 0.5;
                s.slot = Math.min(14, r * slotDepth0 * (0.25 + 1.5 * dn));
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

    private static double sq(double v) {
        return v * v;
    }

    private static double unit(long hash, int k) {
        return (GradientNoise.mix(hash + k * 0x9E3779B97F4A7C15L) >>> 11) * 0x1.0p-53;
    }

    /** SplitMix64, so a system is the same in every thread. */
    static final class Rng {
        private long state;

        Rng(long seed) {
            state = seed;
        }

        double next() {
            state += 0x9E3779B97F4A7C15L;
            return (GradientNoise.mix(state) >>> 11) * 0x1.0p-53;
        }

        long nextLong() {
            state += 0x9E3779B97F4A7C15L;
            return GradientNoise.mix(state);
        }
    }
}
