package com.seaweed.grandatlas.core.hydro;

import static com.seaweed.grandatlas.core.Maths.lerp;
import static com.seaweed.grandatlas.core.Maths.smoothstep;

import com.seaweed.grandatlas.core.AtlasCore;
import com.seaweed.grandatlas.core.ColumnSample;
import com.seaweed.grandatlas.core.noise.GradientNoise;
import com.seaweed.grandatlas.core.world.WorldField;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLongArray;
import java.util.concurrent.atomic.AtomicReferenceArray;

/**
 * Watershed rivers (design §8, playtest round 3: "small rivers flow into big ones and big ones
 * into oceans, like real watersheds"). A drainage graph over the terrain itself:
 * <ol>
 *   <li><b>Nodes</b> every 128 blocks, each at the lowest of five points in its cell, with the
 *       terrain height and the rain that falls there (from humidity: deserts get a tenth).</li>
 *   <li><b>Flow</b>: each node drains to its lowest neighbour by terrain plus a gentle pull toward
 *       the sea ({@link CoastDistance}), so water follows valleys locally and heads for the coast
 *       over kilometres.</li>
 *   <li><b>Pits</b> (a node with no lower neighbour) breach: a flood from the pit finds the lowest
 *       pass to ground lower than the pit within 15 nodes, and the path over it is routed through,
 *       cutting a gorge. Pits are breached from the lowest up and the first route through a node
 *       wins, so every breach ends lower than it began and nothing can loop. A pit with no way out
 *       is a closed basin: it gets a lake.</li>
 *   <li><b>Lakes on rivers</b>: where a river runs through a hollow (its node lower than all eight
 *       around it), it pools into a lake at its own level and leaves by its outlet. About one
 *       every 2 km of river country.</li>
 *   <li><b>Catchment</b>: a node's flow is all the rain upstream of it. A stream starts where that
 *       reaches {@link #SOURCE} and widens as tributaries join it.</li>
 *   <li><b>Water level</b> only ever falls downstream: at most the lowest level upstream, and
 *       kept below the banks all along the course. So a river crossing a rise cuts a gorge.</li>
 *   <li><b>Pools</b>: the water stands in flat pools that spill into each other: riffles on
 *       gentle rivers, falls into plunge pools on steep ones, tiered falls down cliffs. Still
 *       water never lies on a slope.</li>
 *   <li><b>Containment</b>: no water stands in the open. Dry ground next to water is at least a
 *       block above it, raised into a bank or levee where it isn't ({@link #carve}).</li>
 * </ol>
 * Everything is a function of position, computed in 32x32-node tiles that any chunk can ask for
 * and that agree wherever they meet. A river's course between nodes is a spline through them,
 * with meanders scaled to its width. Columns near it are cut down to its channel, floodplain and
 * valley walls, and banks are raised where they must hold water.
 */
public final class Drainage {
    public static final double STEP = 128;
    static final int TILE = 32;
    static final int BREACH = 15;
    /** Rain (in nodes of average rainfall) a stream needs upstream to start. */
    public static final double SOURCE = 6;
    /** Routing pull toward the sea, in blocks of height per block of distance to it. */
    static final double PULL = 0.08;
    static final double SEA = AtlasCore.SEA_LEVEL - 0.1;
    /** Water stands at least this far below the ground at a node and along both banks. */
    static final double BANK = 2.5;
    static final int SEGMENTS = 16;
    static final byte OCEAN = -2, SINK = -1;
    private static final byte UNSET = 100;
    static final int[] DX = {1, 1, 0, -1, -1, -1, 0, 1};
    static final int[] DZ = {0, 1, 1, 1, 0, -1, -1, -1};
    /** A node is lowland (its river at sea level) if nothing on its way to the sea is higher than this over it. */
    static final double LOWLAND_RISE = 70;
    /** Fine routing: an 8-block grid in a corridor 80 blocks wide, 16 past each end. */
    private static final int PATH_CELL = 8, PATH_HALF = 40, PATH_PAD = 16;
    /** Lakes flood an 8-block grid out to 18 cells (144 blocks) from their node. */
    private static final int LAKE_CELL = 8, LAKE_RADIUS = 18;
    private static final long UNKNOWN = Long.MIN_VALUE;
    private static final Object NO_LAKE = new Object();

    /** The terrain before rivers. */
    @FunctionalInterface
    public interface Terrain {
        ColumnSample sample(double x, double z);
    }

    /** What {@link #carve} found at a column. */
    public static final class Carve {
        /** Water surface over the column, or NaN. */
        public double water;
        public boolean lake;
        /** Within a few blocks of water: no 3D roughness or caves here, or they would hole the banks. */
        public boolean near;
        /** At the lip of a pool, where the river spills into the next one down. */
        public boolean spill;
        /** The water is a fjord's: the sea in a drowned glacial valley. */
        public boolean fjord;
        /** Downstream direction of the river here (zero in a lake). */
        public double flowX, flowZ;
        double lower;
        // Each body's own result at this column, to give the water to whichever cut it deepest.
        int count;
        final double[] cLower = new double[64], cWater = new double[64], cFlowX = new double[64], cFlowZ = new double[64];
        final boolean[] cLake = new boolean[64], cSpill = new boolean[64], cFjord = new boolean[64];
        /** Blocks beyond the body's guarded bank (0 on it, infinite where it guards nothing). */
        final double[] cBeyond = new double[64];

        /**
         * One body's result here: its own lowered ground, its water (or NaN), and how far this column
         * lies beyond its guarded bank. No other body may cut the bank below the body's own shape
         * while the column stays dry, and beyond it only down a steep slope, so a valley next to a
         * higher pool rises to its bank instead of standing a sheer wall of rock beside it.
         */
        void offer(double lower, double water, boolean lake, double flowX, double flowZ, boolean spill, double beyond) {
            if (count == cLower.length) return;
            cLower[count] = lower;
            cWater[count] = water;
            cLake[count] = lake;
            cFlowX[count] = flowX;
            cFlowZ[count] = flowZ;
            cSpill[count] = spill;
            cBeyond[count] = beyond;
            cFjord[count] = false;
            count++;
        }

        /**
         * Gives the water to whichever river or lake cut this column (within a block) to its final
         * depth: a river whose channel another's valley has cut away must not hang its water over
         * that valley. A lake wins over the rivers in it; otherwise the lowest river water does.
         */
        boolean settle() {
            double lakeLevel = Double.NaN, riverLevel = Double.NaN;
            int river = -1;
            for (int c = 0; c < count; c++) {
                // A lake fills whatever is cut inside its shore; a river only its own channel.
                if (Double.isNaN(cWater[c]) || !cLake[c] && cLower[c] > lower + 1 || lower >= cWater[c]) continue;
                if (cLake[c]) {
                    if (!(lakeLevel >= cWater[c])) lakeLevel = cWater[c];
                } else if (!(riverLevel <= cWater[c])) {
                    riverLevel = cWater[c];
                    river = c;
                }
            }
            if (!Double.isNaN(lakeLevel)) {
                water = lakeLevel;
                lake = true;
            } else if (river >= 0) {
                water = riverLevel;
                flowX = cFlowX[river];
                flowZ = cFlowZ[river];
                spill = cSpill[river];
                fjord = cFjord[river];
            }
            return !Double.isNaN(water);
        }
    }

    private final long seed;
    private final Terrain terrain;
    private final CoastDistance coast;
    private final TileCache<Nodes> nodeTiles = new TileCache<>(1024);
    private final TileCache<Routes> routeTiles = new TileCache<>(1024);
    private final ThreadLocal<Recent> recent = ThreadLocal.withInitial(Recent::new);

    public Drainage(long seed, WorldField world, Terrain terrain) {
        this.seed = GradientNoise.fork(seed, 1);
        this.terrain = terrain;
        this.coast = new CoastDistance(world);
    }

    // ---- stage 1: nodes

    private static final class Nodes {
        final float[] x = new float[TILE * TILE], z = new float[TILE * TILE], t = new float[TILE * TILE];
        final float[] q = new float[TILE * TILE], rain = new float[TILE * TILE];
        final boolean[] ocean = new boolean[TILE * TILE];
    }

    private Nodes computeNodes(int ti, int tj) {
        Nodes n = new Nodes();
        for (int b = 0; b < TILE; b++) {
            for (int a = 0; a < TILE; a++) {
                int i = ti * TILE + a, j = tj * TILE + b, k = a + b * TILE;
                long h = GradientNoise.hash(seed, i, j);
                double cx = (i + 0.5 + 0.5 * (unit(h, 0) - 0.5)) * STEP;
                double cz = (j + 0.5 + 0.5 * (unit(h, 1) - 0.5)) * STEP;
                // The lowest of five points, so the network keeps to the valleys.
                ColumnSample best = terrain.sample(cx, cz);
                double bx = cx, bz = cz;
                for (int o = 0; o < 4; o++) {
                    double px = cx + ((o & 1) == 0 ? -0.22 : 0.22) * STEP, pz = cz + ((o & 2) == 0 ? -0.22 : 0.22) * STEP;
                    ColumnSample c = terrain.sample(px, pz);
                    if (c.height() < best.height()) {
                        best = c;
                        bx = px;
                        bz = pz;
                    }
                }
                n.x[k] = (float) bx;
                n.z[k] = (float) bz;
                n.t[k] = (float) best.height();
                // (Up to 0.05: the coast's bays reach that far into the plain field's land.)
                n.ocean[k] = best.height() < AtlasCore.SEA_LEVEL - 1 && best.continentalness() < 0.05;
                n.rain[k] = n.ocean[k] ? 0 : (float) (0.1 + 0.9 * smoothstep(-0.55, 0.35, best.humidity()));
                n.q[k] = (float) (best.height() + PULL * coast.at(bx, bz));
            }
        }
        return n;
    }

    // ---- stage 2: routes (flow directions, with pits breached)

    private static final class Routes {
        final byte[] down = new byte[TILE * TILE];
        /** Catchment and water level per node, packed as two floats; 0 = not yet computed. */
        final AtomicLongArray memo = new AtomicLongArray(TILE * TILE);
        /** Each node's river course ({@link Edge}), built on first use. */
        final AtomicReferenceArray<Object> shapes = new AtomicReferenceArray<>(TILE * TILE);
        /** Each node's path to the next ({@link Course}), built on first use; an edge needs its neighbours' too. */
        final AtomicReferenceArray<Object> paths = new AtomicReferenceArray<>(TILE * TILE);
        /** Each node's lake, or NO_LAKE, built on first use. */
        final AtomicReferenceArray<Object> lakes = new AtomicReferenceArray<>(TILE * TILE);
        /** Highest ground on each node's way to the sea (as double bits), or UNKNOWN. */
        final AtomicLongArray seaward;

        Routes() {
            long[] init = new long[TILE * TILE];
            Arrays.fill(init, UNKNOWN);
            seaward = new AtomicLongArray(init);
        }
    }

