package com.seaweed.grandatlas.core;

import static com.seaweed.grandatlas.core.Maths.clamp;
import static com.seaweed.grandatlas.core.Maths.lerp;
import static com.seaweed.grandatlas.core.Maths.smoothstep;

import com.seaweed.grandatlas.core.hydro.Drainage;
import com.seaweed.grandatlas.core.micro.Features;
import com.seaweed.grandatlas.core.micro.MesoKind;
import com.seaweed.grandatlas.core.micro.MesoSite;
import com.seaweed.grandatlas.core.micro.MicroFeature;
import com.seaweed.grandatlas.core.micro.MicroKind;
import com.seaweed.grandatlas.core.landform.CanyonPlateau;
import com.seaweed.grandatlas.core.landform.DuneSea;
import com.seaweed.grandatlas.core.landform.Frame;
import com.seaweed.grandatlas.core.landform.Landform;
import com.seaweed.grandatlas.core.landform.Megarange;
import com.seaweed.grandatlas.core.landform.Ordinary;
import com.seaweed.grandatlas.core.landform.Playa;
import com.seaweed.grandatlas.core.landform.RockDesert;
import com.seaweed.grandatlas.core.landform.Shape;
import com.seaweed.grandatlas.core.landform.Zone;
import com.seaweed.grandatlas.core.macro.Basin;
import com.seaweed.grandatlas.core.macro.ElevatedMesa;
import com.seaweed.grandatlas.core.macro.GreatPlateau;
import com.seaweed.grandatlas.core.macro.Macro;
import com.seaweed.grandatlas.core.macro.MacroField;
import com.seaweed.grandatlas.core.macro.MacroSite;
import com.seaweed.grandatlas.core.macro.MacroZone;
import com.seaweed.grandatlas.core.macro.Wall;
import com.seaweed.grandatlas.core.macro.Massif;
import com.seaweed.grandatlas.core.noise.Fractal;
import com.seaweed.grandatlas.core.noise.GradientNoise;
import com.seaweed.grandatlas.core.province.Province;
import com.seaweed.grandatlas.core.province.ProvinceMosaic;
import com.seaweed.grandatlas.core.world.WorldField;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * The seeded terrain core: one per world. Pure Java, so the previewer and self-checks run it
 * without Minecraft. Immutable after construction apart from per-thread caches.
 *
 * <p>Per column (design §5–6): the world field gives continents, datum and climate; the province
 * mosaic gives the nearby provinces; each nearby landform shapes its relief over the datum;
 * the blender combines them (§7); then the ocean profile and coast finish the height. If the
 * column falls within a macro landform, the macro's own sub-mosaic runs too, in the macro's
 * frame, and the macro's edge combines the two.
 */
public final class AtlasCore {
    /** The world's floor (M7: the user's hard requirement). */
    public static final int MIN_Y = -512;
    /**
     * The old floor, which the surface terrain and the 0.x caves are still built against: no ground
     * goes below {@code TERRAIN_FLOOR + 16}, and the caverns and pits stay above it until M7c
     * replaces them.
     */
    public static final int TERRAIN_FLOOR = -128;
    /** Exclusive build limit. */
    public static final int MAX_Y = 1024;
    public static final int SEA_LEVEL = 63;
    /** No summit goes above this, leaving build room under the limit. */
    public static final int PEAK_CAP = 980;
    public static final double PROVINCE_CELL = 1600;

    /**
     * Density units per block of depth. About 16 blocks below the surface this reaches
     * vanilla's 1.5625 "solid crust" threshold, below which the router lets caves carve.
     */
    public static final double GRADIENT = 0.1;
    public static final double DENSITY_BOUND = (MAX_Y - MIN_Y) * GRADIENT + 8;

    /** Base half-width of a province transition, in warped blocks. */
    private static final double TRANSITION = 260;

    private final long seed;
    private final WorldField world;
    private final ProvinceMosaic mosaic;
    private final MacroField macros;
    private final Caves grandCaves;
    private final Passages passages;
    private final MegaCaverns megaCaverns;
    private final Waters waters;
    private final SetPieces setPieces;
    private final Drainage drainage;
    private final Features features;
    private final Map<Macro, ProvinceMosaic> subMosaics = new IdentityHashMap<>();
    private final List<Landform> landforms;
    private final Landform ocean;
    private final long roughness, caves, variant, weird, micro, coastNoise;
    private final ThreadLocal<ColumnCache> cache = ThreadLocal.withInitial(ColumnCache::new);
    private final ThreadLocal<Scratch> scratch = ThreadLocal.withInitial(Scratch::new);
    /** For a macro's landmarks looking around (see {@link #innerTerrain}); separate, since that runs inside sample(). */
    private final ThreadLocal<Scratch> traceScratch = ThreadLocal.withInitial(Scratch::new);
    private final Map<Macro, Macro.Terrain> innerTerrains = new IdentityHashMap<>();

    public AtlasCore(long seed) {
        this.seed = seed;
        world = new WorldField(seed);
        ocean = new Ordinary.Ocean();
        Landform lowlands = new Ordinary.Lowlands(seed);
        DuneSea dunes = new DuneSea(seed);
        landforms = List.of(lowlands, new Ordinary.Hills(seed), new Ordinary.Highlands(seed),
                new Megarange(seed), new CanyonPlateau(seed), dunes, new Playa(seed), new RockDesert(seed, dunes));
        mosaic = new ProvinceMosaic(GradientNoise.fork(seed, 50), PROVINCE_CELL, world, landforms, ocean, lowlands);
        macros = new MacroField(GradientNoise.fork(seed, 60), world,
                List.of(new GreatPlateau(seed), new Massif(seed), new Basin(seed), new ElevatedMesa(seed)));
        for (Macro kind : macros.kinds()) {
            subMosaics.put(kind, subMosaic(kind));
            innerTerrains.put(kind, (px, pz, out) -> innerTerrain(kind, px, pz, out));
        }
        grandCaves = new Caves(this, GradientNoise.fork(seed, 300));
        passages = new Passages(this, GradientNoise.fork(seed, 400));
        megaCaverns = new MegaCaverns(this, GradientNoise.fork(seed, 500));
        waters = new Waters(this, GradientNoise.fork(seed, 600));
        setPieces = new SetPieces(this, GradientNoise.fork(seed, 700));
        drainage = new Drainage(GradientNoise.fork(seed, 80), world, this::sampleBase);
        features = new Features(GradientNoise.fork(seed, 90), this::sampleBase, drainage);
        roughness = GradientNoise.fork(seed, 11);
        caves = GradientNoise.fork(seed, 12);
        variant = GradientNoise.fork(seed, 13);
        weird = GradientNoise.fork(seed, 14);
        micro = GradientNoise.fork(seed, 15);
        coastNoise = GradientNoise.fork(seed, 16);
    }

    public long seed() {
        return seed;
    }

    public List<Landform> landforms() {
        return landforms;
    }

    public Landform landform(String id) {
        return landforms.stream().filter(l -> l.id().equals(id)).findFirst().orElse(null);
    }

    public ProvinceMosaic mosaic() {
        return mosaic;
    }

    /** The world layer (continents, climate, island seas). */
    public WorldField world() {
        return world;
    }

    public MacroField macros() {
        return macros;
    }

    /** M7e: the Hollow, the Great Geode, Titan's grave. */
    public SetPieces setPieces() {
        return setPieces;
    }

    /** M7d: the underground water table. */
    public Waters waters() {
        return waters;
    }

    /** M7c: mega-caverns (and M7d: their lakes and seas). */
    public MegaCaverns megaCaverns() {
        return megaCaverns;
    }

    /** M7b: cave networks, entrances and worm bores; M7c: rifts. */
    public Passages passages() {
        return passages;
    }

    public Caves caves() {
        return grandCaves;
    }

    public Drainage drainage() {
        return drainage;
    }

    public Features features() {
        return features;
    }

    public Macro macro(String id) {
        return macros.kinds().stream().filter(m -> m.id().equals(id)).findFirst().orElse(null);
    }

    /**
     * A macro's sub-mosaic (design §6.2): finer cells, its family of landforms only, and a
     * context that sees the macro's climate offsets and is always inland. One mosaic per kind
     * serves every site of that kind; sites are far apart, so they draw from unrelated parts of it.
     */
    private ProvinceMosaic subMosaic(Macro kind) {
        ProvinceMosaic.Context context = (x, z) -> {
            WorldField.Point p = world.at(x, z);
            MacroField.Hit hit = new MacroField.Hit();
            if (macros.query(x, z, hit) && hit.site.kind() == kind) {
                Frame f = kind.frame(hit, p);
                return p.inland(f.tempOffset(), f.humidOffset());
            }
            return p.inland(0, 0);
        };
        Landform fallback = landforms.stream().filter(l -> kind.familyWeight(l, null) > 0)
                .findFirst().orElse(landforms.get(0));
        // The desert landforms keep to warm country inside a macro too: a cold basin took dune seas
        // as readily as a hot one, sand beside the snow.
        ProvinceMosaic.Weigher weigher = (l, c) -> kind.familyWeight(l, c) * warmOnly(l, c);
        return new ProvinceMosaic(GradientNoise.fork(seed, 70 + macros.kinds().indexOf(kind)), kind.subCell(),
                context, weigher, landforms, ocean, fallback);
    }

    /** 1, or for the sand and badlands landforms, how warm the country is for them (0 in the cold). */
    private static double warmOnly(Landform l, WorldField.Point c) {
        return switch (l.id()) {
            case "dune_sea" -> smoothstep(0.15, 0.4, c.temperature());
            case "rock_desert" -> smoothstep(0.1, 0.35, c.temperature());
            case "canyon_plateau" -> smoothstep(0.0, 0.25, c.temperature());
            default -> 1;
        };
    }

