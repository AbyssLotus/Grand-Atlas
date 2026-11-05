package com.seaweed.grandatlas.core.landform;

import static com.seaweed.grandatlas.core.Maths.lerp;
import static com.seaweed.grandatlas.core.Maths.smoothstep;

import com.seaweed.grandatlas.core.BiomeKind;
import com.seaweed.grandatlas.core.Biomes;
import com.seaweed.grandatlas.core.noise.Fractal;
import com.seaweed.grandatlas.core.noise.GradientNoise;
import com.seaweed.grandatlas.core.province.Province;
import com.seaweed.grandatlas.core.world.WorldField;

/**
 * Megarange (design §10): eroded ridges 520–780 blocks over a datum that tectonic belts have
 * already lifted, with glacial cirques carved into the upper flanks. Where three bowls bite
 * into one summit, a horn is left standing.
 *
 * <p>The ridge field is seeded per landform, not per province, so neighbouring megarange
 * provinces along a belt form one continuous chain; only the amplitude differs.
 *
 * <p><b>Styles</b> (playtest round 2: "too sweepy and smooth, tips always rounded, they all rise
 * like steep ramps"). Three slow regional fields (~3 km) blend continuously, so one range can
 * pass from one style to the next:
 * <ul>
 *   <li><b>alpine</b>: the slope profile's exponent runs 1.3 to 2.4. High values give concave
 *       flanks that steepen into sharp pyramid summits.</li>
 *   <li><b>dolomite</b>: the upper slopes go through the canyon strata in 45-70-block bands:
 *       cliff bands and towers over talus.</li>
 *   <li><b>needles</b>: sharp spires 50-140 blocks tall on high ridges.</li>
 *   <li><b>crags</b> (the user, 2026-11-03: "a more aggressive vibe, but only sometimes"): summits
 *       split into twin and triple towers by sheer clefts, blocky tiers on the high slopes, leaning
 *       slabs of rock on the flanks, and roughness past 1, which the density turns into shelves
 *       jutting over undercuts and deep vertical cracks.</li>
 * </ul>
 * Every summit also gets knife-edge micro-ridges. Amplitude is capped so raw summits stay under
 * the peak cap's compression curve (which had been rounding the tallest tips).
 *
 * <p><b>Cirques.</b> Bowls sit on a 420-block jittered grid; 60% of cells host one, and only
 * on steep upper flanks (on a broad summit a bowl reads as a crater). Each bowl is stretched
 * downhill, so its flat floor (a tarn once lakes exist, M4) runs out into a mouth below a steep
 * headwall. Its wall keeps climbing past the radius, so the smooth-min that carves it never
 * cuts off at the evaluation edge.
 */
public final class Megarange implements Landform {
    private static final double CIRQUE_SPACING = 420;
    /** Downhill distances count this much, so a bowl reaches 1/MOUTH times farther downhill. */
    private static final double MOUTH = 0.55;
    private static final double GRAD_STEP = 24;
    private static final double NEEDLE_SPACING = 260;
    /** Highest raw summit (datum + relief) a range aims for; the peak cap only starts at 900. */
    private static final double SUMMIT = 945;

    private final long ridges, cirques, crags, styleAlpine, styleDolomite, styleNeedles, needles, styleCrags, slabs;
    private final ThreadLocal<RidgeCache> ridgeCache = ThreadLocal.withInitial(RidgeCache::new);

    public Megarange(long seed) {
        ridges = GradientNoise.fork(seed, 10);
        cirques = GradientNoise.fork(seed, 11);
        crags = GradientNoise.fork(seed, 12);
        styleAlpine = GradientNoise.fork(seed, 13);
        styleDolomite = GradientNoise.fork(seed, 14);
        styleNeedles = GradientNoise.fork(seed, 15);
        needles = GradientNoise.fork(seed, 16);
        styleCrags = GradientNoise.fork(seed, 17);
        slabs = GradientNoise.fork(seed, 18);
    }

    @Override public String id() { return "megarange"; }
    @Override public Elevation elevation() { return Elevation.HIGH; }
    @Override public int color() { return 0xE8E4E0; }
    @Override public double sameNeighbour() { return 2.2; }
    @Override public double hardness() { return 0.3; }
    @Override public double taper() { return 400; }
    @Override public double taperFloor() { return 0.12; }

    @Override
    public double weight(WorldField.Point c) {
        if (c.interior() < 0.25) return 0;
        return 0.03 + 9 * c.belt() * c.belt() * c.belt();
    }

