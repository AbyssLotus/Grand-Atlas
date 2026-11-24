package com.seaweed.grandatlas.mortus;

import com.seaweed.grandatlas.core.noise.GradientNoise;
import java.io.DataInputStream;
import java.io.InputStream;
import java.util.ArrayDeque;
import java.util.zip.GZIPInputStream;

/**
 * The map of Domus Mortus (docs/UNDERWORLD_DESIGN.md section 4), laid out by the user: they painted
 * it in biomes in a flat world (2026-11-19), and that painting is baked into
 * {@code data/grandatlas/domus/layout.bin} (tools/domus/bake_layout.py): 640 x 640 cells of 4 blocks,
 * centred on 0,0, about 2,560 blocks across.
 *
 * <pre>
 *   painted as        here
 *   river             the outer sea, beyond the realm
 *   jagged peaks      the mountain ring round the realm
 *   ocean             the Styx, the river circling the realm inside the mountains
 *   basalt deltas     the entrance: a dark basalt mountain in the ring, a cave through it, the
 *                     Acheron running out of it
 *   mangrove swamp    the Acheron swamp, which the Styx flows through
 *   frozen river      Cocytus
 *   cherry grove      the giant raised platform for Hades' palace
 *   plains            Asphodel, and the places the user left to me: Tartarus in the fire ring of
 *                     Phlegethon (east), Elysium (north-east), the Groves of Persephone round the
 *                     platform, the springs of Lethe and Mnemosyne (west of centre)
 * </pre>
 * Every water and fire body is a basin under its level with ground above it all round, so the fill
 * (MortusFluids) never spills. Fixed: the same realm in every world.
 */
public final class MortusMap {
    private MortusMap() {}

    public enum Region {
        STYX("styx"), OUTER_SEA("outer_sea"), LANDING("landing_shore"), GATE("the_gate"), ASPHODEL("asphodel_meadows"),
        GROVES("persephone_grove"), LETHE("lethe_springs"), COCYTUS("cocytus"), PHLEGETHON("phlegethon"), TARTARUS("tartarus"),
        PLATEAU("black_plateau"), ELYSIUM("elysium"), ACHERON("acheron_swamp"), ENTRANCE("the_entrance"), PEAKS("encircling_peaks");

        public final String id;

        Region(String id) {
            this.id = id;
        }
    }

    static final long SEED = 0xD0_405_3047L;
    public static final int WATER = 62, FIRE = 64, PLATFORM = 150;
    /** Kept for callers from the first layout; the painted map is drawn at full size. */
    public static final double SCALE = 1;

    // the painted codes
    static final int C_PLAINS = 0, C_OUTER = 1, C_PEAKS = 2, C_SWAMP = 3, C_STYX = 4, C_PLATFORM = 5, C_ENTRANCE = 6, C_COCYTUS = 7;
    static final int N = 640, OFF = 320, CELL = 4;

    // my placements in the open plains (world blocks)
    static final double TX = 500, TZ = -150, PIT_R = 95, FIRE_IN = 104, FIRE_OUT = 122;
    static final double EX = 250, EZ = -560, ELY_R = 200;
    static final double LX1 = -200, LX2 = -80, LZ = 120, LAKE_R = 28, SPRINGS_R = 110;
    // the entrance cave (the user: "a very clear cave with a passage out to the Styx, so the player can
    // spawn there"): a sealed chamber deep in the basalt, a dry ledge in it, and a tunnel east into the
    // swamp, the Acheron rising in the chamber and running down it to the Styx
    static final double CHX = -1072, CHZ = 167, CH_R = 18, CH_H = 16, LEDGE_TOP = 63;
    static final double CAVE_X0 = CHX, CAVE_X1 = -990, CAVE_Z = 167, CAVE_W = 9, CAVE_FLOOR = 57, CAVE_H = 14;
    /** Where the player spawns: on the chamber's dry ledge, the river below. */
    public static final int SPAWN_X = (int) CHX, SPAWN_Y = (int) LEDGE_TOP + 1, SPAWN_Z = (int) CHZ + 11;

