package com.seaweed.grandatlas.core;

public final class Maths {
    private Maths() {}

    public static double clamp(double v, double lo, double hi) {
        return v < lo ? lo : v > hi ? hi : v;
    }

    public static double lerp(double t, double a, double b) {
        return a + t * (b - a);
    }

    /** Hermite smoothstep of v across [edge0, edge1], clamped to [0, 1]. */
    public static double smoothstep(double edge0, double edge1, double v) {
        double t = clamp((v - edge0) / (edge1 - edge0), 0, 1);
        return t * t * (3 - 2 * t);
    }

    /** Maps v from [a, b] to [c, d] linearly, clamped to the output range. */
    public static double remap(double v, double a, double b, double c, double d) {
        return lerp(clamp((v - a) / (b - a), 0, 1), c, d);
    }
}
