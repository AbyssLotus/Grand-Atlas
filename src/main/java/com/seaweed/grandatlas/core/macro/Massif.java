package com.seaweed.grandatlas.core.macro;

import com.seaweed.grandatlas.core.Maths;
import com.seaweed.grandatlas.core.landform.Frame;
import com.seaweed.grandatlas.core.landform.Landform;
import com.seaweed.grandatlas.core.noise.GradientNoise;
import com.seaweed.grandatlas.core.world.WorldField;

/**
 * Mountain massif (design §6.4): a 3–5.4 km dome of high country lifted up to 250–420 blocks at
 * its heart, rising through broad foothills with no single edge. Its sub-mosaic is mostly
 * mountain (ranges attract each other), with high upland valleys between. The peaks, cirques
 * and valleys are the megarange's own, standing on a higher base.
 */
public final class Massif extends Macro {
    public Massif(long seed) {
        super(GradientNoise.fork(seed, 202));
    }

    @Override public String id() { return "massif"; }
    @Override public int color() { return 0xD0E0FF; }
    @Override public double subCell() { return 900; }

    @Override
    public double weight(WorldField.Point c) {
        return c.interior() < 0.3 ? 0 : 0.5 + 1.5 * c.belt();
    }

    @Override
    public double radius(MacroSite site) {
        return 1500 + 1200 * site.param(1);
    }

    @Override
    public double reachBlocks(MacroSite site) {
        return site.radius() * 0.25;
    }

    @Override
    public double familyWeight(Landform l, WorldField.Point c) {
        return switch (l.id()) {
            case "megarange" -> 3.0;
            case "highlands" -> 1.0;
            case "hills" -> 0.5;
            default -> 0;
        };
    }

    @Override
    public Frame frame(MacroField.Hit hit, WorldField.Point world) {
        // Bolder than at first (150-300), so a massif reads as one giant raised mass.
        double lift = (250 + 170 * hit.site.param(2)) * smooth(1.1, 0.25, hit.s);
        double datum = world.datum() + lift;
        return new Frame(datum, -80, 760, 0.9, -0.05, 0.05, datum);
    }

    @Override
    public boolean covers(MacroField.Hit hit) {
        return hit.s < 0.8;
    }

    @Override
    public void combine(MacroField.Hit hit, double outerH, double innerH, double x, double z, Combined out) {
        double c = smooth(1.25, 0.85, hit.s);
        out.height = Maths.lerp(c, outerH, innerH);
        out.inside = c;
        out.roughness = 0;
        out.zone = c > 0.95 ? MacroZone.INTERIOR : MacroZone.FLANK;
    }
}
