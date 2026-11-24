package com.seaweed.grandatlas.core.macro;

import com.seaweed.grandatlas.core.Maths;
import com.seaweed.grandatlas.core.landform.CanyonPlateau;
import com.seaweed.grandatlas.core.landform.Frame;
import com.seaweed.grandatlas.core.landform.Landform;
import com.seaweed.grandatlas.core.noise.Fractal;
import com.seaweed.grandatlas.core.noise.GradientNoise;
import com.seaweed.grandatlas.core.world.WorldField;

/**
 * Coiled Peak (M2 fantasy, 2026-11-23): a lone mountain 380–560 blocks high, wrapped by one great
 * ledge that spirals round it from the foot to near the summit, four to six turns. Each turn is a
 * broad bench that climbs gently as it goes round, under a cliff the height of one turn. A player
 * can walk to the top along it. Some coil one way, some the other. The rule it breaks: rock doesn't
 * weather into a staircase.
 */
public final class CoiledPeak extends Macro {
    private final long crags;

    public CoiledPeak(long seed) {
        super(GradientNoise.fork(seed, 212));
        crags = noiseSeed(1);
    }

    @Override public String id() { return "coiled_peak"; }
    @Override public int color() { return 0x80D0B0; }
    @Override public double subCell() { return 700; }

    @Override
    public double weight(WorldField.Point c) {
        return c.interior() < 0.3 ? 0 : 0.3 + 0.4 * c.belt();
    }

    @Override
    public double radius(MacroSite site) {
        return 1300 + 800 * site.param(1);
    }

    @Override
    public double reachBlocks(MacroSite site) {
        return site.radius() * 0.3;
    }

    @Override
    public double familyWeight(Landform l, WorldField.Point c) {
        return switch (l.id()) {
            case "hills" -> 1.6;
            case "highlands" -> 1.0;
            case "lowlands" -> 0.8;
            default -> 0;
        };
    }

    @Override
    public Frame frame(MacroField.Hit hit, WorldField.Point world) {
        double datum = world.datum() + 50 * smooth(1.2, 0.4, hit.s);
        return new Frame(datum, -30, 160, 0.6, -0.04, 0, datum);
    }

    @Override
    public boolean covers(MacroField.Hit hit) {
        return hit.s < 0.8;
    }

    @Override
    public void landmarks(MacroField.Hit hit, double x, double z, Terrain terrain, Landmarks out) {
        MacroSite site = hit.site;
        double pr = (0.42 + 0.1 * site.param(2)) * site.radius();
        double dx = x - site.x(), dz = z - site.z(), d = Math.hypot(dx, dz);
        double u = d / pr + 0.03 * GradientNoise.noise2(crags ^ 0x3L, x / 200.0, z / 200.0);
        if (u >= 1) return;
        double height = 380 + 180 * site.param(3);
        double base = height * Math.pow(1 - u, 1.25);
        // The coil: strata whose phase turns once round per band, so each bench becomes a ramp that
        // climbs one band per turn. Continuous across the angle's wrap: strata shifted a whole band
        // is the same strata.
        int turns = 4 + (int) (3 * site.param(4));
        double band = height / (turns + 0.6);
        double sense = site.param(5) < 0.5 ? 1 : -1;
        double phase = sense * Math.atan2(dz, dx) / (Math.PI * 2);
        double coiled = Math.max(0, CanyonPlateau.strata(base, band, phase));
        // Off at the very foot (the coil starts from the ground) and at the summit (the angle has no meaning there).
        double w = smooth(1.0, 0.88, u) * smooth(0.03, 0.12, u);
        double h = Maths.lerp(w, base, coiled);
        // Crags on the cliffs between benches.
        double cliff = Math.abs(base - coiled) / band;
        h += 6 * (Fractal.erodedRidges(crags, x / 40.0, z / 40.0, 3) - 0.5) * smooth(0.02, 0.15, cliff) * w;
        if (out.datum + h > out.height) {
            out.height = out.datum + h;
            out.roughness = 0.3 * w * smooth(0.05, 0.2, cliff);
            if (base > 30) out.zone = MacroZone.SUMMIT;
        }
    }

    @Override
    public void combine(MacroField.Hit hit, double outerH, double innerH, double x, double z, Combined out) {
        double c = smooth(1.2, 0.85, hit.s);
        out.height = Maths.lerp(c, outerH, innerH);
        out.inside = c;
        out.roughness = 0;
        out.zone = c > 0.95 ? MacroZone.INTERIOR : MacroZone.FLANK;
    }
}
