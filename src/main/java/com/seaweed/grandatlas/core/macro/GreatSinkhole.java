package com.seaweed.grandatlas.core.macro;

import com.seaweed.grandatlas.core.AtlasCore;
import com.seaweed.grandatlas.core.BiomeKind;
import com.seaweed.grandatlas.core.Maths;
import com.seaweed.grandatlas.core.landform.Frame;
import com.seaweed.grandatlas.core.landform.Landform;
import com.seaweed.grandatlas.core.noise.GradientNoise;
import com.seaweed.grandatlas.core.world.WorldField;

/**
 * Great sinkhole (M2 expansion, 2026-11-23): a karst upland domed 120–200 blocks over the country,
 * wooded and hilly, with one colossal pit sunk into it, after the tiankeng of Xiaozhai and
 * Dashiwei. The pit is 560–1000 blocks across with a lobed outline and sheer walls, and drops to
 * about the country's own level. On its floor, out of the wind, is a lost world: jungle wherever it
 * isn't frozen, old-growth spruce where it is. Rivers on the upland run to the rim and pour in.
 */
public final class GreatSinkhole extends Macro {
    /** Width of the pit's wall band, in blocks. */
    private static final double WALL = 28;

    private final long outline, floor, flutes;

    public GreatSinkhole(long seed) {
        super(GradientNoise.fork(seed, 209));
        outline = noiseSeed(1);
        floor = noiseSeed(2);
        flutes = noiseSeed(3);
    }

    @Override public String id() { return "great_sinkhole"; }
    @Override public int color() { return 0x40A060; }
    @Override public double subCell() { return 650; }
    @Override public double altitudeDiscount() { return 0.5; }

    @Override
    public double weight(WorldField.Point c) {
        if (c.interior() < 0.3 || c.humidity() < -0.15) return 0;
        return 0.4 + 0.8 * Math.max(0, c.humidity()) + 0.3 * Math.max(0, c.temperature());
    }

    @Override
    public double radius(MacroSite site) {
        return 1300 + 700 * site.param(1);
    }

    @Override
    public double reachBlocks(MacroSite site) {
        return site.radius() * 0.3;
    }

    @Override
    public double familyWeight(Landform l, WorldField.Point c) {
        return switch (l.id()) {
            case "hills" -> 2.0;
            case "highlands" -> 1.4;
            case "lowlands" -> 0.6;
            default -> 0;
        };
    }

    private double lift(MacroSite site) {
        return 120 + 80 * site.param(2);
    }

    @Override
    public Frame frame(MacroField.Hit hit, WorldField.Point world) {
        double datum = world.datum() + lift(hit.site) * smooth(1.15, 0.55, hit.s);
        return new Frame(datum, -30, 220, 0.8, 0, 0.12, datum);
    }

    @Override
    public boolean covers(MacroField.Hit hit) {
        return hit.s < 0.8;
    }

    /** {x, z, radius}: the pit, a little off the dome's centre. */
    public static double[] pit(MacroSite site) {
        double a = site.param(3) * Math.PI * 2, off = 0.15 * site.param(4) * site.radius();
        return new double[] {site.x() + Math.cos(a) * off, site.z() + Math.sin(a) * off,
                Math.min(280 + 220 * site.param(5), 0.3 * site.radius())}; // a squeezed site keeps its pit inside the upland
    }

    @Override
    public void landmarks(MacroField.Hit hit, double x, double z, Terrain terrain, Landmarks out) {
        MacroSite site = hit.site;
        double[] p = pit(site);
        double dx = x - p[0], dz = z - p[1], d = Math.hypot(dx, dz);
        if (d > p[2] * 1.4 + WALL) return;
        double ux = d > 1e-6 ? dx / d : 1, uz = d > 1e-6 ? dz / d : 0;
        double lobe = 1 + 0.18 * GradientNoise.noise2(outline ^ site.seed(), ux * 1.4, uz * 1.4)
                + 0.07 * GradientNoise.noise2(outline ^ site.seed() ^ 0x5L, ux * 3.5, uz * 3.5);
        double angle = Math.atan2(uz, ux);
        double inside = p[2] * lobe - d + 4 * Math.sin(angle * (18 + Math.floor(8 * site.param(6))))
                + 3 * GradientNoise.noise2(flutes, x / 8.0, z / 8.0);
        if (inside <= -2) return;
        // The floor: the country's own level (never under the sea), gently rolling, a little lower to the middle.
        double base = Math.max(AtlasCore.SEA_LEVEL + 6, out.datum - lift(site) * smooth(1.15, 0.55, hit.s) + 4);
        double floorH = base + 8 * GradientNoise.noise2(floor, x / 70.0, z / 70.0) + 10 * smooth(0, p[2], inside)
                - 8 * smooth(p[2] * 0.4, p[2], inside) + 6;
        double t = smooth(-2, WALL, inside);
        out.height = Maths.lerp(t, out.height, Math.min(out.height, floorH));
        out.roughness = 0.5 * Math.min(1, 4 * t * (1 - t));
        out.zone = t > 0.97 ? MacroZone.PIT : MacroZone.FACE;
    }

    @Override
    public void combine(MacroField.Hit hit, double outerH, double innerH, double x, double z, Combined out) {
        double c = smooth(1.2, 0.85, hit.s);
        out.height = Maths.lerp(c, outerH, innerH);
        out.inside = c;
        out.roughness = 0;
        out.zone = c > 0.95 ? MacroZone.INTERIOR : MacroZone.FLANK;
    }

    @Override
    public BiomeKind biome(MacroZone zone, double t, double w, BiomeKind biome) {
        if (zone != MacroZone.PIT) return biome;
        return t < -0.35 ? BiomeKind.OLD_GROWTH_SPRUCE_TAIGA : t < 0.05 ? BiomeKind.DARK_FOREST : BiomeKind.JUNGLE;
    }
}