    /** A macro's own terrain before its landmarks, with how far inside its edge the point is. */
    private boolean innerTerrain(Macro kind, double x, double z, double[] out) {
        Scratch t = traceScratch.get();
        if (!macros.query(x, z, t.hit) || t.hit.site.kind() != kind) return false;
        Frame inner = kind.frame(t.hit, world.at(x, z));
        t.reset(ocean);
        blend(subMosaics.get(kind), x, z, inner, t);
        out[0] = inner.datum() + t.relief;
        out[1] = t.hit.inside;
        return true;
    }

    /** Cached column lookup; use this from hot paths. */
    public ColumnSample column(int x, int z) {
        return cache.get().get(this, x, z);
    }

    /** Uncached column computation. */
    public ColumnSample sample(int x, int z) {
        return sample((double) x, (double) z);
    }

    /** Uncached column computation at any point (the self-checks probe between blocks). */
    public ColumnSample sample(double x, double z) {
        return sample(x, z, true);
    }

    /**
     * The terrain before rivers: what the drainage network routes over and measures its banks
     * against. Uncached.
     */
    public ColumnSample sampleBase(double x, double z) {
        return sample(x, z, false);
    }

    private ColumnSample sample(double x, double z, boolean rivers) {
        WorldField.Point wp = world.at(x, z);
        double cont = wp.continentalness();
        Scratch s = scratch.get();
        MacroField.Hit hit = s.hit;
        boolean inMacro = macros.query(x, z, hit);
        MacroSite macroSite = inMacro ? hit.site : null; // the scratch hit can be reused by nested samples below
        boolean covered = inMacro && hit.site.kind().covers(hit);

        // Ordinary terrain: the world frame and the main mosaic, finished by coast and ocean.
        Frame frame = Frame.world(wp.datum(), SEA_LEVEL);
        double height = 0, relief = 0, rough = 0, rock = 0, rockInside = 0, rockSize = 0;
        double coastEnvelope = Double.NaN;
        Shore shore = null;
        s.reset(ocean);
        if (!covered) {
            boolean coastal = Math.abs(cont) < COAST_BAND;
            if (coastal) {
                // The land's character (relief, roughness) comes from a little way inland, so the
                // coast's cliffs and hills carry on smoothly out over what is sea on the plain field.
                shoreLine(x, z, cont, s.coast);
                double back = Math.max(0, COAST_ANCHOR - s.coast.sd);
                blend(mosaic, x + s.coast.nx * back, z + s.coast.nz * back, frame, s);
            } else if (cont > 0) {
                blend(mosaic, x, z, frame, s);
            }
            // A volcanic chain's cones (see WorldField.oceanIslands) stand over their islands' own relief.
            double cone = world.volcano(x, z);
            if (cone > 0) {
                s.relief += cone;
                s.roughness = Math.max(s.roughness, 0.15);
            }
            // Coast (design §8): relief survives to within ~150 blocks of the sea, so hills and
            // ranges can meet it as slopes or cliffs. Only a narrow strip at the waterline is
            // pulled down to beach height. (The first version faded relief and flattened
            // everything over a band hundreds of blocks wide: a featureless shelf with a sand stripe.)
            double reliefMask, plainMask = smoothstep(0, 0.03, cont);
            double plainRelief = s.relief * plainMask + microRelief(x, z) * smoothstep(0, 0.02, cont) * s.micro;
            double plainHeight = lerp(smoothstep(-0.025, 0.006, cont), world.oceanFloor(-cont, x, z),
                    lerp(smoothstep(0, 0.012, cont), SEA_LEVEL + 1, frame.datum() + plainRelief));
            if (coastal) {
                // Near the sea: the coast model (beaches, cliffs, rocky shores, bays and headlands).
                Coast cs = s.coast;
                coast(x, z, frame.datum(), s.relief, microRelief(x, z) * s.micro, wp.humidity(), cs);
                if (cs.height < SEA_LEVEL - 1 && cs.sdp < 0) {
                    s.landform = ocean; // under the sea it is sea, whatever the land behind is
                    s.zone = Zone.OCEAN;
                    s.transition = Transition.INTERIOR;
                    s.cellX = 0;
                    s.cellZ = 0;
                }
                height = cs.height;
                coastEnvelope = cs.envelope;
                // The grid blends each column from samples 4 blocks apart, so next to a sheer face the
                // blend reaches past where the face's envelope ends; nothing there would trim it back
                // and thin ridges were left standing in the sea (the user's "ruins this coast line").
                // A margin round the face with the envelope just over the ground has every column cut exact.
                if (cs.cliff > 0.02 && cs.sdp > cs.edge - 16 && cs.sdp < cs.edge + 6) {
                    coastEnvelope = Math.max(Double.isNaN(coastEnvelope) ? height : coastEnvelope, height + 0.6);
                }
                // Sea stacks and arches off hard coasts, sea caves in cliffs.
                CoastFeature feature = null;
                boolean onStack = false, sheer = false;
                // Karst towers reach 400 out plus their radius. Whether one stands is its cell's call,
                // so every warm column looks (a column's own karst test cut towers in half at the field's edge).
                if (Math.abs(cs.sdp) < (wp.temperature() > 0.25 ? 480 : 90)) {
                    int si = Math.floorDiv((int) Math.floor(x), STACK_CELL), sj = Math.floorDiv((int) Math.floor(z), STACK_CELL);
                    for (int dj = -1; dj <= 1; dj++) {
                        for (int di = -1; di <= 1; di++) {
                            CoastFeature st = stack(si + di, sj + dj);
                            if (st == null) continue;
                            double o = st.outline(x, z);
                            double out = (o - 1) * st.radius(); // roughly blocks out from its side
                            if (out > 12) continue;
                            // The grid keeps a stack's sides sheer only if it interpolates its full height
                            // round it, with a margin past that where every column is cut exact.
                            coastEnvelope = Math.max(Double.isNaN(coastEnvelope) ? height : coastEnvelope,
                                    out < 4 ? st.top() : height + 0.6);
                            if (Math.abs(o - 1) < 0.25) sheer = true;
                            if (o < 1) {
                                double top = st.kind() == CoastFeature.Kind.KARST
                                        ? st.top() - 0.22 * (st.top() - SEA_LEVEL) * Math.pow(o, 3) + 2.5 * cn(27, x, z, 6) // a rounded crown
                                        : st.top() - 1.5 * Math.pow(o, 8) + 0.4 * cn(26, x, z, 5);
                                if (top > height) {
                                    height = top;
                                    onStack = true;
                                }
                                feature = st;
                            }
                        }
                    }
                    if (feature == null) {
                        int ci = Math.floorDiv((int) Math.floor(x), CAVE_CELL), cj = Math.floorDiv((int) Math.floor(z), CAVE_CELL);
                        for (int dj = -1; dj <= 1 && feature == null; dj++) {
                            for (int di = -1; di <= 1 && feature == null; di++) {
                                CoastFeature cv = seaCave(ci + di, cj + dj);
                                if (cv == null) continue;
                                double dx = x - cv.x(), dz = z - cv.z();
                                double u = dx * cv.ax() + dz * cv.az(), v = -dx * cv.az() + dz * cv.ax();
                                if (u > -4 && u < cv.depth() + 1 && Math.abs(v) < cv.radius() + 4) feature = cv;
                            }
                        }
                    }
                }
                shore = new Shore(cs.kind, cs.sdp, cs.edge, cs.beachEdge, cs.cliff, cs.pebbles, feature, onStack, sheer);
                reliefMask = cs.mask;
                relief = s.relief * reliefMask;
                // The band's outer edge hands back to the plain field, whatever the coast did.
                double out = smoothstep(0.75 * COAST_BAND, COAST_BAND, Math.abs(cont));
                if (out > 0) {
                    height = lerp(out, height, plainHeight);
                    if (!Double.isNaN(coastEnvelope)) coastEnvelope = lerp(out, coastEnvelope, plainHeight);
                    reliefMask = lerp(out, reliefMask, plainMask);
                    relief = lerp(out, relief, plainRelief);
                }
            } else {
                reliefMask = plainMask;
                relief = plainRelief;
                height = plainHeight;
            }
            rough = s.roughness * reliefMask;
            rock = s.rock * reliefMask;
            rockInside = s.rockInside;
            rockSize = s.rockSize;
        }
        Landform landform = s.landform;
        Zone zone = s.zone;
        Transition transition = s.transition;
        int cellX = s.cellX, cellZ = s.cellZ;
        double t = wp.temperature(), w = wp.humidity();
        Macro macro = null;
        MacroZone macroZone = MacroZone.NONE;
        double inside = 0, water = Double.NaN;
        Wall wall = null;

        if (inMacro) {
            // The macro's own terrain: its sub-mosaic in its frame, then its edge decides how
            // that meets the ordinary terrain (design §6.2-6.3).
            Macro kind = hit.site.kind();
            Frame inner = kind.frame(hit, wp);
            s.reset(ocean);
            blend(subMosaics.get(kind), x, z, inner, s);
            Macro.Landmarks marks = s.landmarks;
            marks.reset(inner.datum() + s.relief, inner.datum());
            kind.landmarks(hit, x, z, innerTerrains.get(kind), marks);
            double innerH = marks.height;
            Macro.Combined combined = s.combined;
            combined.wall = null;
            kind.combine(hit, covered ? innerH : height, innerH, x, z, combined);
            wall = combined.wall;
            inside = combined.inside;
            height = combined.height;
            rough = Math.min(2, lerp(inside, rough, s.roughness) + combined.roughness + inside * marks.roughness);
            if (inside > 0.97 && (!(kind instanceof GreatPlateau) || !rivers || plateauLakeClear(hit.site))) {
                water = marks.water; // up to the top of the edge, where a river pours off
            }
            t = clamp(t + inside * inner.tempOffset(), -1, 1);
            w = clamp(w + inside * inner.humidOffset(), -1, 1);
            macro = kind;
            macroZone = combined.zone;
            if (marks.zone != null && inside >= 0.5) {
                macroZone = marks.zone;
            }
            rock = lerp(inside, rock, s.rock);
            if (inside >= 0.5) {
                rockInside = s.rockInside;
                rockSize = s.rockSize;
                landform = s.landform;
                zone = s.zone;
                transition = s.transition;
                cellX = s.cellX;
                cellZ = s.cellZ;
                frame = inner;
                relief = s.relief;
            }
        }
        height = clamp(capPeaks(height), TERRAIN_FLOOR + 16, PEAK_CAP);

        // Meso sites and what stands up in them (hoodoos, cinder cones; docs/MICRO_FEATURES.md).
        // With rivers only: the drainage and the sites themselves are judged on the ground without them.
        MesoSite site = null;
        MicroFeature feature = null;
        boolean microWater = false;
        double envelope = Double.isNaN(coastEnvelope) ? height : Math.max(height, coastEnvelope);
        Features.Column fc = s.features;
        if (rivers && cont > 0) {
            features.shapes(x, z, height, fc);
            site = fc.site;
            feature = fc.micro;
            height = fc.height;
            envelope = fc.envelope;
            if (fc.calm) {
                rough = 0;
                rock = 0;
            }
            envelope = Math.max(envelope, Double.isNaN(coastEnvelope) ? fc.envelope : Math.max(fc.envelope, coastEnvelope));
        }

        // Rivers and lakes (design §8): the drainage network cuts the finished ground down to its
        // channels and valleys. Last, so a river can start on a plateau and fall off its edge. The
        // network samples the terrain before rivers to route itself, and may do so from in here;
        // nothing below reads this thread's scratch, so that's safe.
        Zone wet = null;
        double uncut = Math.max(height, envelope);
        boolean nearWater = !Double.isNaN(water), spill = false;
        double flowX = 0, flowZ = 0;
        // Rivers wherever there's land, not only by the continent field: a plateau standing in its
        // oceanic part had its rivers cut off along a contour (a sheer 20-block wall where the
        // valley stopped, the river ending in it).
        if (rivers && (cont > -0.12 || height > SEA_LEVEL - 4)) {
            Drainage.Carve carve = s.carve;
            // Out past the shore a river's channel runs on as a groove in the sea floor, but its
            // water is the sea's: no river biome or current out there.
            boolean atSea = height < SEA_LEVEL - 1 && (cont < 0 || shore != null && shore.distance() < 0);
            double before = height;
            height = drainage.carve(x, z, height, carve);
            // Water the macro set (a plateau's lake or river) can't stand over a valley cut under it.
            if (!Double.isNaN(water) && (height < before - 1.5 || carve.near)) water = Double.NaN;
            if (carve.near) {
                rough = 0; // 3D roughness would hole the banks and let the water out
                rock = 0;
                nearWater = true;
            }
            if (carve.water > height && !(water >= carve.water) && !atSea) {
                water = carve.water;
                wet = carve.lake ? Zone.LAKE : carve.fjord ? Zone.OCEAN : Zone.RIVER;
                flowX = carve.flowX;
                flowZ = carve.flowZ;
                spill = carve.spill;
            }
        }
        if (site != null) {
            // What is cut into the ground (ponds, sinkholes, cenotes), after the rivers it keeps clear of.
            features.hollows(x, z, height, fc);
            height = fc.height;
            if (fc.micro != null) feature = fc.micro;
            if (Double.isNaN(water) && fc.water > height) {
                water = fc.water;
                microWater = true;
            }
            if (fc.near) {
                nearWater = true;
                rough = 0;
                rock = 0;
            }
        }
        if (rivers && macroSite != null && macroSite.kind() instanceof GreatPlateau) {
            // The plunge pool where the plateau's river lands after its fall (user, 0.1.2: "a
            // waterfall at the plateau but no lake under it").
            double[] pool = plungePool(macroSite);
            if (pool != null) {
                double d = Math.hypot(x - pool[0], z - pool[1]), level = pool[3];
                double r = pool[2] * (1 + 0.15 * GradientNoise.noise2(micro ^ 0x3C1L, x / 8.0, z / 8.0)); // not a circle
                if (d < r * 1.45 && inside < 0.95) { // never into the tableland: its face stays sheer, the pool comes right up to it
                    double lower = d < r ? level + 1.2 - (pool[4] + 1.2) * smoothstep(0, 0.6, 1 - d / r)
                            : lerp(smoothstep(r, r * 1.45, d), level + 1.2, Math.max(height, level + 1.2));
                    height = Math.min(height, lower);
                    if (d < r && height < level && Double.isNaN(water)) {
                        water = level;
                        microWater = true;
                    }
                    nearWater = true;
                    rough = 0;
                    rock = 0;
                }
            }
        }
        if (!(water > height)) {
            water = Double.NaN;
            wet = null;
            flowX = flowZ = 0;
            spill = false;
        }
        if (wet != null) {
            zone = wet;
        }

        // Mountain-ness is height over the world datum, so a plateau's lifted country counts too.
        double mountain = smoothstep(150, 520, height - wp.datum());
        double erosion = clamp(0.6 - 1.6 * mountain, -1, 1);
        // Kept out of vanilla's +-0.16 "river" band so the vanilla spawn finder accepts land.
        double weirdness = 0.3 + 0.6 * Math.abs(Fractal.fbm(weird, x / 3000.0, z / 3000.0, 2));

        BiomeKind biome;
        boolean marshFlat = shore != null && shore.kind() == Shore.Kind.MARSH && height > SEA_LEVEL - 5;
        if (wet == Zone.OCEAN) {
            biome = oceanBiome(t, height, x, z); // a fjord: the sea, run up its valley
        } else if (height < SEA_LEVEL - 1 && (cont < 0.01 || shore != null && shore.distance() < 40) && wet == null && !marshFlat) {
            zone = Zone.OCEAN;
            biome = oceanBiome(t, height, x, z);
        } else if (shore != null && inside < 0.5 && wet == null && coastShore(shore, height)) {
            zone = Zone.COAST;
            biome = shore.stack() ? (shore.feature() != null && shore.feature().kind() == CoastFeature.Kind.KARST
                    ? (w > 0 ? BiomeKind.JUNGLE : BiomeKind.SPARSE_JUNGLE) : BiomeKind.STONY_SHORE) : switch (shore.kind()) {
                case BEACH -> t < -0.5 ? BiomeKind.SNOWY_BEACH : shore.pebbles() ? BiomeKind.PEBBLE_BEACH : BiomeKind.BEACH;
                case MARSH -> t > 0.45 ? BiomeKind.MANGROVE_SWAMP : t < -0.45 ? BiomeKind.SNOWY_PLAINS : BiomeKind.SALT_MARSH;
                default -> BiomeKind.STONY_SHORE;
            };
        } else if (shore == null && height < SEA_LEVEL + 3 && cont < 0.015 && inside < 0.5 && wet == null) {
            zone = Zone.COAST;
            biome = rough > 0.15 || relief > 40 ? BiomeKind.STONY_SHORE : t < -0.5 ? BiomeKind.SNOWY_BEACH : BiomeKind.BEACH;
        } else {
            // Inland ground below sea level is a pond in its landform's own biome, not a scrap of
            // ocean (which drew beached shipwrecks inland).
            double v = GradientNoise.noise2(variant, x / 1100.0, z / 1100.0);
            double altitude = height;
            if (macro != null && inside >= 0.5) {
                altitude -= macro.altitudeDiscount() * Math.max(0, frame.datum() - wp.datum());
            }
            // The biome table sees the climate roughened (the user: "less clean lines... crank up the
            // chaos while still grounding biomes"). Temperature and humidity bend over kilometres, so a
            // threshold on them ran dead straight for kilometres. Three scales of smooth noise make the
            // edges wander in lobes, push tongues into each other and leave the odd outlier patch. At
            // most ~0.16 in temperature: snow (under -0.5) and desert (over 0.55) still can't meet
            // without a height difference between them.
            double tb = t + 0.09 * cn(50, x, z, 900) + 0.05 * cn(51, x, z, 260) + 0.02 * cn(52, x, z, 70);
            double wb = w + 0.15 * cn(53, x, z, 900) + 0.08 * cn(54, x, z, 260) + 0.03 * cn(55, x, z, 70);
            biome = !Double.isNaN(water) && !microWater ? (t < -0.5 ? BiomeKind.FROZEN_RIVER : BiomeKind.RIVER)
                    : rock > 6 ? BiomeKind.BADLANDS // standing rock is banded sandstone whatever province it's in
                    : landform.biome(zone, tb, wb, altitude, v);
            double fromSite = site == null ? Double.MAX_VALUE : Math.hypot(x - site.x(), z - site.z());
            if (site != null && site.kind() == MesoKind.GLADE && Double.isNaN(water) && fromSite < 0.62 * site.radius()) {
                biome = BiomeKind.MEADOW; // the glade's clearing: open meadow, no trees
            } else if (site != null && (site.kind() == MesoKind.HOODOO_GARDEN && fromSite < 0.8 * site.radius()
                    || site.kind() == MesoKind.BUTTE_FIELD && fromSite < 0.85 * site.radius()) && Double.isNaN(water)) {
                biome = BiomeKind.BADLANDS; // bare eroded ground, and the pillars banded terracotta like Bryce Canyon
            } else if (site != null && (site.kind() == MesoKind.TARN_BASIN || site.kind() == MesoKind.ERRATIC_FIELD)
                    && Double.isNaN(water) && fromSite < 0.85 * site.radius()) {
                // Open ground, where its lakes and boulders can be seen: alpine meadow round tarns,
                // open plain under erratics, snowy where it's cold.
                biome = t < -0.45 ? BiomeKind.SNOWY_PLAINS : site.kind() == MesoKind.TARN_BASIN ? BiomeKind.MEADOW : BiomeKind.PLAINS;
            } else if (site != null && Double.isNaN(water) && fromSite < 0.85 * site.radius() && forestBiome(site.kind(), site.biome(), t) != null) {
                biome = forestBiome(site.kind(), site.biome(), t);
            } else if (microWater && site != null && site.kind() == MesoKind.CALDERA) {
                biome = t < -0.8 ? BiomeKind.FROZEN_RIVER : BiomeKind.CRATER_LAKE; // deep: it only freezes in the hardest cold
            } else if (microWater && feature != null && (feature.kind == MicroKind.CENOTE || feature.kind == MicroKind.GROTTO || feature.kind == MicroKind.TRAVERTINE)) {
                biome = BiomeKind.WARM_OCEAN; // for the water's colour: a cenote's clear turquoise
            }
        }
        return new ColumnSample(height, frame.datum(), relief, cont, t, w, mountain, erosion, weirdness, rough,
                macro, macroZone, inside, landform, zone, transition, cellX, cellZ, biome,
                Math.max(0, rock), rockInside, rockSize, water, nearWater, flowX, flowZ, spill, uncut, site, feature, wall, shore);
    }

