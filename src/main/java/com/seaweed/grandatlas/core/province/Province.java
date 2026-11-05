package com.seaweed.grandatlas.core.province;

import com.seaweed.grandatlas.core.landform.Landform;
import com.seaweed.grandatlas.core.noise.GradientNoise;

/**
 * One cell of the province mosaic: a centre (in the mosaic's warped space), the landform it
 * holds, and a seed for that province's own parameters (a range's height, a plateau's rim).
 */
public record Province(int cellX, int cellZ, double x, double z, Landform landform, long seed) {

    /** A stable per-province parameter in [0, 1); {@code k} picks which one. */
    public double param(int k) {
        return (GradientNoise.hash(seed, k, 0) >>> 11) * 0x1.0p-53;
    }

    public long key() {
        return ProvinceMosaic.key(cellX, cellZ);
    }
}
