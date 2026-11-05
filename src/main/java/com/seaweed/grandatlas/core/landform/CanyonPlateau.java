package com.seaweed.grandatlas.core.landform;

import static com.seaweed.grandatlas.core.Maths.smoothstep;

import com.seaweed.grandatlas.core.BiomeKind;
import com.seaweed.grandatlas.core.noise.Fractal;
import com.seaweed.grandatlas.core.noise.GradientNoise;
import com.seaweed.grandatlas.core.province.Province;
import com.seaweed.grandatlas.core.world.WorldField;

/**
 * Canyon plateau (design §10, the Grand Canyon): a flat rim 200–290 blocks over the datum, cut
 * by a main canyon network down to near the datum, with dendritic side canyons that only branch
 * near the main ones, and buttes left standing inside. Walls step in strata: flat benches broken by
 * near-vertical cliffs, with bench edges that wander.
 *
 * <p>No taper: a plateau ends where its province ends, so neighbours see an escarpment.
 * Rivers along the canyon floors arrive with M3's sea-level rivers.
 */
public final class CanyonPlateau implements Landform {
    private static final double BUTTE_SPACING = 300;

    private final long main, side, buttes;

    public CanyonPlateau(long seed) {
        main = GradientNoise.fork(seed, 20);
        side = GradientNoise.fork(seed, 21);
        buttes = GradientNoise.fork(seed, 22);
    }

    @Override public String id() { return "canyon_plateau"; }
    @Override public Elevation elevation() { return Elevation.HIGH; }
    @Override public int color() { return 0xC8643C; }
    @Override public double hardness() { return 1; }

    @Override
    public double weight(WorldField.Point c) {
        if (c.humidity() > 0.2 || c.temperature() < 0.0 || c.interior() < 0.1) return 0; // badlands: warm country only
        return 1.6 * (1 - c.belt()) * smoothstep(0.2, -0.25, c.humidity()) * smoothstep(0.0, 0.25, c.temperature());
    }

    @Override
    public void shape(Province province, Frame frame, double x, double z, double edge, Shape out) {
        double rim = 200 + 90 * province.param(1);
        double band = 22 + 12 * province.param(2);
        double floor = 10;

        double n1 = Math.abs(Fractal.fbm(main, x / 1100.0, z / 1100.0, 3));
        double mainCanyon = 1 - smoothstep(0.03, 0.17, n1);
        double n2 = Math.abs(Fractal.fbm(side, x / 420.0, z / 420.0, 2));
        double nearMain = 1 - smoothstep(0.12, 0.35, n1);
        double sideCanyon = (1 - smoothstep(0.02, 0.085, n2)) * nearMain * 0.8;
        double cut = Math.max(mainCanyon, sideCanyon);

        // Buttes fade in with the cut (a hard "cut > 0.2" switch made a 14-block seam).
        double butte = cut > 0.1 ? butte(x, z) * smoothstep(0.1, 0.3, cut) : 0;
        cut *= 1 - butte;

        double phase = 0.3 * GradientNoise.noise2(buttes ^ 0x5157L, x / 90.0, z / 90.0);
        // The rim steps down toward the province edge through the same strata, so the plateau's
        // outer escarpment is a staircase too, not a smooth blended ramp.
        // Narrow (110 blocks), so the staircase drops straight into the neighbour instead of
        // leaving a band of ordinary ground between a mesa and a desert.
        double edgeRamp = smoothstep(0, 110, edge);
        double relief = strata(floor + (rim - floor) * (1 - cut) * edgeRamp, band, phase);
        Zone zone = butte > 0.5 ? Zone.BUTTE
                : cut < 0.04 ? Zone.MESA_TOP
                : cut > 0.9 ? Zone.CANYON_FLOOR
                : Zone.CANYON_WALL;
        out.set(relief, zone, zone == Zone.CANYON_WALL ? 0.12 : 0.03);
    }

    /**
     * Stepped strata: each band is a flat bench for ~78% of its run, then the whole band's rise in
     * one near-vertical cliff. {@code phase} (in bands) shifts where the cliffs fall, so bench edges
     * can wander. Continuous: the top of one band meets the bottom of the next.
     * (The first version, a 60% gentle bench and a 40% steeper face, read in game as one steep ramp.)
     */
    public static double strata(double h, double band, double phase) {
        double t = h / band + phase;
        double f = Math.floor(t);
        double fr = t - f;
        double t2 = Math.max(0, Math.min(1, (fr - 0.78) / 0.18));
        return (f + t2 * t2 * (3 - 2 * t2) - phase) * band;
    }

    /** 0..1: inside a butte (a flat-topped remnant left at rim height). */
    private double butte(double x, double z) {
        int gx0 = GradientNoise.floor(x / BUTTE_SPACING), gz0 = GradientNoise.floor(z / BUTTE_SPACING);
        double best = 0;
        for (int dz = -1; dz <= 1; dz++) {
            for (int dx = -1; dx <= 1; dx++) {
                long h = GradientNoise.hash(buttes, gx0 + dx, gz0 + dz);
                if ((h >>> 61) >= 4) continue; // half the cells
                double cx = (gx0 + dx + 0.25 + 0.5 * ((h >>> 20 & 0xFFFF) / 65536.0)) * BUTTE_SPACING;
                double cz = (gz0 + dz + 0.25 + 0.5 * ((h >>> 36 & 0xFFFF) / 65536.0)) * BUTTE_SPACING;
                double r = 28 + 45 * ((h >>> 4 & 0xFFFF) / 65536.0);
                double d = Math.hypot(x - cx, z - cz);
                best = Math.max(best, 1 - smoothstep(0.8 * r, r, d));
            }
        }
        return best;
    }

    @Override
    public BiomeKind biome(Zone zone, double t, double w, double h, double v) {
        return switch (zone) {
            case CANYON_WALL -> BiomeKind.BADLANDS;
            case CANYON_FLOOR -> BiomeKind.ERODED_BADLANDS;
            // The rim is a badlands biome too, so its stepped edges and cave mouths show bands
            // (as savanna plateau, every step on the rim was a bare stone wall).
            case BUTTE -> BiomeKind.WOODED_BADLANDS;
            default -> w < -0.35 ? BiomeKind.BADLANDS : BiomeKind.WOODED_BADLANDS;
        };
    }
}
