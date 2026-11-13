package com.seaweed.grandatlas.world;

import com.mojang.serialization.MapCodec;
import com.seaweed.grandatlas.core.AtlasCore;
import com.seaweed.grandatlas.core.BiomeKind;
import com.seaweed.grandatlas.core.ColumnSample;
import com.seaweed.grandatlas.core.noise.GradientNoise;
import com.seaweed.grandatlas.world.AssetLibrary.Asset;
import com.seaweed.grandatlas.world.AssetLibrary.Group;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.feature.Feature;
import javax.annotation.Nullable;

/**
 * {@code grandatlas:atlas_assets}: the hand-built trees and rock formations (the user,
 * 2026-11-10: "make good use of these large custom trees, as well as the rock formations").
 * Sources and terms are in docs/CREDITS.md; the files are packed by tools/assets/Import.java.
 * <ul>
 *   <li><b>Trees</b> (H2CO3's 1k treepack): mixed through every wooded biome in the wood and leaf
 *       of the biome, small to giant, thinning as they get bigger. They go in before the game's
 *       own trees, which then fill the ground between them.</li>
 *   <li><b>God trees</b> (Plutouthere): landmark trees 80-180 tall, one in a few hundred blocks at
 *       most, on gentle ground: oaks in the woods and plains, willows in wet lowlands, a jungle
 *       giant, blossoming ones in cherry groves and meadows, bare ones in the snow and the dry.</li>
 *   <li><b>Rock formations</b> (Dannypan): outcrops everywhere, dressed for their biome and thick
 *       where the country is stony; natural arches now and then; and whole districts of spires,
 *       of pillar mountains (Zhangjiajie, in warm wet hill country), of bladed fins in the deserts
 *       and badlands, and spike clusters in the dry and the high.</li>
 * </ul>
 * Every asset is anchored by a hash of its grid cell and built from the core's columns, each chunk
 * writing only its own blocks, so one that spans chunks comes out whole whichever generates
 * first. Runs after the country detail and before trees and plants.
 */
public record AtlasAssets() implements AtlasFeature {
    public static final MapCodec<AtlasAssets> CODEC = MapCodec.unit(AtlasAssets::new);
    private static final long SEED = 0xA55E7A7A5L;

    private enum Layer {
        TREES(11, 1, AssetLibrary.TREES, "tree"),
        GOD(540, 2, AssetLibrary.GOD_TREES, "godtree"),
        OUTCROP(64, 3, AssetLibrary.ROCKS, "outcrop"),
        ARCH(330, 4, AssetLibrary.ROCKS, "arch"),
        SPIRE(78, 5, AssetLibrary.ROCKS, "spire"),
        PILLAR(86, 6, AssetLibrary.ROCKS, "pillar"),
        FIN(46, 7, AssetLibrary.ROCKS, "fin"),
        SPIKES(42, 8, AssetLibrary.ROCKS, "spikes"),
        RING(520, 9, AssetLibrary.ROCKS, "ring"),
        // M6: the Silverwood's colossal trees, far thicker than god trees anywhere else.
        SILVER(150, 10, AssetLibrary.GOD_TREES, "godtree");

        final double cell;
        final int salt;
        final String file, tag;
        private int margin = -1;

        Layer(double cell, int salt, String file, String tag) {
            this.cell = cell;
            this.salt = salt;
            this.file = file;
            this.tag = tag;
        }

        /** How far outside a chunk an anchor can be and still reach it: the widest asset of this kind. */
        int margin() {
            if (margin < 0) margin = AssetLibrary.group(file).reach(tag) + 2;
            return margin;
        }
    }

    private enum Country { SILVER, WOODS, BIRCH, DARK, TAIGA, SNOWY, JUNGLE, SWAMP, SAVANNA, OPEN, CHERRY, DRY, RED, ALPINE, NONE }

    @Override
    public MapCodec<AtlasAssets> codec() {
        return CODEC;
    }