    private Routes computeRoutes(int ti, int tj) {
        int m = 2 * BREACH + 1;
        int w = TILE + 2 * m;
        int i0 = ti * TILE - m, j0 = tj * TILE - m;
        float[] q = new float[w * w];
        boolean[] sea = new boolean[w * w];
        for (int b = 0; b < w; b++) {
            for (int a = 0; a < w; a++) {
                Nodes nd = nodes(i0 + a, j0 + b);
                int k = local(i0 + a, j0 + b);
                q[a + b * w] = nd.q[k];
                sea[a + b * w] = nd.ocean[k];
            }
        }
        // Steepest descent for the tile and everything within BREACH of it; pits there.
        byte[] down = new byte[w * w];
        Arrays.fill(down, UNSET);
        List<Integer> sinks = new ArrayList<>();
        for (int b = m - BREACH; b < m + TILE + BREACH; b++) {
            for (int a = m - BREACH; a < m + TILE + BREACH; a++) {
                int at = a + b * w;
                if (sea[at]) {
                    down[at] = OCEAN;
                    continue;
                }
                int best = -1;
                float bq = q[at];
                for (int k = 0; k < 8; k++) {
                    int n = at + DX[k] + DZ[k] * w;
                    if (q[n] < bq) {
                        bq = q[n];
                        best = k;
                    }
                }
                down[at] = best >= 0 ? (byte) best : SINK;
                if (best < 0) sinks.add(at);
            }
        }
        // Breach pits from the lowest up; the first route through a node wins.
        sinks.sort((u, v) -> {
            int c = Float.compare(q[u], q[v]);
            if (c != 0) return c;
            c = Integer.compare(j0 + u / w, j0 + v / w);
            return c != 0 ? c : Integer.compare(i0 + u % w, i0 + v % w);
        });
        byte[] over = new byte[w * w];
        Arrays.fill(over, UNSET);
        int[] stamp = new int[w * w], parent = new int[w * w];
        Heap heap = new Heap(1 << 10);
        int mark = 0;
        for (int s : sinks) {
            mark++;
            heap.clear();
            stamp[s] = mark;
            heap.push(q[s], s);
            int sa = s % w, sb = s / w, outlet = -1;
            while (!heap.isEmpty()) {
                double level = heap.topKey();
                int x = heap.pop();
                if (x != s && (sea[x] || q[x] < q[s])) {
                    outlet = x;
                    break;
                }
                int xa = x % w, xb = x / w;
                for (int k = 0; k < 8; k++) {
                    int na = xa + DX[k], nb = xb + DZ[k];
                    if (Math.max(Math.abs(na - sa), Math.abs(nb - sb)) > BREACH) continue;
                    int n = na + nb * w;
                    if (stamp[n] == mark) continue;
                    stamp[n] = mark;
                    parent[n] = x;
                    heap.push(Math.max(level, q[n]), n);
                }
            }
            if (outlet < 0) continue; // a closed basin: it keeps its lake
            for (int y = outlet; y != s; ) {
                int p = parent[y];
                if (over[p] == UNSET) over[p] = direction(y % w - p % w, y / w - p / w);
                y = p;
            }
        }
        Routes r = new Routes();
        for (int b = 0; b < TILE; b++) {
            for (int a = 0; a < TILE; a++) {
                int at = (a + m) + (b + m) * w;
                r.down[a + b * TILE] = over[at] != UNSET ? over[at] : down[at];
            }
        }
        return r;
    }

    private static byte direction(int da, int db) {
        for (int k = 0; k < 8; k++) {
            if (DX[k] == da && DZ[k] == db) return (byte) k;
        }
        throw new IllegalStateException("not a neighbour: " + da + ", " + db);
    }

    // ---- stage 3: catchment and water level

    private long memo(int i, int j) {
        long v = routes(i, j).memo.get(local(i, j));
        return v != 0 ? v : computeMemo(i, j);
    }

    /** Post-order walk up the whole catchment, iteratively (a big river's runs to tens of thousands of nodes). */
    private long computeMemo(int i0, int j0) {
        long[] stack = new long[256];
        int top = 0;
        Set<Long> open = new HashSet<>();
        stack[top++] = key(i0, j0);
        open.add(key(i0, j0));
        while (top > 0) {
            long node = stack[top - 1];
            int i = (int) (node >> 32), j = (int) node;
            Routes r = routes(i, j);
            int k = local(i, j);
            if (r.memo.get(k) != 0) {
                top--;
                open.remove(node);
                continue;
            }
            Nodes n = nodes(i, j);
            double acc = n.rain[k], level = n.t[k] - BANK;
            boolean ready = true;
            for (int e = 0; e < 8; e++) {
                int ui = i + DX[e], uj = j + DZ[e];
                if (!drainsTo(ui, uj, i, j)) continue;
                long um = routes(ui, uj).memo.get(local(ui, uj));
                if (um != 0) {
                    acc += catchment(um);
                    level = Math.min(level, level(um));
                } else if (!open.contains(key(ui, uj))) { // already open would be a loop; leave it out
                    if (top == stack.length) stack = Arrays.copyOf(stack, top * 2);
                    stack[top++] = key(ui, uj);
                    open.add(key(ui, uj));
                    ready = false;
                }
            }
            if (ready) {
                r.memo.set(k, pack(acc, n.ocean[k] ? SEA : Math.max(SEA, level)));
                top--;
                open.remove(node);
            }
        }
        return routes(i0, j0).memo.get(local(i0, j0));
    }

    private boolean drainsTo(int ui, int uj, int i, int j) {
        byte d = downOf(ui, uj);
        return d >= 0 && ui + DX[d] == i && uj + DZ[d] == j;
    }

    private static long pack(double catchment, double level) {
        return ((long) Float.floatToRawIntBits((float) catchment) << 32) | (Float.floatToRawIntBits((float) level) & 0xFFFFFFFFL);
    }

    private static double catchment(long memo) {
        return Float.intBitsToFloat((int) (memo >>> 32));
    }

    private static double level(long memo) {
        return Float.intBitsToFloat((int) memo);
    }

    // ---- lowland and lake levels

    /** Highest ground on the way from a node to the sea, its own included (+infinity if it never gets there). */
    private double seaward(int i, int j) {
        Routes r = routes(i, j);
        long v = r.seaward.get(local(i, j));
        if (v != UNKNOWN) return Double.longBitsToDouble(v);
        // Walk down to the sea or to a node already known, then fill the way back.
        long[] path = new long[64];
        int count = 0;
        int ci = i, cj = j;
        double known = Double.POSITIVE_INFINITY;
        for (int step = 0; step < 20_000; step++) {
            long cv = routes(ci, cj).seaward.get(local(ci, cj));
            if (cv != UNKNOWN) {
                known = Double.longBitsToDouble(cv);
                break;
            }
            if (count == path.length) path = Arrays.copyOf(path, count * 2);
            path[count++] = key(ci, cj);
            byte d = downOf(ci, cj);
            if (d == OCEAN) {
                known = SEA;
                break;
            }
            if (d == SINK) break;
            ci += DX[d];
            cj += DZ[d];
        }
        for (int k = count - 1; k >= 0; k--) {
            int pi = (int) (path[k] >> 32), pj = (int) path[k];
            Nodes n = nodes(pi, pj);
            known = n.ocean[local(pi, pj)] ? SEA : Math.max(known, n.t[local(pi, pj)]);
            routes(pi, pj).seaward.set(local(pi, pj), Double.doubleToRawLongBits(known));
        }
        return Double.longBitsToDouble(routes(i, j).seaward.get(local(i, j)));
    }

    /**
     * A lowland node: nothing on its way to the sea stands more than {@link #LOWLAND_RISE}
     * above it. Its river runs at sea level in a valley lowered to it, filled by the sea itself,
     * as vanilla's and TerraForged's rivers are. Everything downstream of one is lowland too.
     */
    private boolean lowland(int i, int j) {
        return seaward(i, j) <= SEA + LOWLAND_RISE || fjord(i, j);
    }

    private final java.util.concurrent.ConcurrentHashMap<Long, Boolean> fjords = new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * Fjords (docs/M3_M4_PLAN.md, phase 6): a node whose river reaches the sea within ~3 km, in the
     * cold, with mountains standing over it on two sides or more. Its river is the sea, run up a
     * drowned glacial valley: sea level, a deep U-shaped trough, sheer walls.
     */
    private boolean fjord(int i, int j) {
        Boolean known = fjords.get(key(i, j));
        if (known != null) return known;
        boolean f = false;
        int steps = toOcean(i, j);
        if (steps >= 0 && steps < 26) {
            // A head itself, or downstream of one: the trough runs on to the sea.
            f = fjordHead(i, j);
            for (int e = 0; e < 8 && !f; e++) {
                int ui = i + DX[e], uj = j + DZ[e];
                if (drainsTo(ui, uj, i, j) && toOcean(ui, uj) == steps + 1) f = fjord(ui, uj);
            }
        }
        fjords.putIfAbsent(key(i, j), f);
        return f;
    }

    /** The node where this node's river reaches the sea, as a key (or this node's own, if it never does). */
    private int mouthKey(int i, int j) {
        int ci = i, cj = j;
        for (int steps = 0; steps < 40; steps++) {
            byte d = downOf(ci, cj);
            if (d == OCEAN || d == SINK) break;
            ci += DX[d];
            cj += DZ[d];
        }
        return ci * 73856093 ^ cj * 19349663;
    }

    /** Nodes down to the sea, or -1 if it never gets there. */
    private int toOcean(int i, int j) {
        int ci = i, cj = j;
        for (int steps = 0; steps < 40; steps++) {
            byte d = downOf(ci, cj);
            if (d == OCEAN) return steps;
            if (d == SINK) return -1;
            ci += DX[d];
            cj += DZ[d];
        }
        return -1;
    }

    private boolean fjordHead(int i, int j) {
        {
            Nodes n = nodes(i, j);
            int at = local(i, j);
            if (n.ocean[at]) return false;
            double x = n.x[at], z = n.z[at];
            ColumnSample c = terrain.sample(x, z);
            // A drowned valley: its floor near the sea already, not up on a plateau or massif (a trough
            // cut from there down under the sea slices the tableland and its lake in two).
            if (c.macroInside() > 0.05) return false;
            boolean cold = c.temperature() <= -0.2;
            // In temperate hill country some valleys near the sea are sea lochs instead (the user's
            // "more unique landforms"): Scotland's long narrow inlets, the hills lower than a fjord's.
            if (!cold && (c.temperature() > 0.45 || unit(GradientNoise.hash(seed ^ 0x10C4L, mouthKey(i, j), 0), 0) > 0.15)) return false; // per river, not per node
            double need = cold ? AtlasCore.SEA_LEVEL + 140 : AtlasCore.SEA_LEVEL + 90, reach = cold ? 220 : 180;
            int high = 0;
            for (int s = 0; s < 4; s++) {
                double a = s * Math.PI / 2 + 0.4;
                if (terrain.sample(x + reach * Math.cos(a), z + reach * Math.sin(a)).height() > need) high++;
            }
            return high >= 2;
        }
    }

    /** The water level a node's rivers meet at: sea level in the lowlands, a lake's surface where there is one. */
    private double surfaceLevel(int i, int j) {
        if (lowland(i, j)) return SEA;
        double level = level(memo(i, j));
        Lake lake = lakeAt(i, j);
        return lake != null ? Math.max(level, lake.level) : level;
    }

    // ---- river courses

    /**
     * A river's course from one node to the next, along the valley floor: the lowest way through
     * an 80-block corridor on an 8-block grid, smoothed. With it, the water level ("safe": never
     * rising, kept under the ground on the course and both banks) and the flat pools that level
     * is stepped into.
     */
    private static final class Edge {
        float[] x, z, arc, safe;
        float[] poolStart, poolLevel;
        /** Per pool: the radius of the amphitheatre below the waterfall it starts with, and the fall's height (0: no fall). */
        float[] fallR, fallH;
        float half0, half1, depth, minX, maxX, minZ, maxZ;
        boolean source, lowland, fjord, estuary;
        /** A fjord in temperate country: a sea loch, narrower and shallower, its sides less sheer. */
        boolean loch;
        /** The topmost reach of a fjord or loch, which tapers to its head. */
        boolean fjordHead;
        /** Nodes from this edge's start down to the sea (fjords and lochs widen toward their mouths). */
        int seaSteps;
        /** A big lowland river's last reach splitting into a delta: its channels, and how far they fan out. */
        boolean delta;
        int deltaChannels;
        double deltaSpread;
        double[][] oxbows = new double[0][];
        /** The lake at the upstream node, if any: the river holds its level until it's out of it. */
        Lake lake;
    }

