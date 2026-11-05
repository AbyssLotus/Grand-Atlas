package com.seaweed.grandatlas.core.macro;

import com.seaweed.grandatlas.core.noise.Fractal;
import com.seaweed.grandatlas.core.noise.GradientNoise;
import com.seaweed.grandatlas.core.world.WorldField;
import java.util.List;

/**
 * Where macro landforms are (design §6.4). The world is cut into 5 km cells; most host one
 * macro, picked by the context at its centre, so macros are 2-4.6 km across with gaps of about
 * their own size between them, the Breath of the Wild density the user asked for in playtest
 * round 2. (8 km cells at half occupancy left one per ~400 km², and in a tour only the plateau
 * got noticed.) Footprints are warped blobs whose radius wobbles
 * with direction, so outlines look like terrain, not circles.
 *
 * <p><b>No overlaps.</b> A candidate shrinks until its reach clears every higher-priority
 * candidate in a neighbouring cell (judged by their full, unshrunk reach), and is dropped only
 * if that leaves it under {@link #MIN_RADIUS}. Every surviving pair is therefore apart. Every
 * column sees at most one macro, and the terrain stays continuous. Candidates are judged
 * independently of each other's survival, so the rule is local and every chunk agrees.
 */
public final class MacroField {
    public static final double CELL = 5000;
    /** Chance an eligible (mostly land) cell offers a candidate, before shrinking to fit. */
    private static final double HOST_CHANCE = 0.85;
    /** A candidate squeezed below this radius is dropped rather than kept as a stub. */
    private static final double MIN_RADIUS = 650;
    /** Footprint warp amplitude, in blocks. */
    private static final double WARP = 220;

    /** A column's relation to the macro it falls in. */
    public static final class Hit {
        public MacroSite site;
        /** Distance from the centre over the footprint radius in this direction (1 = on the edge). */
        public double s;
        /** Blocks inside the edge (negative outside). */
        public double inside;
        /** Unit direction from the centre. */
        public double dirX, dirZ;
    }

    private static final MacroSite NONE = new MacroSite(null, 0, 0, 0, 0, 0, null, 0);

    private final long seed;
    private final WorldField world;
    private final List<Macro> kinds;
    private final long warpX, warpZ;
    private final ThreadLocal<SiteCache> cache = ThreadLocal.withInitial(SiteCache::new);

    public MacroField(long seed, WorldField world, List<Macro> kinds) {
        this.seed = seed;
        this.world = world;
        this.kinds = kinds;
        warpX = GradientNoise.fork(seed, 1);
        warpZ = GradientNoise.fork(seed, 2);
    }

    public List<Macro> kinds() {
        return kinds;
    }

    /** Fills {@code out} if (x, z) is within some macro's reach; returns whether it is. */
    public boolean query(double x, double z, Hit out) {
        int cx = GradientNoise.floor(x / CELL), cz = GradientNoise.floor(z / CELL);
        for (int dz = -1; dz <= 1; dz++) {
            for (int dx = -1; dx <= 1; dx++) {
                MacroSite site = site(cx + dx, cz + dz);
                if (site == null) continue;
                shape(site, x, z, out);
                if (out.inside > -site.kind().reachBlocks(site)) {
                    return true;
                }
            }
        }
        return false;
    }

    /** The surviving macro of a cell, or null. */
    public MacroSite site(int cx, int cz) {
        SiteCache c = cache.get();
        long key = ((long) cx << 32) | (cz & 0xFFFFFFFFL);
        int slot = (int) GradientNoise.mix(key ^ 0xA5A5L) & (SiteCache.SIZE - 1);
        if (c.values[slot] != null && c.keys[slot] == key) {
            return c.values[slot] == NONE ? null : c.values[slot];
        }
        MacroSite site = candidate(cx, cz);
        if (site != null) {
            double priority = unit(cx, cz, 3);
            // The most this site may reach: clear of every higher-priority neighbour's full
            // reach, with 2 × the footprint warp (≤ ~220 blocks each way) as margin.
            double allowed = Double.MAX_VALUE;
            for (int dz = -1; dz <= 1; dz++) {
                for (int dx = -1; dx <= 1; dx++) {
                    if (dx == 0 && dz == 0) continue;
                    MacroSite other = candidate(cx + dx, cz + dz);
                    if (other != null && unit(cx + dx, cz + dz, 3) > priority) {
                        double d = Math.hypot(other.x() - site.x(), other.z() - site.z());
                        allowed = Math.min(allowed, d - other.reachBlocks() - 2 * WARP);
                    }
                }
            }
            site = fit(site, allowed);
        }
        c.keys[slot] = key;
        c.values[slot] = site == null ? NONE : site;
        return site;
    }

