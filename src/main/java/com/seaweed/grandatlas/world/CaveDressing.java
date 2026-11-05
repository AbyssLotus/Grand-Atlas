package com.seaweed.grandatlas.world;

import com.mojang.serialization.MapCodec;
import com.seaweed.grandatlas.core.AtlasCore;
import com.seaweed.grandatlas.core.BiomeKind;
import com.seaweed.grandatlas.core.ColumnSample;
import com.seaweed.grandatlas.core.Waters;
import com.seaweed.grandatlas.core.micro.MesoKind;
import com.seaweed.grandatlas.core.noise.GradientNoise;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.AmethystClusterBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.densityfunction.SamplerContext;
import net.minecraft.world.level.levelgen.feature.Feature;
import org.jspecify.annotations.Nullable;

/**
 * {@code grandatlas:cave_dressing} (M7e): what the cave biomes need beyond their rock rules and
 * vanilla features, so they don't look bland.
 * <ul>
 *   <li><b>Formations</b> in each biome's own blocks: stalactites and stalagmites up to ~16 long,
 *       fat at the root, and columns where they would meet. Ice (blue-ice cores in packed ice,
 *       clear tips), sandstone (smooth sheath, wall-block drips), crystal (amethyst cores in
 *       calcite, cluster tips), magma (magma cores in basalt), abyssal shores (smooth basalt in
 *       tuff).</li>
 *   <li><b>Root caves</b>: strands of root in the wood of the trees above, ending in hanging roots;
 *       some run down to the floor as trunks; thick 2x2 roots under ancient groves.</li>
 *   <li><b>Ice caves</b>: snow drifts on the floors and the odd powder-snow pit.</li>
 *   <li><b>Sandstone caves</b>: sand mounds, dead bushes, and bone in the walls.</li>
 *   <li><b>Fungal forests</b>: shelf fungi on the walls, red weeping vines hanging, teal twisting vines
 *       rising.</li>
 *   <li><b>Glowworm grottos</b>: strands of glowworm silk from every ceiling.</li>
 *   <li><b>Crystal caves</b>: clusters on the walls, and selenite beams 6-60 long spanning the caves
 *       (amethyst knots), placed from the core's own caves
 *       ({@link Waters#state}) so a beam crossing chunks comes out whole.</li>
 * </ul>
 * Chances come from smooth noise over the region and a hash of the spot, so they bunch and thin.
 * Each column is read once, bottom to top. Runs at underground_decoration; formations may reach
 * a few blocks into the chunks beside (the decoration region allows it).
 */
public record CaveDressing() implements Feature {
    public static final MapCodec<CaveDressing> CODEC = MapCodec.unit(CaveDressing::new);
    private static final long SEED = 0x7A11C0DEL;

    private static volatile @Nullable ServerLevel lastLevel;
    private static volatile @Nullable AtlasCore lastCore;

    /** A biome's formations: blocks, chances per ceiling and floor spot, longest. */
    private record Spikes(BlockState core, BlockState outer, BlockState tipDown, BlockState tipUp, double ceiling, double floor, int longest) {}

    private static final java.util.Map<BiomeKind, Spikes> SPIKES = new java.util.EnumMap<>(BiomeKind.class);

    static {
        SPIKES.put(BiomeKind.ICE_CAVES, new Spikes(Blocks.BLUE_ICE.defaultBlockState(), Blocks.PACKED_ICE.defaultBlockState(),
                Blocks.ICE.defaultBlockState(), Blocks.ICE.defaultBlockState(), 0.04, 0.02, 16));
        SPIKES.put(BiomeKind.SANDSTONE_CAVES, new Spikes(Blocks.SANDSTONE.defaultBlockState(), Blocks.SMOOTH_SANDSTONE.defaultBlockState(),
                Blocks.SANDSTONE_WALL.defaultBlockState(), Blocks.SANDSTONE_WALL.defaultBlockState(), 0.03, 0.025, 11));
        SPIKES.put(BiomeKind.CRYSTAL_CAVES, new Spikes(Blocks.AMETHYST_BLOCK.defaultBlockState(), Blocks.CALCITE.defaultBlockState(),
                Blocks.AMETHYST_CLUSTER.defaultBlockState().setValue(AmethystClusterBlock.FACING, Direction.DOWN),
                Blocks.AMETHYST_CLUSTER.defaultBlockState().setValue(AmethystClusterBlock.FACING, Direction.UP), 0.04, 0.04, 13));
        SPIKES.put(BiomeKind.MAGMA_CHAMBERS, new Spikes(Blocks.MAGMA_BLOCK.defaultBlockState(), Blocks.BASALT.defaultBlockState(),
                Blocks.BLACKSTONE.defaultBlockState(), Blocks.BLACKSTONE.defaultBlockState(), 0.03, 0.03, 11));
        SPIKES.put(BiomeKind.ABYSSAL_SHORES, new Spikes(Blocks.SMOOTH_BASALT.defaultBlockState(), Blocks.TUFF.defaultBlockState(),
                Blocks.TUFF.defaultBlockState(), Blocks.TUFF.defaultBlockState(), 0.025, 0.015, 10));
    }

