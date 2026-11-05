package com.seaweed.grandatlas.core.landform;

import static com.seaweed.grandatlas.core.Maths.smoothstep;

import com.seaweed.grandatlas.core.noise.GradientNoise;

/**
 * Wind-carved sandstone standing on sand (playtest round 2: Wadi Rum and Dune: Awakening's deep
 * desert). Three kinds of rock, on jittered grids:
 * <ul>
 *   <li><b>Jebels</b>: massifs 140-380 blocks across and 70-180 tall, stretched along the wind, with
 *       sheer walls, domed and terraced tops, and slot canyons (siqs) cut through them.</li>
 *   <li><b>Towers</b>: columns and spires 24-72 blocks across and up to 140 tall, stepped into
 *       ledges.</li>
 *   <li><b>Fins</b>: thin walls 10-22 blocks thick and 70-200 long, turned roughly with the wind.</li>
 * </ul>
 * This is the 2D part: each rock's height and how far inside its footprint a column is. The 3D part
 * (holes, arches, a sand-blasted foot, grooves along soft layers) is carved per block from those
 * numbers, in {@link com.seaweed.grandatlas.core.Caves#fine}.
 */
public final class Rocks {
    private static final double JEBEL_SPACING = 480;
    private static final double TOWER_SPACING = 150;
    private static final double FIN_SPACING = 210;

    /** The tallest rock at a column: height over the sand baseline, blocks inside its outline, its radius. */
    public static final class Hit {
        public double top, inside, size;
        final double[] grad = new double[2];

        void offer(double top, double inside, double size) {
            if (top > this.top) {
                this.top = top;
                this.inside = inside;
                this.size = size;
            }
        }
    }

    private final long jebels, towers, fins, slots, warp;
    private final double dirX, dirZ;

    Rocks(long seed, double dirX, double dirZ) {
        jebels = GradientNoise.fork(seed, 1);
        towers = GradientNoise.fork(seed, 2);
        fins = GradientNoise.fork(seed, 3);
        slots = GradientNoise.fork(seed, 4);
        warp = GradientNoise.fork(seed, 5);
        this.dirX = dirX;
        this.dirZ = dirZ;
    }

    /**
     * Rocks at (x, z). {@code density} 0..1 sets how many grid cells hold a rock (1 is a Wadi Rum
     * of jebels, towers and fins; ~0.1 is the odd tower or fin in an open dune sea).
     */
    public void sample(double x, double z, double density, Hit out) {
        out.top = 0;
        out.inside = 0;
        out.size = 0;
        if (density <= 0) return;
        // Ragged outlines: one warp for all rocks, at two scales.
        double wx = x + 14 * GradientNoise.noise2(warp, x / 50.0, z / 50.0) + 4 * GradientNoise.noise2(warp ^ 0x3L, x / 11.0, z / 11.0);
        double wz = z + 14 * GradientNoise.noise2(warp ^ 0x5L, x / 50.0, z / 50.0) + 4 * GradientNoise.noise2(warp ^ 0x7L, x / 11.0, z / 11.0);
        jebels(wx, wz, density, out);
        towers(wx, wz, density, out);
        fins(wx, wz, density, out);
    }

    private void jebels(double x, double z, double density, Hit out) {
        int gx0 = GradientNoise.floor(x / JEBEL_SPACING), gz0 = GradientNoise.floor(z / JEBEL_SPACING);
        for (int dz = -1; dz <= 1; dz++) {
            for (int dx = -1; dx <= 1; dx++) {
                long h = GradientNoise.hash(jebels, gx0 + dx, gz0 + dz);
                double presence = presence(h, 0.75 * density);
                if (presence <= 0) continue;
                double cx = (gx0 + dx + 0.25 + 0.5 * unit(h, 1)) * JEBEL_SPACING;
                double cz = (gz0 + dz + 0.25 + 0.5 * unit(h, 2)) * JEBEL_SPACING;
                double r = 70 + 120 * unit(h, 3);
                double ox = x - cx, oz = z - cz;
                double along = (ox * dirX + oz * dirZ) / 1.35, across = -ox * dirZ + oz * dirX;
                double d = Math.hypot(along, across);
                if (d > r * 1.4) continue;
                double ux = d > 1e-6 ? along / d : 1, uz = d > 1e-6 ? across / d : 0;
                double lobes = 1 + 0.22 * GradientNoise.noise2(h, ux * 1.7, uz * 1.7) + 0.1 * GradientNoise.noise2(h ^ 0x2BL, ux * 4.1, uz * 4.1);
                double reach = r * lobes;
                double s = d / reach;
                if (s >= 1) continue;
                double height = 70 + 110 * unit(h, 4);
                // A domed top with bumps, terraced by the layers, inside sheer walls. (Terracing the
                // wall too made it ~800 blocks/block steep, which the seam check can't tell from a seam.)
                double dome = height * (0.8 + 0.2 * Math.sqrt(Math.max(0, 1 - s * s)))
                        * (1 + 0.07 * GradientNoise.noise2(h ^ 0x9L, x / 28.0, z / 28.0));
                double band = 15 + 9 * unit(h, 5);
                dome = 0.4 * dome + 0.6 * CanyonPlateau.strata(dome, band, 0.3 * GradientNoise.noise2(h ^ 0x11L, x / 60.0, z / 60.0));
                double top = dome * smoothstep(1.0, 0.95, s) * presence;
                double inside = reach - d;
                // Slot canyons: narrow sand-floored cuts along a contour line through the rock.
                double[] g = out.grad;
                double n = GradientNoise.noise2d(slots, x / 240.0, z / 240.0, g);
                double width = 0.018 + 0.016 * unit(h, 6);
                double slot = 1 - smoothstep(width * 0.55, width, Math.abs(n));
                if (slot > 0) {
                    top *= 1 - slot;
                }
                double slotDistance = (Math.abs(n) - width) * 240 / Math.max(0.2, Math.hypot(g[0], g[1]));
                out.offer(top, Math.min(inside, Math.max(0, slotDistance)), r);
            }
        }
    }