    private final java.util.concurrent.ConcurrentHashMap<Long, Object> plungePools = new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * A Great Plateau's plunge pool: {x, z, radius, level, depth} at the foot of the cliff its river
     * pours off, or null (no river, or a drainage river already runs there and takes the fall).
     * Found once per plateau by walking out from the crest to where the ground stops falling; the
     * level is a block under the lowest ground round it, after rivers have cut it, so it holds.
     */
    private final java.util.concurrent.ConcurrentHashMap<Long, Boolean> plateauLakes = new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * A Great Plateau keeps its lake only where no drainage river comes near it: one that did cut
     * the lake's shore down to its own, lower level and stood the lake's water over it. Without
     * the water the bowl is a hollow the network may fill as a lake of its own. Checked once per
     * plateau, on a ring round the lake's farthest reach.
     */
    private boolean plateauLakeClear(MacroSite site) {
        long key = ((long) site.cellX() << 32) ^ (site.cellZ() & 0xFFFFFFFFL);
        Boolean ok = plateauLakes.get(key);
        if (ok != null) return ok;
        double[] lake = ((GreatPlateau) site.kind()).lake(site, innerTerrains.get(site.kind()));
        Drainage.Carve carve = new Drainage.Carve();
        boolean clear = true;
        for (double ring : new double[] {0.6, 1.0, 1.25}) {
            for (int k = 0; k < 32 && clear; k++) {
                double a = k * Math.PI / 16, r = lake[2] * ring + 16;
                double px = lake[0] + r * Math.cos(a), pz = lake[1] + r * Math.sin(a);
                drainage.carve(px, pz, sampleBase(px, pz).height(), carve);
                clear = !carve.near && Double.isNaN(carve.water);
            }
        }
        plateauLakes.putIfAbsent(key, clear);
        return clear;
    }

