package com.seaweed.grandatlas.world;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.seaweed.grandatlas.core.AtlasCore;
import com.seaweed.grandatlas.core.micro.Features;
import com.seaweed.grandatlas.core.macro.GreatPlateau;
import com.seaweed.grandatlas.core.Caves;
import com.seaweed.grandatlas.core.CoastFeature;
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
 * {@code grandatlas:caves}: the core's own caves (see {@link Caves}). {@code fine} (pits and
 * fissures, wind-carved rock, and the cut of rivers and lakes) is thin or sharp-edged, so the
 * generated final density mins it in per block, outside the interpolation; {@code caverns} is
 * large and joins vanilla's cave mix on the interpolated grid.
 */
public record GrandCaves(Mode mode) implements DensityFunction {
    public enum Mode implements StringRepresentable {
        FINE, CAVERNS;

        public static final Codec<Mode> CODEC = StringRepresentable.fromEnum(Mode::values);

        @Override
        public String getSerializedName() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    public static final MapCodec<GrandCaves> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Mode.CODEC.fieldOf("mode").forGetter(GrandCaves::mode)
    ).apply(i, GrandCaves::new));

    @Override
    public DensitySampler compileSampler(CompileContext context) {
        return new Sampler(CoreHandle.coreFor(context), mode);
    }

    @Override
    public DensityFunction rewriteChildren(DfRewriteRule rule) {
        return this;
    }

    @Override
    public Interval range() {
        return Interval.of(-64, (float) Caves.NONE);
    }

    @Override
    public @Axes int domainAxes() {
        return ALL_AXES;
    }

    @Override
    public MapCodec<GrandCaves> codec() {
        return CODEC;
    }

    private record Sampler(AtlasCore core, Mode mode) implements CoreHandle.CoreSampler {
        // What a column holds, asked once per column, not once per block: each of these reads a
        // per-thread cache, and four such reads on every block of a 1,536-block column was the
        // largest single cost left in chunk generation (profiled 2026-11-10).
        private static final int NEAR = 1, MEGA = 2, FINE_NONE = 4;

        private int flags(int x, int z) {
            int f = core.setPieces().nearColumn(x, z) ? NEAR : 0;
            if (mode == Mode.CAVERNS) {
                if (core.megaCaverns().openColumn(x, z)) f |= MEGA;
            } else if (core.caves().fineNone(x, z)) {
                f |= FINE_NONE;
            }
            return f;
        }

        @Override
        public void sampleVolume(SamplerContext context, DensityBuffer out, DensityVolume volume) {
            int index = 0;
            for (int z = 0; z < volume.sizeZ(); z++) {
                int blockZ = volume.blockZ(z);
                for (int x = 0; x < volume.sizeX(); x++) {
                    int blockX = volume.blockX(x);
                    ColumnSample column = core.column(blockX, blockZ);
                    int f = flags(blockX, blockZ);
                    int lo = mode == Mode.FINE ? core.passages().lowest(blockX, blockZ) : 0, hi = mode == Mode.FINE ? core.passages().highest(blockX, blockZ) : 0;
                    for (int y = 0; y < volume.sizeY(); y++) {
                        out.set(index++, value(column, blockX, volume.blockY(y), blockZ, f, lo, hi));
                    }
                }
            }
        }

        @Override
        public float sampleValue(SamplerContext context, int blockX, int blockY, int blockZ) {
            boolean fine = mode == Mode.FINE;
            return value(core.column(blockX, blockZ), blockX, blockY, blockZ, flags(blockX, blockZ),
                    fine ? core.passages().lowest(blockX, blockZ) : 0, fine ? core.passages().highest(blockX, blockZ) : 0);
        }

        /**
         * Below this nothing here carves: the caverns' envelope starts 13 above the old floor, pits
         * stop 20 above it, and rivers, lakes, sinkholes, plateau walls and coasts cut down from
         * ground that never goes under {@code TERRAIN_FLOOR + 16}.
         */
        private int floor() {
            return mode == Mode.CAVERNS ? AtlasCore.TERRAIN_FLOOR + 13 : AtlasCore.TERRAIN_FLOOR - 64;
        }

        private float value(ColumnSample column, int x, int y, int z, int flags, int passLo, int passHi) {
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
}
