package com.seaweed.grandatlas.core.macro;

import com.seaweed.grandatlas.core.Maths;
import com.seaweed.grandatlas.core.landform.CanyonPlateau;
import com.seaweed.grandatlas.core.landform.Frame;
import com.seaweed.grandatlas.core.landform.Landform;
import com.seaweed.grandatlas.core.noise.GradientNoise;
import com.seaweed.grandatlas.core.world.WorldField;

/**
 * Elevated mesa (design §6.4): an arid tableland 2.2–5 km across, lifted 200–330 blocks by a
 * stepped cliff, the same strata stepping canyon walls use. On top, canyonlands alternate with
 * uplands, dune fields and playas. Canyons there cut down to the mesa's own base, not the
 * plains below.
 */
public final class ElevatedMesa extends Macro {
    private static final double EDGE = 140;

    private final long jag;

    public ElevatedMesa(long seed) {
        super(GradientNoise.fork(seed, 204));
        jag = noiseSeed(1);
    }

    @Override public String id() { return "elevated_mesa"; }
    @Override public int color() { return 0xFF7040; }
    @Override public double subCell() { return 650; }

    @Override
    public double weight(WorldField.Point c) {
        return c.humidity() > 0.05 || c.interior() < 0.3 ? 0 : 1.2;
    }

    @Override
    public double radius(MacroSite site) {
        return 1100 + 1400 * site.param(1);
    }

    @Override
    public double reachBlocks(MacroSite site) {
        return EDGE;
    }

    @Override
    public double familyWeight(Landform l, WorldField.Point c) {
        return switch (l.id()) {
            case "canyon_plateau" -> 2.0;
            case "highlands" -> 0.8;
            case "dune_sea" -> 0.6;
            case "rock_desert" -> 0.5;
            case "playa" -> 0.5;
            default -> 0;
        };
    }

    @Override
    public Frame frame(MacroField.Hit hit, WorldField.Point world) {
        double datum = world.datum() + 200 + 130 * hit.site.param(2);
        return new Frame(datum, -30, 300, 0.7, 0.05, -0.2, datum);
    }

    @Override
    public boolean covers(MacroField.Hit hit) {
        return hit.inside > 300;
    }

    @Override
    public void combine(MacroField.Hit hit, double outerH, double innerH, double x, double z, Combined out) {
        double e = hit.inside + 20 * GradientNoise.noise2(jag, x / 50.0, z / 50.0);
        double c = smooth(-EDGE / 2, EDGE / 2, e);
        double linear = Maths.lerp(c, outerH, innerH);
        // Strata only within the edge band (the bell), so the interior stays the mesa's own terrain.
        double stepped = outerH + CanyonPlateau.strata(c * (innerH - outerH), 44,
                0.3 * GradientNoise.noise2(jag ^ 0x77L, x / 90.0, z / 90.0));
        double bell = Math.min(1, 4 * c * (1 - c));
        out.height = Maths.lerp(bell, linear, stepped);
        out.inside = c;
        out.roughness = 0.25 * bell;
        out.zone = c >= 0.5 ? (e > EDGE / 2 + 60 ? MacroZone.INTERIOR : MacroZone.RIM) : MacroZone.FOOT;
    }
}