    private double[] plungePool(MacroSite site) {
        long key = ((long) site.cellX() << 32) ^ (site.cellZ() & 0xFFFFFFFFL);
        Object o = plungePools.get(key);
        if (o == null) {
            double[] pool = findPlungePool(site);
            o = pool == null ? Boolean.FALSE : pool;
            Object had = plungePools.putIfAbsent(key, o);
            if (had != null) o = had;
        }
        return o instanceof double[] pool ? pool : null;
    }

    private double[] findPlungePool(MacroSite site) {
        double[] fall = ((GreatPlateau) site.kind()).waterfall(site, innerTerrains.get(site.kind()));
        if (fall == null) return null;
        double x = fall[0], z = fall[1], ux = fall[2], uz = fall[3];
        // The face: the first point out from the crest that is off the plateau. The fall drops
        // just past it, so the pool's edge sits against the face.
        double fx = Double.NaN, fz = Double.NaN;
        for (double t = 1; t < 300; t += 1) {
            double px = x + ux * t, pz = z + uz * t;
            if (sampleBase(px, pz).macroInside() < 0.5) {
                fx = px;
                fz = pz;
                break;
            }
        }
        if (Double.isNaN(fx)) return null;
        double r = 12 + 1.6 * fall[4];
        double cx = fx + ux * (r - 4), cz = fz + uz * (r - 4);
        Drainage.Carve carve = new Drainage.Carve();
        drainage.carve(cx, cz, sampleBase(cx, cz).height(), carve);
        if (carve.near || !Double.isNaN(carve.water)) return null; // a river is there already
        double low = Double.MAX_VALUE;
        for (int k = 0; k < 24; k++) {
            double a = k * Math.PI / 12;
            for (double ring : new double[] {1.1, 1.35}) {
                double px = cx + Math.cos(a) * r * ring, pz = cz + Math.sin(a) * r * ring;
                low = Math.min(low, drainage.carve(px, pz, sampleBase(px, pz).height(), carve));
            }
        }
        return new double[] {cx, cz, r, low - 1, 5 + 0.1 * r};
    }

    /**
     * The biome a forest site gives its heart: old growth round ancient trees, dark forest over a
     * mushroom hollow, pale garden for a pale grove. Null where the site keeps the forest it's in.
     */
    private static BiomeKind forestBiome(MesoKind kind, BiomeKind around, double t) {
        return switch (kind) {
            case ANCIENT_GROVE -> switch (around) {
                case BIRCH_FOREST, OLD_GROWTH_BIRCH_FOREST -> BiomeKind.OLD_GROWTH_BIRCH_FOREST;
                case TAIGA, SNOWY_TAIGA, OLD_GROWTH_SPRUCE_TAIGA, OLD_GROWTH_PINE_TAIGA -> BiomeKind.OLD_GROWTH_PINE_TAIGA;
                default -> null;
            };
            case MUSHROOM_HOLLOW -> t > -0.2 ? BiomeKind.DARK_FOREST : null;
            case PALE_GROVE -> BiomeKind.PALE_GARDEN;
            default -> null;
        };
    }

    // ---- coasts (docs/M3_M4_PLAN.md, phase 1)

    /** Where the coast model runs: within this much continentalness of the shore (a few hundred blocks). */
    private static final double COAST_BAND = 0.12;
    /** Blocks inland of the shore where the land's own character is read for the coast. */
    private static final double COAST_ANCHOR = 40;

    /** One column's coast. */
    static final class Coast {
        double g, sd, nx, nz, near;
        double sdp, height, envelope, mask, beachEdge, edge, cliff, rocky, top;
        Shore.Kind kind;
        boolean pebbles;
    }

    /** Where (x, z) sits against the plain field's shore: blocks from it and the inland direction. */
    private void shoreLine(double x, double z, double cont, Coast out) {
        double gx = (world.at(x + 6, z).continentalness() - world.at(x - 6, z).continentalness()) / 12;
        double gz = (world.at(x, z + 6).continentalness() - world.at(x, z - 6).continentalness()) / 12;
        double g = Math.max(2e-5, Math.hypot(gx, gz));
        out.g = g;
        out.nx = gx / g;
        out.nz = gz / g;
        out.sd = cont / g;
        out.near = 1 - smoothstep(150, 450, Math.abs(out.sd));
    }

    private double cn(long salt, double x, double z, double scale) {
        return GradientNoise.noise2(coastNoise ^ salt, x / scale, z / scale);
    }