    /** A node's share of the land, in km2 (catchment is counted in nodes of average rain). */
    static final double NODE_KM2 = STEP * STEP / 1e6;

    /**
     * Half the channel's width. It grows with the catchment the way real channels do (bankfull
     * width about 2.7 A^0.47 metres for A km2), but three times as wide: true to life, nearly every
     * stream was a 1-block trickle (the user: "that's just not it"). So a stream starts about 3
     * wide, is 8 at 1 km2, 24 at 10, and the big rivers reach 60-80.
     */
    static final double WIDTH_SCALE = 3;

    static double halfWidth(double catchment) {
        double km2 = Math.max(0, catchment) * NODE_KM2;
        return Math.max(1.2, Math.min(42, WIDTH_SCALE * 1.35 * Math.pow(km2, 0.47)));
    }

    /** Depth over the bed: a block and a bit for a creek, deeper as the channel widens. */
    private static double depthOf(double catchment) {
        return Math.min(8, 1.1 + 0.2 * halfWidth(catchment));
    }

    private Edge edge(int i, int j) {
        Routes r = routes(i, j);
        int k = local(i, j);
        if (r.shapes.get(k) instanceof Edge e) return e;
        Edge e = buildEdge(i, j);
        r.shapes.set(k, e);
        return e;
    }

    /** A node's river path to the next node, points every 6 blocks, and the lowest ground on it and its banks. */
    /** @param oxbows the cut-off loops beside its bends: centre x, z, radius, width, heading away from the river */
    private record Course(double[][] p, double low, double[][] oxbows) {
    }

    private Course course(int i, int j) {
        Routes r = routes(i, j);
        int k = local(i, j);
        if (r.paths.get(k) instanceof Course c) return c;
        byte d = downOf(i, j);
        java.util.List<double[]> oxbows = new java.util.ArrayList<>();
        double[][] p = meander(i, j, resample(smooth(finePath(position(i, j), position(i + DX[d], j + DZ[d]))), 6), oxbows);
        double off = halfWidth(catchment(memo(i, j))) + 3, low = Double.MAX_VALUE;
        for (int s = 0; s < p.length; s++) {
            int a = Math.max(0, s - 1), b = Math.min(p.length - 1, s + 1);
            double tx = p[b][0] - p[a][0], tz = p[b][1] - p[a][1], tl = Math.max(1e-6, Math.hypot(tx, tz));
            double nx = -tz / tl * off, nz = tx / tl * off;
            low = Math.min(low, Math.min(terrain.sample(p[s][0], p[s][1]).height(),
                    Math.min(terrain.sample(p[s][0] + nx, p[s][1] + nz).height(), terrain.sample(p[s][0] - nx, p[s][1] - nz).height())));
        }
        Course c = new Course(p, low, oxbows.toArray(new double[0][]));
        r.paths.set(k, c);
        return c;
    }

    /**
     * Meanders (docs/M3_M4_PLAN.md, phase 4): on flat ground a river swings from side to side in
     * loops about 14 of its widths long, as far as 30% of that out, the swing growing with how flat
     * the reach is; its rhythm wanders. Straight at both ends, so reaches still meet at their nodes.
     * Fjords stay as they are.
     */
    private double[][] meander(int i, int j, double[][] p, java.util.List<double[]> oxbows) {
        int n = p.length;
        if (n < 4 || fjord(i, j)) return p;
        double total = 0;
        double[] arc = new double[n];
        for (int s = 1; s < n; s++) arc[s] = total += Math.hypot(p[s][0] - p[s - 1][0], p[s][1] - p[s - 1][1]);
        double h0 = terrain.sample(p[0][0], p[0][1]).height(), h1 = terrain.sample(p[n - 1][0], p[n - 1][1]).height();
        double grade = Math.abs(h0 - h1) / Math.max(1, total);
        double flat = 1 - smoothstep(0.012, 0.07, grade);
        double half = halfWidth(catchment(memo(i, j)));
        // Every river short of a mountain torrent wanders a little (routed on an 8-block grid it ran
        // in ruler-straight diagonals); on flat ground it meanders in full.
        double wiggle = (3 + 1.2 * half) * (1 - smoothstep(0.08, 0.16, grade));
        if (flat <= 0 && wiggle <= 0) return p;
        // Real meanders repeat every 10-14 channel widths and wander across a belt 5-10 widths wide;
        // the wavelength keeps to 36+ so the 6-block course still draws it smooth.
        double wave = Math.max(36, 11 * 2 * half), amp = Math.max(wiggle, flat * Math.min(0.3 * wave, 5 * half + 5));
        if (flat <= 0) wave = Math.max(30, 8 * 2 * half);
        long h = GradientNoise.hash(seed ^ 0x3E4DL, i, j);
        double phase = (h & 0xFFFF) / 65536.0 * Math.PI * 2;
        double[][] q = new double[n][];
        double[] swings = new double[n];
        for (int s = 0; s < n; s++) {
            int a = Math.max(0, s - 1), b = Math.min(n - 1, s + 1);
            double tx = p[b][0] - p[a][0], tz = p[b][1] - p[a][1], tl = Math.max(1e-6, Math.hypot(tx, tz));
            double ends = smoothstep(0, 0.22, arc[s] / Math.max(1, total)) * smoothstep(0, 0.22, 1 - arc[s] / Math.max(1, total));
            double swing = amp * ends * Math.sin(arc[s] / wave * Math.PI * 2 + phase
                    + 1.2 * GradientNoise.noise2(seed ^ 0x3E4EL, p[s][0] / (2 * wave), p[s][1] / (2 * wave)));
            swings[s] = swing;
            q[s] = new double[] {p[s][0] - tz / tl * swing, p[s][1] + tx / tl * swing};
        }
        // Oxbow lakes (docs/M3_M4_PLAN.md, phase 4): beside some of a lowland river's full bends, the
        // crescent of a loop it has since cut off, a little way out from the bend.
        if (flat > 0.5 && lowland(i, j) && half >= 3 * WIDTH_SCALE && amp > 2 * half + 6) {
            for (int s = 1; s + 1 < n; s++) {
                double sw = swings[s];
                if (Math.abs(sw) < 0.9 * amp || Math.abs(sw) < Math.abs(swings[s - 1]) || Math.abs(sw) < Math.abs(swings[s + 1])) continue;
                if (((GradientNoise.hash(seed ^ 0x0B0BL, i * 31 + s, j) >>> 11) * 0x1.0p-53) > 0.45) continue;
                int a = Math.max(0, s - 1), b = Math.min(n - 1, s + 1);
                double tx = p[b][0] - p[a][0], tz = p[b][1] - p[a][1], tl = Math.max(1e-6, Math.hypot(tx, tz));
                double ox = -tz / tl * Math.signum(sw), oz = tx / tl * Math.signum(sw); // out from the bend
                double r = Math.max(12, Math.min(40, 0.5 * amp)), w = Math.max(3, 0.9 * half);
                double gap = r + half + w + 6;
                oxbows.add(new double[] {q[s][0] + ox * gap, q[s][1] + oz * gap, r, w, Math.atan2(oz, ox)});
            }
        }
        return resample(q, 6);
    }

    /**
     * The level a node's rivers meet at: its surface level, but no higher than the lowest ground
     * on any river's way into it allows. Node levels come from the ground at the nodes; a path
     * between two can dip under that, and the river must not be held up over the dip.
     */
    /**
     * How far a river has cut down below the land round it, beyond its banks' 2.5: real streams
     * sit well under the ground they drain (in the Appalachians the ground is 10 m up 120 m from
     * a 5 km2 stream), so their valleys are real valleys. 3 for a fresh creek, 6 at 1 km2, 13 at
     * 10, up to 30.
     */
    static double incision(double catchment) {
        return Math.min(30, 6 * Math.pow(Math.max(0, catchment) * NODE_KM2, 0.35));
    }

    private double meetLevel(int i, int j) {
        double level = surfaceLevel(i, j);
        if (lowland(i, j) || lakeAt(i, j) != null) return level;
        level -= incision(catchment(memo(i, j)));
        for (int e = 0; e < 8; e++) {
            int ui = i + DX[e], uj = j + DZ[e];
            if (drainsTo(ui, uj, i, j) && catchment(memo(ui, uj)) >= SOURCE) level = Math.min(level, course(ui, uj).low - BANK);
        }
        return Math.max(SEA, level);
    }

    private static double distanceTo(double[][] path, double x, double z) {
        double best = Double.MAX_VALUE;
        for (int s = 0; s + 1 < path.length; s++) {
            double ax = path[s][0], az = path[s][1], vx = path[s + 1][0] - ax, vz = path[s + 1][1] - az;
            double len2 = vx * vx + vz * vz;
            double u = len2 > 1e-9 ? Math.max(0, Math.min(1, ((x - ax) * vx + (z - az) * vz) / len2)) : 0;
            double dx = x - (ax + u * vx), dz = z - (az + u * vz);
            best = Math.min(best, dx * dx + dz * dz);
        }
        return Math.sqrt(best);
    }