    /**
     * Light slope damping (0.12, not the default 0.35): strong damping kept steep slopes almost
     * free of detail, which read as smooth cones up close.
     */
    private double ridge(double x, double z) {
        return Fractal.erodedRidges(ridges, x / 2300.0, z / 2300.0, 8, 0.12);
    }

    /** Slope-profile exponent at a point: the alpine style field, 1.3 (convex) to 2.4 (concave, sharp). */
    private double exponent(double x, double z) {
        return 1.3 + 1.1 * Ordinary.fbm01(styleAlpine, x / 3000.0, z / 3000.0, 2);
    }

    /** Fraction of the province amplitude for a ridge value. */
    private static double profile(double ridge, double exponent) {
        return 0.08 + 0.92 * Math.pow(ridge, exponent);
    }

    @Override
    public void shape(Province province, Frame frame, double x, double z, double edge, Shape out) {
        double amp = Math.max(200, Math.min(520 + 260 * province.param(1), SUMMIT - frame.datum()));
        double fraction = profile(ridge(x, z), exponent(x, z));
        double base = amp * fraction;
        // Crags: ridged detail of a few dozen blocks on the upper slopes, where rock shows.
        double crag = Fractal.erodedRidges(crags, x / 170.0, z / 170.0, 4, 0.0) - 0.45;
        base += amp * 0.07 * crag * smoothstep(0.2, 0.55, fraction)
                + 6 * Fractal.fbm(crags ^ 0x31L, x / 60.0, z / 60.0, 2);
        // Knife-edge micro-ridges on the summits, so tips come to a point instead of a dome.
        double knife = 1 - Math.abs(GradientNoise.noise2(crags ^ 0x77L, x / 70.0, z / 70.0));
        base += amp * 0.08 * (knife * knife * knife - 0.4) * smoothstep(0.6, 0.95, fraction);
        // Dolomite style: cliff bands and towers on the upper slopes.
        double dolomite = smoothstep(0.55, 0.75, Ordinary.fbm01(styleDolomite, x / 2600.0, z / 2600.0, 2))
                * smoothstep(0.25, 0.5, fraction);
        if (dolomite > 0) {
            double band = 45 + 25 * province.param(3);
            double phase = 0.3 * GradientNoise.noise2(styleDolomite ^ 0x9L, x / 80.0, z / 80.0);
            base = lerp(dolomite, base, CanyonPlateau.strata(base, band, phase));
        }
        // Needles: spires on high ridges.
        double needleField = smoothstep(0.55, 0.75, Ordinary.fbm01(styleNeedles, x / 2400.0, z / 2400.0, 2))
                * smoothstep(0.45, 0.65, fraction);
        if (needleField > 0) {
            base += needleField * needle(x, z);
        }

        // Crags: only in some ranges (a slow regional field), the most aggressive face of a range.
        double cragField = smoothstep(0.58, 0.74, Ordinary.fbm01(styleCrags, x / 2800.0, z / 2800.0, 2));
        double rough = 0.25 + 0.75 * smoothstep(0.15, 0.6, fraction);
        if (cragField > 0) {
            // Blocky tiers on the high slopes, sharper than the dolomites'.
            double tiers = cragField * smoothstep(0.3, 0.55, fraction);
            if (tiers > 0) {
                double phase = 0.4 * GradientNoise.noise2(styleCrags ^ 0x9L, x / 60.0, z / 60.0);
                base = lerp(0.8 * tiers, base, CanyonPlateau.strata(base, 28 + 12 * province.param(4), phase));
            }
            // Clefts: a few narrow sheer gaps splitting summits into towers (the twin peaks), short
            // and straight, and only on the highest ground (as lines of a noise they ran everywhere
            // and wound like worm tracks).
            double high = smoothstep(0.62, 0.85, fraction) * cragField;
            if (high > 0) base -= high * amp * 0.3 * cleft(x, z);
            // Sharper tips.
            base += cragField * amp * 0.06 * (knife * knife * knife * knife - 0.3) * smoothstep(0.55, 0.9, fraction);
            // Leaning slabs on the flanks.
            base += cragField * smoothstep(0.08, 0.2, fraction) * (1 - smoothstep(0.55, 0.7, fraction)) * slab(x, z);
            rough += cragField * smoothstep(0.2, 0.5, fraction);
        }

        double relief = base;
        boolean cirqueFloor = false;
        int gx0 = GradientNoise.floor(x / CIRQUE_SPACING), gz0 = GradientNoise.floor(z / CIRQUE_SPACING);
        for (int dz = -1; dz <= 1; dz++) {
            for (int dx = -1; dx <= 1; dx++) {
                int gx = gx0 + dx, gz = gz0 + dz;
                long h = GradientNoise.hash(cirques, gx, gz);
                if (unit(h, 0) > 0.6) continue;
                double cx = (gx + 0.2 + 0.6 * unit(h, 1)) * CIRQUE_SPACING;
                double cz = (gz + 0.2 + 0.6 * unit(h, 2)) * CIRQUE_SPACING;
                double radius = 120 + 70 * unit(h, 3);
                if (Math.hypot(x - cx, z - cz) > radius * 1.6 / MOUTH) continue;
                double[] centreRidge = ridgeCache.get().get(this, gx, gz, cx, cz);
                double e = centreRidge[5];
                double centreFraction = profile(centreRidge[0], e);
                if (centreFraction < 0.38 || centreFraction > 0.78) continue;
                // Flanks only: on a broad summit a bowl reads as a crater.
                double gradX = (profile(centreRidge[1], e) - profile(centreRidge[2], e)) / (2 * GRAD_STEP);
                double gradZ = (profile(centreRidge[3], e) - profile(centreRidge[4], e)) / (2 * GRAD_STEP);
                double slope = Math.hypot(gradX, gradZ);
                if (slope * amp < 0.35) continue;

                // Stretch the bowl downhill so its floor runs out into a mouth below the headwall.
                double downX = -gradX / slope, downZ = -gradZ / slope;
                double along = (x - cx) * downX + (z - cz) * downZ;
                double across = -(x - cx) * downZ + (z - cz) * downX;
                double d = Math.hypot(across, along > 0 ? along * MOUTH : along);
                if (d > radius * 1.6) continue;

                double centre = amp * centreFraction;
                double floor = centre - (70 + 50 * unit(h, 4));
                double t = d / radius;
                double bowl = floor + (centre + 30 - floor) * Math.pow(smoothstep(0.35, 1.15, t), 1.4)
                        + Math.max(0, t - 0.9) * radius * 4;
                relief = smoothMin(relief, bowl, 14);
                if (t < 0.4 && bowl < base - 10) cirqueFloor = true;
            }
        }

        Zone zone = cirqueFloor ? Zone.CIRQUE
                : fraction > 0.72 ? Zone.PEAK
                : fraction > 0.45 ? Zone.HIGH_SLOPE
                : fraction > 0.2 ? Zone.LOW_SLOPE
                : Zone.VALLEY;
        out.set(relief, zone, rough);
    }

