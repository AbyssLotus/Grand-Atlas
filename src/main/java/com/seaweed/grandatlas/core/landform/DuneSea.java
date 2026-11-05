package com.seaweed.grandatlas.core.landform;

import static com.seaweed.grandatlas.core.Maths.lerp;
import static com.seaweed.grandatlas.core.Maths.smoothstep;

import com.seaweed.grandatlas.core.BiomeKind;
import com.seaweed.grandatlas.core.noise.Fractal;
import com.seaweed.grandatlas.core.noise.GradientNoise;
import com.seaweed.grandatlas.core.province.Province;
import com.seaweed.grandatlas.core.world.WorldField;

/**
 * Dune sea (design §10): sinuous transverse dunes with a gentle windward side and a steep slip
 * face, a faint cross set, megadune zones up to ~150 blocks, star dunes on scattered cells, and
 * rock outcrops: flat-topped mini mesas with stepped cliffs rising out of the sand, in the spirit
 * of Dune: Awakening's deep desert (the user asked for them after playtesting a basin's dune sea),
 * and the odd wind-carved tower or fin ({@link Rocks}), more of them toward rock country. Outcrops and
 * rocks are carved in 3D (holes, arches, undercut feet). Wind direction is per world, so dunes line
 * up across neighbouring dune provinces, and the rock desert reuses these dunes and rocks.
 */
public final class DuneSea implements Landform {
    private static final double STAR_SPACING = 950;
    private static final double OUTCROP_SPACING = 520;

    private final long meander, mega, crest, cross, stars, outcrops, seif, rockCountry;
    private final double dirX, dirZ;
    private final Rocks rocks;
    private final ThreadLocal<Rocks.Hit> hits = ThreadLocal.withInitial(Rocks.Hit::new);

    public DuneSea(long seed) {
        meander = GradientNoise.fork(seed, 30);
        mega = GradientNoise.fork(seed, 31);
        crest = GradientNoise.fork(seed, 32);
        cross = GradientNoise.fork(seed, 33);
        stars = GradientNoise.fork(seed, 34);
        outcrops = GradientNoise.fork(seed, 36);
        seif = GradientNoise.fork(seed, 37);
        rockCountry = GradientNoise.fork(seed, 39);
        double angle = (GradientNoise.fork(seed, 35) >>> 11) * 0x1.0p-53 * Math.PI * 2;
        dirX = Math.cos(angle);
        dirZ = Math.sin(angle);
        rocks = new Rocks(GradientNoise.fork(seed, 38), dirX, dirZ);
    }

    /** The wind-carved rocks, shared with the rock desert so both use the same wind. */
    public Rocks rocks() {
        return rocks;
    }

    @Override public String id() { return "dune_sea"; }
    @Override public Elevation elevation() { return Elevation.LOW; }
    @Override public int color() { return 0xF0D070; }
    @Override public double sameNeighbour() { return 2.2; }
    // Dunes run right up to a neighbour's foot (a 350-block taper left a flat strip between a
    // mesa's cliff and the dunes it overlooked).
    @Override public double taper() { return 120; }
    @Override public double taperFloor() { return 0.3; }

    @Override
    public double weight(WorldField.Point c) {
        // Hot country only (the user: a desert must never sit next to snow): it was allowed down to
        // -0.25, a sand sea beside the taiga.
        if (c.humidity() > -0.2 || c.temperature() < 0.15 || c.interior() < 0.1) return 0;
        return (2.2 * smoothstep(-0.2, -0.5, c.humidity()) + 1.0 * c.basin()) * smoothstep(0.15, 0.4, c.temperature());
    }

    /** One dune cross-section over t in [0, 1): rise for 78%, slip face for 22%. */
    private static double profile(double t) {
        return t < 0.78 ? smoothstep(0, 1, t / 0.78) : 1 - smoothstep(0, 1, (t - 0.78) / 0.22);
    }

    private static double frac(double v) {
        return v - Math.floor(v);
    }