    private Edge buildEdge(int i, int j) {
        byte d = downOf(i, j);
        int di = i + DX[d], dj = j + DZ[d];
        double[] p1 = position(i, j), p2 = position(di, dj);
        double inflow = -1;
        for (int e = 0; e < 8; e++) {
            int ui = i + DX[e], uj = j + DZ[e];
            if (drainsTo(ui, uj, i, j)) inflow = Math.max(inflow, catchment(memo(ui, uj)));
        }
        Edge edge = new Edge();
        edge.source = inflow < SOURCE;
        edge.lowland = lowland(i, j);
        edge.fjord = fjord(i, j);
        edge.loch = edge.fjord && terrain.sample(p1[0], p1[1]).temperature() > -0.2;
        if (edge.fjord) {
            // The head reach: no fjord comes into it from upstream. It narrows and shallows to its head.
            boolean fed = false;
            for (int e = 0; e < 8 && !fed; e++) {
                int ui = i + DX[e], uj = j + DZ[e];
                fed = drainsTo(ui, uj, i, j) && fjord(ui, uj);
            }
            edge.fjordHead = !fed;
            edge.seaSteps = Math.max(0, toOcean(i, j));
        }
        edge.estuary = edge.lowland && downOf(di, dj) == OCEAN;
        edge.lake = lakeAt(i, j);
        long m0 = memo(i, j), m1 = memo(di, dj);
        double a0 = catchment(m0), a1 = Math.max(a0, catchment(m1));
        double l0 = meetLevel(i, j), l1 = Math.min(l0, meetLevel(di, dj));

        // A tributary meets a bigger river: the one leaving the junction, and any bigger one into it.
        // It keeps its own width, and it is down to the junction's level where it reaches that
        // river's channel and banks (or the junction's lake), not at the junction node inside them;
        // otherwise its last pools stand along the big river as a wall of water.
        List<double[][]> main = new ArrayList<>();
        boolean mainStem = true;
        for (int e = 0; e < 8; e++) {
            int ui = di + DX[e], uj = dj + DZ[e];
            if ((ui != i || uj != j) && drainsTo(ui, uj, di, dj)) {
                double c = catchment(memo(ui, uj));
                if (c > a0 || c == a0 && key(ui, uj) < key(i, j)) {
                    main.add(course(ui, uj).p());
                    mainStem = false;
                }
            }
        }
        if (!mainStem && downOf(di, dj) >= 0) main.add(course(di, dj).p());
        Lake meets = lakeAt(di, dj);
        double halfJ = halfWidth(catchment(m1)), reach = halfJ + 2 + 0.4 * halfJ + 3;
        edge.half0 = (float) halfWidth(a0);
        edge.half1 = (float) (mainStem ? halfWidth(a1) : halfWidth(a0));
        // Deltas (docs/M3_M4_PLAN.md, phase 3): a lowland river 4+ wide reaching the sea splits into
        // 3-5 channels fanning out across low marsh islands, on three in four such rivers; the fan
        // scales with the river, a small one at a creek-sized river, a broad one at a big river.
        long dh = GradientNoise.hash(seed ^ 0xDE17AL, i, j);
        edge.delta = edge.estuary && edge.half1 >= 2.2 * WIDTH_SCALE && unit(dh, 0) < 0.75;
        edge.deltaChannels = 3 + (int) (unit(dh, 1) * 3);
        edge.deltaSpread = Math.min(260, 30 + 25 * edge.half1 / WIDTH_SCALE * 1.5);
        edge.depth = (float) depthOf(a0);

        double[][] path = course(i, j).p();
        edge.oxbows = course(i, j).oxbows();
        int n = path.length;
        edge.x = new float[n];
        edge.z = new float[n];
        edge.arc = new float[n];
        edge.safe = new float[n];
        for (int s = 0; s < n; s++) {
            edge.x[s] = (float) path[s][0];
            edge.z[s] = (float) path[s][1];
            if (s > 0) edge.arc[s] = edge.arc[s - 1] + (float) Math.hypot(path[s][0] - path[s - 1][0], path[s][1] - path[s - 1][1]);
        }
        double total = edge.arc[n - 1];
        double enter = total;
        if (!edge.lowland) {
            for (int s = 0; s < n; s++) {
                boolean in = meets != null && meets.field(edge.x[s], edge.z[s]) > 0.2;
                for (int m = 0; m < main.size() && !in; m++) in = distanceTo(main.get(m), edge.x[s], edge.z[s]) < reach;
                if (in) {
                    enter = edge.arc[s];
                    break;
                }
            }
        }
        // The safe level: never rising, at least 2.5 under the ground on the course and on both
        // banks, and down to the next node's level where it meets the river or lake there. Sea
        // level all the way in the lowlands.
        double lev = l0;
        for (int s = 0; s < n; s++) {
            if (edge.lowland) {
                edge.safe[s] = (float) SEA;
                continue;
            }
            if (s > 0) {
                double t = total > 1e-6 ? edge.arc[s] / total : 1;
                double toward = enter > 1e-6 ? Math.min(1, edge.arc[s] / enter) : 1;
                int a = Math.max(0, s - 1), b = Math.min(n - 1, s + 1);
                double tx = edge.x[b] - edge.x[a], tz = edge.z[b] - edge.z[a], tl = Math.max(1e-6, Math.hypot(tx, tz));
                double half = lerp(smoothstep(0.6, 1, t), edge.half0, edge.half1);
                double off = half + 3;
                double nx = -tz / tl * off, nz = tx / tl * off;
                double ground = Math.min(terrain.sample(edge.x[s], edge.z[s]).height(),
                        Math.min(terrain.sample(edge.x[s] + nx, edge.z[s] + nz).height(),
                                terrain.sample(edge.x[s] - nx, edge.z[s] - nz).height()));
                lev = Math.max(l1, Math.min(lev, Math.min(ground - BANK - incision(lerp(t, a0, a1)), lerp(smoothstep(0.5, 1, toward), l0, l1))));
            }
            edge.safe[s] = (float) lev;
        }
        buildPools(edge, l0);
        double pad = 3 * edge.half1 + valleyWidth(edge.half1) + MAX_WIDEN + 16;
        if (edge.fjord) pad = Math.max(pad, 400); // a fjord's trough and leaning walls reach further
        if (edge.delta) pad = Math.max(pad, edge.deltaSpread + 90);
        edge.minX = edge.maxX = edge.x[0];
        edge.minZ = edge.maxZ = edge.z[0];
        for (int s = 1; s < n; s++) {
            edge.minX = Math.min(edge.minX, edge.x[s]);
            edge.maxX = Math.max(edge.maxX, edge.x[s]);
            edge.minZ = Math.min(edge.minZ, edge.z[s]);
            edge.maxZ = Math.max(edge.maxZ, edge.z[s]);
        }
        edge.minX -= pad;
        edge.maxX += pad;
        edge.minZ -= pad;
        edge.maxZ += pad;
        return edge;
    }

    /**
     * The lowest way from a to b: Dijkstra over an 8-block grid in a corridor 80 blocks wide
     * (and 16 past each end), each step costing more the higher above the corridor's lowest
     * ground it goes. So the course keeps to the valley floor instead of the straight line
     * between nodes, which ran along hillsides and needed banks built up to hold its water.
     */
    private double[][] finePath(double[] a, double[] b) {
        double dx = b[0] - a[0], dz = b[1] - a[1], len = Math.max(1, Math.hypot(dx, dz));
        double ux = dx / len, uz = dz / len, nx = -uz, nz = ux;
        int na = (int) Math.ceil((len + 2 * PATH_PAD) / PATH_CELL) + 1, nc = 2 * PATH_HALF / PATH_CELL + 1;
        double[] h = new double[na * nc];
        double low = Double.MAX_VALUE;
        for (int ia = 0; ia < na; ia++) {
            for (int ic = 0; ic < nc; ic++) {
                double along = -PATH_PAD + ia * PATH_CELL, across = -PATH_HALF + ic * PATH_CELL;
                double v = terrain.sample(a[0] + ux * along + nx * across, a[1] + uz * along + nz * across).height();
                h[ia * nc + ic] = v;
                low = Math.min(low, v);
            }
        }
        int mid = nc / 2;
        int start = (PATH_PAD / PATH_CELL) * nc + mid;
        int goal = Math.min(na - 1, (int) Math.round((len + PATH_PAD) / PATH_CELL)) * nc + mid;
        double[] dist = new double[na * nc];
        int[] prev = new int[na * nc];
        Arrays.fill(dist, Double.MAX_VALUE);
        Arrays.fill(prev, -1);
        Heap heap = new Heap(256);
        dist[start] = 0;
        heap.push(0, start);
        while (!heap.isEmpty()) {
            double dcur = heap.topKey();
            int c = heap.pop();
            if (c == goal) break;
            if (dcur > dist[c]) continue;
            int ia = c / nc, ic = c % nc;
            for (int k = 0; k < 8; k++) {
                int ja = ia + DX[k], jc = ic + DZ[k];
                if (ja < 0 || jc < 0 || ja >= na || jc >= nc) continue;
                int nb = ja * nc + jc;
                double step = PATH_CELL * ((k & 1) == 0 ? 1 : Math.sqrt(2));
                double cost = dcur + step * (1 + 0.15 * Math.max(0, h[nb] - low));
                if (cost < dist[nb]) {
                    dist[nb] = cost;
                    prev[nb] = c;
                    heap.push(cost, nb);
                }
            }
        }
        List<double[]> out = new ArrayList<>();
        for (int c = goal; c >= 0; c = prev[c]) {
            int ia = c / nc, ic = c % nc;
            double along = -PATH_PAD + ia * PATH_CELL, across = -PATH_HALF + ic * PATH_CELL;
            out.add(new double[] {a[0] + ux * along + nx * across, a[1] + uz * along + nz * across});
            if (c == start) break;
        }
        java.util.Collections.reverse(out);
        if (out.size() < 2) return new double[][] {a, b};
        out.set(0, a);
        out.set(out.size() - 1, b);
        return out.toArray(new double[0][]);
    }

    /** Two rounds of Chaikin corner cutting, ends kept. */
    private static double[][] smooth(double[][] p) {
        for (int round = 0; round < 2 && p.length > 2; round++) {
            List<double[]> q = new ArrayList<>();
            q.add(p[0]);
            for (int s = 0; s + 1 < p.length; s++) {
                double[] u = p[s], v = p[s + 1];
                if (s > 0) q.add(new double[] {0.75 * u[0] + 0.25 * v[0], 0.75 * u[1] + 0.25 * v[1]});
                if (s + 2 < p.length) q.add(new double[] {0.25 * u[0] + 0.75 * v[0], 0.25 * u[1] + 0.75 * v[1]});
            }
            q.add(p[p.length - 1]);
            p = q.toArray(new double[0][]);
        }
        return p;
    }

    /** Points every {@code spacing} blocks along a polyline, ends kept. */
    private static double[][] resample(double[][] p, double spacing) {
        double total = 0;
        for (int s = 1; s < p.length; s++) total += Math.hypot(p[s][0] - p[s - 1][0], p[s][1] - p[s - 1][1]);
        int n = Math.max(2, (int) Math.ceil(total / spacing) + 1);
        double[][] out = new double[n][];
        int s = 0;
        double at = 0, segStart = 0;
        for (int k = 0; k < n; k++) {
            double target = total * k / (n - 1);
            while (s < p.length - 2 && segStart + Math.hypot(p[s + 1][0] - p[s][0], p[s + 1][1] - p[s][1]) < target) {
                segStart += Math.hypot(p[s + 1][0] - p[s][0], p[s + 1][1] - p[s][1]);
                s++;
            }
            double len = Math.hypot(p[s + 1][0] - p[s][0], p[s + 1][1] - p[s][1]);
            double u = len > 1e-9 ? Math.max(0, Math.min(1, (target - segStart) / len)) : 0;
            out[k] = new double[] {lerp(u, p[s][0], p[s + 1][0]), lerp(u, p[s][1], p[s + 1][1])};
        }
        out[0] = p[0];
        out[n - 1] = p[p.length - 1];
        return out;
    }

