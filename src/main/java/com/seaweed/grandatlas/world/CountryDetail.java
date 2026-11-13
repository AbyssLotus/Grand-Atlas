package com.seaweed.grandatlas.world;

import com.mojang.serialization.MapCodec;
import com.seaweed.grandatlas.core.AtlasCore;
import com.seaweed.grandatlas.core.BiomeKind;
import com.seaweed.grandatlas.core.ColumnSample;
import com.seaweed.grandatlas.core.noise.GradientNoise;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.feature.Feature;

/**
 * {@code grandatlas:country_detail}: small detail on the ordinary ground between the meso sites
 * (the surface review, 2026-11-09: boulders, logs and ground cover existed only inside sites, and
 * the country between them was a clean sheet of grass, sand or rock; the user: "not enough small
 * level detail").
 * <ul>
 *   <li><b>Boulder fields</b>: boulders in the country's own stone (mossy in wet woods, sandstone
 *       in deserts, terracotta in badlands, grey rock in the cold and the mountains), thick in
 *       some fields a few hundred blocks across, a lone one here and there elsewhere.</li>
 *   <li><b>Rock gardens</b>: inside the stoniest fields, bedrock showing flush through the turf.</li>
 *   <li><b>Fallen trunks</b> in the woods, in the wood of the forest.</li>
 *   <li><b>Thickets</b> on grass: drifts of bushes, ferns and tall grass.</li>
 *   <li><b>Dry country</b>: tufts of dry grass and dead bushes in patches on sand and terracotta,
 *       and slabs of sandstone showing through the sand.</li>
 *   <li><b>Scree</b>: gravel and cobble in patches on level bare rock in the mountains.</li>
 * </ul>
 * Everything gathers by smooth fields (never per-block chance alone), so there are stony places
 * and clean ones. Nothing is placed in water, on coasts, on salt flats, on wind-carved rock, or
 * inside a meso site (those dress themselves: {@link MicroDetail}). Runs at local_modifications,
 * before trees and plants; every object is built whole from its own hash and each chunk writes
 * only its own blocks.
 */
public record CountryDetail() implements Feature {
    public static final MapCodec<CountryDetail> CODEC = MapCodec.unit(CountryDetail::new);
    private static final long SEED = 0xC0497D7A11L;

    private enum Ground { GRASS, SAND, RED_SAND, TERRACOTTA, STONE, OTHER }

    private enum Country { WOODS, WET_WOODS, COLD, OPEN, DRY, RED, ALPINE, NONE }

    @Override
    public MapCodec<CountryDetail> codec() {
        return CODEC;
    }

    @Override
    public boolean place(WorldGenLevel level, ChunkGenerator chunkGenerator, RandomSource random, BlockPos origin) {
        AtlasCore core = CoreHandle.coreOf(level.getLevel());
        if (core == null) return false;
        int x0 = origin.getX() & ~15, z0 = origin.getZ() & ~15;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        boolean placed = false;
        // What the ground is made of on a lattice round the chunk (8 apart, 12 past each side), to
        // find where sand country meets other country.
        int[] lattice = new int[36];
        ColumnSample[] latticeCol = new ColumnSample[36];
        boolean mixed = false;
        for (int j = 0; j < 6; j++) {
            for (int i = 0; i < 6; i++) {
                ColumnSample lc = core.column(x0 - 12 + 8 * i, z0 - 12 + 8 * j);
                latticeCol[j * 6 + i] = lc;
                lattice[j * 6 + i] = sandOf(lc);
                mixed |= lattice[j * 6 + i] >= 0 && lattice[0] >= 0 && lattice[j * 6 + i] != lattice[0];
            }
        }
        mixed |= lattice[0] < 0;
        for (int dz = 0; dz < 16; dz++) {
            for (int dx = 0; dx < 16; dx++) {
                int x = x0 + dx, z = z0 + dz;
                ColumnSample c = core.column(x, z);
                if (mixed && tongue(level, core, c, x, z, x0, z0, lattice, latticeCol, pos)) {
                    placed = true;
                    continue;
                }
                if (!Double.isNaN(c.water())) {
                    placed |= freshwater(level, c, x, z, pos);
                    continue;
                }
                if (!clear(c)) {
                    if (coast(c)) placed |= duneGrass(level, c, x, z, pos);
                    continue;
                }
                placed |= reeds(level, c, x, z, pos);
                placed |= cover(level, core, c, x, z, pos);
            }
        }
        placed |= objects(level, core, x0, z0, 9, 1, 4, true);
        placed |= objects(level, core, x0, z0, 19, 2, 9, false);
        return placed;
    }