    @Override
    public void shape(Province province, Frame frame, double x, double z, double edge, Shape out) {
        double relief = dunes(x, z);
        Rocks.Hit hit = hits.get();
        rocks.sample(x, z, 0.08 + 0.3 * smoothstep(0.55, 0.8, Ordinary.fbm01(rockCountry, x / 3000.0, z / 3000.0, 2)), hit);
        outcrop(x, z, hit);
        double rockTop = 10 + hit.top;
        if (rockTop > relief) {
            out.set(rockTop, Zone.OUTCROP, 0.15);
            out.rock = rockTop - relief;
            out.rockInside = hit.inside;
            out.rockSize = hit.size;
            return;
        }
        out.set(relief, relief > 10 + 0.35 * amplitude(x, z) ? Zone.DUNE : Zone.INTERDUNE, 0);
    }

    /** Megadune zones: dune height swells from ~35 to ~150 blocks over a couple of kilometres. */
    private double amplitude(double x, double z) {
        return 35 + 115 * smoothstep(0.2, 0.8, Ordinary.fbm01(mega, x / 2200.0, z / 2200.0, 2));
    }

    /** The sand alone, without rock: transverse, cross, seif and star dunes (relief over the datum). */
    public double dunes(double x, double z) {
        double along = -x * dirZ + z * dirX;
        // Two meander scales make the crests sinuous rather than ruled lines.
        double across = x * dirX + z * dirZ + 140 * Fractal.fbm(meander, x / 1000.0, z / 1000.0, 2)
                + 55 * GradientNoise.noise2(meander ^ 0x55L, along / 260.0, (x * dirX + z * dirZ) / 900.0);
        double wavelength = 420;
        double amp = amplitude(x, z);
        double crestMod = 0.35 + 0.65 * Ordinary.fbm01(crest, along / 600.0, across / 2000.0, 2);

        double c2 = Math.cos(1.1), s2 = Math.sin(1.1);
        double across2 = x * (dirX * c2 - dirZ * s2) + z * (dirZ * c2 + dirX * s2)
                + 90 * Fractal.fbm(cross, x / 700.0, z / 700.0, 2);
        double relief = 10 + amp * (profile(frac(across / wavelength)) * crestMod
                + 0.12 * profile(frac(across2 / (wavelength * 0.55))));
        // Linear (seif) dune regions: long sharp-crested ridges running with the wind, so a dune
        // sea isn't one endless field of the same waves.
        double seifWeight = smoothstep(0.52, 0.72, Ordinary.fbm01(seif, x / 3500.0, z / 3500.0, 2));
        if (seifWeight > 0) {
            double wobble = 70 * Fractal.fbm(seif ^ 0x3L, across / 900.0, along / 900.0, 2);
            double t = frac((along + wobble) / 300.0);
            double ridge = Math.pow(Math.max(0, 1 - Math.abs(2 * t - 1)), 1.7);
            double height = 30 + 60 * Ordinary.fbm01(seif ^ 0x5L, across / 1500.0, along / 600.0, 2);
            relief = lerp(seifWeight, relief, 10 + height * ridge);
        }
        return Math.max(relief, 10 + star(x, z));
    }

