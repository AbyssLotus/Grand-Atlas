package com.seaweed.grandatlas.world;

import com.mojang.serialization.MapCodec;
import com.seaweed.grandatlas.core.AtlasCore;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkGenerator;
import javax.annotation.Nullable;

/**
 * {@code grandatlas:fall_clear}: after trees, clears the leaves and vines grown into the waterfalls'
 * way ({@link PerchedWater#clearFalls}). Once per chunk, at top_layer_modification, from every
 * overworld biome (tools/gen_data.py writes the data).
 */
public record FallClear() implements AtlasFeature {
    public static final MapCodec<FallClear> CODEC = MapCodec.unit(FallClear::new);

    @Override
    public MapCodec<FallClear> codec() {
        return CODEC;
    }

    @Override
    public boolean place(WorldGenLevel level, ChunkGenerator chunkGenerator, RandomSource random, BlockPos origin) {
        AtlasCore core = CoreHandle.coreOf(level.getLevel());
        if (core == null) return false;
        return PerchedWater.clearFalls(level, core, origin.getX() & ~15, origin.getZ() & ~15);
    }
}
