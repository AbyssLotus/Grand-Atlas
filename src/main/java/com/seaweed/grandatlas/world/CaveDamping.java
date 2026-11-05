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
 * {@code grandatlas:cave_damping}: the core's {@link AtlasCore#caveDamping} bias. The generated
 * final density adds it to vanilla's deep caves, surface entrances and noodles, so tall
 * mountains stay mostly solid. The noodles' copy ({@code noodle: true}) also keeps them a few
 * blocks under open ground ({@link AtlasCore#noodleDamping}).
 */
public record CaveDamping(boolean noodle) implements DensityFunction {
    public static final MapCodec<CaveDamping> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.BOOL.optionalFieldOf("noodle", false).forGetter(CaveDamping::noodle)
    ).apply(i, CaveDamping::new));

    @Override
    public DensitySampler compileSampler(CompileContext context) {
        return new Sampler(CoreHandle.coreFor(context), noodle);
    }

    @Override
    public DensityFunction rewriteChildren(DfRewriteRule rule) {
        return this;
    }

    @Override
    public Interval range() {
        return Interval.of(0, 2);
    }

    @Override
    public @Axes int domainAxes() {
        return ALL_AXES;
    }

    @Override
    public MapCodec<CaveDamping> codec() {
        return CODEC;
    }

    private record Sampler(AtlasCore core, boolean noodle) implements CoreHandle.CoreSampler {
        private double at(ColumnSample column, int y) {
            return noodle ? core.noodleDamping(column, y) : core.caveDamping(column, y);
        }

        /**
         * Below the old floor the damping is always 0 (it fades out under y 96, and the river,
         * plateau-wall and noodle terms all sit within 16 blocks of ground that never goes under
         * {@code TERRAIN_FLOOR + 16}), so the deep skips the column lookups.
         */
        private static final int DEEP = AtlasCore.TERRAIN_FLOOR;

        @Override
        public void sampleVolume(SamplerContext context, DensityBuffer out, DensityVolume volume) {
            int index = 0;
            boolean deep = Math.max(volume.blockY(0), volume.blockY(volume.sizeY() - 1)) < DEEP;
            for (int z = 0; z < volume.sizeZ(); z++) {
                int blockZ = volume.blockZ(z);
                for (int x = 0; x < volume.sizeX(); x++) {
                    int blockX = volume.blockX(x);
                    // M7d: vanilla caves keep out of a cavern lake's bed and water.
                    double lake = core.megaCaverns().water(blockX, blockZ);
                    double bed = Double.isNaN(lake) ? 0 : core.megaCaverns().floor(blockX, blockZ);
                    ColumnSample column = deep ? null : core.column(blockX, blockZ);
                    boolean piece = core.setPieces().nearColumn(blockX, blockZ);
                    for (int y = 0; y < volume.sizeY(); y++) {
                        int blockY = volume.blockY(y);
                        float v = deep ? 0f : (float) at(column, blockY);
                        if (!Double.isNaN(lake) && blockY >= bed - 10 && blockY <= lake + 2) v = 2f;
                        if (piece && core.setPieces().sample(blockX, blockY, blockZ).claimed) v = 2f; // vanilla caves keep out of set pieces
                        out.set(index++, v);
                    }
                }
            }
        }

        @Override
        public float sampleValue(SamplerContext context, int blockX, int blockY, int blockZ) {
            double lake = core.megaCaverns().water(blockX, blockZ);
            if (!Double.isNaN(lake) && blockY >= core.megaCaverns().floor(blockX, blockZ) - 10 && blockY <= lake + 2) return 2f;
            if (core.setPieces().nearColumn(blockX, blockZ) && core.setPieces().sample(blockX, blockY, blockZ).claimed) return 2f;
            return blockY < DEEP ? 0f : (float) at(core.column(blockX, blockZ), blockY);
        }
    }
}
