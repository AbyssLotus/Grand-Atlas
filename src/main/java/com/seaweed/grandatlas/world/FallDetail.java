package com.seaweed.grandatlas.world;

import com.mojang.serialization.MapCodec;
import com.seaweed.grandatlas.core.AtlasCore;
import com.seaweed.grandatlas.core.ColumnSample;
import com.seaweed.grandatlas.core.hydro.Drainage;
import com.seaweed.grandatlas.core.noise.GradientNoise;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.VineBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.densityfunction.SamplerContext;
import net.minecraft.world.level.levelgen.feature.Feature;
import org.jspecify.annotations.Nullable;

/**
 * {@code grandatlas:fall_detail}: dresses the river network's waterfalls (docs/M3_M4_PLAN.md,
 * "Waterfalls"). Each fall's amphitheatre takes a style from its climate:
 * <ul>
 *   <li><b>mossy stone</b>: the walls go mossy where the spray would reach them, thickest close to
 *       the fall and in smooth patches;</li>
 *   <li><b>punchbowl</b> (Latourell), tall falls in wet country: walls of upright basalt columns,
 *       mossy near the fall;</li>
 *   <li><b>red canyon</b> (Havasu), warm dry country: walls banded in red, orange and pale terracotta.</li>
 * </ul>
 * Vines hang down the walls (not in the canyon), and the rock floor gets moss, ferns and the odd
 * mossy boulder.
 * <ul>
 *   <li><b>icy</b>, in the cold: the spray frozen onto the walls as packed and blue ice, snow
 *       further off, ice and snow on the floor (the user's frozen Skógafoss).</li>
 * </ul>
 *
 * <p>Runs at local_modifications, after the water is in. All choices come from position hashes
 * and smooth noise, so a fall across a chunk border comes out the same whichever side is first.
 */
public record FallDetail() implements Feature {
    public static final MapCodec<FallDetail> CODEC = MapCodec.unit(FallDetail::new);
    private static final long SEED = 0xFA11DE7A1L;

    private static volatile @Nullable ServerLevel lastLevel;
    private static volatile @Nullable AtlasCore lastCore;

    @Override
    public MapCodec<FallDetail> codec() {
        return CODEC;
    }

    @Override
    public boolean place(WorldGenLevel level, ChunkGenerator chunkGenerator, RandomSource random, BlockPos origin) {
        AtlasCore core = core(level.getLevel());
        if (core == null) return false;
        int x0 = origin.getX() & ~15, z0 = origin.getZ() & ~15;
        boolean placed = rapids(level, core, x0, z0);
        List<Drainage.Fall> falls = new ArrayList<>();
        core.drainage().falls(x0, z0, x0 + 15, z0 + 15, falls);
        if (falls.isEmpty()) return placed;
        for (Drainage.Fall f : falls) {
            for (int dz = 0; dz < 16; dz++) {
                for (int dx = 0; dx < 16; dx++) {
                    dress(level, core, f, x0 + dx, z0 + dz);
                }
            }
        }
        return true;
    }