    private static final byte[] CODE = new byte[N * N];
    /** Per cell: the blended target height, and signed distances (cells; + inside) to a few regions. */
    private static final float[] BASE = new float[N * N];
    private static final float[] D_PLATFORM = new float[N * N], D_PEAKS = new float[N * N], D_ENTRANCE = new float[N * N],
            D_STYX = new float[N * N], D_SWAMP = new float[N * N], D_COCYTUS = new float[N * N], D_OUTER = new float[N * N];
    /** The Styx ring's radius at each degree (measured from the painting, bridged where the swamp covers it). */
    private static final double[] STYX_R = new double[360];
    /** Where the Acheron meets the Styx along the cave's line (x). */
    private static double RIVER_END_X;

    static {
        try (InputStream in = MortusMap.class.getResourceAsStream("/data/grandatlas/domus/layout.bin")) {
            if (in == null) throw new IllegalStateException("data/grandatlas/domus/layout.bin missing");
            new DataInputStream(new GZIPInputStream(in)).readFully(CODE);
        } catch (Exception e) {
            throw new IllegalStateException("Domus Mortus layout", e);
        }
        signed(C_PLATFORM, D_PLATFORM);
        signed(C_PEAKS, D_PEAKS);
        signed(C_ENTRANCE, D_ENTRANCE);
        signed(C_STYX, D_STYX);
        signed(C_SWAMP, D_SWAMP);
        signed(C_COCYTUS, D_COCYTUS);
        signed(C_OUTER, D_OUTER);
        float[] t = new float[N * N];
        for (int i = 0; i < t.length; i++) {
            t[i] = switch (CODE[i]) {
                case C_OUTER -> 22;
                case C_PEAKS -> 140;
                case C_SWAMP -> 62;
                case C_STYX -> 44;
                case C_ENTRANCE -> 130;
                case C_COCYTUS -> 66;
                default -> 72; // plains, and the platform's foot (its top is set apart below)
            };
        }
        blur(t, 5);
        blur(t, 5);
        System.arraycopy(t, 0, BASE, 0, t.length);
        // the Styx's radius by angle: the median of its painted cells in each degree, the gaps (where the
        // swamp was painted over it: "the Styx flows through the swamp") bridged in a straight run
        java.util.List<java.util.List<Double>> bins = new java.util.ArrayList<>();
        for (int k = 0; k < 360; k++) bins.add(new java.util.ArrayList<>());
        for (int i = 0; i < CODE.length; i++) {
            if (CODE[i] != C_STYX) continue;
            double x = (i % N - OFF + 0.5) * CELL, z = (i / N - OFF + 0.5) * CELL;
            bins.get((int) ((Math.toDegrees(Math.atan2(z, x)) + 360) % 360)).add(Math.hypot(x, z));
        }
        double[] r = new double[360];
        boolean[] has = new boolean[360];
        for (int k = 0; k < 360; k++) {
            java.util.List<Double> b = bins.get(k);
            if (b.size() < 2) continue;
            java.util.Collections.sort(b);
            r[k] = b.get(b.size() / 2);
            has[k] = true;
        }
        for (int k = 0; k < 360; k++) {
            if (has[k]) {
                STYX_R[k] = r[k];
                continue;
            }
            int a = k, b = k;
            while (!has[((a - 1) % 360 + 360) % 360] && a > k - 359) a--;
            while (!has[(b + 1) % 360] && b < k + 359) b++;
            int ka = ((a - 1) % 360 + 360) % 360, kb = (b + 1) % 360;
            double t2 = (double) (k - (a - 1)) / ((b + 1) - (a - 1));
            STYX_R[k] = has[ka] && has[kb] ? lerp(r[ka], r[kb], t2) : 980;
        }
        RIVER_END_X = -560;
        for (double x = CAVE_X1; x < -500; x += 1) {
            if (Math.abs(Math.hypot(x, CAVE_Z) - styxR(x, CAVE_Z)) < 3) {
                RIVER_END_X = x;
                break;
            }
        }
    }

