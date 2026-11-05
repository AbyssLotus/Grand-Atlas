package com.seaweed.grandatlas.core.world;

import static com.seaweed.grandatlas.core.Maths.clamp;
import static com.seaweed.grandatlas.core.Maths.lerp;
import static com.seaweed.grandatlas.core.Maths.smoothstep;

import com.seaweed.grandatlas.core.AtlasCore;
import com.seaweed.grandatlas.core.noise.Fractal;
import com.seaweed.grandatlas.core.noise.GradientNoise;

/**
 * The world layer (design §5.1–5.2): continents, tectonic belts, basins, the continental
 * datum that landforms build on, and climate. Everything here varies over kilometres, and
 * the same fields serve both columns and province centres (which pick their landform from
 * the climate and context there).
 */
public final class WorldField {
    /**
     * @param continentalness ocean (&lt; 0) to deep interior (≈ 1)
     * @param interior        0 at the coast, 1 well inland
     * @param belt            0..1, strength of a tectonic mountain belt
     * @param basin           0..1, strength of an enclosed lowland basin
     * @param datum           base elevation of the land before landform relief (world-level frame)
     * @param shadow          0..1, rain shadow from belts upwind
     */
    public record Point(double continentalness, double interior, double belt, double basin, double datum,
                        double temperature, double humidity, double shadow) {
        public boolean isOcean() {
            return continentalness < -0.06;
        }

        /**
         * This point as seen from inside a macro landform: always land, well inland, with the
         * macro's climate offsets applied. Its sub-mosaic picks landforms from this context.
         */
        public Point inland(double tempOffset, double humidOffset) {
            return new Point(Math.max(continentalness, 0.3), Math.max(interior, 0.6), belt, basin, datum,
                    clamp(temperature + tempOffset, -1, 1), clamp(humidity + humidOffset, -1, 1), shadow);
        }
    }

    /** Prevailing wind blows toward +x; rain shadows fall east of mountain belts. */
    private static final double WIND_STEP = 1600;

    private final long warpX, warpZ, continents, belts, basins, undulation, temperature, humidity, abyss, seabed;

    public WorldField(long seed) {
        warpX = GradientNoise.fork(seed, 1);
        warpZ = GradientNoise.fork(seed, 2);
        continents = GradientNoise.fork(seed, 3);
        belts = GradientNoise.fork(seed, 4);
        basins = GradientNoise.fork(seed, 5);
        undulation = GradientNoise.fork(seed, 6);
        temperature = GradientNoise.fork(seed, 8);
        humidity = GradientNoise.fork(seed, 9);
        abyss = GradientNoise.fork(seed, 10);
        seabed = GradientNoise.fork(seed, 11);
    }

    public Point at(double x, double z) {
        double wx = warpedX(x, z), wz = warpedZ(x, z);
        // 4 octaves: the 5th (~500-block features) dug small sub-sea-level pockets just inland.
        double cont = oceanIslands(x, z, archipelago(x, z, continentalness(wx, wz, 4)));
        double interior = smoothstep(0.02, 0.3, cont);
        double belt = belt(wx, wz);
        double basin = smoothstep(0.58, 0.82, Fractal.fbm(basins, wx / 7000.0, wz / 7000.0, 3) * 0.5 + 0.5)
                * interior * (1 - belt);

        // Belts lift the ground they cross, so ranges stand on high country rather than rising
        // straight out of the plains; basins sink it. Never below the shore, or basins flood.
        double datum = AtlasCore.SEA_LEVEL + 2
                + interior * (45 + 22 * Fractal.fbm(undulation, x / 2600.0, z / 2600.0, 3))
                + belt * interior * 110
                - basin * 25;
        datum = Math.max(datum, AtlasCore.SEA_LEVEL + 3);

        double shadow = rainShadow(x, z);
        double temp = clamp(Fractal.fbm(temperature, x / 8000.0, z / 8000.0, 3) * 2.2, -1, 1);
        double humid = clamp(Fractal.fbm(humidity, x / 6000.0, z / 6000.0, 3) * 2.2 - 0.75 * shadow - 0.3 * basin, -1, 1);
        return new Point(cont, interior, belt, basin, datum, temp, humid, shadow);
    }