    private static final java.util.Set<BiomeKind> DRESSED = java.util.EnumSet.of(BiomeKind.THE_HOLLOW, BiomeKind.TITANS_GRAVE, BiomeKind.GLOWWORM_GROTTO, BiomeKind.ROOT_CAVES, BiomeKind.ICE_CAVES,
            BiomeKind.SANDSTONE_CAVES, BiomeKind.FUNGAL_FOREST, BiomeKind.CRYSTAL_CAVES, BiomeKind.MAGMA_CHAMBERS, BiomeKind.ABYSSAL_SHORES);
    private static final int[][] SIDES = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

    @Override
    public MapCodec<CaveDressing> codec() {
        return CODEC;
    }

    @Override
    public boolean place(WorldGenLevel level, ChunkGenerator chunkGenerator, RandomSource random, BlockPos origin) {
        AtlasCore core = core(level.getLevel());
        if (core == null) return false;
        int x0 = origin.getX() & ~15, z0 = origin.getZ() & ~15;
        // Which 4x4x4 cells of the chunk are dressed cave biomes.
        int top = Integer.MIN_VALUE;
        for (int qz = 0; qz < 4; qz++) for (int qx = 0; qx < 4; qx++) top = Math.max(top, core.column(x0 + qx * 4 + 2, z0 + qz * 4 + 2).surfaceY());
        int qy0 = (AtlasCore.MIN_Y + 8) >> 2, qy1 = Math.min(top, 1000) >> 2;
        BiomeKind[] cells = new BiomeKind[16 * (qy1 - qy0 + 1)];
        boolean any = false, crystal = false;
        for (int qz = 0; qz < 4; qz++) {
            for (int qx = 0; qx < 4; qx++) {
                int x = x0 + qx * 4 + 2, z = z0 + qz * 4 + 2;
                ColumnSample c = core.column(x, z);
                for (int qy = qy0; qy <= qy1; qy++) {
                    BiomeKind b = core.biomeAt(c, x, qy * 4 + 2, z);
                    if (DRESSED.contains(b)) {
                        cells[(qy - qy0) * 16 + qz * 4 + qx] = b;
                        any = true;
                        crystal |= b == BiomeKind.CRYSTAL_CAVES;
                    }
                }
            }
        }
        if (!any) return false;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int dz = 0; dz < 16; dz++) {
            for (int dx = 0; dx < 16; dx++) {
                int x = x0 + dx, z = z0 + dz;
                ColumnSample c = core.column(x, z);
                // One pass up the column: each block read once, the one below remembered.
                BlockState below = level.getBlockState(pos.set(x, qy0 * 4 - 1, z));
                for (int y = qy0 * 4; y <= qy1 * 4 + 3; y++) {
                    BlockState here = level.getBlockState(pos.set(x, y, z));
                    BiomeKind b = cells[((y >> 2) - qy0) * 16 + (dz >> 2) * 4 + (dx >> 2)];
                    if (b != null && here.isAir()) {
                        boolean floor = solid(below);
                        boolean ceiling = solid(level.getBlockState(pos.set(x, y + 1, z)));
                        dress(level, core, c, b, x, y, z, floor, ceiling);
                    }
                    below = here;
                }
            }
        }
        if (crystal) beams(level, core, x0, z0, cells, qy0);
        return true;
    }

    private static boolean solid(BlockState s) {
        return s.isSolid() && s.getFluidState().isEmpty();
    }

    private static double unit(long h) {
        return (GradientNoise.mix(h) >>> 11) * 0x1.0p-53;
    }

    /** Everything one open block of a dressed biome may get, by its biome and whether it's at a floor or ceiling. */
    private static void dress(WorldGenLevel level, AtlasCore core, ColumnSample c, BiomeKind b, int x, int y, int z, boolean floor, boolean ceiling) {
        long h = GradientNoise.hash(SEED, x, y, z);
        double roll = unit(h);
        double bunch = 0.3 + 1.4 * (0.5 + 0.5 * GradientNoise.noise2(SEED ^ b.ordinal(), x / 23.0, z / 23.0));
        Spikes spikes = SPIKES.get(b);
        // Inside a set piece the crystal caves' calcite spikes would hide the geode's amethyst wall.
        if (spikes != null && b == BiomeKind.CRYSTAL_CAVES && core.setPieces().nearColumn(x, z) && core.setPieces().sample(x, y, z).claimed) spikes = null;
        if (ceiling) {
            if (b == BiomeKind.THE_HOLLOW) {
                // Glowworm silk under the floating islands and from the dome.
                if (roll < 0.12 * bunch) silk(level, x, y, z, h, 3 + (int) (unit(h ^ 0x81) * unit(h ^ 0x82) * 14));
                return;
            }
            if (b == BiomeKind.TITANS_GRAVE) {
                // Overgrown: curtains of cave vines with glow berries from every rib and the roof.
                if (roll < 0.28 * bunch) caveVines(level, x, y, z, h, 2 + (int) (unit(h ^ 0x81) * unit(h ^ 0x82) * 18));
                else if (roll < 0.33 * bunch) level.setBlock(new BlockPos(x, y, z), Blocks.HANGING_ROOTS.defaultBlockState(), 2);
                return;
            }
            if (b == BiomeKind.GLOWWORM_GROTTO) {
                if (roll < 0.2 * bunch) silk(level, x, y, z, h, 2 + (int) (unit(h ^ 0x81) * unit(h ^ 0x82) * 11));
                return;
            }
            if (b == BiomeKind.ROOT_CAVES) {
                if (unit(h ^ 0x83) < 0.03 * bunch) {
                    silk(level, x, y, z, h, 2 + (int) (unit(h ^ 0x81) * 6)); // glowworms among the roots
                    return;
                }
                roots(level, c, x, y, z, h, bunch);
                return;
            }
            if (b == BiomeKind.ICE_CAVES && roll >= 0.04 * bunch && roll < 0.1 * bunch) {
                icicle(level, x, y, z, 1 + (int) (unit(h ^ 0x84) * unit(h ^ 0x85) * 6));
                return;
            }
            if (b == BiomeKind.FUNGAL_FOREST) {
                if (roll < 0.05 * bunch) vine(level, x, y, z, h, true);
                return;
            }
            if (spikes != null && roll < spikes.ceiling() * bunch) {
                spike(level, spikes, x, y, z, h, true);
                return;
            }
        }
        if (floor) {
            if (spikes != null && roll < spikes.floor() * bunch) {
                spike(level, spikes, x, y, z, h, false);
                return;
            }
            double r2 = unit(h ^ 0x77);
            switch (b) {
                case ICE_CAVES -> {
                    if (r2 < 0.004) level.setBlock(new BlockPos(x, y - 1, z), Blocks.POWDER_SNOW.defaultBlockState(), 2);
                    else if (r2 < 0.45 * bunch) {
                        int layers = 1 + (int) (3 * (0.5 + 0.5 * GradientNoise.noise2(SEED ^ 0x5E, x / 9.0, z / 9.0)));
                        level.setBlock(new BlockPos(x, y, z), Blocks.SNOW.defaultBlockState().setValue(SnowLayerBlock.LAYERS, Math.max(1, Math.min(8, layers))), 2);
                    }
                }
                case SANDSTONE_CAVES -> {
                    if (r2 < 0.035 * bunch) {
                        level.setBlock(new BlockPos(x, y, z), Blocks.SAND.defaultBlockState(), 2);
                        if (unit(h ^ 0x78) < 0.4 && level.getBlockState(new BlockPos(x, y + 1, z)).isAir())
                            level.setBlock(new BlockPos(x, y + 1, z), Blocks.SAND.defaultBlockState(), 2);
                    } else if (r2 < 0.05 * bunch) {
                        level.setBlock(new BlockPos(x, y, z), Blocks.DEAD_BUSH.defaultBlockState(), 2);
                    }
                }
                case FUNGAL_FOREST -> {
                    if (r2 < 0.02 * bunch) vine(level, x, y, z, h, false);
                }
                case TITANS_GRAVE -> {
                    // Moss over the bones' tops, and the odd azalea growing on them.
                    BlockState under = level.getBlockState(new BlockPos(x, y - 1, z));
                    if ((under.is(Blocks.BONE_BLOCK) || under.is(Blocks.MOSS_BLOCK)) && r2 < 0.65 * Math.min(1, bunch)) {
                        level.setBlock(new BlockPos(x, y, z), r2 < 0.03 ? Blocks.AZALEA.defaultBlockState()
                                : r2 < 0.05 ? Blocks.FLOWERING_AZALEA.defaultBlockState() : Blocks.MOSS_CARPET.defaultBlockState(), 2);
                    }
                }
                case MAGMA_CHAMBERS -> {
                    BlockPos under = new BlockPos(x, y - 1, z);
                    if (r2 < 0.3 * bunch && level.getBlockState(under).is(ModBlocks.CINDER_MOSS.get()))
                        level.setBlock(new BlockPos(x, y, z), ModBlocks.CINDER_MOSS_CARPET.get().defaultBlockState(), 2);
                }
                default -> {
                }
            }
        }
        // Walls: shelf fungi, crystal clusters, bone in the sandstone.
        if (b == BiomeKind.FUNGAL_FOREST || b == BiomeKind.CRYSTAL_CAVES || b == BiomeKind.SANDSTONE_CAVES) {
            double r3 = unit(h ^ 0x99);
            double chance = b == BiomeKind.FUNGAL_FOREST ? 0.03 : b == BiomeKind.CRYSTAL_CAVES ? 0.05 : 0.004;
            if (r3 >= chance * bunch) return;
            for (int[] s : SIDES) {
                BlockPos wall = new BlockPos(x + s[0], y, z + s[1]);
                if (!solid(level.getBlockState(wall))) continue;
                switch (b) {
                    case FUNGAL_FOREST -> shelf(level, x, y, z, s, h);
                    case CRYSTAL_CAVES -> level.setBlock(new BlockPos(x, y, z), Blocks.AMETHYST_CLUSTER.defaultBlockState()
                            .setValue(AmethystClusterBlock.FACING, (s[0] > 0 ? Direction.WEST : s[0] < 0 ? Direction.EAST : s[1] > 0 ? Direction.NORTH : Direction.SOUTH)), 2);
                    default -> level.setBlock(wall, Blocks.BONE_BLOCK.defaultBlockState(), 2);
                }
                return;
            }
        }
    }

    /** A stalactite (down) or stalagmite (up) of the biome's blocks; a column where it would meet the other side. */
    private static void spike(WorldGenLevel level, Spikes s, int x, int y, int z, long h, boolean down) {
        int dir = down ? -1 : 1;
        // How much open room there is before the other side.
        int gap = 0;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        while (gap < 40 && level.getBlockState(pos.set(x, y + dir * gap, z)).isAir()) gap++;
        double u = unit(h ^ 0x51);
        int len = 2 + (int) ((s.longest() - 2) * u * u);
        boolean column = gap <= len + 3 && gap >= 3 && unit(h ^ 0x52) < 0.5;
        if (column) len = gap;
        else len = Math.min(len, gap - 2);
        if (len < 1) return;
        double radius = Math.min(3, 0.6 + len / 5.0);
        for (int k = 0; k < len; k++) {
            double t = (double) k / len;
            // A column flares at both ends; a spike thins to its tip.
            double r = column ? radius * (0.6 + 0.4 * Math.pow(Math.abs(2 * t - 1), 2)) : radius * Math.pow(1 - t, 0.9);
            int yy = y + dir * k;
            boolean tip = !column && k == len - 1;
            for (int ddx = -3; ddx <= 3; ddx++) {
                for (int ddz = -3; ddz <= 3; ddz++) {
                    double d = Math.sqrt(ddx * ddx + ddz * ddz);
                    if (tip ? d > 0.1 : d > r + 0.35) continue;
                    pos.set(x + ddx, yy, z + ddz);
                    if (!level.getBlockState(pos).isAir()) continue;
                    BlockState state = tip ? (down ? s.tipDown() : s.tipUp()) : d < r - 1.1 ? s.core() : s.outer();
                    level.setBlock(pos, state, 2);
                }
            }
        }
    }

    /** Roots from the ceiling over (x, y, z): strands in the wood of the trees above, some down to the floor as trunks. */
    private static void roots(WorldGenLevel level, ColumnSample c, int x, int y, int z, long h, double bunch) {
        if (unit(h) > (0.06 + 0.22 * bunch) / 1.7) return;
        boolean giant = c.site() != null && c.site().kind() == MesoKind.ANCIENT_GROVE && unit(h ^ 0x3) < 0.25;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int gap = 0;
        while (gap < 30 && level.getBlockState(pos.set(x, y - gap, z)).isAir()) gap++;
        boolean trunk = gap < 25 && unit(h ^ 0x4) < 0.2;
        int len = trunk ? gap : giant ? 6 + (int) (unit(h ^ 0x1) * 14) : 1 + (int) (unit(h ^ 0x1) * unit(h ^ 0x2) * 9);
        len = Math.min(len, gap);
        BlockState wood = rootWood(c.biome());
        for (int k = 0; k < len; k++) {
            boolean tip = k == len - 1 && !trunk;
            for (int e = 0; e < (giant ? 4 : 1); e++) {
                pos.set(x + (e & 1), y - k, z + (e >> 1));
                if (!level.getBlockState(pos).isAir()) continue;
                level.setBlock(pos, tip && !giant ? Blocks.HANGING_ROOTS.defaultBlockState() : wood, 2);
            }
        }
        if (giant && !trunk && level.getBlockState(pos.set(x, y - len, z)).isAir()) level.setBlock(pos, Blocks.HANGING_ROOTS.defaultBlockState(), 2);
    }

    /** A strand of glowworm silk hanging from the ceiling over (x, y, z): silk, ending in its beaded tip. */
    private static void silk(WorldGenLevel level, int x, int y, int z, long h, int len) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int placed = 0;
        while (placed < len && level.getBlockState(pos.set(x, y - placed, z)).isAir()) placed++;
        for (int k = 0; k < placed; k++) {
            level.setBlock(pos.set(x, y - k, z), ModBlocks.GLOWWORM_SILK.get().defaultBlockState().setValue(GlowwormSilkBlock.TIP, k == placed - 1), 2);
        }
    }

    /** Cave vines hanging from the ceiling over (x, y, z), a third of them bearing glow berries. */
    private static void caveVines(WorldGenLevel level, int x, int y, int z, long h, int len) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int placed = 0;
        while (placed < len && level.getBlockState(pos.set(x, y - placed, z)).isAir()) placed++;
        for (int k = 0; k < placed; k++) {
            boolean berries = unit(GradientNoise.hash(h, k, 0, 0)) < 0.33;
            BlockState s = k == placed - 1
                    ? Blocks.CAVE_VINES.defaultBlockState().setValue(net.minecraft.world.level.block.CaveVines.BERRIES, berries)
                    : Blocks.CAVE_VINES_PLANT.defaultBlockState().setValue(net.minecraft.world.level.block.CaveVines.BERRIES, berries);
            level.setBlock(pos.set(x, y - k, z), s, 2);
        }
    }

    /** A thin icicle hanging from the ceiling over (x, y, z): base, middles, tip. */
    private static void icicle(WorldGenLevel level, int x, int y, int z, int len) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int placed = 0;
        while (placed < len && level.getBlockState(pos.set(x, y - placed, z)).isAir()) placed++;
        for (int k = 0; k < placed; k++) {
            IcicleBlock.Part part = IcicleBlock.partFor(k > 0, k < placed - 1);
            level.setBlock(pos.set(x, y - k, z), ModBlocks.ICICLE.get().defaultBlockState().setValue(IcicleBlock.PART, part), 2);
        }
    }

    /** Weeping vines down from a ceiling, or twisting vines up from a floor. */
    private static void vine(WorldGenLevel level, int x, int y, int z, long h, boolean down) {
        int len = 2 + (int) (unit(h ^ 0x61) * (down ? 8 : 6));
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int dir = down ? -1 : 1, placed = 0;
        for (int k = 0; k < len; k++) {
            if (!level.getBlockState(pos.set(x, y + dir * k, z)).isAir()) break;
            placed++;
        }
        for (int k = 0; k < placed; k++) {
            boolean end = k == placed - 1;
            BlockState state = down ? (end ? Blocks.WEEPING_VINES.defaultBlockState() : Blocks.WEEPING_VINES_PLANT.defaultBlockState())
                    : (end ? Blocks.TWISTING_VINES.defaultBlockState() : Blocks.TWISTING_VINES_PLANT.defaultBlockState());
            level.setBlock(pos.set(x, y + dir * k, z), state, 2);
        }
    }

    /** A shelf fungus: a flat disc of mushroom out from a wall. */
    private static void shelf(WorldGenLevel level, int x, int y, int z, int[] wallSide, long h) {
        int r = 1 + (int) (unit(h ^ 0x71) * 2);
        BlockState cap = Blocks.BROWN_MUSHROOM_BLOCK.defaultBlockState();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int cx = x - wallSide[0] * (r - 1), cz = z - wallSide[1] * (r - 1);
        for (int ddx = -r; ddx <= r; ddx++) {
            for (int ddz = -r; ddz <= r; ddz++) {
                if (ddx * ddx + ddz * ddz > r * r + 0.5) continue;
                pos.set(cx + ddx, y, cz + ddz);
                if (level.getBlockState(pos).isAir()) level.setBlock(pos, cap, 2);
            }
        }
    }

    /** The wood of the trees above, for the roots. */
    private static BlockState rootWood(BiomeKind above) {
        Block log = switch (above) {
            case TAIGA, OLD_GROWTH_SPRUCE_TAIGA, OLD_GROWTH_PINE_TAIGA, SNOWY_TAIGA, GROVE -> Blocks.SPRUCE_LOG;
            case BIRCH_FOREST, OLD_GROWTH_BIRCH_FOREST -> Blocks.BIRCH_LOG;
            case JUNGLE, SPARSE_JUNGLE -> Blocks.JUNGLE_LOG;
            case DARK_FOREST -> Blocks.DARK_OAK_LOG;
            case PALE_GARDEN -> Blocks.PALE_OAK_LOG;
            case CHERRY_GROVE -> Blocks.CHERRY_LOG;
            case WOODED_BADLANDS, SAVANNA -> Blocks.ACACIA_LOG;
            case MANGROVE_SWAMP -> Blocks.MANGROVE_ROOTS;
            default -> Blocks.OAK_LOG;
        };
        return log.defaultBlockState();
    }

    /**
     * Crystal beams: one chance per 48-block cell, from a point in the core's open cave in a
     * crystal-cave cell, out both ways along a random line to the rock. Every chunk the beam
     * crosses draws its own part, so the whole beam forms whichever chunk comes first.
     */
    private static void beams(WorldGenLevel level, AtlasCore core, int x0, int z0, BiomeKind[] cells, int qy0) {
        int ci = Math.floorDiv(x0, 48), cj = Math.floorDiv(z0, 48);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        BlockState glass = ModBlocks.SELENITE.get().defaultBlockState(), core0 = glass, knot = Blocks.AMETHYST_BLOCK.defaultBlockState();
        for (int j = cj - 1; j <= cj + 1; j++) {
            for (int i = ci - 1; i <= ci + 1; i++) {
                for (int b = 0; b < 4; b++) {
                    long h = GradientNoise.hash(SEED ^ 0xBEA, i, b, j);
                    if (unit(h) > 0.55) continue;
                    double ox = (i + unit(h ^ 1)) * 48, oz = (j + unit(h ^ 2)) * 48;
                    ColumnSample c = core.column((int) ox, (int) oz);
                    // The column's open crystal cave, if any: every 8 blocks up it, one picked by the hash.
                    int found = 0, pick = Integer.MIN_VALUE; // heights here are negative: no sentinel among them
                    // (The biome test first: it's cheap, and the cave map is asked only inside crystal caves.)
                    for (int y = AtlasCore.MIN_Y + 12; y < Math.min(0, c.height() - 20); y += 8) {
                        if (core.biomeAt(c, (int) ox, y, (int) oz) != BiomeKind.CRYSTAL_CAVES) continue;
                        if (core.waters().state(c, (int) ox, y, (int) oz) != Waters.AIR) continue;
                        found++;
                        if (unit(h ^ (3 + found)) < 1.0 / found) pick = y; // reservoir pick: each spot equally likely
                    }
                    if (pick == Integer.MIN_VALUE) continue;
                    double oy = pick + 0.5;
                    // Up to eight directions, the first whose span from rock to rock is 6-60 long.
                    double ux = 0, uy = 0, uz = 0, lo = 0, hi = 0, len = 0;
                    for (int tries = 0; tries < 8; tries++) {
                        double yaw = unit(h ^ (40 + tries)) * 2 * Math.PI, pitch = (unit(h ^ (50 + tries)) - 0.5) * 1.6;
                        ux = Math.cos(yaw) * Math.cos(pitch);
                        uy = Math.sin(pitch);
                        uz = Math.sin(yaw) * Math.cos(pitch);
                        lo = reach(core, ox, oy, oz, -ux, -uy, -uz);
                        hi = reach(core, ox, oy, oz, ux, uy, uz);
                        len = lo + hi;
                        if (len >= 6 && len <= 60) break;
                    }
                    if (len < 6 || len > 60) continue;
                    double r = 1 + unit(h ^ 6) * 1.2;
                    for (double s = -lo - 1; s <= hi + 1; s += 0.5) {
                        double cx = ox + ux * s, cy = oy + uy * s, cz = oz + uz * s;
                        boolean knotHere = unit(GradientNoise.hash(h, (int) Math.floor(s / 7), 0, 0)) < 0.25 && Math.floorMod((int) Math.floor(s), 7) < 2;
                        for (int ddx = -2; ddx <= 2; ddx++) for (int ddy = -2; ddy <= 2; ddy++) for (int ddz = -2; ddz <= 2; ddz++) {
                            int bx = (int) Math.floor(cx) + ddx, by = (int) Math.floor(cy) + ddy, bz = (int) Math.floor(cz) + ddz;
                            if (bx >> 4 != x0 >> 4 || bz >> 4 != z0 >> 4) continue;
                            // distance from the beam's axis
                            double px = bx + 0.5 - ox, py = by + 0.5 - oy, pz = bz + 0.5 - oz;
                            double along = px * ux + py * uy + pz * uz;
                            double qx = px - ux * along, qy = py - uy * along, qz = pz - uz * along;
                            double d = Math.sqrt(qx * qx + qy * qy + qz * qz);
                            if (d > r + (knotHere ? 0.8 : 0)) continue;
                            pos.set(bx, by, bz);
                            BlockState here = level.getBlockState(pos);
                            if (!here.isAir() && !here.is(ModBlocks.SELENITE.get())) continue;
                            level.setBlock(pos, knotHere && d > r - 0.6 ? knot : d < 0.75 ? core0 : glass, 2);
                        }
                    }
                }
            }
        }
    }

    /** How far the core's open cave runs from (x, y, z) along (ux, uy, uz), up to 48. */
    private static double reach(AtlasCore core, double x, double y, double z, double ux, double uy, double uz) {
        for (double s = 1; s <= 48; s += 1) {
            int bx = (int) Math.floor(x + ux * s), by = (int) Math.floor(y + uy * s), bz = (int) Math.floor(z + uz * s);
            if (core.waters().state(core.column(bx, bz), bx, by, bz) == Waters.ROCK) return s;
        }
        return 99;
    }

    private static @Nullable AtlasCore core(ServerLevel level) {
        if (level == lastLevel) return lastCore;
        AtlasCore core = CoreHandle.coreFrom(level.getChunkSource().randomState().createClimateSampler(SamplerContext.EMPTY_UNCACHED));
        lastCore = core;
        lastLevel = level;
        return core;
    }
}