    /** Ordinary dry ground: not water, coast, salt flat, wind-carved rock, a meso site or a micro feature. */
    private static boolean clear(ColumnSample c) {
        if (!Double.isNaN(c.water()) || c.height() < AtlasCore.SEA_LEVEL + 1) return false;
        // The coast dresses itself: the beach, the cliff and a margin behind them. (A shore is set far
        // inland too, with its distance from the sea, so the test is that distance, not its presence.)
        if (coast(c)) return false;
        if (c.site() != null || (c.micro() != null && c.micro().present()) || c.rock() > 0 || c.wall() != null) return false;
        return country(c.biome()) != Country.NONE;
    }

    /**
     * Life in the lakes and rivers (their beds were bare: vanilla grows water plants only in its own
     * river and ocean biomes): meadows of seagrass on the bed, and lily pads in drifts on still
     * shallow water. Not in a meso site's water (ponds dress themselves), the sea, or frozen country.
     */
    private static boolean freshwater(WorldGenLevel level, ColumnSample c, int x, int z, BlockPos.MutableBlockPos pos) {
        if (c.site() != null || (c.micro() != null && c.micro().present()) || c.temperature() < -0.45) return false;
        if (c.biome().name().endsWith("OCEAN") || c.spill()) return false;
        int top = (int) Math.floor(c.water());
        BlockState surface = level.getBlockState(pos.set(x, top, z));
        if (!surface.is(RiverWater.BLOCK.get())) return false;
        int bed = top;
        while (bed > top - 24 && level.getBlockState(pos.set(x, bed, z)).is(RiverWater.BLOCK.get())) bed--;
        BlockState floor = level.getBlockState(pos.set(x, bed, z));
        if (!floor.isSolid() || !floor.getFluidState().isEmpty()) return false;
        int depth = top - bed;
        long h = GradientNoise.hash(SEED ^ 0x60, x, 0, z);
        double roll = unit(h);
        boolean still = c.flowX() == 0 && c.flowZ() == 0;
        boolean placed = false;
        double weed = GradientNoise.noise2(SEED ^ 0x61, x / 17.0, z / 17.0) + 0.3 * GradientNoise.noise2(SEED ^ 0x62, x / 6.0, z / 6.0);
        if (weed > 0.15 && roll < 0.15 + 0.8 * (weed - 0.15)) {
            if (depth >= 2 && unit(h ^ 1) < 0.3) {
                level.setBlock(pos.set(x, bed + 1, z), Blocks.TALL_SEAGRASS.defaultBlockState().setValue(net.minecraft.world.level.block.TallSeagrassBlock.HALF, DoubleBlockHalf.LOWER), 2);
                level.setBlock(pos.set(x, bed + 2, z), Blocks.TALL_SEAGRASS.defaultBlockState().setValue(net.minecraft.world.level.block.TallSeagrassBlock.HALF, DoubleBlockHalf.UPPER), 2);
            } else {
                level.setBlock(pos.set(x, bed + 1, z), Blocks.SEAGRASS.defaultBlockState(), 2);
            }
            placed = true;
        }
        if (still && depth <= 5 && c.temperature() > -0.2 && level.getBlockState(pos.set(x, top + 1, z)).isAir()
                && level.getBlockState(pos.set(x, top, z)).getFluidState().isSource()) {
            double pads = GradientNoise.noise2(SEED ^ 0x63, x / 9.0, z / 9.0) + 0.4 * GradientNoise.noise2(SEED ^ 0x64, x / 40.0, z / 40.0);
            if (pads > 0.3 && unit(h ^ 2) < 0.2 + 0.7 * (pads - 0.3)) {
                level.setBlock(pos.set(x, top + 1, z), Blocks.LILY_PAD.defaultBlockState(), 2);
                placed = true;
            }
        }
        return placed;
    }