    /**
     * Ocean floor by distance past the coast (t = -continentalness, 0 at the shore), with the sea
     * floor's own landforms (docs/M3_M4_PLAN.md, phase 2), every one smooth so it joins seamlessly:
     * <ul>
     *   <li><b>shelf</b>: sandbars along the shore, long sand ripples, and patches of rocky reef
     *       (coral grows on them in warm seas);</li>
     *   <li><b>slope</b>: submarine canyons cut down it;</li>
     *   <li><b>abyss</b>: hills, rare long trenches, and seamounts, some flat-topped (guyots), in warm
     *       seas some ringed into atolls (a reef just under the surface round a lagoon);</li>
     *   <li><b>blue holes</b>: round sheer pits in warm shallows.</li>
     * </ul>
     */
    public double oceanFloor(double t, double x, double z) {
        // A continental shelf, a slope, the abyss (the user: use real data for the oceans). Real seas
        // stay shallow far out (Maine: 20 m at 1 km, 49 at 4, 100 at 16; the Outer Banks 20 m at
        // 16 km) and only then drop; ours went 100 deep within 1 km of every shore. Our oceans are
        // narrower than Earth's, so the shelf is scaled to them: 1.2-2.3 km out to ~30 deep at its
        // edge (wider in some regions than others), then the slope down over about a kilometre.
        double s0 = 0.22 + 0.2 * (0.5 + 0.5 * GradientNoise.noise2(seabed ^ 21, x / 9000.0, z / 9000.0)), s1 = s0 + 0.2;
        double floor;
        if (t < s0) {
            floor = 62 - 30 * Math.pow(Math.max(0, t) / s0, 1.2); // continental shelf
        } else if (t < s1) {
            floor = lerp(smoothstep(s0, s1, t), 32, -40); // continental slope
        } else {
            floor = -40 - 18 * smoothstep(s1, s1 + 0.4, t) + 6 * Fractal.fbm(abyss, x / 700.0, z / 700.0, 3); // abyssal hills
        }
        double shelf = 1 - smoothstep(0.8 * s0, s0, t), near = smoothstep(0.0, 0.004, t);
        // Sandbars: ridges along the shore, a little way out, broken into lengths.
        floor += near * 2.4 * Math.exp(-Math.pow((t - 0.012) / 0.004, 2)) * Math.max(0, 0.4 + GradientNoise.noise2(seabed ^ 1, x / 260.0, z / 260.0));
        // Ripples: low sand waves in patches, their spacing and heading wandering (as plain stripes
        // they ruled the whole shelf).
        double patch = Math.max(0, GradientNoise.noise2(seabed ^ 17, x / 320.0, z / 320.0));
        if (patch > 0) {
            double ra = 1.4 * GradientNoise.noise2(seabed ^ 2, x / 500.0, z / 500.0);
            double wave = 4 + 2.5 * (0.5 + 0.5 * GradientNoise.noise2(seabed ^ 18, x / 200.0, z / 200.0));
            floor += shelf * near * 1.1 * patch * Math.sin((x * Math.cos(ra) + z * Math.sin(ra)) / wave + 4 * GradientNoise.noise2(seabed ^ 3, x / 45.0, z / 45.0));
        }
        // Rocky reefs on the shelf: patches of broken rock standing a few blocks off the sand.
        double reef = GradientNoise.noise2(seabed ^ 4, x / 220.0, z / 220.0);
        if (reef > 0.35) {
            double rock = 1 - Math.abs(GradientNoise.noise2(seabed ^ 5, x / 14.0, z / 14.0));
            floor += shelf * near * smoothstep(0.35, 0.6, reef) * (2 + 5 * rock * rock);
        }
        // Submarine canyons down the slope.
        double slope = smoothstep(s0 - 0.03, s0 + 0.03, t) * (1 - smoothstep(s1, s1 + 0.08, t));
        if (slope > 0) {
            // Few and broad: as fine lines of a noise they squiggled over every slope like worm tracks.
            double c = Math.abs(GradientNoise.noise2(seabed ^ 6, x / 1100.0, z / 1100.0) + 0.08 * GradientNoise.noise2(seabed ^ 7, x / 240.0, z / 240.0));
            if (c < 0.035) floor -= slope * 34 * Math.pow(1 - c / 0.035, 0.7);
        }
        double deep = smoothstep(s1, s1 + 0.1, t);
        if (deep > 0) {
            // Trenches: rare, long and deep.
            double tr = Math.abs(GradientNoise.noise2(seabed ^ 8, x / 7000.0, z / 7000.0));
            if (tr < 0.02) floor -= deep * 38 * Math.pow(1 - tr / 0.02, 0.8);
            floor = Math.max(floor, seamounts(x, z, floor, deep));
        }
        // Blue holes: in warm shallows (and atoll lagoons).
        if (floor > AtlasCore.SEA_LEVEL - 26 && warmth(x, z) > 0.25) floor = blueHole(x, z, floor);
        return floor;
    }

