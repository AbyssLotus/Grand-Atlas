package com.seaweed.grandatlas.core.macro;

/** Shared profile for the fantasy macros' sheer stone towers (spires, pillars). */
final class Spires {
    private Spires() {}

    /**
     * A tower's height at distance d from its axis: a near-flat top rounding a little at the rim,
     * vertical sides (the jump at r is kept sheer by the envelope), then a talus skirt.
     */
    static double tower(double d, double r, double tall) {
        if (d < r) {
            double q = d / r;
            return tall * (1 - 0.05 * Math.pow(q, 6)) - 3 * q * q;
        }
        double t = (d - r) / (0.9 * r + 10);
        if (t >= 1) return 0;
        return Math.min(0.15 * tall, 40) * (1 - t) * (1 - t);
    }

    /** The envelope for a tower: its height {@code inset} blocks nearer the axis, over its height here. */
    static double envelope(double d, double r, double tall, double inset) {
        return Math.max(0, tower(Math.max(0, d - inset), r, tall) - tower(d, r, tall));
    }
}
