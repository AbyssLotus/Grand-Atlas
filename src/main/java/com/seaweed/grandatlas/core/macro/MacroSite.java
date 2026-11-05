package com.seaweed.grandatlas.core.macro;

import com.seaweed.grandatlas.core.noise.GradientNoise;
import com.seaweed.grandatlas.core.world.WorldField;

/**
 * One placed macro landform: its kind, centre, footprint radius, the context at its centre,
 * and a seed for its own parameters (a plateau's lift, where its ramps are).
 */
public record MacroSite(Macro kind, int cellX, int cellZ, double x, double z, double radius,
                        WorldField.Point centre, long seed) {

    /** A stable per-site parameter in [0, 1); {@code k} picks which one. */
    public double param(int k) {
        return (GradientNoise.hash(seed, k, 1) >>> 11) * 0x1.0p-53;
    }

    /** Furthest the footprint reaches, edge band included (the footprint's direction noise is at most ±21%). */
    public double reachBlocks() {
        return radius * 1.21 + kind.reachBlocks(this);
    }
}