    /**
     * Steps the safe level into flat pools (step-pool streams). Each pool sits at the lowest safe
     * level inside it, so the ground at its lip holds it, and its upstream end is cut into the
     * slope by however much the safe level falls along it. So a pool's length comes from the room
     * the slope gives it (the user's idea): it may fall 2.5-5 blocks along its length, so on gentle
     * ground pools run long (up to ~48 blocks) and on steep ground they are short, a staircase of
     * small falls instead of long flat boxes cut deep into the hillside. Lengths are random within
     * that room. A cliff, where the safe level drops further than that in a block, stays one fall.
     * A lake at the upstream node holds its level until the river is out of it.
     */
    private static void buildPools(Edge e, double l0) {
        int last = e.arc.length - 1;
        double total = e.arc[last];
        int n = Math.max(2, (int) Math.ceil(total));
        double[] lv = new double[n + 1];
        for (int k = 0; k <= n; k++) lv[k] = safeAt(e, k * total / n);
        float[] starts = new float[16], levels = new float[16], fallR = new float[16], fallH = new float[16];
        int count = 0, start = 0;
        double step = total / n;
        // Each pool draws its own length and drop, so a long descent isn't a run of identical steps.
        long seed = GradientNoise.hash(0x9001L, (int) Math.floor(e.x[0]), (int) Math.floor(e.z[0]));
        int hold = 0;
        // No fall starts inside the last one's bowl: two bowls overlapping at different levels
        // stood a wall of water between them (one at 174 against one at 132 under a 420-block fall).
        int bowlUntil = 0;
        if (e.lake != null) {
            while (hold < n && e.lake.field(pointAt(e, hold * total / n)) > 0.3) hold++;
        }
        while (start < n) {
            int end;
            double level;
            double fr = 0, fh = 0;
            // A waterfall (the user: "waterfalls are awesome, I have not seen many"): where the course
            // drops at least FALL_MIN within FALL_RUN of the last pool's lip, it drops in one fall into
            // an amphitheatre instead of stepping down the slope. The pool below the fall runs on far
            // enough to hold the whole bowl.
            int steep = start;
            if (count > 0 && start * step > 15) {
                while (steep < n - 3 && (steep - start) * step < FALL_RUN && lv[steep] - lv[steep + 3] > 3 * 0.35 * step) steep++;
            }
            double fallDrop = count > 0 ? levels[count - 1] - lv[steep] : 0;
            double roll = (GradientNoise.hash(seed, count, 7) >>> 11) * 0x1.0p-53;
            int fallEnd = -1;
            double tryR = 0;
            if (fallDrop >= FALL_MIN && roll < FALL_CHANCE && !(start == 0 && hold > 0) && start >= bowlUntil) {
                // On a steep mountainside the bowl shrinks until the pool below it sits no more than
                // FALL_MAX under the lip; where even the smallest bowl can't, no fall (a sheer face:
                // a bowl there would be a narrow shaft hundreds of blocks deep).
                tryR = fallRadius((steep - start) * step, fallDrop);
                fallEnd = fallEnd(lv, n, start, steep, step, tryR);
                while (tryR > 9 && levels[count - 1] - lv[Math.min(n, fallEnd) - 1] > FALL_MAX) {
                    tryR = Math.max(9, tryR - 3);
                    fallEnd = fallEnd(lv, n, start, steep, step, tryR);
                }
                if (fallEnd >= n - 4 || levels[count - 1] - lv[fallEnd - 1] > FALL_MAX) fallEnd = -1;
            }
            // A sheer face too tall for a bowl at its lip (Angel Falls): leave the face as it is and
            // let the river pour off the lip down it; the plunge pool and its amphitheatre sit at the foot.
            // The face is a gap in the pools (its level NaN): no channel, no water of its own.
            int foot = -1;
            if (fallEnd < 0 && fallDrop >= 40 && roll < FALL_CHANCE && !(start == 0 && hold > 0) && start >= bowlUntil
                    && fallDrop / Math.max(1, (steep - start) * step) >= 2.5) {
                tryR = Math.min(28, 9 + 0.12 * fallDrop);
                int footEnd = fallEnd(lv, n, steep, steep, step, tryR);
                if (footEnd < n - 4 && steep > start) {
                    foot = steep;
                    fallEnd = footEnd;
                }
            }
            if (foot > 0) {
                if (count + 1 >= starts.length) {
                    starts = Arrays.copyOf(starts, starts.length * 2);
                    levels = Arrays.copyOf(levels, levels.length * 2);
                    fallR = Arrays.copyOf(fallR, fallR.length * 2);
                    fallH = Arrays.copyOf(fallH, fallH.length * 2);
                }
                starts[count] = (float) (start * total / n);
                levels[count] = Float.NaN; // the face
                count++;
                start = foot;
            }
            if (start == 0 && hold > 0) {
                end = hold;
                level = l0;
            } else if (fallEnd > 0) {
                end = fallEnd;
                level = lv[end - 1];
                // A free fall (off a sheer face, 100-400 tall) lands in a wide plunge pool, not an
                // amphitheatre: a 28-block bowl cut sheer into the face's skirt was lost under it, and
                // where several stood side by side they left thin pillars and stepped blocks between them
                // (the user: "cascading pools near plateaus are very wonky").
                fr = foot > 0 ? 0 : tryR;
                fh = (foot > 0 ? levels[count - 2] : levels[count - 1]) - level;
            } else {
                int look = Math.min(n, start + 12);
                double grade = Math.max(0, (lv[start] - lv[look]) / Math.max(1e-6, (look - start) * total / n));
                double u1 = (GradientNoise.hash(seed, count, 1) >>> 11) * 0x1.0p-53, u2 = (GradientNoise.hash(seed, count, 2) >>> 11) * 0x1.0p-53;
                double budget = 2.5 + 2.5 * u1; // how far the pool's upstream end may be cut into the slope
                double room = Math.min(48, budget / Math.max(0.03, grade));
                int maxLen = Math.max(1, (int) Math.ceil(Math.max(3, room * (0.55 + 0.9 * u2)) * n / total));
                end = start + 1;
                while (end < n && end - start < maxLen && lv[end] >= lv[start] - budget) end++;
                level = end >= n ? lv[n] : lv[end - 1];
            }
            if (count == starts.length) {
                starts = Arrays.copyOf(starts, count * 2);
                levels = Arrays.copyOf(levels, count * 2);
                fallR = Arrays.copyOf(fallR, count * 2);
                fallH = Arrays.copyOf(fallH, count * 2);
            }
            starts[count] = (float) (start * total / n);
            levels[count] = (float) level;
            fallR[count] = (float) fr;
            fallH[count] = (float) fh;
            if (fr > 0 || foot > 0 && tryR > 0) bowlUntil = start + (int) Math.ceil(BOWL_REACH * Math.max(fr, tryR) / step) + 2;
            count++;
            start = end;
        }
        e.poolStart = Arrays.copyOf(starts, count);
        e.poolLevel = Arrays.copyOf(levels, count);
        e.fallR = Arrays.copyOf(fallR, count);
        e.fallH = Arrays.copyOf(fallH, count);
    }

    /** Waterfalls: at least this tall, over at most this run of steep course, on this share of the chances. */
    private static final double FALL_MIN = 10, FALL_RUN = 40, FALL_CHANCE = 0.8, FALL_MAX = 90;
    /** How far past the lip a fall's bowl reaches, in radii (centre 0.45, stretched 1.1, lobes up to +30%). */
    private static final double BOWL_REACH = 0.45 + 1.1 * 1.3 + 0.1;

    /** Where the pool below a fall ends: past the steep run, and far enough on to hold its whole bowl. */
    private static int fallEnd(double[] lv, int n, int start, int steep, double step, double radius) {
        // Past the steep run, and on toward the bowl's far rim, but never more than a few blocks
        // under the foot of the fall: where the land keeps falling away below it, the pool would sink
        // into a trench (it did, 60 deep). A bowl reaching past its pool spills out as a cascade.
        int end = Math.max(steep + 1, start + 3), reach = start + (int) Math.ceil(BOWL_REACH * radius / step);
        while (end < n && end < reach && lv[end] >= lv[steep] - 4) end++;
        return end;
    }

    /** A fall's amphitheatre: wide enough for the steep run it replaces and for its height. */
    private static double fallRadius(double run, double height) {
        return Math.max(9, Math.min(28, 4 + 0.35 * run + 0.2 * height));
    }

    private static double safeAt(Edge e, double a) {
        int s = segmentAt(e, a);
        double len = e.arc[s + 1] - e.arc[s];
        return lerp(len > 1e-6 ? Math.max(0, Math.min(1, (a - e.arc[s]) / len)) : 0, e.safe[s], e.safe[s + 1]);
    }

    private static double[] pointAt(Edge e, double a) {
        int s = segmentAt(e, a);
        double len = e.arc[s + 1] - e.arc[s];
        double u = len > 1e-6 ? Math.max(0, Math.min(1, (a - e.arc[s]) / len)) : 0;
        return new double[] {lerp(u, e.x[s], e.x[s + 1]), lerp(u, e.z[s], e.z[s + 1])};
    }

    private static int segmentAt(Edge e, double a) {
        int s = 0;
        while (s < e.arc.length - 2 && e.arc[s + 1] < a) s++;
        return s;
    }

    /**
     * Valley width beyond the flood plain: 17 blocks for a stream, 90 for the biggest rivers.
     * (TerraForged's 80-200 suits rivers kilometres apart; ours are a few hundred blocks apart,
     * and wide valleys cut away each other's banks.)
     */
    /** How much wider a valley may grow where it cuts deep (see riverColumn). */
    private static final double MAX_WIDEN = 80;

    /**
     * How far out a river's valley reaches past its banks, from its catchment (read back from its
     * width). Real valleys reach far: in the Appalachians the ground is still rising 1 km from a
     * stream draining 5 km2. 75 for a fresh creek, 105 at 1 km2, 160 at 10, 260 at 100; where two
     * valleys meet, the ground between them is left as the ridge that parts their watersheds.
     */
    private static double valleyWidth(double half) {
        double km2 = Math.pow(half / (WIDTH_SCALE * 1.35), 1 / 0.47);
        return Math.min(260, 50 + 55 * Math.pow(km2, 0.3));
    }

    // ---- lakes

    /**
     * A lake that fills its basin: a priority flood from the node over an 8-block grid finds the
     * lowest point of the basin's rim, and the lake fills to 1.5 blocks under it. Its shore is a
     * smooth field over the flooded cells, so it follows the land instead of being a disc.
     */
    static final class Lake {
        double x0, z0, level, depth;
        int size;
        float[] fill;

        /** 0..1: how far into the lake (x, z) is; the shore is at 0.5. */
        double field(double[] p) {
            return field(p[0], p[1]);
        }

        double field(double x, double z) {
            double gx = (x - x0) / LAKE_CELL, gz = (z - z0) / LAKE_CELL;
            int i = GradientNoise.floor(gx), j = GradientNoise.floor(gz);
            if (i < 0 || j < 0 || i >= size - 1 || j >= size - 1) return 0;
            double fx = gx - i, fz = gz - j;
            return lerp(fz, lerp(fx, fill[i + j * size], fill[i + 1 + j * size]),
                    lerp(fx, fill[i + (j + 1) * size], fill[i + 1 + (j + 1) * size]));
        }
    }

    /** The lake at a node: in a closed basin, or where a river passes through a hollow. Lowlands have none. */
    private Lake lakeAt(int i, int j) {
        Routes r = routes(i, j);
        int k = local(i, j);
        Object o = r.lakes.get(k);
        if (o == NO_LAKE) return null;
        if (o instanceof Lake l) return l;
        Lake lake = null;
        byte d = downOf(i, j);
        double flow = catchment(memo(i, j));
        if (d != OCEAN && flow >= SOURCE && !lowland(i, j) && (d == SINK || flow >= 2 * SOURCE && hollow(i, j))) {
            lake = buildLake(i, j);
        }
        r.lakes.set(k, lake != null ? lake : NO_LAKE);
        return lake;
    }

    private Lake buildLake(int i, int j) {
        double[] p = position(i, j);
        int r = LAKE_RADIUS, n = 2 * r + 1;
        double x0 = p[0] - r * LAKE_CELL, z0 = p[1] - r * LAKE_CELL;
        double[] h = new double[n * n];
        for (int b = 0; b < n; b++) {
            for (int a = 0; a < n; a++) h[a + b * n] = terrain.sample(x0 + a * LAKE_CELL, z0 + b * LAKE_CELL).height();
        }
        boolean[] seen = new boolean[n * n];
        Heap heap = new Heap(256);
        int centre = r + r * n;
        heap.push(h[centre], centre);
        seen[centre] = true;
        double level = Double.NEGATIVE_INFINITY, spill = Double.NaN;
        List<Integer> basin = new ArrayList<>();
        while (!heap.isEmpty()) {
            double key = heap.topKey();
            int c = heap.pop();
            int a = c % n, b = c / n;
            boolean edge = a == 0 || b == 0 || a == n - 1 || b == n - 1;
            if (key < level - 0.5 || edge) {
                // Over the rim, it spills at the highest point so far; out of reach, at most there.
                spill = edge ? Math.min(level, key) : level;
                break;
            }
            level = Math.max(level, key);
            basin.add(c);
            for (int k = 0; k < 8; k += 2) {
                int na = a + DX[k], nb = b + DZ[k];
                int q = na + nb * n;
                if (seen[q]) continue;
                seen[q] = true;
                heap.push(h[q], q);
            }
        }
        if (Double.isNaN(spill)) return null;
        double lakeLevel = Math.max(SEA, spill - 1.5);
        Lake lake = new Lake();
        lake.x0 = x0;
        lake.z0 = z0;
        lake.size = n;
        int cells = 0;
        double bottom = Double.MAX_VALUE;
        boolean hollow = true;
        for (int pass = 0; pass < 4; pass++) {
            float[] fill = new float[n * n];
            cells = 0;
            bottom = Double.MAX_VALUE;
            for (int c : basin) {
                if (h[c] < lakeLevel) {
                    fill[c] = 1;
                    cells++;
                    bottom = Math.min(bottom, h[c]);
                }
            }
            lake.fill = fill;
            if (pass == 0) hollow = cells >= 4 && lakeLevel - bottom >= 3;
            // The rim, block by block: the 8-block flood can miss a lane lower than its samples, and
            // the shore field reaches a little past the flooded cells. Wherever the outer edge of
            // the field (where the shore's ragged noise can end the water) has ground under the
            // level, the water would stand over the land beyond: the lake fills only to under it.
            double rim = Double.MAX_VALUE;
            for (double z = z0; z <= z0 + (n - 1) * LAKE_CELL; z += 2) {
                for (double x = x0; x <= x0 + (n - 1) * LAKE_CELL; x += 2) {
                    double f = lake.field(x, z);
                    if (f > 0.35) continue; // the shore band too: an outlet's banks there held the lake over its river
                    boolean near = f > 0;
                    for (int k = 0; k < 8 && !near; k += 2) near = lake.field(x + 4 * DX[k], z + 4 * DZ[k]) > 0;
                    if (near) rim = Math.min(rim, terrain.sample(x, z).height());
                }
            }
            if (rim >= lakeLevel + 1) break;
            lakeLevel = rim - 1;
        }
        // A real hollow, not a dip in a plain: there the lake would sit level with the land and
        // hold the rivers through it up at the ground. (Judged at its first level: a lake lowered
        // to its rim keeps its bowl, cut under the new level.)
        if (!hollow || cells < 4 || lakeLevel - bottom < 0.5 || lakeLevel < SEA) return null;
        lake.level = lakeLevel;
        lake.depth = 2 + Math.min(8, 0.5 * Math.sqrt(cells));
        return lake;
    }

