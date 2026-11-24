package com.seaweed.grandatlas.world;

import com.mojang.serialization.MapCodec;
import com.seaweed.grandatlas.core.AtlasCore;
import com.seaweed.grandatlas.core.BiomeKind;
import com.seaweed.grandatlas.core.ColumnSample;
import com.seaweed.grandatlas.core.noise.GradientNoise;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.VineBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;

/**
 * {@code grandatlas:rock_face_life}: life on the bare rock {@link SteepRock} leaves, and on stony shores
 * (the quality review's open items, 2026-11-23: "ledge plants on dry and alpine rock faces" and
 * "rocky shores, gravel 98% bare").
 * <ul>
 *   <li><b>Ledges</b> (the top of a rock step, open sky above): a pocket of soil with something in it.
 *       Wet country: moss with ferns, grass and the odd azalea. High and cold: krummholz, low wind-cut
 *       spruce. Dry and hot: coarse dirt with dead bushes. In between: grass tufts in moss.</li>
 *   <li><b>Faces</b> (a step's riser): vines hanging down the stone in warm, wet country.</li>
 *   <li><b>Stony shores</b>: cobble and mossy boulders, driftwood and pale stone on the gravel.</li>
 * </ul>
 * All of it gathers by a smooth field, so some faces are green and some stay bare. Runs at
 * vegetal_decoration, after the trees; each chunk writes only its own blocks.
 */
public record RockFaceLife() implements AtlasFeature {
    public static final MapCodec<RockFaceLife> CODEC = MapCodec.unit(RockFaceLife::new);
    private static final long SEED = 0x40C4F11EL, STEEP_SEED = 0x57EE9L;
    private static final Direction[] SIDES = {Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST};

    @Override
    public MapCodec<RockFaceLife> codec() {
        return CODEC;
    }

    @Override
    public boolean place(WorldGenLevel level, ChunkGenerator generator, RandomSource random, BlockPos origin) {
        AtlasCore core = CoreHandle.coreOf(level.getLevel());
        if (core == null) return false;
        int x0 = origin.getX() & ~15, z0 = origin.getZ() & ~15;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        boolean placed = false;
        for (int dz = 0; dz < 16; dz++) {
            for (int dx = 0; dx < 16; dx++) {
                int x = x0 + dx, z = z0 + dz;
                ColumnSample c = core.column(x, z);
                if (!Double.isNaN(c.water()) && c.water() > c.height()) continue;
                if (c.biome() == BiomeKind.STONY_SHORE) {
                    placed |= shore(level, pos, c, x, z);
                    continue;
                }
                // SteepRock's own test for bare rock, so this dresses exactly the rock it made
                double slope = Math.max(Math.abs(core.column(x + 1, z).height() - core.column(x - 1, z).height()),
                        Math.abs(core.column(x, z + 1).height() - core.column(x, z - 1).height())) / 2;
                if (slope <= 1.45 + 0.45 * GradientNoise.noise2(STEEP_SEED, x / 14.0, z / 14.0)) continue;
                int top = top(level, pos, x, c.surfaceY(), z);
                if (top == Integer.MIN_VALUE) continue;
                placed |= ledge(level, pos, c, x, top, z);
                placed |= vines(level, pos, c, x, top, z, x0, z0);
            }
        }
        return placed;
    }

    /** The top stone block of a bare-rock column with open air above it, or MIN_VALUE. */
    private static int top(WorldGenLevel level, BlockPos.MutableBlockPos pos, int x, int surface, int z) {
        for (int y = surface + 3; y >= surface - 4; y--) {
            BlockState s = level.getBlockState(pos.set(x, y, z));
            if (s.isAir()) continue;
            return s.is(BlockTags.BASE_STONE_OVERWORLD) && level.getBlockState(pos.set(x, y + 1, z)).isAir() ? y : Integer.MIN_VALUE;
        }
        return Integer.MIN_VALUE;
    }

    private static double unit(long h) {
        return (h >>> 11) * 0x1.0p-53;
    }

