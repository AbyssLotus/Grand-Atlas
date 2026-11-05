package com.seaweed.grandatlas.core.landform;

import com.seaweed.grandatlas.core.BiomeKind;
import com.seaweed.grandatlas.core.Biomes;
import com.seaweed.grandatlas.core.noise.Fractal;
import com.seaweed.grandatlas.core.noise.GradientNoise;
import com.seaweed.grandatlas.core.province.Province;
import com.seaweed.grandatlas.core.world.WorldField;

/**
 * The everyday landforms most provinces hold: lowlands, hills and highlands. They taper into
 * their neighbours and take their biomes from the climate table, so they read as ordinary
 * country between the wonders. Plus the ocean placeholder for provinces centred at sea.
 */
public final class Ordinary {
    private Ordinary() {}

    static double fbm01(long seed, double x, double z, int octaves) {
        return Math.max(0, Math.min(1, Fractal.fbm(seed, x, z, octaves) * 0.5 + 0.5));
    }

    public static final class Ocean implements Landform {
        @Override public String id() { return "ocean"; }
        @Override public Elevation elevation() { return Elevation.LOW; }
        @Override public int color() { return 0x2A62C0; }
        @Override public double weight(WorldField.Point context) { return 0; }

        @Override
        public void shape(Province province, Frame frame, double x, double z, double edge, Shape out) {
            out.set(0, Zone.COAST, 0);
        }

        @Override
        public BiomeKind biome(Zone zone, double t, double w, double h, double v) {
            return Biomes.climate(t, w, h, v);
        }
    }

    public static final class Lowlands implements Landform {
        private final long seed;

        public Lowlands(long seed) {
            this.seed = GradientNoise.fork(seed, 1);
        }

        @Override public String id() { return "lowlands"; }
        @Override public Elevation elevation() { return Elevation.LOW; }
        @Override public int color() { return 0x8DB360; }
        @Override public double sameNeighbour() { return 0.4; }

        @Override
        public double weight(WorldField.Point c) {
            return 2.5 + 2.5 * (1 - c.interior());
        }

        @Override
        public void shape(Province province, Frame frame, double x, double z, double edge, Shape out) {
            out.set(3 + 34 * Math.pow(fbm01(seed, x / 750.0, z / 750.0, 4), 1.8)
                    + 7 * Fractal.fbm(seed ^ 0x5L, x / 220.0, z / 220.0, 3), Zone.PLAIN, 0);
        }

        @Override
        public BiomeKind biome(Zone zone, double t, double w, double h, double v) {
            return Biomes.climate(t, w, h, v);
        }
    }

    public static final class Hills implements Landform {
        private final long seed;

        public Hills(long seed) {
            this.seed = GradientNoise.fork(seed, 2);
        }

        @Override public String id() { return "hills"; }
        @Override public Elevation elevation() { return Elevation.MID; }
        @Override public int color() { return 0x5E8F3A; }
        @Override public double sameNeighbour() { return 0.4; }
        @Override public double taper() { return 350; }
        @Override public double taperFloor() { return 0.4; }

        @Override
        public double weight(WorldField.Point c) {
            return 0.6 + 2.2 * c.interior();
        }

        @Override
        public void shape(Province province, Frame frame, double x, double z, double edge, Shape out) {
            out.set(18 + 150 * Math.pow(fbm01(seed, x / 620.0, z / 620.0, 5), 1.7)
                    + 14 * Fractal.fbm(seed ^ 0x7L, x / 180.0, z / 180.0, 3), Zone.HILL, 0.12);
        }

        @Override
        public BiomeKind biome(Zone zone, double t, double w, double h, double v) {
            return Biomes.climate(t, w, h, v);
        }
    }

    public static final class Highlands implements Landform {
        private final long broad, rough;

        public Highlands(long seed) {
            this.broad = GradientNoise.fork(seed, 3);
            this.rough = GradientNoise.fork(seed, 4);
        }

        @Override public String id() { return "highlands"; }
        @Override public Elevation elevation() { return Elevation.HIGH; }
        @Override public int color() { return 0x9C8A5A; }
        @Override public double sameNeighbour() { return 0.4; }
        @Override public double taper() { return 450; }
        @Override public double taperFloor() { return 0.35; }

        @Override
        public double weight(WorldField.Point c) {
            // A little near coasts too, so a coastal cell whose neighbours took lowlands and
            // hills still has a third option.
            return (0.25 + 1.1 * c.interior()) * (1 - c.basin());
        }

        @Override
        public void shape(Province province, Frame frame, double x, double z, double edge, Shape out) {
            double relief = 150 + 100 * fbm01(broad, x / 1000.0, z / 1000.0, 4)
                    + 30 * Fractal.fbm(rough, x / 260.0, z / 260.0, 3);
            out.set(relief, Zone.UPLAND, 0.12);
        }

        @Override
        public BiomeKind biome(Zone zone, double t, double w, double h, double v) {
            return Biomes.climate(t, w, h, v);
        }
    }
}
