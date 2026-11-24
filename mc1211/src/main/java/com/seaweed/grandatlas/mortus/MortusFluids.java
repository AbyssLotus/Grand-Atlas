package com.seaweed.grandatlas.mortus;

import com.mojang.serialization.MapCodec;
import com.seaweed.grandatlas.world.AtlasFeature;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;

/**
 * {@code grandatlas:mortus_fluids}: Domus Mortus's water and fire (the barrier ceiling is MortusCeiling, placed last). Each column of
 * the chunk is filled from its ground up to its body's level (MortusMap: the Styx, the outer sea,
 * Lethe and Mnemosyne, Cocytus frozen over, Phlegethon's fire), and a layer of barrier caps the
 * dimension so nothing leaves through the false sky (the design's section 3). Once per chunk.
 */
public record MortusFluids() implements AtlasFeature {
    public static final MapCodec<MortusFluids> CODEC = MapCodec.unit(MortusFluids::new);

    @Override
    public MapCodec<MortusFluids> codec() {
        return CODEC;
    }

    @Override
    public boolean place(WorldGenLevel level, ChunkGenerator generator, RandomSource random, BlockPos origin) {
        int x0 = origin.getX() & ~15, z0 = origin.getZ() & ~15;
        BlockState water = Blocks.WATER.defaultBlockState(), lava = Blocks.LAVA.defaultBlockState(), ice = Blocks.PACKED_ICE.defaultBlockState();
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int dz = 0; dz < 16; dz++) {
            for (int dx = 0; dx < 16; dx++) {
                int x = x0 + dx, z = z0 + dz;
                double w = MortusMap.water(x, z), f = MortusMap.fire(x, z);
                double lvl = !Double.isNaN(f) ? f : w;
                if (Double.isNaN(lvl)) continue;
                boolean frozen = MortusMap.region(x, z) == MortusMap.Region.COCYTUS;
                // every air block up to the level, from well under it (the cave's floor lies under its mountain)
                for (int y = Math.max(level.getMinBuildHeight(), (int) lvl - 60); y <= (int) lvl; y++) {
                    if (!level.getBlockState(p.set(x, y, z)).isAir()) continue;
                    BlockState s = !Double.isNaN(f) ? lava : frozen && y == (int) lvl ? ice : water;
                    level.setBlock(p, s, 2);
                }
            }
        }
        return true;
    }
}
