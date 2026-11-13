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
        // Forest is the common cover of temperate and warm country, plains the dry exception (the
        // user, 2026-11-10: "make forests more likely to occur than plains"). Plains were everything
        // drier than middling; now only the driest band is open, and the band above it is woodland
        // broken by clearings.
        if (t < 0.25) {
            // (the climate runs dry: a tenth of the land sits at the driest humidity, so the open band is kept narrow)
            if (w < -0.72) return v > 0.4 ? BiomeKind.SUNFLOWER_PLAINS : BiomeKind.PLAINS;
            if (w < -0.2) return v > 0.35 ? BiomeKind.FLOWER_FOREST : v > -0.3 ? BiomeKind.FOREST : BiomeKind.PLAINS;
            if (w < 0.3) return v > 0.1 ? BiomeKind.BIRCH_FOREST : BiomeKind.FOREST;
            return h < 86 ? BiomeKind.SWAMP : BiomeKind.DARK_FOREST;
        }
        if (t < 0.55) {
            if (w < -0.4) return h > 150 ? BiomeKind.SAVANNA_PLATEAU : BiomeKind.SAVANNA;
            if (w < -0.1) return v > 0.35 ? BiomeKind.FLOWER_FOREST : v > -0.25 ? BiomeKind.FOREST : BiomeKind.PLAINS;
            if (w < 0.45) return BiomeKind.FOREST;
            return BiomeKind.SPARSE_JUNGLE;
        }
        if (w < -0.25) {
            if (h > 170) return v > 0 ? BiomeKind.WOODED_BADLANDS : BiomeKind.BADLANDS;
            return BiomeKind.DESERT;
        }
        if (w < 0.05) return h > 150 ? BiomeKind.SAVANNA_PLATEAU : BiomeKind.SAVANNA;
        if (w < 0.3) return BiomeKind.SPARSE_JUNGLE;
        return h < 84 ? BiomeKind.MANGROVE_SWAMP : BiomeKind.JUNGLE;
    }

    /**
     * The family round a biome (the user, 2026-11-10: "people, including me, love the large variety of
     * forest biomes"; old growth birch, pale garden, cherry grove, grove, swamp and jungle were each
     * under 1% of the land, and six vanilla biomes could not appear at all). The climate table above
     * picks the broad cover; this breaks it into its kinds by two regional fields, so a forest country
     * holds birch woods, dark forest with pale gardens in it, flower forest and cherry groves side
     * by side, each a few hundred blocks across, a taiga has its old growth, a jungle its bamboo.
     *
     * @param p1 a regional field, about -1..1, ~700 blocks
     * @param p2 a second, independent one, ~520 blocks
     * @param interior 0 at the coast and on islands, 1 well inland
     */
    public static BiomeKind variety(BiomeKind b, double t, double w, double h, double p1, double p2, double interior) {
        switch (b) {
            case FOREST -> {
                if (h > 250 && p2 < -0.1) return BiomeKind.WINDSWEPT_FOREST;
                if (h < 82 && w > 0.05 && p2 < -0.3) return BiomeKind.SWAMP;
                if (t > -0.05 && p2 < -0.62) return BiomeKind.CHERRY_GROVE;
                if (p1 > 0.72) return BiomeKind.OLD_GROWTH_BIRCH_FOREST;
                if (p1 > 0.4) return BiomeKind.BIRCH_FOREST;
                if (p1 < -0.42 && w > -0.45) return p2 > 0.55 ? BiomeKind.PALE_GARDEN : BiomeKind.DARK_FOREST;
                if (p2 > 0.58) return BiomeKind.FLOWER_FOREST;
                return b;
            }
            case BIRCH_FOREST -> {
                return p1 > 0.55 ? BiomeKind.OLD_GROWTH_BIRCH_FOREST : p2 < -0.62 && t > -0.05 ? BiomeKind.CHERRY_GROVE : b;
            }
            case DARK_FOREST -> {
                return p2 > 0.58 ? BiomeKind.PALE_GARDEN : h < 82 && p2 < -0.35 ? BiomeKind.SWAMP : b;
            }
            case TAIGA -> {
                return p1 > 0.55 ? BiomeKind.OLD_GROWTH_PINE_TAIGA : p1 < -0.55 ? BiomeKind.OLD_GROWTH_SPRUCE_TAIGA : h > 240 && p2 > 0.1 ? BiomeKind.GROVE : b;
            }
            case SNOWY_SLOPES -> {
                return p1 > 0.0 && w > -0.5 ? BiomeKind.GROVE : b;
            }
            case SNOWY_PLAINS -> {
                return p2 > 0.42 ? BiomeKind.ICE_SPIKES : p1 > 0.15 && w > -0.6 ? BiomeKind.SNOWY_TAIGA : b;
            }
            case PLAINS -> {
                if (interior < 0.12 && p2 > 0.5 && Math.abs(t) < 0.45) return BiomeKind.MUSHROOM_FIELDS;
                return p2 > 0.38 ? BiomeKind.SUNFLOWER_PLAINS : b;
            }
            case SPARSE_JUNGLE -> {
                return p1 > 0.3 ? BiomeKind.BAMBOO_JUNGLE : p1 < -0.15 ? BiomeKind.JUNGLE : h < 80 && p2 < -0.4 ? BiomeKind.MANGROVE_SWAMP : b;
            }
            case JUNGLE -> {
                return p1 > 0.3 ? BiomeKind.BAMBOO_JUNGLE : b;
            }
            case SAVANNA, SAVANNA_PLATEAU -> {
                return p2 > 0.45 ? BiomeKind.WINDSWEPT_SAVANNA : b;
            }
            case WINDSWEPT_HILLS -> {
                return p1 > 0.2 ? BiomeKind.WINDSWEPT_GRAVELLY_HILLS : p1 < -0.25 ? BiomeKind.WINDSWEPT_FOREST : b;
            }
            case MEADOW -> {
                return p2 < -0.3 && t > 0.0 ? BiomeKind.CHERRY_GROVE : b;
            }
            default -> {
                return b;
            }
        }
    }
}