    private double warmth(double x, double z) {
        return clamp(Fractal.fbm(temperature, x / 8000.0, z / 8000.0, 3) * 2.2, -1, 1);
    }

    private static double unit(long h, int k) {
        return (GradientNoise.mix(h + k * 0x9E3779B97F4A7C15L) >>> 11) * 0x1.0p-53;
    }

    /**
     * Seamounts on 35% of 2400-block cells: volcanic cones 60-120 over the abyss, 280-650 across.
     * A third are guyots, their tops planed flat 20-35 under the sea. In warm seas, those that reach
     * near the surface become atolls: a ring of reef 1-2 under the water round a lagoon 7-12 deep.
     */
    private double seamounts(double x, double z, double floor, double deep) {
        int gx0 = GradientNoise.floor(x / 2400.0), gz0 = GradientNoise.floor(z / 2400.0);
        double best = floor;
        for (int dz = -1; dz <= 1; dz++) {
            for (int dx = -1; dx <= 1; dx++) {
                long h = GradientNoise.hash(seabed ^ 9, gx0 + dx, gz0 + dz);
                if (unit(h, 0) > 0.35) continue;
                double cx = (gx0 + dx + 0.25 + 0.5 * unit(h, 1)) * 2400, cz = (gz0 + dz + 0.25 + 0.5 * unit(h, 2)) * 2400;
                double r = 280 + 370 * unit(h, 3);
                double d = Math.hypot(x - cx, z - cz) / (r * (1 + 0.12 * GradientNoise.noise2(seabed ^ 10, x / 160.0, z / 160.0)));
                if (d >= 1) continue;
                double base = -48, tall = 60 + 60 * unit(h, 4);
                double cone = base + tall * Math.pow(1 - d, 1.6) + 5 * Fractal.fbm(seabed ^ 11, x / 90.0, z / 90.0, 2);
                double kind = unit(h, 5);
                boolean warm = warmth(cx, cz) > 0.3;
                if (warm && kind < 0.5) {
                    // An atoll: the reef ring just under the surface, the lagoon inside, the cone's flanks outside.
                    double rim = AtlasCore.SEA_LEVEL - 1.5 + 0.8 * GradientNoise.noise2(seabed ^ 12, x / 9.0, z / 9.0);
                    double ring = Math.exp(-Math.pow((d - 0.32) / 0.05, 2));
                    double lagoon = AtlasCore.SEA_LEVEL - 7 - 5 * unit(h, 6) + 1.5 * GradientNoise.noise2(seabed ^ 13, x / 40.0, z / 40.0);
                    double top = d < 0.32 ? lerp(ring, lagoon, rim) : lerp(ring, Math.min(rim, cone + 40 * (1 - smoothstep(0.32, 0.6, d))), rim);
                    cone = d < 0.45 ? top : lerp(smoothstep(0.45, 0.7, d), top, cone);
                } else if (kind < 0.33 || warm) {
                    // A guyot: planed flat.
                    double flat = AtlasCore.SEA_LEVEL - 20 - 15 * unit(h, 6);
                    cone = Math.min(cone, flat + 1.2 * GradientNoise.noise2(seabed ^ 14, x / 30.0, z / 30.0));
                } else {
                    cone = Math.min(cone, AtlasCore.SEA_LEVEL - 8); // a peak, still under the water
                }
                cone = lerp(smoothstep(0.85, 1, d), cone, floor); // into the abyss at its foot, no step
                best = Math.max(best, lerp(deep, floor, cone));
            }
        }
        return best;
    }