    @Override
    public boolean place(WorldGenLevel level, ChunkGenerator chunkGenerator, RandomSource random, BlockPos origin) {
        AtlasCore core = CoreHandle.coreOf(level.getLevel());
        if (core == null) return false;
        int x0 = origin.getX() & ~15, z0 = origin.getZ() & ~15;
        boolean placed = false;
        for (Layer layer : Layer.values()) {
            int m = layer.margin();
            int i0 = GradientNoise.floor((x0 - m) / layer.cell), i1 = GradientNoise.floor((x0 + 15 + m) / layer.cell);
            int j0 = GradientNoise.floor((z0 - m) / layer.cell), j1 = GradientNoise.floor((z0 + 15 + m) / layer.cell);
            for (int j = j0; j <= j1; j++) {
                for (int i = i0; i <= i1; i++) {
                    long h = GradientNoise.hash(SEED ^ layer.salt, i, layer.salt, j);
                    int[] at = anchor(layer, core, i, j, h);
                    int px = at[0], pz = at[1];
                    ColumnSample c = core.column(px, pz);
                    if (!clear(core, c, px, pz)) continue;
                    Asset a = choose(layer, core, c, px, pz, h);
                    if (a == null) continue;
                    int turn = layer == Layer.FIN ? 0 : (int) ((h >>> 20) & 3); // fins lean one way: the wind's
                    if (!reaches(a, px, pz, x0, z0)) continue;
                    if (wet(core, a, px, pz)) continue;
                    placed |= build(level, core, a, px, pz, turn, layer, x0, z0);
                    if (layer == Layer.GOD || layer == Layer.SILVER) placed |= garden(level, core, a, px, pz, x0, z0, h);
                }
            }
        }
        return placed;
    }

    /** What {@code /grandatlas locate} can find of these: name to layer. */
    public static final java.util.List<String> LOCATABLE = java.util.List.of("god_tree", "rock_arch", "spires", "pillar_mountains", "rock_fins",
            "spike_field", "stone_ring", "outcrop");

    /** The nearest anchor of a kind to (x, z), searched outward ring by ring: {x, ground y, z}, or null within about 8 km. */
    public static @Nullable int[] nearest(AtlasCore core, String what, int x, int z) {
        Layer layer = switch (what) {
            case "god_tree" -> Layer.GOD;
            case "rock_arch" -> Layer.ARCH;
            case "spires" -> Layer.SPIRE;
            case "pillar_mountains" -> Layer.PILLAR;
            case "rock_fins" -> Layer.FIN;
            case "spike_field" -> Layer.SPIKES;
            case "stone_ring" -> Layer.RING;
            default -> Layer.OUTCROP;
        };
        int ci = GradientNoise.floor(x / layer.cell), cj = GradientNoise.floor(z / layer.cell);
        int rings = (int) Math.min(400, 8000 / layer.cell);
        int[] best = null;
        double bd = Double.MAX_VALUE;
        for (int r = 0; r <= rings; r++) {
            if (best != null && (r - 1) * layer.cell > bd) break; // nothing in a farther ring can be nearer
            for (int j = -r; j <= r; j++) {
                for (int i = -r; i <= r; i += (Math.abs(j) == r ? 1 : Math.max(1, 2 * r))) {
                    long h = GradientNoise.hash(SEED ^ layer.salt, ci + i, layer.salt, cj + j);
                    int[] at = anchor(layer, core, ci + i, cj + j, h);
                    int px = at[0], pz = at[1];
                    double d = Math.hypot(px - x, pz - z);
                    if (d >= bd) continue;
                    ColumnSample c = core.column(px, pz);
                    if (!clear(core, c, px, pz)) continue;
                    Asset found = choose(layer, core, c, px, pz, h);
                    if (found == null || wet(core, found, px, pz)) continue; // (one beside water is never built: don't send anyone to it)
                    bd = d;
                    best = new int[] {px, c.surfaceY() + 1, pz};
                }
            }
        }
        return best;
    }

