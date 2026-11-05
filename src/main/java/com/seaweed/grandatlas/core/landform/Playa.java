package com.seaweed.grandatlas.core.landform;

import static com.seaweed.grandatlas.core.Maths.lerp;
import static com.seaweed.grandatlas.core.Maths.smoothstep;

import com.seaweed.grandatlas.core.BiomeKind;
import com.seaweed.grandatlas.core.noise.Fractal;
import com.seaweed.grandatlas.core.noise.GradientNoise;
import com.seaweed.grandatlas.core.province.Province;
import com.seaweed.grandatlas.core.world.WorldField;

/**
 * Playa in basin-and-range country (design §10): parallel bare ranges 150–280 blocks high,
 * alluvial fans spreading from their feet, and dead-flat white salt pans in the lowest basins.
 * Ranges come from an anisotropic noise (stretched 6:1 along the range axis), so they run
 * parallel. Fans are a soft ramp onto the basin floor, which stays exactly flat beyond it.
 */
public final class Playa implements Landform {
    private final long ranges, detail, pans, bajada;
    private final double cos, sin;

    public Playa(long seed) {
        ranges = GradientNoise.fork(seed, 40);
        detail = GradientNoise.fork(seed, 41);
        pans = GradientNoise.fork(seed, 42);
        bajada = GradientNoise.fork(seed, 43);
        double angle = (GradientNoise.fork(seed, 44) >>> 11) * 0x1.0p-53 * Math.PI;
        cos = Math.cos(angle);
        sin = Math.sin(angle);
    }

    @Override public String id() { return "playa"; }
    @Override public Elevation elevation() { return Elevation.LOW; }
    @Override public int color() { return 0xA8D8E0; }
    @Override public double hardness() { return 0.3; }
    @Override public double taper() { return 300; }
    @Override public double taperFloor() { return 0.25; }

    @Override
    public double weight(WorldField.Point c) {
        if (c.humidity() > 0.0 || c.temperature() < -0.5 || c.interior() < 0.1) return 0;
        return 1.3 * smoothstep(0.0, -0.4, c.humidity()) + 1.5 * c.basin();
    }

    @Override
    public void shape(Province province, Frame frame, double x, double z, double edge, Shape out) {
        double a = x * cos + z * sin, b = -x * sin + z * cos;
        double n = Math.abs(Fractal.fbm(ranges, a / 4200.0, b / 700.0, 2));
        double rangeMask = 1 - smoothstep(0.02, 0.13, n);
        double rangeHeight = 150 + 130 * province.param(1);
        double range = rangeHeight * Math.pow(rangeMask, 1.3)
                * (0.7 + 0.3 * Fractal.erodedRidges(detail, x / 500.0, z / 500.0, 4));

        // Pans lie along each basin's axis, as far from the range lines as the noise gets, so a
        // range never runs across one. (An independent pan mask let ridges slice salt flats in two.)
        double pan = smoothstep(-0.05, 0.12, Fractal.fbm(pans, x / 1600.0, z / 1600.0, 2)) * smoothstep(0.15, 0.21, n);
        // Dead flat wherever the crust is: the pan floor reaches 0 by pan = 0.6 and the crust
        // starts at 0.65. (The crust used to cover a slope of up to ~9 blocks.)
        double flat = smoothstep(0.3, 0.6, pan);
        double floor = lerp(flat, 8 + 10 * Ordinary.fbm01(bajada, x / 700.0, z / 700.0, 2), 0);
        floor = Math.max(floor, frame.reliefMin());

        double above = fan(range - floor, 60);
        Zone zone = above < 1 && pan > 0.65 ? Zone.PAN : above < 45 ? Zone.BASIN_FLOOR : Zone.RANGE;
        out.set(floor + above, zone, zone == Zone.RANGE ? 0.15 : 0);
        out.micro = 1 - flat;
    }

    /** Soft ramp: 0 for u ≤ 0, quadratic up to k, then linear. Concave fans at range feet. */
    private static double fan(double u, double k) {
        if (u <= 0) return 0;
        return u < k ? u * u / (2 * k) : u - k / 2;
    }

    @Override
    public BiomeKind biome(Zone zone, double t, double w, double h, double v) {
        return switch (zone) {
            case PAN -> BiomeKind.SALT_FLAT;
            case RANGE -> t > 0.3 ? BiomeKind.BADLANDS : BiomeKind.WINDSWEPT_HILLS;
            default -> t > 0.2 ? BiomeKind.DESERT : t > -0.1 ? BiomeKind.SAVANNA : BiomeKind.PLAINS;
        };
    }
}
