package com.seaweed.grandatlas.world;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

/**
 * The shape of a Grand Atlas feature as the 26.3 code writes it: a record with a codec and one
 * {@code place} method. 1.21.1's features are classes with a configuration; {@link #adapt} wraps
 * one of ours as a 1.21.1 feature with no configuration, so each feature's code is the same file
 * on both versions apart from this interface's name.
 */
public interface AtlasFeature {
    MapCodec<? extends AtlasFeature> codec();

    boolean place(WorldGenLevel level, ChunkGenerator chunkGenerator, RandomSource random, BlockPos origin);

    static Feature<NoneFeatureConfiguration> adapt(AtlasFeature feature) {
        return new Feature<>(NoneFeatureConfiguration.CODEC) {
            @Override
            public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
                return feature.place(context.level(), context.chunkGenerator(), context.random(), context.origin());
            }
        };
    }
}