    /** Reeds: sugar cane in clumps on the banks, where the ground is level with the water beside it. */
    private static boolean reeds(WorldGenLevel level, ColumnSample c, int x, int z, BlockPos.MutableBlockPos pos) {
        if (!c.nearWater() || c.temperature() < -0.2) return false;
        if (GradientNoise.noise2(SEED ^ 0x65, x / 7.0, z / 7.0) + 0.4 * GradientNoise.noise2(SEED ^ 0x66, x / 33.0, z / 33.0) < 0.22) return false;
        int y = top(level, c, x, z, pos);
        if (y == Integer.MIN_VALUE) return false;
        BlockState under = level.getBlockState(pos.set(x, y, z));
        if (!under.is(Blocks.GRASS_BLOCK) && !under.is(Blocks.SAND) && !under.is(Blocks.DIRT) && !under.is(Blocks.COARSE_DIRT) && !under.is(Blocks.PODZOL)) return false;
        boolean wet = false;
        for (Direction d : Direction.Plane.HORIZONTAL) {
            wet |= level.getBlockState(pos.set(x + d.getStepX(), y, z + d.getStepZ())).getFluidState().is(net.minecraft.tags.FluidTags.WATER);
        }
        if (!wet) return false;
        long h = GradientNoise.hash(SEED ^ 0x67, x, 0, z);
        if (unit(h) > 0.6) return false;
        int n = 1 + (int) (unit(h ^ 1) * 3);
        for (int k = 1; k <= n; k++) {
            if (!level.getBlockState(pos.set(x, y + k, z)).isAir()) break;
            level.setBlock(pos, Blocks.SUGAR_CANE.defaultBlockState(), 2);
        }
        return true;
    }

    /** The ground's stuff by its country: 1 sand, 2 red sand, 0 anything else on land, -1 water (ignored). */
    private static int sandOf(ColumnSample c) {
        if (!Double.isNaN(c.water()) || c.height() < AtlasCore.SEA_LEVEL + 1) return -1;
        return switch (c.biome()) {
            case DESERT -> 1;
            case RED_DESERT -> 2;
            default -> 0;
        };
    }

    /**
     * Where sand country meets other country the two met on a dead-straight line, sand against
     * rock or grass (the surface review: at 1280 1280, a dune sea against a mountain). Within about
     * 22 blocks of such an edge each side reaches into the other in tongues: sand blown up over the
     * rock and the turf, bare earth, gravel and rock running out into the sand. The tongues come
     * from smooth noise, thick at the edge and thinning away from it. True if this column changed.
     */
    private static boolean tongue(WorldGenLevel level, AtlasCore core, ColumnSample c, int x, int z, int x0, int z0, int[] lattice,
                                  ColumnSample[] latticeCol, BlockPos.MutableBlockPos pos) {
        int own = sandOf(c);
        if (own < 0 || c.site() != null || (c.micro() != null && c.micro().present()) || c.rock() > 0 || c.wall() != null || coast(c)) return false;
        double best = Double.MAX_VALUE;
        int other = -1, otherX = 0, otherZ = 0;
        ColumnSample otherCol = null;
        for (int j = 0; j < 6; j++) {
            for (int i = 0; i < 6; i++) {
                int m = lattice[j * 6 + i];
                if (m < 0 || m == own) continue;
                double d = Math.hypot(x0 - 12 + 8 * i - x, z0 - 12 + 8 * j - z);
                if (d < best) {
                    best = d;
                    other = m;
                    otherCol = latticeCol[j * 6 + i];
                    otherX = x0 - 12 + 8 * i;
                    otherZ = z0 - 12 + 8 * j;
                }
            }
        }
        if (other < 0 || best > 22) return false;
        double n = 0.5 + 0.9 * GradientNoise.noise2(SEED ^ 0x50, x / 10.0, z / 10.0) + 0.5 * GradientNoise.noise2(SEED ^ 0x51, x / 27.0, z / 27.0);
        if (n < 0.4 + 0.45 * best / 22) return false;
        if (slope(core, x, z) > 1.5) return false; // sand doesn't lie on a face
        int y = top(level, c, x, z, pos);
        if (y == Integer.MIN_VALUE) {
            // a plant may be in the way of the air test: look under it
            for (int yy = c.surfaceY() + 3; yy >= c.surfaceY() - 5 && y == Integer.MIN_VALUE; yy--) {
                BlockState s = level.getBlockState(pos.set(x, yy, z));
                if (!s.isAir() && !s.canBeReplaced() && s.getFluidState().isEmpty()) y = yy;
            }
            if (y == Integer.MIN_VALUE) return false;
        }
        BlockState under = level.getBlockState(pos.set(x, y, z));
        Ground g = ground(under);
        Block put;
        if (other == 1 || other == 2) {
            if (g != Ground.GRASS && g != Ground.STONE && g != Ground.SAND && g != Ground.RED_SAND && !under.is(Blocks.DIRT) && !under.is(Blocks.COARSE_DIRT)
                    && !under.is(Blocks.GRAVEL)) return false;
            put = other == 1 ? Blocks.SAND : Blocks.RED_SAND;
        } else {
            if (g != Ground.SAND && g != Ground.RED_SAND) return false;
            // What the other side's ground really is, a little way in from the edge (rock under a
            // mountain's desert biome is still rock: grass tongues against it looked wrong).
            double patch = GradientNoise.noise2(SEED ^ 0x52, x / 6.0, z / 6.0);
            Ground og = Ground.OTHER;
            BlockPos.MutableBlockPos q = new BlockPos.MutableBlockPos();
            for (int yy = otherCol.surfaceY() + 3; yy >= otherCol.surfaceY() - 5; yy--) {
                BlockState s = level.getBlockState(q.set(otherX, yy, otherZ));
                if (s.isAir() || s.canBeReplaced()) continue;
                og = ground(s);
                break;
            }
            put = og == Ground.GRASS ? (patch > 0.15 ? Blocks.GRASS_BLOCK : Blocks.COARSE_DIRT)
                    : og == Ground.TERRACOTTA ? Blocks.TERRACOTTA : patch > 0.1 ? Blocks.GRAVEL : Blocks.STONE;
        }
        if (under.is(put)) return false;
        if ((put == Blocks.SAND || put == Blocks.RED_SAND || put == Blocks.GRAVEL) && level.getBlockState(pos.set(x, y - 1, z)).isAir()) return false; // it would fall
        level.setBlock(pos.set(x, y, z), put.defaultBlockState(), 2);
        BlockState above = level.getBlockState(pos.set(x, y + 1, z));
        if (!above.isAir() && above.canBeReplaced() && above.getFluidState().isEmpty()) level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
        return true;
    }

