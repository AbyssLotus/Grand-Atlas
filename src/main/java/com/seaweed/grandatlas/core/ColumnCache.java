package com.seaweed.grandatlas.core;

import com.seaweed.grandatlas.core.noise.GradientNoise;
import java.util.Arrays;

/**
 * A small direct-mapped cache of column samples, one per thread (C2ME and Distant Horizons
 * generate on many threads at once). The density function asks for the same column once
 * per cell layer (192 layers in a 1536-tall world), so nearly every lookup is a hit.
 */
final class ColumnCache {
    private static final int SIZE = 4096; // power of two
    private static final long EMPTY = Long.MIN_VALUE; // x = Integer.MIN_VALUE never occurs

    private final long[] keys = new long[SIZE];
    private final ColumnSample[] values = new ColumnSample[SIZE];

    ColumnCache() {
        Arrays.fill(keys, EMPTY);
    }

    ColumnSample get(AtlasCore core, int x, int z) {
        long key = ((long) x << 32) | (z & 0xFFFFFFFFL);
        int slot = (int) GradientNoise.mix(key) & (SIZE - 1);
        if (keys[slot] == key) {
            return values[slot];
        }
        ColumnSample sample = core.sample(x, z);
        keys[slot] = key;
        values[slot] = sample;
        return sample;
    }
}
