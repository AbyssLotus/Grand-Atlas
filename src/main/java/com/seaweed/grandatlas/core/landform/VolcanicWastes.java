package com.seaweed.grandatlas.core.landform;

import static com.seaweed.grandatlas.core.Maths.smoothstep;

import com.seaweed.grandatlas.core.BiomeKind;
import com.seaweed.grandatlas.core.noise.Fractal;
import com.seaweed.grandatlas.core.noise.GradientNoise;
import com.seaweed.grandatlas.core.province.Province;
import com.seaweed.grandatlas.core.world.WorldField;

/**
 * Volcanic wastes (M6 fantasy, design §10): black basalt plains crossed by the lobed ridges of old
 * lava flows, over which stand great stratovolcanoes 220 to 450 blocks tall. Each cone's flanks are
 * concave and ribbed with gullies, its summit holds a crater with a lake of lava, and channels of
 * lava run from the rim down the flanks. The rule this fantasy breaks: the lava never cools.
 *
 * <p>The ground is shaped here. The lava itself goes in at decoration ({@code world/FantasyDetail}),
 * from {@link #volcano} (where the crater is) and {@link #channel} (how deep a channel is cut).
 */
public final class VolcanicWastes implements Landform {
    /** One volcano at most per cell this size; about four cells in five hold one. */
    public static final double CELL = 1100;
    private final long plain, flows, cones, gullies, rivers;

    public VolcanicWastes(long seed) {
        plain = GradientNoise.fork(seed, 130);
        flows = GradientNoise.fork(seed, 131);
        cones = GradientNoise.fork(seed, 132);
        gullies = GradientNoise.fork(seed, 133);
        rivers = GradientNoise.fork(seed, 134);
    }

    /** A volcano: centre, cone radius, height, crater radius and depth, and how many lava channels run from its rim. */
    public record Volcano(long key, double x, double z, double radius, double height, double crater, double depth, int channels, long seed) {}

    @Override public String id() { return "volcanic_wastes"; }
    @Override public Elevation elevation() { return Elevation.MID; }
    @Override public int color() { return 0x3A3030; }
    @Override public double hardness() { return 0.25; }
    // The cones shrink toward the province's edge, so a volcano never stops in a cliff.
    @Override public double taper() { return 380; }
    @Override public double taperFloor() { return 0.15; }

    @Override
    public double weight(WorldField.Point c) {
        if (c.temperature() < -0.25 || c.humidity() > 0.45 || c.interior() < 0.15) return 0;
        return 0.4 * smoothstep(-0.25, 0.1, c.temperature()) * (1 - smoothstep(0.2, 0.45, c.humidity()));
    }

    /** The volcano of the cell round (x, z) whose cone reaches it, or null. */
    public Volcano volcano(double x, double z) {
        int ci = GradientNoise.floor(x / CELL), cj = GradientNoise.floor(z / CELL);
        Volcano best = null;
        double bestQ = 1;
        for (int j = cj - 1; j <= cj + 1; j++) {
            for (int i = ci - 1; i <= ci + 1; i++) {
                Volcano v = cell(i, j);
                if (v == null) continue;
                double q = Math.hypot(x - v.x, z - v.z) / v.radius;
                if (q < bestQ) {
                    bestQ = q;
                    best = v;
                }
            }
        }
        return best;
    }

    private Volcano cell(int i, int j) {
        long h = GradientNoise.hash(cones, i, j);
        if (unit(h, 0) > 0.8) return null;
        double size = unit(h, 3);
        double radius = 360 + 240 * size;
        // Kept far enough inside the cell that two cones never overlap.
        double x = (i + 0.5 + 0.12 * (unit(h, 1) * 2 - 1)) * CELL, z = (j + 0.5 + 0.12 * (unit(h, 2) * 2 - 1)) * CELL;
        double height = 220 + 230 * size * (0.7 + 0.3 * unit(h, 4));
        double crater = radius * (0.1 + 0.05 * unit(h, 5));
        double depth = 35 + 35 * unit(h, 6);
        int channels = 3 + (int) (unit(h, 7) * 3);
        return new Volcano(((long) i << 32) ^ (j & 0xFFFFFFFFL), x, z, radius, height, crater, depth, channels, h);
    }