    /**
     * The coast at (x, z): a blend of four profiles across the shore. Which one a stretch of coast
     * gets is a soft contest between slow fields along it, weighted by the land behind (high land
     * meets the sea in cliffs, low wet land in marsh), so types hand over gradually: a cliff
     * shrinks into a beach, a beach breaks up into rocks.
     * <ul>
     *   <li><b>Beach:</b> a foreshore sloping gently into the sea, a berm, sand 12-38 blocks wide
     *       (or shingle on stony stretches), and behind a dry beach, dune ridges along the shore.</li>
     *   <li><b>Cliff:</b> the land lifts to a sea cliff 15-50 tall, its line ragged, exact to the
     *       block (the envelope lets the per-block cut keep it sheer), with a wave-cut platform just
     *       under the water at its foot, 6-24 wide, then the sea floor.</li>
     *   <li><b>Rocky shore:</b> broken, lumpy rock running into the water.</li>
     *   <li><b>Salt marsh:</b> flat grass a block over the sea, laced with winding tidal creeks and
     *       salt pans, fronted by wide tidal flats just under the water.</li>
     * </ul>
     * The shore line itself wanders: broad bays at three scales, and on hard (cliff and rocky) coasts,
     * narrow headlands pushing out to sea. Far from the shore every profile comes back to the plain
     * land and sea floor, so the band's edge is seamless.
     */
    private void coast(double x, double z, double datum, double relief, double micro, double humidity, Coast out) {
        double g = out.g, near = out.near;
        // The land's height over the sea (its frame's datum and its relief), brought down to the shore.
        double lift = datum - SEA_LEVEL - 1 + relief;
        double tall = Math.max(0, lift);

        // Which coast: a soft contest (scores 0.1 apart are about a 3:1 blend).
        double sBeach = 0.18;
        double sCliff = cn(4, x, z, 1400) + tall / 500 - 0.12;
        double sRocky = 0.8 * cn(5, x, z, 900) + 0.02;
        double sMarsh = cn(17, x, z, 1100) + humidity - 0.15 - tall / 120;
        double top4 = Math.max(Math.max(sBeach, sCliff), Math.max(sRocky, sMarsh));
        double eB = Math.exp(11 * (sBeach - top4)), eC = Math.exp(11 * (sCliff - top4));
        double eR = Math.exp(11 * (sRocky - top4)), eM = Math.exp(11 * (sMarsh - top4));
        double sum = eB + eC + eR + eM;
        double beachW = eB / sum, cliffW = eC / sum, rockyW = eR / sum, marshW = eM / sum;

        // The shore line: bays at three scales, and on hard coasts narrow headlands (ridges of a
        // noise: thin lines where it crosses zero, so they come out as fingers, with bays between).
        double hard = cliffW + rockyW;
        double bays = 85 * cn(1, x, z, 700) + 40 * cn(2, x, z, 230) + 12 * cn(3, x, z, 70) + 2 * cn(21, x, z, 24);
        double ridge = Math.max(-0.6, 1 - 3 * Math.abs(cn(22, x, z, 320)));
        // Only near the shore, or the ridges would run on under the sea as long thin reefs.
        double headlands = (12 + 38 * hard) * ridge * (1 - smoothstep(60, 220, Math.abs(out.sd)));
        double sdp = out.sd + near * (bays + headlands);
        double floor = world.oceanFloor(Math.max(0, -sdp * g), x, z);
        double seaFade = smoothstep(-130, -40, sdp); // every profile is back on the sea floor by 130 out

        // Beach. High land comes down to the sea over a distance that grows with its height (about
        // 35 degrees for a range), so a mountain coast is a steep slope over its shore, not a wall.
        double bw = 12 + 26 * (0.5 + 0.5 * cn(6, x, z, 300));
        double mB = smoothstep(0, 140 + 0.6 * tall, sdp);
        double inland = SEA_LEVEL + 1 + lift * mB + micro * smoothstep(0, 90, sdp);
        double dry = 1 - smoothstep(-0.2, 0.15, humidity);
        double hB;
        if (sdp >= 0) {
            double berm = SEA_LEVEL + 0.4 + Math.min(1.3, sdp * 0.3);
            hB = lerp(smoothstep(bw, bw + 60, sdp), berm, inland);
            if (dry > 0) {
                double band = smoothstep(bw - 2, bw + 8, sdp) * (1 - smoothstep(bw + 45, bw + 80, sdp));
                double crest = Math.pow(0.5 + 0.5 * Math.sin(sdp / 7.5 + 3 * cn(7, x, z, 45)), 2);
                hB += dry * band * (3 + 5 * (0.5 + 0.5 * cn(8, x, z, 120))) * crest;
            }
        } else {
            // Gently into the water: real sandy shores are 1-4 deep 100 out (ours were 7-13).
            double slope = 0.015 + 0.025 * (0.5 + 0.5 * cn(9, x, z, 400));
            hB = lerp(seaFade, floor, Math.max(floor, SEA_LEVEL + 0.4 + sdp * slope));
        }

        // Cliff.
        double mC = smoothstep(-6, 22 + 1.3 * tall, sdp);
        double top = Math.max(SEA_LEVEL + 1 + lift * mC + micro,
                SEA_LEVEL + 14 + 34 * (0.5 + 0.5 * cn(10, x, z, 500)) + 8 * cn(30, x, z, 110));
        top = lerp(smoothstep(80, 220, sdp), top, inland); // the coastal rise fades inland
        double pw = 6 + 18 * (0.5 + 0.5 * cn(11, x, z, 260));
        double platform = SEA_LEVEL - 2.3 + 0.6 * cn(12, x, z, 6); // under the water, not a stone plain at its surface
        // A ragged line: buttresses and gullies every few dozen blocks, broken up close to.
        double edge = 4 * cn(13, x, z, 40) + 1.5 * cn(14, x, z, 12) + 0.4 * cn(28, x, z, 4);
        double hC, envC = Double.NaN;
        if (sdp >= edge) {
            hC = top;
        } else if (sdp >= -pw) {
            // Rubble fallen at the foot, then the bare platform.
            double rubble = (2.5 * Math.max(0, cn(29, x, z, 5)) + 0.8) * (1 - smoothstep(0, 7, edge - sdp));
            hC = platform + rubble;
            if (sdp > edge - 5) envC = top; // so the 4-block grid keeps the face sheer
        } else {
            hC = lerp(seaFade, floor, Math.max(floor, platform + (sdp + pw) * 0.35));
        }

        // Rocky shore.
        double mR = smoothstep(0, 70 + 0.9 * tall, sdp);
        double lumps = 3.5 * Math.max(0, cn(15, x, z, 10)) + 1.6 * Math.max(0, cn(16, x, z, 4)) + 0.6 * cn(31, x, z, 2.5) - 0.4;
        double hR = sdp >= 0
                ? lerp(smoothstep(30, 110, sdp), Math.max(SEA_LEVEL + 0.8, SEA_LEVEL + 1 + lift * mR + micro) + lumps * (1 - smoothstep(10, 30, sdp)), inland)
                : lerp(seaFade, floor, Math.max(floor, SEA_LEVEL + 0.8 + sdp * 0.22 + lumps));

        // Salt marsh: creeks follow the zero lines of a noise, widest at the sea and dying out inland.
        double mM = smoothstep(120, 320 + 0.6 * tall, sdp);
        double hM;
        if (sdp >= 0) {
            double flat = SEA_LEVEL + 0.35 + 1.1 * smoothstep(0, 220, sdp) + 0.35 * cn(18, x, z, 14);
            double creekW = 0.07 * (1 - smoothstep(40, 200, sdp)) + 0.012;
            double creek = Math.abs(cn(19, x, z, 85) + 0.3 * cn(20, x, z, 24));
            if (creek < creekW) flat = Math.min(flat, SEA_LEVEL - 0.6 - 1.4 * (1 - creek / creekW));
            if (cn(23, x, z, 22) > 0.5 && sdp > 25) flat = Math.min(flat, SEA_LEVEL - 0.4); // a salt pan
            hM = lerp(smoothstep(160, 320, sdp), flat, SEA_LEVEL + 1 + lift * mM + micro * smoothstep(120, 260, sdp));
        } else {
            // Tidal flats: a block or two of water over mud for a long way out.
            hM = lerp(seaFade, floor, Math.max(floor, SEA_LEVEL - 1.4 + sdp * 0.012 + 0.3 * cn(24, x, z, 9)));
        }

        out.sdp = sdp;
        out.height = beachW * hB + cliffW * hC + rockyW * hR + marshW * hM;
        // Barrier islands (docs/M3_M4_PLAN.md, phase 3): off low sandy coasts in places, a long island
        // of sand and dunes runs along the shore 60-130 out, broken by tidal inlets, with a shallow
        // lagoon behind it. Shaped after the coast types blend, or the blend drowned it in pieces.
        double barrier = smoothstep(0.0, 0.1, cn(32, x, z, 1800)) * (1 - smoothstep(50, 110, tall)) * smoothstep(0.3, 0.55, beachW);
        if (barrier > 0 && sdp < 0) {
            double off = 60 + 70 * (0.5 + 0.5 * cn(33, x, z, 900));
            double w = 9 + 7 * (0.5 + 0.5 * cn(34, x, z, 300));
            double across = Math.abs(sdp + off) / w;
            double inlet = smoothstep(-0.55, -0.4, cn(35, x, z, 220)); // gaps where the tide runs through
            double lagoon = smoothstep(-off + w, -off + w + 6, sdp) * (1 - smoothstep(-14, -4, sdp));
            double shaped = out.height;
            if (lagoon > 0) shaped = Math.min(shaped, lerp(lagoon, shaped, SEA_LEVEL - 2.5 + 0.8 * cn(37, x, z, 30)));
            if (across < 1) {
                double island = SEA_LEVEL + 0.8 + 2.2 * (1 - across * across) + 2.5 * Math.max(0, cn(36, x, z, 25));
                shaped = Math.max(shaped, lerp(inlet, island, shaped));
            } else if (across < 2) {
                shaped = Math.max(shaped, lerp(inlet, lerp(across - 1, SEA_LEVEL - 1, shaped), shaped)); // its shoreface
            }
            out.height = lerp(barrier, out.height, shaped);
        }
        // Sheer only where the cliff dominates; where it is fading out the grid smooths its step into a bank.
        out.envelope = Double.isNaN(envC) || cliffW < 0.35 ? Double.NaN : out.height + cliffW * (envC - hC);
        out.cliff = cliffW;
        out.rocky = rockyW;
        out.top = top;
        out.mask = beachW * mB + cliffW * mC + rockyW * mR + marshW * mM;
        out.beachEdge = bw + (dry > 0.3 ? 75 : 5); // how far the sand runs: to the berm, or over the dunes
        out.edge = edge;
        double best = Math.max(Math.max(beachW, cliffW), Math.max(rockyW, marshW));
        out.kind = best == cliffW ? Shore.Kind.CLIFF : best == rockyW ? Shore.Kind.ROCKY : best == marshW ? Shore.Kind.MARSH : Shore.Kind.BEACH;
        out.pebbles = cn(25, x, z, 600) + 1.5 * rockyW > 0.45; // shingle where the coast is stony
    }

    private static final int STACK_CELL = 56, CAVE_CELL = 96;
    private static final Object NO_FEATURE = new Object();
    private final java.util.concurrent.ConcurrentHashMap<Long, Object> stacks = new java.util.concurrent.ConcurrentHashMap<>();
    private final java.util.concurrent.ConcurrentHashMap<Long, Object> seaCaves = new java.util.concurrent.ConcurrentHashMap<>();
    private final ThreadLocal<Scratch> coastScratch = ThreadLocal.withInitial(Scratch::new);

    /** The coast at a point, as the column there sees it (but none inside a macro's reach). */
    private boolean coastProbe(double x, double z, Coast out) {
        WorldField.Point wp = world.at(x, z);
        double cont = wp.continentalness();
        if (Math.abs(cont) >= 0.75 * COAST_BAND) return false;
        Scratch s = coastScratch.get();
        if (macros.query(x, z, s.hit)) return false;
        s.reset(ocean);
        shoreLine(x, z, cont, out);
        double back = Math.max(0, COAST_ANCHOR - out.sd);
        Frame frame = Frame.world(wp.datum(), SEA_LEVEL);
        blend(mosaic, x + out.nx * back, z + out.nz * back, frame, s);
        coast(x, z, frame.datum(), s.relief, microRelief(x, z) * s.micro, wp.humidity(), out);
        return true;
    }

    private static double unit(long h, int k) {
        return (GradientNoise.mix(h + k * 0x9E3779B97F4A7C15L) >>> 11) * 0x1.0p-53;
    }