    private static boolean coast(ColumnSample c) {
        if (c.shore() == null) return false;
        if (c.shore().stack() || c.shore().feature() != null) return true;
        // Low ground near the shore line. (High ground can carry a shore distance too: a dune sea on a
        // sunk basin floor did, a hundred blocks up, and the whole of it was left bare.)
        return c.height() < AtlasCore.SEA_LEVEL + 14 && c.shore().distance() < Math.max(c.shore().beachWidth(), 0) + 12;
    }

    /**
     * Dune grass: tufts of dry grass in drifts on a beach's dry sand, well above the water (a dune
     * beach is up to a hundred blocks of sand, and it was bare).
     */
    private static boolean duneGrass(WorldGenLevel level, ColumnSample c, int x, int z, BlockPos.MutableBlockPos pos) {
        if (c.shore() == null || c.shore().stack() || c.shore().feature() != null) return false;
        if (!Double.isNaN(c.water()) || c.height() < AtlasCore.SEA_LEVEL + 2) return false;
        if (c.site() != null || (c.micro() != null && c.micro().present())) return false;
        int y = top(level, c, x, z, pos);
        if (y == Integer.MIN_VALUE) return false;
        Ground g = ground(level.getBlockState(pos.set(x, y, z)));
        if (g != Ground.SAND && g != Ground.RED_SAND) return false;
        return dry(level, x, y, z, unit(GradientNoise.hash(SEED ^ 0x20, x, 0, z)), 0.2, pos);
    }

    private static Country country(BiomeKind b) {
        return switch (b) {
            case FOREST, FLOWER_FOREST, BIRCH_FOREST, OLD_GROWTH_BIRCH_FOREST, CHERRY_GROVE, WOODED_BADLANDS, WINDSWEPT_FOREST -> Country.WOODS;
            case DARK_FOREST, PALE_GARDEN, JUNGLE, SPARSE_JUNGLE, BAMBOO_JUNGLE, SWAMP, MANGROVE_SWAMP -> Country.WET_WOODS;
            case TAIGA, OLD_GROWTH_SPRUCE_TAIGA, OLD_GROWTH_PINE_TAIGA, SNOWY_TAIGA, GROVE, SNOWY_PLAINS, SNOWY_SLOPES, ICE_SPIKES -> Country.COLD;
            case PLAINS, SUNFLOWER_PLAINS, MEADOW, SAVANNA, SAVANNA_PLATEAU, WINDSWEPT_SAVANNA, WINDSWEPT_HILLS, WINDSWEPT_GRAVELLY_HILLS -> Country.OPEN;
            case DESERT -> Country.DRY;
            case RED_DESERT, BADLANDS, ERODED_BADLANDS -> Country.RED;
            case STONY_PEAKS, JAGGED_PEAKS, FROZEN_PEAKS -> Country.ALPINE;
            default -> Country.NONE;
        };
    }

