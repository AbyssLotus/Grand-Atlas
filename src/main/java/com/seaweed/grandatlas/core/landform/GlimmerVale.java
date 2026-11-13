package com.seaweed.grandatlas.core.landform;

import static com.seaweed.grandatlas.core.Maths.smoothstep;

import com.seaweed.grandatlas.core.BiomeKind;
import com.seaweed.grandatlas.core.Biomes;
import com.seaweed.grandatlas.core.noise.Fractal;
import com.seaweed.grandatlas.core.noise.GradientNoise;
import com.seaweed.grandatlas.core.province.Province;
import com.seaweed.grandatlas.core.world.WorldField;

/**
 * The Glimmer Vale (M6 fantasy, design §10, "enchanted valley"): a broad, sheltered valley winding
 * between steep green ridges, its floor lush with flowers and flowering azalea, and a glowing river
 * down its length: its own water colour and a bed of glowing sea pickles. Its river biome is
 * {@link BiomeKind#GLIMMER_RIVER}, set where the core picks river biomes. Motes drift in the air.
 * The ridges are high enough that the drainage network brings streams down them in falls.
 */
public final class GlimmerVale implements Landform {
    private final long axis, ridges, floor;

    public GlimmerVale(long seed) {
        axis = GradientNoise.fork(seed, 150);
        ridges = GradientNoise.fork(seed, 151);
        floor = GradientNoise.fork(seed, 152);
    }

    @Override public String id() { return "glimmer_vale"; }
    @Override public Elevation elevation() { return Elevation.MID; }
    @Override public int color() { return 0x50E0B0; }
    @Override public double hardness() { return 0.3; }
    @Override public double taper() { return 240; }
    @Override public double taperFloor() { return 0.35; }

    @Override
    public double weight(WorldField.Point c) {
        if (c.temperature() < -0.15 || c.temperature() > 0.55 || c.humidity() < -0.1 || c.interior() < 0.2) return 0;
        return 0.35 * smoothstep(-0.1, 0.2, c.humidity());
    }

    @Override
    public void shape(Province province, Frame frame, double x, double z, double edge, Shape out) {
        // The valley follows a zero line of a slow noise, so it winds through the province; the
        // ridges rise steeply either side of a flat floor a few hundred blocks wide.
        double n = Math.abs(Fractal.fbm(axis, x / 1500.0, z / 1500.0, 2) + 0.15 * GradientNoise.noise2(axis ^ 0x9L, x / 300.0, z / 300.0));
        double wall = smoothstep(0.06, 0.3, n);
        double crest = 110 + 70 * province.param(2);
        double ridge = crest * Math.pow(wall, 1.5) * (0.75 + 0.25 * Fractal.erodedRidges(ridges, x / 420.0, z / 420.0, 4));
        double bed = 6 + 6 * Ordinary.fbm01(floor, x / 240.0, z / 240.0, 3);
        out.set(bed + ridge, wall < 0.15 ? Zone.VALLEY : wall < 0.6 ? Zone.LOW_SLOPE : Zone.HILL, wall > 0.4 ? 0.3 : 0.02);
    }

    @Override
    public BiomeKind biome(Zone zone, double t, double w, double h, double v) {
        if (h > Biomes.peakLine(t) - 170) return Biomes.climate(t, w, h, v);
        return zone == Zone.HILL ? Biomes.variety(Biomes.lowland(t, Math.max(w, 0.0), h, v), t, w, h, v, -v, 1) : BiomeKind.GLIMMER_VALE;
    }
}
