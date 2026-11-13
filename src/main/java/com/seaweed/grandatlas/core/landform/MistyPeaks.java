package com.seaweed.grandatlas.core.landform;

import static com.seaweed.grandatlas.core.Maths.smoothstep;

import com.seaweed.grandatlas.core.BiomeKind;
import com.seaweed.grandatlas.core.Biomes;
import com.seaweed.grandatlas.core.noise.Fractal;
import com.seaweed.grandatlas.core.noise.GradientNoise;
import com.seaweed.grandatlas.core.province.Province;
import com.seaweed.grandatlas.core.world.WorldField;

/**
 * Misty peaks (M6 fantasy, design §10; after Huangshan and Zhangjiajie, our own take): a raised
 * upland cut by deep, steep gorges, and out of it a forest of granite needles 80 to 320 blocks
 * tall, sheer-sided, with flat summits where pines cling. The gorges drop below cloud height
 * (vanilla's clouds at 192), so from the summits the needles stand out of a sea of cloud.
 *
 * <p>Needles are jittered cells 72 blocks apart; about half the cells hold one, more in the
 * needle districts. A needle's outline wobbles with the angle (granite splits into ribs), its
 * sides are vertical through {@link Shape#envelope}, and a skirt of talus spreads at its foot.
 * The rule this fantasy breaks: needles that thin and that tall would not stand.
 */
public final class MistyPeaks implements Landform {
    private static final double CELL = 72;
    private final long upland, gorges, district, needles, ribs;

    public MistyPeaks(long seed) {
        upland = GradientNoise.fork(seed, 120);
        gorges = GradientNoise.fork(seed, 121);
        district = GradientNoise.fork(seed, 122);
        needles = GradientNoise.fork(seed, 123);
        ribs = GradientNoise.fork(seed, 124);
    }

    @Override public String id() { return "misty_peaks"; }
    @Override public Elevation elevation() { return Elevation.HIGH; }
    @Override public int color() { return 0xB8C8C0; }
    // The upland ends in cliffs, and its needles shrink toward the edge rather than stopping at it.
    @Override public double hardness() { return 0.55; }
    @Override public double taper() { return 260; }
    @Override public double taperFloor() { return 0.3; }

    @Override
    public double weight(WorldField.Point c) {
        // Temperate to warm, wet country, well inland: the mist needs the damp.
        if (c.humidity() < 0.0 || c.temperature() < -0.35 || c.temperature() > 0.6 || c.interior() < 0.3) return 0;
        return 0.45 * smoothstep(0.0, 0.3, c.humidity()) * (1 - smoothstep(0.35, 0.6, c.temperature()));
    }

    @Override
    public void shape(Province province, Frame frame, double x, double z, double edge, Shape out) {
        double base = base(province, x, z);
        // Needles from the cells round this one: the tallest wins.
        int ci = GradientNoise.floor(x / CELL), cj = GradientNoise.floor(z / CELL);
        double best = 0, bestEnv = 0;
        boolean onNeedle = false;
        for (int j = cj - 1; j <= cj + 1; j++) {
            for (int i = ci - 1; i <= ci + 1; i++) {
                long h = GradientNoise.hash(needles, i, j);
                double nx = (i + 0.2 + 0.6 * unit(h, 1)) * CELL, nz = (j + 0.2 + 0.6 * unit(h, 2)) * CELL;
                // Whether a needle stands is the needle's own call (judged at its axis), never the column's.
                double dens = 0.35 + 0.5 * smoothstep(0.35, 0.7, Ordinary.fbm01(district, nx / 900.0, nz / 900.0, 2));
                if (unit(h, 0) >= dens) continue;
                double dx = x - nx, dz = z - nz;
                double d = Math.sqrt(dx * dx + dz * dz);
                double size = unit(h, 3);
                double r = 7 + 17 * size;
                if (d > r * 2.2 + 8) continue;
                // Ribs: the outline wobbles with the angle, sampled on a circle so it has no seam.
                double ux = d > 0 ? dx / d : 1, uz = d > 0 ? dz / d : 0;
                double rib = GradientNoise.noise2(ribs ^ h, nx / 9.0 + 2.2 * ux, nz / 9.0 + 2.2 * uz);
                double rr = r * (1 + 0.22 * rib);
                double tall = 80 + 240 * Math.pow(unit(h, 4), 1.4) * (0.6 + 0.4 * size);
                double top = needle(d, rr, tall);
                double env = needle(Math.max(0, d - 6), rr, tall);
                if (top > best) best = top;
                if (env > bestEnv) bestEnv = env;
                if (d < rr) onNeedle = true;
            }
        }
        double relief = base + best;
        Zone zone = onNeedle ? Zone.PEAK : base < gorgeFloor(province) + 30 ? Zone.VALLEY : Zone.UPLAND;
        out.set(relief, zone, onNeedle || bestEnv > best + 1 ? 0.03 : 0.35);
        out.envelope = Math.max(0, bestEnv - best);
        out.micro = onNeedle ? 0.2 : 1;
    }

    /** A needle's height at distance d from its axis: a flat summit rounding off at the rim, then a talus skirt. */
    private static double needle(double d, double r, double tall) {
        if (d < r) {
            double q = d / r;
            return tall * (1 - 0.06 * q * q * q * q * q * q) - 3 * q * q;
        }
        double t = (d - r) / (1.2 * r);
        if (t >= 1) return 0;
        return 0.18 * tall * Math.min(1, 0.55 + 0.45 * (1 - t)) * (1 - t) * (1 - t);
    }

    private double gorgeFloor(Province p) {
        return 25;
    }

    /** The upland the needles stand on: high, rolling, cut by steep gorges that wind between them. */
    private double base(Province p, double x, double z) {
        double plateau = 150 + 90 * p.param(0);
        double roll = Ordinary.fbm01(upland, x / 520.0, z / 520.0, 4);
        double g = Math.abs(Fractal.fbm(gorges, x / 760.0, z / 760.0, 3));
        double g2 = Math.abs(GradientNoise.noise2(gorges ^ 0x6A11L, x / 260.0, z / 260.0));
        // Steep-walled gorges: near zero of the noise the ground drops nearly to the floor.
        double cut = 1 - smoothstep(0.03, 0.16, g);
        double side = 1 - smoothstep(0.02, 0.09, g2);
        double h = plateau * (0.72 + 0.28 * roll);
        h -= (h - gorgeFloor(p)) * Math.max(cut, 0.55 * side * smoothstep(0.2, 0.45, g));
        return h;
    }

    private static double unit(long h, int k) {
        return (GradientNoise.mix(h + k * 0x9E3779B97F4A7C15L) >>> 11) * 0x1.0p-53;
    }

    @Override
    public BiomeKind biome(Zone zone, double t, double w, double h, double v) {
        if (h > Biomes.peakLine(t) + 40) return Biomes.peaks(t);
        return zone == Zone.PEAK || zone == Zone.UPLAND && h > 330 ? BiomeKind.MISTY_PEAKS : BiomeKind.CLOUD_FOREST;
    }
}
