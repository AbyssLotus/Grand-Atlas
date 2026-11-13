package com.seaweed.grandatlas.core.micro;

import static com.seaweed.grandatlas.core.Maths.smoothstep;

import com.seaweed.grandatlas.core.AtlasCore;
import com.seaweed.grandatlas.core.BiomeKind;
import com.seaweed.grandatlas.core.ColumnSample;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.SplittableRandom;

/**
 * The meso sites (DESIGN §6.1, docs/MICRO_FEATURES.md). Each is judged at its centre on the ground
 * before rivers and features: how well the place suits it (0 forbidden, up to 1), then it lays
 * out its micro features. Sites aren't owned by biomes; a site that suits a forest still runs
 * on into the meadow beside it.
 */
public enum MesoKind {
    /** Humid, gentle country dotted with ponds: the small lakes the drainage network doesn't make. */
    POND_COUNTRY(1.0, 150, 240),
    /** Karst: round pits with undercut lips, flooded into cenotes where it's wet. */
    SINKHOLE_FIELD(1.0, 130, 220),
    /** Dry country with clusters of capped rock pillars. */
    HOODOO_GARDEN(0.9, 110, 200),
    /** A small volcanic field: cinder cones, basalt columns and black ground. */
    CINDER_FIELD(0.35, 180, 290),
    /** A clearing in a forest: open meadow ringed by boulders and fallen trees. */
    GLADE(0.9, 45, 80),
    /** Cool country strewn with great boulders the glaciers dropped. */
    ERRATIC_FIELD(0.3, 120, 220),
    /** Open hills crowned with stacked granite tors. */
    TOR_HILLS(0.6, 140, 240),
    /** High mountain hollows holding small cold lakes with stony shores. */
    TARN_BASIN(1.0, 110, 190),
    /** Dry country with a scatter of small banded buttes. */
    BUTTE_FIELD(0.5, 160, 270),
    /** Old-growth forest round a few giant trees. */
    ANCIENT_GROVE(0.8, 110, 190),
    /** A dim hollow under dark forest where giant mushrooms grow. */
    MUSHROOM_HOLLOW(0.5, 90, 160),
    /** Woods where trees fall and stand dead: snags, fallen trunks, leaf litter. */
    DEADFALL(0.6, 100, 170),
    /** A patch of pale garden inside an ordinary forest: grey oaks, pale moss, an eerie hush. */
    PALE_GROVE(0.35, 60, 110),
    /** Woods cut by winding gullies full of ferns. */
    FERN_GULLY(0.6, 120, 200),
    /** A grotto (set piece): a collapsed cave with a pool under an overhang and a fall from its rim. */
    GROTTO(1.0, 50, 80),
    /** Terraced cascades (set piece): turquoise pools stepping down a wooded hillside over travertine rims. */
    TERRACE_FALLS(0.9, 80, 110),
    /** Crag towers (set piece): one to three great rock towers standing alone in open country. */
    CRAG_TOWERS(0.5, 120, 200),
    /**
     * A lake district (the Canadian Shield, docs/M3_M4_PLAN.md phase 5): low, rocky, cool country
     * crowded with lakes of every size, bare rock shores, rocky islands in the big ones.
     */
    LAKE_DISTRICT(1.6, 220, 340),
    /**
     * A caldera (Crater Lake, docs/M3_M4_PLAN.md phase 5, a set piece): the broken-off stump of a
     * great volcano, a ring of forested rim standing high over the country round it, sheer cliffs
     * and talus inside dropping to a deep blue lake, often with a cinder-cone island in it.
     */
    CALDERA(0.45, 270, 300),
    // ---- M6 fantasy sites (built in world/MicroDetail)
    /** Giant spires of aurora crystal thrusting out of the ground at angles, glowing in their colours. */
    CRYSTAL_GARDEN(0.8, 70, 130),
    /** A ring of tall rune-cut standing stones round an altar stone, on open ground. */
    STANDING_STONES(0.5, 34, 52),
    /** Boulders and crags floating over the ground, hung with vines and roots, the odd one grassed and treed. */
    FLOATING_ROCKS(0.8, 70, 120),
    /** The bones of giants: great rib cages arching out of the ground along their spines, skulls half buried. */
    BONE_FIELD(0.6, 80, 140),
    /** A petrified forest: trunks of stone, some standing, most fallen, in bare ground. */
    PETRIFIED_FOREST(0.6, 90, 150),
    // ---- M5 real-world wonders
    /**
     * Spotted Lake (after Kliluk in British Columbia): a shallow basin in dry grassland whose water
     * has dried back into dozens of round pools, each a different colour (green, gold, blue) from
     * the minerals left in it, walled apart by a white crust you can walk across.
     */
    SPOTTED_LAKE(1.6, 75, 110);

