package com.seaweed.grandatlas.core;

/**
 * The climate table: vanilla biomes by temperature, humidity and absolute height. Ordinary
 * landforms use it for every zone; wonder landforms use it for their generic zones (valleys,
 * lower slopes) and override the rest.
 */
public final class Biomes {
    private Biomes() {}

    /** Snow line for peaks; hotter climates push it up. */
    public static double peakLine(double t) {
        return 560 + t * 160;
    }

    public static BiomeKind climate(double t, double w, double h, double v) {
        double peakLine = peakLine(t);
        if (h > peakLine) {
            return peaks(t);
        }
        if (h > peakLine - 170) {
            return highSlopes(t, w, v);
        }
        return lowland(t, w, h, v);
    }

    public static BiomeKind peaks(double t) {
        // Bare stone, not snow, from 0.35: snowy peaks over a hot desert should be rare.
        return t < -0.25 ? BiomeKind.FROZEN_PEAKS : t < 0.35 ? BiomeKind.JAGGED_PEAKS : BiomeKind.STONY_PEAKS;
    }

    public static BiomeKind highSlopes(double t, double w, double v) {
        if (t < -0.1) return BiomeKind.SNOWY_SLOPES;
        if (w < -0.3) return t > 0.3 ? BiomeKind.WOODED_BADLANDS : BiomeKind.WINDSWEPT_HILLS; // dry heights, not green meadow
        if (w > 0.25) return BiomeKind.GROVE;
        return t > 0.3 && v > 0.3 ? BiomeKind.CHERRY_GROVE : BiomeKind.MEADOW;
    }

    public static BiomeKind lowland(double t, double w, double h, double v) {
        if (t < -0.5) return w < 0 ? BiomeKind.SNOWY_PLAINS : BiomeKind.SNOWY_TAIGA;
        if (t < -0.15) return w > 0.4 ? BiomeKind.OLD_GROWTH_SPRUCE_TAIGA : BiomeKind.TAIGA;
        if (t < 0.25) {
            if (w < -0.35) return v > 0.4 ? BiomeKind.SUNFLOWER_PLAINS : BiomeKind.PLAINS;
            if (w < 0.05) return v > 0.25 ? BiomeKind.FLOWER_FOREST : BiomeKind.PLAINS;
            if (w < 0.35) return v > 0 ? BiomeKind.BIRCH_FOREST : BiomeKind.FOREST;
            return h < 72 ? BiomeKind.SWAMP : BiomeKind.DARK_FOREST;
        }
        if (t < 0.55) {
            if (w < -0.3) return h > 150 ? BiomeKind.SAVANNA_PLATEAU : BiomeKind.SAVANNA;
            if (w < 0.1) return v > 0.35 ? BiomeKind.FLOWER_FOREST : BiomeKind.PLAINS;
            if (w < 0.45) return BiomeKind.FOREST;
            return BiomeKind.SPARSE_JUNGLE;
        }
        if (w < -0.25) {
            if (h > 170) return v > 0 ? BiomeKind.WOODED_BADLANDS : BiomeKind.BADLANDS;
            return BiomeKind.DESERT;
        }
        if (w < 0.15) return h > 150 ? BiomeKind.SAVANNA_PLATEAU : BiomeKind.SAVANNA;
        if (w < 0.45) return BiomeKind.SPARSE_JUNGLE;
        return h < 70 ? BiomeKind.MANGROVE_SWAMP : BiomeKind.JUNGLE;
    }
}