    /**
     * How deep a lava channel is cut here (0 off the channels): 2 to 4 blocks, along a few lines
     * that wander from the crater rim down to the cone's foot.
     */
    public double channel(double x, double z) {
        Volcano v = volcano(x, z);
        return v == null ? 0 : channel(v, x, z);
    }

    private double channel(Volcano v, double x, double z) {
        double dx = x - v.x, dz = z - v.z, d = Math.sqrt(dx * dx + dz * dz);
        if (d < v.crater * 1.05 || d > v.radius * 0.88) return 0;
        double ang = Math.atan2(dz, dx);
        double best = 0;
        for (int k = 0; k < v.channels; k++) {
            double a0 = unit(v.seed, 20 + k) * 2 * Math.PI;
            double a = a0 + 0.35 * GradientNoise.noise2(rivers ^ (v.seed + k), d / 140.0, k * 7.3) + 0.12 * GradientNoise.noise2(rivers ^ (v.seed + 31 * k), d / 35.0, k * 3.1);
            double diff = Math.atan2(Math.sin(ang - a), Math.cos(ang - a));
            double off = Math.abs(diff) * d;
            double halfWidth = 1.5 + 2.5 * (d / v.radius);
            if (off < halfWidth + 1.5) {
                double depth = (2 + 2 * (d / v.radius)) * (1 - smoothstep(halfWidth, halfWidth + 1.5, off));
                best = Math.max(best, depth);
            }
        }
        return best;
    }

    @Override
    public void shape(Province province, Frame frame, double x, double z, double edge, Shape out) {
        // The plain: low basalt swells, and over them the lobes and pressure ridges of old flows.
        double ground = 10 + 22 * Math.pow(Ordinary.fbm01(plain, x / 560.0, z / 560.0, 3), 1.4);
        double lobe = Ordinary.fbm01(flows, x / 110.0, z / 110.0, 3);
        ground += 7 * smoothstep(0.55, 0.85, lobe) + 2.5 * Math.abs(GradientNoise.noise2(flows ^ 0x77L, x / 24.0, z / 24.0));
        Zone zone = Zone.PLAIN;
        double rough = 0.12;
        Volcano v = volcano(x, z);
        if (v != null) {
            double dx = x - v.x, dz = z - v.z, d = Math.sqrt(dx * dx + dz * dz);
            double q = d / v.radius;
            // Concave flanks: steep near the summit, spreading out to the foot.
            double cone = v.height * Math.pow(1 - q, 1.7);
            // Gullies ribbing the flanks, deepest mid-slope, sampled round a circle so there's no seam.
            double ux = d > 0 ? dx / d : 1, uz = d > 0 ? dz / d : 0;
            double gully = Math.abs(GradientNoise.noise2(gullies ^ v.seed, 9 * ux + d / 300.0, 9 * uz));
            cone -= 18 * (1 - smoothstep(0.0, 0.25, gully)) * Math.sin(Math.PI * Math.min(1, q * 1.15)) * smoothstep(0.08, 0.2, q);
            double rimR = v.crater;
            double rim = v.height * Math.pow(1 - rimR / v.radius, 1.7);
            if (d < rimR) {
                // The crater: a bowl under the rim, its floor flat where the lava lake lies.
                double u = d / rimR;
                cone = rim + 4 * u - v.depth * (1 - smoothstep(0.55, 1.0, u));
            } else if (d < rimR * 1.35) {
                // The rim itself, a little raised over the flank.
                cone += 6 * (1 - (d - rimR) / (0.35 * rimR));
            }
            double ch = channel(v, x, z);
            cone -= ch;
            if (cone > 0) {
                ground = Math.max(ground, 8 + cone);
                if (cone > 35 || d < rimR * 1.35) {
                    zone = Zone.PEAK;
                    // Smooth where lava lies (crater, channels): 3D roughness would hole the walls and let it out.
                    rough = d < rimR * 1.15 || ch > 0 ? 0 : 0.2;
                }
            }
        }
        out.set(ground, zone, rough);
    }

    private static double unit(long h, int k) {
        return (GradientNoise.mix(h + k * 0x9E3779B97F4A7C15L) >>> 11) * 0x1.0p-53;
    }

    @Override
    public BiomeKind biome(Zone zone, double t, double w, double h, double v) {
        return zone == Zone.PEAK ? BiomeKind.VOLCANO : BiomeKind.VOLCANIC_WASTES;
    }
}