    /**
     * The sea stack of one grid cell, if any: a pillar left standing off a hard coast, 5-40 blocks
     * out. Off cliffs they are tall (half to all of the cliff's height) and some are arches, a
     * thin fin with a tunnel through it at the waterline; off rocky shores they are small.
     */
    private CoastFeature stack(int i, int j) {
        long key = ((long) i << 32) ^ (j & 0xFFFFFFFFL);
        Object v = stacks.get(key);
        if (v == null) {
            v = makeStack(i, j);
            stacks.putIfAbsent(key, v);
        }
        return v == NO_FEATURE ? null : (CoastFeature) v;
    }

    /**
     * Ha Long Bay (docs/M3_M4_PLAN.md, phase 6): on warm coasts, a slow field marks bays full of
     * karst towers rising out of the water.
     */
    private boolean karst(double x, double z, double temperature) {
        return temperature > 0.35 && cn(31, x, z, 2500) > 0.2;
    }

    private Object makeStack(int i, int j) {
        long h = GradientNoise.hash(coastNoise ^ 0x57ACL, i, j);
        double px = (i + 0.15 + 0.7 * unit(h, 1)) * STACK_CELL, pz = (j + 0.15 + 0.7 * unit(h, 2)) * STACK_CELL;
        if (karst(px, pz, world.at(px, pz).temperature())) {
            // A karst tower: tall, round-crowned, sheer, crowded across the bay.
            if (unit(h, 0) > 0.85) return NO_FEATURE;
            // In the water, out across the whole bay (as far as the coast model reaches).
            Coast c = new Coast();
            if (!coastProbe(px, pz, c) || c.sdp > -6 || c.sdp < -400) return NO_FEATURE;
            double tall = 25 + 55 * Math.pow(unit(h, 3), 1.3), radius = 7 + 11 * unit(h, 4);
            double angle = Math.PI * 2 * unit(h, 6);
            return new CoastFeature(CoastFeature.Kind.KARST, px, pz, Math.cos(angle), Math.sin(angle), radius,
                    SEA_LEVEL + tall, radius * (1 + 0.35 * unit(h, 5)), 0, 0, GradientNoise.mix(h + 9));
        }
        if (unit(h, 0) > 0.42) return NO_FEATURE;
        Coast c = new Coast();
        if (!coastProbe(px, pz, c) || c.cliff + c.rocky < 0.55 || c.sdp > -5 || c.sdp < -40) return NO_FEATURE;
        boolean big = c.cliff > c.rocky;
        double tall = big ? (0.45 + 0.5 * unit(h, 3)) * (c.top - SEA_LEVEL) : 5 + 9 * unit(h, 3);
        if (tall < 5) return NO_FEATURE;
        double radius = big ? 3 + 4 * unit(h, 4) : 2.5 + 2.5 * unit(h, 4);
        double length = radius * (1.1 + 0.5 * unit(h, 5));
        double angle = Math.atan2(c.nz, c.nx) + Math.PI * 2 * unit(h, 6);
        double arch = 0, archTop = 0;
        if (big && tall >= 14 && unit(h, 7) < 0.35) {
            // An arch: a fin along the shore, so the tunnel through it looks out to sea.
            radius = 2.5 + 1.5 * unit(h, 4);
            length = 7 + 6 * unit(h, 5);
            angle = Math.atan2(c.nx, -c.nz) + (unit(h, 6) - 0.5) * 0.6;
            arch = Math.min(length - 2.5, 2.5 + 0.3 * length);
            archTop = CoastFeature.FLOOR + (0.4 + 0.2 * unit(h, 8)) * (tall + 2.5);
        }
        return new CoastFeature(CoastFeature.Kind.STACK, px, pz, Math.cos(angle), Math.sin(angle), radius,
                SEA_LEVEL + tall, length, arch, archTop, GradientNoise.mix(h + 9));
    }

    /** The sea cave of one grid cell, if any: a tunnel at the waterline into a cliff 12+ blocks tall. */
    private CoastFeature seaCave(int i, int j) {
        long key = ((long) i << 32) ^ (j & 0xFFFFFFFFL);
        Object v = seaCaves.get(key);
        if (v == null) {
            v = makeSeaCave(i, j);
            seaCaves.putIfAbsent(key, v);
        }
        return v == NO_FEATURE ? null : (CoastFeature) v;
    }

    private Object makeSeaCave(int i, int j) {
        long h = GradientNoise.hash(coastNoise ^ 0xCA7EL, i, j);
        if (unit(h, 0) > 0.45) return NO_FEATURE;
        double px = (i + 0.15 + 0.7 * unit(h, 1)) * CAVE_CELL, pz = (j + 0.15 + 0.7 * unit(h, 2)) * CAVE_CELL;
        Coast c = new Coast();
        // Walk onto the cliff's face (distances are blocks, so a step or two lands on it).
        for (int k = 0; k < 3; k++) {
            if (!coastProbe(px, pz, c) || c.cliff < 0.6) return NO_FEATURE;
            double move = c.edge - c.sdp;
            if (Math.abs(move) > 60) return NO_FEATURE;
            if (Math.abs(move) < 1.5) break;
            px += c.nx * move;
            pz += c.nz * move;
        }
        if (Math.abs(c.edge - c.sdp) > 2.5 || c.top - SEA_LEVEL < 12) return NO_FEATURE;
        double half = 2.5 + 2.5 * unit(h, 3);
        double roof = Math.min(CoastFeature.FLOOR + 6 + 6 * unit(h, 4), c.top - 5);
        double depth = 10 + 16 * unit(h, 5);
        return new CoastFeature(CoastFeature.Kind.CAVE, px, pz, c.nx, c.nz, half, roof, depth, 0, 0, GradientNoise.mix(h + 9));
    }

    /** True where a coast's shore biome goes: the beach and its dunes, a cliff's platform, a rocky shore, the marsh. */
    private static boolean coastShore(Shore s, double height) {
        // Anything standing out past the shore (rocks, rubble, a stack, a little islet) is shore, never
        // the land's forest: trees were growing out of the shallows.
        if (s.stack() || s.distance() < 0) return true;
        return switch (s.kind()) {
            case BEACH -> s.distance() < s.beachWidth() && height < SEA_LEVEL + 14;
            case CLIFF -> s.distance() < s.edge() && height < SEA_LEVEL + 6;
            case ROCKY -> s.distance() < 10 && height < SEA_LEVEL + 7;
            case MARSH -> s.distance() < 240 && height < SEA_LEVEL + 4;
        };
    }

    /**
     * Small-scale ground texture for all land: a few blocks of undulation over ~70–200 blocks, so
     * gentle slopes don't render as perfectly even contour steps.
     */
    private double microRelief(double x, double z) {
        return 4 * Fractal.fbm(micro, x / 160.0, z / 160.0, 3) + 1.5 * GradientNoise.noise2(micro ^ 0x99L, x / 45.0, z / 45.0);
    }

    /**
     * Summits approach {@link #PEAK_CAP} smoothly instead of being cut flat: above 900 the
     * remaining room is shared out exponentially (value and slope continuous at 900). Landforms
     * aim their summits below this (megaranges cap their amplitude), because the compression
     * rounds whatever passes through it.
     */
    static double capPeaks(double h) {
        return h <= 900 ? h : PEAK_CAP - (PEAK_CAP - 900) * Math.exp(-(h - 900) / (PEAK_CAP - 900));
    }

    /**
     * The preliminary surface level vanilla's aquifers and surface rules expect: a conservative
     * estimate <i>below</i> the real ground. Vanilla's own sits a few blocks under the lowest
     * nearby surface, snapped down to its 8-block cells. Aquifers add 8 to it and treat that as
     * where the ground is. Handing them the exact height put local water levels up to 8 blocks
     * above the ground, so cave mouths and dips near the surface filled to the brim and left water
     * hanging in walls.
     *
     * <p>The same value feeds the surface rules (as {@code chunk_surface_level}), which only paint
     * blocks above it. Vanilla interpolates that across 16-block cells, which on a 300-block canyon
     * wall overshot the real ground and left bare stone where banded terracotta belonged. So this
     * is computed per 4-block cell: the lowest grid column in and around the cell (cached, since the
     * density function uses the same grid), minus 6, minus how far the 3D roughness can push the
     * ground below the column height there, snapped down to a multiple of 8. (Without the roughness
     * margin, rough canyon walls and crags dipped under the estimate and showed bare stone.)
     * Under wind-carved rock it's the sand at the rock's foot: the rock is all "surface", so it's
     * banded through and its holes and arches are open air to the aquifers. Likewise the floor of
     * any pit or fissure here (0.0.3 left their walls as the last bare stone in a canyon).
     */
    public double preliminarySurface(int x, int z) {
        int gx = Math.floorDiv(x, 4) * 4, gz = Math.floorDiv(z, 4) * 4;
        double low = Double.MAX_VALUE, rough = 0;
        for (int dz = -4; dz <= 8; dz += 4) {
            for (int dx = -4; dx <= 8; dx += 4) {
                ColumnSample c = column(gx + dx, gz + dz);
                low = Math.min(low, c.height() - c.rock());
                rough = Math.max(rough, c.roughness());
            }
        }
        // Pits and fissures open the ground far below that: their walls are surface too (banded in
        // badlands, and open air to the aquifers).
        low = Math.min(low, grandCaves.cutFloor(x, z));
        return 8 * Math.floor((low - 6 - ROUGH_DEPTH * rough) / 8);
    }

    /** Deepest the 3D roughness term can push the ground below the column height, per unit roughness. */
    private static final double ROUGH_DEPTH = 30;

    /** The nearest macro of a kind, for {@code /grandatlas locate}. */
    public MacroSite findMacro(Macro kind, double x, double z) {
        return macros.find(kind, x, z, 18);
    }