    /** Blue holes on 25% of 900-block cells: round sheer pits 12-28 across and 35-55 deep. */
    private double blueHole(double x, double z, double floor) {
        int gx0 = GradientNoise.floor(x / 900.0), gz0 = GradientNoise.floor(z / 900.0);
        for (int dz = -1; dz <= 1; dz++) {
            for (int dx = -1; dx <= 1; dx++) {
                long h = GradientNoise.hash(seabed ^ 15, gx0 + dx, gz0 + dz);
                if (unit(h, 0) > 0.25) continue;
                double cx = (gx0 + dx + 0.2 + 0.6 * unit(h, 1)) * 900, cz = (gz0 + dz + 0.2 + 0.6 * unit(h, 2)) * 900;
                double r = 6 + 8 * unit(h, 3);
                double d = Math.hypot(x - cx, z - cz) / (r * (1 + 0.1 * GradientNoise.noise2(seabed ^ 16, x / 6.0, z / 6.0)));
                if (d >= 1.25) continue;
                double depth = 35 + 20 * unit(h, 4);
                if (d < 1) return Math.min(floor, floor - depth * (1 - Math.pow(d, 8)));
                return floor - 1.5 * (1.25 - d) / 0.25; // a slight lip
            }
        }
        return floor;
    }

    private double warpedX(double x, double z) {
        return x + 1100 * Fractal.fbm(warpX, x / 4096.0, z / 4096.0, 3);
    }

    // ---- archipelagos

    /**
     * Archipelagos (the user: "archipelagos are AWESOME", and an ocean feature "should forcibly make
     * more ocean around it"): a site on a coast, 14-26 km across, inside which the continent field
     * is drawn down to a shallow sea, and toward its rim to deeper water, so it always lies in open
     * sea with the mainland's coast facing it across a sound. Out of that sea a few big islands,
     * many small ones and skerries rise where a mix of noises crosses over, with deeper channels
     * and sounds between them. Everything downstream (coasts, cliffs, shelves, rivers, biomes)
     * reads it as ordinary land and sea.
     */
    private double archipelago(double x, double z, double cont) {
        int ci = GradientNoise.floor(x / ARCH_CELL), cj = GradientNoise.floor(z / ARCH_CELL);
        double out = cont;
        for (int dj = -2; dj <= 2; dj++) {
            for (int di = -2; di <= 2; di++) {
                Arch a = arch(ci + di, cj + dj);
                if (a == null) continue;
                double dx = x - a.x, dz = z - a.z, reach = a.r * a.stretch * 1.75;
                if (Math.abs(dx) > reach || Math.abs(dz) > reach) continue;
                // An irregular outline: an ellipse at its own heading, its edge warped.
                double u = (dx * a.ca + dz * a.sa) / a.stretch, v = -dx * a.sa + dz * a.ca;
                double d = Math.hypot(u, v) / a.r + 0.18 * Fractal.fbm(a.salt ^ 1, x / 6000.0, z / 6000.0, 3);
                double w = 1 - smoothstep(0.92, 1.3, d);
                if (w <= 0) continue;
                // Islands: broad lumps (a few big islands), finer ones (many small), and a fine
                // speckle of skerries round them; channels cut between as ridged lines.
                double broad = Fractal.fbm(a.salt ^ 2, x / a.scale, z / a.scale, 3);
                double fine = Fractal.fbm(a.salt ^ 3, x / (a.scale * 0.35), z / (a.scale * 0.35), 3);
                double skerry = GradientNoise.noise2(a.salt ^ 4, x / 140.0, z / 140.0);
                double channel = 1 - Math.abs(Fractal.fbm(a.salt ^ 5, x / (a.scale * 1.3), z / (a.scale * 1.3), 2));
                double rim = smoothstep(0.55, 0.95, d); // deeper water toward the edge, open sea round it
                double isl = 0.62 * broad + 0.38 * fine + a.land - 0.3 * rim - 0.25 * smoothstep(0.86, 1, channel);
                double sea = -0.05 - 0.07 * rim - 0.06 * smoothstep(0.8, 1, channel);
                // Smooth everywhere (the coast reads the shore's distance off this field's slope): the
                // sea floor rises into each island, and skerries are smooth bumps.
                double c = sea + 0.6 * Math.max(isl, -0.25) + 0.25 * Math.max(0, isl)
                        + 0.1 * smoothstep(0.6, 0.85, skerry) * smoothstep(-0.25, -0.1, isl) * (1 - smoothstep(0.5, 0.7, rim));
                out = lerp(w, out, c);
            }
        }
        return out;
    }