    /** True when the node is lower than all eight around it. */
    private boolean hollow(int i, int j) {
        double t = nodes(i, j).t[local(i, j)];
        for (int e = 0; e < 8; e++) {
            if (nodes(i + DX[e], j + DZ[e]).t[local(i + DX[e], j + DZ[e])] <= t) return false;
        }
        return true;
    }

    // ---- columns

    /**
     * Shapes the ground at (x, z) around every river and lake nearby and reports the water over
     * it. Ground is only ever lowered (overlapping rivers and lakes combine in any order): to a
     * river's bed, a flood plain just over its water, then a valley that blends back into the
     * land (TerraForged's way); a lake's bowl and shore. Nothing is built up to hold water: where
     * one body's valley would cut another's bank, the cut stops at that bank (still under the
     * land). Where a lake covers a column its level wins; elsewhere the lowest river water does,
     * so a tributary steps down into its main river. Returns the new height (never higher than {@code h}).
     */
    public double carve(double x, double z, double h, Carve out) {
        out.water = Double.NaN;
        out.lake = false;
        out.near = false;
        out.spill = false;
        out.fjord = false;
        out.flowX = 0;
        out.flowZ = 0;
        out.lower = h;
        out.count = 0;
        int ci = GradientNoise.floor(x / STEP), cj = GradientNoise.floor(z / STEP);
        for (int dj = -3; dj <= 3; dj++) {
            for (int di = -3; di <= 3; di++) {
                int i = ci + di, j = cj + dj;
                byte d = downOf(i, j);
                if (d == OCEAN) continue;
                long m = memo(i, j);
                if (catchment(m) < SOURCE) continue;
                Lake lake = lakeAt(i, j);
                if (lake != null) lakeColumn(lake, x, z, h, out);
                if (d >= 0) riverColumn(edge(i, j), x, z, h, out);
            }
        }
        if (out.settle()) return out.lower;
        // Dry here: no other body's valley may cut a river's or lake's bank below that body's own
        // shape, or its water would stand over the cut. (Never above the land: every shape is under h.)
        double guard = out.lower;
        for (int c = 0; c < out.count; c++) {
            // Near a bank only: further out it held whole valley walls (a fjord's) up off their cut.
            if (out.cBeyond[c] < 12) guard = Math.max(guard, out.cLower[c] - GUARD_SLOPE * out.cBeyond[c]);
        }
        if (guard > out.lower) {
            out.lower = guard;
            out.settle(); // back at a river's own bed, its water returns
        }
        return out.lower;
    }

    /** How steeply ground beyond a guarded bank may fall away to another body's cut (blocks per block). */
    private static final double GUARD_SLOPE = 1.0;

    /**
     * A waterfall on the river network, for the detail pass that dresses it.
     *
     * @param lipX   where the river pours off
     * @param cx     the centre of the amphitheatre below
     * @param ux     the direction the river leaves it (unit)
     * @param radius the amphitheatre's radius
     * @param level  the plunge pool's water
     * @param lip    the water at the lip
     */
    public record Fall(double lipX, double lipZ, double cx, double cz, double ux, double uz, double radius, double level, double lip) {
    }

    /** The falls whose amphitheatres reach the box [x0, x1] x [z0, z1]. */
    public void falls(double x0, double z0, double x1, double z1, java.util.List<Fall> out) {
        int i0 = GradientNoise.floor((x0 - 200) / STEP), i1 = GradientNoise.floor((x1 + 200) / STEP);
        int j0 = GradientNoise.floor((z0 - 200) / STEP), j1 = GradientNoise.floor((z1 + 200) / STEP);
        for (int j = j0; j <= j1; j++) {
            for (int i = i0; i <= i1; i++) {
                byte d = downOf(i, j);
                if (d < 0 || catchment(memo(i, j)) < SOURCE) continue;
                Edge e = edge(i, j);
                if (e.fallR == null) continue;
                double total = e.arc[e.arc.length - 1];
                for (int f = 1; f < e.fallR.length; f++) {
                    double r = e.fallR[f];
                    if (r <= 0 || f < 2 && Float.isNaN(e.poolLevel[f - 1])) continue;
                    double lipA = e.poolStart[f];
                    double[] lip = pointAt(e, lipA), c = pointAt(e, Math.min(total, lipA + 0.45 * r));
                    double[] ahead = pointAt(e, Math.min(total, lipA + 0.45 * r + 4)), behind = pointAt(e, Math.max(0, lipA + 0.45 * r - 4));
                    double ux = ahead[0] - behind[0], uz = ahead[1] - behind[1], ul = Math.max(1e-6, Math.hypot(ux, uz));
                    double reach = 1.6 * r + 4;
                    if (c[0] + reach < x0 || c[0] - reach > x1 || c[1] + reach < z0 || c[1] - reach > z1) continue;
                    boolean free = Float.isNaN(e.poolLevel[f - 1]); // a free fall: the face is pool f - 1
                    if (free) lip = pointAt(e, e.poolStart[f - 1]);
                    out.add(new Fall(lip[0], lip[1], c[0], c[1], ux / ul, uz / ul, r, e.poolLevel[f], e.poolLevel[free ? f - 2 : f - 1]));
                }
            }
        }
    }

