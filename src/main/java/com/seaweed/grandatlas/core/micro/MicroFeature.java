package com.seaweed.grandatlas.core.micro;

/**
 * One micro feature, placed by its meso site. Immutable apart from a check on first use: no river
 * through it, and for ponds, sinkholes and cenotes ground around them that holds them. The result
 * is the same whoever computes it, so races are harmless.
 *
 * @param r      main radius: a pond's or pit's top, a hoodoo's shaft, a cone's base
 * @param h      a hoodoo's or cone's height, a pit's depth
 * @param r2     a hoodoo's caprock radius, a cone's crater radius
 * @param extra  a cenote's water depth, a cone's crater depth, a gully's heading, a mushroom's colour (0 red, 1 brown)
 * @param ground the ground at its centre before any features or rivers
 */
public final class MicroFeature {
    public final MicroKind kind;
    public final double x, z, r, h, r2, extra, ground;
    private final double phase1, phase2;

    static final int UNKNOWN = 0, OK = 1, DRY = 2, NO = -1;
    volatile int state = UNKNOWN;
    /** A pond's or cenote's water level, once checked. */
    volatile double level = Double.NaN;
    /** A grotto's overhang (and its stream) faces this way, once checked: where the ground holds the stream. */
    volatile double heading = Double.NaN;
    /** Terraced cascades, once checked: the pools' boundaries down the slope (n+1) and their water (n). */
    volatile double[] steps, levels;

    MicroFeature(MicroKind kind, double x, double z, double r, double h, double r2, double extra, double ground, long seed) {
        this.kind = kind;
        this.x = x;
        this.z = z;
        this.r = r;
        this.h = h;
        this.r2 = r2;
        this.extra = extra;
        this.ground = ground;
        this.phase1 = (seed & 0xFFFF) / 65536.0 * Math.PI * 2;
        this.phase2 = ((seed >>> 16) & 0xFFFF) / 65536.0 * Math.PI * 2;
    }

    /** How far out it shapes or dresses the ground. */
    public double reach() {
        return switch (kind) {
            case POND -> r * 1.45 + 2;
            case SINKHOLE, CENOTE -> r * 1.5 + 4;
            case HOODOO -> r2 + 7;
            case CINDER_CONE -> r * 1.15;
            case BASALT_COLUMNS -> r;
            case ERRATIC -> r + 2;
            case TOR -> r * 2.2 + 4;
            case BUTTE -> r * 1.26 + 7;
            case GIANT_TREE -> r2 + 3;
            case GIANT_MUSHROOM -> r2 + 2;
            case SNAG -> 5;
            case GULLY -> r2 / 2 + r * 1.2 + 4;
            case GROTTO -> r + r2 + 18;
            case TRAVERTINE -> r2 + r * 2.6 + 10;
            case SPIRE -> r * 1.3 + 10;
            case CALDERA -> r2;
        };
    }

    /** Its outline's radius toward angle a, so ponds and pits aren't perfect circles. */
    public double outline(double a) {
        return switch (kind) {
            case POND -> {
                double lobes = r * (1 + 0.22 * Math.sin(2 * a + phase1) + 0.12 * Math.sin(3 * a + phase2) + 0.08 * Math.sin(5 * a + phase1 * 1.7));
                if (r <= 15) yield lobes;
                // A big lake runs long, the way glaciers scoured it: drawn out along one heading.
                double s = 1.3 + 0.9 * (phase2 / (Math.PI * 2)), da = a - phase1;
                yield lobes / Math.sqrt(Math.pow(Math.cos(da) / s, 2) + Math.pow(Math.sin(da) * s, 2)) / Math.sqrt(s);
            }
            case SINKHOLE, CENOTE, GROTTO -> r * (1 + 0.14 * Math.sin(3 * a + phase1));
            case HOODOO -> r2 * (1 + 0.08 * Math.sin(5 * a + phase1));
            case BUTTE -> r * (1 + 0.18 * Math.sin(3 * a + phase1) + 0.08 * Math.sin(7 * a + phase2));
            case SPIRE -> r * (1 + 0.2 * Math.sin(2 * a + phase1) + 0.12 * Math.sin(5 * a + phase2));
            default -> r;
        };
    }

    /** False for a feature that turned out to sit on a river (or a water feature on a slope). */
    public boolean present() {
        return state != NO;
    }

