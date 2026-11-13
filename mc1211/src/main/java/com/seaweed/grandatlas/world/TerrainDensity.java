package com.seaweed.grandatlas.world;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.seaweed.grandatlas.core.AtlasCore;
import com.seaweed.grandatlas.core.ColumnSample;
import javax.annotation.Nullable;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.world.level.levelgen.DensityFunction;

/**
 * {@code grandatlas:terrain}: the core's surface density, in the slot vanilla's
 * {@code overworld/sloped_cheese} used to fill. Vanilla's caves, slides and squeeze stay
 * around it in {@code grandatlas:overworld/final_density}. {@code jagged: false} skips the 3D
 * roughness. It is the ground before rivers cut it; {@link GrandCaves} cuts them in per block.
 */
public final class TerrainDensity implements DensityFunction, CoreHandle.CoreFunction {
    public static final MapCodec<TerrainDensity> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.BOOL.optionalFieldOf("jagged", true).forGetter(t -> t.jagged)
    ).apply(i, TerrainDensity::new));
    public static final KeyDispatchDataCodec<TerrainDensity> CODEC = KeyDispatchDataCodec.of(MAP_CODEC);

    private final boolean jagged;
    private final @Nullable AtlasCore core;
    private final PerColumn<ColumnSample> columns;

    public TerrainDensity(boolean jagged) {
        this(jagged, null);
    }

    private TerrainDensity(boolean jagged, @Nullable AtlasCore core) {
        this.jagged = jagged;
        this.core = core;
        this.columns = new PerColumn<>((x, z) -> CoreHandle.require(this.core).column(x, z));
    }

    @Override
    public @Nullable AtlasCore boundCore() {
        return core;
    }

    @Override
    public double compute(FunctionContext context) {
        int x = context.blockX(), z = context.blockZ();
        // (float): the 26.3 build hands this over as a float; keep the same values.
        return (float) CoreHandle.require(core).uncutDensity(columns.get(x, z), x, context.blockY(), z, jagged);
    }

    @Override
    public void fillArray(double[] array, ContextProvider provider) {
        provider.fillAllDirectly(array, this);
    }

    @Override
    public DensityFunction mapAll(Visitor visitor) {
        AtlasCore wiring = CoreHandle.wiring();
        return visitor.apply(wiring != null && wiring != core ? new TerrainDensity(jagged, wiring) : this);
    }

    @Override
    public double minValue() {
        return -AtlasCore.DENSITY_BOUND;
    }

    @Override
    public double maxValue() {
        return AtlasCore.DENSITY_BOUND;
    }

    @Override
    public KeyDispatchDataCodec<? extends DensityFunction> codec() {
        return CODEC;
    }
}