    /** The Styx ring's radius in the direction of (x, z). */
    static double styxR(double x, double z) {
        double a = (Math.toDegrees(Math.atan2(z, x)) + 360) % 360;
        int k = (int) Math.floor(a);
        return lerp(STYX_R[k % 360], STYX_R[(k + 1) % 360], a - k);
    }

    /** Distance across from the Styx's middle, where it runs through the swamp (blocks). */
    static double styxAcross(double x, double z) {
        return Math.abs(Math.hypot(x, z) - styxR(x, z));
    }

    /** Signed distance (cells) to a painted region: positive inside (to its edge), negative outside. */
    private static void signed(int code, float[] out) {
        float[] in = bfs(code, true), outside = bfs(code, false);
        for (int i = 0; i < out.length; i++) out[i] = CODE[i] == code ? in[i] : -outside[i];
    }

    /** Distance (8-neighbour steps) from every cell to the nearest cell that is (or isn't) the code. */
    private static float[] bfs(int code, boolean toOutside) {
        float[] d = new float[N * N];
        java.util.Arrays.fill(d, Float.MAX_VALUE);
        ArrayDeque<Integer> q = new ArrayDeque<>();
        for (int i = 0; i < d.length; i++) {
            boolean seed = toOutside ? CODE[i] != code : CODE[i] == code;
            if (seed) {
                d[i] = 0;
                q.add(i);
            }
        }
        while (!q.isEmpty()) {
            int c = q.poll(), cx = c % N, cz = c / N;
            for (int dz = -1; dz <= 1; dz++) {
                for (int dx = -1; dx <= 1; dx++) {
                    int nx = cx + dx, nz = cz + dz;
                    if ((dx | dz) == 0 || nx < 0 || nz < 0 || nx >= N || nz >= N) continue;
                    float nd = d[c] + ((dx != 0 && dz != 0) ? 1.414f : 1f);
                    int n = nz * N + nx;
                    if (nd < d[n]) {
                        d[n] = nd;
                        q.add(n);
                    }
                }
            }
        }
        for (int i = 0; i < d.length; i++) if (d[i] == Float.MAX_VALUE) d[i] = 999;
        return d;
    }

    private static void blur(float[] a, int r) {
        float[] tmp = new float[a.length];
        for (int z = 0; z < N; z++) {
            for (int x = 0; x < N; x++) {
                float s = 0;
                int n = 0;
                for (int k = Math.max(0, x - r); k <= Math.min(N - 1, x + r); k++) {
                    s += a[z * N + k];
                    n++;
                }
                tmp[z * N + x] = s / n;
            }
        }
        for (int x = 0; x < N; x++) {
            for (int z = 0; z < N; z++) {
                float s = 0;
                int n = 0;
                for (int k = Math.max(0, z - r); k <= Math.min(N - 1, z + r); k++) {
                    s += tmp[k * N + x];
                    n++;
                }
                a[z * N + x] = s / n;
            }
        }
    }

    private static double n2(int salt, double x, double z, double scale) {
        return GradientNoise.noise2(GradientNoise.fork(SEED, salt), x / scale, z / scale);
    }

    /** A cell field at a block, bilinear between cell centres. */
    private static double field(float[] f, double x, double z) {
        double gx = x / CELL + OFF - 0.5, gz = z / CELL + OFF - 0.5;
        int ix = (int) Math.floor(gx), iz = (int) Math.floor(gz);
        double fx = gx - ix, fz = gz - iz;
        return lerp(lerp(at(f, ix, iz), at(f, ix + 1, iz), fx), lerp(at(f, ix, iz + 1), at(f, ix + 1, iz + 1), fx), fz);
    }

    private static float at(float[] f, int ix, int iz) {
        if (ix < 0 || iz < 0 || ix >= N || iz >= N) return f == BASE ? 22 : f == D_OUTER ? 50 : -50;
        return f[iz * N + ix];
    }