    private static final double ARCH_CELL = 24000;

    // ---- island seas: atoll fields and volcanic chains

    /**
     * Islands out in the open sea, on their own 30 km grid (offset from the archipelago grid, and
     * kept clear of archipelagos). Like the archipelagos they live in the continent field, so the
     * coast model dresses them, and every term is smooth (a step here would break the shore's distance).
     * <ul>
     *   <li><b>Atoll fields</b> in warm open sea: a scatter of ring reefs a block under the surface
     *       round shallow turquoise lagoons, small sandy islets on the rings, the floor dropping
     *       away to deep water outside each ring.</li>
     *   <li><b>Volcanic chains</b>: a line of steep cone islands strung out from a coast or across
     *       open sea, deep water close in; {@link #volcano} gives their cones height.</li>
     * </ul>
     */
    private double oceanIslands(double x, double z, double cont) {
        int ci = GradientNoise.floor((x - ISLE_OFFSET) / ISLE_CELL), cj = GradientNoise.floor((z - ISLE_OFFSET) / ISLE_CELL);
        double out = cont;
        for (int dj = -1; dj <= 1; dj++) {
            for (int di = -1; di <= 1; di++) {
                IslandSea s = islandSea(ci + di, cj + dj);
                if (s == null) continue;
                double dx = x - s.x, dz = z - s.z;
                if (Math.abs(dx) > s.reach || Math.abs(dz) > s.reach) continue;
                double d = Math.hypot(dx, dz) / s.r + 0.12 * Fractal.fbm(s.salt ^ 1, x / 5000.0, z / 5000.0, 3);
                double w = 1 - smoothstep(0.8, 1.15, d);
                if (w <= 0) continue;
                double c = s.atolls ? atollField(s, x, z) : chain(s, x, z, out);
                out = lerp(w, out, c);
            }
        }
        return out;
    }

    /** The continent field over an atoll field: deep sea, with each atoll's ring, lagoon and islets. */
    private double atollField(IslandSea s, double x, double z) {
        double best = -0.5; // deep blue water between the atolls: they rise out of the abyss
        int gi = GradientNoise.floor(x / ATOLL_CELL), gj = GradientNoise.floor(z / ATOLL_CELL);
        for (int dj = -1; dj <= 1; dj++) {
            for (int di = -1; di <= 1; di++) {
                long h = GradientNoise.hash(s.salt ^ 0xA70L, gi + di, gj + dj);
                if (archUnit(h, 1) > 0.5) continue; // not every cell has one
                double ax = (gi + di + 0.1 + 0.8 * archUnit(h, 2)) * ATOLL_CELL, az = (gj + dj + 0.1 + 0.8 * archUnit(h, 3)) * ATOLL_CELL;
                // Sizes from a little reef ring to a big one (cubed, so most are small), some drawn out long.
                double size = archUnit(h, 4);
                double R = 180 + 1000 * size * size * size, width = 40 + 60 * archUnit(h, 5) + 0.05 * R, stretch = 1 + 0.9 * archUnit(h, 6), ang = archUnit(h, 7) * Math.PI;
                double ddx = x - ax, ddz = z - az, ca = Math.cos(ang), sa = Math.sin(ang);
                double u = (ddx * ca + ddz * sa) / stretch, v = -ddx * sa + ddz * ca;
                double d = Math.hypot(u, v) * (1 + 0.12 * GradientNoise.noise2(h, x / 260.0, z / 260.0));
                if (d > R + 6 * width) continue;
                double q = (d - R) / width; // 0 on the ring's crest, negative inside
                // Inside: a lagoon 3-8 deep; the ring itself a reef just under the surface; outside,
                // the reef front falls away steeply to the deep.
                double lagoon = -0.05 - 0.03 * archUnit(h, 8);
                double ring = -0.004 + 0.03 * Math.max(0, GradientNoise.noise2(h ^ 9, x / 90.0, z / 90.0) - 0.35); // islets where the noise rises
                // A pass or two through the ring, where the lagoon opens to the sea.
                double around = Math.atan2(v, u);
                for (int pk = 0; pk < 2; pk++) {
                    if (archUnit(h, 10 + pk) > 0.6) continue;
                    double da = Math.abs(Math.IEEEremainder(around - archUnit(h, 12 + pk) * Math.PI * 2, Math.PI * 2));
                    ring = lerp(smoothstep(0.05, 0.16, da), -0.035, ring);
                }
                double c = q < 0 ? lerp(smoothstep(-1.6, -0.6, q), lagoon, ring) : lerp(smoothstep(0.6, 5.5, q), ring, -0.5);
                best = Math.max(best, c);
            }
        }
        return best;
    }

