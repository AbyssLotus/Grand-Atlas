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
 * The deep and the abyss (cave rework C1, docs/CAVE_RESEARCH.md section 4): one connected network
 * under the whole world from y -95 down, built like {@link Solution} (cells, world-fixed levels,
 * gates on the cell edges) at the sizes the vertical rule asks for.
 *
 * <ul>
 *   <li><b>The deep</b> (four levels, y -95 to -230): heightened real. Galleries 12-20 across, tall
 *       canyons, giant halls with columns, breakdown rooms.</li>
 *   <li><b>The abyss</b> (four levels, y -285 to -450): Rule of Cool, more open than closed. Grand
 *       passages 20-30 across and halls 60-100 across, closer together.</li>
 *   <li><b>Down from above</b>: shafts and ramps from the solution caves' lowest level (y -52) into the
 *       deep's top level, so the caves under the fields lead all the way down.</li>
 *   <li><b>Into the mega-caverns</b>: from each cell, a grand passage from the junction nearest a
 *       mega-cavern (core/MegaCaverns) into its open middle, so travel goes cavern to cavern.</li>
 * </ul>
 */
public final class Deep {
    static final double CELL = 384;
    /** The levels' heights (before a regional wobble of 12). The first four are the deep, the rest the abyss. */
    static final double[] LEVEL_Y = {-95, -140, -185, -230, -285, -340, -395, -450};
    static final double COVER = 45;
    static final int BIN = 32;
    /** Dev: {@code -Dgrandatlas.noDeep=true} leaves the deep network out (for comparisons). */
    static final boolean OFF = Boolean.getBoolean("grandatlas.noDeep");

    private final AtlasCore core;
    private final long seed, levelSeed;
    private final ConcurrentHashMap<Long, Cell> cells = new ConcurrentHashMap<>();

    Deep(AtlasCore core, long seed) {
        this.core = core;
        this.seed = seed;
        this.levelSeed = GradientNoise.fork(seed, 1);
    }

    static final class Cell {
        final List<Passages.Seg> segs = new ArrayList<>();
        final HashMap<Long, List<Passages.Seg>> bins = new HashMap<>();
        double minX = Double.MAX_VALUE, maxX = -Double.MAX_VALUE, minZ = Double.MAX_VALUE, maxZ = -Double.MAX_VALUE;
    }

    static boolean abyss(int k) {
        return k >= 4;
    }

    double levelY(int k, double x, double z) {
        return LEVEL_Y[k] + 12 * GradientNoise.noise2(levelSeed, x / 900.0, z / 900.0);
    }

    /** The level whose height is nearest y here, or -1 above the deep (debug). */
    public int levelAt(double x, double y, double z) {
        int best = -1;
        double bd = 30;
        for (int k = 0; k < LEVEL_Y.length; k++) {
            double d = Math.abs(levelY(k, x, z) - y);
            if (d < bd) {
                bd = d;
                best = k;
            }
        }
        return best;
    }

    private static long key(int i, int j) {
        return ((long) i << 32) ^ (j & 0xFFFFFFFFL);
    }

    private static int cellOf(double v) {
        return GradientNoise.floor(v / CELL);
    }

    Cell cell(int i, int j) {
        long k = key(i, j);
        Cell c = cells.get(k);
        if (c != null) return c;
        if (cells.size() > 256) cells.clear();
        c = build(i, j);
        Cell prev = cells.putIfAbsent(k, c);
        return prev != null ? prev : c;
    }