    /** The painted code at a block, its edges broken a little by noise so cells don't show. */
    private static int code(double x, double z) {
        double jx = x + 5 * n2(20, x, z, 23), jz = z + 5 * n2(21, x, z, 23);
        int ix = (int) Math.floor(jx / CELL) + OFF, iz = (int) Math.floor(jz / CELL) + OFF;
        if (ix < 0 || iz < 0 || ix >= N || iz >= N) return C_OUTER;
        return CODE[iz * N + ix];
    }

    public static Region region(double x, double z) {
        int c = code(x, z);
        switch (c) {
            case C_OUTER: return Region.OUTER_SEA;
            case C_PEAKS: return Region.PEAKS;
            case C_SWAMP: return styxAcross(x, z) < 7 ? Region.STYX : Region.ACHERON; // the Styx flows on through the swamp
            case C_STYX: return Region.STYX;
            case C_PLATFORM: return Region.PLATEAU;
            case C_ENTRANCE: return Region.ENTRANCE;
            case C_COCYTUS: return Region.COCYTUS;
            default: break;
        }
        // the plains: my placements, then the shore along the Styx, then Asphodel
        double dt = Math.hypot(x - TX, z - TZ);
        if (dt < FIRE_OUT + 8) return dt < PIT_R + 6 ? Region.TARTARUS : Region.PHLEGETHON;
        if (Math.hypot(x - EX, z - EZ) < ELY_R + 30 * n2(22, x, z, 120)) return Region.ELYSIUM;
        if (Math.hypot(x - (LX1 + LX2) / 2, z - LZ) < SPRINGS_R) return Region.LETHE;
        if (field(D_PLATFORM, x, z) > -32 + 6 * n2(23, x, z, 90)) return Region.GROVES; // within ~130 of the platform
        if (field(D_STYX, x, z) > -5) return Region.LANDING;
        return Region.ASPHODEL;
    }

    /** The ground's height (the top solid block's y, about). */
    public static double height(double x, double z) {
        double h = field(BASE, x, z);
        // Asphodel and the rest of the plains: gentle rolling
        h += 4 * n2(3, x, z, 160) + 2 * n2(4, x, z, 45);
        // the mountain ring: jagged ridges, sharper the further in
        double dp = field(D_PEAKS, x, z);
        if (dp > -3) {
            double ridge = 1 - Math.abs(n2(5, x, z, 70)), ridge2 = 1 - Math.abs(n2(6, x, z, 26));
            h += smooth(-2, 6, dp) * (55 * ridge * ridge + 18 * ridge2 * ridge2 - 10);
        }
        // the entrance: a dark basalt mountain
        double de = field(D_ENTRANCE, x, z);
        if (de > -3) h = lerp(h, 128 + 18 * n2(7, x, z, 60), smooth(-2, 3, de));
        // the Styx: a channel under the water, whatever the blend made of it
        double ds = field(D_STYX, x, z);
        if (ds > -3) h = Math.min(h, lerp(h, 44 + 3 * n2(8, x, z, 40), smooth(-1.5, 1.5, ds)));
        // the outer sea's bed
        double dout = field(D_OUTER, x, z);
        if (dout > -2) h = Math.min(h, lerp(h, 18 + 6 * n2(9, x, z, 150), smooth(-1, 6, dout)));
        // the Acheron swamp: just above and below the water, pools between mangrove ground
        double dw = field(D_SWAMP, x, z);
        if (dw > -3) {
            double swamp = 61.6 + 1.8 * n2(10, x, z, 60) + 0.8 * n2(11, x, z, 18);
            // the Acheron itself, from the cave mouth east through the swamp until it meets the Styx
            double riv = (1 - smooth(6, 11, Math.abs(z - CAVE_Z - 6 * n2(12, x, 0, 90)))) * (1 - smooth(RIVER_END_X, RIVER_END_X + 12, x));
            swamp = lerp(swamp, 55, riv);
            // the Styx's channel through the swamp
            swamp = lerp(swamp, 44 + 3 * n2(8, x, z, 40), 1 - smooth(5, 10, styxAcross(x, z)));
            h = lerp(h, swamp, smooth(-2, 2, dw));
        }
        // Cocytus: a frozen river in its channel
        double dc = field(D_COCYTUS, x, z);
        if (dc > -2.5) h = lerp(h, 57, smooth(-2, 0.5, dc));
        // the giant raised platform for Hades' palace: flat on top, cliffs round it
        double dpl = field(D_PLATFORM, x, z);
        if (dpl > -2) h = lerp(h, PLATFORM + 1.5 * n2(13, x, z, 50), smooth(-0.5, 3, dpl));
        // Lethe and Mnemosyne: two round spring lakes side by side
        for (double lx : new double[] {LX1, LX2}) h = Math.min(h, lerp(WATER - 8, h, smooth(LAKE_R - 10, LAKE_R + 8, Math.hypot(x - lx, z - LZ))));
        // Elysium: gentle and a touch higher
        double ely = 1 - smooth(ELY_R - 40, ELY_R + 20, Math.hypot(x - EX, z - EZ));
        h = lerp(h, 77 + 3 * n2(14, x, z, 90), ely);
        // Phlegethon: the fire ring round the pit, and Tartarus, a sheer pit to y -290
        double dt = Math.hypot(x - TX, z - TZ);
        double fire = smooth(FIRE_IN - 4, FIRE_IN, dt) * (1 - smooth(FIRE_OUT, FIRE_OUT + 4, dt));
        h = lerp(h, FIRE - 5, fire);
        h = lerp(-290 + 5 * n2(15, x, z, 20), h, smooth(PIT_R - 8, PIT_R, dt));
        return h;
    }