    /** The continent field over a volcanic chain: the islands' outlines (their height comes from {@link #volcano}). */
    private double chain(IslandSea s, double x, double z, double around) {
        double best = Math.min(around, -0.45); // deep water close round them, even where it was shallower
        for (int k = 0; k < s.count; k++) {
            double[] v = cone(s, k);
            double d = Math.hypot(x - v[0], z - v[1]) / v[2] + 0.15 * Fractal.fbm(s.salt ^ (20 + k), x / 700.0, z / 700.0, 3);
            best = Math.max(best, 0.12 * (1 - d) - 0.01 - 0.44 * smoothstep(1, 1.45, d));
        }
        return best;
    }

    /** One cone of a chain: {x, z, radius, height}. */
    private static double[] cone(IslandSea s, int k) {
        double t = (k - (s.count - 1) / 2.0) * s.spacing;
        double side = 0.18 * s.spacing * Math.sin(k * 2.3 + (s.salt & 7));
        double x = s.x + s.ca * t - s.sa * side, z = s.z + s.sa * t + s.ca * side;
        double u = archUnit(s.salt, 30 + k), r = 500 + 600 * u, height = 150 + 250 * (0.4 + 0.6 * u) * (0.6 + 0.4 * archUnit(s.salt, 40 + k));
        return new double[] {x, z, r, height};
    }

    /**
     * Extra relief from a volcanic chain's cones, for the column's landform (blocks over its own):
     * each cone rises steeply from its shore to a peak 150-400 over the sea, the odd one with a
     * summit crater. 0 away from them.
     */
    public double volcano(double x, double z) {
        int ci = GradientNoise.floor((x - ISLE_OFFSET) / ISLE_CELL), cj = GradientNoise.floor((z - ISLE_OFFSET) / ISLE_CELL);
        double best = 0;
        for (int dj = -1; dj <= 1; dj++) {
            for (int di = -1; di <= 1; di++) {
                IslandSea s = islandSea(ci + di, cj + dj);
                if (s == null || s.atolls) continue;
                if (Math.abs(x - s.x) > s.reach || Math.abs(z - s.z) > s.reach) continue;
                for (int k = 0; k < s.count; k++) {
                    double[] v = cone(s, k);
                    double d = Math.hypot(x - v[0], z - v[1]) / v[2];
                    if (d >= 1.1) continue;
                    double f = Math.pow(Math.max(0, 1 - d), 1.6);
                    // A crater on some summits: a bowl in the top fifth.
                    if (archUnit(s.salt, 50 + k) < 0.45) f -= 0.5 * Math.pow(Math.max(0, 1 - d / 0.14), 2) * smoothstep(0.7, 0.9, 1 - d);
                    best = Math.max(best, v[3] * f);
                }
            }
        }
        return best;
    }

    /** The island-sea kinds /grandatlas locate knows. */
    public static final java.util.List<String> ISLAND_SEAS = java.util.List.of("archipelago", "atoll_field", "volcanic_chain");

