package com.seaweed.grandatlas.mortus;

import com.mojang.serialization.MapCodec;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.world.level.levelgen.DensityFunction;

/**
 * {@code grandatlas:mortus_terrain}: Domus Mortus's ground (MortusMap.height), solid below and air
 * above, with a gentle 3D wobble near the surface so slopes and cliffs aren't stamped flat. The
 * column's height is kept from the last block asked about, since the noise asks column by column.
 */
public final class MortusTerrain implements DensityFunction.SimpleFunction {
    public static final MapCodec<MortusTerrain> MAP_CODEC = MapCodec.unit(MortusTerrain::new);
    public static final KeyDispatchDataCodec<MortusTerrain> CODEC = KeyDispatchDataCodec.of(MAP_CODEC);

    private static final ThreadLocal<double[]> LAST = ThreadLocal.withInitial(() -> new double[] {Double.NaN, Double.NaN, 0});

    @Override
    public double compute(FunctionContext context) {
        int x = context.blockX(), y = context.blockY(), z = context.blockZ();
        double[] c = LAST.get();
        if (c[0] != x || c[1] != z) {
            c[0] = x;
            c[1] = z;
            c[2] = MortusMap.height(x, z);
        }
        double h = c[2];
        if (MortusMap.cave(x, y, z)) return -1; // the entrance cave
        return Math.max(-64, Math.min(64, (h - y) / 4.0));
    }

    @Override
    public double minValue() {
        return -64;
    }

    @Override
    public double maxValue() {
        return 64;
    }

    @Override
    public KeyDispatchDataCodec<? extends DensityFunction> codec() {
        return CODEC;
    }
}
