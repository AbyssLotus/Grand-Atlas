package com.seaweed.grandatlas.world;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.seaweed.grandatlas.core.AtlasCore;
import com.seaweed.grandatlas.core.ColumnSample;
import java.util.Locale;
import javax.annotation.Nullable;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.levelgen.DensityFunction;

/**
 * {@code grandatlas:climate}: one column value from the core per channel. These fill the
 * noise router's climate slots, so anything that reads them agrees with the terrain: the
 * vanilla spawn finder wants land continentalness and weirdness off the river band, and the
 * aquifer exclusion reads erosion and depth.
 *
 * <p>{@code initial} fills 1.21.1's {@code initial_density_without_jaggedness} slot, which the
 * game scans from the top down to find the preliminary surface: positive below the core's
 * preliminary surface ({@link AtlasCore#preliminarySurface}), negative above. The scan only
 * lands on every eighth level, so {@code mixin/NoiseChunkMixin} answers with the exact level
 * instead; this stays correct for anything else that reads the slot.
 *
 * <p>Our biome source doesn't use these values. It uses the core they carry (see
 * {@link CoreHandle#coreFrom}).
 */
public final class ClimateDensity implements DensityFunction, CoreHandle.CoreFunction {
    public enum Channel implements StringRepresentable {
        TEMPERATURE, VEGETATION, CONTINENTS, EROSION, DEPTH, RIDGES, INITIAL;

        public static final Codec<Channel> CODEC = StringRepresentable.fromEnum(Channel::values);

        @Override
        public String getSerializedName() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    public static final MapCodec<ClimateDensity> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Channel.CODEC.fieldOf("channel").forGetter(c -> c.channel)
    ).apply(i, ClimateDensity::new));
    public static final KeyDispatchDataCodec<ClimateDensity> CODEC = KeyDispatchDataCodec.of(MAP_CODEC);

    private static final double DEPTH_RANGE = (AtlasCore.MAX_Y - AtlasCore.MIN_Y) / 128.0 + 1;

    private final Channel channel;
    private final @Nullable AtlasCore core;

    public ClimateDensity(Channel channel) {
        this(channel, null);
    }

    private ClimateDensity(Channel channel, @Nullable AtlasCore core) {
        this.channel = channel;
        this.core = core;
    }

    public Channel channel() {
        return channel;
    }

    @Override
    public @Nullable AtlasCore boundCore() {
        return core;
    }

    @Override
    public double compute(FunctionContext context) {
        AtlasCore c = CoreHandle.require(core);
        int x = context.blockX(), z = context.blockZ();
        if (channel == Channel.INITIAL) {
            return context.blockY() < c.preliminarySurface(x, z) ? 1 : -1;
        }
        ColumnSample column = c.column(x, z);
        return switch (channel) {
            // Vanilla's depth is ~0 at the surface and +1 about 128 blocks down.
            case DEPTH -> (column.height() - context.blockY()) / 128.0;
            case TEMPERATURE -> column.temperature();
            case VEGETATION -> column.humidity();
            case CONTINENTS -> column.continentalness();
            case EROSION -> column.erosion();
            case RIDGES -> column.weirdness();
            case INITIAL -> throw new IllegalStateException();
        };
    }

    @Override
    public void fillArray(double[] array, ContextProvider provider) {
        provider.fillAllDirectly(array, this);
    }

    @Override
    public DensityFunction mapAll(Visitor visitor) {
        AtlasCore wiring = CoreHandle.wiring();
        return visitor.apply(wiring != null && wiring != core ? new ClimateDensity(channel, wiring) : this);
    }

    @Override
    public double minValue() {
        return channel == Channel.DEPTH ? -DEPTH_RANGE : -1;
    }

    @Override
    public double maxValue() {
        return channel == Channel.DEPTH ? DEPTH_RANGE : 1;
    }

    @Override
    public KeyDispatchDataCodec<? extends DensityFunction> codec() {
        return CODEC;
    }
}