    /**
     * Rock outcrops on about 37% of 520-block cells: flat tops 45-110 blocks up, outlines 90-260
     * blocks long stretched along the wind and wobbling with direction, and sides stepped by the
     * canyon strata in 13-19-block bands. A max() with the dunes means sand drifts up their feet.
     * strata(0) is never positive, so an outcrop fades out without a seam. Offered to {@code hit}
     * alongside the rocks: the tallest wins.
     */
    private void outcrop(double x, double z, Rocks.Hit hit) {
        int gx0 = GradientNoise.floor(x / OUTCROP_SPACING), gz0 = GradientNoise.floor(z / OUTCROP_SPACING);
        for (int dz = -1; dz <= 1; dz++) {
            for (int dx = -1; dx <= 1; dx++) {
                long h = GradientNoise.hash(outcrops, gx0 + dx, gz0 + dz);
                if ((h >>> 61) >= 3) continue;
                double cx = (gx0 + dx + 0.2 + 0.6 * ((h >>> 20 & 0xFFFF) / 65536.0)) * OUTCROP_SPACING;
                double cz = (gz0 + dz + 0.2 + 0.6 * ((h >>> 36 & 0xFFFF) / 65536.0)) * OUTCROP_SPACING;
                double radius = 45 + 85 * ((h >>> 4 & 0xFFFF) / 65536.0);
                double ox = x - cx, oz = z - cz;
                double along = (ox * dirX + oz * dirZ) / 1.6, across = -ox * dirZ + oz * dirX;
                double d = Math.hypot(along, across);
                if (d > radius * 1.5) continue;
                double ux = d > 1e-6 ? along / d : 1, uz = d > 1e-6 ? across / d : 0;
                double wobble = 1 + 0.25 * GradientNoise.noise2(h, ux * 1.6, uz * 1.6)
                        + 0.08 * GradientNoise.noise2(h ^ 0x2BL, ux * 4.3, uz * 4.3);
                double s = d / (radius * wobble);
                double top = 45 + 65 * ((h >>> 52 & 0xFF) / 256.0);
                double band = 13 + 6 * ((h >>> 44 & 0xFF) / 256.0);
                double phase = 0.3 * GradientNoise.noise2(h ^ 0x51L, x / 40.0, z / 40.0);
                hit.offer(CanyonPlateau.strata(top * smoothstep(1.05, 0.8, s), band, phase), (1.05 - s) * radius * wobble, radius);
            }
        }
    }

    /**
     * Star dunes: a central peak 80-120 blocks high with five narrow, sharp-crested arms, on a
     * quarter of the cells. (At first they were 160-220-block lobed cones, three times the height
     * of the dunes around them, and read in game as a sand mountain.)
     */
    private double star(double x, double z) {
        int gx0 = GradientNoise.floor(x / STAR_SPACING), gz0 = GradientNoise.floor(z / STAR_SPACING);
        double best = 0;
        for (int dz = -1; dz <= 1; dz++) {
            for (int dx = -1; dx <= 1; dx++) {
                long h = GradientNoise.hash(stars, gx0 + dx, gz0 + dz);
                if ((h >>> 62) != 0) continue; // a quarter of the cells
                double cx = (gx0 + dx + 0.3 + 0.4 * ((h >>> 20 & 0xFFFF) / 65536.0)) * STAR_SPACING;
                double cz = (gz0 + dz + 0.3 + 0.4 * ((h >>> 36 & 0xFFFF) / 65536.0)) * STAR_SPACING;
                double radius = 240;
                double d = Math.hypot(x - cx, z - cz);
                if (d >= radius) continue;
                double phase = (h >>> 4 & 0xFFFF) / 65536.0 * Math.PI * 2;
                double spoke = Math.max(0, Math.cos(5 * Math.atan2(z - cz, x - cx) + phase));
                double arms = 0.25 + 0.75 * spoke * spoke * spoke * spoke;
                arms = lerp(smoothstep(0, 40, d), 1, arms); // the angle is undefined at the summit
                double height = 80 + 40 * ((h >>> 52 & 0xFF) / 256.0);
                best = Math.max(best, height * Math.pow(1 - d / radius, 1.3) * arms);
            }
        }
        return best;
    }

    @Override
    public BiomeKind biome(Zone zone, double t, double w, double h, double v) {
        // Outcrop rock wears badlands' terracotta bands; everything else is sand, up to the dunes'
        // own heights. A mountain rising out of the sea of sand takes the climate's biomes, banded
        // badlands and bare peaks (it was sand to its top: "half of it is sand, the other half mesa").
        if (h > 170) return com.seaweed.grandatlas.core.Biomes.climate(t, w, h, v);
        return zone == Zone.OUTCROP ? BiomeKind.BADLANDS : BiomeKind.DESERT;
    }
}
