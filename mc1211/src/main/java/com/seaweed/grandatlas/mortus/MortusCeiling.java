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
 * {@code grandatlas:mortus_ceiling}: a layer of barrier across the top of Domus Mortus, so nothing
 * leaves through the false sky (docs/UNDERWORLD_DESIGN.md section 3). Placed at the last step, after
 * every plant and tree: placed earlier, the heightmaps took it for the ground.
 */
public record MortusCeiling() implements AtlasFeature {
    public static final MapCodec<MortusCeiling> CODEC = MapCodec.unit(MortusCeiling::new);

    @Override
    public MapCodec<MortusCeiling> codec() {
        return CODEC;
    }

    @Override
    public boolean place(WorldGenLevel level, ChunkGenerator generator, RandomSource random, BlockPos origin) {
        int x0 = origin.getX() & ~15, z0 = origin.getZ() & ~15, top = level.getMaxBuildHeight() - 1;
        BlockState barrier = Blocks.BARRIER.defaultBlockState();
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int dz = 0; dz < 16; dz++) for (int dx = 0; dx < 16; dx++) level.setBlock(p.set(x0 + dx, top, z0 + dz), barrier, 2);
        return true;
    }
}
