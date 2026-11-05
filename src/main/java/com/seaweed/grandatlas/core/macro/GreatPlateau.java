package com.seaweed.grandatlas.core.macro;

import static com.seaweed.grandatlas.core.Maths.lerp;
import static com.seaweed.grandatlas.core.Maths.smoothstep;

import com.seaweed.grandatlas.core.Maths;
import com.seaweed.grandatlas.core.landform.Frame;
import com.seaweed.grandatlas.core.landform.Landform;
import com.seaweed.grandatlas.core.noise.Fractal;
import com.seaweed.grandatlas.core.noise.GradientNoise;
import com.seaweed.grandatlas.core.world.WorldField;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Great Plateau (design §6.4, after Breath of the Wild): a 2.4–5.6 km tableland lifted 260–380
 * blocks by sheer cliff walls, with two to four ramps up. On top, its own sub-mosaic of fields,
 * forested hills, uplands and the odd range runs at 75% relief, and every plateau carries three
 * landmarks of its own:
 * <ul>
 *   <li><b>A summit</b> off to one side, 190–340 blocks over the tableland (its Mount Hylia),
 *       snow-capped from altitude alone.</li>
 *   <li><b>A lake</b> 100-280 blocks across on the far side of the centre.</li>
 *   <li><b>A river</b> from the lake to the rim, where it pours off the cliff as a waterfall. Its
 *       course is traced once per plateau over the terrain, with a water level that only ever
 *       falls, so it cuts a gorge through any hill in its way. It leaves clear of the ramps.</li>
 * </ul>
 * (Playtest round 2: the top was "nothing other than plains and forest", with no rivers flowing
 * off and no mountains.)
 */
public final class GreatPlateau extends Macro {
    private static final double RIVER_STEP = 16;

    private final long jag, summitRidges, meander;
    private final Map<Long, Plan> plans = new ConcurrentHashMap<>();

    public GreatPlateau(long seed) {
        super(GradientNoise.fork(seed, 201));
        jag = noiseSeed(1);
        summitRidges = noiseSeed(2);
        meander = noiseSeed(3);
    }

    @Override public String id() { return "great_plateau"; }
    @Override public int color() { return 0xFFD040; }
    @Override public double subCell() { return 700; }
    @Override public double altitudeDiscount() { return 0.6; }

    @Override
    public double weight(WorldField.Point c) {
        return c.interior() < 0.35 ? 0 : 1.0 * (1 - 0.6 * c.belt());
    }

    @Override
    public double radius(MacroSite site) {
        return 1200 + 1600 * site.param(1);
    }

    @Override
    public double reachBlocks(MacroSite site) {
        return 300; // half the widest ramp, plus jag
    }

    @Override
    public double familyWeight(Landform l, WorldField.Point c) {
        return switch (l.id()) {
            case "hills" -> 2.0;
            case "highlands" -> 1.5;
            case "lowlands" -> 1.0;
            // Ranges attract each other (x2.2 per mountain neighbour), so even 0.5 filled half
            // the plateau with peaks. 0.3 gives a ridge or two besides the summit landmark.
            case "megarange" -> 0.3;
            default -> 0;
        };
    }

    private double lift(MacroSite site) {
        return 260 + 120 * site.param(2);
    }

    @Override
    public Frame frame(MacroField.Hit hit, WorldField.Point world) {
        double datum = world.datum() + lift(hit.site);
        return new Frame(datum, -40, 420, 0.75, 0, 0, datum);
    }

    @Override
    public boolean covers(MacroField.Hit hit) {
        return hit.inside > 600;
    }

    // ---- landmarks

    /** One plateau's landmarks, planned once per site. */
    private record Plan(double summitX, double summitZ, double summitR, double summitH,
                        double lakeX, double lakeZ, double lakeR, double lakeLevel,
                        double axisX, double axisZ, double[] riverX, double[] riverZ, double[] riverLevel,
                        double riverHalf) {}