    @Override
    public BiomeKind biome(Zone zone, double t, double w, double h, double v) {
        return switch (zone) {
            case PEAK -> Biomes.peaks(t);
            case HIGH_SLOPE -> h > Biomes.peakLine(t) ? Biomes.peaks(t) : Biomes.highSlopes(t, w, v);
            case CIRQUE -> t < -0.1 ? BiomeKind.SNOWY_SLOPES : BiomeKind.MEADOW;
            default -> Biomes.climate(t, w, h, v);
        };
    }

    /** Spires 50-140 blocks tall and 18-40 wide on a third of 260-block cells, with sharp tips. */
    private double needle(double x, double z) {
        int gx0 = GradientNoise.floor(x / NEEDLE_SPACING), gz0 = GradientNoise.floor(z / NEEDLE_SPACING);
        double best = 0;
        for (int dz = -1; dz <= 1; dz++) {
            for (int dx = -1; dx <= 1; dx++) {
                long h = GradientNoise.hash(needles, gx0 + dx, gz0 + dz);
                if (unit(h, 0) > 0.35) continue;
                double cx = (gx0 + dx + 0.2 + 0.6 * unit(h, 1)) * NEEDLE_SPACING;
                double cz = (gz0 + dz + 0.2 + 0.6 * unit(h, 2)) * NEEDLE_SPACING;
                double radius = 18 + 22 * unit(h, 3);
                double d = Math.hypot(x - cx, z - cz);
                if (d >= radius) continue;
                double height = 50 + 90 * unit(h, 4);
                best = Math.max(best, height * Math.pow(1 - d / radius, 1.4));
            }
        }
        return best;
    }

