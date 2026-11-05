package com.seaweed.grandatlas.world;

import com.mojang.serialization.MapCodec;
import com.seaweed.grandatlas.core.AtlasCore;
import com.seaweed.grandatlas.core.BiomeKind;
import com.seaweed.grandatlas.core.ColumnSample;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.densityfunction.SamplerContext;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.material.Fluids;
import org.jspecify.annotations.Nullable;

/**
 * {@code grandatlas:perched_water}: fills the rivers and lakes the core carves ({@link ColumnSample#water()}:
 * the drainage network's, and a Great Plateau's own) with {@link RiverWater river water}, which holds
 * still wherever it is put and carries the river's current ({@link ColumnSample#flowX()}). Vanilla's
 * aquifers know a single sea level, so the water goes in as a decoration, at the lakes step before
 * ores and plants. Sea-level rivers already hold the aquifer's water; it is swapped for river water
 * too, so they run as well.
 *
 * <p>Wherever a block of it would face open air (a pool's lip over the next pool down, a bank
 * lower than the water) it is ordinary water instead, given a fluid tick: it pours over the lip
 * as a waterfall, or runs down the bank as a trickle, and stops on the river water below. Each
 * block decides from the core's columns, not the world, so the answer is the same whichever
 * chunk generates first. Frozen rivers stay ordinary water, so they ice over.
 *
 * <p>Vanilla's carvers cut their tunnels after the terrain, blind to it, and a tunnel can pass
 * under a river or through its bank. Where the core has ground beside or under the water but the
 * world has air, the hole is filled with the ground around it, as vanilla's own lakes do.
 *
 * <p>Placed once per chunk (no placement modifiers), from every overworld biome via a NeoForge
 * biome modifier (tools/gen_data.py writes both). It writes water only into its own chunk; a
 * plugged bank can be just over the border.
 */
public record PerchedWater() implements Feature {
    public static final MapCodec<PerchedWater> CODEC = MapCodec.unit(PerchedWater::new);
    private static final int[][] SIDES = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

    private static volatile @Nullable ServerLevel lastLevel;
    private static volatile @Nullable AtlasCore lastCore;

    @Override
    public MapCodec<PerchedWater> codec() {
        return CODEC;
    }

    @Override
    public boolean place(WorldGenLevel level, ChunkGenerator chunkGenerator, RandomSource random, BlockPos origin) {
        AtlasCore core = core(level.getLevel());
        if (core == null) return false;
        int x0 = origin.getX() & ~15, z0 = origin.getZ() & ~15;
        BlockState water = Blocks.WATER.defaultBlockState();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        boolean placed = false;
        for (int dz = 0; dz < 16; dz++) {
            for (int dx = 0; dx < 16; dx++) {
                int x = x0 + dx, z = z0 + dz;
                ColumnSample c = core.column(x, z);
                if (Double.isNaN(c.water())) continue;
                int top = (int) Math.floor(c.water());
                // A fjord (an ocean biome on the core's water) is the sea: still vanilla water, as the
                // sea's own, where kelp and seagrass grow.
                boolean frozen = c.biome() == BiomeKind.FROZEN_RIVER || c.biome().name().endsWith("OCEAN") && c.water() < AtlasCore.SEA_LEVEL;
                BlockState river = RiverWater.BLOCK.get().defaultBlockState().setValue(RiverWater.FLOW, RiverFlow.of(c.flowX(), c.flowZ()));
                boolean pouring = false; // the block above is vanilla water spilling out
                for (int y = top; y >= c.surfaceY() - 3; y--) {
                    pos.set(x, y, z);
                    BlockState here = level.getBlockState(pos);
                    boolean sea = here.is(Blocks.WATER) && here.getFluidState().isSource(); // a sea-level river's aquifer water
                    if (y < c.height()) {
                        // Under the bed: a carver's tunnel can run under a river (carvers don't see the
                        // density, and come in from chunks around). Plug it, as vanilla's lakes do.
                        if (!here.isAir()) break;
                        level.setBlock(pos, plug(level, pos), 2);
                        continue;
                    }
                    if (!here.isAir() && !sea) break;
                    if (frozen) {
                        if (!sea) level.setBlock(pos, water, 2);
                    } else if (open(core, x, y, z)) {
                        // Only the top of an open face is a source: under it the face is left empty,
                        // and the source pours down it. A source on every block of the face stood a
                        // wall of water there (sources don't fall; the user: "floating water").
                        if (pouring) {
                            if (!here.isAir()) level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
                        } else {
                            level.setBlock(pos, water, 2);
                            level.scheduleTick(pos.immutable(), Fluids.WATER, 0);
                        }
                        pouring = true;
                    } else {
                        level.setBlock(pos, river, 2);
                        pouring = false;
                    }
                    plugBanks(core, level, x, y, z);
                    placed = true;
                }
            }
        }
        return placed;
    }

    /** Beside water at (x, y, z): where the core has ground but the world has air (a carver's hole), fill it. */
    private static void plugBanks(AtlasCore core, WorldGenLevel level, int x, int y, int z) {
        BlockPos.MutableBlockPos side = new BlockPos.MutableBlockPos();
        for (int[] s : SIDES) {
            if (y >= core.column(x + s[0], z + s[1]).height()) continue;
            side.set(x + s[0], y, z + s[1]);
            if (level.getBlockState(side).isAir()) level.setBlock(side, plug(level, side), 2);
        }
    }

    /** Ground to fill a hole with: the nearest solid block in its column, or stone. */
    private static BlockState plug(WorldGenLevel level, BlockPos at) {
        for (int d : new int[] {-1, 1, -2, 2, -3, 3, -4}) {
            BlockState s = level.getBlockState(at.above(d));
            if (s.isSolid() && s.getFluidState().isEmpty()) return s;
        }
        return Blocks.STONE.defaultBlockState();
    }

    /** True if a side of the block at (x, y, z) would face air: no ground, river water or sea there. */
    private static boolean open(AtlasCore core, int x, int y, int z) {
        for (int[] s : SIDES) {
            ColumnSample n = core.column(x + s[0], z + s[1]);
            boolean ground = y < n.height();
            boolean wet = !Double.isNaN(n.water()) && y <= Math.floor(n.water());
            if (!ground && !wet && y >= AtlasCore.SEA_LEVEL) return true;
        }
        return false;
    }

    private static @Nullable AtlasCore core(ServerLevel level) {
        if (level == lastLevel) return lastCore;
        AtlasCore core = CoreHandle.coreFrom(level.getChunkSource().randomState().createClimateSampler(SamplerContext.EMPTY_UNCACHED));
        lastCore = core;
        lastLevel = level;
        return core;
    }
}