    /** Every deep piece whose bounds meet the box, added once each. */
    void collect(double x0, double z0, double x1, double z1, List<Passages.Seg> out) {
        if (OFF) return;
        int ci0 = cellOf(x0 - 260), ci1 = cellOf(x1 + 260), cj0 = cellOf(z0 - 260), cj1 = cellOf(z1 + 260);
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
        final long h;
        Moods.Mood mood;
        int room = -1;      // the room planned here: HALL, BREAKDOWN, DOME, LOW, or -1
        boolean squeeze;    // a tight passage leads into its room

        Node(double x, double y, double z, boolean gate, long h) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.gate = gate;
            this.h = h;
        }
    }

    private double ground(double x, double z) {
        return core.sampleBase(x, z).height();
    }

    private boolean fits(int k, double x, double y, double z) {
        return y < ground(x, z) - COVER;
    }

    private Cell build(int ci, int cj) {
        Cell cell = new Cell();
        long h = GradientNoise.mix(seed ^ key(ci, cj) * 0x9E3779B97F4A7C15L);
        Karst.Rng rnd = new Karst.Rng(h);
        double x0 = ci * CELL, z0 = cj * CELL;
        List<List<Node>> levels = new ArrayList<>();
        List<Passages.Seg> out = cell.segs;

        for (int k = 0; k < LEVEL_Y.length; k++) {
            int grid = 8; // dense rather than big: more to stumble on (Rule of Cool)
            double sp = CELL / grid;
            List<Node> nodes = new ArrayList<>();
            gates(ci, cj, k, nodes);
            for (int gj = 0; gj < grid; gj++) {
                for (int gi = 0; gi < grid; gi++) {
                    long nh = GradientNoise.hash(h, gi, k, gj);
                    if (unit(nh, 0) < 0.1) continue;
                    double x = x0 + (gi + 0.5 + 0.7 * (unit(nh, 1) - 0.5)) * sp;
                    double z = z0 + (gj + 0.5 + 0.7 * (unit(nh, 2) - 0.5)) * sp;
                    double y = levelY(k, x, z) + (unit(nh, 3) - 0.5) * 10;
                    if (!fits(k, x, y, z)) continue;
                    nodes.add(new Node(x, y, z, false, nh));
                }
            }
            levels.add(nodes);
            if (nodes.size() < 2) continue;
            for (Node n : nodes) n.mood = core.moods().at(n.x, n.y, n.z);
            double maxLink = sp * 2.3;
            tree(nodes, maxLink);
            // Loops: how many depends on the mood (clever: many; fear and horror: few), more in the abyss.
            List<int[]> loops = new ArrayList<>();
            for (int a = 0; a < nodes.size(); a++) {
                Node n = nodes.get(a);
                if (n.gate || unit(n.h, 5) >= loopChance(n.mood) + (abyss(k) ? 0.1 : 0)) continue;
                int best = -1;
                double bd = maxLink;
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
                    loops.add(new int[] {a, best});
                    n.degree++;
                    nodes.get(best).degree++;
                }
            }
            for (Node n : nodes) if (!n.gate) plan(n);
            for (Node n : nodes) {
                if (n.parent < 0) continue;
                Node p = nodes.get(n.parent);
                // A tight squeeze into a room, sometimes: the junction's own link toward the root.
                passage(out, k, n, p, rnd, n.squeeze);
            }
            for (int[] l : loops) passage(out, k, nodes.get(l[0]), nodes.get(l[1]), rnd, false);
            for (Node n : nodes) {
                if (n.gate) continue;
                room(out, k, n);
                ends(out, k, n);
            }
        }

        // Between levels: two to four shafts or ramps a cell.
        for (int k = 1; k < levels.size(); k++) {
            List<Node> up = levels.get(k - 1), down = levels.get(k);
            if (up.isEmpty() || down.isEmpty()) continue;
            // Awe drops in shafts, clever stacks its levels: more links between them.
            Moods.Mood cm = core.moods().at(x0 + CELL / 2, LEVEL_Y[k], z0 + CELL / 2);
            int n = 2 + (int) (rnd.next() * 3) + (cm == Moods.Mood.AWE || cm == Moods.Mood.CLEVER ? 2 : 0);
            for (int t = 0; t < n; t++) {
                Node a = up.get((int) (rnd.next() * up.size()));
                Node b = nearest(down, a.x, a.z, 110);
                if (b == null) continue;
                double r = abyss(k) ? 3.5 + 2.5 * rnd.next() : 2.8 + 2 * rnd.next();
                Karst.Style st = rnd.next() < 0.5 ? Karst.Style.canyon(r, 1.0, 0.5) : Karst.Style.tube(r, 0.9, 0.5);
                Karst.walk(out, a.x, a.y, a.z, b.x, b.y, b.z, rnd.nextLong(), st.varied().at(-1), null);
            }
        }

        // Down from the solution caves: from their lowest level into the deep's top.
        if (!levels.isEmpty() && !levels.get(0).isEmpty()) {
            for (Node a : levels.get(0)) {
                if (a.gate || unit(a.h, 40) >= 0.3) continue;
                double[] up = solutionLow(a.x, a.z, 80);
                if (up == null) continue;
                Karst.Style st = unit(a.h, 41) < 0.5 ? Karst.Style.canyon(3.2 + 2 * unit(a.h, 42), 1.0, 0.5) : Karst.Style.tube(3 + 2 * unit(a.h, 42), 0.9, 0.5);
                Karst.walk(out, up[0], up[1], up[2], a.x, a.y, a.z, a.h ^ 0xD0E1L, st.varied().at(-1), null);
            }
        }

        // Into the nearest mega-cavern: a grand passage from the junction nearest it.
        double[] mc = core.megaCaverns().nearest(x0 + CELL / 2, z0 + CELL / 2);
        if (mc != null && Math.hypot(mc[0] - (x0 + CELL / 2), mc[1] - (z0 + CELL / 2)) < CELL * 0.5 + mc[2] * 0.5 + 200) {
            int k = 0;
            for (int t = 1; t < LEVEL_Y.length; t++) if (Math.abs(LEVEL_Y[t] - mc[3]) < Math.abs(LEVEL_Y[k] - mc[3])) k = t;
            if (k < levels.size()) {
                Node a = nearest(levels.get(k), mc[0], mc[1], 1e9);
                if (a != null && !a.gate) {
                    double d = Math.hypot(mc[0] - a.x, mc[1] - a.z);
                    double reach = Math.max(0, d - mc[2] * 0.25); // to within a quarter of its width of the middle
                    if (reach > 20 && reach < 700) {
                        double tx = a.x + (mc[0] - a.x) / d * reach, tz = a.z + (mc[1] - a.z) / d * reach;
                        double r = abyss(k) ? 10 + 5 * unit(a.h, 50) : 7 + 4 * unit(a.h, 50);
                        Karst.walk(out, a.x, a.y, a.z, tx, mc[3], tz, a.h ^ 0x6CA7L,
                                Karst.Style.tube(r, 0.75, 0.5).varied().at(-1), null);
                    }
                }
            }
        }
        return finish(cell);
    }

    /** The nearest solution-cave junction of the lowest level to (x, z) within r, or null. */
    private double[] solutionLow(double x, double z, double r) {
        double[] best = null;
        double bd = r;
        for (int j = GradientNoise.floor((z - r) / Solution.CELL); j <= GradientNoise.floor((z + r) / Solution.CELL); j++) {
            for (int i = GradientNoise.floor((x - r) / Solution.CELL); i <= GradientNoise.floor((x + r) / Solution.CELL); i++) {
                for (double[] n : core.solution().cell(i, j).low) {
                    double d = Math.hypot(n[0] - x, n[2] - z);
                    if (d < bd) {
                        bd = d;
                        best = n;
                    }
                }
            }
        }
        return best;
    }

    private void gates(int ci, int cj, int k, List<Node> out) {
        int[][] edges = {{ci, cj, 0}, {ci + 1, cj, 0}, {ci, cj, 1}, {ci, cj + 1, 1}};
        for (int[] e : edges) {
            long gh = GradientNoise.hash(seed ^ 0x6A7EL, e[0] * 2 + e[2], k, e[1]);
            if (unit(gh, 0) > 0.9) continue;
            double t = 0.2 + 0.6 * unit(gh, 1);
            double x = e[2] == 0 ? e[0] * CELL : (e[0] + t) * CELL;
            double z = e[2] == 0 ? (e[1] + t) * CELL : e[1] * CELL;
            double y = levelY(k, x, z);
            if (!fits(k, x, y, z)) continue;
            out.add(new Node(x, y, z, true, gh));
        }
    }

    private static Node nearest(List<Node> nodes, double x, double z, double r) {
        Node best = null;
        double bd = r;
        for (Node m : nodes) {
            double d = Math.hypot(m.x - x, m.z - z);
            if (d < bd) {
                bd = d;
                best = m;
            }
        }
        return best;
    }

    private static void tree(List<Node> nodes, double maxLink) {
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
                    if (dd > maxLink) continue;
                    double w = dd * (0.8 + 0.5 * unit(b.h ^ a.h, 4));
                    if (w < best[k]) {
                        best[k] = w;
                        from[k] = u;
                    }
                }
            }
        }
    }

    static final int HALL = 0, BREAKDOWN = 1, DOME = 2, LOW = 3;

    private static double loopChance(Moods.Mood m) {
        return switch (m) {
            case CLEVER -> 0.55;
            case COZY -> 0.28;
            case AWE -> 0.22;
            case FEAR -> 0.1;
            case HORROR -> 0.08;
        };
    }

    /** Size of the passages by mood: fear and horror tight, awe a little wider. */
    private static double passageScale(Moods.Mood m) {
        return switch (m) {
            case AWE -> 1.1;
            case COZY -> 0.9;
            case CLEVER -> 0.85;
            case FEAR -> 0.65;
            case HORROR -> 0.6;
        };
    }

    /** Which room (if any) a junction gets, by its mood; and whether a squeeze leads into it. */
    private void plan(Node n) {
        double chance = switch (n.mood) {
            case AWE -> 0.5;
            case COZY -> 0.45;
            case CLEVER -> 0.3;
            case FEAR -> 0.12;
            case HORROR -> 0.15;
        };
        if (n.degree >= 3) chance += 0.15;
        if (unit(n.h, 11) >= chance) return;
        double r = unit(n.h, 12);
        n.room = switch (n.mood) {
            case AWE -> r < 0.55 ? DOME : HALL;
            case COZY -> r < 0.7 ? LOW : HALL;
            case CLEVER -> r < 0.6 ? HALL : BREAKDOWN;
            case FEAR, HORROR -> BREAKDOWN;
        };
        // Contrast: a tight squeeze before a big room, now and then (not every time).
        n.squeeze = n.room != BREAKDOWN && unit(n.h, 13) < 0.35;
    }

    /** A passage between two junctions of level k, shaped by the mood where it starts. */
    private void passage(List<Passages.Seg> out, int k, Node a, Node b, Karst.Rng rnd, boolean tight) {
        boolean ab = abyss(k);
        Moods.Mood m = a.mood;
        Karst.Style st;
        if (tight && Math.hypot(a.x - b.x, a.z - b.z) < 80) {
            st = Karst.Style.tube(1.8 + 0.5 * rnd.next(), 0.75, 0.45); // the squeeze
        } else {
            double r = (ab ? 5.5 + 3 * rnd.next() : 4.6 + 2.4 * rnd.next()) * passageScale(m);
            double roll = rnd.next();
            double crawl = m == Moods.Mood.HORROR ? 0.25 : m == Moods.Mood.FEAR ? 0.2 : 0.04;
            double gallery = m == Moods.Mood.COZY ? 0.45 : 0.12;
            double canyon = m == Moods.Mood.AWE ? 0.45 : m == Moods.Mood.FEAR ? 0.4 : m == Moods.Mood.HORROR ? 0.3 : 0.25;
            if (roll < crawl) {
                st = Karst.Style.tube(1.8 + 0.6 * rnd.next(), 0.65, 0.4);
            } else if (roll < crawl + gallery) {
                st = Karst.Style.tube(r * 1.4, 0.45 + 0.1 * rnd.next(), 0.5).bedding(0.6 + 0.2 * rnd.next()); // wide and low
            } else if (roll < crawl + gallery + canyon) {
                double vr = m == Moods.Mood.AWE ? 1.2 + 0.4 * rnd.next() : 0.95 + 0.25 * rnd.next();
                st = Karst.Style.canyon(r * 0.8, vr, 0.5);
            } else {
                st = Karst.Style.tube(r, (m == Moods.Mood.FEAR ? 0.55 : 0.65) + 0.3 * rnd.next(), 0.42 + 0.2 * rnd.next());
                if (rnd.next() < 0.35) st.bedding(0.6 + 0.25 * rnd.next());
            }
        }
        Karst.walk(out, a.x, a.y, a.z, b.x, b.y, b.z, rnd.nextLong(), st.varied().at(-1), null);
    }

    /** The room planned at a junction. Sizes are for feeling, not bulk (the user: Rule of Cool is not Rule of Big). */
    private void room(List<Passages.Seg> out, int k, Node n) {
        if (n.room < 0) return;
        double scale = abyss(k) ? 1.3 : 1.0;
        double r, vr, roof = 0, pillars = 0;
        int blobs = 1;
        switch (n.room) {
            case HALL -> { // on columns
                r = (10 + 8 * unit(n.h, 14)) * scale;
                vr = 0.4 + 0.15 * unit(n.h, 15);
                roof = 0.8;
                pillars = 8 + 5 * unit(n.h, 16);
            }
            case DOME -> { // tall and round: awe looks up
                r = (6 + 4 * unit(n.h, 14)) * scale;
                vr = 1.0 + 0.4 * unit(n.h, 15);
            }
            case LOW -> { // low and wide under a flat roof: shelter
                r = (8 + 6 * unit(n.h, 14)) * scale;
                vr = 0.33 + 0.08 * unit(n.h, 15);
                roof = 0.7;
            }
            default -> { // breakdown: overlapping blobs under a fallen-flat roof
                r = (7 + 5 * unit(n.h, 14)) * scale;
                vr = 0.55 + 0.25 * unit(n.h, 15);
                roof = 0.5 + 0.2 * unit(n.h, 17);
                blobs = 2 + (int) (unit(n.h, 18) * 2);
            }
        }
        double room = ground(n.x, n.z) - 30 - n.y;
        if (r * vr > room) r = room / vr;
        if (r < 4) return;
        for (int b = 0; b < blobs; b++) {
            double rb = b == 0 ? r : r * (0.45 + 0.35 * unit(n.h, 20 + b));
            double ang = unit(n.h, 24 + b) * 2 * Math.PI, off = b == 0 ? 0 : r * (0.5 + 0.4 * unit(n.h, 28 + b));
            Passages.Seg s = new Passages.Seg();
            s.ax = s.bx = n.x + Math.cos(ang) * off;
            s.az = s.bz = n.z + Math.sin(ang) * off;
            s.ay = s.by = n.y + rb * vr * 0.3;
            s.ra = s.rb = rb;
            s.vr = vr;
            s.roof = roof;
            s.pillars = b == 0 ? pillars : 0;
            s.seed = n.h ^ b;
            s.ribs = 0.3; // a flat floor across most of the room (no bowl of terraces)
            s.kind = Passages.CHAMBER;
            s.bounds();
            out.add(s);
        }
    }

    /** Dead ends, pits and avens, by mood: horror and fear have the dead ends and pits, awe the avens. */
    private void ends(List<Passages.Seg> out, int k, Node n) {
        Moods.Mood m = n.mood;
        double dead = m == Moods.Mood.HORROR ? 0.5 : m == Moods.Mood.FEAR ? 0.35 : m == Moods.Mood.COZY ? 0.1 : 0.18;
        if (unit(n.h, 6) < dead) {
            double ang = unit(n.h, 7) * 2 * Math.PI, len = 20 + 45 * unit(n.h, 8);
            double tx = n.x + Math.cos(ang) * len, tz = n.z + Math.sin(ang) * len;
            double ty = n.y + (unit(n.h, 9) - 0.5) * 8;
            if (fits(k, tx, ty, tz)) {
                Karst.walk(out, n.x, n.y, n.z, tx, ty, tz, n.h ^ 0xDEADL,
                        Karst.Style.tube((2.2 + 1.4 * unit(n.h, 10)) * passageScale(m), 0.7, 0.5).tapered().varied().at(-1), null);
            }
        }
        double pit = m == Moods.Mood.HORROR ? 0.3 : m == Moods.Mood.FEAR ? 0.15 : m == Moods.Mood.COZY ? 0 : 0.06;
        if (unit(n.h, 36) < pit) {
            double depth = 8 + 14 * unit(n.h, 37);
            Karst.walk(out, n.x + 2, n.y - 1, n.z + 2, n.x + 3, n.y - depth, n.z + 1, n.h ^ 0x9175L,
                    Karst.Style.shaft(1.6 + 1.0 * unit(n.h, 38)).tapered().at(-1), null);
        }
        double aven = m == Moods.Mood.AWE ? 0.4 : m == Moods.Mood.CLEVER ? 0.15 : 0.08;
        if (unit(n.h, 30) < aven) {
            double top = Math.min(n.y + 12 + 22 * unit(n.h, 32), ground(n.x, n.z) - COVER);
            if (top > n.y + 8) {
                Karst.walk(out, n.x, n.y + 2, n.z, n.x + (unit(n.h, 33) - 0.5) * 6, top, n.z + (unit(n.h, 34) - 0.5) * 6, n.h ^ 0xA7E5L,
                        Karst.Style.shaft(1.8 + 1.4 * unit(n.h, 35)).tapered().at(-1), null);
            }
        }
    }

    private static Cell finish(Cell cell) {
        for (Passages.Seg s : cell.segs) {
            s.province = Passages.DEEP;
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
