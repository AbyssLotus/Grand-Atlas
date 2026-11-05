package com.seaweed.grandatlas.core.landform;

import static com.seaweed.grandatlas.core.Maths.smoothstep;

import com.seaweed.grandatlas.core.BiomeKind;
import com.seaweed.grandatlas.core.noise.GradientNoise;
import com.seaweed.grandatlas.core.province.Province;
import com.seaweed.grandatlas.core.world.WorldField;

/**
 * Rock desert (playtest round 2, after Wadi Rum and Dune: Awakening): red sand running between
 * wind-carved sandstone jebels, towers and fins ({@link Rocks}), with holes, arches, undercut feet
 * and grooved layers carved in 3D. Each province varies from open sand with scattered towers to a
 * dense maze of rock, so a district has both.
 *
 * <p>It pairs with dune seas (ProvinceMosaic's wonder pairs) and shares their dunes and wind, so
 * the dunes run on into the rocks. Rocks stand on a level baseline rather than on the dunes, so their
 * tops don't follow the sand, and a big dune can bury a small rock's foot.
 */
public final class RockDesert implements Landform {
    private final DuneSea dunes;
    private final long district, redSand;
    private final ThreadLocal<Rocks.Hit> hits = ThreadLocal.withInitial(Rocks.Hit::new);

    public RockDesert(long seed, DuneSea dunes) {
        this.dunes = dunes;
        district = GradientNoise.fork(seed, 40);
        redSand = GradientNoise.fork(seed, 41);
    }

    @Override public String id() { return "rock_desert"; }
    @Override public Elevation elevation() { return Elevation.LOW; }
    @Override public int color() { return 0xC86A3C; }
    @Override public double sameNeighbour() { return 1.4; }
    @Override public double hardness() { return 0.3; }
    // The rocks shrink toward a neighbour, so a dune sea swallows them gradually.
    @Override public double taper() { return 220; }
    @Override public double taperFloor() { return 0.3; }

    @Override
    public double weight(WorldField.Point c) {
        if (c.humidity() > -0.15 || c.temperature() < 0.1 || c.interior() < 0.1) return 0; // warm country only, like the dune sea
        return (1.6 * smoothstep(-0.15, -0.45, c.humidity()) + 0.7 * c.basin()) * smoothstep(0.1, 0.35, c.temperature());
    }

    @Override
    public void shape(Province province, Frame frame, double x, double z, double edge, Shape out) {
        // Smaller dunes than the open sea: sand sheets and ripples between the rocks.
        double sand = 10 + 0.5 * (dunes.dunes(x, z) - 10);
        double density = 0.45 + 0.55 * smoothstep(0.3, 0.7, Ordinary.fbm01(district, x / 2400.0, z / 2400.0, 2));
        Rocks.Hit hit = hits.get();
        dunes.rocks().sample(x, z, density, hit);
        double rockTop = 10 + hit.top;
        double rock = Math.max(0, rockTop - sand);
        Zone zone;
        if (rock > 6) {
            zone = Zone.ROCK;
        } else {
            // Red sand gives way to the dune sea's gold in patches near the edge, not along a line.
            double patch = Ordinary.fbm01(redSand, x / 90.0, z / 90.0, 2);
            zone = edge > 40 + 220 * patch ? Zone.SAND : Zone.DUNE;
        }
        out.set(Math.max(sand, rockTop), zone, rock > 6 ? 0.04 : 0);
        out.rock = rock;
        out.rockInside = hit.inside;
        out.rockSize = hit.size;
    }

    @Override
    public BiomeKind biome(Zone zone, double t, double w, double h, double v) {
        if (h > 200) return com.seaweed.grandatlas.core.Biomes.climate(t, w, h, v); // high ground: not sand
        return switch (zone) {
            case ROCK -> BiomeKind.BADLANDS;
            case SAND -> BiomeKind.RED_DESERT;
            default -> BiomeKind.DESERT;
        };
    }
}
