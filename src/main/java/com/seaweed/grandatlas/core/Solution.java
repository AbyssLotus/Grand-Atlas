package com.seaweed.grandatlas.core;

import com.seaweed.grandatlas.core.noise.GradientNoise;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The solution-caves baseline province (cave rework C1, docs/CAVE_RESEARCH.md): the caves under
 * ordinary country, everywhere a karst system (core/Karst) doesn't own the ground. They carry most of
 * the underground's volume (the user's bar: Grand Atlas's caves, vanilla's off, open at least 75% of
 * what vanilla's do in every band), so they are full systems, not filler.
 *
 * <ul>
 *   <li><b>Levels</b> at fixed heights for the whole world ({@link #GAP} apart, wobbling gently by
 *       region), from {@link #LOW} up to under the ground, so a level is the same level in every cell
 *       and two cells' passages meet.</li>
 *   <li><b>Cells</b> {@link #CELL} across, each one connected system: on every level a spanning tree of
 *       junctions about {@link #SPACING} apart, with loops, joined to the next cells through
 *       <b>gates</b> (points on the shared edge both cells work out alike), so the systems link into one
 *       network instead of fading in and out.</li>
 *   <li><b>Passages</b>: tubes, vadose canyons (more near the surface), wide low galleries, the odd
 *       short crawl; widths and heights vary along each one.</li>
 *   <li><b>Rooms</b> where passages meet: breakdown rooms (two or three overlapping blobs), column halls
 *       (wide and low), small side chambers.</li>
 *   <li><b>Shafts and ramps</b> join each level to the one below, two to four a cell.</li>
 *   <li><b>Entrances</b>: hillside mouths from the top level, one or two a cell where the ground falls
 *       away (never near water or under the sea).</li>
 * </ul>
 *
 * <p>Pieces are ordinary passage pieces ({@link Passages.Seg}), carved, flooded and drawn like the rest.
 * Cells are built on demand and kept in a bounded cache.
 */
public final class Solution {
    static final double CELL = 256;
    static final int GRID = 6;                 // junctions per cell side
    static final double SPACING = CELL / GRID; // ~43
    static final double GAP = 26;              // between levels
    static final double LOW = -52;             // the lowest level's height
    static final double COVER = 16;            // rock over a junction's middle
    static final int MAX_LEVELS = 14;          // in one column, from the top down
    static final double MAX_LINK = 96;
    static final int BIN = 32;
    /** Dev: {@code -Dgrandatlas.noSolution=true} leaves the solution caves out (for comparisons). */
    static final boolean OFF = Boolean.getBoolean("grandatlas.noSolution");

    private final AtlasCore core;
    private final long seed, levelSeed;
    private final ConcurrentHashMap<Long, Cell> cells = new ConcurrentHashMap<>();

    Solution(AtlasCore core, long seed) {
        this.core = core;
        this.seed = seed;
        this.levelSeed = GradientNoise.fork(seed, 1);
    }

    /** One cell's system, as built. */
    static final class Cell {
        final List<Passages.Seg> segs = new ArrayList<>();
        final HashMap<Long, List<Passages.Seg>> bins = new HashMap<>();
        double minX = Double.MAX_VALUE, maxX = -Double.MAX_VALUE, minZ = Double.MAX_VALUE, maxZ = -Double.MAX_VALUE;
        int nodes, levels;
        /** Under the sea (any of its junction points on the sea floor): its caves are drowned to the sea level. */
        boolean wet;
        /** The lowest level's junctions {x, y, z}, where core/Deep's shafts come up into this cell. */
        final List<double[]> low = new ArrayList<>();
    }

    /** Rock kept over a junction under the sea: more than the passages' own cover by water (26). */
    static final double SEA_COVER = 30;

    /** True where the solution caves own the underground: land or sea floor that no karst system holds. */
    public boolean covers(double x, double z) {
        return core.karst().near(x, z) == null && country(core.sampleBase(x, z)) != 0;
    }

    /** 1 land, 2 sea floor, 0 neither (a shore: too shallow for either). */
    static int country(ColumnSample c) {
        if (!c.isOcean()) return c.height() > AtlasCore.SEA_LEVEL - 2 ? 1 : 0;
        return c.height() < AtlasCore.SEA_LEVEL - 6 ? 2 : 0;
    }

    /** Height of level k at (x, z). */
    double levelY(int k, double x, double z) {
        return LOW + k * GAP + 9 * GradientNoise.noise2(levelSeed, x / 800.0, z / 800.0);
    }

    /** The level whose height is nearest y here (for the debug view). */
    public int levelAt(double x, double y, double z) {
        return (int) Math.round((y - LOW - 9 * GradientNoise.noise2(levelSeed, x / 800.0, z / 800.0)) / GAP);
    }

    private static long key(int i, int j) {
        return ((long) i << 32) ^ (j & 0xFFFFFFFFL);
    }

    private static int cellOf(double v) {
        return GradientNoise.floor(v / CELL);
    }

    /** Most pieces kept cached across all cells (about 33 MB): a cell count alone let them grow to
     * hundreds of MB once Distant Horizons generated far afield (the Shark pack, 2026-11-23). */
    private static final int PIECE_CAP = 150_000;
    private final java.util.concurrent.atomic.AtomicInteger pieces = new java.util.concurrent.atomic.AtomicInteger();

    Cell cell(int i, int j) {
        long k = key(i, j);
        Cell c = cells.get(k);
        if (c != null) return c;
        if (cells.size() > 384 || pieces.get() > PIECE_CAP) {
            cells.clear();
            pieces.set(0);
        }
        c = build(i, j);
        Cell prev = cells.putIfAbsent(k, c);
        if (prev == null) pieces.addAndGet(c.segs.size());
        return prev != null ? prev : c;
    }

    /** Every solution piece whose bounds meet the box, added to {@code out} once each. */
    void collect(double x0, double z0, double x1, double z1, List<Passages.Seg> out) {
        if (OFF) return;
        int ci0 = cellOf(x0 - 150), ci1 = cellOf(x1 + 150), cj0 = cellOf(z0 - 150), cj1 = cellOf(z1 + 150);
        Set<Passages.Seg> seen = null;
        for (int cj = cj0; cj <= cj1; cj++) {
            for (int ci = ci0; ci <= ci1; ci++) {
                Cell c = cell(ci, cj);
                if (c.maxX < x0 || c.minX > x1 || c.maxZ < z0 || c.minZ > z1) continue;
                for (int bj = GradientNoise.floor(z0 / BIN); bj <= GradientNoise.floor(z1 / BIN); bj++) {
                    for (int bi = GradientNoise.floor(x0 / BIN); bi <= GradientNoise.floor(x1 / BIN); bi++) {
                        List<Passages.Seg> list = c.bins.get(key(bi, bj));
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

    // ---- building

    private static final class Node {
        final double x, y, z;
        final boolean gate;
        int parent = -1, degree;
        long h;

        Node(double x, double y, double z, boolean gate, long h) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.gate = gate;
            this.h = h;
        }
    }

    /** The ground and whether a cave may sit under it, cached per point within one build. */
    private final class Ground {
        final HashMap<Long, double[]> memo = new HashMap<>();

        /** {ground height, 1 land / 2 sea floor / 0 no solution caves (shore, karst)}. */
        double[] at(double x, double z) {
            long k = key((int) Math.floor(x), (int) Math.floor(z));
            double[] v = memo.get(k);
            if (v == null) {
                ColumnSample c = core.sampleBase(x, z);
                int kind = core.karst().near(x, z) == null ? country(c) : 0;
                v = new double[] {c.height(), kind};
                memo.put(k, v);
            }
            return v;
        }

        /** Rock to keep over a cave's roof here. */
        double roofCover(double x, double z) {
            return at(x, z)[1] == 2 ? SEA_COVER - 2 : 8;
        }

        /** The highest level a junction at (x, z) may sit on, or Integer.MIN_VALUE. */
        int topLevel(double x, double z) {
            double[] g = at(x, z);
            if (g[1] == 0) return Integer.MIN_VALUE;
            double cover = g[1] == 2 ? SEA_COVER + 4 : COVER;
            int k = (int) Math.floor((g[0] - cover - LOW) / GAP) + 1;
            while (k >= 0 && levelY(k, x, z) > g[0] - cover) k--;
            return k;
        }
    }

    private Cell build(int ci, int cj) {
        Cell cell = new Cell();
        long h = GradientNoise.mix(seed ^ key(ci, cj) * 0x9E3779B97F4A7C15L);
        Karst.Rng rnd = new Karst.Rng(h);
        Ground ground = new Ground();
        double x0 = ci * CELL, z0 = cj * CELL;
        cell.wet = wet(ci, cj);

        // Which levels this cell has at all: from the lowest to the highest any junction can reach.
        int kTop = -1;
        for (int gj = 0; gj < GRID; gj++) {
            for (int gi = 0; gi < GRID; gi++) {
                kTop = Math.max(kTop, ground.topLevel(x0 + (gi + 0.5) * SPACING, z0 + (gj + 0.5) * SPACING));
            }
        }
        if (kTop < 0) return finish(cell);

        List<List<Node>> levels = new ArrayList<>();
        for (int k = 0; k <= kTop; k++) {
            List<Node> nodes = new ArrayList<>();
            // Gates on the four edges first (any of them can root the tree), then the junctions.
            gates(ground, ci, cj, k, nodes, cell.wet);
            for (int gj = 0; gj < GRID; gj++) {
                for (int gi = 0; gi < GRID; gi++) {
                    long nh = GradientNoise.hash(h, gi, k, gj);
                    if (unit(nh, 0) < 0.1) continue; // a gap: not a lattice
                    double x = x0 + (gi + 0.5 + 0.7 * (unit(nh, 1) - 0.5)) * SPACING;
                    double z = z0 + (gj + 0.5 + 0.7 * (unit(nh, 2) - 0.5)) * SPACING;
                    int top = ground.topLevel(x, z);
                    if (top < k || top - k >= MAX_LEVELS) continue;
                    nodes.add(new Node(x, levelY(k, x, z) + (unit(nh, 3) - 0.5) * 6, z, false, nh));
                }
            }
            levels.add(nodes);
        }

        List<Passages.Seg> out = cell.segs;
        for (int k = 0; k < levels.size(); k++) {
            List<Node> nodes = levels.get(k);
            if (nodes.size() < 2) continue;
            cell.levels++;
            cell.nodes += nodes.size();
            if (k == 0 && !cell.wet) for (Node n : nodes) if (!n.gate) cell.low.add(new double[] {n.x, n.y, n.z});
            tree(nodes, rnd);
            for (Node n : nodes) {
                if (n.parent >= 0) passage(out, n, nodes.get(n.parent), ground, rnd);
            }
            // Loops: a second way round, to the nearest junction it isn't already joined to.
            for (int a = 0; a < nodes.size(); a++) {
                Node n = nodes.get(a);
                if (unit(n.h, 5) >= 0.24 || n.gate) continue;
                int best = -1;
                double bd = 90;
                for (int b = 0; b < nodes.size(); b++) {
                    Node m = nodes.get(b);
                    if (b == a || m.parent == a || n.parent == b) continue;
                    double d = Math.hypot(m.x - n.x, m.z - n.z);
                    if (d < bd) {
                        bd = d;
                        best = b;
                    }
                }
                if (best >= 0) {
                    passage(out, n, nodes.get(best), ground, rnd);
                    n.degree++;
                    nodes.get(best).degree++;
                }
            }
            for (Node n : nodes) {
                if (n.gate) continue;
                room(out, n, ground, rnd);
                chimneys(out, n, ground);
            }
            for (Node n : nodes) {
                // Dead ends: a passage that wanders off and tapers out.
                if (n.gate || unit(n.h, 6) >= 0.18) continue;
                double ang = unit(n.h, 7) * 2 * Math.PI, len = 22 + 40 * unit(n.h, 8);
                double tx = n.x + Math.cos(ang) * len, tz = n.z + Math.sin(ang) * len;
                if (ground.topLevel(tx, tz) < k) continue;
                Karst.walk(out, n.x, n.y, n.z, tx, n.y + (unit(n.h, 9) - 0.5) * 6, tz, n.h ^ 0xDEADL,
                        Karst.Style.tube(2.4 + 1.6 * unit(n.h, 10), 0.75, 0.5).tapered().at(-1), null);
            }
        }

        // Shafts and ramps between levels: two to four a cell, from a junction to the nearest below.
        for (int k = 1; k < levels.size(); k++) {
            List<Node> up = levels.get(k), down = levels.get(k - 1);
            if (up.isEmpty() || down.isEmpty()) continue;
            int n = 2 + (int) (rnd.next() * 3);
            for (int t = 0; t < n; t++) {
                Node a = up.get((int) (rnd.next() * up.size()));
                Node b = null;
                double bd = 75;
                for (Node m : down) {
                    double d = Math.hypot(m.x - a.x, m.z - a.z);
                    if (d < bd) {
                        bd = d;
                        b = m;
                    }
                }
                if (b == null) continue;
                Karst.Style st = rnd.next() < 0.5 ? Karst.Style.canyon(3.0 + 1.5 * rnd.next(), 0.9, 0.5) : Karst.Style.tube(2.8 + 1.4 * rnd.next(), 0.9, 0.5);
                Karst.walk(out, a.x, a.y, a.z, b.x, b.y, b.z, rnd.nextLong(), st.at(-1), null);
            }
        }

        // Entrances: hillside mouths from the top level, one or two a cell.
        int mouths = rnd.next() < 0.75 ? 1 + (rnd.next() < 0.4 ? 1 : 0) : 0;
        for (int t = 0, tries = 0; t < mouths && tries < 8; tries++) {
            List<Node> top = null;
            for (int k = levels.size() - 1; k >= 0 && top == null; k--) if (levels.get(k).size() >= 2) top = levels.get(k);
            if (top == null) break;
            Node a = top.get((int) (rnd.next() * top.size()));
            if (a.gate) continue;
            if (entrance(out, a, ground, rnd)) t++;
        }
        blueHoles(out, levels, ground, rnd);
        return finish(cell);
    }

    /**
     * Blue holes: under open sea, now and then a top-level junction opens straight up to the sea floor
     * through a wide round shaft, the way into the drowned caves (they fill with the sea). Never near
     * a river's or lake's water, nor in shallows.
     */
    private void blueHoles(List<Passages.Seg> out, List<List<Node>> levels, Ground ground, Karst.Rng rnd) {
        if (rnd.next() >= 0.35) return;
        for (int k = levels.size() - 1; k >= 0; k--) {
            for (Node n : levels.get(k)) {
                if (n.gate || unit(n.h, 60) >= 0.5) continue;
                double[] g = ground.at(n.x, n.z);
                if (g[1] != 2 || g[0] > AtlasCore.SEA_LEVEL - 12 || ground.topLevel(n.x, n.z) != k) continue;
                double r = 4 + 5 * unit(n.h, 61);
                boolean clear = true;
                for (int t = 0; t < 8 && clear; t++) {
                    double a = t * Math.PI / 4, rr = r + 6;
                    ColumnSample c = core.column((int) Math.floor(n.x + Math.cos(a) * rr), (int) Math.floor(n.z + Math.sin(a) * rr));
                    clear = c.isOcean() && !c.nearWater() && Double.isNaN(c.water()) && c.height() < AtlasCore.SEA_LEVEL - 10;
                }
                ColumnSample c = core.column((int) Math.floor(n.x), (int) Math.floor(n.z));
                if (!clear || c.nearWater() || !Double.isNaN(c.water())) continue;
                Karst.walk(out, n.x, n.y, n.z, n.x + (unit(n.h, 62) - 0.5) * 4, c.height() + 1, n.z + (unit(n.h, 63) - 0.5) * 4, n.h ^ 0xB1E0L,
                        Karst.Style.shaft(r).open().wetOk().varied().at(-1), null);
                return; // one a cell at most
            }
        }
    }

    /** Gate points of level k on the cell's four edges, worked out from the edge alone so both cells agree. */
    private void gates(Ground ground, int ci, int cj, int k, List<Node> out, boolean wetHere) {
        // edges: west (ci, cj, vertical), east (ci + 1, cj, vertical), south (ci, cj, horizontal), north (ci, cj + 1, horizontal)
        int[][] edges = {{ci, cj, 0}, {ci + 1, cj, 0}, {ci, cj, 1}, {ci, cj + 1, 1}};
        int[][] beyond = {{ci - 1, cj}, {ci + 1, cj}, {ci, cj - 1}, {ci, cj + 1}};
        for (int ei = 0; ei < 4; ei++) {
            int[] e = edges[ei];
            // A drowned cell and a dry one don't join under the sea level (the water would stand as a wall).
            if (levelY(k, e[0] * CELL, e[1] * CELL) < AtlasCore.SEA_LEVEL + 6 && wet(beyond[ei][0], beyond[ei][1]) != wetHere) continue;
            long gh = GradientNoise.hash(seed ^ 0x6A7EL, e[0] * 2 + e[2], k, e[1]);
            if (unit(gh, 0) > 0.85) continue;
            double t = 0.2 + 0.6 * unit(gh, 1);
            double x = e[2] == 0 ? e[0] * CELL : (e[0] + t) * CELL;
            double z = e[2] == 0 ? (e[1] + t) * CELL : e[1] * CELL;
            int top = ground.topLevel(x, z);
            if (top < k || top - k >= MAX_LEVELS) continue;
            out.add(new Node(x, levelY(k, x, z), z, true, gh));
        }
    }

    /** A spanning forest by Prim's method, links no longer than {@link #MAX_LINK}, weights jittered. */
    private static void tree(List<Node> nodes, Karst.Rng rnd) {
        int n = nodes.size();
        boolean[] in = new boolean[n];
        double[] best = new double[n];
        int[] from = new int[n];
        for (int start = 0; start < n; start++) {
            if (in[start]) continue;
            java.util.Arrays.fill(best, Double.MAX_VALUE);
            for (int k = 0; k < n; k++) if (!in[k]) from[k] = -1;
            best[start] = 0;
            while (true) {
                int u = -1;
                for (int k = 0; k < n; k++) if (!in[k] && best[k] < Double.MAX_VALUE && (u < 0 || best[k] < best[u])) u = k;
                if (u < 0) break;
                in[u] = true;
                Node a = nodes.get(u);
                if (from[u] >= 0) {
                    a.parent = from[u];
                    a.degree++;
                    nodes.get(from[u]).degree++;
                }
                for (int k = 0; k < n; k++) {
                    if (in[k]) continue;
                    Node b = nodes.get(k);
                    double dd = Math.hypot(a.x - b.x, a.z - b.z);
                    if (dd > MAX_LINK) continue;
                    double w = dd * (0.8 + 0.5 * unit(b.h ^ a.h, 4));
                    if (w < best[k]) {
                        best[k] = w;
                        from[k] = u;
                    }
                }
            }
        }
    }

    /** A passage between two junctions of one level. */
    private void passage(List<Passages.Seg> out, Node a, Node b, Ground ground, Karst.Rng rnd) {
        double[] mid = ground.at((a.x + b.x) / 2, (a.z + b.z) / 2);
        double gMid = mid[0];
        boolean sea = mid[1] == 2;
        double y = (a.y + b.y) / 2;
        boolean shallow = gMid - y < 50;
        double len = Math.hypot(a.x - b.x, a.z - b.z);
        // Heavy-tailed widths (docs/CAVE_REDESIGN.md): mostly modest, a few wide trunks, some tight.
        double r = Rooms.lognormal(rnd.nextLong(), 0, 3.4, 0.4, 1.4, 8);
        if (y < 0) r *= 1.1;
        double roll = rnd.next();
        Karst.Style st;
        if (roll < 0.09 && len < 60) {
            st = Karst.Style.tube(1.45 + 0.2 * rnd.next(), 0.8, 0.45); // a crawl at crouch height
        } else if (roll < 0.2) {
            // A wide low gallery under a flat bedding-plane roof.
            st = Karst.Style.tube(r * 1.6, 0.5 + 0.12 * rnd.next(), 0.5).bedding(0.55 + 0.2 * rnd.next());
        } else if (roll < (sea ? 0.28 : shallow ? 0.55 : 0.35)) {
            st = Karst.Style.canyon(r * 0.9, 0.85 + 0.2 * rnd.next(), 0.5); // vadose: a keyhole canyon
        } else {
            st = Karst.Style.tube(r, 0.6 + 0.3 * rnd.next(), 0.42 + 0.2 * rnd.next());
            if (rnd.next() < 0.4) st.bedding(0.6 + 0.25 * rnd.next()); // a roof along the bedding
        }
        st.varied();
        // Keep the roof under the rock: thin cover narrows the passage rather than breaking the ground.
        double room = gMid - ground.roofCover((a.x + b.x) / 2, (a.z + b.z) / 2) - y;
        double up = st.r * st.vr;
        if (up > room && room > 1.5) st.r *= room / up;
        Karst.walk(out, a.x, a.y, a.z, b.x, b.y, b.z, rnd.nextLong(), st.at(-1), null);
    }

    /** Rooms the solution caves draw from where three or more passages meet, and elsewhere. */
    private static final int[] JUNCTION = {Rooms.BREAKDOWN, Rooms.CROSSROADS, Rooms.RAMIFORM, Rooms.PILLARS, Rooms.DOME};
    private static final int[] ALONG = {Rooms.KEYHOLE, Rooms.GALLERY, Rooms.CANYON, Rooms.RAMIFORM, Rooms.TERRACES, Rooms.BOWL,
            Rooms.FISSURES, Rooms.FINS, Rooms.AVEN, Rooms.BRIDGE};

    /** A room at a junction (a library room, core/Rooms): likelier where three or more passages meet, bigger there. */
    private void room(List<Passages.Seg> out, Node n, Ground ground, Karst.Rng rnd) {
        double chance = n.degree >= 3 ? 0.75 : n.degree == 2 ? 0.4 : 0.4;
        if (unit(n.h, 11) >= chance) return;
        double g = ground.at(n.x, n.z)[0];
        int[] arch = n.degree >= 3 ? JUNCTION : ALONG;
        int a = arch[(int) (unit(n.h, 12) * arch.length)];
        double w = Rooms.lognormal(n.h, 13, (n.degree >= 3 ? 24 : 18) * (n.y < 0 ? 1.15 : 1), 0.45, 9, 40);
        double floorY = n.y - 1.5;
        double maxH = g - ground.roofCover(n.x, n.z) - 1 - floorY;
        if (maxH < 5) return;
        out.add(Passages.Seg.of(Rooms.make(n.h ^ 0x501DL, n.x, floorY, n.z, w, maxH, a)));
    }

    /** Avens (chimneys rising from a junction's roof) and pits (shafts dropping from its floor). */
    private void chimneys(List<Passages.Seg> out, Node n, Ground ground) {
        double g = ground.at(n.x, n.z)[0];
        if (unit(n.h, 30) < 0.28) {
            double top = Math.min(n.y + 9 + 16 * unit(n.h, 32), g - ground.roofCover(n.x, n.z) - 1);
            if (top > n.y + 6) {
                double jx = (unit(n.h, 33) - 0.5) * 6, jz = (unit(n.h, 34) - 0.5) * 6;
                Karst.walk(out, n.x, n.y + 2, n.z, n.x + jx, top, n.z + jz, n.h ^ 0xA7E5L,
                        Karst.Style.shaft(1.8 + 1.4 * unit(n.h, 35)).tapered().at(-1), null);
            }
        }
        if (unit(n.h, 36) < 0.12) {
            double depth = 6 + 9 * unit(n.h, 37);
            Karst.walk(out, n.x + 2, n.y - 1, n.z + 2, n.x + 3, n.y - depth, n.z + 1, n.h ^ 0x9175L,
                    Karst.Style.shaft(1.6 + 1.0 * unit(n.h, 38)).tapered().at(-1), null);
        }
    }

    /** A passage from a top-level junction rising to a hillside mouth. False if there's no hillside within reach. */
    private boolean entrance(List<Passages.Seg> out, Node from, Ground ground, Karst.Rng rnd) {
        double ang = rnd.next() * 2 * Math.PI, ux = Math.cos(ang), uz = Math.sin(ang);
        for (double d = 16; d <= 110; d += 8) {
            double x = from.x + ux * d, z = from.z + uz * d, y = from.y + d * 0.3;
            ColumnSample c = core.column((int) Math.floor(x), (int) Math.floor(z));
            if (y < c.height() - 2) continue;
            if (c.isOcean() || c.nearWater() || c.height() < AtlasCore.SEA_LEVEL + 4) return false;
            ColumnSample f = core.column((int) Math.floor(x + ux * 10), (int) Math.floor(z + uz * 10));
            if (c.height() - f.height() < 3) return false; // flat ground: it would only be a hole
            Karst.walk(out, from.x, from.y, from.z, x, c.height() - 1, z, rnd.nextLong(),
                    Karst.Style.tube(3.0 + 1.6 * rnd.next(), 0.85, 0.5).open().at(-1), null);
            return true;
        }
        return false;
    }

    private final ConcurrentHashMap<Long, Boolean> wetCells = new ConcurrentHashMap<>();

    /** Whether cell (ci, cj) lies under the sea: any of its junction points on the sea floor. Worked out from the cell alone. */
    boolean wet(int ci, int cj) {
        return wetCells.computeIfAbsent(key(ci, cj), k -> {
            for (int gj = 0; gj < GRID; gj++) {
                for (int gi = 0; gi < GRID; gi++) {
                    if (country(core.sampleBase((ci + (gi + 0.5) / GRID) * CELL, (cj + (gj + 0.5) / GRID) * CELL)) == 2) return true;
                }
            }
            return false;
        });
    }

    private static Cell finish(Cell cell) {
        for (Passages.Seg s : cell.segs) {
            s.province = Passages.SOLUTION;
            if (cell.wet) s.water = AtlasCore.SEA_LEVEL - 0.5; // drowned: the core fills them to the sea level
            cell.minX = Math.min(cell.minX, s.minX);
            cell.maxX = Math.max(cell.maxX, s.maxX);
            cell.minZ = Math.min(cell.minZ, s.minZ);
            cell.maxZ = Math.max(cell.maxZ, s.maxZ);
            for (int bj = GradientNoise.floor(s.minZ / BIN); bj <= GradientNoise.floor(s.maxZ / BIN); bj++) {
                for (int bi = GradientNoise.floor(s.minX / BIN); bi <= GradientNoise.floor(s.maxX / BIN); bi++) {
                    cell.bins.computeIfAbsent(key(bi, bj), k -> new ArrayList<>()).add(s);
                }
            }
        }
        return cell;
    }

    private static double unit(long hash, int k) {
        return (GradientNoise.mix(hash + k * 0x9E3779B97F4A7C15L) >>> 11) * 0x1.0p-53;
    }
}
