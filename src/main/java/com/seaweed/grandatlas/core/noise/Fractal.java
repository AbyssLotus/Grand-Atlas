package com.seaweed.grandatlas.core.noise;

/** Fractal sums built on {@link GradientNoise}. Each octave gets its own forked seed. */
public final class Fractal {
    private Fractal() {}

    /** Plain fBm, normalised to roughly [-1, 1]. */
    public static double fbm(long seed, double x, double z, int octaves) {
        double sum = 0, amp = 1, freq = 1, norm = 0;
        for (int i = 0; i < octaves; i++) {
            sum += amp * GradientNoise.noise2(GradientNoise.fork(seed, i), x * freq, z * freq);
            norm += amp;
            amp *= 0.5;
            freq *= 2.03;
        }
        return sum / norm;
    }

    public static double fbm3(long seed, double x, double y, double z, int octaves) {
        double sum = 0, amp = 1, freq = 1, norm = 0;
        for (int i = 0; i < octaves; i++) {
            sum += amp * GradientNoise.noise3(GradientNoise.fork(seed, i), x * freq, y * freq, z * freq);
            norm += amp;
            amp *= 0.5;
            freq *= 2.03;
        }
        return sum / norm;
    }

    /**
     * Ridged multifractal with slope damping, in [0, 1]. Each octave is weighted by the
     * previous one (detail gathers on ridges, valleys stay smooth), and the running
     * gradient of the lower octaves suppresses higher ones on steep ground. Together these
     * give drainage-like ridges and valleys without an erosion simulation.
     */
    public static double erodedRidges(long seed, double x, double z, int octaves) {
        return erodedRidges(seed, x, z, octaves, 0.35);
    }

    /** As {@link #erodedRidges(long, double, double, int)}, with the slope damping strength given. */
    public static double erodedRidges(long seed, double x, double z, int octaves, double damping) {
        double[] d = new double[2];
        double sum = 0, amp = 1, freq = 1, norm = 0, weight = 1, gx = 0, gz = 0;
        for (int i = 0; i < octaves; i++) {
            double n = GradientNoise.noise2d(GradientNoise.fork(seed, i), x * freq, z * freq, d);
            double r = 1 - Math.abs(n);
            // Damp with the slope of the smooth underlying noise, not of |n|: the latter flips
            // sign on every ridge crest and would make the damping (and the height) jump there.
            gx += amp * d[0] * freq;
            gz += amp * d[1] * freq;
            r *= r;
            double damp = 1 / (1 + damping * (gx * gx + gz * gz));
            sum += amp * r * weight * damp;
            norm += amp;
            weight = Math.min(1, r * 1.8);
            amp *= 0.5;
            freq *= 2.03;
        }
        return Math.min(1, sum / norm * 1.6);
    }
}
