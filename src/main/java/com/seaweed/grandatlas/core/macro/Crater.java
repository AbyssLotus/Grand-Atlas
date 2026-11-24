package com.seaweed.grandatlas.core.macro;

import com.seaweed.grandatlas.core.AtlasCore;
import com.seaweed.grandatlas.core.Maths;
import com.seaweed.grandatlas.core.landform.CanyonPlateau;
import com.seaweed.grandatlas.core.landform.Frame;
import com.seaweed.grandatlas.core.landform.Landform;
import com.seaweed.grandatlas.core.noise.Fractal;
import com.seaweed.grandatlas.core.noise.GradientNoise;
import com.seaweed.grandatlas.core.world.WorldField;

/**
 * Great crater (M2 expansion, 2026-11-23): an old impact scar 2.6–4.6 km across. A floor sunk
 * 60–110 blocks under the country round it, inside a rim wall raised 110–200 blocks over that
 * country. The rim's inner face comes down in slumped terraces and its crest is scalloped. On the
 * outside it falls away over a long apron of ejecta. Bigger craters keep a central peak complex.
 * The floor is a closed basin, so the river network may put a lake in it.
 */
public final class Crater extends Macro {
    /** Where the inner wall starts, as a fraction of the radius. */
    private static final double WALL = 0.86;

    private final long scallop, terraces, peak;

    public Crater(long seed) {
        super(GradientNoise.fork(seed, 205));
        scallop = noiseSeed(1);
        terraces = noiseSeed(2);
        peak = noiseSeed(3);
    }

    @Override public String id() { return "crater"; }
    @Override public int color() { return 0xB0A090; }
    @Override public double subCell() { return 700; }

    @Override
    public double weight(WorldField.Point c) {
        // Out of the mountain belts: a rim stacked on a range ran to the build cap and read as more range.
        return c.interior() < 0.3 || c.belt() > 0.35 ? 0 : 0.7;
    }

    @Override
    public double radius(MacroSite site) {
        return 1300 + 1000 * site.param(1);
    }

    @Override
    public double reachBlocks(MacroSite site) {
        return site.radius() * 0.45; // the ejecta apron
    }

    @Override
    public double familyWeight(Landform l, WorldField.Point c) {
        double wet = switch (l.id()) {
            case "lowlands" -> 2.0;
            case "hills" -> 0.6;
            default -> 0;
        };
        double dry = switch (l.id()) {
            case "playa" -> 1.5;
            case "lowlands" -> 1.0;
            case "rock_desert" -> 0.6;
            case "dune_sea" -> 0.4;
            default -> 0;
        };
        if (c == null) return Math.max(wet, dry);
        return c.humidity() > -0.1 ? wet : dry;
    }

    private double sink(MacroSite site) {
        return 60 + 50 * site.param(2);
    }

    private double rim(MacroSite site) {
        return 110 + 90 * site.param(3);
    }

    private static boolean centralPeak(MacroSite site) {
        return site.radius() > 1700 && site.param(4) > 0.25;
    }

    @Override
    public Frame frame(MacroField.Hit hit, WorldField.Point world) {
        double sink = sink(hit.site) * smooth(1.0, WALL - 0.1, hit.s);
        double datum = Math.max(AtlasCore.SEA_LEVEL + 6, world.datum() - sink);
        // Relief at a third: the floor is a flat plain, not a second landscape.
        return new Frame(datum, -10, 60, 0.35, 0, 0, datum);
    }

    @Override
    public boolean covers(MacroField.Hit hit) {
        return hit.s < 0.75;
    }

    @Override
    public void landmarks(MacroField.Hit hit, double x, double z, Terrain terrain, Landmarks out) {
        if (!centralPeak(hit.site) || hit.s > 0.3) return;
        // The central peak: a cluster of three or four lobes, rugged, up to 90-170 over the floor.
        double h = 0;
        double r = hit.site.radius();
        for (int k = 0; k < 4; k++) {
            double a = hit.site.param(10 + k) * Math.PI * 2, off = (k == 0 ? 0 : 0.05 + 0.05 * hit.site.param(20 + k)) * r;
            double px = hit.site.x() + Math.cos(a) * off, pz = hit.site.z() + Math.sin(a) * off;
            double pr = (0.09 + 0.05 * hit.site.param(30 + k)) * r;
            double d = Math.hypot(x - px, z - pz) / pr;
            if (d >= 1) continue;
            double lobe = (k == 0 ? 1 : 0.55 + 0.3 * hit.site.param(40 + k)) * Math.pow(1 - d, 1.4);
            h = Math.max(h, lobe);
        }
        if (h <= 0) return;
        double top = (90 + 80 * hit.site.param(5)) * h * (0.6 + 0.4 * Fractal.erodedRidges(peak, x / 120.0, z / 120.0, 4));
        out.height = Math.max(out.height, out.datum + top);
        out.roughness = 0.4 * smooth(0.1, 0.4, h);
        if (top > 30) out.zone = MacroZone.SUMMIT;
    }

    @Override
    public void combine(MacroField.Hit hit, double outerH, double innerH, double x, double z, Combined out) {
        double s = hit.s;
        double c = smooth(1.0, WALL, s);
        double crest = rim(hit.site) * (0.72 + 0.28 * Fractal.erodedRidges(scallop, x / 380.0, z / 380.0, 3));
        double apron = 0.45 * (1 + 0.2 * GradientNoise.noise2(scallop ^ 0x51L, x / 300.0, z / 300.0));
        double add;
        if (s < 1) {
            // The inner wall: up from the floor in slumped terraces to the crest.
            double t = smooth(WALL, 1.0, s);
            double phase = 0.4 * GradientNoise.noise2(terraces, x / 110.0, z / 110.0);
            double stepped = CanyonPlateau.strata(t * crest, 26, phase);
            double bell = Math.min(1, 4 * t * (1 - t)) * 0.8;
            add = Maths.lerp(bell, t * crest, Math.max(0, stepped));
        } else {
            // Outside: the ejecta falls away, concave, over the apron.
            double u = Math.min(1, (s - 1) / apron);
            add = crest * Math.pow(1 - u, 2.2);
        }
        out.height = Maths.lerp(c, outerH, innerH) + add;
        out.inside = c;
        out.roughness = 0.25 * smooth(30, 120, add) * (s < 1 ? 1 : 0.4);
        out.zone = add > 40 ? MacroZone.RING : c >= 0.5 ? MacroZone.INTERIOR : MacroZone.FOOT;
    }
}
