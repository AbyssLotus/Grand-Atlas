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

    /**
     * Vanilla's noise caves are off (cave rework C1, 2026-11-17: the user wants them "as little as
     * possible"; core/Solution and the other provinces fill the underground). The bias added to every
     * vanilla cave term is then {@link #OFF}, which no cave value comes near. Dev:
     * {@code -Dgrandatlas.vanillaCaves=true} brings them back for comparison.
     */
    static final boolean VANILLA = Boolean.getBoolean("grandatlas.vanillaCaves");
    static final double OFF = 10;

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
        if (!VANILLA) return OFF;
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
        if (!VANILLA) {
            java.util.Arrays.fill(array, OFF);
            return;
        }
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
        return VANILLA ? 2 : OFF;
    }

    @Override
    public KeyDispatchDataCodec<? extends DensityFunction> codec() {
        return CODEC;
    }
}
