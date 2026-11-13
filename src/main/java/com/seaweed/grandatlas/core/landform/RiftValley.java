package com.seaweed.grandatlas.core.landform;

import static com.seaweed.grandatlas.core.Maths.smoothstep;

import com.seaweed.grandatlas.core.BiomeKind;
import com.seaweed.grandatlas.core.Biomes;
import com.seaweed.grandatlas.core.noise.Fractal;
import com.seaweed.grandatlas.core.noise.GradientNoise;
import com.seaweed.grandatlas.core.province.Province;
import com.seaweed.grandatlas.core.world.WorldField;

/**
 * A rift valley (M5 real-world wonders, design §9; after the Great Rift Valley in Kenya and
 * Ethiopia): the land pulled apart along a line. A flat floor 240 to 400 blocks wide runs straight
 * through the province, sunk between walls that climb in three or four fault scarps, each a sheer
 * drop with a tilted terrace above it, to shoulders standing 120 to 260 blocks over the floor. On
 * the floor stand one or two volcanoes, and long troughs along its middle where water gathers.
 *
 * <p>The line runs through the province's centre at the province's own heading, wavering a
 * little; the province's taper closes the rift at its two ends. The scarps are kept sheer through
 * {@link Shape#envelope}.
 */
public final class RiftValley implements Landform {
    private final long floor, wander, scarps, cones;

    public RiftValley(long seed) {
        floor = GradientNoise.fork(seed, 160);
        wander = GradientNoise.fork(seed, 161);
        scarps = GradientNoise.fork(seed, 162);
        cones = GradientNoise.fork(seed, 163);
    }

    private com.seaweed.grandatlas.core.province.ProvinceMosaic mosaic;

    /** The world's province mosaic, whose warped space the rift is laid out in (set once, by AtlasCore). */
    public void bind(com.seaweed.grandatlas.core.province.ProvinceMosaic mosaic) {
        this.mosaic = mosaic;
    }

    @Override public String id() { return "rift_valley"; }
    @Override public Elevation elevation() { return Elevation.MID; }
    @Override public int color() { return 0xB08A50; }
    @Override public double hardness() { return 0.3; }
    @Override public double taper() { return 240; }
    @Override public double taperFloor() { return 0.0; }

    @Override
    public double weight(WorldField.Point c) {
        // Warm, dry to middling country, well inland: savanna and grassland.
        if (c.temperature() < -0.15 || c.temperature() > 0.7 || c.humidity() > 0.3 || c.interior() < 0.3) return 0;
        return 0.3 * smoothstep(-0.15, 0.15, c.temperature()) * (1 - smoothstep(0.05, 0.3, c.humidity()));
    }

    /** The rift's frame in this province: heading, half width of the floor, and its steps. */
    private record Rift(double nx, double nz, double half, int steps, double[] stepW, double[] stepH) {}

    private Rift rift(Province p) {
        double heading = p.param(0) * Math.PI;
        double half = 120 + 80 * p.param(1);
        int steps = 3 + (p.param(2) < 0.45 ? 1 : 0);
        double[] w = new double[steps], h = new double[steps];
        for (int k = 0; k < steps; k++) {
            w[k] = 60 + 40 * p.param(10 + k);
            h[k] = 40 + 26 * p.param(20 + k);
        }
        return new Rift(-Math.sin(heading), Math.cos(heading), half, steps, w, h);
    }