    private void riverColumn(Edge e, double x, double z, double h, Carve out) {
        if (x < e.minX || x > e.maxX || z < e.minZ || z > e.maxZ) return;
        for (double[] o : e.oxbows) {
            // An oxbow: a crescent of still water, its horns pointing back at the river.
            double dx = x - o[0], dz = z - o[1], d = Math.hypot(dx, dz);
            if (Math.abs(d - o[2]) > o[3] + 3) continue;
            double ang = Math.abs(Math.IEEEremainder(Math.atan2(dz, dx) - o[4], Math.PI * 2));
            double horn = 1 - smoothstep(1.4, 1.9, ang); // 0 past the horns' tips
            double w = o[3] * horn * (1 + 0.15 * GradientNoise.noise2(seed ^ 0x0B0CL, x / 8.0, z / 8.0));
            double q = Math.abs(d - o[2]) / Math.max(0.1, w);
            if (w < 0.5 || q > 1.6) continue;
            double lower = q < 1 ? SEA - 0.4 - 1.6 * (1 - q * q) : SEA + 0.6 + 1.5 * (q - 1);
            lower = Math.min(h, lower);
            out.offer(lower, lower < SEA ? SEA : Double.NaN, true, 0, 0, false, q < 1.4 ? 0 : Double.POSITIVE_INFINITY);
            out.near = true;
            out.lower = Math.min(out.lower, lower);
        }
        int n = e.x.length;
        double bestD2 = Double.MAX_VALUE, bestU = 0, side = 1;
        int bestS = 0;
        for (int s = 0; s + 1 < n; s++) {
            double ax = e.x[s], az = e.z[s], vx = e.x[s + 1] - ax, vz = e.z[s + 1] - az;
            double len2 = vx * vx + vz * vz;
            double u = len2 > 1e-9 ? Math.max(0, Math.min(1, ((x - ax) * vx + (z - az) * vz) / len2)) : 0;
            double dx = x - (ax + u * vx), dz = z - (az + u * vz);
            double d2 = dx * dx + dz * dz;
            if (d2 < bestD2) {
                bestD2 = d2;
                bestS = s;
                bestU = u;
                side = vx * dz - vz * dx >= 0 ? 1 : -1;
            }
        }
        double total = e.arc[n - 1];
        double a = lerp(bestU, e.arc[bestS], e.arc[bestS + 1]);
        double t = total > 1e-6 ? a / total : 0;
        double half = lerp(smoothstep(0.6, 1, t), e.half0, e.half1);
        if (e.source) half *= 0.35 + 0.65 * smoothstep(0, 0.4, t); // a spring widens into its stream
        if (e.estuary) half *= 1 + 2.2 * smoothstep(0.35, 1, t); // an estuary: the mouth flares into a funnel
        // Natural irregularity (user: "all bodies of water are WAY too uniform"): the channel swells
        // and pinches, pool edges wander instead of crossing it square, and the bed undulates.
        half *= 1 + 0.28 * GradientNoise.noise2(seed ^ 0x3A1L, x / 48.0, z / 48.0) + 0.12 * GradientNoise.noise2(seed ^ 0x3A2L, x / 14.0, z / 14.0);
        double safe = lerp(bestU, e.safe[bestS], e.safe[bestS + 1]);
        double off = Math.sqrt(bestD2), off0 = off;
        if (off - half > valleyWidth(half) + MAX_WIDEN + 2 * half + 8 && !(e.fjord && off < 380) && !(e.delta && off < e.deltaSpread + 80)) return;

        if (e.delta && t > 0.25) {
            // A delta: the river splits into channels that fan apart toward the sea, each wandering a
            // little, with flat marsh islands just over the water between them. Past the fan's edge
            // the river's own valley carries on as usual.
            double fan = smoothstep(0.25, 1, t), spread = e.deltaSpread * Math.pow(fan, 1.3), signed = side * off0;
            int channels = e.deltaChannels;
            double chHalf = Math.max(0.85, e.half1 * (0.75 - 0.3 * fan)), best = Double.MAX_VALUE;
            for (int k = 0; k < channels; k++) {
                double frac = 2.0 * k / (channels - 1) - 1;
                double wob = 10 * fan * GradientNoise.noise2(seed ^ (0xDE17BL + k), a / 45.0, k * 3.7);
                best = Math.min(best, Math.abs(signed - (frac * spread + wob)));
            }
            double plainHalf = spread + 3 * chHalf + 25;
            if (Math.abs(signed) < plainHalf) {
                double lower;
                if (best < chHalf) {
                    double q = best / chHalf;
                    lower = SEA - (1.2 + 0.15 * chHalf) * (1 - q * q); // a channel's bed
                } else {
                    lower = SEA + 0.6 + 1.2 * smoothstep(0, 6, best - chHalf) + 0.6 * GradientNoise.noise2(seed ^ 0xDE17CL, x / 20.0, z / 20.0);
                }
                lower = Math.min(h, lerp(smoothstep(plainHalf - 22, plainHalf, Math.abs(signed)), lower, h));
                double fx = e.x[bestS + 1] - e.x[bestS], fz = e.z[bestS + 1] - e.z[bestS], fl = Math.max(1e-6, Math.hypot(fx, fz));
                out.offer(lower, best < chHalf && lower < SEA ? SEA : Double.NaN, false, fx / fl, fz / fl, false, Math.max(0, Math.abs(signed) - plainHalf));
                out.near = true;
                out.lower = Math.min(out.lower, lower);
                return;
            }
        }

        if (e.fjord) {
            // A glacial trough: wide and flat-floored, 18-36 under the sea, walls standing sheer to the
            // mountainsides. It widens toward its mouth.
            // Width and depth vary in space, not per edge (per edge they restarted at every node: beads).
            double u = 0.5 + 0.5 * GradientNoise.noise2(seed ^ 0xF40DL, x / 700.0, z / 700.0);
            double trough = (e.loch ? 11 + 12 * u + 0.5 * half : 26 + 22 * u + 0.6 * half) * (1 + 0.12 * GradientNoise.noise2(seed ^ 0xF40EL, x / 90.0, z / 90.0));
            double floor = AtlasCore.SEA_LEVEL - (e.loch ? 7 + 9 * u : 18 + 18 * u) + 3 * GradientNoise.noise2(seed ^ 0xF40FL, x / 40.0, z / 40.0);
            // Widest at the sea, narrowing as it winds inland: up to 2.5 times as wide at the mouth.
            trough *= 1 + 1.5 * (1 - smoothstep(0, 9, e.seaSteps + 1 - t));
            if (e.fjordHead) {
                // Toward its head the trough narrows to a third and rises to a shallow end, where its
                // river comes in (it stopped square, the full width, like a canal).
                double head = smoothstep(0.0, 0.75, t);
                trough *= 0.35 + 0.65 * head;
                floor = lerp(head, AtlasCore.SEA_LEVEL - 2.5, floor);
            }
            double q = off0 / trough, lower;
            if (q < 1) {
                lower = lerp(Math.pow(q, 4), floor, SEA - 0.5);
            } else {
                // Steep, not plumb: the wall leans back about 70-80 degrees (more where it's tall), its
                // lean varying along the fjord, with ledges where the slope eases.
                double lean = (e.loch ? 0.5 + 0.4 * (0.5 + 0.5 * GradientNoise.noise2(seed ^ 0xF410L, x / 160.0, z / 160.0))
                        : 0.15 + 0.15 * (0.5 + 0.5 * GradientNoise.noise2(seed ^ 0xF410L, x / 160.0, z / 160.0))) * Math.max(0, h - SEA);
                double wall = (off0 - trough) / (3 + lean);
                double ledges = 0.08 * Math.sin(wall * 9 + 3 * GradientNoise.noise2(seed ^ 0xF411L, x / 30.0, z / 30.0));
                lower = wall < 1 ? lerp(Math.max(0, Math.min(1, Math.pow(wall, 0.8) + ledges)), SEA + 1, h) : h;
            }
            lower = Math.min(h, lower);
            double fx = e.x[bestS + 1] - e.x[bestS], fz = e.z[bestS + 1] - e.z[bestS], fl = Math.max(1e-6, Math.hypot(fx, fz));
            int c = out.count;
            out.offer(lower, q < 1 && lower < SEA ? SEA : Double.NaN, false, 0, 0, false, Math.max(0, off0 - trough - 6)); // still: the sea
            if (c < out.count) out.cFjord[c] = true;
            if (off0 - trough < 12) out.near = true;
            out.lower = Math.min(out.lower, lower);
            return;
        }
        double water, surface, depth = e.depth;
        boolean spill = false;
        double guardHalf = half; // the bank's guard ignores a lip's pinch, or another valley cuts a slot beside the fall
        if (e.lowland) {
            water = SEA;
            surface = SEA;
        } else {
            double grade = Math.max(0, (safe - safeAt(e, Math.min(total, a + 8))) / 8);
            half *= lerp(smoothstep(0.1, 0.6, grade), 1, 0.65); // steep reaches run narrower
            guardHalf = half;
            int k = 0;
            int count = e.poolStart.length;
            // The wander stays under a third of the reach's shortest pool, so it never skips a pool
            // (a column two pools down beside one at the upper level would be a wall of water).
            double shortest = total;
            for (int p = 0; p < count; p++) {
                shortest = Math.min(shortest, (p + 1 < count ? e.poolStart[p + 1] : total) - e.poolStart[p]);
            }
            double wander = Math.min(4, shortest / 3);
            for (int f = 1; f < count; f++) {
                if (e.fallR[f] > 0 && Math.abs(a - e.poolStart[f]) < 10) wander = 0;
            }
            double ap = Math.max(0, Math.min(total, a + wander * GradientNoise.noise2(seed ^ 0x3A3L, x / 10.0, z / 10.0)));
            while (k + 1 < count && e.poolStart[k + 1] <= ap) k++;
            double into = ap - e.poolStart[k], len = (k + 1 < count ? e.poolStart[k + 1] : total) - e.poolStart[k];
            for (int f = 1; f < count; f++) {
                if (e.fallR[f] <= 0 || Math.abs(a - e.poolStart[f]) > 1.6 * e.fallR[f] + 4) continue;
                // Below the fall's lip, or beside the river just above it past its banks, so the
                // bowl curves back round the lip and the river pours off a spout of rock.
                // Only its own pool: a later, lower pool inside the rim keeps its own level.
                boolean below = k == f, beside = k == f - 1 && off0 > half + 2 + 0.4 * half + 1.5;
                if ((below || beside) && fallBowl(e, f, x, z, h, off0, half, bestS, out)) return;
            }
            water = e.poolLevel[k];
            if (Double.isNaN(water)) {
                // The face of a free fall: left as it is, the river pouring down it from the lip. Where
                // the ground still stands over the pool above (a crest before the drop), its channel
                // runs on through at its level, so the water reaches the edge and pours off.
                double above = e.poolLevel[k - 1];
                if (h <= above - 0.5) {
                    if (off0 - half < 12) out.near = true;
                    return;
                }
                water = above;
            }
            surface = water;
            depth = poolDepth(e, k, into, len);
            double next = k + 1 < count ? e.poolLevel[k + 1] : Double.NaN;
            if (Double.isNaN(next) && k + 2 < count) next = e.poolLevel[k + 2]; // past a free fall's face
            double dropOut = k + 1 < count ? water - next : 0;
            double dropIn = k > 0 ? e.poolLevel[k - 1] - water : 0;
            // A fall pours from a lip pinched to a chute into a plunge pool much wider than the stream.
            if (dropOut >= 6) half *= 1 - 0.55 * smoothstep(len - 6, len, into);
            if (dropIn >= 6) half *= 1 - 0.55 * (1 - smoothstep(0, 3, into));
            half += plungeWidth(e, k, into, half);
            // Each pool its own shape (user: "the steps still look like ovals"): it bows out to one
            // side, one bank lies further out than the other, and its edge is ragged close to.
            long ph = GradientNoise.hash(seed ^ 0x3A7L, (int) Math.floor(e.x[0]) * 31 + k, (int) Math.floor(e.z[0]));
            double u1 = ((ph >>> 11) * 0x1.0p-53) - 0.5, u2 = ((GradientNoise.mix(ph) >>> 11) * 0x1.0p-53) - 0.5;
            double bow = 1.1 * u1 * half * Math.sin(Math.PI * Math.max(0, Math.min(1, into / len)));
            double signed = side * off - bow;
            off = Math.abs(signed) / (1 + 0.6 * u2 * Math.signum(signed));
            half *= 1 + 0.2 * GradientNoise.noise2(seed ^ 0x3A8L, x / 6.5, z / 6.5);
            if (k > 0 && into < 0.25 && !Float.isNaN(e.poolLevel[k - 1])) {
                double prevLen = e.poolStart[k] - e.poolStart[k - 1];
                double r = smoothstep(0, 0.25, into);
                surface = lerp(r, e.poolLevel[k - 1], water);
                depth = lerp(r, poolDepth(e, k - 1, prevLen, prevLen), depth);
            }
            spill = dropOut >= 1 && into > len - 1.5;
            double wetHalf = 0.75 * half; // the bed rises out of the water a little past this
            if (off >= wetHalf) {
                // Beside a fall the channel's dry edge and banks keep the upper pool's level for as far
                // downstream as they are from the water, so the cut is a notch that never opens
                // under the upper pool.
                double from = ap - (off - wetHalf) - 2;
                int kb = k;
                while (kb > 0 && e.poolStart[kb] > from && e.fallR[kb] <= 0) kb--;
                if (!Float.isNaN(e.poolLevel[kb])) surface = Math.max(surface, e.poolLevel[kb]);
            }
        }

        depth *= 1 + 0.3 * GradientNoise.noise2(seed ^ 0x3A4L, x / 9.0, z / 9.0);

        // Bed, flood plain just over the water, then a valley blending back into the land.
        double plain = surface + 1.5;
        double lower;
        if (off < half) {
            double q = off / half;
            double bed = surface - depth * (1 - q * q);
            lower = bed + (plain - bed) * smoothstep(0.75, 1, q);
        } else {
            double over = off - half, bank = 2 + 0.4 * half;
            if (over < bank) {
                lower = plain + 0.1 * over;
            } else {
                // The valley widens with how deep it cuts, so its sides slope (about 30 degrees at
                // most) instead of standing sheer where a river drops fast past high ground
                // (round 3c: "on the right of the drop the valley is super steep and unnatural").
                double floor = plain + 0.1 * bank;
                double width = Math.min(valleyWidth(half) + MAX_WIDEN, Math.max(valleyWidth(half), 1.7 * (h - floor)));
                double s = Math.min(1, (over - bank) / width);
                // A lowland river lies on a flat floodplain under bluffs; elsewhere the valley's sides are
                // steepest just over the stream and ease off onto the ridges (real hill valleys).
                double rise = e.lowland ? Math.pow(s, 1.5) : 1 - Math.pow(1 - s, 1.6);
                lower = floor >= h ? h : lerp(rise, floor, h);
            }
        }
        if (off < half * 0.85 && half >= 5 && !Double.isNaN(water)) {
            // River islands and braids (docs/M3_M4_PLAN.md, phase 4): in a wide river, the odd long
            // island mid-channel; where a region's rivers braid, many low gravel bars the water runs
            // between. Drawn out along the flow (its local heading), so they read as bars, not blobs.
            double fx = e.x[bestS + 1] - e.x[bestS], fz = e.z[bestS + 1] - e.z[bestS], fl = Math.max(1e-6, Math.hypot(fx, fz));
            fx /= fl;
            fz /= fl;
            double u = x * fx + z * fz, v = -x * fz + z * fx;
            double braided = smoothstep(0.2, 0.35, GradientNoise.noise2(seed ^ 0xB4A1L, x / 2600.0, z / 2600.0));
            double bar = GradientNoise.noise2(seed ^ 0xB4A2L, u / (5 * half), v / (0.9 * half));
            double threshold = lerp(braided, half >= 9 ? 0.55 : 2, 0.12);
            double edgeFade = 1 - smoothstep(0.55, 0.85, off / half); // islands keep clear of the banks
            if (bar > threshold && edgeFade > 0) {
                double top = water + 0.35 + (1 - braided) * 2.5 * (bar - threshold) / (1 - threshold);
                lower = Math.max(lower, lerp(edgeFade, lower, top));
            }
        }
        lower = Math.min(h, lower);
        double gh = Math.max(half, guardHalf);
        double beyond = Math.max(0, off - (gh + 2 + 0.4 * gh)); // past the channel and flood plain
        if (off < half && lower < water) {
            double fx = e.x[bestS + 1] - e.x[bestS], fz = e.z[bestS + 1] - e.z[bestS], fl = Math.max(1e-6, Math.hypot(fx, fz));
            fx /= fl;
            fz /= fl;
            // Toward the banks the current also runs in toward the middle (the user: so swimmers and
            // boats aren't pinned against the bank), turning to the inward diagonal by the edge.
            double cx = e.x[bestS] + bestU * (e.x[bestS + 1] - e.x[bestS]), cz = e.z[bestS] + bestU * (e.z[bestS + 1] - e.z[bestS]);
            double ix = cx - x, iz = cz - z, il = Math.hypot(ix, iz);
            if (il > 0.5) {
                double pull = smoothstep(0.3, 0.75, off / half);
                fx += pull * ix / il;
                fz += pull * iz / il;
                fl = Math.hypot(fx, fz);
                fx /= fl;
                fz /= fl;
            }
            out.offer(lower, water, false, fx, fz, spill, beyond);
        } else {
            out.offer(lower, Double.NaN, false, 0, 0, false, beyond);
        }
        if (off - half < 12) out.near = true;
        out.lower = Math.min(out.lower, lower);
    }

