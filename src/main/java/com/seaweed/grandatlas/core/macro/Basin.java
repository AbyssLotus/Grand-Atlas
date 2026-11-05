package com.seaweed.grandatlas.core.macro;

import com.seaweed.grandatlas.core.AtlasCore;
import com.seaweed.grandatlas.core.Maths;
import com.seaweed.grandatlas.core.landform.Frame;
import com.seaweed.grandatlas.core.landform.Landform;
import com.seaweed.grandatlas.core.noise.Fractal;
import com.seaweed.grandatlas.core.noise.GradientNoise;
import com.seaweed.grandatlas.core.world.WorldField;

/**
 * Enclosed basin (design §6.4): a 2.8–5.2 km floor sunk below the surrounding country inside a
 * ring of mountains 220–440 blocks high, broken by two or three passes. A dry basin (the usual
 * case) holds playas and dunes; one in a humid region becomes a warm, wet "lost world" valley.
 * Terminal lakes arrive with water datums in M4; until then the floor stays above sea level.
 */
public final class Basin extends Macro {
    private final long rim;

    public Basin(long seed) {
        super(GradientNoise.fork(seed, 203));
        rim = noiseSeed(1);
    }

    @Override public String id() { return "basin"; }
    @Override public int color() { return 0x60E0C0; }
    @Override public double subCell() { return 800; }

    @Override
    public double weight(WorldField.Point c) {
        return c.interior() < 0.3 ? 0 : 0.4 + 2 * c.basin();
    }

    @Override
    public double radius(MacroSite site) {
        return 1400 + 1200 * site.param(1);
    }

    @Override
    public double reachBlocks(MacroSite site) {
        return site.radius() * 0.4;
    }

    private static boolean humid(MacroSite site) {
        return site.centre().humidity() > 0.1;
    }

    /**
     * A humid basin's context sits well above 0.1 humidity once its +0.3 offset is applied, and a
     * dry one well below after its -0.35, so the context alone tells the families apart.
     */
    @Override
    public double familyWeight(Landform l, WorldField.Point c) {
        double humid = switch (l.id()) {
            case "lowlands" -> 2.0;
            case "hills" -> 1.0;
            case "highlands" -> 0.3;
            default -> 0;
        };
        double dry = switch (l.id()) {
            case "playa" -> 2.5;
            case "dune_sea" -> 1.2;
            case "rock_desert" -> 0.9;
            case "lowlands" -> 0.8;
            default -> 0;
        };
        if (c == null) return Math.max(humid, dry);
        return c.humidity() > 0.1 ? humid : dry;
    }

    @Override
    public Frame frame(MacroField.Hit hit, WorldField.Point world) {
        double sink = (30 + 30 * hit.site.param(2)) * smooth(1.1, 0.7, hit.s);
        double datum = Math.max(AtlasCore.SEA_LEVEL + 4, world.datum() - sink);
        boolean humid = humid(hit.site);
        return new Frame(datum, AtlasCore.SEA_LEVEL + 2 - datum, 300, 0.8,
                humid ? 0.15 : 0.1, humid ? 0.3 : -0.35, datum);
    }

    @Override
    public boolean covers(MacroField.Hit hit) {
        return hit.s < 0.85;
    }

    @Override
    public void combine(MacroField.Hit hit, double outerH, double innerH, double x, double z, Combined out) {
        double c = smooth(1.12, 0.9, hit.s);
        double pass = spokes(hit, 2 + (int) (hit.site.param(4) * 2), 20, 0.05);
        double ring = Math.exp(-Math.pow((hit.s - 1) / 0.13, 2)) * smooth(1.4, 1.3, hit.s);
        double rimHeight = (220 + 220 * hit.site.param(3)) * ring * (1 - 0.85 * pass)
                * (0.55 + 0.45 * Fractal.erodedRidges(rim, x / 420.0, z / 420.0, 5));
        out.height = Maths.lerp(c, outerH, innerH) + rimHeight;
        out.inside = c;
        out.roughness = 0.35 * smooth(30, 150, rimHeight);
        out.zone = rimHeight > 50 ? MacroZone.RING : c >= 0.5 ? MacroZone.INTERIOR : MacroZone.FOOT;
    }
}