    /** The nearest archipelago, atoll field or volcanic chain to (x, z): {x, z, radius}, or null within ~150 km. */
    public double[] nearestIslandSea(String kind, double x, double z) {
        double[] best = null;
        double bestD = Double.MAX_VALUE;
        boolean arch = kind.equals("archipelago");
        double cell = arch ? ARCH_CELL : ISLE_CELL, offset = arch ? 0 : ISLE_OFFSET;
        int ci = GradientNoise.floor((x - offset) / cell), cj = GradientNoise.floor((z - offset) / cell);
        for (int dj = -5; dj <= 5; dj++) {
            for (int di = -5; di <= 5; di++) {
                double sx, sz, sr;
                if (arch) {
                    Arch a = arch(ci + di, cj + dj);
                    if (a == null) continue;
                    sx = a.x; sz = a.z; sr = a.r * a.stretch;
                } else {
                    IslandSea s = islandSea(ci + di, cj + dj);
                    if (s == null || s.atolls != kind.equals("atoll_field")) continue;
                    sx = s.x; sz = s.z; sr = s.r;
                }
                double d = Math.hypot(sx - x, sz - z);
                if (d < bestD) {
                    bestD = d;
                    best = new double[] {sx, sz, sr};
                }
            }
        }
        return best;
    }

    private static final double ISLE_CELL = 30000, ISLE_OFFSET = 11000, ATOLL_CELL = 2600;

    private record IslandSea(double x, double z, double r, double reach, boolean atolls, int count, double spacing, double ca, double sa, long salt) {}

    private final Object[] isles = new Object[1 << 9];

    private IslandSea islandSea(int i, int j) {
        int slot = (int) (GradientNoise.hash(0x15E5L, i, j) & (isles.length - 1));
        if (isles[slot] instanceof ArchSlot s && s.i() == i && s.j() == j) return s.arch() == NO_ARCH ? null : (IslandSea) s.arch();
        IslandSea a = buildIslandSea(i, j);
        isles[slot] = new ArchSlot(i, j, a == null ? NO_ARCH : a);
        return a;
    }

    private IslandSea buildIslandSea(int i, int j) {
        long h = GradientNoise.hash(warpZ ^ 0x15E5L, i, j);
        if (archUnit(h, 1) > 0.8) return null;
        double x = ISLE_OFFSET + (i + 0.25 + 0.5 * archUnit(h, 2)) * ISLE_CELL, z = ISLE_OFFSET + (j + 0.25 + 0.5 * archUnit(h, 3)) * ISLE_CELL;
        double c = continentalness(warpedX(x, z), warpedZ(x, z), 4);
        double temp = clamp(Fractal.fbm(temperature, x / 8000.0, z / 8000.0, 3) * 2.2, -1, 1);
        // Keep clear of archipelagos (each is its own kind of island sea).
        int ai = GradientNoise.floor(x / ARCH_CELL), aj = GradientNoise.floor(z / ARCH_CELL);
        for (int dj = -1; dj <= 1; dj++) {
            for (int di = -1; di <= 1; di++) {
                Arch a = arch(ai + di, aj + dj);
                if (a != null && Math.hypot(a.x - x, a.z - z) < a.r * a.stretch * 1.1 + 9000) return null;
            }
        }
        double ang = archUnit(h, 4) * Math.PI;
        if (c < 0.1 && temp > 0.3) { // warm sea, or warm coast it makes its own sea out of
            double r = 7000 + 5000 * archUnit(h, 5);
            return new IslandSea(x, z, r, r * 1.3, true, 0, 0, Math.cos(ang), Math.sin(ang), h);
        }
        if (c < 0.15 && archUnit(h, 6) < 0.8) {
            int count = 3 + (int) (archUnit(h, 7) * 5);
            double spacing = 2700 + 1300 * archUnit(h, 8), r = (count * spacing) / 2 + 2500; // apart: each its own island
            return new IslandSea(x, z, r, r * 1.3, false, count, spacing, Math.cos(ang), Math.sin(ang), h);
        }
        return null;
    }

    private record Arch(double x, double z, double r, double stretch, double ca, double sa, double scale, double land, long salt) {}

    private final Object[] arches = new Object[1 << 10];
    private static final Object NO_ARCH = new Object();

    private record ArchSlot(int i, int j, Object arch) {}

