package com.seaweed.grandatlas.core;

import com.seaweed.grandatlas.core.noise.GradientNoise;

/**
 * A sea stack (some of them arches) standing off a hard coast, or a sea cave in a cliff's face
 * (docs/M3_M4_PLAN.md, phase 1). Fixed per cell of a coarse grid, so every column agrees on it.
 *
 * @param kind     what it is
 * @param x        a stack's centre, or the mouth of a cave on the cliff face
 * @param z        a stack's centre, or the mouth of a cave on the cliff face
 * @param ax       a stack's long axis (an arch's tunnel runs along it), or a cave's direction into the land
 * @param az       a stack's long axis (an arch's tunnel runs along it), or a cave's direction into the land
 * @param radius   a stack's radius across its long axis, or a cave's half-width at the mouth
 * @param top      a stack's top, or a cave's roof at the mouth (y)
 * @param depth    a stack's half-length along its axis, or how far a cave runs into the land
 * @param arch     an arch's tunnel half-width (0: a solid stack)
 * @param archTop  the top of an arch's tunnel (y)
 * @param seed     for the outline's wobble
 */
public record CoastFeature(Kind kind, double x, double z, double ax, double az, double radius, double top, double depth,
                           double arch, double archTop, long seed) {

    public enum Kind { STACK, CAVE, KARST }

    /** Floor of caves and arches: a little under the sea, so the sea runs in. */
    public static final double FLOOR = AtlasCore.SEA_LEVEL - 2.5;

    /** A stack's outline at (x, z): under 1 inside, 1 on its edge. */
    public double outline(double px, double pz) {
        double dx = px - x, dz = pz - z;
        double u = dx * ax + dz * az, v = -dx * az + dz * ax;
        double a = Math.atan2(v, u);
        double p1 = (seed & 0xFFFF) / 65536.0 * Math.PI * 2, p2 = ((seed >>> 16) & 0xFFFF) / 65536.0 * Math.PI * 2;
        double wobble = 1 + 0.16 * Math.sin(3 * a + p1) + 0.08 * Math.sin(5 * a + p2)
                + 0.1 * GradientNoise.noise2(seed, px / 4.0, pz / 4.0);
        return Math.hypot(u / depth, v / radius) / wobble;
    }

    /**
     * The per-block carve (negative inside): an arch's tunnel through its stack, or a sea cave
     * running into the cliff, narrowing and lowering as it goes.
     */
    public static double carve(Shore shore, int x, int y, int z) {
        if (shore == null || shore.feature() == null) return Double.POSITIVE_INFINITY;
        CoastFeature f = shore.feature();
        if (y < FLOOR) return Double.POSITIVE_INFINITY;
        double dx = x + 0.5 - f.x, dz = z + 0.5 - f.z;
        double u = dx * f.ax + dz * f.az, v = -dx * f.az + dz * f.ax;
        if (f.kind == Kind.KARST) {
            // The wave-cut notch: the sea has undercut the tower 1.5-3 blocks at the waterline.
            if (y > AtlasCore.SEA_LEVEL + 2) return Double.POSITIVE_INFINITY;
            double o = f.outline(x + 0.5, z + 0.5);
            double notch = (1.5 + 1.5 * (0.5 + 0.5 * GradientNoise.noise2(f.seed ^ 0xB1L, x / 7.0, z / 7.0))) / f.radius;
            double band = 1 - Math.abs(y - (AtlasCore.SEA_LEVEL - 0.5)) / 2.5;
            return o > 1 - notch * band ? -1 : Double.POSITIVE_INFINITY;
        }
        if (f.kind == Kind.STACK) {
            // The tunnel goes through the fin's thin side: across its long axis.
            if (f.arch <= 0 || y > f.archTop) return Double.POSITIVE_INFINITY;
            double half = f.arch * (1 + 0.15 * GradientNoise.noise2(f.seed ^ 0xA1L, v / 5.0, y / 5.0));
            double rise = f.archTop - FLOOR;
            double q = (u / half) * (u / half) + ((y - FLOOR) / rise) * ((y - FLOOR) / rise);
            return q < 1 ? (q - 1) * 2 : Double.POSITIVE_INFINITY;
        }
        // A cave: u runs into the land from the mouth on the face.
        if (u < -3 || u > f.depth) return Double.POSITIVE_INFINITY;
        double in = Math.max(0, u) / f.depth;
        double scale = 1 - 0.6 * in;
        double half = f.radius * scale, rise = (f.top - FLOOR) * (1 - 0.45 * in);
        double vv = v + 1.5 * GradientNoise.noise2(f.seed ^ 0xC1L, u / 9.0, 0.5); // it winds a little
        double q = (vv / half) * (vv / half) + ((y - FLOOR) / rise) * ((y - FLOOR) / rise);
        return q < 1 ? (q - 1) * 2 : Double.POSITIVE_INFINITY;
    }
}
