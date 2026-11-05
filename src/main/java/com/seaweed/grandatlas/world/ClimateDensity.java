package com.seaweed.grandatlas.world;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.seaweed.grandatlas.core.AtlasCore;
import com.seaweed.grandatlas.core.ColumnSample;
import java.util.Locale;
import net.minecraft.util.Interval;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.levelgen.densityfunction.DensityBuffer;
import net.minecraft.world.level.levelgen.densityfunction.DensityFunction;
import net.minecraft.world.level.levelgen.densityfunction.DensitySampler;
import net.minecraft.world.level.levelgen.densityfunction.DensityVolume;
import net.minecraft.world.level.levelgen.densityfunction.DfRewriteRule;
import net.minecraft.world.level.levelgen.densityfunction.SamplerContext;

/**
 * {@code grandatlas:climate}: one column value from the core per channel. These fill the
 * noise router's climate slots, so anything that reads them agrees with the terrain: the
 * vanilla spawn finder wants land continentalness and weirdness off the river band, and the
 * aquifer exclusion reads erosion and depth. {@code surface} is the preliminary surface level
 * (see {@link AtlasCore#preliminarySurface}), computed directly instead of by vanilla's top-down
 * density scan.
 *
 * <p>Our biome source doesn't use these values. It uses the core they carry (see
 * {@link CoreHandle#coreFrom}).
 */
public record ClimateDensity(Channel channel) implements DensityFunction {
    public enum Channel implements StringRepresentable {
        TEMPERATURE, VEGETATION, CONTINENTS, EROSION, DEPTH, RIDGES, SURFACE;

        public static final Codec<Channel> CODEC = StringRepresentable.fromEnum(Channel::values);

        @Override
        public String getSerializedName() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    public static final MapCodec<ClimateDensity> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Channel.CODEC.fieldOf("channel").forGetter(ClimateDensity::channel)
    ).apply(i, ClimateDensity::new));

    private static final float DEPTH_RANGE = (AtlasCore.MAX_Y - AtlasCore.MIN_Y) / 128f + 1;

    @Override
    public DensitySampler compileSampler(CompileContext context) {
        return new Sampler(CoreHandle.coreFor(context), channel);
    }

    @Override
    public DensityFunction rewriteChildren(DfRewriteRule rule) {
        return this;
    }

    @Override
    public Interval range() {
        return switch (channel) {
            case DEPTH -> Interval.ofSymmetric(DEPTH_RANGE);
            case SURFACE -> Interval.of(AtlasCore.MIN_Y, AtlasCore.MAX_Y);
            default -> Interval.of(-1, 1);
        };
    }

    @Override
    public @Axes int domainAxes() {
        return channel == Channel.DEPTH ? ALL_AXES : AXIS_X | AXIS_Z;
    }

    @Override
    public MapCodec<ClimateDensity> codec() {
        return CODEC;
    }

    private record Sampler(AtlasCore core, Channel channel) implements CoreHandle.CoreSampler {
        @Override
        public void sampleVolume(SamplerContext context, DensityBuffer out, DensityVolume volume) {
            for (int z = 0; z < volume.sizeZ(); z++) {
                int blockZ = volume.blockZ(z);
                for (int x = 0; x < volume.sizeX(); x++) {
                    int blockX = volume.blockX(x);
                    int index = volume.indexUnchecked(x, 0, z);
                    if (channel == Channel.SURFACE) {
                        out.setRange(index, volume.sizeY(), (float) core.preliminarySurface(blockX, blockZ));
                        continue;
                    }
                    ColumnSample column = core.column(blockX, blockZ);
                    if (channel == Channel.DEPTH) {
                        for (int y = 0; y < volume.sizeY(); y++) {
                            out.set(index + y, depth(column, volume.blockY(y)));
                        }
                    } else {
                        out.setRange(index, volume.sizeY(), flat(column));
                    }
                }
            }
        }

        @Override
        public float sampleValue(SamplerContext context, int blockX, int blockY, int blockZ) {
            if (channel == Channel.SURFACE) {
                return (float) core.preliminarySurface(blockX, blockZ);
            }
            ColumnSample column = core.column(blockX, blockZ);
            return channel == Channel.DEPTH ? depth(column, blockY) : flat(column);
        }

        /** Vanilla's depth is ~0 at the surface and +1 about 128 blocks down. */
        private static float depth(ColumnSample c, int y) {
            return (float) ((c.height() - y) / 128.0);
        }

        private float flat(ColumnSample c) {
            return (float) switch (channel) {
                case TEMPERATURE -> c.temperature();
                case VEGETATION -> c.humidity();
                case CONTINENTS -> c.continentalness();
                case EROSION -> c.erosion();
                case RIDGES -> c.weirdness();
                case SURFACE, DEPTH -> throw new IllegalStateException(channel + " is sampled separately");
            };
        }
    }
}
