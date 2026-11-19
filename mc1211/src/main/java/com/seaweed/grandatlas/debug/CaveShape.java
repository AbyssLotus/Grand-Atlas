package com.seaweed.grandatlas.debug;

import com.seaweed.grandatlas.GrandAtlas;
import com.seaweed.grandatlas.core.AtlasCore;
import com.seaweed.grandatlas.core.ColumnSample;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.stream.IntStream;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.RandomState;

/**
 * The cave shape survey (cave redesign, 2026-11-18). The user: "our large parts are smaller than
 * Minecraft's, but our small parts are larger than vanilla". The volume survey (CaveSurvey) can't
 * show that, so this measures the shape of the open space, every block, in small boxes, for vanilla's
 * caves (vanilla's own terrain for this seed) and Grand Atlas's:
 * <ul>
 *   <li><b>Width spectrum:</b> the radius of the space at its middle (open blocks that are local
 *       maxima of the distance to the nearest rock: the medial axis), so one number per passage or
 *       room slice, not weighted by volume.</li>
 *   <li><b>Open volume by distance to rock</b>, so how much of the space is "in the middle of a big room".</li>
 *   <li><b>Space sizes:</b> the open volume split by the size of the connected space it's in.</li>
 *   <li><b>Heights and widths:</b> open volume by the length of the vertical (and horizontal) run of air it's in.</li>
 * </ul>
 * Run by the {@code caveShape} dev run; writes {@code grandatlas/cave_shape.txt}.
 */
public final class CaveShape {
    private CaveShape() {}

    static final int[][] BOXES = {{0, 0}, {4573, 3347}, {-1400, 5100}, {9000, 6000}, {-7000, -2500}, {2500, -8000}};
    static final int W = 112, H = 120, MARGIN = 10;
    static final double[] R_BINS = {1.5, 2.5, 4, 6, 9, 14, 22, 1e9};
    static final int[] RUN_BINS = {2, 4, 7, 12, 20, 35, 60, 1 << 30};
    static final long[] SIZE_BINS = {100, 1_000, 10_000, 100_000, 1L << 60};

    interface Open {
        /** For a column: true where the block (index y - y0) is open cave. */
        void column(int x, int z, boolean[] out);
    }

    static final class Stats {
        final String name;
        long open, total;
        final long[] ridge = new long[R_BINS.length], byDist = new long[R_BINS.length];
        final long[] vRun = new long[RUN_BINS.length], hRun = new long[RUN_BINS.length], sizes = new long[SIZE_BINS.length];

        Stats(String name) {
            this.name = name;
        }
    }

    public static void start(MinecraftServer server, Runnable done) {
        Thread t = new Thread(() -> {
            try {
                run(server);
            } catch (Throwable e) {
                GrandAtlas.LOG.error("Cave shape survey failed", e);
            } finally {
                done.run();
            }
        }, "Grand Atlas cave shape");
        t.setDaemon(true);
        t.start();
    }

    static void run(MinecraftServer server) throws Exception {
        long seed = server.getWorldData().worldGenOptions().seed();
        NoiseGeneratorSettings vanilla = CaveSurvey.vanillaSettings(server);
        RandomState state = RandomState.create(vanilla, server.registryAccess().lookupOrThrow(Registries.NOISE), seed);
        DensityFunction f = state.router().finalDensity();
        AtlasCore core = com.seaweed.grandatlas.world.CoreHandle.coreOf(server.overworld());
        Stats van = new Stats("vanilla, y -60..60"), ours = new Stats("Grand Atlas, y -60..60"),
                deep = new Stats("Grand Atlas deep, y -250..-130"), abyss = new Stats("Grand Atlas abyss, y -450..-330");
        StringBuilder rep = new StringBuilder();
        for (int[] b : BOXES) {
            int x0 = b[0] - W / 2, z0 = b[1] - W / 2;
            long t0 = System.nanoTime();
            box(van, x0, z0, -60, (x, z, out) -> vanillaColumn(f, x, z, -60, out));
            if (core != null) {
                box(ours, x0, z0, -60, (x, z, out) -> atlasColumn(core, x, z, -60, out));
                box(deep, x0, z0, -250, (x, z, out) -> atlasColumn(core, x, z, -250, out));
                box(abyss, x0, z0, -450, (x, z, out) -> atlasColumn(core, x, z, -450, out));
            }
            GrandAtlas.LOG.info("[cave shape] box {} {} in {} s", b[0], b[1], (int) ((System.nanoTime() - t0) / 1e9));
        }
        rep.append(String.format("Cave shape survey, seed %d, %d boxes %dx%dx%d, every block%n", seed, BOXES.length, W, W, H));
        for (Stats s : new Stats[] {van, ours, deep, abyss}) report(rep, s);
        Path file = server.getServerDirectory().resolve("grandatlas").resolve("cave_shape.txt");
        Files.createDirectories(file.getParent());
        Files.writeString(file, rep);
        for (String line : rep.toString().split("\n")) GrandAtlas.LOG.info("[cave shape] {}", line);
    }