    /**
     * Blends the landforms of the nearby provinces (design §7). Weights fall off with how much
     * farther a province is than the nearest one, so they're continuous everywhere and there are
     * no seams. Then:
     * <ul>
     *   <li><b>Taper</b>: landforms that taper fade toward a neighbour with a different landform
     *       (down to their taper floor), so ranges end in foothills and dunes in a sand sheet.
     *       Same-landform neighbours never taper, so merged regions and chained ranges run on
     *       unbroken.</li>
     *   <li><b>Escarpments</b>: the weights are raised to a power that grows with how much the
     *       blended reliefs disagree, scaled by the edge hardness of the landforms involved.
     *       Soft neighbours blend over a few hundred blocks; a 250-block canyon plateau beside
     *       lowland narrows to a cliff band a few dozen blocks wide.</li>
     * </ul>
     */
    private void blend(ProvinceMosaic mosaic, double x, double z, Frame frame, Scratch s) {
        ProvinceMosaic.Nearest near = s.near;
        mosaic.nearest(x, z, near);
        double d1 = near.distance[0];
        int n = 0;
        double sumW = 0, sumWR = 0;
        for (int i = 0; i < ProvinceMosaic.WINDOW; i++) {
            double a = near.distance[i] - d1;
            if (a >= TRANSITION) break;
            double w = (1 - a / TRANSITION) * (1 - a / TRANSITION);
            Province p = near.province[i];
            Landform l = p.landform();
            double edge = edgeDistance(near, i);
            l.shape(p, frame, x, z, edge, s.shape);
            double taper = (l.taper() > 0 ? lerp(smoothstep(0, l.taper(), edge), l.taperFloor(), 1) : 1)
                    * frame.reliefScale();
            s.w[n] = w;
            s.hard[n] = l.hardness();
            s.untapered[n] = s.shape.relief;
            s.r[n] = s.shape.relief * taper;
            s.rough[n] = s.shape.roughness;
            s.micros[n] = s.shape.micro;
            s.rocks[n] = s.shape.rock * taper;
            s.rockInsides[n] = s.shape.rockInside;
            s.rockSizes[n] = s.shape.rockSize;
            s.zones[n] = s.shape.zone;
            s.provinces[n] = p;
            sumW += w;
            sumWR += w * s.r[n];
            n++;
        }
        double mean = sumWR / sumW;
        double spread = 0, hardSum = 0, hardSq = 0;
        for (int i = 0; i < n; i++) {
            spread += s.w[i] * Math.abs(s.r[i] - mean);
            hardSum += s.w[i] * s.hard[i];
            hardSq += s.w[i] * s.hard[i] * s.hard[i];
        }
        spread /= sumW;
        // Weighted toward the harder side: a plateau's cliff stays a cliff even though its
        // lowland neighbour is soft. Hills meeting lowlands stay a gentle blend.
        double hardness = hardSq / hardSum;

        double power = 1 + spread / 30 * hardness;
        double sumP = 0, maxP = -1, maxW = 0;
        int dominant = 0;
        for (int i = 0; i < n; i++) {
            double wp = Math.pow(s.w[i], power);
            s.wp[i] = wp;
            sumP += wp;
            if (wp > maxP) {
                maxP = wp;
                dominant = i;
            }
            maxW = Math.max(maxW, s.w[i]);
        }
        double relief = 0, rough = 0, untapered = 0, micro = 0, rock = 0;
        for (int i = 0; i < n; i++) {
            double wn = s.wp[i] / sumP;
            relief += wn * s.r[i];
            rock += wn * s.rocks[i];
            rough += wn * s.rough[i];
            untapered += wn * s.untapered[i];
            micro += wn * s.micros[i];
        }
        s.micro = micro;
        double mixing = 1 - maxW / sumW; // 0 deep inside a province, ~0.5 on a two-way edge
        // (Over 1 is the megarange's crag style; the province-edge boost only fills up to 1.)
        rough = Math.max(rough, Math.min(1, rough + 0.4 * smoothstep(40, 160, spread) * smoothstep(0.02, 0.2, mixing)));

        Province dom = s.provinces[dominant];
        s.relief = frame.clampRelief(relief);
        s.roughness = rough;
        s.rock = rock;
        s.rockInside = s.rockInsides[dominant];
        s.rockSize = s.rockSizes[dominant];
        s.landform = dom.landform();
        s.zone = s.zones[dominant];
        s.cellX = dom.cellX();
        s.cellZ = dom.cellZ();
        if (mixing < 0.03) {
            s.transition = Transition.INTERIOR;
        } else if (spread > 60) {
            s.transition = Transition.ESCARPMENT;
        } else if (untapered > 120 && relief < 0.4 * untapered) {
            s.transition = Transition.VALLEY;
            s.zone = Zone.VALLEY;
        } else {
            s.transition = Transition.SOFT;
        }
    }

    /**
     * Distance (warped blocks) from province i's edge toward the nearest province with a
     * different landform: half the distance difference, the usual Voronoi bisector estimate.
     * Negative outside province i.
     */
    private static double edgeDistance(ProvinceMosaic.Nearest near, int i) {
        Landform l = near.province[i].landform();
        double best = 10_000;
        for (int j = 0; j < ProvinceMosaic.WINDOW; j++) {
            if (j != i && near.province[j].landform() != l) {
                best = Math.min(best, (near.distance[j] - near.distance[i]) / 2);
            }
        }
        return best;
    }

    /**
     * The sea's biome by temperature and depth, with both edges broken up by noise: plain thresholds
     * drew clean curved bands across the water (the user: "the ocean forms in clear bands"), since
     * each ocean biome has its own water colour and frozen seas their ice.
     */
    private BiomeKind oceanBiome(double t, double h, double x, double z) {
        t += 0.16 * GradientNoise.noise2(variant ^ 0x0CEAL, x / 240.0, z / 240.0) + 0.06 * GradientNoise.noise2(variant ^ 0x0CEBL, x / 55.0, z / 55.0);
        boolean deep = h < 15 + 13 * GradientNoise.noise2(variant ^ 0x0CECL, x / 140.0, z / 140.0) + 4 * GradientNoise.noise2(variant ^ 0x0CEDL, x / 35.0, z / 35.0);
        if (t < -0.5) return deep ? BiomeKind.DEEP_FROZEN_OCEAN : BiomeKind.FROZEN_OCEAN;
        if (t < -0.15) return deep ? BiomeKind.DEEP_COLD_OCEAN : BiomeKind.COLD_OCEAN;
        if (t < 0.25) return deep ? BiomeKind.DEEP_OCEAN : BiomeKind.OCEAN;
        if (t < 0.55 || deep) return deep ? BiomeKind.DEEP_LUKEWARM_OCEAN : BiomeKind.LUKEWARM_OCEAN;
        return BiomeKind.WARM_OCEAN;
    }

    /**
     * Terrain density at a block: positive is solid. {@code jagged} adds the 3D roughness that
     * gives steep faces ledges and overhangs; the smooth version is never used for the surface
     * estimate (that comes straight from the column height).
     */
    public double density(ColumnSample c, int x, int y, int z, boolean jagged) {
        return Math.min(Math.min(Math.min(uncutDensity(c, x, y, z, jagged), riverCut(c, y)), Features.carve(c, x, y, z)),
                Math.min(GreatPlateau.carveWall(c.wall(), x, y, z), CoastFeature.carve(c.shore(), x, y, z)));
    }

    /**
     * The density of the ground before rivers and lakes cut it: what the world interpolates on its
     * 4x8x4 grid. The cut ({@link #riverCut}) goes in per block, outside the interpolation, which
     * would round a river's sharp banks off below its water.
     */
    public double uncutDensity(ColumnSample c, int x, int y, int z, boolean jagged) {
        double depth = c.uncut() - y;
        double d = depth * GRADIENT;
        double rough = c.roughness();
        if (jagged && rough > 0.02 && depth > -48 - 20 * Math.max(0, rough - 1) && depth < 48 + 20 * Math.max(0, rough - 1)) {
            // Blobby fBm plus a ridged term: ledges, ribs and crags rather than smooth lumps.
            double n = Fractal.fbm3(roughness, x / 44.0, y / 30.0, z / 44.0, 3);
            d += (n * 2.6 + (0.35 - Math.abs(n)) * 2.2) * Math.min(1, rough);
            if (rough > 1) {
                // Crag country (roughness past 1, from the megarange's crag style): shelves of rock
                // that jut out over undercuts, and deep vertical cracks, so the faces overhang.
                double crag = rough - 1;
                double shelf = GradientNoise.noise3(roughness ^ 0xC4A6L, x / 26.0, y / 6.0, z / 26.0);
                double crack = 1 - Math.abs(GradientNoise.noise3(roughness ^ 0xC4A7L, x / 9.0, y / 45.0, z / 9.0));
                double add = crag * (2.4 * shelf + 0.8 * (0.4 - crack * crack * crack));
                // Over the surface it only cuts away: overhangs come from undercuts under the ledges
                // left standing. Adding rock out there floated shelves as islands in front of faces.
                d += depth < 0 ? Math.min(0, add) : add;
            }
        }
        return d;
    }

    /** Air above a river's or lake's cut surface (min it with the ground); no limit where nothing cuts. */
    public static double riverCut(ColumnSample c, int y) {
        return c.height() < c.uncut() - 1e-4 ? (c.height() - y) * GRADIENT : Double.POSITIVE_INFINITY;
    }

    // ---- caves

    /**
     * Most bias {@link #caveDamping} ever adds (deep inside tall mountains). Was 0.5, which left
     * mountains ~1% open; playtest round 2 found that "way too solid". At 0.12 mountain interiors
     * were still only 2-4% open, so it's 0.06: a light touch, with Grand Atlas's own caves
     * ({@link Caves}) adding the variety.
     */
    public static final double CAVE_DAMPING = 0.06;

