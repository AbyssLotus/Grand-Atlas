package com.seaweed.grandatlas.core.landform;

import static com.seaweed.grandatlas.core.Maths.smoothstep;

import com.seaweed.grandatlas.core.BiomeKind;
import com.seaweed.grandatlas.core.Biomes;
import com.seaweed.grandatlas.core.noise.GradientNoise;
import com.seaweed.grandatlas.core.province.Province;
import com.seaweed.grandatlas.core.world.WorldField;

/**
 * Tepuis (M5 real-world wonders, design §9; after Roraima and Auyán-tepui, whose wall Angel Falls
 * pours off): table mountains 300 to 640 blocks across that rise 230 to 400 blocks straight out
 * of a hot, wet lowland of jungle hills. Their walls are sheer all the way round, lobed and cut by
 * clefts; a forested apron of fallen rock runs out from the foot; the tops are a world of their
 * own, bare dark rock cracked into a maze, with moss and pools in the hollows.
 *
 * <p>One tepui stands near each province's middle, laid out in the province mosaic's warped space
 * (where the province really is) and shrunk to fit well inside it, so it stands whole. The walls are kept vertical through {@link Shape#envelope}, as a misty needle's
 * are. The falls pouring off the rim are placed at decoration ({@code world/FantasyDetail}) from
 * {@link #tepui} and {@link Mesa#falls}.
 */
public final class Tepui implements Landform {
    private final long lowland, cells, lobes, cracks, top;

    public Tepui(long seed) {
        lowland = GradientNoise.fork(seed, 150);
        cells = GradientNoise.fork(seed, 151);
        lobes = GradientNoise.fork(seed, 152);
        cracks = GradientNoise.fork(seed, 153);
        top = GradientNoise.fork(seed, 154);
    }

    /**
     * One table mountain: centre, mean radius, height of its top over the lowland, and how many
     * falls pour off its rim.
     */
    public record Mesa(long seed, double x, double z, double radius, double height, int falls) {
        /** The heading (radians) of fall {@code k}. */
        public double fallAngle(int k) {
            return unit(seed, 40 + k) * 2 * Math.PI;
        }
    }

    @Override public String id() { return "tepui"; }
    @Override public Elevation elevation() { return Elevation.HIGH; }
    @Override public int color() { return 0x6A4A3A; }
    @Override public double hardness() { return 0.3; }
    // The table mountains shrink toward the province edge rather than ending in a cut-off wall.
    @Override public double taper() { return 140; }
    @Override public double taperFloor() { return 0.2; }

    @Override
    public double weight(WorldField.Point c) {
        // Hot and wet: the rainforest the tepuis stand out of.
        if (c.temperature() < 0.3 || c.humidity() < 0.1 || c.interior() < 0.2) return 0;
        return 0.4 * smoothstep(0.3, 0.55, c.temperature()) * smoothstep(0.1, 0.35, c.humidity());
    }

    /**
     * The province's table mountains: one great one near its middle, and in about half of them a
     * smaller one off to one side. Kept well inside the province, so the province's taper never
     * reaches them and they stand whole.
     */
    public java.util.List<Mesa> mesas(Province p) {
        java.util.List<Mesa> out = new java.util.ArrayList<>(1);
        if (mosaic == null) return out;
        // How far the province's edge is at the least from its centre (in the warped space centres
        // live in): half the distance to the nearest other centre, less the taper and a margin.
        double nearest = Double.MAX_VALUE;
        for (int j = -2; j <= 2; j++) {
            for (int i = -2; i <= 2; i++) {
                if (i == 0 && j == 0) continue;
                Province q = mosaic.province(p.cellX() + i, p.cellZ() + j);
                nearest = Math.min(nearest, Math.hypot(q.x() - p.x(), q.z() - p.z()));
            }
        }
        double room = nearest / 2 - taper() - 60;
        long h = GradientNoise.hash(cells, p.cellX(), p.cellZ());
        double height = 230 + 170 * unit(h, 4);
        double ox = 80 * (unit(h, 1) * 2 - 1), oz = 80 * (unit(h, 2) * 2 - 1);
        // The mesa, its lobes and its apron all inside that room: shrunk to fit, or left out.
        double radius = Math.min(150 + 100 * unit(h, 3), (room - Math.hypot(ox, oz) - 0.28 * height) / 1.3);
        if (radius >= 90) {
            out.add(new Mesa(h, p.x() + ox, p.z() + oz, radius, height, 1 + (int) (unit(h, 5) * 2.4) + (height > 330 ? 1 : 0)));
        }
        return out;
    }

    private com.seaweed.grandatlas.core.province.ProvinceMosaic mosaic;

    /** The world's province mosaic, whose warped space the mesas are laid out in (set once, by AtlasCore). */
    public void bind(com.seaweed.grandatlas.core.province.ProvinceMosaic mosaic) {
        this.mosaic = mosaic;
    }

    /** A mesa's centre in real blocks (its {@code x}, {@code z} are in the mosaic's warped space). */
    public double[] centre(Mesa m) {
        return mosaic == null ? new double[] {m.x, m.z} : mosaic.unwarp(m.x, m.z);
    }