    static void vanillaColumn(DensityFunction f, int x, int z, int y0, boolean[] out) {
        int top = Integer.MIN_VALUE;
        for (int y = 316; y >= -60; y -= 4) {
            if (f.compute(new DensityFunction.SinglePointContext(x, y, z)) > 0) {
                top = y;
                break;
            }
        }
        for (int i = 0; i < H; i++) {
            int y = y0 + i;
            out[i] = y < top - 10 && f.compute(new DensityFunction.SinglePointContext(x, y, z)) <= 0;
        }
    }

    static void atlasColumn(AtlasCore core, int x, int z, int y0, boolean[] out) {
        ColumnSample c = core.column(x, z);
        int top = c.surfaceY();
        boolean megaCol = core.megaCaverns().openColumn(x, z), setCol = core.setPieces().nearColumn(x, z);
        boolean fineCol = !core.caves().fineNone(x, z);
        for (int i = 0; i < H; i++) {
            int y = y0 + i;
            out[i] = y < top - 10 && (core.passages().carve(c, x, y, z) < 0
                    || megaCol && core.megaCaverns().carve(x, y, z) < 0
                    || y > AtlasCore.TERRAIN_FLOOR + 13 && core.caves().cavern(c, x, y, z) < 0
                    || fineCol && core.caves().fine(x, y, z) < 0
                    || setCol && core.setPieces().sample(x, y, z).open);
        }
    }

    static int idx(int x, int y, int z) {
        return (x * W + z) * H + y;
    }

    static void box(Stats s, int x0, int z0, int y0, Open open) {
        boolean[] g = new boolean[W * W * H];
        IntStream.range(0, W * W).parallel().forEach(k -> {
            int x = k / W, z = k % W;
            boolean[] col = new boolean[H];
            open.column(x0 + x, z0 + z, col);
            System.arraycopy(col, 0, g, idx(x, 0, z), H);
        });
        float[] d = edt(g);
        boolean inner;
        for (int x = 0; x < W; x++) {
            for (int z = 0; z < W; z++) {
                for (int y = 0; y < H; y++) {
                    inner = x >= MARGIN && x < W - MARGIN && z >= MARGIN && z < W - MARGIN && y >= MARGIN && y < H - MARGIN;
                    if (!inner) continue;
                    s.total++;
                    int i = idx(x, y, z);
                    if (!g[i]) continue;
                    s.open++;
                    double r = Math.sqrt(d[i]);
                    s.byDist[bin(r)]++;
                    boolean max = true;
                    for (int dx = -1; dx <= 1 && max; dx++) {
                        for (int dz = -1; dz <= 1 && max; dz++) {
                            for (int dy = -1; dy <= 1; dy++) {
                                if (d[idx(x + dx, y + dy, z + dz)] > d[i]) {
                                    max = false;
                                    break;
                                }
                            }
                        }
                    }
                    if (max) s.ridge[bin(r)]++;
                }
            }
        }
        // vertical and horizontal runs, volume-weighted, counted for runs whose middle is inner
        for (int x = MARGIN; x < W - MARGIN; x++) {
            for (int z = MARGIN; z < W - MARGIN; z++) {
                int y = 0;
                while (y < H) {
                    if (!g[idx(x, y, z)]) {
                        y++;
                        continue;
                    }
                    int a = y;
                    while (y < H && g[idx(x, y, z)]) y++;
                    s.vRun[runBin(y - a)] += y - a;
                }
            }
        }
        for (int y = MARGIN; y < H - MARGIN; y++) {
            for (int z = MARGIN; z < W - MARGIN; z++) {
                int x = 0;
                while (x < W) {
                    if (!g[idx(x, y, z)]) {
                        x++;
                        continue;
                    }
                    int a = x;
                    while (x < W && g[idx(x, y, z)]) x++;
                    s.hRun[runBin(x - a)] += x - a;
                }
            }
        }
        // connected spaces (6-connected), within the box
        boolean[] seen = new boolean[g.length];
        ArrayDeque<Integer> q = new ArrayDeque<>();
        for (int i = 0; i < g.length; i++) {
            if (!g[i] || seen[i]) continue;
            long n = 0;
            seen[i] = true;
            q.add(i);
            while (!q.isEmpty()) {
                int c = q.poll();
                n++;
                int y = c % H, xz = c / H, z = xz % W, x = xz / W;
                int[] nb = {x > 0 ? c - W * H : -1, x < W - 1 ? c + W * H : -1, z > 0 ? c - H : -1, z < W - 1 ? c + H : -1, y > 0 ? c - 1 : -1, y < H - 1 ? c + 1 : -1};
                for (int m : nb) {
                    if (m >= 0 && g[m] && !seen[m]) {
                        seen[m] = true;
                        q.add(m);
                    }
                }
            }
            int k = 0;
            while (n >= SIZE_BINS[k]) k++;
            s.sizes[k] += n;
        }
    }

