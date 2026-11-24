package com.seaweed.grandatlas.core.macro;

import com.seaweed.grandatlas.core.BiomeKind;
import com.seaweed.grandatlas.core.Maths;
import com.seaweed.grandatlas.core.landform.Frame;
import com.seaweed.grandatlas.core.landform.Landform;
import com.seaweed.grandatlas.core.noise.GradientNoise;
import com.seaweed.grandatlas.core.world.WorldField;

/**
 * Monolith (M2 expansion, 2026-11-23): one colossal rock rising sheer out of a flat plain, after
 * Uluru, Sugarloaf and Half Dome. It stands 220–380 blocks high and 700–1300 long. It's an oval
 * with a lobed outline and fluted walls, and its top is a rounded dome. On some, a slot gorge
 * splits it end to end. Around it the footprint flattens the country into an open plain, so
 * nothing competes with it.
 * In dry country it is banded red rock; elsewhere bare stone.
 */
public final class Monolith extends Macro {
    /** How wide the sheer wall is, in blocks: steep, but continuous, so the seam checks hold. */
    private static final double WALL = 26;

    private final long lobes, flutes, cleft;

    public Monolith(long seed) {
        super(GradientNoise.fork(seed, 206));
        lobes = noiseSeed(1);
        flutes = noiseSeed(2);
        cleft = noiseSeed(3);
    }

    @Override public String id() { return "monolith"; }
    @Override public int color() { return 0xD05A30; }
    @Override public double subCell() { return 600; }

    @Override
    public double weight(WorldField.Point c) {
        if (c.interior() < 0.3 || c.belt() > 0.6) return 0;
        return 0.45 + 0.6 * Math.max(0, -c.humidity());
    }

    @Override
    public double radius(MacroSite site) {
        return 1000 + 500 * site.param(1);
    }

    @Override
    public double reachBlocks(MacroSite site) {
        return 160;
    }

    @Override
    public double familyWeight(Landform l, WorldField.Point c) {
        return switch (l.id()) {
            case "lowlands" -> 2.5;
            case "hills" -> 0.4;
            case "playa" -> 0.5;
            case "dune_sea" -> 0.4;
            default -> 0;
        };
    }

    @Override
    public Frame frame(MacroField.Hit hit, WorldField.Point world) {
        // A calm plain round the rock: relief at a third, a little drier.
        return new Frame(world.datum(), -10, 40, 0.3, 0.02, -0.1, world.datum());
    }

    @Override
    public boolean covers(MacroField.Hit hit) {
        return hit.s < 0.8;
    }

    /** The rock's height over the plain at (x, z), or 0 off it. Fills {@code face} with whether this is its wall. */
    private double rock(MacroSite site, double x, double z, boolean[] face) {
        double angle = site.param(3) * Math.PI;
        double ux = Math.cos(angle), uz = Math.sin(angle);
        double long_ = (0.33 + 0.12 * site.param(2)) * site.radius();
        double short_ = long_ * (0.5 + 0.3 * site.param(4));
        double dx = x - site.x(), dz = z - site.z();
        double a = dx * ux + dz * uz, b = -dx * uz + dz * ux;
        double e = Math.hypot(a / long_, b / short_);
        if (e > 1.4) return 0;
        // A lobed outline, then flutes: shallow grooves running up the wall.
        double dir = Math.atan2(b / short_, a / long_);
        double lobe = 1 + 0.1 * GradientNoise.noise2(lobes ^ site.seed(), Math.cos(dir) * 1.6, Math.sin(dir) * 1.6);
        double size = Math.min(long_, short_ * 1.5);
        double inside = (lobe - e) * size + 5 * GradientNoise.noise2(flutes, x / 9.0, z / 9.0)
                + 3 * Math.sin(dir * (14 + Math.floor(10 * site.param(6))));
        if (inside <= 0) return 0;
        double height = 220 + 160 * site.param(5);
        double wall = smooth(0, WALL, inside);
        double dome = smooth(WALL, size * 0.7, inside);
        double h = height * (0.82 * wall + 0.18 * dome);
        if (site.param(7) < 0.45) {
            // A slot gorge splits the rock end to end, wandering, 10-18 wide at the bottom.
            double ca = angle + 0.5 + 1.2 * site.param(8);
            double across = -dx * Math.sin(ca) + dz * Math.cos(ca)
                    + 18 * GradientNoise.noise2(cleft, (dx * Math.cos(ca) + dz * Math.sin(ca)) / 140.0, 0.5);
            double gorge = smooth(5 + 4 * site.param(9), 12 + 6 * site.param(9), Math.abs(across));
            h *= gorge;
            if (gorge < 0.95) face[0] = true;
        }
        if (inside < WALL + 4) face[0] = true;
        return h;
    }

    @Override
    public void landmarks(MacroField.Hit hit, double x, double z, Terrain terrain, Landmarks out) {
        // The rock is a landmark (not part of the edge) so it can also stand on another macro as its guest.
        boolean[] face = {false};
        double rock = rock(hit.site, x, z, face);
        if (rock <= 0) return;
        out.height += rock;
        if (rock > 8) {
            // Pocked, honeycombed walls (tafoni), smooth top.
            out.roughness = face[0] ? 0.45 : 0;
            out.zone = face[0] ? MacroZone.FACE : MacroZone.SUMMIT;
        }
    }

    @Override
    public void combine(MacroField.Hit hit, double outerH, double innerH, double x, double z, Combined out) {
        double c = smooth(1.12, 0.82, hit.s);
        out.height = Maths.lerp(c, outerH, innerH);
        out.inside = c;
        out.roughness = 0;
        out.zone = c >= 0.5 ? MacroZone.INTERIOR : MacroZone.FOOT;
    }

    @Override
    public BiomeKind biome(MacroZone zone, double t, double w, BiomeKind biome) {
        if (zone != MacroZone.FACE && zone != MacroZone.SUMMIT) return biome;
        if (t > 0.15 && w < 0.1) return BiomeKind.BADLANDS; // Uluru: banded red rock
        if (t < -0.45) return biome; // snow from the climate table
        if (w > 0.35 && zone == MacroZone.SUMMIT) return biome; // Sugarloaf: green on top
        return BiomeKind.STONY_PEAKS; // Half Dome: bare granite
    }
}