    private enum Climate { WET, COLD, DRY, MILD }

    /** The rock's climate from the column's biome (the core's humidity alone put dead bushes in jungles). */
    private static Climate climate(ColumnSample c, int top) {
        return switch (c.biome()) {
            case JUNGLE, SPARSE_JUNGLE, BAMBOO_JUNGLE, SWAMP, MANGROVE_SWAMP, DARK_FOREST, CLOUD_FOREST, LUSH_CAVES, SALT_MARSH -> Climate.WET;
            case TAIGA, OLD_GROWTH_SPRUCE_TAIGA, OLD_GROWTH_PINE_TAIGA, SNOWY_TAIGA, SNOWY_PLAINS, GROVE, SNOWY_SLOPES, ICE_SPIKES,
                 STONY_PEAKS, JAGGED_PEAKS, FROZEN_PEAKS, MISTY_PEAKS, WINDSWEPT_FOREST -> Climate.COLD;
            case DESERT, RED_DESERT, BADLANDS, WOODED_BADLANDS, ERODED_BADLANDS, SAVANNA, SAVANNA_PLATEAU, WINDSWEPT_SAVANNA,
                 VOLCANIC_WASTES, VOLCANO, SALT_FLAT, RAINBOW_MOUNTAINS -> Climate.DRY;
            default -> top > 190 ? Climate.COLD : c.humidity() > 0.35 ? Climate.WET : Climate.MILD;
        };
    }

    /** How alive the rock is here, 0..1: green faces a few dozen blocks across, bare ones between. */
    private static double life(ColumnSample c, int x, int z) {
        double f = 0.45 + 0.45 * GradientNoise.noise2(SEED, x / 26.0, z / 26.0) + 0.3 * c.humidity();
        return Math.max(0, Math.min(1, f));
    }