    @Override
    public void shape(Province province, Frame frame, double x, double z, double edge, Shape out) {
        Rift r = rift(province);
        // Laid out in the mosaic's warped space, where the province's centre is.
        double wx = mosaic == null ? x : mosaic.warpX(x, z), wz = mosaic == null ? z : mosaic.warpZ(x, z);
        double dx = wx - province.x(), dz = wz - province.z();
        double along = dx * r.nz - dz * r.nx;               // distance along the rift
        double across = dx * r.nx + dz * r.nz;              // signed distance from its line
        across += 70 * GradientNoise.noise2(wander ^ province.seed(), along / 900.0, 0.5)
                + 14 * GradientNoise.noise2(wander ^ (province.seed() * 3), along / 160.0, 1.5);
        double s = Math.abs(across);
        int side = across < 0 ? 0 : 1;

        // The floor: dead flat in the large, with low swells, and troughs along its middle where lakes lie.
        double flat = 6 + 9 * Ordinary.fbm01(floor, x / 300.0, z / 300.0, 3);
        double trough = Math.abs(Fractal.fbm(floor ^ 0x31L, along / 520.0, 3.3 + side, 2));
        double centre = 1 - smoothstep(0, r.half * 0.45, s);
        flat -= 7 * centre * (1 - smoothstep(0.05, 0.3, trough));

        // The walls: each scarp a sheer drop (its line wavering on its own), the terrace above it
        // tilted back down away from the rift, so the land steps up like a stair.
        double relief = flat, upper = flat, pos = r.half;
        Zone zone = Zone.BASIN_FLOOR;
        boolean nearScarp = false;
        double height = 0;
        for (int k = 0; k < r.steps; k++) {
            double line = pos + 30 * GradientNoise.noise2(scarps ^ (province.seed() + 17L * k + side * 991L), along / 260.0, k * 1.7);
            double lower = height;
            height += r.stepH[k];
            double top = flat + height;
            if (s >= line) {
                // On or past this scarp: the drop over two blocks, then the terrace.
                double into = s - line;
                double next = k + 1 < r.steps ? r.stepW[k] : 300;
                double tilt = 9 * (1 - smoothstep(0, next, into)); // highest at the scarp's lip
                relief = flat + lower + (top - flat - lower) * smoothstep(0, 2.5, into) + tilt * smoothstep(0, 3, into);
                upper = top + tilt;
                zone = into < 4 ? Zone.HIGH_SLOPE : Zone.UPLAND;
            } else if (s > line - 6) {
                // Just under a scarp: the envelope covers it so the grid can't round its face off.
                nearScarp = true;
                upper = top + 9;
            }
            pos = line + r.stepW[k];
        }
        // Past the last step the shoulder falls slowly away from the rift into the plateau.
        if (s > pos) relief -= 0.12 * Math.min(900, s - pos) * smoothstep(0, 300, s - pos);

        double rough = zone == Zone.HIGH_SLOPE || nearScarp ? 0.02 : 0.1;
        out.set(relief, zone, rough);
        if (nearScarp) out.envelope = Math.max(0, upper - relief);

        // Volcanoes on the floor: one or two cones standing on the rift's line.
        int n = 1 + (province.param(3) < 0.5 ? 1 : 0);
        for (int k = 0; k < n; k++) {
            double u = (province.param(30 + k) * 2 - 1) * 380;
            double cr = 110 + 70 * province.param(40 + k), ch = 90 + 90 * province.param(50 + k);
            double vx = province.x() + r.nz * u, vz = province.z() - r.nx * u;
            double d = Math.hypot(wx - vx, wz - vz);
            if (d >= cr) continue;
            double q = d / cr, crater = 0.13;
            double cone = ch * Math.pow(1 - q, 1.6);
            if (q < crater) cone = ch * Math.pow(1 - crater, 1.6) - 18 * (1 - q / crater) * (1 - q / crater);
            double ux = d > 0 ? (wx - vx) / d : 1, uz = d > 0 ? (wz - vz) / d : 0;
            cone -= 8 * (1 - smoothstep(0, 0.2, Math.abs(GradientNoise.noise2(cones ^ (province.seed() + k), 8 * ux, 8 * uz))))
                    * smoothstep(0.15, 0.3, q) * (1 - q);
            if (flat + cone > out.relief) {
                out.relief = flat + cone;
                out.zone = Zone.PEAK;
                out.roughness = 0.08;
            }
        }
    }

    @Override
    public BiomeKind biome(Zone zone, double t, double w, double h, double v) {
        if (h > Biomes.peakLine(t)) return Biomes.climate(t, w, h, v);
        return switch (zone) {
            case PEAK -> t > 0.2 ? BiomeKind.SAVANNA_PLATEAU : BiomeKind.WINDSWEPT_HILLS;
            case HIGH_SLOPE, UPLAND -> t > 0.25 ? (v > 0.3 ? BiomeKind.WINDSWEPT_SAVANNA : BiomeKind.SAVANNA_PLATEAU) : BiomeKind.MEADOW;
            default -> t > 0.2 ? BiomeKind.SAVANNA : BiomeKind.PLAINS;
        };
    }
}
