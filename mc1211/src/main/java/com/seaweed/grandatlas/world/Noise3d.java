package com.seaweed.grandatlas.world;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.world.level.levelgen.SurfaceRules;
import net.minecraft.world.level.levelgen.synth.NormalNoise;

/**
 * {@code grandatlas:noise_3d}: a noise threshold read at the block itself (x, y, z).
 *
 * <p>26.3's own {@code noise_threshold} has an {@code is_3d} switch, which the cave biomes' rock
 * patches use so that a cave wall shows lenses, not vertical stripes. 1.21.1's reads the noise at
 * y 0 only, so this is the same condition with the height put back.
 */
public record Noise3d(ResourceKey<NormalNoise.NoiseParameters> noise, double minThreshold, double maxThreshold) implements SurfaceRules.ConditionSource {
    public static final MapCodec<Noise3d> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            ResourceKey.codec(Registries.NOISE).fieldOf("noise").forGetter(Noise3d::noise),
            Codec.DOUBLE.fieldOf("min_threshold").forGetter(Noise3d::minThreshold),
            Codec.DOUBLE.fieldOf("max_threshold").forGetter(Noise3d::maxThreshold)
    ).apply(i, Noise3d::new));
    public static final KeyDispatchDataCodec<Noise3d> CODEC = KeyDispatchDataCodec.of(MAP_CODEC);

    @Override
    public KeyDispatchDataCodec<? extends SurfaceRules.ConditionSource> codec() {
        return CODEC;
    }

    @Override
    public SurfaceRules.Condition apply(SurfaceRules.Context context) {
        NormalNoise sampler = context.randomState.getOrCreateNoise(noise);
        return () -> {
            double value = sampler.getValue(context.blockX, context.blockY, context.blockZ);
            return value >= minThreshold && value <= maxThreshold;
        };
    }
}