    /** A cenote whose rim was too low to hold its pool is a dry sinkhole. */
    public boolean wet() {
        return (kind == MicroKind.CENOTE || kind == MicroKind.POND || kind == MicroKind.GROTTO || kind == MicroKind.TRAVERTINE
                || kind == MicroKind.CALDERA) && state == OK;
    }

    public double level() {
        return level;
    }

    public double heading() {
        return heading;
    }

    // ---- a grotto's parts (see Features.grotto)

    /** The overhang's cavity: its centre, a little past the hole's edge toward the heading, and its radius. */
    public double cavityX() {
        return x + Math.cos(heading) * r * 0.6;
    }

    public double cavityZ() {
        return z + Math.sin(heading) * r * 0.6;
    }

    public double cavityR() {
        return r * 0.6 + r2;
    }

    /** The pit's floor, and the shelf's underside over the cavity (4 blocks of rock under the ground). */
    public double floor() {
        return ground - h;
    }

    public double shelf() {
        return ground - 4;
    }

    // ---- a caldera (see Features.shapes): r is the rim's radius, h its height over the ground, r2
    // how far out its flanks run.

    /** The rim's radius toward a: scalloped, where slices of the wall slid in. */
    public double calderaRim(double a) {
        return r * (1 + 0.07 * Math.sin(2 * a + phase2) + 0.05 * Math.sin(3 * a + phase1) + 0.025 * Math.sin(7 * a + phase2 * 1.3));
    }

    /** The rim's crest toward a: rising and falling round the ring, with a few peaks standing up out of it. */
    public double calderaCrest(double a) {
        double c = ground + h * (1 + 0.1 * Math.sin(2 * a + phase1) + 0.06 * Math.sin(3 * a + phase2) + 0.03 * Math.sin(6 * a + phase1 * 1.7));
        for (int k = 0; k < 3; k++) {
            double at = phase1 * (k + 1.3) + phase2 * k, da = Math.IEEEremainder(a - at, Math.PI * 2), w = 0.16 + 0.06 * k;
            c += h * (0.22 - 0.05 * k) * Math.exp(-da * da / (w * w));
        }
        return c;
    }

    /** How far the sheer cliff under the crest drops before the talus starts (once checked: it needs the level). */
    public double calderaCliff(double a) {
        return Math.max(8, (calderaCrest(a) - level) * (0.68 + 0.1 * Math.sin(4 * a + phase2) + 0.04 * Math.sin(9 * a + phase1)));
    }

    /** How far in from the rim the cliff reaches at its foot: near sheer. */
    public double calderaRun(double a) {
        return 2 + 0.16 * calderaCliff(a);
    }

    /** How wide the rounded top of the rim is outside the crest, before the flanks fall away. */
    public double calderaBand(double a) {
        return 7 + 3 * Math.sin(3 * a + phase2);
    }

    /** How deep the lake is over its floor. */
    public double calderaDepth() {
        return 18 + 0.12 * h;
    }

    /** How many ribs and gullies run down its flanks. */
    public int calderaRibs() {
        return 22 + (int) (phase1 * 2.5);
    }

    // ---- terraced cascades (see Features.terraces): r is the half-width at the top, r2 the length
    // down the slope, extra the downhill heading; (x, z) is the top.

    /** Half-width at s down the slope: a fan, widening downhill, swelling and pinching as it goes. */
    public double terraceWidth(double s) {
        return r * (1 + 0.9 * Math.max(0, Math.min(1, s / r2)))
                * (1 + 0.25 * Math.sin(s / 11 + phase1) + 0.12 * Math.sin(s / 5 + phase2));
    }

    /** The chain's middle at s: it wanders across the slope instead of running dead straight down. */
    public double terraceCentre(double s) {
        return r * (0.45 * Math.sin(s / 15 + phase2) + 0.2 * Math.sin(s / 6.5 + phase1));
    }

    /** How far a rim bows out downhill at a across the chain (signed), lopsided and wavy. */
    public double terraceBow(double a, double width) {
        double q = Math.min(1, Math.abs(a) / width);
        return 3.5 * (1 - q * q) * (1 + 0.35 * Math.signum(a) * Math.sin(phase1)) + 1.1 * Math.sin(a / 3.5 + phase2);
    }
}
