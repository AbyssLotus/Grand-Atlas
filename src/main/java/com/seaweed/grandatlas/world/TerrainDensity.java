package com.seaweed.grandatlas.world;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.seaweed.grandatlas.core.AtlasCore;
import com.seaweed.grandatlas.core.ColumnSample;
import net.minecraft.util.Interval;
import net.minecraft.world.level.levelgen.densityfunction.DensityBuffer;
import net.minecraft.world.level.levelgen.densityfunction.DensityFunction;
import net.minecraft.world.level.levelgen.densityfunction.DensitySampler;
import net.minecraft.world.level.levelgen.densityfunction.DensityVolume;
import net.minecraft.world.level.levelgen.densityfunction.DfRewriteRule;
import net.minecraft.world.level.levelgen.densityfunction.SamplerContext;

/**
 * {@code grandatlas:terrain}: the core's surface density, in the slot vanilla's
 * {@code overworld/sloped_cheese} used to fill. Vanilla's caves, slides and squeeze stay
 * around it in {@code grandatlas:overworld/final_density}. {@code jagged: false} skips the 3D
 * roughness. It is the ground before rivers cut it; {@link GrandCaves} cuts them in per block.
 */
public record TerrainDensity(boolean jagged) implements DensityFunction {
    public static final MapCodec<TerrainDensity> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.BOOL.optionalFieldOf("jagged", true).forGetter(TerrainDensity::jagged)
    ).apply(i, TerrainDensity::new));

    @Override
    public DensitySampler compileSampler(CompileContext context) {
        return new Sampler(CoreHandle.coreFor(context), jagged);
    }

    @Override
    public DensityFunction rewriteChildren(DfRewriteRule rule) {
        return this;
    }

    @Override
    public Interval range() {
        return Interval.ofSymmetric((float) AtlasCore.DENSITY_BOUND);
    }

    @Override
    public @Axes int domainAxes() {
        return ALL_AXES;
    }

    @Override
    public MapCodec<TerrainDensity> codec() {
        return CODEC;
    }

    private record Sampler(AtlasCore core, boolean jagged) implements CoreHandle.CoreSampler {
        /** One column lookup per (x, z), then the whole Y run: the volume is laid out Y-innermost. */
        @Override
        public void sampleVolume(SamplerContext context, DensityBuffer out, DensityVolume volume) {
            int index = 0;
            for (int z = 0; z < volume.sizeZ(); z++) {
                int blockZ = volume.blockZ(z);
                for (int x = 0; x < volume.sizeX(); x++) {
                    int blockX = volume.blockX(x);
                    ColumnSample column = core.column(blockX, blockZ);
                    for (int y = 0; y < volume.sizeY(); y++) {
                        out.set(index++, (float) core.uncutDensity(column, blockX, volume.blockY(y), blockZ, jagged));
                    }
                }
            }
        }

        @Override
        public float sampleValue(SamplerContext context, int blockX, int blockY, int blockZ) {
            return (float) core.uncutDensity(core.column(blockX, blockZ), blockX, blockY, blockZ, jagged);
        }
    }
}