    /**
     * Boulder rapids (the user's photo of a river breaking through rounded rocks): where a river's
     * water drops fast (1.2+ over 6 blocks downstream, its step pools), great rounded boulders stand
     * in the channel and out of the water, mossy where it's damp, the water running between them.
     * One on about 1 in 30 such columns, more where it drops faster.
     */
    private static boolean rapids(WorldGenLevel level, AtlasCore core, int x0, int z0) {
        boolean placed = false;
        for (int dz = 0; dz < 16; dz++) {
            for (int dx = 0; dx < 16; dx++) {
                int x = x0 + dx, z = z0 + dz;
                ColumnSample c = core.column(x, z);
                if (Double.isNaN(c.water()) || c.water() <= c.height() || c.flowX() == 0 && c.flowZ() == 0) continue;
                if (c.biome() != com.seaweed.grandatlas.core.BiomeKind.RIVER && c.biome() != com.seaweed.grandatlas.core.BiomeKind.FROZEN_RIVER) continue;
                double fl = Math.hypot(c.flowX(), c.flowZ());
                ColumnSample down = core.column(x + (int) Math.round(6 * c.flowX() / fl), z + (int) Math.round(6 * c.flowZ() / fl));
                if (Double.isNaN(down.water())) continue;
                double drop = c.water() - down.water();
                // A rapid, not a fall: no boulder in a lip's spout (the user: "some water falls don't
                // fall", a boulder dammed the stream at the edge) or just above one.
                if (c.spill() || drop > 5) continue;
                ColumnSample near = core.column(x + (int) Math.round(3 * c.flowX() / fl), z + (int) Math.round(3 * c.flowZ() / fl));
                if (near.spill() || !Double.isNaN(near.water()) && c.water() - near.water() > 3) continue;
                if (drop < 1.2 || roll(x, 0, z, 11) > 0.035 * Math.min(2, drop / 2)) continue;
                double r = 1.1 + 1.3 * roll(x, 1, z, 12);
                double top = c.water() + 0.4 + 1.2 * roll(x, 2, z, 13), bottom = c.height() - 1;
                double cy = Math.max(bottom + r * 0.6, top - r);
                boolean mossy = c.temperature() > -0.3 && c.humidity() > -0.2;
                int ir = (int) Math.ceil(r);
                for (int by = (int) Math.floor(bottom); by <= (int) Math.ceil(top); by++) {
                    for (int bz = -ir; bz <= ir; bz++) {
                        for (int bx = -ir; bx <= ir; bx++) {
                            double ny = (by + 0.5 - cy) / (by + 0.5 > cy ? Math.max(0.5, top - cy) / r : 1);
                            double q = Math.sqrt(bx * bx + bz * bz + ny * ny) / r
                                    + 0.15 * GradientNoise.noise3(SEED ^ 9, (x + bx) / 2.5, by / 2.5, (z + bz) / 2.5);
                            if (q > 1) continue;
                            BlockPos at = new BlockPos(x + bx, by, z + bz);
                            BlockState s = level.getBlockState(at);
                            if (!(s.isAir() || !s.getFluidState().isEmpty() || s.canBeReplaced())) continue;
                            double n = GradientNoise.noise3(SEED ^ 10, (x + bx) / 3.0, by / 3.0, (z + bz) / 3.0);
                            boolean above = by > c.water();
                            level.setBlock(at, (mossy && above && n > 0.15 ? Blocks.MOSSY_COBBLESTONE : n > -0.2 ? Blocks.ANDESITE : Blocks.STONE).defaultBlockState(), 2);
                            placed = true;
                        }
                    }
                }
            }
        }
        return placed;
    }

    /** Distance from (x, z) to the amphitheatre's centre, in radii, on its stretched shape. */
    private static double bowl(Drainage.Fall f, double x, double z) {
        double dx = x - f.cx(), dz = z - f.cz();
        double along = dx * f.ux() + dz * f.uz(), across = -dx * f.uz() + dz * f.ux();
        return Math.hypot(along / 1.1, across / 0.85) / f.radius();
    }

    private static void dress(WorldGenLevel level, AtlasCore core, Drainage.Fall f, int x, int z) {
        double b = bowl(f, x + 0.5, z + 0.5);
        if (b > 1.6) return;
        ColumnSample c = core.column(x, z);
        Style style = style(core, f);
        double toLip = Math.hypot(x + 0.5 - f.lipX(), z + 0.5 - f.lipZ());
        double spray = 0.25 + 0.65 * Math.exp(-toLip / (0.7 * f.radius())); // wettest by the fall
        int floor = c.surfaceY();

        // The wall: this column stands high over a neighbour on the bowl's floor.
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            int nx = x + dir.getStepX(), nz = z + dir.getStepZ();
            ColumnSample n = core.column(nx, nz);
            int nFloor = n.surfaceY();
            if (floor - nFloor < 3 || bowl(f, nx + 0.5, nz + 0.5) > 1.2) continue;
            int bottom = Math.max(nFloor + 1, (int) Math.floor(f.level()) + 1);
            for (int y = bottom; y <= floor; y++) {
                BlockPos at = new BlockPos(x, y, z);
                BlockState s = level.getBlockState(at);
                if (!(s.is(BlockTags.BASE_STONE_OVERWORLD) || s.is(BlockTags.DIRT) || s.is(Blocks.GRAVEL)) || !level.getBlockState(new BlockPos(nx, y, nz)).isAir()) continue;
                double patch = GradientNoise.noise3(SEED, x / 5.0, y / 4.0, z / 5.0);
                if (style == Style.ICY) {
                    // Spray frozen onto the rock: ice glazing near the fall, snow-crusted further off.
                    if (patch > 0.6 - 0.9 * spray) {
                        level.setBlock(at, (patch > 0.8 - 0.6 * spray ? Blocks.BLUE_ICE : Blocks.PACKED_ICE).defaultBlockState(), 2);
                    } else if (patch > 0.2) {
                        level.setBlock(at, Blocks.SNOW_BLOCK.defaultBlockState(), 2);
                    }
                    continue;
                }
                if (style == Style.CANYON) {
                    // Bands that wander a little up and down along the wall.
                    int band = Math.floorMod((int) Math.floor((y + 4 * GradientNoise.noise2(SEED ^ 5, x / 40.0, z / 40.0)) / 3), BANDS.length);
                    level.setBlock(at, BANDS[band].defaultBlockState(), 2);
                    continue;
                }
                if (patch > 0.55 - 0.8 * spray) {
                    level.setBlock(at, (patch > 0.75 - 0.6 * spray ? Blocks.MOSS_BLOCK : Blocks.MOSSY_COBBLESTONE).defaultBlockState(), 2);
                } else if (style == Style.BASALT) {
                    // Upright columns: the choice follows (x, z) only, so each column runs the wall's height.
                    double col = GradientNoise.noise2(SEED ^ 6, x / 1.7, z / 1.7);
                    level.setBlock(at, (col > 0.15 ? Blocks.POLISHED_BASALT : Blocks.BASALT).defaultBlockState(), 2);
                }
            }
            if (style == Style.CANYON || style == Style.ICY) continue;
            // Vines hanging from the rim, here and there, a few to many blocks down.
            if (roll(x, dir.ordinal(), z, 1) < 0.18 + 0.3 * spray) {
                BlockState vine = Blocks.VINE.defaultBlockState().setValue(VineBlock.getPropertyForFace(dir.getOpposite()), true);
                int length = 3 + (int) (roll(x, dir.ordinal(), z, 2) * (6 + 0.5 * (floor - nFloor)));
                for (int y = floor, k = 0; k < length && y > bottom; y--, k++) {
                    BlockPos at = new BlockPos(nx, y, nz);
                    if (!level.getBlockState(at).isAir()) break;
                    level.setBlock(at, vine, 2);
                }
            }
        }

