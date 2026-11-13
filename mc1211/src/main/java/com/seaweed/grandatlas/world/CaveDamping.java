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
 * {@code grandatlas:cave_damping}: the core's {@link AtlasCore#caveDamping} bias. The generated
 * final density adds it to vanilla's deep caves, surface entrances and noodles, so tall
 * mountains stay mostly solid. The noodles' copy ({@code noodle: true}) also keeps them a few
 * blocks under open ground ({@link AtlasCore#noodleDamping}).
 */
public final class CaveDamping implements DensityFunction, CoreHandle.CoreFunction {
    public static final MapCodec<CaveDamping> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.BOOL.optionalFieldOf("noodle", false).forGetter(c -> c.noodle)
    ).apply(i, CaveDamping::new));
    public static final KeyDispatchDataCodec<CaveDamping> CODEC = KeyDispatchDataCodec.of(MAP_CODEC);

    /**
     * Below the old floor the damping is always 0 (it fades out under y 96, and the river,
     * plateau-wall and noodle terms all sit within 16 blocks of ground that never goes under
     * {@code TERRAIN_FLOOR + 16}), so the deep skips the column's damping.
     */
    private static final int DEEP = AtlasCore.TERRAIN_FLOOR;

    /** What a column holds, asked once per column. */
    private record Col(ColumnSample column, double lake, double bed, boolean piece) {}

    private final boolean noodle;
    private final @Nullable AtlasCore core;
    private final PerColumn<Col> columns;

    public CaveDamping(boolean noodle) {
        this(noodle, null);
    }

    private CaveDamping(boolean noodle, @Nullable AtlasCore core) {
        this.noodle = noodle;
        this.core = core;
        this.columns = new PerColumn<>((x, z) -> {
            AtlasCore c = CoreHandle.require(this.core);
            // M7d: vanilla caves keep out of a cavern lake's bed and water.
            double lake = c.megaCaverns().water(x, z);
            double bed = Double.isNaN(lake) ? 0 : c.megaCaverns().floor(x, z);
            return new Col(c.column(x, z), lake, bed, c.setPieces().nearColumn(x, z));
        });
    }

    @Override
    public @Nullable AtlasCore boundCore() {
        return core;
    }

    @Override
    public double compute(FunctionContext context) {
        AtlasCore c = CoreHandle.require(core);
        int x = context.blockX(), y = context.blockY(), z = context.blockZ();
        Col col = columns.get(x, z);
        if (!Double.isNaN(col.lake) && y >= col.bed - 10 && y <= col.lake + 2) return 2;
        if (col.piece && c.setPieces().sample(x, y, z).claimed) return 2; // vanilla caves keep out of set pieces
        if (y < DEEP) return 0;
        // (float): the 26.3 build hands these over as floats; keep the same values.
        return (float) (noodle ? c.noodleDamping(col.column, y) : c.caveDamping(col.column, y));
    }

    @Override
    public void fillArray(double[] array, ContextProvider provider) {
        provider.fillAllDirectly(array, this);
    }

    @Override
    public DensityFunction mapAll(Visitor visitor) {
        AtlasCore wiring = CoreHandle.wiring();
        return visitor.apply(wiring != null && wiring != core ? new CaveDamping(noodle, wiring) : this);
    }

    @Override
    public double minValue() {
        return 0;
    }

    @Override
    public double maxValue() {
        return 2;
    }

    @Override
    public KeyDispatchDataCodec<? extends DensityFunction> codec() {
        return CODEC;
    }
}