    /** How stony the country is here, 0..1: boulder fields a few hundred blocks across, broken at a finer scale. */
    private static double stony(int x, int z) {
        double f = 0.5 + 0.34 * GradientNoise.noise2(SEED, x / 190.0, z / 190.0) + 0.22 * GradientNoise.noise2(SEED ^ 1, x / 57.0, z / 57.0);
        return Math.max(0, Math.min(1, f));
    }

    private static double unit(long h) {
        return (GradientNoise.mix(h) >>> 11) * 0x1.0p-53;
    }

    private static double slope(AtlasCore core, int x, int z) {
        return Math.max(Math.abs(core.column(x + 1, z).height() - core.column(x - 1, z).height()),
                Math.abs(core.column(x, z + 1).height() - core.column(x, z - 1).height())) / 2;
    }

    /** The column's top solid block near the core's surface, with open air over it; or MIN_VALUE. */
    private static int top(WorldGenLevel level, ColumnSample c, int x, int z, BlockPos.MutableBlockPos pos) {
        for (int y = c.surfaceY() + 3; y >= c.surfaceY() - 5; y--) {
            BlockState s = level.getBlockState(pos.set(x, y, z));
            if (s.isAir() || s.canBeReplaced()) continue;
            if (!s.getFluidState().isEmpty()) return Integer.MIN_VALUE;
            return level.getBlockState(pos.set(x, y + 1, z)).isAir() ? y : Integer.MIN_VALUE;
        }
        return Integer.MIN_VALUE;
    }

    private static Ground ground(BlockState s) {
        if (s.is(Blocks.GRASS_BLOCK) || s.is(Blocks.PODZOL)) return Ground.GRASS;
        if (s.is(Blocks.SAND)) return Ground.SAND;
        if (s.is(Blocks.RED_SAND)) return Ground.RED_SAND;
        if (s.is(net.minecraft.tags.BlockTags.TERRACOTTA)) return Ground.TERRACOTTA;
        if (s.is(Blocks.STONE) || s.is(Blocks.ANDESITE)) return Ground.STONE;
        return Ground.OTHER;
    }

    // ---- ground cover, per column

