package com.seaweed.grandatlas.world;

import com.mojang.serialization.MapCodec;
import com.seaweed.grandatlas.core.AtlasCore;
import com.seaweed.grandatlas.core.SkyIsles;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.world.level.levelgen.DensityFunction;

/**
 * {@code grandatlas:sky_isles}: the floating isles' density ({@link SkyIsles}). The generated final
 * density takes the larger of the world's own and this, outside every cut, so a river or cave
 * under an isle never holes it. Per block, not interpolated; columns with no isle over them
 * answer at once from the per-column table.
 */
public final class SkyIsleDensity implements DensityFunction, CoreHandle.CoreFunction {
    public static final MapCodec<SkyIsleDensity> MAP_CODEC = MapCodec.unit(SkyIsleDensity::new);
    public static final KeyDispatchDataCodec<SkyIsleDensity> CODEC = KeyDispatchDataCodec.of(MAP_CODEC);

    private final @Nullable AtlasCore core;
    private final PerColumn<List<SkyIsles.Isle>> columns;

    public SkyIsleDensity() {
        this(null);
    }

    private SkyIsleDensity(@Nullable AtlasCore core) {
        this.core = core;
        this.columns = new PerColumn<>((x, z) -> CoreHandle.require(this.core).skyIsles().near(x, z));
    }

    @Override
    public @Nullable AtlasCore boundCore() {
        return core;
    }

    @Override
    public double compute(FunctionContext context) {
        int x = context.blockX(), z = context.blockZ();
        List<SkyIsles.Isle> isles = columns.get(x, z);
        if (isles.isEmpty()) return SkyIsles.NONE;
        return CoreHandle.require(core).skyIsles().density(isles, x, context.blockY(), z);
    }

    @Override
    public void fillArray(double[] array, ContextProvider provider) {
        provider.fillAllDirectly(array, this);
    }

    @Override
    public DensityFunction mapAll(Visitor visitor) {
        AtlasCore wiring = CoreHandle.wiring();
        return visitor.apply(wiring != null && wiring != core ? new SkyIsleDensity(wiring) : this);
    }

    @Override
    public double minValue() {
        return SkyIsles.NONE;
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
