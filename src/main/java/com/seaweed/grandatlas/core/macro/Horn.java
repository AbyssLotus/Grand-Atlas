package com.seaweed.grandatlas.core.macro;

import com.seaweed.grandatlas.core.Maths;
import com.seaweed.grandatlas.core.landform.Frame;
import com.seaweed.grandatlas.core.landform.Landform;
import com.seaweed.grandatlas.core.noise.Fractal;
import com.seaweed.grandatlas.core.noise.GradientNoise;
import com.seaweed.grandatlas.core.world.WorldField;

/**
 * Titan horn (M2 expansion, 2026-11-23): one colossal pyramidal peak, after the Matterhorn and
 * K2, standing out of its own raised mountain country. Three or four concave faces meet in sharp
 * aretes that run down from a summit 480–620 blocks over the base, and the summit nears the
 * build cap. The faces are ribbed and craggy, and altitude alone gives the snow and ice. Lesser
 * ranges and valleys of the macro's own family crowd its foot, so it rises out of mountains,
 * not out of a plain.
 */
public final class Horn extends Macro {
    private final long faces, ribs;

    public Horn(long seed) {
        super(GradientNoise.fork(seed, 210));
        faces = noiseSeed(1);
        ribs = noiseSeed(2);
    }

    @Override public String id() { return "horn"; }
    @Override public int color() { return 0xF0F0FF; }
    @Override public double subCell() { return 800; }

    @Override
    public double weight(WorldField.Point c) {
        if (c.interior() < 0.3 || c.temperature() > 0.45) return 0;
        return 0.25 + 1.1 * c.belt();
    }

    @Override
    public double radius(MacroSite site) {
        return 1500 + 900 * site.param(1);
    }

    @Override
    public double reachBlocks(MacroSite site) {
        return site.radius() * 0.3;
    }

    @Override
    public double familyWeight(Landform l, WorldField.Point c) {
        return switch (l.id()) {
            // No megaranges: at this lift they ran into the relief budget and were cut flat, and stood
            // within a hundred blocks of the horn's summit.
            case "highlands" -> 1.6;
            case "hills" -> 1.0;
            default -> 0;
        };
    }

    @Override
    public Frame frame(MacroField.Hit hit, WorldField.Point world) {
        double datum = world.datum() + 110 * smooth(1.2, 0.3, hit.s);
        // Relief kept down, so the foothills crowd the horn without rivalling it.
        return new Frame(datum, -40, 240, 0.8, -0.12, 0, datum);
    }

    @Override
    public boolean covers(MacroField.Hit hit) {
        return hit.s < 0.8;
    }

    @Override
    public void landmarks(MacroField.Hit hit, double x, double z, Terrain terrain, Landmarks out) {
        MacroSite site = hit.site;
        double r = (0.42 + 0.12 * site.param(3)) * site.radius();
        double dx = x - site.x(), dz = z - site.z();
        double d = Math.hypot(dx, dz);
        if (d > r * 1.1) return;
        // Pyramidal distance: the largest projection onto the faces' normals, so the contours are
        // polygons and the faces meet in sharp ridges (aretes).
        // The faces are measured from a warped point, so the aretes bend and kink instead of running ruler-straight.
        double fx = dx + 0.06 * r * GradientNoise.noise2(faces ^ 0x21L, x / 230.0, z / 230.0);
        double fz = dz + 0.06 * r * GradientNoise.noise2(faces ^ 0x22L, x / 230.0, z / 230.0);
        int n = 3 + (site.param(4) < 0.55 ? 1 : 0);
        double a0 = site.param(5) * Math.PI * 2, pd = -Double.MAX_VALUE, p2 = -Double.MAX_VALUE;
        for (int k = 0; k < n; k++) {
            double a = a0 + k * Math.PI * 2 / n + 0.35 * (site.param(10 + k) - 0.5);
            double p = fx * Math.cos(a) + fz * Math.sin(a);
            if (p > pd) {
                p2 = pd;
                pd = p;
            } else if (p > p2) {
                p2 = p;
            }
        }
        // Aretes: where two faces nearly tie, lift a knife-edge ridge out of the polygon's corner.
        double arete = Math.exp(-(pd - p2) / (0.035 * r));
        pd -= 0.07 * r * arete * smooth(0.95, 0.2, d / r);
        pd = Math.max(pd, 0);
        // Blend toward round low down, so the base isn't a hard polygon, and wobble the faces.
        double u = Maths.lerp(smooth(0.25, 0.85, d / r), pd, d) / r;
        u += 0.04 * GradientNoise.noise2(faces, x / 160.0, z / 160.0);
        if (u >= 1) return;
        double height = 480 + 140 * site.param(6);
        double cone = Math.pow(1 - Math.max(0, u), 2.0); // concave faces, steepening to the summit
        double rib = Fractal.erodedRidges(ribs, x / 90.0, z / 90.0, 4);
        double horn = out.datum + height * cone * (0.92 + 0.08 * rib);
        if (horn > out.height) {
            out.height = horn;
            out.zone = cone > 0.08 ? MacroZone.SUMMIT : out.zone;
        }
        out.roughness = Math.max(out.roughness, 0.4 * smooth(0.05, 0.35, cone));
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