    /** How common it is where it suits. */
    final double chance;
    final double minRadius, maxRadius;

    MesoKind(double chance, double minRadius, double maxRadius) {
        this.chance = chance;
        this.minRadius = minRadius;
        this.maxRadius = maxRadius;
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** 0 where it can't go, up to 1 where it fits best. {@code slope} is the ground's rise per block nearby. */
    double suitability(ColumnSample c, double slope) {
        if (c.continentalness() < 0.03 || c.height() < AtlasCore.SEA_LEVEL + 3 || c.rock() > 0.5 || !Double.isNaN(c.water())) return 0;
        String land = c.landform().id();
        boolean ordinary = land.equals("lowlands") || land.equals("hills") || land.equals("highlands");
        boolean enchanted = land.equals("glimmer_vale") || land.equals("silverwood") || land.equals("misty_peaks");
        boolean volcanic = land.equals("volcanic_wastes");
        boolean dry = DRY.contains(c.biome());
        double t = c.temperature(), w = c.humidity();
        double gentle = 1 - smoothstep(0.12, 0.35, slope);
        return switch (this) {
            case POND_COUNTRY -> ordinary ? gentle * smoothstep(-0.25, 0.1, w) * smoothstep(-0.8, -0.5, t) : 0;
            case SINKHOLE_FIELD -> ordinary ? gentle * smoothstep(-0.05, 0.25, w) * smoothstep(-0.5, -0.2, t) : 0;
            case HOODOO_GARDEN -> (ordinary || land.equals("canyon_plateau")) && !forest(c.biome()) && t > -0.4
                    ? gentle * (1 - smoothstep(-0.2, 0.05, w)) : 0;
            case CINDER_FIELD -> volcanic ? gentle * 0.6 : (ordinary || land.equals("canyon_plateau") || land.equals("playa") && !c.zone().id().equals("pan"))
                    ? gentle * smoothstep(-0.6, -0.3, t) : 0;
            case GLADE -> forest(c.biome()) ? gentle : 0;
            case ERRATIC_FIELD -> ordinary ? gentle * (1 - smoothstep(-0.1, 0.25, t)) : 0;
            case TOR_HILLS -> (ordinary && !forest(c.biome())) ? (1 - smoothstep(0.3, 0.5, slope)) * smoothstep(-0.4, -0.15, t) * (1 - smoothstep(0.1, 0.4, w)) : 0;
            case TARN_BASIN -> (land.equals("megarange") || land.equals("highlands")) && c.height() > 230
                    ? gentle * (1 - smoothstep(0.1, 0.4, t)) : 0;
            case ANCIENT_GROVE -> forest(c.biome()) && c.biome() != BiomeKind.JUNGLE ? gentle : 0;
            case MUSHROOM_HOLLOW -> forest(c.biome()) && t > -0.3 ? gentle * smoothstep(-0.2, 0.15, w) : 0;
            case DEADFALL -> forest(c.biome()) ? gentle : 0;
            case PALE_GROVE -> forest(c.biome()) && t > -0.3 && t < 0.55 && c.biome() != BiomeKind.JUNGLE && c.biome() != BiomeKind.SPARSE_JUNGLE ? gentle : 0;
            case FERN_GULLY -> forest(c.biome()) && t > -0.35 && c.biome() != BiomeKind.SNOWY_TAIGA // snow would bury the ferns
                    ? (1 - smoothstep(0.25, 0.45, slope)) * smoothstep(-0.3, 0.0, w) : 0;
            case BUTTE_FIELD -> (ordinary || land.equals("playa") && !c.zone().id().equals("pan")) && !forest(c.biome()) && t > -0.3
                    ? gentle * (1 - smoothstep(-0.25, 0.0, w)) : 0;
            case GROTTO -> (ordinary || forest(c.biome())) && t > -0.35 ? gentle * smoothstep(-0.3, 0.05, w) : 0;
            case CRAG_TOWERS -> ordinary && !forest(c.biome()) ? gentle : 0;
            case LAKE_DISTRICT -> ordinary && t > -0.7 && t < 0.25 ? gentle * smoothstep(-0.25, 0.1, w) : 0;
            case CALDERA -> ordinary ? 1 - smoothstep(0.1, 0.3, slope) : 0;
            // A hillside, not flat ground: the terraces need a fall to step down.
            // Fantasy: at home in the fantasy landforms, a rare wonder in ordinary country.
            case CRYSTAL_GARDEN -> (land.equals("glimmer_vale") || land.equals("misty_peaks")) ? gentle
                    : ordinary && t > -0.6 ? 0.08 * gentle : 0;
            case STANDING_STONES -> land.equals("silverwood") ? gentle : ordinary && !forest(c.biome()) && !dry ? 0.25 * gentle : 0;
            case FLOATING_ROCKS -> enchanted ? 1 - smoothstep(0.3, 0.6, slope) : ordinary && w > 0.1 ? 0.06 * gentle : 0;
            case BONE_FIELD -> volcanic ? gentle * 0.8 : (ordinary || land.equals("dune_sea") || land.equals("rock_desert") || land.equals("playa")
                    && !c.zone().id().equals("pan")) && dry ? 0.35 * gentle : 0;
            case PETRIFIED_FOREST -> volcanic ? gentle * 0.8 : (ordinary || land.equals("canyon_plateau") || land.equals("playa")
                    && !c.zone().id().equals("pan")) && (dry || t > 0.2 && w < -0.2) ? 0.6 * gentle : 0;
            case SPOTTED_LAKE -> (ordinary || land.equals("rift_valley") || land.equals("rainbow_mountains") && c.zone().id().equals("valley")
                    || land.equals("playa") && !c.zone().id().equals("pan")) && !forest(c.biome()) && t > -0.45
                    ? (1 - smoothstep(0.05, 0.14, slope)) * (1 - smoothstep(-0.15, 0.15, w)) : 0;
            case TERRACE_FALLS -> (ordinary || forest(c.biome())) && t > -0.25
                    ? smoothstep(0.06, 0.14, slope) * (1 - smoothstep(0.4, 0.6, slope)) * smoothstep(-0.2, 0.15, w) : 0;
        };
    }

    private static final java.util.EnumSet<BiomeKind> DRY = java.util.EnumSet.of(BiomeKind.DESERT, BiomeKind.RED_DESERT, BiomeKind.BADLANDS,
            BiomeKind.ERODED_BADLANDS, BiomeKind.WOODED_BADLANDS, BiomeKind.SAVANNA, BiomeKind.SAVANNA_PLATEAU, BiomeKind.WINDSWEPT_SAVANNA,
            BiomeKind.VOLCANIC_WASTES, BiomeKind.VOLCANO);

    private static boolean forest(BiomeKind b) {
        return switch (b) {
            case FOREST, FLOWER_FOREST, BIRCH_FOREST, DARK_FOREST, TAIGA, OLD_GROWTH_SPRUCE_TAIGA, SNOWY_TAIGA, JUNGLE, SPARSE_JUNGLE,
                    OLD_GROWTH_BIRCH_FOREST, OLD_GROWTH_PINE_TAIGA, PALE_GARDEN -> true;
            default -> false;
        };
    }

    /** Lays out the site's micro features, spaced apart, each on the ground at its own centre. */
    List<MicroFeature> populate(SplittableRandom rnd, double cx, double cz, double radius, ColumnSample centre, Features.Terrain ground) {
        List<MicroFeature> out = new ArrayList<>();
        double cx0 = cx, cz0 = cz;
        switch (this) {
            case POND_COUNTRY -> {
                int n = 4 + rnd.nextInt(5);
                for (int k = 0; k < n; k++) {
                    double r = 6 + 14 * Math.pow(rnd.nextDouble(), 1.6);
                    place(out, rnd, cx0, cz0, radius, cx, cz, radius * 0.8, r * 1.45 + 6, ground, (x, z, g, seed) ->
                            new MicroFeature(MicroKind.POND, x, z, r, 0, 0, 0, g, seed));
                }
            }
            case SINKHOLE_FIELD -> {
                double wet = smoothstep(0.1, 0.5, centre.humidity());
                int n = 3 + rnd.nextInt(4);
                for (int k = 0; k < n; k++) {
                    boolean cenote = rnd.nextDouble() < wet;
                    double r = cenote ? 7 + 8 * rnd.nextDouble() : 4 + 7 * rnd.nextDouble();
                    double depth = cenote ? 22 + 14 * rnd.nextDouble() : 14 + 22 * rnd.nextDouble();
                    double water = cenote ? depth - (8 + 6 * rnd.nextDouble()) : 4; // a cenote's pool 8-14 under the rim, in sight
                    place(out, rnd, cx0, cz0, radius, cx, cz, radius * 0.8, r * 1.5 + 10, ground, (x, z, g, seed) ->
                            new MicroFeature(cenote ? MicroKind.CENOTE : MicroKind.SINKHOLE, x, z, r, depth, 0, water, g, seed));
                }
            }
            case HOODOO_GARDEN -> {
                int clusters = 2 + rnd.nextInt(3);
                for (int c = 0; c < clusters; c++) {
                    double a = rnd.nextDouble() * Math.PI * 2, d = radius * 0.6 * Math.sqrt(rnd.nextDouble());
                    double kx = cx + Math.cos(a) * d, kz = cz + Math.sin(a) * d;
                    int n = 4 + rnd.nextInt(7);
                    for (int k = 0; k < n; k++) {
                        double r0 = 2.0 + 2.0 * rnd.nextDouble();
                        double tall = 7 + 17 * Math.pow(rnd.nextDouble(), 1.5);
                        place(out, rnd, cx0, cz0, radius, kx, kz, 26, r0 * 1.3 + 2.5, ground, (x, z, g, seed) ->
                                new MicroFeature(MicroKind.HOODOO, x, z, r0, tall, r0 * 1.3, 0, g, seed));
                    }
                }
            }
            case CINDER_FIELD -> {
                int cones = 1 + rnd.nextInt(3);
                for (int k = 0; k < cones; k++) {
                    double r = 22 + 26 * rnd.nextDouble();
                    double tall = r * (0.42 + 0.13 * rnd.nextDouble());
                    place(out, rnd, cx0, cz0, radius, cx, cz, radius * 0.7, r * 1.15 + 12, ground, (x, z, g, seed) ->
                            new MicroFeature(MicroKind.CINDER_CONE, x, z, r, tall, r * 0.32, tall * 0.4, g, seed));
                }
                int clusters = 2 + rnd.nextInt(3);
                for (int k = 0; k < clusters; k++) {
                    double r = 3 + 4 * rnd.nextDouble();
                    place(out, rnd, cx0, cz0, radius, cx, cz, radius * 0.85, r + 4, ground, (x, z, g, seed) ->
                            new MicroFeature(MicroKind.BASALT_COLUMNS, x, z, r, 0, 0, 0, g, seed));
                }
            }
            case GLADE -> {
                // A clearing: no terrain features, only its meadow and detail.
            }
            case ERRATIC_FIELD -> {
                int n = 6 + rnd.nextInt(8);
                for (int k = 0; k < n; k++) {
                    double r = 2.5 + 3.5 * Math.pow(rnd.nextDouble(), 1.4);
                    place(out, rnd, cx0, cz0, radius, cx, cz, radius * 0.85, r + 8, ground, (x, z, g, seed) ->
                            new MicroFeature(MicroKind.ERRATIC, x, z, r, 0, 0, 0, g, seed));
                }
            }
            case TOR_HILLS -> {
                int n = 3 + rnd.nextInt(4);
                for (int k = 0; k < n; k++) {
                    double r = 4 + 3 * rnd.nextDouble();
                    int stack = 2 + rnd.nextInt(3), pillars = 1 + rnd.nextInt(3);
                    place(out, rnd, cx0, cz0, radius, cx, cz, radius * 0.8, r * 2 + 14, ground, (x, z, g, seed) ->
                            new MicroFeature(MicroKind.TOR, x, z, r, stack, pillars, 0, g, seed));
                }
            }
            case TARN_BASIN -> {
                int n = 2 + rnd.nextInt(3);
                for (int k = 0; k < n; k++) {
                    double r = 7 + 10 * rnd.nextDouble();
                    place(out, rnd, cx0, cz0, radius, cx, cz, radius * 0.8, r * 1.45 + 8, ground, (x, z, g, seed) ->
                            new MicroFeature(MicroKind.POND, x, z, r, 0, 0, 0, g, seed));
                }
            }
            case ANCIENT_GROVE -> {
                int n = 2 + rnd.nextInt(3);
                for (int k = 0; k < n; k++) {
                    // Far bigger than anything vanilla grows (mega spruces reach ~30), so they stand out.
                    double trunk = 2.5 + 1.1 * rnd.nextDouble(), tall = 40 + 15 * rnd.nextDouble();
                    double crown = 13 + 4 * rnd.nextDouble();
                    place(out, rnd, cx0, cz0, radius, cx, cz, radius * 0.6, crown + 6, ground, (x, z, g, seed) ->
                            new MicroFeature(MicroKind.GIANT_TREE, x, z, trunk, tall, crown, 0, g, seed));
                }
            }
            case MUSHROOM_HOLLOW -> {
                int n = 4 + rnd.nextInt(5);
                for (int k = 0; k < n; k++) {
                    boolean brown = rnd.nextDouble() < 0.45;
                    double stem = 1 + 0.7 * rnd.nextDouble(), tall = 8 + 11 * rnd.nextDouble();
                    double cap = brown ? 5 + 4 * rnd.nextDouble() : 4 + 3 * rnd.nextDouble();
                    place(out, rnd, cx0, cz0, radius, cx, cz, radius * 0.7, cap + 3, ground, (x, z, g, seed) ->
                            new MicroFeature(MicroKind.GIANT_MUSHROOM, x, z, stem, tall, cap, brown ? 1 : 0, g, seed));
                }
            }
            case DEADFALL -> {
                int n = 5 + rnd.nextInt(7);
                for (int k = 0; k < n; k++) {
                    double tall = 6 + 9 * rnd.nextDouble(), thick = rnd.nextDouble() < 0.3 ? 1 : 0.5;
                    place(out, rnd, cx0, cz0, radius, cx, cz, radius * 0.85, 9, ground, (x, z, g, seed) ->
                            new MicroFeature(MicroKind.SNAG, x, z, thick, tall, 0, 0, g, seed));
                }
            }
            case PALE_GROVE -> {
                // The pale garden itself (its biome) is the feature: grey oaks, pale moss, creaking.
            }
            case FERN_GULLY -> {
                int n = 1 + rnd.nextInt(2);
                for (int k = 0; k < n; k++) {
                    // A real ravine (the user: the first ones, 4-8 deep and wide, didn't show under the trees).
                    double half = 3 + 2 * rnd.nextDouble(), depth = 9 + 7 * rnd.nextDouble(), length = 60 + 50 * rnd.nextDouble();
                    double heading = rnd.nextDouble() * Math.PI;
                    place(out, rnd, cx0, cz0, radius, cx, cz, radius * 0.4, length / 2 + 10, ground, (x, z, g, seed) ->
                            new MicroFeature(MicroKind.GULLY, x, z, half, depth, length, heading, g, seed));
                }
            }
            case LAKE_DISTRICT -> {
                int n = 14 + rnd.nextInt(15);
                for (int k = 0; k < n; k++) {
                    double r = 7 + 45 * Math.pow(rnd.nextDouble(), 1.8);
                    place(out, rnd, cx0, cz0, radius, cx, cz, radius * 0.85, r * 0.6 + 8, ground, (x, z, g, seed) ->
                            new MicroFeature(MicroKind.POND, x, z, r, 0, 0, 0, g, seed));
                }
            }
            case CALDERA -> {
                // The volcano is the whole site; its island cone, if it has one, sits off centre in the lake.
                double rim = radius * (0.48 + 0.05 * rnd.nextDouble()), tall = 60 + 35 * rnd.nextDouble();
                MicroFeature cal = new MicroFeature(MicroKind.CALDERA, cx, cz, rim, tall, radius - 2, 0, ground.sample(cx, cz).height(), rnd.nextLong());
                out.add(cal);
                if (rnd.nextDouble() < 0.7) {
                    double a = rnd.nextDouble() * Math.PI * 2, off = rim * (0.15 + 0.15 * rnd.nextDouble());
                    double r = rim * (0.28 + 0.04 * rnd.nextDouble()), cone = cal.calderaDepth() + 16 + 10 * rnd.nextDouble();
                    double ix = cx + Math.cos(a) * off, iz = cz + Math.sin(a) * off;
                    out.add(new MicroFeature(MicroKind.CINDER_CONE, ix, iz, r, cone, r * 0.25, cone * 0.18, ground.sample(ix, iz).height(), rnd.nextLong()));
                }
            }
            case SPOTTED_LAKE -> {
                // A main basin and two smaller ones, ponds whose water is broken into pools at
                // decoration. A pond only holds on level enough ground, so of the three, those that
                // fit the lie of the land stand and the rest never appear.
                for (int k = 0; k < 3; k++) {
                    double r = k == 0 ? 24 + 8 * rnd.nextDouble() : 14 + 8 * rnd.nextDouble();
                    place(out, rnd, cx0, cz0, radius, cx, cz, radius * (k == 0 ? 0.15 : 0.55), r * 1.45 + 6, ground, (x, z, g, seed) ->
                            new MicroFeature(MicroKind.POND, x, z, r, 0, 0, 0, g, seed));
                }
            }
            case CRYSTAL_GARDEN -> {
                int n = 7 + rnd.nextInt(9);
                int colour = rnd.nextInt(5); // the garden has a colour of its own, its spires in it and its neighbours
                for (int k = 0; k < n; k++) {
                    boolean big = k < 2;
                    double half = big ? 3 + 1.5 * rnd.nextDouble() : 1.2 + 2 * rnd.nextDouble();
                    double tall = big ? 26 + 16 * rnd.nextDouble() : 7 + 18 * Math.pow(rnd.nextDouble(), 1.3);
                    double lean = rnd.nextDouble() * Math.PI * 2;
                    int col = (colour + (rnd.nextDouble() < 0.7 ? 0 : rnd.nextDouble() < 0.5 ? 1 : 4)) % 5;
                    place(out, rnd, cx0, cz0, radius, cx, cz, radius * (big ? 0.35 : 0.8), half + 5, ground, (x, z, g, seed) ->
                            new MicroFeature(MicroKind.CRYSTAL_SPIRE, x, z, half, tall, lean, col, g, seed));
                }
            }
            case STANDING_STONES -> {
                // A ring, not a scatter: evenly round a circle, one gap where the way in is.
                int n = 7 + rnd.nextInt(6);
                double ring = Math.min(radius - 8, 12 + 9 * rnd.nextDouble()), gap = rnd.nextDouble() * Math.PI * 2;
                for (int k = 0; k < n; k++) {
                    double a = gap + (k + 0.5) * (Math.PI * 2 - 0.5) / n + 0.25;
                    double x = cx + Math.cos(a) * ring, z = cz + Math.sin(a) * ring;
                    double tall = 6 + 5 * rnd.nextDouble();
                    out.add(new MicroFeature(MicroKind.MONOLITH, x, z, 1, tall, a, rnd.nextDouble() < 0.15 ? 1 : 0, ground.sample(x, z).height(), rnd.nextLong()));
                }
                out.add(new MicroFeature(MicroKind.MONOLITH, cx, cz, 2, 2, gap, 2, ground.sample(cx, cz).height(), rnd.nextLong())); // the altar
            }
            case FLOATING_ROCKS -> {
                int n = 5 + rnd.nextInt(7);
                for (int k = 0; k < n; k++) {
                    double r = 2.5 + 7 * Math.pow(rnd.nextDouble(), 1.5), lift = 12 + 40 * rnd.nextDouble();
                    place(out, rnd, cx0, cz0, radius, cx, cz, radius * 0.8, r + 6, ground, (x, z, g, seed) ->
                            new MicroFeature(MicroKind.FLOAT_ROCK, x, z, r, lift, 0, 0, g, seed));
                }
            }
            case BONE_FIELD -> {
                int n = 1 + rnd.nextInt(3);
                for (int k = 0; k < n; k++) {
                    double length = 28 + 30 * rnd.nextDouble(), tall = 10 + 9 * rnd.nextDouble(), heading = rnd.nextDouble() * Math.PI;
                    place(out, rnd, cx0, cz0, radius, cx, cz, radius * 0.5, length / 2 + 8, ground, (x, z, g, seed) ->
                            new MicroFeature(MicroKind.RIB_CAGE, x, z, tall * 0.9, tall, length, heading, g, seed));
                }
            }
            case PETRIFIED_FOREST -> {
                int n = 9 + rnd.nextInt(10);
                for (int k = 0; k < n; k++) {
                    double r = 1.3 + 1.4 * rnd.nextDouble(), tall = 5 + 16 * rnd.nextDouble();
                    double fallen = rnd.nextDouble() < 0.6 ? rnd.nextDouble() * Math.PI * 2 : -1;
                    place(out, rnd, cx0, cz0, radius, cx, cz, radius * 0.85, fallen >= 0 ? tall / 2 + 3 : r + 4, ground, (x, z, g, seed) ->
                            new MicroFeature(MicroKind.PETRIFIED_TRUNK, x, z, r, tall, 0, fallen, g, seed));
                }
            }
            case CRAG_TOWERS -> {
                int n = 1 + (rnd.nextDouble() < 0.5 ? 0 : 1 + rnd.nextInt(2));
                for (int k = 0; k < n; k++) {
                    double r = (k == 0 ? 20 : 12) + 12 * rnd.nextDouble(), tall = r * (3.2 + 1.8 * rnd.nextDouble());
                    double cleft = rnd.nextDouble() * Math.PI;
                    place(out, rnd, cx0, cz0, radius, cx, cz, radius * 0.6, r * 1.3 + 14, ground, (x, z, g, seed) ->
                            new MicroFeature(MicroKind.SPIRE, x, z, r, tall, 0, cleft, g, seed));
                }
            }
            case TERRACE_FALLS -> {
                // Down the fall line: from the ground's gradient at the centre, top uphill of it.
                double gx = ground.sample(cx + 16, cz).height() - ground.sample(cx - 16, cz).height();
                double gz = ground.sample(cx, cz + 16).height() - ground.sample(cx, cz - 16).height();
                double heading = Math.atan2(-gz, -gx);
                double half = 8 + 5 * rnd.nextDouble(), length = 55 + 30 * rnd.nextDouble();
                double tx = cx - Math.cos(heading) * length / 2, tz = cz - Math.sin(heading) * length / 2;
                long seed = rnd.nextLong();
                out.add(new MicroFeature(MicroKind.TRAVERTINE, tx, tz, half, 0, length, heading, ground.sample(tx, tz).height(), seed));
            }
            case GROTTO -> {
                double r = 9 + 5 * rnd.nextDouble(), depth = 17 + 7 * rnd.nextDouble(), reach = r * (0.6 + 0.35 * rnd.nextDouble());
                place(out, rnd, cx0, cz0, radius, cx, cz, radius * 0.3, r + reach + 20, ground, (x, z, g, seed) ->
                        new MicroFeature(MicroKind.GROTTO, x, z, r, depth, reach, 0, g, seed));
            }
            case BUTTE_FIELD -> {
                int n = 2 + rnd.nextInt(4);
                for (int k = 0; k < n; k++) {
                    double r = 9 + 12 * rnd.nextDouble();
                    double tall = 12 + 14 * rnd.nextDouble();
                    place(out, rnd, cx0, cz0, radius, cx, cz, radius * 0.75, r + 20, ground, (x, z, g, seed) ->
                            new MicroFeature(MicroKind.BUTTE, x, z, r, tall, 0, 0, g, seed));
                }
            }
        }
        return out;
    }

    @FunctionalInterface
    private interface Maker {
        MicroFeature make(double x, double z, double ground, long seed);
    }

    /**
     * Up to 30 tries for a spot within {@code spread} of (cx, cz), at least {@code clear} from the
     * others' reach, and with its own reach inside the site (columns outside it don't see the site).
     */
    private static void place(List<MicroFeature> out, SplittableRandom rnd, double siteX, double siteZ, double siteR,
            double cx, double cz, double spread, double clear, Features.Terrain ground, Maker maker) {
        for (int attempt = 0; attempt < 30; attempt++) {
            double a = rnd.nextDouble() * Math.PI * 2, d = spread * Math.sqrt(rnd.nextDouble());
            double x = cx + Math.cos(a) * d, z = cz + Math.sin(a) * d;
            boolean free = Math.hypot(x - siteX, z - siteZ) + maker.make(x, z, 0, 0).reach() <= siteR;
            for (MicroFeature m : out) {
                if (!free) break;
                if (Math.hypot(m.x - x, m.z - z) < clear + m.reach()) {
                    free = false;
                    break;
                }
            }
            if (free) {
                out.add(maker.make(x, z, ground.sample(x, z).height(), rnd.nextLong()));
                return;
            }
        }
    }
}