    /**
     * A waterfall's amphitheatre (the user's Skógafoss and Latourell): a round bowl with sheer walls
     * (the per-block cut keeps them sheer) and a lobed, ragged rim, centred a little downstream of
     * the lip. A deep plunge pool lies under the fall, a rocky floor a block over the water rings it,
     * and the river runs on out of the bowl's open, downhill side. True if (x, z) is in it.
     */
    private boolean fallBowl(Edge e, int f, double x, double z, double h, double off, double half, int seg, Carve out) {
        double total = e.arc[e.arc.length - 1];
        double lipA = e.poolStart[f], r = e.fallR[f], height = e.fallH[f], level = e.poolLevel[f];
        double[] c = pointAt(e, Math.min(total, lipA + 0.45 * r));
        double[] ahead = pointAt(e, Math.min(total, lipA + 0.45 * r + 4)), behind = pointAt(e, Math.max(0, lipA + 0.45 * r - 4));
        double ux = ahead[0] - behind[0], uz = ahead[1] - behind[1], ul = Math.max(1e-6, Math.hypot(ux, uz));
        ux /= ul;
        uz /= ul;
        double dx = x - c[0], dz = z - c[1];
        // Stretched down the valley: an amphitheatre opening into the gorge the river leaves by.
        double along = dx * ux + dz * uz, across = -dx * uz + dz * ux;
        double d = Math.hypot(along / 1.1, across / 0.85);
        if (d > 1.5 * r) return false;
        long fh = GradientNoise.hash(seed ^ 0xFA11L, (int) Math.floor(e.x[0]) * 31 + f, (int) Math.floor(e.z[0]));
        double p1 = (fh & 0xFFFF) / 65536.0 * Math.PI * 2, p2 = ((fh >>> 16) & 0xFFFF) / 65536.0 * Math.PI * 2;
        double ang = Math.atan2(across, along);
        double rim = r * (1 + 0.16 * Math.sin(3 * ang + p1) + 0.1 * Math.sin(5 * ang + p2))
                * (1 + 0.1 * GradientNoise.noise2(seed ^ 0xFA12L, x / 9.0, z / 9.0) + 0.05 * GradientNoise.noise2(seed ^ 0xFA15L, x / 3.5, z / 3.5));
        if (d >= rim) return false;
        // The plunge pool sits under the fall, not filling the bowl; talus and rock floor round it.
        double[] pc = pointAt(e, Math.min(total, lipA + 0.22 * r));
        double dp = Math.hypot(x - pc[0], z - pc[1]);
        // The pool scales with the stream as well as the fall: a creek's is a few times its width.
        double pr = Math.min(0.42 * r + 2, 3 + 2 * half + 0.08 * height) * (1 + 0.15 * GradientNoise.noise2(seed ^ 0xFA13L, x / 8.0, z / 8.0));
        double lower;
        boolean wet = true;
        if (dp < pr) {
            double q = dp / pr;
            lower = level - 0.6 - Math.min(10, 2.5 + 0.12 * height) * (1 - q * q); // deepest under the fall
        } else if (off < half * 1.1) {
            double q = off / (half * 1.1);
            lower = level - 0.3 - 1.5 * (1 - q * q); // the river leaving the pool
        } else {
            // Rocky floor, rising in talus against the walls.
            double wall = smoothstep(0.7, 1, d / rim);
            lower = level + 0.7 + 0.8 * Math.max(0, GradientNoise.noise2(seed ^ 0xFA14L, x / 4.0, z / 4.0)) + wall * (2 + 0.08 * height);
            wet = false;
        }
        lower = Math.min(h, lower);
        double fx = e.x[seg + 1] - e.x[seg], fz = e.z[seg + 1] - e.z[seg], fl = Math.max(1e-6, Math.hypot(fx, fz));
        out.offer(lower, wet && lower < level ? level : Double.NaN, false, fx / fl, fz / fl, false, 0);
        out.near = true;
        out.lower = Math.min(out.lower, lower);
        return true;
    }

    /** Pool depth: shallow over the sill at a lip, deeper in the plunge pool below a fall. */
    private static double poolDepth(Edge e, int k, double into, double len) {
        double depth = e.depth;
        int count = e.poolLevel.length;
        double dropIn = k > 0 ? e.poolLevel[k - 1] - e.poolLevel[k] : 0;
        double dropOut = k + 1 < count ? e.poolLevel[k] - e.poolLevel[k + 1] : 0;
        if (dropOut >= 2) depth *= 1 - 0.7 * smoothstep(len - 4, len, into);
        if (dropIn >= 3) depth += Math.min(6, 0.3 * dropIn) * smoothstep(0, 2, into) * (1 - smoothstep(6, 18, into));
        return depth;
    }

    /** Below a fall, a plunge pool: a small round lake much wider than the stream, which then drains on. */
    private static double plungeWidth(Edge e, int k, double into, double half) {
        double dropIn = k > 0 ? e.poolLevel[k - 1] - e.poolLevel[k] : 0;
        if (!(dropIn >= 4.5) || e.fallR[k] > 0) return 0; // (NaN on a free fall's face: none)
        double v = ((GradientNoise.hash(0x3A9L, (int) Math.floor(e.x[0]) * 31 + k, (int) Math.floor(e.z[0])) >>> 11) * 0x1.0p-53);
        return Math.min(1.6 * half + 4, 0.8 * dropIn) * (0.7 + 0.5 * v) * smoothstep(0, 3, into) * (1 - smoothstep(4 + 4 * v, 10 + 12 * v, into));
    }

    private void lakeColumn(Lake l, double x, double z, double h, Carve out) {
        double f = l.field(x, z);
        if (f > 0) f += 0.12 * GradientNoise.noise2(seed ^ 0x3A5L, x / 10.0, z / 10.0) + 0.05 * GradientNoise.noise2(seed ^ 0x3A6L, x / 4.0, z / 4.0); // a ragged shore
        if (f <= 0) return;
        double lower;
        if (f >= 0.5) {
            // The bowl: from 1.5 over the water at the shore line down to full depth inside.
            lower = l.level + 1.5 - (l.depth + 1.5) * smoothstep(0.5, 0.95, f);
        } else {
            // The shore blends back into the land.
            double s = smoothstep(0, 1, (0.5 - f) / 0.5);
            lower = lerp(s, l.level + 1.5, Math.max(h, l.level + 1.5));
        }
        lower = Math.min(h, lower);
        // Water wherever the ground inside the field lies under the level, not only inside the
        // shore line: on flat ground its ragged edge otherwise left ground a hair under the level dry.
        out.offer(lower, lower < l.level ? l.level : Double.NaN, true, 0, 0, false, f > 0.25 ? 0 : Double.POSITIVE_INFINITY);
        if (f > 0.05) out.near = true;
        out.lower = Math.min(out.lower, lower);
    }

    /** Every river and lake shaping a column, for debugging. */
    public String explain(double x, double z, double h) {
        StringBuilder sb = new StringBuilder();
        int ci = GradientNoise.floor(x / STEP), cj = GradientNoise.floor(z / STEP);
        for (int dj = -3; dj <= 3; dj++) {
            for (int di = -3; di <= 3; di++) {
                int i = ci + di, j = cj + dj;
                byte d = downOf(i, j);
                if (d == OCEAN || catchment(memo(i, j)) < SOURCE) continue;
                Carve one = new Carve();
                one.lower = h;
                Lake lake = lakeAt(i, j);
                if (lake != null) lakeColumn(lake, x, z, h, one);
                if (d >= 0) riverColumn(edge(i, j), x, z, h, one);
                for (int c = 0; c < one.count; c++) {
                    if (one.cLower[c] < h || !Double.isNaN(one.cWater[c])) {
                        sb.append(String.format("  node %d,%d %s%s: lower %.2f water %.2f%n", i, j, one.cLake[c] ? "lake" : "river",
                                d >= 0 && edge(i, j).lowland ? " (lowland)" : "", one.cLower[c], one.cWater[c]));
                    }
                }
            }
        }
        return sb.toString();
    }

    // ---- lookups

    private static final class Recent {
        static final int SIZE = 64;
        final long[] nodeKeys = new long[SIZE], routeKeys = new long[SIZE];
        final Nodes[] nodes = new Nodes[SIZE];
        final Routes[] routes = new Routes[SIZE];

        Recent() {
            Arrays.fill(nodeKeys, Long.MIN_VALUE);
            Arrays.fill(routeKeys, Long.MIN_VALUE);
        }
    }

    private Nodes nodes(int i, int j) {
        int ti = Math.floorDiv(i, TILE), tj = Math.floorDiv(j, TILE);
        long key = TileCache.key(ti, tj);
        Recent r = recent.get();
        int slot = (int) GradientNoise.mix(key) & (Recent.SIZE - 1);
        if (r.nodeKeys[slot] == key) return r.nodes[slot];
        Nodes n = nodeTiles.get(ti, tj, () -> computeNodes(ti, tj));
        r.nodeKeys[slot] = key;
        r.nodes[slot] = n;
        return n;
    }

    private Routes routes(int i, int j) {
        int ti = Math.floorDiv(i, TILE), tj = Math.floorDiv(j, TILE);
        long key = TileCache.key(ti, tj);
        Recent r = recent.get();
        int slot = (int) GradientNoise.mix(key ^ 0x5EEDL) & (Recent.SIZE - 1);
        if (r.routeKeys[slot] == key) return r.routes[slot];
        Routes routes = routeTiles.get(ti, tj, () -> computeRoutes(ti, tj));
        r.routeKeys[slot] = key;
        r.routes[slot] = routes;
        return routes;
    }

    private byte downOf(int i, int j) {
        return routes(i, j).down[local(i, j)];
    }

    private double[] position(int i, int j) {
        Nodes n = nodes(i, j);
        int k = local(i, j);
        return new double[] {n.x[k], n.z[k]};
    }

    private static int local(int i, int j) {
        return Math.floorMod(i, TILE) + Math.floorMod(j, TILE) * TILE;
    }

    private static long key(int i, int j) {
        return ((long) i << 32) | (j & 0xFFFFFFFFL);
    }

    private static double unit(long hash, int k) {
        return (GradientNoise.mix(hash + k * 0x9E3779B97F4A7C15L) >>> 11) * 0x1.0p-53;
    }

    // ---- inspection (self-checks, tools, /grandatlas where)

    /** One node of the network, for tools. */
    public record Node(int i, int j, double x, double z, double terrain, double catchment, double level, int down) {
        public boolean river() {
            return catchment >= SOURCE && down != OCEAN;
        }
    }

    public Node node(int i, int j) {
        Nodes n = nodes(i, j);
        int k = local(i, j);
        long m = memo(i, j);
        return new Node(i, j, n.x[k], n.z[k], n.t[k], catchment(m), level(m), downOf(i, j));
    }

    /** The next node downstream, or null at the sea or in a closed basin. */
    public Node next(Node n) {
        return n.down() >= 0 ? node(n.i() + DX[n.down()], n.j() + DZ[n.down()]) : null;
    }

    public static int cell(double v) {
        return GradientNoise.floor(v / STEP);
    }
}