    /** Shrinks a site until its reach fits within {@code allowed}; null if it gets too small. */
    private static MacroSite fit(MacroSite site, double allowed) {
        MacroSite s = site;
        for (int i = 0; i < 6 && s.reachBlocks() > allowed; i++) {
            double radius = s.radius() * Math.max(0, allowed) / s.reachBlocks() * 0.98;
            if (radius < MIN_RADIUS) return null;
            s = new MacroSite(s.kind(), s.cellX(), s.cellZ(), s.x(), s.z(), radius, s.centre(), s.seed());
        }
        return s.reachBlocks() <= allowed ? s : null;
    }

    /** Searches outward ring by ring for the nearest macro of a kind. */
    public MacroSite find(Macro kind, double x, double z, int maxRings) {
        int cx = GradientNoise.floor(x / CELL), cz = GradientNoise.floor(z / CELL);
        for (int r = 0; r <= maxRings; r++) {
            MacroSite best = null;
            double bestD = Double.MAX_VALUE;
            for (int dz = -r; dz <= r; dz++) {
                for (int dx = -r; dx <= r; dx++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != r) continue;
                    MacroSite s = site(cx + dx, cz + dz);
                    if (s == null || s.kind() != kind) continue;
                    double d = Math.hypot(s.x() - x, s.z() - z);
                    if (d < bestD) {
                        bestD = d;
                        best = s;
                    }
                }
            }
            if (best != null) return best;
        }
        return null;
    }

    /**
     * Footprint geometry at (x, z): a warped point measured against a radius that wobbles with
     * direction. Direction noise is sampled on a circle, so it's continuous all the way round.
     */
    public void shape(MacroSite site, double x, double z, Hit out) {
        double wx = x + WARP * Fractal.fbm(warpX, x / 1100.0, z / 1100.0, 2);
        double wz = z + WARP * Fractal.fbm(warpZ, x / 1100.0, z / 1100.0, 2);
        double dx = wx - site.x(), dz = wz - site.z();
        double d = Math.sqrt(dx * dx + dz * dz);
        double ux = d > 1e-6 ? dx / d : 1, uz = d > 1e-6 ? dz / d : 0;
        double wobble = 1 + 0.15 * GradientNoise.noise2(site.seed(), ux * 1.7, uz * 1.7)
                + 0.06 * GradientNoise.noise2(site.seed() ^ 0x77L, ux * 4.1, uz * 4.1);
        double r = site.radius() * wobble;
        out.site = site;
        out.s = d / r;
        out.inside = r - d;
        out.dirX = ux;
        out.dirZ = uz;
    }

    private MacroSite candidate(int cx, int cz) {
        if (unit(cx, cz, 1) > HOST_CHANCE) return null;
        double x = (cx + 0.3 + 0.4 * unit(cx, cz, 10)) * CELL;
        double z = (cz + 0.3 + 0.4 * unit(cx, cz, 11)) * CELL;
        WorldField.Point centre = world.at(x, z);
        if (centre.continentalness() < 0.25) return null;
        double total = 0;
        double[] weights = new double[kinds.size()];
        for (int i = 0; i < weights.length; i++) {
            weights[i] = kinds.get(i).weight(centre);
            total += weights[i];
        }
        if (total <= 0) return null;
        double target = unit(cx, cz, 2) * total;
        Macro kind = kinds.get(kinds.size() - 1);
        for (int i = 0; i < weights.length; i++) {
            target -= weights[i];
            if (target < 0) {
                kind = kinds.get(i);
                break;
            }
        }
        long siteSeed = GradientNoise.hash(seed, cx, 5, cz);
        MacroSite draft = new MacroSite(kind, cx, cz, x, z, 0, centre, siteSeed);
        return new MacroSite(kind, cx, cz, x, z, kind.radius(draft), centre, siteSeed);
    }

    private double unit(int cx, int cz, int salt) {
        return (GradientNoise.hash(seed + salt * 0x9E3779B97F4A7C15L, cx, cz) >>> 11) * 0x1.0p-53;
    }

    private static final class SiteCache {
        static final int SIZE = 256;
        final long[] keys = new long[SIZE];
        final MacroSite[] values = new MacroSite[SIZE];
    }
}