    /** The mesa of province {@code p} that real (x, z) is on or close to (within its apron), or null. */
    public Mesa tepui(Province p, double x, double z) {
        if (mosaic == null) return null;
        double u = mosaic.warpX(x, z), v = mosaic.warpZ(x, z);
        for (Mesa m : mesas(p)) {
            if (Math.hypot(u - m.x, v - m.z) < m.radius * 1.3 + apron(m)) return m;
        }
        return null;
    }

    private static double apron(Mesa m) {
        return 0.28 * m.height;
    }

    /** The rim's distance from the centre at heading {@code a}: lobed, with narrow clefts cut back into it. */
    public double rim(Mesa m, double a) {
        double ux = Math.cos(a), uz = Math.sin(a);
        double lobe = GradientNoise.noise2(lobes ^ m.seed, 1.6 * ux, 1.6 * uz);
        double fine = GradientNoise.noise2(lobes ^ (m.seed * 31), 5.5 * ux, 5.5 * uz);
        double r = m.radius * (1 + 0.2 * lobe + 0.06 * fine);
        // Clefts: a few narrow notches running back into the wall, like the gorges of a real tepui.
        double cleft = Math.abs(GradientNoise.noise2(lobes ^ (m.seed * 7 + 3), 3.2 * ux, 3.2 * uz));
        if (cleft < 0.09) r -= m.radius * 0.14 * smoothstepCleft(1 - cleft / 0.09);
        return r;
    }

    private static double smoothstepCleft(double t) {
        return t * t * (3 - 2 * t);
    }

    @Override
    public void shape(Province province, Frame frame, double x, double z, double edge, Shape out) {
        // The lowland: jungle hills, soft, with stream valleys.
        double ground = 8 + 30 * Math.pow(Ordinary.fbm01(lowland, x / 430.0, z / 430.0, 4), 1.5)
                + 3 * GradientNoise.noise2(lowland ^ 0x5AL, x / 60.0, z / 60.0);
        Mesa m = tepui(province, x, z);
        if (m == null) {
            out.set(ground, ground > 26 ? Zone.HILL : Zone.PLAIN, 0.08);
            return;
        }
        // The outline is drawn in the warped space the mesa was laid out in; the warp bends it a
        // little more, which only adds to its lobes.
        double dx = mosaic.warpX(x, z) - m.x, dz = mosaic.warpZ(x, z) - m.z, d = Math.sqrt(dx * dx + dz * dz);
        double a = Math.atan2(dz, dx), r = rim(m, a);
        double base = 8 + 30 * Math.pow(Ordinary.fbm01(lowland, m.x / 430.0, m.z / 430.0, 4), 1.5);
        double topH = base + m.height + summit(m, x, z, r - d);
        if (d < r) {
            // On top: the summit plateau, its rim exact and its sides vertical.
            out.set(topH, Zone.MESA_TOP, 0.0); // no 3D roughness: it would blob the flat top and chew the rim
            out.micro = 0.4;
            return;
        }
        // The apron: talus fallen off the wall, steep under it and spreading out into the lowland.
        double t = (d - r) / apron(m);
        double talus = t < 1 ? 0.3 * m.height * Math.pow(1 - t, 2.2) : 0;
        double relief = Math.max(ground, base + talus);
        out.set(relief, t < 0.45 ? Zone.HIGH_SLOPE : Zone.HILL, t < 0.3 ? 0.0 : 0.04);
        // The envelope reaches the top for 6 blocks out from the wall, so the 4-block grid can't
        // round the wall off: the exact relief then trims it back per block.
        // Sideways too: the rim's farthest reach over the headings within 10 blocks either side, so
        // the walls of a cleft (which run across the radius) keep their envelope as well.
        double da = Math.min(0.5, 10 / Math.max(1, d)), reach = r;
        for (int k = -2; k <= 2; k++) if (k != 0) reach = Math.max(reach, rim(m, a + k * da / 2));
        if (d < reach + 8) out.envelope = Math.max(0, topH - relief);
    }

    /**
     * The summit's own relief over its plateau: gentle swells, a maze of cracks a few blocks deep
     * (the eroded quartzite of Roraima's top), shallow hollows, and a lip that drops a little
     * toward the rim so streams run off it.
     */
    private double summit(Mesa m, double x, double z, double inside) {
        double swell = 10 * Ordinary.fbm01(top ^ m.seed, x / 140.0, z / 140.0, 3) - 5;
        double c1 = Math.abs(GradientNoise.noise2(cracks, x / 26.0, z / 26.0));
        double c2 = Math.abs(GradientNoise.noise2(cracks ^ 0x77L, x / 11.0, z / 11.0));
        double crack = 5 * (1 - smoothstep(0.02, 0.07, c1)) + 2.5 * (1 - smoothstep(0.02, 0.06, c2));
        double lip = -4 * (1 - smoothstep(0, 18, inside));
        return swell - crack + lip;
    }

    private static double unit(long h, int k) {
        return (GradientNoise.mix(h + k * 0x9E3779B97F4A7C15L) >>> 11) * 0x1.0p-53;
    }

    @Override
    public BiomeKind biome(Zone zone, double t, double w, double h, double v) {
        if (zone == Zone.MESA_TOP) return BiomeKind.TEPUI_SUMMIT;
        if (h > Biomes.peakLine(t)) return Biomes.climate(t, w, h, v);
        return v > 0.35 ? BiomeKind.BAMBOO_JUNGLE : v < -0.4 ? BiomeKind.SPARSE_JUNGLE : BiomeKind.JUNGLE;
    }
}