    /**
     * A positive bias added to vanilla's cave densities (see tools/gen_data.py). Vanilla's
     * caves were tuned for a world whose ground stops near y 256, so left alone they hollow
     * out 12–25% of a 600-block mountain's body.
     *
     * <p>Tuned in the dev world (seed grand-atlas, 256x256 over a 627-block summit; open
     * fraction of rock below the top 16 blocks). Caves stay as vanilla made them in the classic
     * cave layer (below y ~100, 11–14% open), thin out in a shell just under any surface, and
     * fall to rare caverns (~0–1.5%) deep inside mountains. Openness drops steeply with bias:
     * 0.1 already halves it, and 0.5 closes almost everything at mountain altitudes.
     */
    public double caveDamping(ColumnSample c, int y) {
        if (c.nearWater() && y > c.height() - 16) {
            return 2; // no cave mouth under a river or lake bed or its banks: it would drain into it
        }
        Wall wall = c.wall();
        if (wall != null && y > wall.low() - 8 && wall.dist() > -wall.width() / 2 - 8 && wall.dist() < wall.width() / 2 + 14) {
            return 2; // no vanilla cave half-opened in a plateau's face; the wall has its own cave mouths
        }
        double deepWorld = 1 - smoothstep(96, 224, y);
        double shell = 1 - smoothstep(32, 128, c.height() - y);
        return CAVE_DAMPING * (1 - Math.max(deepWorld, 0.85 * shell));
    }

    /**
     * The noodles' bias: {@link #caveDamping}, and solid in the top few blocks under the ground.
     * Vanilla's noodles are thin winding tunnels; where they reached open ground they scored long
     * grooves across it (the user: "random scores into the ground"). Cave mouths still open
     * through the entrances.
     */
    public double noodleDamping(ColumnSample c, int y) {
        return caveDamping(c, y) + 2 * smoothstep(c.height() - 9, c.height() - 4, y);
    }

    // ---- biomes

    public BiomeKind biomeAt(ColumnSample c, int x, int y, int z) {
        int surface = c.surfaceY();
        // Cave biomes start well under the lowest ground nearby, not under this column's own top:
        // measured from the top, a cliff's exposed face more than 12 blocks down turned dripstone
        // and lost its terracotta bands (61% of the bare stone on 0.0.2's canyon walls).
        if (y < surface - 12 && y < caveCeiling(x, z)) {
            BiomeKind cave = caveBiome(c, x, y, z, surface - y);
            if (cave != null) {
                return cave;
            }
        }
        return c.biome();
    }

    /** Per thread: the last column's cave ceiling, since biome queries walk down a quart column. */
    private final ThreadLocal<long[]> caveCeilingMemo = ThreadLocal.withInitial(() -> new long[] {0, 0, 0});

    private int caveCeiling(int x, int z) {
        long[] memo = caveCeilingMemo.get();
        long key = ((long) x << 32) ^ (z & 0xFFFFFFFFL);
        if (memo[2] == 0 || memo[0] != key) {
            memo[0] = key;
            memo[1] = (long) preliminarySurface(x, z) - 16;
            memo[2] = 1;
        }
        return (int) memo[1];
    }

    /** The top of the abyss band (docs/M7_UNDERGROUND_PLAN.md): deep dark below, lush and dripstone above. */
    public static final int ABYSS_TOP = -250;

    private static final java.util.EnumSet<BiomeKind> FORESTS = java.util.EnumSet.of(BiomeKind.FOREST, BiomeKind.FLOWER_FOREST,
            BiomeKind.BIRCH_FOREST, BiomeKind.DARK_FOREST, BiomeKind.OLD_GROWTH_BIRCH_FOREST, BiomeKind.PALE_GARDEN, BiomeKind.TAIGA,
            BiomeKind.OLD_GROWTH_SPRUCE_TAIGA, BiomeKind.OLD_GROWTH_PINE_TAIGA, BiomeKind.SNOWY_TAIGA, BiomeKind.JUNGLE, BiomeKind.SPARSE_JUNGLE,
            BiomeKind.SWAMP, BiomeKind.MANGROVE_SWAMP, BiomeKind.CHERRY_GROVE, BiomeKind.GROVE, BiomeKind.WOODED_BADLANDS);
    private static final java.util.EnumSet<BiomeKind> DESERTS = java.util.EnumSet.of(BiomeKind.DESERT, BiomeKind.RED_DESERT,
            BiomeKind.BADLANDS, BiomeKind.ERODED_BADLANDS);

    /**
     * Cave biomes in 3D (M7e, docs/M7_UNDERGROUND_PLAN.md): from the depth band, the land above,
     * the mega-caverns and a regional noise, so the underground matches the surface (ice under
     * cold mountains, sandstone under deserts, roots under any forest) and gets stranger with depth.
     * Each kind holds only where its regional noise allows, so no kind covers all its land.
     */
    private BiomeKind caveBiome(ColumnSample c, int x, int y, int z, int depthBelow) {
        double region = GradientNoise.noise2(caves ^ 0x7EA1L, x / 900.0, z / 900.0);
        double region2 = GradientNoise.noise2(caves ^ 0x7EA2L, x / 700.0, z / 700.0);
        int cavern = megaCaverns.inside(x, y, z);
        double t = c.temperature(), w = c.humidity();
        // Set pieces have their own.
        if (setPieces.nearColumn(x, z)) {
            SetPieces.Probe p = setPieces.sample(x, y, z);
            if (p.claimed) return p.kind == SetPieces.HOLLOW ? BiomeKind.THE_HOLLOW : p.kind == SetPieces.GEODE ? BiomeKind.CRYSTAL_CAVES : BiomeKind.TITANS_GRAVE;
        }
        // The upper caves follow the land over them, ice and sandstone reaching down to about -80
        // (the user: they "need more of their respective biome").
        double reach = -80 + 30 * region2;
        if (y > reach) {
            if (t < -0.45 && region > -0.6) return BiomeKind.ICE_CAVES;
            if (DESERTS.contains(c.biome()) && region > -0.6) return BiomeKind.SANDSTONE_CAVES;
            if (y >= 0 && FORESTS.contains(c.biome()) && depthBelow < 50 && region2 > -0.45) return BiomeKind.ROOT_CAVES;
        }
        // Glowworm grottos: damp caves in humid country, upper caves and the deep, threads of glowing
        // silk hanging from every ceiling.
        if (y > -220 && w > 0.2) {
            double grotto = GradientNoise.noise3(caves ^ 0x6A07L, x / 300.0, y / 160.0, z / 300.0);
            if (grotto > 0.48) return BiomeKind.GLOWWORM_GROTTO;
        }
        if (y < 0) {
            // Rare pockets, anywhere in the deep and the abyss, whatever the land above (the user:
            // "they should be rare, but random"): fungal forests, sulfur caves, crystal caves.
            double pocket = GradientNoise.noise3(caves ^ 0x90CL, x / 380.0, y / 220.0, z / 380.0);
            if (pocket > 0.5) {
                double pick = GradientNoise.noise3(caves ^ 0x90DL, x / 1300.0, y / 600.0, z / 1300.0);
                return pick < -0.18 ? BiomeKind.SULFUR_CAVES : pick < 0.18 ? BiomeKind.FUNGAL_FOREST : BiomeKind.CRYSTAL_CAVES;
            }
            // Humid mega-caverns grow fungal forests too.
            if (cavern == 1 && w > 0.05 && region2 > -0.3) return BiomeKind.FUNGAL_FOREST;
        }
        if (y < ABYSS_TOP) {
            if (cavern == 2) return BiomeKind.ABYSSAL_SHORES;
            // Deep dark is the abyss's, under rugged high ground (M7a); ancient cities start at -350.
            if (y > -475 && depthBelow > 96 && c.erosion() < -0.2) return BiomeKind.DEEP_DARK;
            // Magma chambers under volcanoes and hot country.
            if (world.volcano(x, z) > 0 || t > 0.45 && region > 0.1) return BiomeKind.MAGMA_CHAMBERS;
            return null;
        }
        double n = GradientNoise.noise3(caves, x / 640.0, y / 320.0, z / 640.0);
        if (n > 0.42 && w > -0.3) return BiomeKind.LUSH_CAVES;
        if (n < -0.42) return BiomeKind.DRIPSTONE_CAVES;
        return null;
    }

    /** Per-thread working memory for one column. */
    private static final class Scratch {
        final ProvinceMosaic.Nearest near = new ProvinceMosaic.Nearest();
        final MacroField.Hit hit = new MacroField.Hit();
        final Macro.Combined combined = new Macro.Combined();
        final Macro.Landmarks landmarks = new Macro.Landmarks();
        final Drainage.Carve carve = new Drainage.Carve();
        final Features.Column features = new Features.Column();
        final Coast coast = new Coast();
        final Shape shape = new Shape();
        final double[] w = new double[ProvinceMosaic.WINDOW], wp = new double[ProvinceMosaic.WINDOW];
        final double[] r = new double[ProvinceMosaic.WINDOW], untapered = new double[ProvinceMosaic.WINDOW];
        final double[] rough = new double[ProvinceMosaic.WINDOW], hard = new double[ProvinceMosaic.WINDOW];
        final double[] micros = new double[ProvinceMosaic.WINDOW];
        final double[] rocks = new double[ProvinceMosaic.WINDOW], rockInsides = new double[ProvinceMosaic.WINDOW];
        final double[] rockSizes = new double[ProvinceMosaic.WINDOW];
        final Zone[] zones = new Zone[ProvinceMosaic.WINDOW];
        final Province[] provinces = new Province[ProvinceMosaic.WINDOW];
        double relief, roughness, micro, rock, rockInside, rockSize;
        Landform landform;
        Zone zone;
        Transition transition;
        int cellX, cellZ;

        void reset(Landform ocean) {
            relief = 0;
            roughness = 0;
            micro = 1;
            rock = 0;
            rockInside = 0;
            rockSize = 0;
            landform = ocean;
            zone = Zone.OCEAN;
            transition = Transition.INTERIOR;
            cellX = 0;
            cellZ = 0;
        }
    }
}