    private static boolean cover(WorldGenLevel level, AtlasCore core, ColumnSample c, int x, int z, BlockPos.MutableBlockPos pos) {
        int y = top(level, c, x, z, pos);
        if (y == Integer.MIN_VALUE) return false;
        BlockState under = level.getBlockState(pos.set(x, y, z));
        Country country = country(c.biome());
        double f = stony(x, z);
        double roll = unit(GradientNoise.hash(SEED ^ 0x20, x, 0, z));
        switch (ground(under)) {
            case GRASS -> {
                // Rock gardens: bedrock flush through the turf in the stoniest fields.
                if (f > 0.66 && GradientNoise.noise2(SEED ^ 0x21, x / 5.5, z / 5.5) > 0.42 + 1.2 * (0.9 - f)) {
                    double n = GradientNoise.noise2(SEED ^ 0x22, x / 4.0, z / 4.0);
                    Block rock = country == Country.WET_WOODS || country == Country.WOODS && n > 0.2 ? Blocks.MOSSY_COBBLESTONE
                            : n > 0.3 ? Blocks.ANDESITE : n < -0.35 ? Blocks.COBBLESTONE : Blocks.STONE;
                    level.setBlock(pos.set(x, y, z), rock.defaultBlockState(), 2);
                    return true;
                }
                // Thickets: drifts of bushes, ferns and tall grass.
                double thicket = GradientNoise.noise2(SEED ^ 0x23, x / 23.0, z / 23.0) + 0.3 * GradientNoise.noise2(SEED ^ 0x24, x / 7.0, z / 7.0);
                if (thicket < 0.2 || roll > 0.1 + 0.75 * (thicket - 0.2)) return false;
                double pick = unit(GradientNoise.hash(SEED ^ 0x25, x, 0, z));
                boolean cold = country == Country.COLD, wet = country == Country.WET_WOODS;
                if (pick < (cold ? 0.1 : 0.45)) {
                    level.setBlock(pos.set(x, y + 1, z), Blocks.BUSH.defaultBlockState(), 2);
                } else if (pick < (cold || wet ? 0.8 : 0.55)) {
                    if (pick < 0.62 || !level.getBlockState(pos.set(x, y + 2, z)).isAir()) level.setBlock(pos.set(x, y + 1, z), Blocks.FERN.defaultBlockState(), 2);
                    else tall(level, Blocks.LARGE_FERN, x, y + 1, z, pos);
                } else if (level.getBlockState(pos.set(x, y + 2, z)).isAir()) {
                    tall(level, Blocks.TALL_GRASS, x, y + 1, z, pos);
                } else {
                    level.setBlock(pos.set(x, y + 1, z), Blocks.SHORT_GRASS.defaultBlockState(), 2);
                }
                return true;
            }
            case SAND, RED_SAND -> {
                boolean red = ground(under) == Ground.RED_SAND;
                // Slabs of sandstone showing through the sand in the stonier fields.
                if (f > 0.6 && GradientNoise.noise2(SEED ^ 0x26, x / 6.5, z / 6.5) > 0.45 + 1.2 * (0.9 - f)) {
                    level.setBlock(pos.set(x, y, z), (red ? Blocks.RED_SANDSTONE : Blocks.SANDSTONE).defaultBlockState(), 2);
                    return true;
                }
                return dry(level, x, y, z, roll, 0.25, pos);
            }
            case TERRACOTTA -> {
                return dry(level, x, y, z, roll, 0.15, pos);
            }
            case STONE -> {
                // Scree on level bare rock in the mountains (steep rock keeps its face: SteepRock).
                // Moss over rock in wet woods, in patches, ferns and carpet on it.
                if ((country == Country.WET_WOODS || country == Country.WOODS || country == Country.COLD) && c.humidity() > 0
                        && GradientNoise.noise2(SEED ^ 0x2B, x / 8.0, z / 8.0) + 0.4 * GradientNoise.noise2(SEED ^ 0x2C, x / 25.0, z / 25.0) > 0.25) {
                    level.setBlock(pos.set(x, y, z), Blocks.MOSS_BLOCK.defaultBlockState(), 2);
                    if (roll < 0.12) level.setBlock(pos.set(x, y + 1, z), Blocks.FERN.defaultBlockState(), 2);
                    else if (roll < 0.4) level.setBlock(pos.set(x, y + 1, z), Blocks.MOSS_CARPET.defaultBlockState(), 2);
                    return true;
                }
                if (country != Country.ALPINE && c.mountain() < 0.35) return false;
                if (slope(core, x, z) > 0.9) return false;
                double scree = GradientNoise.noise2(SEED ^ 0x28, x / 10.0, z / 10.0) + 0.5 * (f - 0.5);
                if (scree < 0.3) return false;
                boolean firm = !level.getBlockState(pos.set(x, y - 1, z)).isAir();
                level.setBlock(pos.set(x, y, z), (scree > 0.5 && firm ? Blocks.GRAVEL : Blocks.COBBLESTONE).defaultBlockState(), 2);
                return true;
            }
            default -> {
                return false;
            }
        }
    }

    /** Dry tufts in patches: dry grass, short and tall, and dead bushes. */
    private static boolean dry(WorldGenLevel level, int x, int y, int z, double roll, double threshold, BlockPos.MutableBlockPos pos) {
        double tuft = GradientNoise.noise2(SEED ^ 0x27, x / 19.0, z / 19.0) + 0.3 * GradientNoise.noise2(SEED ^ 0x29, x / 6.0, z / 6.0);
        if (tuft < threshold || roll > Math.min(0.5, 0.15 + 1.0 * (tuft - threshold))) return false;
        double pick = unit(GradientNoise.hash(SEED ^ 0x2A, x, 0, z));
        level.setBlock(pos.set(x, y + 1, z), (pick < 0.25 ? Blocks.DEAD_BUSH : pick < 0.8 ? Blocks.SHORT_DRY_GRASS : Blocks.TALL_DRY_GRASS).defaultBlockState(), 2);
        return true;
    }

    private static void tall(WorldGenLevel level, Block plant, int x, int y, int z, BlockPos.MutableBlockPos pos) {
        level.setBlock(pos.set(x, y, z), plant.defaultBlockState().setValue(DoublePlantBlock.HALF, DoubleBlockHalf.LOWER), 2);
        level.setBlock(pos.set(x, y + 1, z), plant.defaultBlockState().setValue(DoublePlantBlock.HALF, DoubleBlockHalf.UPPER), 2);
    }

    // ---- boulders and fallen trunks, on jittered grids

