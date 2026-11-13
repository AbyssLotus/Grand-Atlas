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

    private static final java.util.Set<BiomeKind> DRESSED = java.util.EnumSet.of(BiomeKind.AURORA_VAULT, BiomeKind.WORLDROOT_HALL, BiomeKind.HANGING_PEAKS, BiomeKind.THE_HOLLOW, BiomeKind.TITANS_GRAVE, BiomeKind.GLOWWORM_GROTTO, BiomeKind.ROOT_CAVES, BiomeKind.ICE_CAVES,
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
        // Mega-caverns outside any dressed biome get plain rock detail (they were bare): which of
        // the chunk's cells are inside one.
        boolean cavernHere = false;
        for (int k = 0; k < 5 && !cavernHere; k++) cavernHere = core.megaCaverns().span(x0 + (k == 4 ? 8 : (k & 1) * 15), z0 + (k == 4 ? 8 : (k >> 1) * 15)) != null;
        boolean[] cavern = cavernHere ? new boolean[cells.length] : null;
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
                    } else if (cavern != null && PLAIN.contains(b) && core.megaCaverns().inside(x, qy * 4 + 2, z) != 0) {
                        cavern[(qy - qy0) * 16 + qz * 4 + qx] = true;
                        any = true;
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
                    } else if (b == null && cavern != null && here.isAir() && cavern[((y >> 2) - qy0) * 16 + (dz >> 2) * 4 + (dx >> 2)]) {
                        BlockState above = level.getBlockState(pos.set(x, y + 1, z));
                        if (solid(below) || solid(above)) dressCavern(level, x, y, z, below, above);
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
            if (b == BiomeKind.AURORA_VAULT) {
                // Shards hanging from the dome and the lip round the lake, in drifts.
                if (roll < 0.035 * bunch) {
                    level.setBlock(new BlockPos(x, y, z), ModBlocks.AURORA_SHARD[core.setPieces().auroraBand(x, z)].get().defaultBlockState()
                            .setValue(AmethystClusterBlock.FACING, Direction.DOWN), 2);
                }
                return;
            }
            if (b == BiomeKind.HANGING_PEAKS) {
                // Icicles from the ice on the peaks' tips.
                BlockState up = level.getBlockState(new BlockPos(x, y + 1, z));
                if ((up.is(Blocks.PACKED_ICE) || up.is(Blocks.BLUE_ICE)) && roll < 0.3 * bunch) {
                    icicle(level, x, y, z, 1 + (int) (unit(h ^ 0x84) * unit(h ^ 0x85) * 8));
                }
                return;
            }
            if (b == BiomeKind.WORLDROOT_HALL) {
                // Hanging roots and glow-berry vines from the roots and the roof.
                if (roll < 0.14 * bunch) caveVines(level, x, y, z, h, 2 + (int) (unit(h ^ 0x81) * unit(h ^ 0x82) * 14));
                else if (roll < 0.32 * bunch) level.setBlock(new BlockPos(x, y, z), Blocks.HANGING_ROOTS.defaultBlockState(), 2);
                return;
            }
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
                    } else {
                        // More on the floor (it was sand mounds and the odd bush): drifts of sand
                        // blown across the rock, dry tufts in them, fallen sandstone blocks, old bones.
                        BlockPos underPos = new BlockPos(x, y - 1, z);
                        BlockState under = level.getBlockState(underPos);
                        double drift = GradientNoise.noise2(SEED ^ 0x5A1L, x / 11.0, z / 11.0) + 0.4 * GradientNoise.noise2(SEED ^ 0x5A2L, x / 31.0, z / 31.0);
                        double r3 = unit(h ^ 0x97);
                        if (drift > 0.3 && solid(under) && !under.is(Blocks.SAND) && solid(level.getBlockState(new BlockPos(x, y - 2, z)))) {
                            level.setBlock(underPos, Blocks.SAND.defaultBlockState(), 2);
                            under = Blocks.SAND.defaultBlockState();
                        }
                        if (under.is(Blocks.SAND) && r3 < 0.1 * bunch) {
                            level.setBlock(new BlockPos(x, y, z), (r3 < 0.03 * bunch ? Blocks.TALL_DRY_GRASS : Blocks.SHORT_DRY_GRASS).defaultBlockState(), 2);
                        } else if (r3 > 0.992) {
                            boulder(level, x, y, z, h, SAND_ROCK);
                        } else if (r3 > 0.9895) {
                            boulder(level, x, y, z, h, BONES);
                        }
                    }
                }
                case ABYSSAL_SHORES -> {
                    // The shore above the black sea (froglight and dark sand only before): tide
                    // pools a block deep, some lit by sea pickles, and dark boulders in fields.
                    BlockPos.MutableBlockPos q = new BlockPos.MutableBlockPos(x, y - 1, z);
                    BlockState under = level.getBlockState(q);
                    if (!solid(under) || under.is(Blocks.VERDANT_FROGLIGHT)) break;
                    double r3 = unit(h ^ 0x98);
                    if (GradientNoise.noise2(SEED ^ 0xAB1L, x / 6.0, z / 6.0) > 0.45 && GradientNoise.noise2(SEED ^ 0xAB2L, x / 60.0, z / 60.0) > -0.15
                            && solid(level.getBlockState(q.set(x, y - 2, z)))) {
                        boolean held = true;
                        for (int[] s : SIDES) {
                            BlockState side = level.getBlockState(q.set(x + s[0], y - 1, z + s[1]));
                            if (!solid(side) && !side.getFluidState().is(net.minecraft.tags.FluidTags.WATER) || !level.getBlockState(q.set(x + s[0], y, z + s[1])).isAir()) {
                                held = false;
                                break;
                            }
                        }
                        if (held) {
                            level.setBlock(q.set(x, y - 1, z), r3 < 0.22 ? Blocks.SEA_PICKLE.defaultBlockState()
                                    .setValue(net.minecraft.world.level.block.SeaPickleBlock.PICKLES, 1 + (int) (unit(h ^ 0x99) * 3))
                                    .setValue(net.minecraft.world.level.block.SeaPickleBlock.WATERLOGGED, true) : Blocks.WATER.defaultBlockState(), 2);
                            break;
                        }
                    }
                    double field = GradientNoise.noise2(SEED ^ 0xAB3L, x / 25.0, z / 25.0);
                    if (field > 0.2 && r3 < 0.06 * (field - 0.2) / 0.8 + 0.006) boulder(level, x, y, z, h, DARK_ROCK);
                }
                case FUNGAL_FOREST -> {
                    if (r2 < 0.02 * bunch) vine(level, x, y, z, h, false);
                }
                case AURORA_VAULT -> vaultFloor(level, core, x, y, z, h, r2, bunch);
                case THE_HOLLOW -> {
                    // The islands' tops and the shores: meadow things in drifts between the glowcaps
                    // (they were bare moss): grass and ferns, moss carpet, the odd azalea, boulders.
                    BlockState under = level.getBlockState(new BlockPos(x, y - 1, z));
                    if (!under.is(Blocks.MOSS_BLOCK)) break;
                    double r3 = unit(h ^ 0x95);
                    double meadow = 0.5 + 0.5 * GradientNoise.noise2(SEED ^ 0x40L, x / 17.0, z / 17.0);
                    if (r3 < 0.006) boulder(level, x, y, z, h);
                    else if (r3 < 0.012) level.setBlock(new BlockPos(x, y, z), (h & 4) == 0 ? Blocks.AZALEA.defaultBlockState() : Blocks.FLOWERING_AZALEA.defaultBlockState(), 2);
                    else if (r3 < 0.012 + 0.5 * meadow * meadow) level.setBlock(new BlockPos(x, y, z),
                            (h & 3) == 0 ? Blocks.FERN.defaultBlockState() : Blocks.SHORT_GRASS.defaultBlockState(), 2);
                    else if (r3 < 0.75 && meadow < 0.45) level.setBlock(new BlockPos(x, y, z), Blocks.MOSS_CARPET.defaultBlockState(), 2);
                }
                case GLOWWORM_GROTTO -> {
                    // Under the silk: glowcaps in clumps, ferns and moss carpet on the moss, small
                    // dripleaf on the clay (the floor was bare).
                    BlockState under = level.getBlockState(new BlockPos(x, y - 1, z));
                    double r3 = unit(h ^ 0x96);
                    double clump = GradientNoise.noise2(SEED ^ 0x41L, x / 9.0, z / 9.0);
                    if (under.is(Blocks.MOSS_BLOCK)) {
                        if (clump > 0.45 && r3 < 0.3) level.setBlock(new BlockPos(x, y, z), ModBlocks.GLOWCAP.get().defaultBlockState(), 2);
                        else if (r3 < 0.1) level.setBlock(new BlockPos(x, y, z), Blocks.FERN.defaultBlockState(), 2);
                        else if (r3 < 0.3) level.setBlock(new BlockPos(x, y, z), Blocks.MOSS_CARPET.defaultBlockState(), 2);
                        else if (r3 < 0.305) boulder(level, x, y, z, h);
                    } else if (under.is(Blocks.CLAY) && r3 < 0.08) {
                        level.setBlock(new BlockPos(x, y, z), Blocks.SMALL_DRIPLEAF.defaultBlockState()
                                .setValue(net.minecraft.world.level.block.SmallDripleafBlock.HALF, net.minecraft.world.level.block.state.properties.DoubleBlockHalf.LOWER), 2);
                        if (level.getBlockState(new BlockPos(x, y + 1, z)).isAir()) level.setBlock(new BlockPos(x, y + 1, z), Blocks.SMALL_DRIPLEAF.defaultBlockState()
                                .setValue(net.minecraft.world.level.block.SmallDripleafBlock.HALF, net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER), 2);
                        else level.setBlock(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState(), 2);
                    }
                }
                case WORLDROOT_HALL -> {
                    // Small things between the trees: boulders, mushrooms, ferns and grass, firefly bushes.
                    BlockState under = level.getBlockState(new BlockPos(x, y - 1, z));
                    if (!solid(under)) break;
                    double r3 = unit(h ^ 0x93);
                    BlockState put = r3 < 0.012 ? null
                            : r3 < 0.04 ? ((h & 1) == 0 ? Blocks.RED_MUSHROOM.defaultBlockState() : Blocks.BROWN_MUSHROOM.defaultBlockState())
                            : r3 < 0.06 ? Blocks.FIREFLY_BUSH.defaultBlockState()
                            : r3 < 0.2 && under.is(Blocks.MOSS_BLOCK) ? ((h & 2) == 0 ? Blocks.FERN.defaultBlockState() : Blocks.SHORT_GRASS.defaultBlockState())
                            : r3 < 0.32 && under.is(Blocks.MOSS_BLOCK) ? Blocks.MOSS_CARPET.defaultBlockState() : Blocks.AIR.defaultBlockState();
                    if (put == null) boulder(level, x, y, z, h);
                    else if (!put.isAir()) level.setBlock(new BlockPos(x, y, z), put, 2);
                }
                case TITANS_GRAVE -> {
                    BlockState under = level.getBlockState(new BlockPos(x, y - 1, z));
                    // Small things on the floor between the bones: mushrooms, little boulders, bone
                    // fragments, grass and ferns.
                    if (!under.is(Blocks.BONE_BLOCK) && solid(under)) {
                        double r3 = unit(h ^ 0x91);
                        if (r3 < 0.025) {
                            level.setBlock(new BlockPos(x, y, z), (h & 1) == 0 ? Blocks.RED_MUSHROOM.defaultBlockState() : Blocks.BROWN_MUSHROOM.defaultBlockState(), 2);
                            return;
                        }
                        if (r3 < 0.034) {
                            boulder(level, x, y, z, h);
                            return;
                        }
                        if (r3 < 0.038) {
                            level.setBlock(new BlockPos(x, y, z), Blocks.BONE_BLOCK.defaultBlockState(), 2);
                            return;
                        }
                        if (r3 < 0.1 && under.is(Blocks.MOSS_BLOCK)) {
                            level.setBlock(new BlockPos(x, y, z), (h & 2) == 0 ? Blocks.SHORT_GRASS.defaultBlockState() : Blocks.FERN.defaultBlockState(), 2);
                            return;
                        }
                    }
                    // Moss over the bones' tops, and the odd azalea growing on them.
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

    /**
     * The Aurora Vault's floor (the user, 2026-11-09: "a pretty ceiling but the floor is barren"):
     * veins of fallen, still-glowing crystal running the way the lake bed's ribbons do, shards along
     * them and on every crystal spire, little stands of crystal, pale boulders, and still puddles a
     * block deep that mirror the aurora overhead. Colours follow the lake bed straight above.
     */
    private static void vaultFloor(WorldGenLevel level, AtlasCore core, int x, int y, int z, long h, double r2, double bunch) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(x, y - 1, z);
        BlockState under = level.getBlockState(pos);
        if (!solid(under)) return;
        int band = core.setPieces().auroraBand(x, z);
        BlockState shard = ModBlocks.AURORA_SHARD[band].get().defaultBlockState().setValue(AmethystClusterBlock.FACING, Direction.UP);
        for (int i = 0; i < ModBlocks.AURORA_CRYSTAL.length; i++) {
            if (under.is(ModBlocks.AURORA_CRYSTAL[i].get())) {
                // on a crystal spire: shards on its tip and shoulders
                if (r2 < 0.5) level.setBlock(pos.set(x, y, z), ModBlocks.AURORA_SHARD[i].get().defaultBlockState().setValue(AmethystClusterBlock.FACING, Direction.UP), 2);
                return;
            }
        }
        boolean stone = under.is(Blocks.CALCITE) || under.is(Blocks.TUFF) || under.is(net.minecraft.tags.BlockTags.BASE_STONE_OVERWORLD);
        if (!stone) return;
        double vein = core.setPieces().floorVein(x, z);
        if (vein > 0) {
            level.setBlock(pos, ModBlocks.SHATTERED_AURORA[band].get().defaultBlockState(), 2);
            if (r2 < 0.08 + 0.22 * vein) level.setBlock(pos.set(x, y, z), shard, 2);
            return;
        }
        // Puddles: smooth blobs a few blocks across, only where the floor holds them on every side.
        // (in some quarters of the floor only: a wet side and a dry side)
        if (GradientNoise.noise2(SEED ^ 0xA0DL, x / 7.0, z / 7.0) > 0.5 - 0.25 * GradientNoise.noise2(SEED ^ 0xA0EL, x / 70.0, z / 70.0)
                && GradientNoise.noise2(SEED ^ 0xA0EL, x / 70.0, z / 70.0) > -0.1 && solid(level.getBlockState(pos.set(x, y - 2, z)))) {
            boolean held = true;
            for (int[] s : SIDES) {
                BlockState side = level.getBlockState(pos.set(x + s[0], y - 1, z + s[1]));
                if (!solid(side) && !side.is(Blocks.WATER) || !level.getBlockState(pos.set(x + s[0], y, z + s[1])).isAir()) {
                    held = false;
                    break;
                }
            }
            if (held) {
                level.setBlock(pos.set(x, y - 1, z), Blocks.WATER.defaultBlockState(), 2);
                return;
            }
        }
        double r3 = unit(h ^ 0xA1);
        if (r3 < 0.006 * bunch) {
            // a little stand of crystal, one to three high, a shard on top
            int n = 1 + (int) ((h >>> 8) % 3);
            BlockState crystal = ModBlocks.AURORA_CRYSTAL[band].get().defaultBlockState();
            int k = 0;
            for (; k < n && level.getBlockState(pos.set(x, y + k, z)).isAir(); k++) level.setBlock(pos, crystal, 2);
            if (level.getBlockState(pos.set(x, y + k, z)).isAir()) level.setBlock(pos, shard, 2);
        } else if (r3 < 0.011 * bunch) {
            boulder(level, x, y, z, h, PALE_ROCK);
        } else if (r3 < 0.011 * bunch + 0.022 * bunch * bunch) {
            level.setBlock(pos.set(x, y, z), shard, 2); // loose shards, thick where things bunch
        }
    }

    /** Biomes with no dressing of their own, where a mega-cavern gets plain rock detail. */
    private static final java.util.Set<BiomeKind> PLAIN = java.util.EnumSet.complementOf(java.util.EnumSet.of(BiomeKind.AURORA_VAULT, BiomeKind.WORLDROOT_HALL,
            BiomeKind.HANGING_PEAKS, BiomeKind.THE_HOLLOW, BiomeKind.TITANS_GRAVE, BiomeKind.GLOWWORM_GROTTO, BiomeKind.ROOT_CAVES, BiomeKind.ICE_CAVES,
            BiomeKind.SANDSTONE_CAVES, BiomeKind.FUNGAL_FOREST, BiomeKind.CRYSTAL_CAVES, BiomeKind.MAGMA_CHAMBERS, BiomeKind.ABYSSAL_SHORES,
            BiomeKind.DEEP_DARK, BiomeKind.SULFUR_CAVES, BiomeKind.LUSH_CAVES, BiomeKind.DRIPSTONE_CAVES));
    private static final BlockState[] GREY_ROCK = {Blocks.COBBLESTONE.defaultBlockState(), Blocks.STONE.defaultBlockState(),
            Blocks.ANDESITE.defaultBlockState(), Blocks.COBBLESTONE.defaultBlockState()};
    private static final BlockState[] DARK_ROCK = {Blocks.COBBLED_DEEPSLATE.defaultBlockState(), Blocks.DEEPSLATE.defaultBlockState(),
            Blocks.TUFF.defaultBlockState(), Blocks.COBBLED_DEEPSLATE.defaultBlockState()};

    /**
     * A mega-cavern outside every dressed biome (bare rock before, the user 2026-11-09: not enough
     * small detail): stalactites and stalagmites in the rock they grow from, boulder fields, and
     * drifts of gravel in the hollows, all in broad patches so parts of a cavern stay clean.
     */
    private static void dressCavern(WorldGenLevel level, int x, int y, int z, BlockState below, BlockState above) {
        long h = GradientNoise.hash(SEED ^ 0xCA7E, x, y, z);
        double roll = unit(h);
        double bunch = 0.2 + 1.6 * (0.5 + 0.5 * GradientNoise.noise2(SEED ^ 0xCA71L, x / 37.0, z / 37.0));
        if (solid(above) && rock(above) && roll < 0.012 * bunch) {
            spike(level, new Spikes(above, above, above, above, 0, 0, 10), x, y, z, h, true);
            return;
        }
        if (!solid(below) || !rock(below)) return;
        if (roll < 0.008 * bunch) {
            spike(level, new Spikes(below, below, below, below, 0, 0, 9), x, y, z, h, false);
            return;
        }
        boolean dark = below.is(Blocks.DEEPSLATE) || below.is(Blocks.BLACKSTONE) || below.is(Blocks.BASALT) || below.is(Blocks.SMOOTH_BASALT) || below.is(Blocks.TUFF);
        double field = GradientNoise.noise2(SEED ^ 0xCA72L, x / 29.0, z / 29.0);
        double r2 = unit(h ^ 0x77);
        if (field > 0.2 && r2 < 0.07 * (field - 0.2) / 0.8 + 0.01) {
            boulder(level, x, y, z, h, dark ? DARK_ROCK : GREY_ROCK);
            return;
        }
        if (GradientNoise.noise2(SEED ^ 0xCA73L, x / 11.0, z / 11.0) > 0.45 && field < 0.1) {
            level.setBlock(new BlockPos(x, y - 1, z), Blocks.GRAVEL.defaultBlockState(), 2);
        }
    }

    /** Plain rock a formation can grow from in its own block. */
    private static boolean rock(BlockState s) {
        return s.is(net.minecraft.tags.BlockTags.BASE_STONE_OVERWORLD) || s.is(Blocks.BLACKSTONE) || s.is(Blocks.BASALT) || s.is(Blocks.SMOOTH_BASALT)
                || s.is(Blocks.CALCITE);
    }

    private static final BlockState[] SAND_ROCK = {Blocks.SANDSTONE.defaultBlockState(), Blocks.SMOOTH_SANDSTONE.defaultBlockState(),
            Blocks.SANDSTONE.defaultBlockState(), Blocks.CUT_SANDSTONE.defaultBlockState()};
    private static final BlockState[] BONES = {Blocks.BONE_BLOCK.defaultBlockState(), Blocks.BONE_BLOCK.defaultBlockState(),
            Blocks.BONE_BLOCK.defaultBlockState(), Blocks.BONE_BLOCK.defaultBlockState()};
    private static final BlockState[] MOSSY_ROCK = {Blocks.MOSSY_COBBLESTONE.defaultBlockState(), Blocks.COBBLESTONE.defaultBlockState(),
            Blocks.TUFF.defaultBlockState(), Blocks.MOSSY_COBBLESTONE.defaultBlockState()};
    private static final BlockState[] PALE_ROCK = {Blocks.CALCITE.defaultBlockState(), Blocks.CALCITE.defaultBlockState(),
            Blocks.TUFF.defaultBlockState(), Blocks.DIORITE.defaultBlockState()};

    /** A little boulder on the floor: one to three blocks of mossy cobblestone, cobblestone or tuff. */
    private static void boulder(WorldGenLevel level, int x, int y, int z, long h) {
        boulder(level, x, y, z, h, MOSSY_ROCK);
    }

    private static void boulder(WorldGenLevel level, int x, int y, int z, long h, BlockState[] rock) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int n = 1 + (int) ((h >>> 8) % 3);
        for (int k = 0; k < n; k++) {
            pos.set(x, y + k, z);
            if (!level.getBlockState(pos).isAir()) break;
            level.setBlock(pos, rock[(int) ((h >>> (12 + k * 2)) & 3)], 2);
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
        return CoreHandle.coreOf(level);
    }
}
