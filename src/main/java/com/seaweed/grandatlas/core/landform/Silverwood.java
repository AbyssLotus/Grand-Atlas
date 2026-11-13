package com.seaweed.grandatlas.core.landform;

import static com.seaweed.grandatlas.core.Maths.smoothstep;

import com.seaweed.grandatlas.core.BiomeKind;
import com.seaweed.grandatlas.core.Biomes;
import com.seaweed.grandatlas.core.noise.Fractal;
import com.seaweed.grandatlas.core.noise.GradientNoise;
import com.seaweed.grandatlas.core.province.Province;
import com.seaweed.grandatlas.core.world.WorldField;

/**
 * The Silverwood (M6 fantasy, design §10, "elven wood" in the design; our own name): soft valleys
 * and gentle rounded hills under colossal silver-barked, golden-leaved trees, with clear streams
 * in every fold and flower meadows in the clearings. The trees are the fantasy: birch-barked giants
 * whose leaves the biome colours gold. world/AtlasAssets plants the god trees thick here, and the
 * biome's own trees fill in between.
 */
public final class Silverwood implements Landform {
    private final long hills, folds;

    public Silverwood(long seed) {
        hills = GradientNoise.fork(seed, 140);
        folds = GradientNoise.fork(seed, 141);
    }

    @Override public String id() { return "silverwood"; }
    @Override public Elevation elevation() { return Elevation.LOW; }
    @Override public int color() { return 0xE0C060; }
    @Override public double hardness() { return 0.1; }
    @Override public double taper() { return 200; }
    @Override public double taperFloor() { return 0.5; }

    @Override
    public double weight(WorldField.Point c) {
        if (c.temperature() < -0.25 || c.temperature() > 0.45 || c.humidity() < -0.35 || c.interior() < 0.2) return 0;
        return 0.4 * smoothstep(-0.35, -0.05, c.humidity()) * (1 - smoothstep(0.3, 0.45, c.temperature()));
    }

    @Override
    public void shape(Province province, Frame frame, double x, double z, double edge, Shape out) {
        // Rounded hills, and between them soft folds where the streams run.
        double hill = Math.pow(Ordinary.fbm01(hills, x / 480.0, z / 480.0, 4), 1.3);
        double fold = Math.abs(Fractal.fbm(folds, x / 650.0, z / 650.0, 3));
        double valley = 1 - smoothstep(0.02, 0.22, fold);
        double relief = 14 + 62 * hill * (1 - 0.7 * valley) + 4 * GradientNoise.noise2(hills ^ 0x51L, x / 70.0, z / 70.0);
        out.set(relief, valley > 0.6 ? Zone.VALLEY : hill > 0.55 ? Zone.HILL : Zone.PLAIN, 0.02);
    }

    @Override
    public BiomeKind biome(Zone zone, double t, double w, double h, double v) {
        if (h > Biomes.peakLine(t) - 170) return Biomes.climate(t, w, h, v);
        return BiomeKind.SILVERWOOD;
    }
}