    @Override
    public void landmarks(MacroField.Hit hit, double x, double z, Terrain terrain, Landmarks out) {
        MacroSite site = hit.site;
        Plan p = plans.computeIfAbsent(((long) site.cellX() << 32) ^ (site.cellZ() & 0xFFFFFFFFL), k -> plan(site, terrain));
        double h = out.height;

        // The summit: a ridged peak with concave flanks, rising from the tableland's own level, so
        // it stands over whatever hills or ridges are there instead of stacking onto them (stacked
        // on a range, it ran into the height cap).
        double ds = Math.hypot(x - p.summitX, z - p.summitZ);
        if (ds < p.summitR * 1.3) {
            double ux = (x - p.summitX) / Math.max(1, ds), uz = (z - p.summitZ) / Math.max(1, ds);
            double lobes = 1 + 0.25 * GradientNoise.noise2(summitRidges ^ site.seed(), ux * 1.5, uz * 1.5);
            double s = ds / (p.summitR * lobes);
            if (s < 1) {
                double cone = Math.pow(1 - s, 1.5);
                double ridges = Fractal.erodedRidges(summitRidges, x / 240.0, z / 240.0, 4);
                h = Math.max(h, out.datum + 20 + p.summitH * cone * (0.6 + 0.4 * ridges));
                out.roughness = 0.4 * smoothstep(0.05, 0.3, cone);
                if (cone > 0.12) out.zone = MacroZone.SUMMIT;
            }
        }

        // The lake: a bowl with gentle shores, its outline wavy and drawn out (round 3c: "it's just
        // a circle"). Distances are measured against the outline toward each direction.
        double dl = Math.hypot(x - p.lakeX, z - p.lakeZ) / lakeShape(site, Math.atan2(z - p.lakeZ, x - p.lakeX));
        if (dl < p.lakeR + 120) {
            h = channel(h, dl, p.lakeR, p.lakeLevel, 4 + 0.08 * p.lakeR, 0.5);
            if (dl < p.lakeR) {
                out.water = p.lakeLevel;
                out.zone = MacroZone.LAKE;
            }
        }

        // The river: the nearest stretch of its course, found from the position along its axis.
        // Retired (OWN_RIVER): a straight channel from before the drainage network, whose own
        // rivers now cross plateaus and pour off their edges. Where they met, the two stood at
        // different levels: walls of water.
        int n = p.riverX.length;
        if (OWN_RIVER && n > 1) {
            double along = (x - p.lakeX) * p.axisX + (z - p.lakeZ) * p.axisZ;
            double across = -(x - p.lakeX) * p.axisZ + (z - p.lakeZ) * p.axisX;
            if (along > -200 && along < n * RIVER_STEP + 200 && Math.abs(across) < 300) {
                int i0 = Math.clamp((long) (along / RIVER_STEP) - 14, 0, n - 2);
                int i1 = Math.clamp((long) (along / RIVER_STEP) + 14, 0, n - 2);
                double best = Double.MAX_VALUE, level = 0;
                for (int i = i0; i <= i1; i++) {
                    double ax = p.riverX[i], az = p.riverZ[i], bx = p.riverX[i + 1], bz = p.riverZ[i + 1];
                    double vx = bx - ax, vz = bz - az;
                    double t = Math.max(0, Math.min(1, ((x - ax) * vx + (z - az) * vz) / (vx * vx + vz * vz)));
                    double d = Math.hypot(x - (ax + t * vx), z - (az + t * vz));
                    if (d < best) {
                        best = d;
                        level = lerp(t, p.riverLevel[i], p.riverLevel[i + 1]);
                    }
                }
                if (best < p.riverHalf + 150) {
                    h = channel(h, best, p.riverHalf, level, 3, 1.0);
                    if (best < p.riverHalf) {
                        out.water = Double.isNaN(out.water) ? level : Math.max(out.water, level);
                        if (out.zone != MacroZone.LAKE) out.zone = MacroZone.RIVER; // it starts in the lake
                    }
                }
            }
        }
        out.height = h;
    }

    /**
     * Ground shaped to hold water at {@code level}: a bed {@code depth} deep inside
     * {@code half}, walls rising at {@code slope} outside it (cut into higher ground, so a deep
     * course runs in a gorge), and a levee where the ground dips below the water. Continuous at
     * the water's edge.
     */
    private static double channel(double h, double d, double half, double level, double depth, double slope) {
        if (d < half) {
            double q = d / half;
            return level - depth * (1 - q * q);
        }
        double bank = level + 1.5 * smoothstep(half, half + 2, d);
        h = Math.min(h, bank + (d - half) * slope);
        return Math.max(h, lerp(smoothstep(half + 10, half + 1, d), h, bank));
    }

