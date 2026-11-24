package com.seaweed.grandatlas.core.macro;

import com.seaweed.grandatlas.core.BiomeKind;
import com.seaweed.grandatlas.core.Maths;
import com.seaweed.grandatlas.core.landform.Frame;
import com.seaweed.grandatlas.core.landform.Landform;
import com.seaweed.grandatlas.core.noise.GradientNoise;
import com.seaweed.grandatlas.core.world.WorldField;

/**
 * Spire Crown (M2 fantasy, 2026-11-23): a ring of 14–21 colossal stone spires, 45–105 blocks thick
 * and 220–450 tall, standing round a sheltered vale like the points of a crown. The vale inside is
 * gentle and green, a glimmering enchanted country wherever it isn't frozen. The spires' sides are
 * vertical (their envelope), with a skirt of talus at their feet. The rule it breaks: nothing
 * raised them, and nothing wears them down.
 */
public final class SpireCrown extends Macro {
    private final long ribs;

    public SpireCrown(long seed) {
        super(GradientNoise.fork(seed, 211));
        ribs = noiseSeed(1);
    }

    @Override public String id() { return "spire_crown"; }
    @Override public int color() { return 0xC080FF; }
    @Override public double subCell() { return 600; }

    @Override
    public double weight(WorldField.Point c) {
        return c.interior() < 0.3 || c.belt() > 0.6 ? 0 : 0.45;
    }

    @Override
    public double radius(MacroSite site) {
        return 1200 + 700 * site.param(1);
    }

    @Override
    public double reachBlocks(MacroSite site) {
        return site.radius() * 0.2;
    }

    @Override
    public double familyWeight(Landform l, WorldField.Point c) {
        return switch (l.id()) {
            case "lowlands" -> 2.0;
            case "hills" -> 1.0;
            default -> 0;
        };
    }

    @Override
    public Frame frame(MacroField.Hit hit, WorldField.Point world) {
        // The vale sits a little low and sheltered, calm and a touch wetter.
        double datum = world.datum() - 15 * smooth(1.0, 0.5, hit.s);
        return new Frame(datum, -10, 70, 0.45, 0.03, 0.15, datum);
    }

    @Override
    public boolean covers(MacroField.Hit hit) {
        return hit.s < 0.8;
    }

    /** {x, z, radius, height} of spire k, or null when there's no such spire. */
    public static double[] spire(MacroSite site, int k) {
        int n = 14 + (int) (8 * site.param(2));
        if (k >= n) return null;
        double ring = (0.36 + 0.08 * site.param(3)) * site.radius();
        double a = (k + 0.35 * (site.param(100 + k) - 0.5)) * Math.PI * 2 / n + site.param(4) * Math.PI * 2;
        double off = ring * (1 + 0.12 * (site.param(200 + k) - 0.5));
        double r = 22 + 30 * site.param(300 + k);
        double tall = 220 + 230 * site.param(400 + k);
        return new double[] {site.x() + Math.cos(a) * off, site.z() + Math.sin(a) * off, r, tall};
    }

    @Override
    public void landmarks(MacroField.Hit hit, double x, double z, Terrain terrain, Landmarks out) {
        MacroSite site = hit.site;
        double best = 0, bestEnv = 0;
        boolean on = false;
        for (int k = 0; k < 22; k++) {
            double[] sp = spire(site, k);
            if (sp == null) break;
            double dx = x - sp[0], dz = z - sp[1], d = Math.hypot(dx, dz);
            if (d > sp[2] * 2 + 12) continue;
            double ux = d > 0 ? dx / d : 1, uz = d > 0 ? dz / d : 0;
            double rr = sp[2] * (1 + 0.2 * GradientNoise.noise2(ribs ^ k, sp[0] / 9.0 + 2.3 * ux, sp[1] / 9.0 + 2.3 * uz));
            double top = Spires.tower(d, rr, sp[3]);
            double env = Spires.tower(Math.max(0, d - 6), rr, sp[3]);
            best = Math.max(best, top);
            bestEnv = Math.max(bestEnv, env);
            if (d < rr) on = true;
        }
        if (bestEnv <= 0) return;
        out.height += best;
        out.envelope = Math.max(0, bestEnv - best);
        out.roughness = on ? 0 : 0.1;
        if (on) out.zone = MacroZone.SUMMIT;
        else if (out.envelope > 2) out.zone = MacroZone.FACE;
    }

    @Override
    public void combine(MacroField.Hit hit, double outerH, double innerH, double x, double z, Combined out) {
        double c = smooth(1.15, 0.8, hit.s);
        out.height = Maths.lerp(c, outerH, innerH);
        out.inside = c;
        out.roughness = 0;
        out.zone = hit.s < 0.33 ? MacroZone.INTERIOR : MacroZone.FLANK; // the vale is inside the ring
    }

    @Override
    public BiomeKind biome(MacroZone zone, double t, double w, BiomeKind biome) {
        if (zone == MacroZone.SUMMIT || zone == MacroZone.FACE) return t < -0.45 ? biome : BiomeKind.STONY_PEAKS;
        if (zone == MacroZone.INTERIOR && t > -0.4) return BiomeKind.GLIMMER_VALE;
        return biome;
    }
}
