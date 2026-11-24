package com.seaweed.grandatlas.core.macro;

import com.seaweed.grandatlas.core.BiomeKind;
import com.seaweed.grandatlas.core.Maths;
import com.seaweed.grandatlas.core.landform.Frame;
import com.seaweed.grandatlas.core.landform.Landform;
import com.seaweed.grandatlas.core.noise.GradientNoise;
import com.seaweed.grandatlas.core.world.WorldField;

/**
 * Sky Pillar (M2 fantasy, 2026-11-23): one column of rock 160–300 blocks across, rising sheer from
 * the woods to y 600–700, with one to three lesser pillars beside it. The top is a hanging garden
 * high above the clouds. The walls are vertical (their envelope), ribbed and pocked, with a skirt
 * of fallen rock at the foot. The rule it breaks: a column that thin and that tall would fall.
 */
public final class SkyPillar extends Macro {
    private final long ribs;

    public SkyPillar(long seed) {
        super(GradientNoise.fork(seed, 214));
        ribs = noiseSeed(1);
    }

    @Override public String id() { return "sky_pillar"; }
    @Override public int color() { return 0x60C0FF; }
    @Override public double subCell() { return 600; }

    @Override
    public double weight(WorldField.Point c) {
        return c.interior() < 0.3 || c.humidity() < -0.25 ? 0 : 0.5;
    }

    @Override
    public double radius(MacroSite site) {
        return 1000 + 400 * site.param(1);
    }

    @Override
    public double reachBlocks(MacroSite site) {
        return 160;
    }

    @Override
    public double familyWeight(Landform l, WorldField.Point c) {
        return switch (l.id()) {
            case "hills" -> 1.5;
            case "lowlands" -> 1.5;
            default -> 0;
        };
    }

    @Override
    public Frame frame(MacroField.Hit hit, WorldField.Point world) {
        return new Frame(world.datum(), -10, 60, 0.5, 0, 0.1, world.datum());
    }

    @Override
    public boolean covers(MacroField.Hit hit) {
        return hit.s < 0.8;
    }

    /** {x, z, radius, top y} of pillar k (0 is the great one), or null. */
    private static double[] pillar(MacroSite site, int k) {
        int n = 2 + (int) (3 * site.param(2));
        if (k >= n) return null;
        if (k == 0) return new double[] {site.x(), site.z(), 80 + 70 * site.param(3), 600 + 100 * site.param(4)}; // under y 700: higher, vanilla snows on the garden (temperature 1.0)
        double a = site.param(10 + k) * Math.PI * 2, off = (180 + 160 * site.param(20 + k));
        return new double[] {site.x() + Math.cos(a) * off, site.z() + Math.sin(a) * off,
                30 + 35 * site.param(30 + k), 360 + 300 * site.param(40 + k)};
    }

    @Override
    public void landmarks(MacroField.Hit hit, double x, double z, Terrain terrain, Landmarks out) {
        MacroSite site = hit.site;
        double best = 0, bestEnv = 0;
        boolean on = false, great = false;
        for (int k = 0; k < 5; k++) {
            double[] p = pillar(site, k);
            if (p == null) break;
            double dx = x - p[0], dz = z - p[1], d = Math.hypot(dx, dz);
            if (d > p[2] * 2 + 12) continue;
            double ux = d > 0 ? dx / d : 1, uz = d > 0 ? dz / d : 0;
            double rr = p[2] * (1 + 0.12 * GradientNoise.noise2(ribs ^ k, p[0] / 9.0 + 2.6 * ux, p[1] / 9.0 + 2.6 * uz)
                    + 0.05 * GradientNoise.noise2(ribs ^ k ^ 0x9L, p[0] / 9.0 + 7 * ux, p[1] / 9.0 + 7 * uz));
            double tall = Math.max(60, p[3] - out.datum);
            double top = Spires.tower(d, rr, tall);
            double env = Spires.tower(Math.max(0, d - 6), rr, tall);
            if (top > best) {
                best = top;
                great = k == 0;
            }
            bestEnv = Math.max(bestEnv, env);
            if (d < rr) on = true;
        }
        if (bestEnv <= 0) return;
        out.height = Math.max(out.height, out.datum + best);
        out.envelope = Math.max(0, out.datum + bestEnv - out.height);
        if (on) out.zone = great ? MacroZone.SUMMIT : MacroZone.RIM;
        else if (out.envelope > 2) out.zone = MacroZone.FACE;
    }

    @Override
    public void combine(MacroField.Hit hit, double outerH, double innerH, double x, double z, Combined out) {
        double c = smooth(1.12, 0.82, hit.s);
        out.height = Maths.lerp(c, outerH, innerH);
        out.inside = c;
        out.roughness = 0;
        out.zone = c > 0.95 ? MacroZone.INTERIOR : MacroZone.FLANK;
    }

    @Override
    public BiomeKind biome(MacroZone zone, double t, double w, BiomeKind biome) {
        // The great pillar's top is a hanging garden; the lesser ones and the walls bare stone.
        if (zone == MacroZone.SUMMIT) return BiomeKind.SKY_GARDEN;
        if (zone == MacroZone.RIM || zone == MacroZone.FACE) return t < -0.45 ? biome : BiomeKind.STONY_PEAKS;
        return biome;
    }
}