    private Plan plan(MacroSite site, Terrain terrain) {
        double radius = site.radius();
        double[] s = new double[2];

        double a = site.param(20) * Math.PI * 2;
        double summitR = Math.min(260 + 220 * site.param(21), 0.38 * radius);
        double summitX = site.x() + Math.cos(a) * 0.5 * radius, summitZ = site.z() + Math.sin(a) * 0.5 * radius;
        double summitH = 190 + 150 * site.param(22);

        double la = a + Math.PI + (site.param(23) - 0.5) * 1.2;
        // Of eight spots across the summit's far side, the lowest: a lake lies in a hollow.
        double lakeX = 0, lakeZ = 0, lowest = Double.MAX_VALUE;
        for (int k = 0; k < 8; k++) {
            double ang = la + (k - 3.5) * 0.3, dist = (0.14 + 0.05 * (k % 4)) * radius;
            double cx = site.x() + Math.cos(ang) * dist, cz = site.z() + Math.sin(ang) * dist;
            double g = terrainAt(terrain, cx, cz, s);
            if (g < lowest) {
                lowest = g;
                lakeX = cx;
                lakeZ = cz;
            }
        }
        double lakeR = 50 + 90 * site.param(24);
        // Just under the lowest ground round its real outline, so the shores hold it.
        double lakeLevel = Double.MAX_VALUE;
        for (int k = 0; k < 36; k++) {
            double ang = k * Math.PI * 2 / 36, rr = lakeR * lakeShape(site, ang) + 12;
            if (terrain.sample(lakeX + Math.cos(ang) * rr, lakeZ + Math.sin(ang) * rr, s)) {
                lakeLevel = Math.min(lakeLevel, s[0]);
            }
        }
        lakeLevel = (lakeLevel == Double.MAX_VALUE ? terrainAt(terrain, lakeX, lakeZ, s) : lakeLevel) - 1.5;

        // The river leaves roughly away from the summit, clear of the ramps.
        double ra = exitAngle(site, la + (site.param(25) - 0.5) * 0.8, a);
        double ex = site.x() + Math.cos(ra) * 1.4 * radius, ez = site.z() + Math.sin(ra) * 1.4 * radius;
        double len = Math.hypot(ex - lakeX, ez - lakeZ);
        double ux = (ex - lakeX) / len, uz = (ez - lakeZ) / len;
        List<double[]> course = new ArrayList<>();
        double level = lakeLevel;
        int crest = -1;
        for (int i = 0; i * RIVER_STEP < len; i++) {
            double t = i * RIVER_STEP;
            double wander = (100 * GradientNoise.noise2(meander ^ site.seed(), t / 420.0, 0.5)
                    + 25 * GradientNoise.noise2(meander ^ site.seed() ^ 0x3L, t / 110.0, 0.5)) * smoothstep(0, 250, t);
            double px = lakeX + ux * t - uz * wander, pz = lakeZ + uz * t + ux * wander;
            if (!terrain.sample(px, pz, s)) break;
            if (crest < 0) {
                if (s[1] < 30) {
                    crest = i; // the top of the cliff: from here the water falls
                } else if (t > lakeR + 6) {
                    double low = s[0];
                    if (terrain.sample(px - uz * 10, pz + ux * 10, s)) low = Math.min(low, s[0]);
                    if (terrain.sample(px + uz * 10, pz - ux * 10, s)) low = Math.min(low, s[0]);
                    level = Math.min(level, low - 3);
                }
            }
            course.add(new double[] {px, pz, level});
            if (crest >= 0 && i >= crest + 3) break;
        }
        if (crest < 0) course.clear(); // never reached the rim: no river
        double[] rx = new double[course.size()], rz = new double[course.size()], rl = new double[course.size()];
        for (int i = 0; i < rx.length; i++) {
            rx[i] = course.get(i)[0];
            rz[i] = course.get(i)[1];
            rl[i] = course.get(i)[2];
        }
        return new Plan(summitX, summitZ, summitR, summitH, lakeX, lakeZ, lakeR, lakeLevel,
                ux, uz, rx, rz, rl, 4 + 3 * site.param(26));
    }

