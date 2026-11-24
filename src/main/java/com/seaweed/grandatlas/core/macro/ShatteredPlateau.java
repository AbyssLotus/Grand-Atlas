package com.seaweed.grandatlas.core.macro;

import com.seaweed.grandatlas.core.Maths;
import com.seaweed.grandatlas.core.landform.CanyonPlateau;
import com.seaweed.grandatlas.core.landform.Frame;
import com.seaweed.grandatlas.core.landform.Landform;
import com.seaweed.grandatlas.core.noise.Fractal;
import com.seaweed.grandatlas.core.noise.GradientNoise;
import com.seaweed.grandatlas.core.world.WorldField;

/**
 * Shattered plateau (M2 expansion, 2026-11-23): a tableland 2.8–4.8 km across, lifted 190–300
 * blocks and broken by a maze of sheer-walled canyons. The canyons cut right down to the country
 * outside, so the top is a scatter of island mesas, each a few hundred blocks across with its own
 * woods or scrub. Some canyons end blind against a wall. Canyonlands' Island in the Sky, but in
 * any climate: green mesas with forest on top in the wet, banded rock in the dry. The canyons open
 * out at the plateau's edge, so the river network can find its way through them.
 */
public final class ShatteredPlateau extends Macro {
    private static final double EDGE = 120;
    /** Mesa cell size. */
    private static final double CELL = 430;

    private final long jag, cells, warp, mask, bands;

    public ShatteredPlateau(long seed) {
        super(GradientNoise.fork(seed, 208));
        jag = noiseSeed(1);
        cells = noiseSeed(2);
        warp = noiseSeed(3);
        mask = noiseSeed(4);
        bands = noiseSeed(5);
    }

    @Override public String id() { return "shattered_plateau"; }
    @Override public int color() { return 0xE0A060; }
    @Override public double subCell() { return 600; }

    @Override
    public double weight(WorldField.Point c) {
        return c.interior() < 0.35 ? 0 : 0.6 * (1 - 0.5 * c.belt());
    }

    @Override
    public double radius(MacroSite site) {
        return 1400 + 1000 * site.param(1);
    }

    @Override
    public double reachBlocks(MacroSite site) {
        return EDGE;
    }

    @Override
    public double familyWeight(Landform l, WorldField.Point c) {
        return switch (l.id()) {
            case "hills" -> 1.5;
            case "lowlands" -> 1.2;
            case "highlands" -> 0.8;
            case "canyon_plateau" -> 0.8; // warm country only (AtlasCore.warmOnly)
            case "rock_desert" -> 0.4;
            default -> 0;
        };
    }

    @Override
    public Frame frame(MacroField.Hit hit, WorldField.Point world) {
        double datum = world.datum() + 190 + 110 * hit.site.param(2);
        return new Frame(datum, -20, 120, 0.5, 0, 0.05, datum);
    }

    @Override
    public boolean covers(MacroField.Hit hit) {
        return false; // the canyons go down to the country outside, so it's always needed
    }

    /** F2 - F1 of a jittered grid in warped space: 0 on a cell border, growing into each mesa. */
    private double border(double x, double z) {
        // Warped hard and at two scales, so borders wander instead of drawing a net of straight cracks.
        double wx = x + 150 * Fractal.fbm(warp, x / 420.0, z / 420.0, 2) + 25 * GradientNoise.noise2(warp ^ 0x7L, x / 60.0, z / 60.0);
        double wz = z + 150 * Fractal.fbm(warp ^ 0x33L, x / 420.0, z / 420.0, 2) + 25 * GradientNoise.noise2(warp ^ 0x8L, x / 60.0, z / 60.0);
        int gx = GradientNoise.floor(wx / CELL), gz = GradientNoise.floor(wz / CELL);
        double f1 = Double.MAX_VALUE, f2 = Double.MAX_VALUE;
        for (int dz = -1; dz <= 1; dz++) {
            for (int dx = -1; dx <= 1; dx++) {
                long h = GradientNoise.hash(cells, gx + dx, gz + dz);
                double px = (gx + dx + 0.1 + 0.8 * ((h >>> 11 & 0xFFFFF) / (double) 0x100000)) * CELL;
                double pz = (gz + dz + 0.1 + 0.8 * ((h >>> 33 & 0xFFFFF) / (double) 0x100000)) * CELL;
                double d = Math.hypot(wx - px, wz - pz);
                if (d < f1) {
                    f2 = f1;
                    f1 = d;
                } else if (d < f2) {
                    f2 = d;
                }
            }
        }
        return f2 - f1;
    }

    @Override
    public void combine(MacroField.Hit hit, double outerH, double innerH, double x, double z, Combined out) {
        double e = hit.inside + 22 * GradientNoise.noise2(jag, x / 55.0, z / 55.0);
        double c = smooth(-EDGE / 2, EDGE / 2, e);
        double linear = Maths.lerp(c, outerH, innerH);
        double phase = 0.3 * GradientNoise.noise2(bands, x / 90.0, z / 90.0);
        double stepped = outerH + CanyonPlateau.strata(c * (innerH - outerH), 34, phase);
        double bell = Math.min(1, 4 * c * (1 - c));
        double top = Maths.lerp(bell, linear, stepped);

        // The canyons: 20-46 wide at the floor; the floor is the country outside, a few blocks up.
        double width = 8 + 22 * (0.5 + 0.5 * GradientNoise.noise2(mask ^ 0x91L, x / 400.0, z / 400.0));
        // About two borders in three are canyons; the rest join mesas, and canyons end blind against them.
        double open = smooth(-0.25, 0.1, GradientNoise.noise2(mask, x / 520.0, z / 520.0));
        double cut = (1 - smooth(width, width + 26, border(x, z))) * open * smooth(0.1, 0.6, c);
        double floor = Math.min(top, Math.max(outerH + 3, com.seaweed.grandatlas.core.AtlasCore.SEA_LEVEL + 3)); // never under the sea
        double drop = Math.max(0, top - floor);
        // The canyon walls step down in strata too.
        double wallBell = Math.min(1, 4 * cut * (1 - cut));
        double straight = top - drop * cut;
        double banded = floor + CanyonPlateau.strata(drop * (1 - cut), 30, phase + 0.37);
        out.height = Maths.lerp(wallBell * smooth(30, 90, drop), straight, banded);
        out.inside = c * (1 - cut);
        out.roughness = 0.25 * bell + 0.3 * wallBell;
        out.zone = cut > 0.5 ? MacroZone.CANYON : c < 0.5 ? MacroZone.FOOT
                : (cut > 0.05 || e < EDGE / 2 + 40 ? MacroZone.RIM : MacroZone.INTERIOR);
    }
}