    private static boolean objects(WorldGenLevel level, AtlasCore core, int x0, int z0, double cell, int salt, int margin, boolean boulders) {
        boolean placed = false;
        int i0 = GradientNoise.floor((x0 - margin) / cell), i1 = GradientNoise.floor((x0 + 15 + margin) / cell);
        int j0 = GradientNoise.floor((z0 - margin) / cell), j1 = GradientNoise.floor((z0 + 15 + margin) / cell);
        for (int j = j0; j <= j1; j++) {
            for (int i = i0; i <= i1; i++) {
                long h = GradientNoise.hash(SEED ^ (0x30 + salt), i, salt, j);
                double px = (i + unit(h ^ 1)) * cell, pz = (j + unit(h ^ 2)) * cell;
                int cx = GradientNoise.floor(px), cz = GradientNoise.floor(pz);
                ColumnSample c = core.column(cx, cz);
                if (!clear(c)) continue;
                Country country = country(c.biome());
                double f = stony(cx, cz), sl = slope(core, cx, cz);
                if (boulders) {
                    if (sl > (country == Country.ALPINE ? 2.0 : 1.2)) continue;
                    double lone = switch (country) {
                        case ALPINE -> 0.12;
                        case COLD, WET_WOODS -> 0.05;
                        case WOODS -> 0.04;
                        default -> 0.02;
                    };
                    double field = switch (country) {
                        case ALPINE -> 0.7 * Math.max(0, f - 0.4);
                        case OPEN -> 0.75 * Math.max(0, f - 0.62);
                        default -> 0.7 * Math.max(0, f - 0.58);
                    };
                    if (unit(h) >= lone + field) continue;
                    // Mostly small; in the fields the odd great one.
                    double size = 1.0 + 1.5 * Math.pow(unit(h ^ 3), 2) + (f > 0.7 && unit(h ^ 4) < 0.12 ? 1.6 : 0);
                    placed |= boulder(level, core, x0, z0, px, pz, c, size, stones(country, c), h);
                } else {
                    if (country != Country.WOODS && country != Country.WET_WOODS && country != Country.COLD) continue;
                    if (c.biome() == BiomeKind.SNOWY_PLAINS || c.biome() == BiomeKind.SNOWY_SLOPES) continue; // no trees there
                    double deadfall = 0.5 + 0.5 * GradientNoise.noise2(SEED ^ 0x40, cx / 130.0, cz / 130.0);
                    if (unit(h) >= 0.05 + 0.3 * Math.max(0, deadfall - 0.6)) continue;
                    placed |= log(level, core, x0, z0, cx, cz, c, h);
                }
            }
        }
        return placed;
    }

    private static final Block[] GREY = {Blocks.STONE, Blocks.STONE, Blocks.ANDESITE, Blocks.COBBLESTONE};
    private static final Block[] MOSSY = {Blocks.MOSSY_COBBLESTONE, Blocks.COBBLESTONE, Blocks.STONE, Blocks.MOSSY_COBBLESTONE};
    private static final Block[] SANDSTONE = {Blocks.SANDSTONE, Blocks.SANDSTONE, Blocks.SMOOTH_SANDSTONE, Blocks.CUT_SANDSTONE};
    private static final Block[] RED = {Blocks.RED_SANDSTONE, Blocks.TERRACOTTA, Blocks.RED_SANDSTONE, Blocks.DYED_TERRACOTTA.orange()};
    private static final Block[] GRANITE = {Blocks.GRANITE, Blocks.GRANITE, Blocks.STONE, Blocks.COBBLESTONE};

    private static Block[] stones(Country country, ColumnSample c) {
        return switch (country) {
            case DRY -> SANDSTONE;
            case RED -> RED;
            case WET_WOODS -> MOSSY;
            case WOODS -> c.humidity() > 0.1 ? MOSSY : GREY;
            case OPEN -> c.biome() == BiomeKind.SAVANNA || c.biome() == BiomeKind.SAVANNA_PLATEAU ? GRANITE : GREY;
            default -> GREY;
        };
    }