    private void towers(double x, double z, double density, Hit out) {
        int gx0 = GradientNoise.floor(x / TOWER_SPACING), gz0 = GradientNoise.floor(z / TOWER_SPACING);
        for (int dz = -1; dz <= 1; dz++) {
            for (int dx = -1; dx <= 1; dx++) {
                long h = GradientNoise.hash(towers, gx0 + dx, gz0 + dz);
                double presence = presence(h, 0.65 * density);
                if (presence <= 0) continue;
                double cx = (gx0 + dx + 0.2 + 0.6 * unit(h, 1)) * TOWER_SPACING;
                double cz = (gz0 + dz + 0.2 + 0.6 * unit(h, 2)) * TOWER_SPACING;
                double r = 12 + 24 * unit(h, 3);
                double d = Math.hypot(x - cx, z - cz);
                if (d > r * 1.3) continue;
                double lobes = 1 + 0.18 * GradientNoise.noise2(h, (x - cx) / (r * 0.8), (z - cz) / (r * 0.8));
                double reach = r * lobes;
                double s = d / reach;
                if (s >= 1) continue;
                double height = Math.min(30 + 110 * unit(h, 4), r * 6.5);
                // A column with near-vertical sides and a rounded, stepped top; some taper into
                // spires. Ledges down the sides come from the carved layers.
                double body = height * (1 - 0.3 * s * s);
                double band = 8 + 6 * unit(h, 6);
                body = 0.35 * body + 0.65 * CanyonPlateau.strata(body, band, 0.3 * GradientNoise.noise2(h ^ 0x11L, x / 25.0, z / 25.0));
                double column = body * smoothstep(1.0, 0.82, s);
                double cone = height * Math.pow(Math.max(0, 1 - s), 0.7);
                double top = (column + smoothstep(0.55, 0.9, unit(h, 5)) * (cone - column)) * presence;
                out.offer(top, reach - d, r);
            }
        }
    }

    private void fins(double x, double z, double density, Hit out) {
        int gx0 = GradientNoise.floor(x / FIN_SPACING), gz0 = GradientNoise.floor(z / FIN_SPACING);
        for (int dz = -1; dz <= 1; dz++) {
            for (int dx = -1; dx <= 1; dx++) {
                long h = GradientNoise.hash(fins, gx0 + dx, gz0 + dz);
                double presence = presence(h, 0.55 * density);
                if (presence <= 0) continue;
                double cx = (gx0 + dx + 0.2 + 0.6 * unit(h, 1)) * FIN_SPACING;
                double cz = (gz0 + dz + 0.2 + 0.6 * unit(h, 2)) * FIN_SPACING;
                double length = 35 + 65 * unit(h, 3);
                double ox = x - cx, oz = z - cz;
                if (ox * ox + oz * oz > length * length * 1.2) continue;
                // Turned within ~25 degrees of the wind.
                double turn = (unit(h, 4) - 0.5) * 0.9;
                double ax = dirX * Math.cos(turn) - dirZ * Math.sin(turn), az = dirZ * Math.cos(turn) + dirX * Math.sin(turn);
                double along = ox * ax + oz * az;
                double across = -ox * az + oz * ax + 4 * GradientNoise.noise2(h, along / 30.0, 0.5);
                double thick = 5 + 6 * unit(h, 5);
                double a = Math.abs(along) / length, c = Math.abs(across) / thick;
                double s = Math.sqrt(Math.sqrt(a * a * a * a + c * c * c * c));
                if (s >= 1) continue;
                double height = Math.min(25 + 50 * unit(h, 6), length * 0.9);
                double crest = (1 - 0.35 * a * a) * (1 + 0.12 * GradientNoise.noise2(h ^ 0x9L, along / 9.0, 0.5));
                double top = height * smoothstep(1.0, 0.8, s) * crest * presence;
                double inside = Math.min(thick - Math.abs(across), length - Math.abs(along));
                out.offer(top, inside, thick);
            }
        }
    }

    /**
     * 0..1: how far a cell's rock has risen out of the sand at this density. A rock exists where
     * the density beats its cell's draw, and grows over the next 0.08 of density, so rocks fade
     * in and out across a district instead of popping up whole (which left 28-block seams).
     */
    private static double presence(long hash, double chance) {
        double draw = unit(hash, 0);
        return smoothstep(draw, draw + 0.08, chance);
    }

    private static double unit(long hash, int k) {
        return (GradientNoise.mix(hash + k * 0x9E3779B97F4A7C15L) >>> 11) * 0x1.0p-53;
    }
}