    /**
     * Where the plateau's river pours off the rim (its crest): {x, z, dirX, dirZ, half-width}, or null when it
     * never reached the rim. The world digs a plunge pool where the fall lands (AtlasCore).
     */
    public double[] waterfall(MacroSite site, Terrain terrain) {
        if (!OWN_RIVER) return null;
        Plan p = plans.computeIfAbsent(((long) site.cellX() << 32) ^ (site.cellZ() & 0xFFFFFFFFL), k -> plan(site, terrain));
        int n = p.riverX.length;
        if (n < 2) return null;
        int crest = Math.max(0, n - 4); // the course runs three steps past the crest, where the fall begins
        return new double[] {p.riverX[crest], p.riverZ[crest], p.axisX, p.axisZ, p.riverHalf};
    }

    /** The plateau's own river and its fall (see {@link #landmarks}): retired. */
    public static final boolean OWN_RIVER = false;

    /** The lake: {x, z, the farthest its outline reaches, level}. */
    public double[] lake(MacroSite site, Terrain terrain) {
        Plan p = plans.computeIfAbsent(((long) site.cellX() << 32) ^ (site.cellZ() & 0xFFFFFFFFL), k -> plan(site, terrain));
        return new double[] {p.lakeX(), p.lakeZ(), p.lakeR() * 1.56, p.lakeLevel()};
    }

    /** The lake's outline toward angle a, as a multiple of its radius: drawn out one way, lobed, never round. */
    private static double lakeShape(MacroSite site, double a) {
        double p1 = site.param(27) * Math.PI * 2, p2 = site.param(28) * Math.PI * 2, p3 = site.param(29) * Math.PI * 2;
        return 1 + 0.3 * Math.sin(2 * a + p1) + 0.17 * Math.sin(3 * a + p2) + 0.09 * Math.sin(5 * a + p3);
    }

    private static double unit(long h) {
        return (h >>> 11) * 0x1.0p-53;
    }

    /** 1 at 0, easing to 0 at |u| = 1. */
    private static double bell(double u) {
        double q = 1 - u * u;
        return q <= 0 ? 0 : q * q;
    }

    /**
     * Air the wall cuts at a block (min it with the ground), or +infinity: the hollow under an
     * overhanging lip, and the wall's own cave mouths. A cave mouth sits at some height up the
     * face, opens as an arch with a flat floor, and narrows as it runs 12-28 blocks into the rock,
     * so a cave that reaches the wall looks meant (vanilla's caves are kept out of the face).
     */
    public static double carveWall(Wall w, int x, int y, int z) {
        if (w == null) return Double.POSITIVE_INFINITY;
        if (!Double.isNaN(w.ledgeLow()) && y > w.ledgeLow() && y < w.ledgeHigh()) {
            return -0.5 - 0.2 * Math.min(y - w.ledgeLow(), w.ledgeHigh() - y);
        }
        if (w.width() > 60 || w.high() - w.low() < 40) return Double.POSITIVE_INFINITY; // ramps and low walls: none
        double best = Double.POSITIVE_INFINITY;
        int k0 = GradientNoise.floor(w.arc() / WALL_CAVE_SPACING);
        for (int k = k0 - 1; k <= k0 + 1; k++) {
            long h = GradientNoise.hash(w.seed() ^ 0xCA7EL, k, 0);
            if (unit(h) > 0.45) continue;
            double u1 = unit(GradientNoise.mix(h + 1)), u2 = unit(GradientNoise.mix(h + 2)), u3 = unit(GradientNoise.mix(h + 3)), u4 = unit(GradientNoise.mix(h + 4));
            double half = 3 + 3 * u2, ry = 3.5 + 3.5 * u3, depth = 12 + 16 * u4;
            double ds = w.arc() - (k + 0.15 + 0.7 * u1) * WALL_CAVE_SPACING;
            if (Math.abs(ds) > half + 1) continue;
            double frac = 0.12 + 0.4 * unit(GradientNoise.mix(h + 5)), my = w.low() + frac * (w.high() - w.low());
            if (Math.abs(y - my) > ry + 1 || y < my - 0.55 * ry) continue; // a flat floor
            // Where the face is at this height: invert the wall's smoothstep.
            double s = 0.5 - Math.sin(Math.asin(1 - 2 * frac) / 3);
            double face = -w.width() / 2 + s * w.width(), in = w.dist() - face;
            if (in < -3 || in > depth) continue;
            double scale = 1 - 0.55 * Math.max(0, Math.min(1, in / depth));
            double q = (ds / (half * scale)) * (ds / (half * scale)) + ((y - my) / (ry * scale)) * ((y - my) / (ry * scale));
            if (q < 1) best = Math.min(best, (q - 1) * 2);
        }
        return best;
    }