    static int bin(double r) {
        int k = 0;
        while (r >= R_BINS[k]) k++;
        return k;
    }

    static int runBin(int n) {
        int k = 0;
        while (n > RUN_BINS[k]) k++;
        return k;
    }

    /** Squared distance from each open block to the nearest rock (exact, separable; Felzenszwalb and Huttenlocher). */
    static float[] edt(boolean[] g) {
        float INF = 1e9f;
        float[] d = new float[g.length];
        for (int i = 0; i < g.length; i++) d[i] = g[i] ? INF : 0;
        // along y
        for (int x = 0; x < W; x++) for (int z = 0; z < W; z++) pass(d, idx(x, 0, z), 1, H);
        // along z
        for (int x = 0; x < W; x++) for (int y = 0; y < H; y++) pass(d, idx(x, y, 0), H, W);
        // along x
        for (int z = 0; z < W; z++) for (int y = 0; y < H; y++) pass(d, idx(0, y, z), W * H, W);
        return d;
    }

    private static void pass(float[] d, int start, int stride, int n) {
        float[] f = new float[n], out = new float[n];
        int[] v = new int[n];
        float[] zz = new float[n + 1];
        for (int i = 0; i < n; i++) f[i] = d[start + i * stride];
        int k = 0;
        v[0] = 0;
        zz[0] = -Float.MAX_VALUE;
        zz[1] = Float.MAX_VALUE;
        for (int q = 1; q < n; q++) {
            float s;
            while (true) {
                s = ((f[q] + q * q) - (f[v[k]] + v[k] * v[k])) / (2f * q - 2f * v[k]);
                if (s <= zz[k] && k > 0) k--;
                else break;
            }
            if (s <= zz[k]) {
                v[0] = q;
                zz[0] = -Float.MAX_VALUE;
                zz[1] = Float.MAX_VALUE;
                k = 0;
                continue;
            }
            k++;
            v[k] = q;
            zz[k] = s;
            zz[k + 1] = Float.MAX_VALUE;
        }
        k = 0;
        for (int q = 0; q < n; q++) {
            while (zz[k + 1] < q) k++;
            out[q] = (q - v[k]) * (q - v[k]) + f[v[k]];
        }
        for (int i = 0; i < n; i++) d[start + i * stride] = out[i];
    }

    static void report(StringBuilder r, Stats s) {
        r.append(String.format("%n== %s: %.2f%% open%n", s.name, s.total == 0 ? 0 : 100.0 * s.open / s.total));
        r.append("  radius at the middle (medial axis), share of middle points:\n   ");
        line(r, s.ridge, rLabels());
        r.append("  open volume by distance to rock:\n   ");
        line(r, s.byDist, rLabels());
        r.append("  open volume by height of the air column it's in:\n   ");
        line(r, s.vRun, runLabels());
        r.append("  open volume by east-west width of the air run it's in:\n   ");
        line(r, s.hRun, runLabels());
        r.append("  open volume by size of the connected space (in the box):\n   ");
        line(r, s.sizes, new String[] {"<100", "<1k", "<10k", "<100k", "100k+"});
    }

    private static String[] rLabels() {
        String[] l = new String[R_BINS.length];
        double lo = 0;
        for (int i = 0; i < l.length; i++) {
            l[i] = R_BINS[i] > 1e8 ? String.format("%.0f+", lo) : String.format("%.1f-%.1f", lo, R_BINS[i]);
            lo = R_BINS[i];
        }
        return l;
    }

    private static String[] runLabels() {
        String[] l = new String[RUN_BINS.length];
        int lo = 1;
        for (int i = 0; i < l.length; i++) {
            l[i] = RUN_BINS[i] > 1 << 29 ? lo + "+" : lo + "-" + RUN_BINS[i];
            lo = RUN_BINS[i] + 1;
        }
        return l;
    }

    private static void line(StringBuilder r, long[] v, String[] labels) {
        long sum = 0;
        for (long x : v) sum += x;
        for (int i = 0; i < v.length; i++) r.append(String.format(" %s:%4.1f%%", labels[i], sum == 0 ? 0 : 100.0 * v[i] / sum));
        r.append('\n');
    }
}
