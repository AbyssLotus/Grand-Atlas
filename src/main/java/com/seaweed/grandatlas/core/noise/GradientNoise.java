package com.seaweed.grandatlas.core.noise;

/**
 * Hash-based gradient (Perlin-style) noise in 2D and 3D. There are no permutation tables,
 * so every 64-bit seed is an independent field with no setup cost, and a single core can
 * use as many fields as it likes. Output is roughly [-1, 1].
 *
 * <p>The 2D variant can also return analytic derivatives, which the eroded-ridge
 * fractal uses to damp detail on steep slopes.
 */
public final class GradientNoise {
    private GradientNoise() {}

    private static final int DIRS = 16;
    private static final double[] GX = new double[DIRS];
    private static final double[] GZ = new double[DIRS];
    static {
        for (int i = 0; i < DIRS; i++) {
            double a = (i + 0.5) * Math.PI * 2 / DIRS;
            GX[i] = Math.cos(a);
            GZ[i] = Math.sin(a);
        }
    }

    /** The 12 cube-edge directions, padded to 16 so a 4-bit hash picks one. */
    private static final int[][] G3 = {
            {1, 1, 0}, {-1, 1, 0}, {1, -1, 0}, {-1, -1, 0},
            {1, 0, 1}, {-1, 0, 1}, {1, 0, -1}, {-1, 0, -1},
            {0, 1, 1}, {0, -1, 1}, {0, 1, -1}, {0, -1, -1},
            {1, 1, 0}, {-1, 1, 0}, {0, -1, 1}, {0, -1, -1}};

    /** Unit-gradient 2D Perlin peaks near ±0.707; this brings it to about ±1. */
    private static final double SCALE2 = 1.41;
    private static final double SCALE3 = 1.0;

    public static long mix(long z) {
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }

    public static long hash(long seed, int x, int z) {
        return mix(seed + x * 0x9E3779B97F4A7C15L + z * 0xC2B2AE3D27D4EB4FL);
    }

    public static long hash(long seed, int x, int y, int z) {
        return mix(seed + x * 0x9E3779B97F4A7C15L + y * 0xD6E8FEB86659FD93L + z * 0xC2B2AE3D27D4EB4FL);
    }

    /** Derives an independent child seed, e.g. one per octave or per named field. */
    public static long fork(long seed, long salt) {
        return mix(seed ^ mix(salt * 0x632BE59BD9B4E019L));
    }

    public static int floor(double v) {
        int i = (int) v;
        return v < i ? i - 1 : i;
    }

    private static double fade(double t) {
        return t * t * t * (t * (t * 6 - 15) + 10);
    }

    private static double dfade(double t) {
        return 30 * t * t * (t * (t - 2) + 1);
    }

    public static double noise2(long seed, double x, double z) {
        int xi = floor(x), zi = floor(z);
        double fx = x - xi, fz = z - zi;
        int g00 = (int) (hash(seed, xi, zi) >>> 60);
        int g10 = (int) (hash(seed, xi + 1, zi) >>> 60);
        int g01 = (int) (hash(seed, xi, zi + 1) >>> 60);
        int g11 = (int) (hash(seed, xi + 1, zi + 1) >>> 60);
        double n00 = GX[g00] * fx + GZ[g00] * fz;
        double n10 = GX[g10] * (fx - 1) + GZ[g10] * fz;
        double n01 = GX[g01] * fx + GZ[g01] * (fz - 1);
        double n11 = GX[g11] * (fx - 1) + GZ[g11] * (fz - 1);
        double u = fade(fx), v = fade(fz);
        double a = n00 + u * (n10 - n00);
        double b = n01 + u * (n11 - n01);
        return SCALE2 * (a + v * (b - a));
    }

    /** As {@link #noise2} but also writes d/dx and d/dz (in noise units) into {@code deriv}. */
    public static double noise2d(long seed, double x, double z, double[] deriv) {
        int xi = floor(x), zi = floor(z);
        double fx = x - xi, fz = z - zi;
        int ga = (int) (hash(seed, xi, zi) >>> 60);
        int gb = (int) (hash(seed, xi + 1, zi) >>> 60);
        int gc = (int) (hash(seed, xi, zi + 1) >>> 60);
        int gd = (int) (hash(seed, xi + 1, zi + 1) >>> 60);
        double a = GX[ga] * fx + GZ[ga] * fz;
        double b = GX[gb] * (fx - 1) + GZ[gb] * fz;
        double c = GX[gc] * fx + GZ[gc] * (fz - 1);
        double d = GX[gd] * (fx - 1) + GZ[gd] * (fz - 1);
        double u = fade(fx), v = fade(fz), du = dfade(fx), dv = dfade(fz);
        double k1 = b - a, k2 = c - a, k3 = a - b - c + d;
        double value = a + k1 * u + k2 * v + k3 * u * v;
        deriv[0] = SCALE2 * (GX[ga] + (GX[gb] - GX[ga]) * u + (GX[gc] - GX[ga]) * v
                + (GX[ga] - GX[gb] - GX[gc] + GX[gd]) * u * v + du * (k1 + k3 * v));
        deriv[1] = SCALE2 * (GZ[ga] + (GZ[gb] - GZ[ga]) * u + (GZ[gc] - GZ[ga]) * v
                + (GZ[ga] - GZ[gb] - GZ[gc] + GZ[gd]) * u * v + dv * (k2 + k3 * u));
        return SCALE2 * value;
    }

    public static double noise3(long seed, double x, double y, double z) {
        int xi = floor(x), yi = floor(y), zi = floor(z);
        double fx = x - xi, fy = y - yi, fz = z - zi;
        double u = fade(fx), v = fade(fy), w = fade(fz);
        double x00 = lerp(u, dot3(seed, xi, yi, zi, fx, fy, fz), dot3(seed, xi + 1, yi, zi, fx - 1, fy, fz));
        double x10 = lerp(u, dot3(seed, xi, yi + 1, zi, fx, fy - 1, fz), dot3(seed, xi + 1, yi + 1, zi, fx - 1, fy - 1, fz));
        double x01 = lerp(u, dot3(seed, xi, yi, zi + 1, fx, fy, fz - 1), dot3(seed, xi + 1, yi, zi + 1, fx - 1, fy, fz - 1));
        double x11 = lerp(u, dot3(seed, xi, yi + 1, zi + 1, fx, fy - 1, fz - 1), dot3(seed, xi + 1, yi + 1, zi + 1, fx - 1, fy - 1, fz - 1));
        return SCALE3 * lerp(w, lerp(v, x00, x10), lerp(v, x01, x11));
    }

    private static double dot3(long seed, int x, int y, int z, double dx, double dy, double dz) {
        int[] g = G3[(int) (hash(seed, x, y, z) >>> 60)];
        return g[0] * dx + g[1] * dy + g[2] * dz;
    }

    private static double lerp(double t, double a, double b) {
        return a + t * (b - a);
    }
}