    /** A cleft's depth at (x, z), 0..1: on 40% of 300-block cells a straight gap 60-140 long and 3-7 wide. */
    private double cleft(double x, double z) {
        int gx0 = GradientNoise.floor(x / 300.0), gz0 = GradientNoise.floor(z / 300.0);
        double best = 0;
        for (int dz = -1; dz <= 1; dz++) {
            for (int dx = -1; dx <= 1; dx++) {
                long h = GradientNoise.hash(styleCrags ^ 0xC1L, gx0 + dx, gz0 + dz);
                if (unit(h, 0) > 0.4) continue;
                double cx = (gx0 + dx + 0.2 + 0.6 * unit(h, 1)) * 300, cz = (gz0 + dz + 0.2 + 0.6 * unit(h, 2)) * 300;
                double a = unit(h, 3) * Math.PI, half = 30 + 40 * unit(h, 4), wide = 1.5 + 2 * unit(h, 5);
                double u = (x - cx) * Math.cos(a) + (z - cz) * Math.sin(a), v = -(x - cx) * Math.sin(a) + (z - cz) * Math.cos(a);
                v += 2 * GradientNoise.noise2(styleCrags ^ 0xC3L, u / 12.0, h & 0xFF); // a little ragged
                double end = 1 - smoothstep(half * 0.7, half, Math.abs(u));
                double across = 1 - Math.abs(v) / wide;
                if (across <= 0 || end <= 0) continue;
                best = Math.max(best, Math.sqrt(across) * end);
            }
        }
        return best;
    }

    /**
     * Leaning slabs (the user's Avatar pillars): on 45% of 150-block cells, a blade of rock 25-45
     * long and 7-12 thick, its top rising toward one end to 35-80 over the slope, so from the side it
     * reads as a pillar leaning out over its foot. It narrows toward that tip.
     */
    private double slab(double x, double z) {
        int gx0 = GradientNoise.floor(x / 150.0), gz0 = GradientNoise.floor(z / 150.0);
        double best = 0;
        for (int dz = -1; dz <= 1; dz++) {
            for (int dx = -1; dx <= 1; dx++) {
                long h = GradientNoise.hash(slabs, gx0 + dx, gz0 + dz);
                if (unit(h, 0) > 0.45) continue;
                double cx = (gx0 + dx + 0.2 + 0.6 * unit(h, 1)) * 150, cz = (gz0 + dz + 0.2 + 0.6 * unit(h, 2)) * 150;
                double a = unit(h, 3) * Math.PI * 2, len = 25 + 20 * unit(h, 4), wide = 3.5 + 2.5 * unit(h, 5), tall = 35 + 45 * unit(h, 6);
                double u = (x - cx) * Math.cos(a) + (z - cz) * Math.sin(a), v = -(x - cx) * Math.sin(a) + (z - cz) * Math.cos(a);
                double t = u / len + 0.5;
                if (t < 0 || t > 1) continue;
                double w = wide * (1 - 0.4 * t) * (1 + 0.15 * GradientNoise.noise2(slabs ^ 0x5L, x / 6.0, z / 6.0));
                if (Math.abs(v) > w) continue;
                double edge = Math.min(1, (w - Math.abs(v)) / 1.5) * Math.min(1, t * 6) * Math.min(1, (1 - t) * 10);
                best = Math.max(best, tall * (0.25 + 0.75 * Math.pow(t, 1.3)) * edge);
            }
        }
        return best;
    }

    static double smoothMin(double a, double b, double k) {
        double h = Math.max(k - Math.abs(a - b), 0) / k;
        return Math.min(a, b) - h * h * k / 4;
    }

    private static double unit(long hash, int k) {
        return (GradientNoise.mix(hash + k * 0x9E3779B97F4A7C15L) >>> 11) * 0x1.0p-53;
    }

    /**
     * Ridge values at each cirque centre and at ±{@link #GRAD_STEP} in x and z (for the slope),
     * which every column within ~500 blocks asks for.
     */
    private static final class RidgeCache {
        static final int SIZE = 512;
        final long[] keys = new long[SIZE];
        final double[][] values = new double[SIZE][];

        double[] get(Megarange m, int gx, int gz, double cx, double cz) {
            long key = ((long) gx << 32) | (gz & 0xFFFFFFFFL);
            int slot = (int) GradientNoise.mix(key) & (SIZE - 1);
            if (values[slot] != null && keys[slot] == key) return values[slot];
            double[] v = {
                    m.ridge(cx, cz),
                    m.ridge(cx + GRAD_STEP, cz), m.ridge(cx - GRAD_STEP, cz),
                    m.ridge(cx, cz + GRAD_STEP), m.ridge(cx, cz - GRAD_STEP),
                    m.exponent(cx, cz)};
            keys[slot] = key;
            values[slot] = v;
            return v;
        }
    }
}