    private static final java.util.concurrent.ConcurrentHashMap<Long, int[]> SITED = new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * Where a cell's asset stands. Most kinds stand wherever the cell's hash puts them. A god tree
     * and a natural arch are landmarks, and a landmark is sited (the user, 2026-11-10: "a more
     * bespoke vibe ... even though it's not actually hand made"): of seven spots in the cell it
     * takes the one that stands proudest of the ground round it, a hilltop, a spur, a rise over a
     * valley, on gentle ground, the way someone placing it by hand would. Remembered per cell.
     */
    private static int[] anchor(Layer layer, AtlasCore core, int i, int j, long h) {
        int jx = GradientNoise.floor((i + unit(h ^ 1)) * layer.cell), jz = GradientNoise.floor((j + unit(h ^ 2)) * layer.cell);
        if (layer != Layer.GOD && layer != Layer.ARCH) return new int[] {jx, jz};
        long key = ((long) layer.salt << 56) ^ ((long) i << 28) ^ (j & 0xFFFFFFFL) ^ (core.hashCode() * 0x9E3779B97F4A7C15L);
        int[] known = SITED.get(key);
        if (known != null) return known;
        if (SITED.size() > 8192) SITED.clear();
        int[] best = {jx, jz};
        double bestScore = -1e9;
        for (int k = 0; k < 7; k++) {
            // kept off the cell's edges, so a landmark never sits in the next cell's lap
            int x = GradientNoise.floor((i + 0.12 + 0.76 * unit(h ^ (40 + 2 * k))) * layer.cell);
            int z = GradientNoise.floor((j + 0.12 + 0.76 * unit(h ^ (41 + 2 * k))) * layer.cell);
            ColumnSample c = core.column(x, z);
            if (!clear(core, c, x, z)) continue;
            double ring = 0;
            for (int q = 0; q < 8; q++) {
                double ang = q * Math.PI / 4;
                ring += core.column(x + (int) (Math.cos(ang) * 44), z + (int) (Math.sin(ang) * 44)).height();
            }
            double score = (c.height() - ring / 8) - 9 * slope(core, x, z) + 2 * unit(h ^ (60 + k));
            if (score > bestScore) {
                bestScore = score;
                best = new int[] {x, z};
            }
        }
        SITED.put(key, best);
        return best;
    }

    /**
     * The god tree whose shade (x, z) is in, as a key for naming it ({@code world/PlaceNames}), or 0.
     */
    public static long godTreeAt(AtlasCore core, int x, int z) {
        Layer layer = Layer.GOD;
        int ci = GradientNoise.floor(x / layer.cell), cj = GradientNoise.floor(z / layer.cell);
        for (int j = cj - 1; j <= cj + 1; j++) {
            for (int i = ci - 1; i <= ci + 1; i++) {
                long h = GradientNoise.hash(SEED ^ layer.salt, i, layer.salt, j);
                int[] at = anchor(layer, core, i, j, h);
                double d = Math.hypot(at[0] - x, at[1] - z);
                if (d > 70) continue;
                ColumnSample c = core.column(at[0], at[1]);
                if (!clear(core, c, at[0], at[1])) continue;
                Asset a = choose(layer, core, c, at[0], at[1], h);
                if (a != null && d < Math.max(20, a.reach() * 0.5) && !wet(core, a, at[0], at[1])) return (GradientNoise.mix(h) & ~7L) | 3;
            }
        }
        return 0;
    }

    /**
     * The ground under a god tree: a tree that old has made its own floor. Podzol and moss in the
     * deep shade near the trunk, ferns and mushrooms on it, and at the edge of the shade a ring of
     * flowers (petals under a blossoming tree, mushrooms under a bare one): a fairy ring a hundred
     * blocks round, which is how you know from the ground that you are standing under something.
     */
    private static boolean garden(WorldGenLevel level, AtlasCore core, Asset a, int px, int pz, int x0, int z0, long h) {
        double shade = a.reach() * 0.42, ring = a.reach() * 0.5;
        if (px + ring + 3 < x0 || px - ring - 3 > x0 + 15 || pz + ring + 3 < z0 || pz - ring - 3 > z0 + 15) return false;
        boolean blossom = a.has("blossom"), bare = a.has("bare");
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        boolean placed = false;
        for (int z = z0; z < z0 + 16; z++) {
            for (int x = x0; x < x0 + 16; x++) {
                double d = Math.hypot(x - px, z - pz);
                // the ring wavers a little, as a real one does
                double edge = ring + 2.2 * GradientNoise.noise2(h, x / 14.0, z / 14.0);
                boolean onRing = Math.abs(d - edge) < 1.1;
                if (d > shade && !onRing) continue;
                ColumnSample c = core.column(x, z);
                if (!Double.isNaN(c.water()) || c.nearWater()) continue;
                int y = Integer.MIN_VALUE;
                for (int yy = c.surfaceY() + 2; yy >= c.surfaceY() - 3; yy--) {
                    BlockState g = level.getBlockState(pos.set(x, yy, z));
                    // (any soil: a taiga's floor is podzol and coarse dirt, and the first version, which
                    // looked for grass alone, left the ring nearly empty there)
                    if (g.is(Blocks.GRASS_BLOCK) || g.is(Blocks.PODZOL) || g.is(Blocks.COARSE_DIRT) || g.is(Blocks.DIRT) || g.is(Blocks.MOSS_BLOCK)) {
                        y = yy;
                        break;
                    }
                }
                if (y == Integer.MIN_VALUE) continue;
                BlockState above = level.getBlockState(pos.set(x, y + 1, z));
                boolean open = above.isAir() || above.canBeReplaced() && above.getFluidState().isEmpty();
                long hh = GradientNoise.hash(h, x, 0, z);
                double roll = unit(hh);
                if (onRing) {
                    if (!open || roll > 0.85) continue;
                    BlockState flower = blossom ? Blocks.PINK_PETALS.defaultBlockState()
                            : bare ? (roll < 0.35 ? Blocks.BROWN_MUSHROOM : Blocks.DEAD_BUSH).defaultBlockState()
                            : (roll < 0.3 ? Blocks.RED_MUSHROOM : roll < 0.5 ? Blocks.BROWN_MUSHROOM : Blocks.FERN).defaultBlockState();
                    if (flower.is(Blocks.DEAD_BUSH)) continue; // on grass it would not stand
                    level.setBlock(pos.set(x, y + 1, z), flower, 2);
                    placed = true;
                    continue;
                }
                double deep = 1 - d / shade; // 1 at the trunk
                double patch = GradientNoise.noise2(h ^ 5, x / 6.0, z / 6.0);
                if (patch > 0.35 - 0.5 * deep) {
                    boolean moss = !bare && GradientNoise.noise2(h ^ 6, x / 9.0, z / 9.0) > 0.2;
                    level.setBlock(pos.set(x, y, z), (moss ? Blocks.MOSS_BLOCK : Blocks.PODZOL).defaultBlockState(), 2);
                    placed = true;
                    if (open && !bare) {
                        if (roll < 0.16) level.setBlock(pos.set(x, y + 1, z), Blocks.FERN.defaultBlockState(), 2);
                        else if (roll < 0.2) level.setBlock(pos.set(x, y + 1, z), (roll < 0.18 ? Blocks.BROWN_MUSHROOM : Blocks.RED_MUSHROOM).defaultBlockState(), 2);
                        else if (moss && roll < 0.45) level.setBlock(pos.set(x, y + 1, z), Blocks.MOSS_CARPET.defaultBlockState(), 2);
                    }
                }
            }
        }
        return placed;
    }

    private static double unit(long h) {
        return (GradientNoise.mix(h) >>> 11) * 0x1.0p-53;
    }

    private static double slope(AtlasCore core, int x, int z) {
        return Math.max(Math.abs(core.column(x + 2, z).height() - core.column(x - 2, z).height()),
                Math.abs(core.column(x, z + 2).height() - core.column(x, z - 2).height())) / 4;
    }

    /** Dry ordinary ground: not water, a low coast, a meso site, wind-carved rock, a plateau wall or a set piece's own surface works. */
    private static boolean clear(AtlasCore core, ColumnSample c, int x, int z) {
        if (!Double.isNaN(c.water()) || c.nearWater() || c.height() < AtlasCore.SEA_LEVEL + 2) return false;
        if (c.site() != null || (c.micro() != null && c.micro().present()) || c.rock() > 0 || c.wall() != null) return false;
        if (c.shore() != null && (c.shore().stack() || c.shore().feature() != null
                || c.height() < AtlasCore.SEA_LEVEL + 14 && c.shore().distance() < Math.max(c.shore().beachWidth(), 0) + 12)) return false;
        return !core.setPieces().nearTree(x, z) && !core.setPieces().nearWell(x, z);
    }

    private static Country country(BiomeKind b) {
        return switch (b) {
            case SILVERWOOD -> Country.SILVER;
            case FOREST, FLOWER_FOREST, WOODED_BADLANDS, WINDSWEPT_FOREST, GLIMMER_VALE, CLOUD_FOREST -> Country.WOODS;
            case BIRCH_FOREST, OLD_GROWTH_BIRCH_FOREST -> Country.BIRCH;
            case DARK_FOREST -> Country.DARK;
            case TAIGA, OLD_GROWTH_SPRUCE_TAIGA, OLD_GROWTH_PINE_TAIGA, GROVE -> Country.TAIGA;
            case SNOWY_TAIGA, SNOWY_PLAINS, SNOWY_SLOPES, ICE_SPIKES -> Country.SNOWY;
            case JUNGLE, SPARSE_JUNGLE, BAMBOO_JUNGLE -> Country.JUNGLE;
            case SWAMP, MANGROVE_SWAMP -> Country.SWAMP;
            case SAVANNA, SAVANNA_PLATEAU, WINDSWEPT_SAVANNA -> Country.SAVANNA;
            case PLAINS, SUNFLOWER_PLAINS, MEADOW, WINDSWEPT_HILLS, WINDSWEPT_GRAVELLY_HILLS -> Country.OPEN;
            case CHERRY_GROVE -> Country.CHERRY;
            case DESERT -> Country.DRY;
            case RED_DESERT, BADLANDS, ERODED_BADLANDS -> Country.RED;
            case STONY_PEAKS, JAGGED_PEAKS, FROZEN_PEAKS -> Country.ALPINE;
            default -> Country.NONE;
        };
    }

    /** A regional field, about 0..1: whole districts of one kind of formation, a kilometre or so across. */
    private static double district(int salt, int x, int z, double scale) {
        return 0.5 + 0.8 * GradientNoise.noise2(SEED ^ (0x100 + salt), x / scale, z / scale)
                + 0.25 * GradientNoise.noise2(SEED ^ (0x200 + salt), x / (scale * 0.3), z / (scale * 0.3));
    }

    private static @Nullable Asset pick(Group g, String tags, long h) {
        Asset[] pool = g.with(tags);
        return pool.length == 0 ? null : pool[(int) Long.remainderUnsigned(GradientNoise.mix(h ^ 0x51CE), pool.length)];
    }

    /** What stands at this anchor, or null: the asset is chosen by the country, the district and the anchor's hash. */
    private static @Nullable Asset choose(Layer layer, AtlasCore core, ColumnSample c, int x, int z, long h) {
        Country country = country(c.biome());
        if (country == Country.NONE) return null;
        Group g = AssetLibrary.group(layer.file);
        double roll = unit(h), sl = slope(core, x, z);
        switch (layer) {
            case TREES -> {
                if (sl > 1.2) return null;
                double density;
                String[] kinds;
                // Kinds are shapes here, not species: the tree is re-skinned in the country's own wood and
                // leaves when it is built (as the rock formations are), so a broadleaf shape serves any
                // broadleaf wood. Conifer shapes stay in conifer country, jungle shapes in the jungle.
                // (Trees with planks, wood slabs or stairs in them are not in the pack file at all: the
                // user, 2026-11-10. That took most of the pack's plain oaks, hence shapes over species.)
                String[] broadleaf = {"leaf_oak", "leaf_oak", "leaf_birch"};
                switch (country) {
                    case WOODS -> { density = 0.34; kinds = broadleaf; }
                    case SILVER -> { density = 0.3; kinds = broadleaf; }
                    case BIRCH -> { density = 0.3; kinds = broadleaf; }
                    case DARK -> { density = 0.36; kinds = new String[] {"leaf_oak", "leaf_birch", "wood_jungle"}; }
                    case CHERRY -> { density = 0.16; kinds = broadleaf; }
                    case TAIGA -> { density = 0.34; kinds = new String[] {"leaf_spruce"}; }
                    case SNOWY -> {
                        if (c.biome() != BiomeKind.SNOWY_TAIGA) return null;
                        density = 0.3;
                        kinds = new String[] {"leaf_spruce"};
                    }
                    case JUNGLE -> { density = c.biome() == BiomeKind.SPARSE_JUNGLE ? 0.14 : 0.4; kinds = new String[] {"wood_jungle", "wood_jungle", "leaf_jungle"}; }
                    case SWAMP -> { density = 0.16; kinds = new String[] {"leaf_oak", "wood_jungle"}; }
                    case SAVANNA -> { density = 0.07; kinds = new String[] {"wood_acacia"}; }
                    case OPEN -> { density = 0.012; kinds = broadleaf; }
                    case RED, DRY -> { density = 0.006; kinds = new String[] {"leaf_bare"}; }
                    default -> { return null; }
                }
                // Woods are thick in places and open in others, at a scale of a few hundred blocks.
                density *= 0.55 + 0.9 * Math.max(0, Math.min(1, district(11, x, z, 260)));
                // Most are small or middling; the big ones stand apart.
                double s = unit(h ^ 3);
                // (weighted to the big ones: the game's own trees already fill the small sizes)
                String size = s < 0.2 ? "small" : s < 0.55 ? "medium" : s < 0.88 ? "large" : "giant";
                double thin = size.equals("large") ? 0.6 : size.equals("giant") ? 0.45 : 1;
                if (roll >= density * thin) return null;
                if (!size.equals("small") && sl > 0.8) size = "small";
                String kind = kinds[(int) (unit(h ^ 4) * kinds.length)];
                Asset a = pick(g, "tree," + kind + "," + size, h);
                return a != null ? a : pick(g, "tree," + kind, h);
            }
            case GOD -> {
                if (sl > 0.6 || c.mountain() > 0.45 || roll >= 0.3) return null; // about one in a square kilometre of gentle country
                String kind = switch (country) {
                    case SWAMP -> "willow,green";
                    case JUNGLE -> unit(h ^ 5) < 0.6 ? "jungle" : "oak,green";
                    case CHERRY -> unit(h ^ 5) < 0.5 ? "oak,blossom" : "bonsai,blossom";
                    case OPEN -> c.biome() == BiomeKind.MEADOW ? (unit(h ^ 5) < 0.5 ? "oak,blossom" : "bonsai,green") : "oak,green";
                    case SNOWY -> "oak,bare";
                    case RED, DRY, SAVANNA -> "dead";
                    case WOODS, BIRCH, DARK, TAIGA, SILVER -> c.humidity() > 0.4 && unit(h ^ 5) < 0.3 ? "willow,green" : "oak,green";
                    default -> null;
                };
                return kind == null ? null : pick(g, "godtree," + kind, h);
            }
            case SILVER -> {
                if (country != Country.SILVER || sl > 0.7 || c.mountain() > 0.45 || roll >= 0.55) return null;
                return pick(g, "godtree,oak,green", h);
            }
            case OUTCROP -> {
                if (sl > 1.6) return null;
                double stony = district(3, x, z, 420);
                if (roll >= 0.1 + 0.55 * Math.max(0, stony - 0.5)) return null;
                String dress = switch (country) {
                    case DRY, RED -> "desert";
                    case SNOWY -> "snowy";
                    case TAIGA -> "taiga";
                    case SAVANNA -> "savanna";
                    case JUNGLE, SWAMP -> "jungle";
                    case CHERRY -> "cherry";
                    case WOODS, BIRCH, DARK -> "forest";
                    case OPEN -> unit(h ^ 6) < 0.5 ? "plains" : "grass";
                    default -> "bare";
                };
                return pick(g, "outcrop," + dress, h);
            }
            case ARCH -> {
                if (sl > 1.2 || roll >= 0.3) return null;
                String dress = switch (country) {
                    case DRY, RED -> "desert";
                    case SNOWY -> "snowy";
                    case TAIGA -> "taiga";
                    case SAVANNA -> "savanna";
                    case JUNGLE, SWAMP -> "jungle";
                    case WOODS, BIRCH, DARK, CHERRY -> "forest";
                    case OPEN -> unit(h ^ 6) < 0.5 ? "plains" : "grass";
                    default -> "bare";
                };
                return pick(g, "arch," + dress, h);
            }
            case SPIRE -> {
                double d = district(5, x, z, 900);
                if (d < 0.78 || sl > 2.2 || roll >= 0.25 + 0.9 * (d - 0.78)) return null;
                String colour = country == Country.RED ? "terracotta" : country == Country.DRY ? "red" : c.temperature() < -0.3 ? "white"
                        : district(15, x, z, 1500) > 0.5 ? "dark" : "stone";
                return pick(g, "spire," + colour, h);
            }
            case PILLAR -> {
                // Pillar-mountain country: warm, wet, wooded hills.
                if (country != Country.WOODS && country != Country.BIRCH && country != Country.DARK && country != Country.JUNGLE && country != Country.OPEN
                        && country != Country.CHERRY) return null;
                if (c.temperature() < -0.2 || c.humidity() < -0.15 || c.mountain() > 0.6) return null;
                double d = district(6, x, z, 1300);
                if (d < 0.8 || sl > 2.0 || roll >= 0.3 + 1.2 * (d - 0.8)) return null;
                return pick(g, "pillar," + (c.humidity() > 0.35 ? "overgrown" : c.humidity() > 0.05 ? "lush" : "plain"), h);
            }
            case FIN -> {
                if (country != Country.DRY && country != Country.RED) return null;
                double d = district(7, x, z, 600);
                if (d < 0.62 || sl > 1.5 || roll >= 0.3 + 0.9 * (d - 0.62)) return null;
                return pick(g, "fin," + (country == Country.RED && unit(h ^ 6) < 0.5 ? "terracotta" : "red"), h);
            }
            case SPIKES -> {
                if (country != Country.DRY && country != Country.RED && country != Country.ALPINE) return null;
                double d = district(8, x, z, 500);
                if (d < 0.6 || roll >= 0.25 + 0.9 * (d - 0.6)) return null;
                return pick(g, "spikes," + (country == Country.RED ? "terracotta" : country == Country.DRY ? "red" : c.temperature() < -0.3 ? "white" : "dark"), h);
            }
            case RING -> {
                // The great stone rings (floating arcs of rock, hung with vines) stand only in pillar
                // country, under the same sky as the pillar mountains: nowhere else are they believable.
                if (country != Country.WOODS && country != Country.BIRCH && country != Country.DARK && country != Country.JUNGLE && country != Country.OPEN
                        && country != Country.CHERRY) return null;
                if (c.humidity() < -0.15 || c.mountain() > 0.6) return null;
                if (district(6, x, z, 1300) < 0.8 || sl > 1.0 || roll >= 0.35 || c.temperature() < -0.2) return null;
                return pick(g, "ring," + (unit(h ^ 6) < 0.5 ? "stone" : "dark"), h);
            }
            default -> {
                return null;
            }
        }
    }

    /**
     * True if a wide asset would stand in or beside water: a river, lake or the sea under any of
     * eight points round its footprint. (An outcrop 180 long reaching a river's bank from an anchor
     * well clear of it opened the bank, and the river ran out down the hillside.)
     */
    private static boolean wet(AtlasCore core, Asset a, int px, int pz) {
        int r = a.reach();
        if (r < 8) return false;
        for (int k = 0; k < 8; k++) {
            double ang = k * Math.PI / 4;
            for (double f = 0.5; f <= 1.0; f += 0.5) {
                ColumnSample c = core.column(px + (int) (Math.cos(ang) * r * f), pz + (int) (Math.sin(ang) * r * f));
                if (!Double.isNaN(c.water()) || c.nearWater() || c.height() < AtlasCore.SEA_LEVEL + 1) return true;
            }
        }
        return false;
    }

    private static boolean reaches(Asset a, int px, int pz, int x0, int z0) {
        int r = a.reach() + 1;
        return px + r >= x0 && px - r <= x0 + 15 && pz + r >= z0 && pz - r <= z0 + 15;
    }

    /** The ground the asset stands on: the anchor's, or for anything wide the lowest under its middle, so no side hangs in the air. */
    private static int ground(AtlasCore core, Asset a, int px, int pz, Layer layer) {
        int g = core.column(px, pz).surfaceY();
        if (layer == Layer.TREES) return g;
        int r = Math.max(2, a.reach() / 3);
        for (int k = 0; k < 4; k++) {
            g = Math.min(g, core.column(px + (k == 0 ? r : k == 1 ? -r : 0), pz + (k == 2 ? r : k == 3 ? -r : 0)).surfaceY());
        }
        return g;
    }

    /** Writes the part of the asset that lies in this chunk. */
    private static boolean build(WorldGenLevel level, AtlasCore core, Asset a, int px, int pz, int turn, Layer layer, int x0, int z0) {
        int gy = ground(core, a, px, pz, layer);
        int cx = a.sx / 2, cz = a.sz / 2;
        boolean rock = layer != Layer.TREES && layer != Layer.GOD && layer != Layer.SILVER;
        // A rock formation wears the country it stands in (its own trees and turf are re-skinned).
        Country land = country(core.column(px, pz).biome());
        Country skin = rock || layer == Layer.TREES || land == Country.SILVER ? land : Country.NONE;
        int minY = level.getMinBuildHeight() + 2, maxY = level.getMinBuildHeight() + level.getHeight() - 2;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        boolean placed = false;
        for (int wz = z0; wz < z0 + 16; wz++) {
            for (int wx = x0; wx < x0 + 16; wx++) {
                int dx = wx - px, dz = wz - pz, ax, az;
                switch (turn) {
                    case 1 -> { ax = dz + cx; az = -dx + cz; }
                    case 2 -> { ax = -dx + cx; az = -dz + cz; }
                    case 3 -> { ax = -dz + cx; az = dx + cz; }
                    default -> { ax = dx + cx; az = dz + cz; }
                }
                if (ax < 0 || az < 0 || ax >= a.sx || az >= a.sz) continue;
                int first = a.firstRun(ax, az), end = a.endRun(ax, az);
                if (first == end) continue;
                // Never in or right beside water: the banks and beds are the rivers' own.
                ColumnSample col = core.column(wx, wz);
                if (!Double.isNaN(col.water()) || col.nearWater()) continue;
                for (int i = first; i < end; i++) {
                    BlockState state = skin == Country.NONE ? a.runState(i, turn) : reskin(a.runState(i, turn), skin);
                    if (state.isAir()) continue;
                    int y0 = gy + 1 + a.runY(i) - a.baseY;
                    for (int k = 0, n = a.runLength(i); k < n; k++) {
                        int wy = y0 + k;
                        if (wy < minY || wy > maxY) continue;
                        placed |= put(level, pos.set(wx, wy, wz), state, rock, wy <= gy);
                    }
                }
                // Stood on uneven ground: the lowest block carried down to it, so nothing floats.
                int low = a.runY(first);
                if (low <= a.baseY + (rock ? 6 : 1)) {
                    BlockState foot = skin == Country.NONE ? a.runState(first, turn) : reskin(a.runState(first, turn), skin);
                    if (foot.isAir() || foot.is(BlockTags.LEAVES) || !foot.isSolid()) continue;
                    BlockState fill = foot; // the same block: stone under stone, trunk under trunk
                    int top = gy + a.runY(first) - a.baseY;
                    for (int wy = top, k = 0; k < (rock ? 28 : 10) && wy > minY; wy--, k++) {
                        BlockState here = level.getBlockState(pos.set(wx, wy, wz));
                        if (!here.isAir() && !here.canBeReplaced() && !here.is(BlockTags.LEAVES)) break;
                        if (!here.getFluidState().isEmpty()) break;
                        level.setBlock(pos, fill, 2);
                        placed = true;
                    }
                }
            }
        }
        return placed;
    }

    private static final java.util.Map<Country, java.util.Map<BlockState, BlockState>> SKINS = new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * A formation's block as the local country has it (the user, 2026-11-10: the pillars "don't biome
     * match": they came with jungle trees wherever they stood). Its trees become the country's own
     * (leaves and wood: acacia in savanna, spruce in the taiga and the snow, birch, dark oak, cherry),
     * its turf becomes sand in the desert and red sand in the badlands where nothing would grow, and
     * leaves there are dropped. Jungle keeps the builder's own. Remembered per country and block.
     */
    private static BlockState reskin(BlockState state, Country country) {
        if (country == Country.JUNGLE) return state;
        return SKINS.computeIfAbsent(country, c -> new java.util.concurrent.ConcurrentHashMap<>()).computeIfAbsent(state, s -> skinOf(s, country));
    }

    private static BlockState skinOf(BlockState s, Country country) {
        boolean dry = country == Country.DRY || country == Country.RED;
        String wood = switch (country) {
            case BIRCH, SILVER -> "birch";
            case DARK -> "dark_oak";
            case TAIGA, SNOWY, ALPINE -> "spruce";
            case SAVANNA, DRY, RED -> "acacia";
            case CHERRY -> "cherry";
            default -> "oak";
        };
        net.minecraft.resources.ResourceLocation id = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(s.getBlock());
        String path = id.getPath();
        if (s.is(BlockTags.LEAVES)) {
            if (dry || country == Country.ALPINE) return Blocks.AIR.defaultBlockState();
            if (path.contains("azalea")) return s; // blossom stays blossom
            // The Silverwood: birch bark under oak leaves, which its biome colours gold.
            BlockState leaves = block((country == Country.SILVER ? "oak" : wood) + "_leaves");
            return leaves.hasProperty(net.minecraft.world.level.block.LeavesBlock.PERSISTENT)
                    ? leaves.setValue(net.minecraft.world.level.block.LeavesBlock.PERSISTENT, true) : leaves;
        }
        if (s.is(BlockTags.LOGS) && id.getNamespace().equals("minecraft") && (path.endsWith("_log") || path.endsWith("_wood")) && !path.startsWith("stripped")) {
            BlockState log = block(wood + (path.endsWith("_wood") ? "_wood" : "_log"));
            return log.hasProperty(net.minecraft.world.level.block.RotatedPillarBlock.AXIS) && s.hasProperty(net.minecraft.world.level.block.RotatedPillarBlock.AXIS)
                    ? log.setValue(net.minecraft.world.level.block.RotatedPillarBlock.AXIS, s.getValue(net.minecraft.world.level.block.RotatedPillarBlock.AXIS)) : log;
        }
        if (dry) {
            if (s.is(Blocks.GRASS_BLOCK) || s.is(Blocks.PODZOL) || s.is(Blocks.MOSS_BLOCK) || s.is(Blocks.DIRT) || s.is(Blocks.COARSE_DIRT) || s.is(Blocks.ROOTED_DIRT)) {
                return (country == Country.RED ? Blocks.RED_SAND : Blocks.SAND).defaultBlockState();
            }
            if (s.is(Blocks.SHORT_GRASS) || s.is(Blocks.FERN) || s.is(Palette.bush())) return Palette.shortDryGrass().defaultBlockState();
            if (s.is(Blocks.TALL_GRASS) || s.is(Blocks.LARGE_FERN) || s.is(Blocks.MOSS_CARPET) || s.is(Blocks.VINE) || s.is(BlockTags.FLOWERS)) return Blocks.AIR.defaultBlockState();
        }
        return s;
    }

    private static BlockState block(String path) {
        return net.minecraft.core.registries.BuiltInRegistries.BLOCK.get(net.minecraft.resources.ResourceLocation.withDefaultNamespace(path)).defaultBlockState();
    }

    /**
     * One block of an asset into the world. Open air, plants and leaves always give way. Rock
     * formations also take the ground's place (they are the ground); a tree does only below
     * ground level, where its roots go. Water and other set blocks are left alone.
     */
    private static boolean put(WorldGenLevel level, BlockPos pos, BlockState state, boolean rock, boolean underground) {
        BlockState here = level.getBlockState(pos);
        if (!here.getFluidState().isEmpty() || here.is(Blocks.BEDROCK)) return false;
        if (!here.isAir() && !here.canBeReplaced() && !here.is(BlockTags.LEAVES)) {
            if (!rock && !underground) return false;
            if (!ground(here)) return false;
        }
        if (state.is(BlockTags.LEAVES) && here.is(BlockTags.LOGS)) return false;
        level.setBlock(pos, state, 2);
        return true;
    }

    private static boolean ground(BlockState s) {
        return s.is(BlockTags.DIRT) || s.is(BlockTags.BASE_STONE_OVERWORLD) || s.is(BlockTags.SAND) || s.is(BlockTags.TERRACOTTA) || s.is(Blocks.GRAVEL)
                || s.is(Blocks.SANDSTONE) || s.is(Blocks.RED_SANDSTONE) || s.is(Blocks.SNOW_BLOCK) || s.is(Blocks.COBBLESTONE) || s.is(Blocks.MOSSY_COBBLESTONE)
                || s.is(Blocks.CALCITE) || s.is(Blocks.CLAY) || s.is(BlockTags.LOGS) || s.is(Blocks.MOSS_BLOCK);
    }
}