    private static boolean ledge(WorldGenLevel level, BlockPos.MutableBlockPos pos, ColumnSample c, int x, int top, int z) {
        double roll = unit(GradientNoise.hash(SEED ^ 3, x, top, z));
        double life = life(c, x, z);
        if (roll > 0.22 * life) return false;
        Climate cl = climate(c, top);
        boolean cold = cl == Climate.COLD, dry = cl == Climate.DRY;
        BlockState plant;
        BlockState soil;
        if (cold) {
            if (top > 215 || c.biome() == BiomeKind.FROZEN_PEAKS || c.biome() == BiomeKind.ICE_SPIKES) return false; // above the trees: bare
            // krummholz: a wind-cut spruce one or two blocks high, sometimes with a stub of trunk
            BlockState leaves = Blocks.SPRUCE_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true);
            int y = top + 1;
            if (roll < 0.06 * life) {
                level.setBlock(pos.set(x, y++, z), Blocks.SPRUCE_LOG.defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.Y), 2);
            }
            level.setBlock(pos.set(x, y, z), leaves, 2);
            if (roll < 0.1 * life) level.setBlock(pos.set(x, y + 1, z), leaves, 2);
            return true;
        } else if (dry) {
            soil = Blocks.COARSE_DIRT.defaultBlockState();
            plant = roll < 0.12 * life ? Blocks.DEAD_BUSH.defaultBlockState() : null;
        } else if (cl == Climate.WET) {
            soil = Blocks.MOSS_BLOCK.defaultBlockState();
            double p = roll / (0.22 * life);
            plant = p < 0.08 ? Blocks.AZALEA.defaultBlockState() : p < 0.4 ? Blocks.FERN.defaultBlockState()
                    : p < 0.75 ? Blocks.SHORT_GRASS.defaultBlockState() : Blocks.MOSS_CARPET.defaultBlockState();
        } else {
            soil = Blocks.MOSS_BLOCK.defaultBlockState();
            plant = roll < 0.13 * life ? Blocks.SHORT_GRASS.defaultBlockState() : null;
        }
        level.setBlock(pos.set(x, top, z), soil, 2);
        if (plant != null) level.setBlock(pos.set(x, top + 1, z), plant, 2);
        return true;
    }

    /** Vines down a step's riser, in warm wet country, on the faces inside this chunk. */
    private static boolean vines(WorldGenLevel level, BlockPos.MutableBlockPos pos, ColumnSample c, int x, int top, int z, int x0, int z0) {
        if (climate(c, top) != Climate.WET || top > 180) return false;
        if (life(c, x, z) < 0.6) return false;
        boolean any = false;
        for (Direction d : SIDES) {
            int nx = x + d.getStepX(), nz = z + d.getStepZ();
            if (nx < x0 || nx > x0 + 15 || nz < z0 || nz > z0 + 15) continue;
            if (unit(GradientNoise.hash(SEED ^ 5, x * 4 + d.ordinal(), top, z)) > 0.35) continue;
            // the face: air beside this column's stone for at least two blocks down
            if (!level.getBlockState(pos.set(nx, top, nz)).isAir() || !level.getBlockState(pos.set(nx, top - 1, nz)).isAir()) continue;
            BlockState vine = Blocks.VINE.defaultBlockState().setValue(VineBlock.getPropertyForFace(d.getOpposite()), true);
            int len = 2 + (int) (unit(GradientNoise.hash(SEED ^ 6, nx, top, nz)) * 5);
            for (int y = top; y > top - len; y--) {
                if (!level.getBlockState(pos.set(nx, y, nz)).isAir() || !level.getBlockState(pos.set(x, y, z)).is(BlockTags.BASE_STONE_OVERWORLD)) break;
                level.setBlock(pos.set(nx, y, nz), vine, 2);
                any = true;
            }
        }
        return any;
    }

    /** Stony shores: boulders of cobble (mossy where it's wet), driftwood, pale stones on the gravel. */
    private static boolean shore(WorldGenLevel level, BlockPos.MutableBlockPos pos, ColumnSample c, int x, int z) {
        int top = Integer.MIN_VALUE;
        for (int y = c.surfaceY() + 3; y >= c.surfaceY() - 4; y--) {
            BlockState s = level.getBlockState(pos.set(x, y, z));
            if (s.isAir()) continue;
            if ((s.is(Blocks.GRAVEL) || s.is(BlockTags.BASE_STONE_OVERWORLD)) && level.getBlockState(pos.set(x, y + 1, z)).isAir()) top = y;
            break;
        }
        if (top == Integer.MIN_VALUE) return false;
        double field = 0.5 + 0.5 * GradientNoise.noise2(SEED ^ 9, x / 30.0, z / 30.0);
        double roll = unit(GradientNoise.hash(SEED ^ 10, x, top, z));
        boolean wet = c.humidity() > 0 && c.temperature() > -0.3;
        if (roll < 0.035 * field) {
            // a boulder: one block, sometimes two stacked, cobble or mossy cobble
            BlockState b = (wet && roll < 0.02 * field ? Blocks.MOSSY_COBBLESTONE : Blocks.COBBLESTONE).defaultBlockState();
            level.setBlock(pos.set(x, top + 1, z), b, 2);
            if (roll < 0.012 * field) level.setBlock(pos.set(x, top + 2, z), b, 2);
            return true;
        }
        if (roll < 0.045 * field) {
            // a driftwood log lying along x or z, inside the column only (one block, bleached)
            Direction.Axis axis = ((x ^ z) & 1) == 0 ? Direction.Axis.X : Direction.Axis.Z;
            level.setBlock(pos.set(x, top + 1, z), Blocks.STRIPPED_BIRCH_LOG.defaultBlockState().setValue(RotatedPillarBlock.AXIS, axis), 2);
            return true;
        }
        double patch = GradientNoise.noise2(SEED ^ 11, x / 7.0, z / 7.0);
        if (level.getBlockState(pos.set(x, top, z)).is(Blocks.GRAVEL) && patch > 0.45) {
            level.setBlock(pos, (patch > 0.65 ? Blocks.ANDESITE : Blocks.STONE).defaultBlockState(), 2);
            return true;
        }
        return false;
    }
}
