package com.seaweed.grandatlas.core.macro;

import com.seaweed.grandatlas.core.Maths;
import com.seaweed.grandatlas.core.landform.CanyonPlateau;
import com.seaweed.grandatlas.core.landform.Frame;
import com.seaweed.grandatlas.core.landform.Landform;
import com.seaweed.grandatlas.core.noise.GradientNoise;
import com.seaweed.grandatlas.core.world.WorldField;

/**
 * Fault block (M2 expansion, 2026-11-23): a slab of country 3–5 km across, tilted up along a
 * fault, after the Sierra Nevada and the Drakensberg. On one side it breaks off in a great
 * escarpment 280–430 blocks high, sheer and banded. From the crest the land slopes gently back
 * down over kilometres until it sinks into the country behind. Its own ranges and high valleys
 * stand on the slab, so the escarpment's crest is jagged, not a ruler line.
 */
public final class FaultBlock extends Macro {
    private static final double EDGE = 70;

    private final long jag, bands;

    public FaultBlock(long seed) {
        super(GradientNoise.fork(seed, 207));
        jag = noiseSeed(1);
        bands = noiseSeed(2);
    }

    @Override public String id() { return "fault_block"; }
    @Override public int color() { return 0x9070D0; }
    @Override public double subCell() { return 800; }

    @Override
    public double weight(WorldField.Point c) {
        return c.interior() < 0.3 ? 0 : 0.4 + 1.0 * c.belt();
    }

    @Override
    public double radius(MacroSite site) {
        return 1500 + 1000 * site.param(1);
    }

    @Override
    public double reachBlocks(MacroSite site) {
        return EDGE + 40;
    }

    @Override
    public double familyWeight(Landform l, WorldField.Point c) {
        return switch (l.id()) {
            case "highlands" -> 1.6;
            case "megarange" -> 0.5;
            case "hills" -> 1.2;
            default -> 0;
        };
    }

    private double scarp(MacroSite site) {
        return 280 + 150 * site.param(3);
    }

    /** 0..1: how far up the tilt this column is (1 at the escarpment, 0 at the far edge). */
    private static double tilt(MacroField.Hit hit) {
        double angle = hit.site.param(2) * Math.PI * 2;
        double along = hit.s * (hit.dirX * Math.cos(angle) + hit.dirZ * Math.sin(angle));
        double t = Math.max(0, Math.min(1, (along + 1) / 1.9));
        return Math.pow(t, 1.3);
    }

    @Override
    public Frame frame(MacroField.Hit hit, WorldField.Point world) {
        double datum = world.datum() + scarp(hit.site) * tilt(hit);
        // Budget over the megarange's amplitude at this scale, so its summits aren't cut flat.
        return new Frame(datum, -40, 620, 0.7, -0.04, 0, datum);
    }

    @Override
    public boolean covers(MacroField.Hit hit) {
        return hit.inside > 400;
    }

    @Override
    public void combine(MacroField.Hit hit, double outerH, double innerH, double x, double z, Combined out) {
        double e = hit.inside + 30 * GradientNoise.noise2(jag, x / 70.0, z / 70.0);
        double c = smooth(-EDGE / 2, EDGE / 2, e);
        double linear = Maths.lerp(c, outerH, innerH);
        // The escarpment's face is banded: thick ledges of rock stepping up it.
        double rise = c * (innerH - outerH);
        // (Weighted out by the bell wherever the rise is small or negative.)
        double stepped = outerH + CanyonPlateau.strata(rise, 36, 0.35 * GradientNoise.noise2(bands, x / 120.0, z / 120.0));
        double bell = Math.min(1, 4 * c * (1 - c)) * smooth(40, 140, innerH - outerH);
        out.height = Maths.lerp(bell, linear, stepped);
        out.inside = c;
        out.roughness = 0.3 * bell;
        double t = tilt(hit);
        out.zone = c < 0.5 ? MacroZone.FOOT : (t > 0.7 && hit.inside < 160 ? MacroZone.RIM : MacroZone.INTERIOR);
    }
}