    /**
     * A rounded boulder a third sunk in the ground, its stone in smooth bands (not per-block
     * speckle), propped down to the ground where that dips away under it.
     */
    private static boolean boulder(WorldGenLevel level, AtlasCore core, int x0, int z0, double px, double pz, ColumnSample c, double size, Block[] stones, long h) {
        int gy = c.surfaceY();
        double cy = gy + 0.35 * size, squash = 0.7 + 0.25 * unit(h ^ 5);
        int r = (int) Math.ceil(size) + 1;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        boolean placed = false;
        for (int x = GradientNoise.floor(px) - r; x <= GradientNoise.floor(px) + r; x++) {
            for (int z = GradientNoise.floor(pz) - r; z <= GradientNoise.floor(pz) + r; z++) {
                if (x < x0 || x > x0 + 15 || z < z0 || z > z0 + 15) continue;
                int lowest = Integer.MAX_VALUE;
                Block lowestBlock = stones[0];
                for (int y = gy - 1; y <= gy + 2 * r; y++) {
                    double ex = (x + 0.5 - px) / size, ey = (y + 0.5 - cy) / (squash * size), ez = (z + 0.5 - pz) / size;
                    double lump = 0.22 * GradientNoise.noise3(h, x / 2.3, y / 2.3, z / 2.3);
                    if (ex * ex + ey * ey + ez * ez > 1 + lump) continue;
                    BlockState here = level.getBlockState(pos.set(x, y, z));
                    if (!here.isAir() && !here.canBeReplaced()) continue;
                    if (!here.getFluidState().isEmpty()) continue;
                    double n = GradientNoise.noise3(h ^ 7, x / 3.5, y / 3.5, z / 3.5);
                    Block b = stones[Math.max(0, Math.min(stones.length - 1, (int) ((n * 0.5 + 0.5) * stones.length)))];
                    level.setBlock(pos, b.defaultBlockState(), 2);
                    placed = true;
                    if (y < lowest) {
                        lowest = y;
                        lowestBlock = b;
                    }
                }
                for (int y = lowest - 1, k = 0; lowest != Integer.MAX_VALUE && k < 4; y--, k++) {
                    BlockState here = level.getBlockState(pos.set(x, y, z));
                    if (!here.isAir() && !here.canBeReplaced()) break;
                    level.setBlock(pos, lowestBlock.defaultBlockState(), 2);
                }
            }
        }
        return placed;
    }

    /** A fallen trunk on level ground in the forest's own wood, moss and the odd mushroom on it. */
    private static boolean log(WorldGenLevel level, AtlasCore core, int x0, int z0, int sx, int sz, ColumnSample c, long h) {
        int length = 4 + (int) (unit(h ^ 3) * 5);
        boolean alongX = (h & 8) != 0;
        int gy = c.surfaceY();
        for (int k = 0; k < length; k++) {
            ColumnSample under = core.column(alongX ? sx + k : sx, alongX ? sz : sz + k);
            if (Math.abs(under.surfaceY() - gy) > 1 || !clear(under)) return false; // only on level, ordinary ground
        }
        BlockState log = wood(c.biome()).defaultBlockState().setValue(RotatedPillarBlock.AXIS, alongX ? Direction.Axis.X : Direction.Axis.Z);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        boolean placed = false;
        int y = gy + 1;
        for (int k = 0; k < length; k++) {
            int x = alongX ? sx + k : sx, z = alongX ? sz : sz + k;
            if (x < x0 || x > x0 + 15 || z < z0 || z > z0 + 15) continue;
            BlockState here = level.getBlockState(pos.set(x, y, z));
            if (!here.isAir() && !here.canBeReplaced() || !here.getFluidState().isEmpty()) continue;
            if (level.getBlockState(pos.set(x, y - 1, z)).isAir()) continue;
            level.setBlock(pos.set(x, y, z), log, 2);
            placed = true;
            double roll = unit(GradientNoise.hash(h, x, y, z));
            if (level.getBlockState(pos.set(x, y + 1, z)).isAir()) {
                if (roll < 0.3) level.setBlock(pos, Blocks.MOSS_CARPET.defaultBlockState(), 2);
                else if (roll < 0.38) level.setBlock(pos, (roll < 0.34 ? Blocks.BROWN_MUSHROOM : Blocks.RED_MUSHROOM).defaultBlockState(), 2);
            }
        }
        return placed;
    }

    private static Block wood(BiomeKind biome) {
        return switch (biome) {
            case TAIGA, SNOWY_TAIGA, OLD_GROWTH_SPRUCE_TAIGA, OLD_GROWTH_PINE_TAIGA, GROVE -> Blocks.SPRUCE_LOG;
            case BIRCH_FOREST, OLD_GROWTH_BIRCH_FOREST -> Blocks.BIRCH_LOG;
            case DARK_FOREST -> Blocks.DARK_OAK_LOG;
            case PALE_GARDEN -> Blocks.PALE_OAK_LOG;
            case JUNGLE, SPARSE_JUNGLE -> Blocks.JUNGLE_LOG;
            case CHERRY_GROVE -> Blocks.CHERRY_LOG;
            case MANGROVE_SWAMP -> Blocks.MANGROVE_LOG;
            default -> Blocks.OAK_LOG;
        };
    }
}
