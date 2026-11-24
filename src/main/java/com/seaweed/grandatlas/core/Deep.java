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
        /** Falls down tall shafts (R5): {x, y of the top junction, z, 1 lava / 0 water, shaft radius}. */
        final List<double[]> falls = new ArrayList<>();
        /** Spiral galleries round a void (R7): {x, top y, z, bottom y, radius}. */
        final List<double[]> spirals = new ArrayList<>();
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

    /** Most pieces kept cached across all cells (about 33 MB): a cell count alone let them grow to
     * hundreds of MB once Distant Horizons generated far afield (the Shark pack, 2026-11-23). */
    private static final int PIECE_CAP = 150_000;
    private final java.util.concurrent.atomic.AtomicInteger pieces = new java.util.concurrent.atomic.AtomicInteger();

    Cell cell(int i, int j) {
        long k = key(i, j);
        Cell c = cells.get(k);
        if (c != null) return c;
        if (cells.size() > 256 || pieces.get() > PIECE_CAP) {
            cells.clear();
            pieces.set(0);
        }
        c = build(i, j);
        Cell prev = cells.putIfAbsent(k, c);
        if (prev == null) pieces.addAndGet(c.segs.size());
        return prev != null ? prev : c;
    }

    /** The falls (R5) whose shaft tops lie in the box: {x, top y, z, lava ? 1 : 0, radius}. */
    public void falls(double x0, double z0, double x1, double z1, List<double[]> out) {
        if (OFF) return;
        for (int cj = cellOf(z0); cj <= cellOf(z1); cj++) {
            for (int ci = cellOf(x0); ci <= cellOf(x1); ci++) {
                for (double[] f : cell(ci, cj).falls) {
                    if (f[0] >= x0 && f[0] < x1 && f[2] >= z0 && f[2] < z1) out.add(f);
                }
            }
        }
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
        int room = -1;      // the room planned here: an archetype of core/Rooms, or -1
        double roomW;       // its width (blocks)
        boolean squeeze;    // a tight passage leads into its room
        int desc;           // junctions upstream of it in the level's tree (passages grow downstream)

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
            // Flow: each junction counts those that drain through it, so passages grow toward the trunk.
            for (int a = 0; a < nodes.size(); a++) {
                for (int q = nodes.get(a).parent, guard = 0; q >= 0 && guard < nodes.size(); q = nodes.get(q).parent, guard++) nodes.get(q).desc++;
            }
            for (Node n : nodes) if (!n.gate) plan(n, k);
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

        // R5, vertical drama: falls down shafts two or three levels tall, into a plunge pool (water) or
        // onto a floor of magma (lava). Likelier in awe, wider there; lava mostly in the abyss.
        for (int t = 0; t < 2; t++) {
            if (levels.size() < 3) break;
            int k0 = (int) (rnd.next() * (levels.size() - 2));
            int k1 = Math.min(levels.size() - 1, k0 + 2 + (rnd.next() < 0.4 ? 1 : 0));
            List<Node> topL = levels.get(k0), botL = levels.get(k1);
            if (topL.isEmpty() || botL.isEmpty()) continue;
            Node a = topL.get((int) (rnd.next() * topL.size()));
            if (a.gate || a.mood == null) continue;
            double chance = switch (a.mood) {
                case AWE -> 0.9;
                case COZY -> 0.5;
                case CLEVER, FEAR -> 0.35;
                case HORROR -> 0.3;
            };
            if (rnd.next() >= chance) continue;
            Node b = nearest(botL, a.x, a.z, 80);
            if (b == null) continue;
            boolean lava = abyss(k1) ? rnd.next() < 0.75 && a.mood != Moods.Mood.COZY : a.mood == Moods.Mood.FEAR && rnd.next() < 0.4;
            // The regional water table (core/Waters) floods every cave under it: no fall from under it
            // (the shaft is a drowned sump there, the deep check found), and where it lies between the
            // top and the foot the flooded part is the plunge pool. Lava doesn't fall into it.
            double table = core.waters().table((int) Math.floor(a.x), (int) Math.floor(a.z));
            boolean flooded = !Double.isNaN(table) && table > b.y;
            if (!Double.isNaN(table) && table > a.y - 14) continue;
            if (flooded && lava) continue;
            double r = (a.mood == Moods.Mood.AWE ? 4.5 + 2.5 * rnd.next() : 3 + 1.5 * rnd.next()) * (abyss(k1) ? 1.2 : 1);
            double fx = a.x + (rnd.next() - 0.5) * 3, fz = a.z + (rnd.next() - 0.5) * 3, foot = b.y;
            Karst.walk(out, a.x, a.y + 2, a.z, fx, foot + r, fz, rnd.nextLong(), Karst.Style.shaft(r).at(-1), null); // (ends a radius up: no pocket under the pool)
            // The pool at the foot: a flat-floored basin, and a bowl in it, the water two above the floor.
            double pr = r + 3 + 3 * rnd.next();
            for (int bowl = 0; bowl < 2; bowl++) {
                Passages.Seg s = new Passages.Seg();
                double rb = bowl == 0 ? pr : pr * 0.6, vr = bowl == 0 ? 0.55 : 0.5;
                s.ax = s.bx = fx;
                s.az = s.bz = fz;
                s.ay = s.by = (bowl == 0 ? foot : foot - 2) + rb * vr * 0.3;
                s.ra = s.rb = rb;
                s.vr = vr;
                s.ribs = 0.3;
                s.kind = Passages.CHAMBER;
                if (!lava && !flooded) s.water = foot + 2.2;
                s.bounds();
                out.add(s);
            }
            // Out of the basin to the level's junction, leaving above the water so the pool holds.
            Karst.walk(out, fx, foot + 4, fz, b.x, b.y, b.z, rnd.nextLong(), Karst.Style.tube(2.6 + rnd.next(), 0.8, 0.5).varied().at(-1), null);
            cell.falls.add(new double[] {a.x, a.y, a.z, lava ? 1 : 0, r});
        }

        // R7, the odd impossible passage: now and then a gallery spiralling down round a void, two levels
        // tall, likelier where the region's mood is awe or clever. Entered at the top from a junction,
        // left at the bottom toward the lower level's nearest junction.
        spiral(cell, levels, rnd, x0, z0);

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

    /** Dev: {@code -Dgrandatlas.spiralAlways=true} puts a spiral in every cell (to look at one). */
    static final boolean SPIRAL_ALWAYS = Boolean.getBoolean("grandatlas.spiralAlways");

    private void spiral(Cell cell, List<List<Node>> levels, Karst.Rng rnd, double x0, double z0) {
        if (levels.size() < 3) return;
        Moods.Mood m = core.moods().at(x0 + CELL / 2, LEVEL_Y[2], z0 + CELL / 2);
        double chance = 0.12 + (m == Moods.Mood.AWE || m == Moods.Mood.CLEVER ? 0.25 : 0);
        if (!SPIRAL_ALWAYS && rnd.next() >= chance) return;
        int k0 = (int) (rnd.next() * (levels.size() - 2)), k1 = k0 + 2;
        List<Node> topL = levels.get(k0), botL = levels.get(k1);
        if (topL.isEmpty() || botL.isEmpty()) return;
        Node a = topL.get((int) (rnd.next() * topL.size()));
        if (a.gate) return;
        double radius = 14 + 8 * rnd.next(), halfW = 2.6 + 1.0 * rnd.next(), height = 5 + 2 * rnd.next();
        double pitch = height + 4 + 2 * rnd.next();
        int dir = rnd.next() < 0.5 ? 1 : -1;
        // the spiral beside the junction, so the top turn's start is a short walk from it
        double ang = rnd.next() * 2 * Math.PI, off = radius + 8;
        double cx = a.x + Math.cos(ang) * off, cz = a.z + Math.sin(ang) * off;
        double top = a.y - 1, bottom = levelY(k1, cx, cz) - 2;
        if (top - bottom < 40) return;
        if (ground(cx, cz) - COVER < top + height + 8) return;
        // Never a drowned spiral, nor one a water table's edge runs through (the edge's rock seal cut the
        // ledge: DeepCheck's spiral walk, 2026-11-19): every table over the drum stays under its floor.
        for (int k = 0; k < 9; k++) {
            double tx = cx + (k == 0 ? 0 : Math.cos(k * Math.PI / 4) * (radius + 8)), tz = cz + (k == 0 ? 0 : Math.sin(k * Math.PI / 4) * (radius + 8));
            double table = core.waters().table((int) tx, (int) tz);
            if (!Double.isNaN(table) && table > bottom - 2) return;
        }
        Passages.Seg sp = Passages.Seg.spiral(cx, cz, top, bottom, radius, halfW, height, pitch, dir, rnd.nextLong());
        cell.segs.add(sp);
        // in at the top turn's start (angle 0, turned by dir: on the +x side either way), on the ledge's middle
        double ex = cx + Passages.spiralLedge(sp, 0, top), ez = cz + 2 * dir;
        Karst.walk(cell.segs, a.x, a.y, a.z, ex, top + 1.5, ez, rnd.nextLong(), Karst.Style.tube(2.6 + rnd.next(), 0.8, 0.5).varied().at(-1), null);
        // out at the bottom, from the void's floor toward the lower level's nearest junction
        Node b = nearest(botL, cx, cz, 160);
        if (b != null) {
            double bx = cx + (b.x - cx) / Math.max(1, Math.hypot(b.x - cx, b.z - cz)) * (radius - 4);
            double bz = cz + (b.z - cz) / Math.max(1, Math.hypot(b.x - cx, b.z - cz)) * (radius - 4);
            Karst.walk(cell.segs, bx, bottom + 2, bz, b.x, b.y, b.z, rnd.nextLong(), Karst.Style.tube(2.6 + rnd.next(), 0.8, 0.5).varied().at(-1), null);
        }
        cell.spirals.add(new double[] {cx, top, cz, bottom, radius});
    }

    /** Debug: the spiral galleries' own distance at a point (nothing else carved), or 8 if none is near. */
    public double spiralOnly(double x, double y, double z) {
        double best = 8;
        for (int cj = cellOf(z - 60); cj <= cellOf(z + 60); cj++) {
            for (int ci = cellOf(x - 60); ci <= cellOf(x + 60); ci++) {
                for (Passages.Seg sg : cell(ci, cj).segs) if (sg.kind == Passages.SPIRAL) best = Math.min(best, Passages.spiralSdf(sg, x + 0.5, y + 0.5, z + 0.5));
            }
        }
        return best;
    }

    /** The spirals (R7) whose centres lie in the box: {x, top y, z, bottom y, radius}. */
    public void spirals(double x0, double z0, double x1, double z1, List<double[]> out) {
        if (OFF) return;
        for (int cj = cellOf(z0); cj <= cellOf(z1); cj++) {
            for (int ci = cellOf(x0); ci <= cellOf(x1); ci++) {
                for (double[] f : cell(ci, cj).spirals) {
                    if (f[0] >= x0 && f[0] < x1 && f[2] >= z0 && f[2] < z1) out.add(f);
                }
            }
        }
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

    /** The library archetypes (core/Rooms) each mood draws its rooms from. */
    private static int[] archetypes(Moods.Mood m) {
        return switch (m) {
            case AWE -> new int[] {Rooms.DOME, Rooms.BRIDGE, Rooms.CANYON, Rooms.AVEN, Rooms.PILLARS, Rooms.RAMIFORM};
            case COZY -> new int[] {Rooms.GALLERY, Rooms.TERRACES, Rooms.BOWL, Rooms.RAMIFORM, Rooms.PILLARS};
            case CLEVER -> new int[] {Rooms.FISSURES, Rooms.KEYHOLE, Rooms.FINS, Rooms.TERRACES, Rooms.CROSSROADS};
            case FEAR -> new int[] {Rooms.BREAKDOWN, Rooms.FISSURES, Rooms.CANYON, Rooms.FINS};
            case HORROR -> new int[] {Rooms.BREAKDOWN, Rooms.RAMIFORM, Rooms.BOWL, Rooms.FINS};
        };
    }

    /**
     * Which room (if any) a junction gets, its size, and whether a squeeze leads into it. Sizes are
     * heavy-tailed (lognormal), bigger where passages meet: a few big rooms a cell, many small ones
     * (docs/CAVE_REDESIGN.md). Big rooms are entered through a squeeze most of the time.
     */
    private void plan(Node n, int k) {
        double chance = switch (n.mood) {
            case AWE -> 0.55;
            case COZY -> 0.5;
            case CLEVER -> 0.4;
            case FEAR -> 0.25;
            case HORROR -> 0.3;
        };
        if (n.degree >= 3) chance += 0.25;
        if (unit(n.h, 11) >= chance) return;
        int[] arch = archetypes(n.mood);
        n.room = arch[(int) (unit(n.h, 12) * arch.length)];
        double median = (abyss(k) ? 24 : 20) * (n.degree >= 3 ? 1.2 : 1);
        n.roomW = Rooms.lognormal(n.h, 14, median, 0.45, 9, 40);
        n.squeeze = n.roomW > 22 ? unit(n.h, 13) < 0.7 : unit(n.h, 13) < 0.2;
    }

    /** A passage between two junctions of level k, shaped by the mood where it starts. */
    private void passage(List<Passages.Seg> out, int k, Node a, Node b, Karst.Rng rnd, boolean tight) {
        boolean ab = abyss(k);
        Moods.Mood m = a.mood;
        Karst.Style st;
        if (tight && Math.hypot(a.x - b.x, a.z - b.z) < 80) {
            st = Karst.Style.tube(1.45, 0.8, 0.45); // the squeeze: crouch height, the compression before the room
        } else {
            // Heavy-tailed widths, growing downstream with the junctions that drain through (a branchwork
            // cave's trunk is its biggest passage): tributaries tight, trunks wide.
            double flow = 1 + 0.28 * Math.log(1 + Math.max(a.desc, b.desc));
            double r = Rooms.lognormal(rnd.nextLong(), 0, ab ? 3.8 : 3.2, 0.4, 1.4, 7) * flow * passageScale(m);
            r = Math.min(r, ab ? 11 : 9);
            double roll = rnd.next();
            double crawl = m == Moods.Mood.HORROR ? 0.25 : m == Moods.Mood.FEAR ? 0.2 : 0.06;
            double gallery = m == Moods.Mood.COZY ? 0.45 : 0.12;
            double canyon = m == Moods.Mood.AWE ? 0.45 : m == Moods.Mood.FEAR ? 0.4 : m == Moods.Mood.HORROR ? 0.3 : 0.25;
            if (roll < crawl && flow < 1.5) {
                st = Karst.Style.tube(1.45 + 0.2 * rnd.next(), 0.8, 0.45); // a crawl at crouch height
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

    /** The room planned at a junction: a library room (core/Rooms), its floor at the passages' floor. */
    private void room(List<Passages.Seg> out, int k, Node n) {
        if (n.room < 0) return;
        double floorY = n.y - 1.5;
        double maxH = ground(n.x, n.z) - 30 - floorY;
        if (maxH < 5) return;
        out.add(Passages.Seg.of(Rooms.make(n.h ^ 0x400DL, n.x, floorY, n.z, n.roomW, maxH, n.room)));
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
