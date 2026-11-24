package com.seaweed.grandatlas.core.macro;

import com.seaweed.grandatlas.core.Maths;
import com.seaweed.grandatlas.core.landform.CanyonPlateau;
import com.seaweed.grandatlas.core.landform.Frame;
import com.seaweed.grandatlas.core.landform.Landform;
import com.seaweed.grandatlas.core.noise.GradientNoise;
import com.seaweed.grandatlas.core.world.WorldField;

/**
 * Sundered Land (M2 fantasy, 2026-11-23): a tableland 3.2–5 km across, lifted 140–220 blocks and
 * torn apart. One to three chasms run across it from edge to edge, jagged as lightning, 30–90 wide
 * and as deep as the whole lift. Along each, the land on one side has heaved up 30–90 blocks over
 * the other, so every chasm is also a step. The rule it breaks: the land was split from above, not
 * worn from below.
 */
public final class SunderedLand extends Macro {
    private static final double EDGE = 130;

    private final long jag, zig, width, bands;

    public SunderedLand(long seed) {
        super(GradientNoise.fork(seed, 213));
        jag = noiseSeed(1);
        zig = noiseSeed(2);
        width = noiseSeed(3);
        bands = noiseSeed(4);
    }

    @Override public String id() { return "sundered_land"; }
    @Override public int color() { return 0x704060; }
    @Override public double subCell() { return 650; }

    @Override
    public double weight(WorldField.Point c) {
        return c.interior() < 0.35 ? 0 : 0.4;
    }

    @Override
    public double radius(MacroSite site) {
        return 1600 + 900 * site.param(1);
    }

    @Override
    public double reachBlocks(MacroSite site) {
        return EDGE;
    }

    @Override
    public double familyWeight(Landform l, WorldField.Point c) {
        return switch (l.id()) {
            case "hills" -> 1.4;
            case "highlands" -> 1.2;
            case "lowlands" -> 1.0;
            case "rock_desert" -> 0.4;
            default -> 0;
        };
    }

    @Override
    public Frame frame(MacroField.Hit hit, WorldField.Point world) {
        double datum = world.datum() + 140 + 80 * hit.site.param(2);
        return new Frame(datum, -20, 140, 0.55, -0.03, 0, datum);
    }

    @Override
    public boolean covers(MacroField.Hit hit) {
        return false; // the chasms go down to the country outside
    }

    @Override
    public void combine(MacroField.Hit hit, double outerH, double innerH, double x, double z, Combined out) {
        MacroSite site = hit.site;
        double e = hit.inside + 25 * GradientNoise.noise2(jag, x / 60.0, z / 60.0);
        double c = smooth(-EDGE / 2, EDGE / 2, e);
        double top = innerH;
        double cut = 0;
        int n = 1 + (int) (2.99 * site.param(3));
        for (int k = 0; k < n; k++) {
            double a = site.param(10 + k) * Math.PI;
            double ux = Math.cos(a), uz = Math.sin(a);
            double off = (site.param(20 + k) - 0.5) * 0.5 * site.radius();
            double dx = x - site.x(), dz = z - site.z();
            double along = dx * ux + dz * uz;
            double across = -dx * uz + dz * ux - off;
            // Jagged like lightning: a wander, then a sawtooth zigzag on top of it.
            double f = along / 70 + 0.6 * GradientNoise.noise2(zig ^ k ^ 0x6L, along / 300.0, 0.5) + site.param(30 + k), fr = f - Math.floor(f);
            double saw = 2 * Math.abs(fr - 0.5) - 0.5; // a triangle wave in [-0.5, 0.5]
            double teeth = 10 + 30 * (0.5 + 0.5 * GradientNoise.noise2(zig ^ k ^ 0x4L, along / 180.0, 0.5)); // irregular, not a saw blade
            across += 90 * GradientNoise.noise2(zig ^ k, along / 400.0, 0.5) + teeth * saw + 10 * GradientNoise.noise2(zig ^ k ^ 0x5L, along / 25.0, 0.5);
            double w = 15 + 30 * (0.5 + 0.5 * GradientNoise.noise2(width ^ k, along / 260.0, 0.5));
            // One side heaved up over the other.
            double heave = (30 + 60 * site.param(40 + k)) * (site.param(50 + k) < 0.5 ? 1 : -1);
            top += heave * 0.5 * (smooth(-w, w, across) * 2 - 1) * smooth(0.2, 0.7, c);
            cut = Math.max(cut, 1 - smooth(w, w + 14, Math.abs(across)));
        }
        double linear = Maths.lerp(c, outerH, top);
        double phase = 0.3 * GradientNoise.noise2(bands, x / 90.0, z / 90.0);
        double stepped = outerH + CanyonPlateau.strata(c * (top - outerH), 38, phase);
        double bell = Math.min(1, 4 * c * (1 - c)) * smooth(30, 100, top - outerH);
        double ground = Maths.lerp(bell, linear, stepped);
        cut *= smooth(0.1, 0.5, c);
        // Never under the sea: inland the world fills anything below it with sea water (2026-11-23).
        double floor = Math.min(ground, Math.max(outerH + 2, com.seaweed.grandatlas.core.AtlasCore.SEA_LEVEL + 3));
        out.height = Maths.lerp(cut, ground, floor);
        out.inside = c * (1 - cut);
        out.roughness = 0.25 * bell + 0.45 * Math.min(1, 4 * cut * (1 - cut));
        out.zone = cut > 0.5 ? MacroZone.CANYON : c < 0.5 ? MacroZone.FOOT : (cut > 0.05 ? MacroZone.RIM : MacroZone.INTERIOR);
    }
}
