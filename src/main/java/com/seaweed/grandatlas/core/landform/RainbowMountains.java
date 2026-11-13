package com.seaweed.grandatlas.core.landform;

import static com.seaweed.grandatlas.core.Maths.smoothstep;

import com.seaweed.grandatlas.core.BiomeKind;
import com.seaweed.grandatlas.core.Biomes;
import com.seaweed.grandatlas.core.noise.Fractal;
import com.seaweed.grandatlas.core.noise.GradientNoise;
import com.seaweed.grandatlas.core.province.Province;
import com.seaweed.grandatlas.core.world.WorldField;

/**
 * Rainbow mountains (M5 real-world wonders, design §9; after Zhangye Danxia in Gansu and
 * Vinicunca in Peru): dry country of steep, sharp-crested ridges, 40 to 190 blocks tall, cut by
 * branching gullies down to flat valley floors, and striped from foot to crest in bands of red,
 * orange, gold, cream, pale green, teal and lavender. The shape is here; the bands are the
 * biome's own rock ({@code world/RainbowBands}, a surface rule), level on average but folded
 * gently, so a ridge's flank shows stripes that rise and dip along it.
 */
public final class RainbowMountains implements Landform {
    private final long ridges, gullies, mass, valley;

    public RainbowMountains(long seed) {
        ridges = GradientNoise.fork(seed, 170);
        gullies = GradientNoise.fork(seed, 171);
        mass = GradientNoise.fork(seed, 172);
        valley = GradientNoise.fork(seed, 173);
    }

    @Override public String id() { return "rainbow_mountains"; }
    @Override public Elevation elevation() { return Elevation.MID; }
    @Override public int color() { return 0xE07050; }
    @Override public double hardness() { return 0.2; }
    @Override public double taper() { return 260; }
    @Override public double taperFloor() { return 0.2; }

    @Override
    public double weight(WorldField.Point c) {
        // Dry, temperate to warm interiors: the loess and sandstone country the colours need.
        if (c.temperature() < -0.3 || c.humidity() > 0.05 || c.interior() < 0.25) return 0;
        return 0.3 * smoothstep(-0.3, 0.0, c.temperature()) * (1 - smoothstep(-0.2, 0.05, c.humidity()));
    }

    @Override
    public void shape(Province province, Frame frame, double x, double z, double edge, Shape out) {
        // Where the ridges crowd together (the massifs) and where valleys open between them.
        double massif = smoothstep(0.3, 0.7, Ordinary.fbm01(mass, x / 700.0, z / 700.0, 3));
        // Sharp crests: a ridged noise, warped so the ridges wind.
        double wx = x + 60 * GradientNoise.noise2(ridges ^ 0x11L, x / 300.0, z / 300.0);
        double wz = z + 60 * GradientNoise.noise2(ridges ^ 0x12L, x / 300.0, z / 300.0);
        double crest = 1 - Math.abs(Fractal.fbm(ridges, wx / 340.0, wz / 340.0, 4));
        crest = Math.pow(crest, 2.4);
        // Gullies: fine branching cuts down the flanks.
        double g = 1 - Math.abs(GradientNoise.noise2(gullies, x / 55.0, z / 55.0));
        double gully = Math.pow(g, 6) * 0.25;
        double tall = 40 + 150 * massif * (0.6 + 0.4 * province.param(0));
        double hill = tall * Math.max(0, crest - gully);
        // The valley floors: flat, a little above the land round them.
        double floor = 10 + 6 * Ordinary.fbm01(valley, x / 240.0, z / 240.0, 2);
        double relief = floor + hill;
        Zone zone = hill > 0.55 * tall && hill > 30 ? Zone.PEAK : hill > 14 ? Zone.HILL : Zone.VALLEY;
        out.set(relief, zone, 0.03);
        out.micro = 0.5;
    }

    @Override
    public BiomeKind biome(Zone zone, double t, double w, double h, double v) {
        if (h > Biomes.peakLine(t) + 20) return Biomes.peaks(t);
        if (zone == Zone.VALLEY) return t > 0.25 ? BiomeKind.SAVANNA : BiomeKind.PLAINS;
        return BiomeKind.RAINBOW_MOUNTAINS;
    }
}