    private Arch arch(int i, int j) {
        int slot = (int) (GradientNoise.hash(0xA2C4L, i, j) & (arches.length - 1));
        if (arches[slot] instanceof ArchSlot s && s.i() == i && s.j() == j) return s.arch() == NO_ARCH ? null : (Arch) s.arch();
        Arch a = buildArch(i, j);
        arches[slot] = new ArchSlot(i, j, a == null ? NO_ARCH : a);
        return a;
    }

    private Arch buildArch(int i, int j) {
        long h = GradientNoise.hash(warpX ^ 0xA2C4L, i, j);
        double u1 = archUnit(h, 1), u2 = archUnit(h, 2), u3 = archUnit(h, 3), u4 = archUnit(h, 4), u5 = archUnit(h, 5), u6 = archUnit(h, 6), u7 = archUnit(h, 7);
        if (u1 > 0.28) return null;
        double x = (i + 0.2 + 0.6 * u2) * ARCH_CELL, z = (j + 0.2 + 0.6 * u3) * ARCH_CELL;
        // On a coast: where the land meets the sea, so it grows off a shore.
        double c = continentalness(warpedX(x, z), warpedZ(x, z), 4);
        if (c < -0.35 || c > 0.3) return null;
        double ang = u4 * Math.PI;
        return new Arch(x, z, 6500 + 5500 * u5, 1 + 0.9 * u6, Math.cos(ang), Math.sin(ang),
                1800 + 1800 * u7, 0.06 + 0.1 * archUnit(h, 8), h);
    }

    private static double archUnit(long h, int k) {
        return (GradientNoise.hash(h, k, 0x51) >>> 11) * 0x1.0p-53;
    }

    private double warpedZ(double x, double z) {
        return z + 1100 * Fractal.fbm(warpZ, x / 4096.0, z / 4096.0, 3);
    }

    private double continentalness(double wx, double wz, int octaves) {
        return clamp(Fractal.fbm(continents, wx / 9000.0, wz / 9000.0, octaves) * 1.6 + 0.12, -1, 1);
    }

    /** Belts follow the zero line of a very low-frequency field: long, winding, ~3 km wide. */
    private double belt(double wx, double wz) {
        return smoothstep(0, 1, 1 - Math.abs(Fractal.fbm(belts, wx / 14000.0, wz / 14000.0, 3)) * 5.5);
    }

    /**
     * The rain shadow, from a 64-block lattice: it changes over kilometres, but sampling it took a
     * dozen noise calls and a quarter of all terrain time (0.3.6 profile). Lattice values are
     * cached; between them it is bilinear, which is smooth enough for humidity.
     */
    private double rainShadow(double x, double z) {
        double gx = x / SHADOW_CELL, gz = z / SHADOW_CELL;
        int i = GradientNoise.floor(gx), j = GradientNoise.floor(gz);
        double fx = gx - i, fz = gz - j;
        return lerp(fz, lerp(fx, shadowAt(i, j), shadowAt(i + 1, j)), lerp(fx, shadowAt(i, j + 1), shadowAt(i + 1, j + 1)));
    }

    private static final double SHADOW_CELL = 64;
    private record Shadow(int i, int j, double value) {}
    private final Shadow[] shadows = new Shadow[1 << 14];

    private double shadowAt(int i, int j) {
        int slot = (int) (GradientNoise.hash(0x5AD0L, i, j) & (shadows.length - 1));
        Shadow s = shadows[slot]; // a record: read whole or not at all, so races only cost a recompute
        if (s != null && s.i() == i && s.j() == j) return s.value();
        double v = rainShadowExact(i * SHADOW_CELL, j * SHADOW_CELL);
        shadows[slot] = new Shadow(i, j, v);
        return v;
    }

    /** Strongest belt upwind (west) within ~5 km, weaker with distance, land only. */
    private double rainShadowExact(double x, double z) {
        double shadow = 0;
        for (int k = 1; k <= 3; k++) {
            double px = x - WIND_STEP * k;
            double wx = warpedX(px, z), wz = warpedZ(px, z);
            double land = smoothstep(0.0, 0.2, continentalness(wx, wz, 3));
            shadow = Math.max(shadow, belt(wx, wz) * land * (1.15 - 0.2 * k));
        }
        return Math.min(1, shadow);
    }
}
