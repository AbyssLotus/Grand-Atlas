package com.seaweed.grandatlas.world;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.seaweed.grandatlas.core.AtlasCore;
import com.seaweed.grandatlas.core.Caves;
import com.seaweed.grandatlas.core.CoastFeature;
import com.seaweed.grandatlas.core.ColumnSample;
import com.seaweed.grandatlas.core.macro.GreatPlateau;
import com.seaweed.grandatlas.core.micro.Features;
import java.util.Locale;
import javax.annotation.Nullable;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.levelgen.DensityFunction;

/**
 * {@code grandatlas:caves}: the core's own caves (see {@link Caves}). {@code fine} (pits and
 * fissures, wind-carved rock, and the cut of rivers and lakes) is thin or sharp-edged, so the
 * generated final density mins it in per block, outside the interpolation; {@code caverns} is
 * large and joins vanilla's cave mix on the interpolated grid.
 */
public final class GrandCaves implements DensityFunction, CoreHandle.CoreFunction {
    public enum Mode implements StringRepresentable {
        FINE, CAVERNS;

        public static final Codec<Mode> CODEC = StringRepresentable.fromEnum(Mode::values);

        @Override
        public String getSerializedName() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    public static final MapCodec<GrandCaves> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Mode.CODEC.fieldOf("mode").forGetter(c -> c.mode)
    ).apply(i, GrandCaves::new));
    public static final KeyDispatchDataCodec<GrandCaves> CODEC = KeyDispatchDataCodec.of(MAP_CODEC);

    // What a column holds, asked once per column, not once per block: each of these reads a
    // per-thread cache, and four such reads on every block of a 1,536-block column was the
    // largest single cost left in chunk generation (profiled 2026-11-10 on 26.3).
    private static final int NEAR = 1, MEGA = 2, FINE_NONE = 4;

    private record Col(ColumnSample column, int flags, int passLo, int passHi) {}

    private final Mode mode;
    private final @Nullable AtlasCore core;
    private final PerColumn<Col> columns;

    public GrandCaves(Mode mode) {
        this(mode, null);
    }

    private GrandCaves(Mode mode, @Nullable AtlasCore core) {
        this.mode = mode;
        this.core = core;
        this.columns = new PerColumn<>((x, z) -> {
            AtlasCore c = CoreHandle.require(this.core);
            int f = c.setPieces().nearColumn(x, z) ? NEAR : 0;
            if (mode == Mode.CAVERNS) {
                if (c.megaCaverns().openColumn(x, z)) f |= MEGA;
            } else if (c.caves().fineNone(x, z)) {
                f |= FINE_NONE;
            }
            boolean fine = mode == Mode.FINE;
            return new Col(c.column(x, z), f, fine ? c.passages().lowest(x, z) : 0, fine ? c.passages().highest(x, z) : 0);
        });
    }

    @Override
    public @Nullable AtlasCore boundCore() {
        return core;
    }

    @Override
    public double compute(FunctionContext context) {
        int x = context.blockX(), z = context.blockZ();
        Col col = columns.get(x, z);
        return value(CoreHandle.require(core), col.column, x, context.blockY(), z, col.flags, col.passLo, col.passHi);
    }

    @Override
    public void fillArray(double[] array, ContextProvider provider) {
        provider.fillAllDirectly(array, this);
    }

    @Override
    public DensityFunction mapAll(Visitor visitor) {
        AtlasCore wiring = CoreHandle.wiring();
        return visitor.apply(wiring != null && wiring != core ? new GrandCaves(mode, wiring) : this);
    }

    @Override
    public double minValue() {
        return -64;
    }

    @Override
    public double maxValue() {
        return Caves.NONE;
    }

    @Override
    public KeyDispatchDataCodec<? extends DensityFunction> codec() {
        return CODEC;
    }

    /**
     * Below this nothing here carves: the caverns' envelope starts 13 above the old floor, pits
     * stop 20 above it, and rivers, lakes, sinkholes, plateau walls and coasts cut down from
     * ground that never goes under {@code TERRAIN_FLOOR + 16}.
     */
    private int floor() {
        return mode == Mode.CAVERNS ? AtlasCore.TERRAIN_FLOOR + 13 : AtlasCore.TERRAIN_FLOOR - 64;
    }

    // (float) throughout: the 26.3 build hands these over as floats; keep the same values.
    private float value(AtlasCore core, ColumnSample column, int x, int y, int z, int flags, int passLo, int passHi) {
        // Set pieces carve themselves, and keep every other cave out but the passages through
        // their unprotected walls (their entrances).
        if ((flags & NEAR) != 0) {
            com.seaweed.grandatlas.core.SetPieces.Probe sp = core.setPieces().sample(x, y, z);
            if (sp.claimed) {
                if (mode == Mode.CAVERNS) return (float) Caves.NONE;
                if (sp.open) return -2f;
                if (sp.protect) return (float) Caves.NONE;
                return (float) Math.max(-64, Math.min(Caves.NONE, y < passLo || y > passHi ? Caves.NONE : core.passages().carve(column, x, y, z)));
            }
        }
        if (mode == Mode.CAVERNS) {
            double mega = (flags & MEGA) != 0 ? core.megaCaverns().carve(x, y, z) : Caves.NONE; // M7c, at any depth
            return (float) Math.max(-64, Math.min(Caves.NONE, y < floor() ? mega : Math.min(mega, core.caves().cavern(column, x, y, z))));
        }
        double p = y < passLo || y > passHi ? Caves.NONE : core.passages().carve(column, x, y, z); // M7b: at every depth
        if (y < floor()) return (float) Math.max(-64, Math.min(Caves.NONE, p));
        double d = Math.min(Math.min(Math.min((flags & FINE_NONE) != 0 ? Caves.NONE : core.caves().fine(x, y, z), AtlasCore.riverCut(column, y)), Features.carve(column, x, y, z)),
                Math.min(GreatPlateau.carveWall(column.wall(), x, y, z), CoastFeature.carve(column.shore(), x, y, z)));
        d = Math.min(d, p);
        return (float) Math.max(-64, Math.min(Caves.NONE, d));
    }
}