    /**
     * Inside the entrance cave: the spawn chamber (a dome sealed in the basalt, a dry ledge along its
     * north side) and the tunnel from it east into the swamp, the Acheron along both floors.
     */
    public static boolean cave(double x, double y, double z) {
        if (y < CAVE_FLOOR - 1 || y > CAVE_FLOOR + CH_H + 2) return false;
        double dy = (y - CAVE_FLOOR) / CH_H;
        // the chamber
        double cx = (x - CHX) / CH_R, cz = (z - CHZ) / CH_R;
        if (cx * cx + cz * cz < 1.2) {
            boolean inside = cx * cx + cz * cz + Math.max(0, dy) * Math.max(0, dy) * 1.2 < 1;
            boolean ledge = z > CHZ + 5 && y < LEDGE_TOP; // the dry ledge where you wake: solid up to y 63
            if (inside) return !ledge;
        }
        if (x < CHX || x > CAVE_X1 || y > CAVE_FLOOR + CAVE_H + 2) return false;
        // the tunnel: straight enough to read as the way out
        double zc = CAVE_Z + 6 * n2(12, x, 0, 90);
        double dz = Math.abs(z - zc) / CAVE_W;
        double ty = (y - CAVE_FLOOR) / CAVE_H;
        if (dz >= 1) return false;
        return dz * dz + Math.max(0, ty) * Math.max(0, ty) < 1;
    }

    /** The water's surface over this column, or NaN where there's none. */
    public static double water(double x, double z) {
        Region g = region(x, z);
        return switch (g) {
            case STYX, OUTER_SEA, LETHE, COCYTUS, LANDING, ACHERON -> WATER;
            case ENTRANCE -> Math.hypot(x - CHX, z - CHZ) < CH_R + 2
                    || x >= CAVE_X0 && x <= CAVE_X1 && Math.abs(z - CAVE_Z - 6 * n2(12, x, 0, 90)) < CAVE_W * 1.2 ? WATER : Double.NaN;
            default -> Double.NaN;
        };
    }

    /** Fire (lava) over this column, or NaN. */
    public static double fire(double x, double z) {
        return region(x, z) == Region.PHLEGETHON ? FIRE : Double.NaN;
    }

    static double smooth(double a, double b, double v) {
        double t = Math.max(0, Math.min(1, (v - a) / (b - a)));
        return t * t * (3 - 2 * t);
    }

    static double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }
}