    private static double terrainAt(Terrain terrain, double x, double z, double[] s) {
        return terrain.sample(x, z, s) ? s[0] : 0;
    }

    /**
     * The river's way out: the nearest angle to {@code wanted} (searching both ways in half-radian
     * steps) that is clear of every ramp (they use params 10.., as in {@link #combine}) and doesn't
     * head back toward the summit.
     */
    private static double exitAngle(MacroSite site, double wanted, double summitAngle) {
        int ramps = 2 + (int) (site.param(3) * 3);
        for (int k = 0; k < 13; k++) {
            double angle = wanted + (k % 2 == 1 ? 1 : -1) * ((k + 1) / 2) * 0.5;
            boolean clear = Math.cos(angle - summitAngle) < Math.cos(1.3);
            for (int r = 0; r < ramps && clear; r++) {
                clear = Math.cos(angle - site.param(10 + r) * Math.PI * 2) < Math.cos(0.45);
            }
            if (clear) return angle;
        }
        return wanted;
    }

    /** Blocks of wall per rare rim feature (a cove, a peninsula, an outcrop or a spike). */
    private static final double RIM_FEATURE_SPACING = 1000;
    /** Blocks of wall per chance of a cave mouth. */
    private static final double WALL_CAVE_SPACING = 180;

    /**
     * The wall (round 3c: "very vertical, exactly what we want, but a bit uniform"). On top of the
     * jagged cliff line, about once every 1000 blocks of wall a rim feature: a cove biting in, a
     * peninsula pushing out, a rocky outcrop, or a spire standing just off the wall. Along the wall
     * the lip varies within about 5 blocks: in places it juts out over the face (an overhang, cut
     * per block in {@link #carveWall}), in others the top rounds over.
     */
    @Override
    public void combine(MacroField.Hit hit, double outerH, double innerH, double x, double z, Combined out) {
        MacroSite site = hit.site;
        // Sheer walls (the user: "plateaus NEED to be WAY sharper in their cliffs"): faces 9 wide,
        // not 34, and one or two ramps up instead of two to four (they took a third of the rim).
        double ramp = spokes(hit, 1 + (int) (site.param(3) * 2), 10, 0.025);
        double width = Maths.lerp(ramp, 9, 520), cliff = 1 - ramp;
        double circumference = 2 * Math.PI * site.radius();
        double arc = (Math.atan2(z - site.z(), x - site.x()) + Math.PI) * site.radius();
        double e = hit.inside + 16 * GradientNoise.noise2(jag, x / 45.0, z / 45.0) * cliff;

        // Rim features, one per stretch of wall, each somewhere in the middle of its stretch.
        int stretches = Math.max(6, (int) Math.round(circumference / RIM_FEATURE_SPACING));
        double stretch = circumference / stretches;
        int k0 = GradientNoise.floor(arc / stretch);
        double spike = 0, spikeDrop = 0;
        for (int k = k0 - 1; k <= k0 + 1; k++) {
            long h = GradientNoise.hash(site.seed() ^ 0x51DEL, Math.floorMod(k, stretches), 0);
            double kind = unit(h), u1 = unit(GradientNoise.mix(h + 1)), u2 = unit(GradientNoise.mix(h + 2)), u3 = unit(GradientNoise.mix(h + 3));
            double ds = arc - (k + 0.2 + 0.6 * u1) * stretch;
            if (kind < 0.35) { // a cove
                e -= (40 + 50 * u3) * bell(ds / (60 + 80 * u2)) * cliff;
            } else if (kind < 0.65) { // a peninsula: steep sides, a broad end
                e += (50 + 70 * u3) * Math.sqrt(bell(ds / (45 + 50 * u2))) * cliff;
            } else if (kind < 0.85) { // a rocky outcrop, ragged
                e += (18 + 14 * u3 + 8 * GradientNoise.noise2(jag ^ 0x77L, x / 9.0, z / 9.0)) * bell(ds / (22 + 25 * u2)) * cliff;
            } else if (cliff > 0.5) { // a spire standing off the wall
                double r = 4 + 4 * u2, off = 16 + 18 * u3, d = Math.hypot(ds, e + off);
                double s = smooth(r + 2, r, d);
                if (s > spike) {
                    spike = s;
                    spikeDrop = 12 + 30 * u1;
                }
            }
        }

        // Where the river pours off, the face is sheer and the lip undercut, so the fall drops
        // clear into its pool instead of tumbling down the wall (round 3c).
        double fall = 0, fallHalf = 0;
        Plan plan = plans.get(((long) site.cellX() << 32) ^ (site.cellZ() & 0xFFFFFFFFL));
        if (plan != null && plan.riverX.length > 3) {
            int crest = plan.riverX.length - 4;
            double crestArc = (Math.atan2(plan.riverZ[crest] - site.z(), plan.riverX[crest] - site.x()) + Math.PI) * site.radius();
            double ds = Math.IEEEremainder(arc - crestArc, circumference);
            fallHalf = plan.riverHalf + 3;
            fall = bell(ds / (fallHalf + 16)) * cliff;
        }
        if (fall > 0) {
            width = Maths.lerp(fall, width, 3);
        }

        // The lip: a slow wave along the wall. Up, it juts out over the face; down, the top rounds over.
        double lip = 5 * Math.max(-1, Math.min(1, 1.6 * GradientNoise.noise2(jag ^ 0x55L, arc / 140.0, 0.5))) * cliff * (1 - fall);
        double c = smooth(-width / 2, width / 2 + (lip < 0 ? -3 * lip : 0), e);
        double height = Maths.lerp(c, outerH, innerH), ledgeLow = Double.NaN, ledgeHigh = Double.NaN;
        if (fall > 0.8 && c > 0.5 && e < width / 2 + 4) {
            ledgeLow = outerH; // hollow behind the fall, from the foot to under the lip
            ledgeHigh = innerH - 9; // a lip thick enough to keep the river's bed whole
        }
        if (lip > 0.5) {
            double top = smooth(-width / 2, width / 2, e + lip);
            if (top >= 0.97 && c < 0.97) {
                double lipH = Maths.lerp(top, outerH, innerH), thick = 4 + 3 * unit(GradientNoise.hash(site.seed(), (int) arc, 7));
                if (lipH - thick > height + 1) {
                    ledgeLow = height;
                    ledgeHigh = lipH - thick;
                    height = lipH;
                    c = top;
                }
            }
        }
        if (spike > 0) height = Math.max(height, Maths.lerp(spike, outerH, innerH - spikeDrop));
        out.height = height;
        out.inside = c;
        // Light texture on the face: heavy roughness half-filled cave mouths with "iffy terrain".
        out.roughness = spike > 0 ? 0 : 0.2 * cliff * (1 - Math.abs(2 * c - 1));
        out.wall = Math.abs(e) < width / 2 + 60 || spike > 0
                ? new Wall(e, arc, width, outerH, innerH, ledgeLow, ledgeHigh, site.seed(), !Double.isNaN(ledgeLow) || spike > 0) : null;
        if (ramp > 0.5 && Math.abs(e) < width / 2) {
            out.zone = MacroZone.RAMP;
        } else if (c >= 0.5) {
            out.zone = e > width / 2 + 80 ? MacroZone.INTERIOR : MacroZone.RIM;
        } else {
            out.zone = MacroZone.FOOT;
        }
    }
}