        // The floor: moss, ferns and mossy boulders on the dry rock ring round the pool.
        if (b < 1.0 && Double.isNaN(c.water()) && floor > f.level()) {
            BlockPos top = new BlockPos(x, floor, z), above = top.above();
            BlockState ground = level.getBlockState(top);
            if (!level.getBlockState(above).isAir()) return;
            double patch = GradientNoise.noise2(SEED ^ 1, x / 6.0, z / 6.0), roll = roll(x, floor, z, 3);
            if (style == Style.ICY) {
                if (patch > 0.5 - spray) level.setBlock(top, (patch > 0.75 ? Blocks.PACKED_ICE : Blocks.SNOW_BLOCK).defaultBlockState(), 2);
                return;
            }
            if ((ground.is(BlockTags.DIRT) || ground.is(BlockTags.BASE_STONE_OVERWORLD) || ground.is(Blocks.GRAVEL)) && patch > 0.4 - spray) {
                level.setBlock(top, Blocks.MOSS_BLOCK.defaultBlockState(), 2);
            }
            Block plant = roll < 0.05 * spray ? Blocks.MOSSY_COBBLESTONE : roll < 0.2 ? Blocks.FERN : roll < 0.32 ? Blocks.MOSS_CARPET : null;
            if (plant != null) level.setBlock(above, plant.defaultBlockState(), 2);
        }
    }

    private enum Style { MOSSY, BASALT, CANYON, ICY }

    private static final Block[] BANDS = {Blocks.TERRACOTTA, Blocks.DYED_TERRACOTTA.orange(), Blocks.DYED_TERRACOTTA.red(), Blocks.TERRACOTTA,
            Blocks.DYED_TERRACOTTA.white(), Blocks.RED_SANDSTONE, Blocks.DYED_TERRACOTTA.orange(), Blocks.DYED_TERRACOTTA.brown()};

    /** A fall's style, from the climate at its lip and its height. */
    private static Style style(AtlasCore core, Drainage.Fall f) {
        ColumnSample c = core.column((int) Math.floor(f.lipX()), (int) Math.floor(f.lipZ()));
        double t = c.temperature(), w = c.humidity();
        if (t < -0.45) return Style.ICY;
        if (t > 0.2 && w < -0.1) return Style.CANYON;
        double roll = (GradientNoise.hash(SEED ^ 7, (int) Math.floor(f.lipX()), (int) Math.floor(f.lipZ())) >>> 11) * 0x1.0p-53;
        if (f.lip() - f.level() >= 30 && w > 0.05 && roll < 0.6) return Style.BASALT;
        return Style.MOSSY;
    }

    private static double roll(int x, int y, int z, int salt) {
        return (GradientNoise.hash(SEED + salt * 0x9E3779B97F4A7C15L, x, y, z) >>> 11) * 0x1.0p-53;
    }

    private static @Nullable AtlasCore core(ServerLevel level) {
        if (level == lastLevel) return lastCore;
        AtlasCore core = CoreHandle.coreFrom(level.getChunkSource().randomState().createClimateSampler(SamplerContext.EMPTY_UNCACHED));
        lastCore = core;
        lastLevel = level;
        return core;
    }
}
