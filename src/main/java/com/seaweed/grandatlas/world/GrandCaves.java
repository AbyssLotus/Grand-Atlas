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
        @Override
        public void sampleVolume(SamplerContext context, DensityBuffer out, DensityVolume volume) {
            int index = 0;
            for (int z = 0; z < volume.sizeZ(); z++) {
                int blockZ = volume.blockZ(z);
                for (int x = 0; x < volume.sizeX(); x++) {
                    int blockX = volume.blockX(x);
                    ColumnSample column = core.column(blockX, blockZ);
                    for (int y = 0; y < volume.sizeY(); y++) {
                        out.set(index++, value(column, blockX, volume.blockY(y), blockZ));
                    }
                }
            }
        }

        @Override
        public float sampleValue(SamplerContext context, int blockX, int blockY, int blockZ) {
            return value(core.column(blockX, blockZ), blockX, blockY, blockZ);
        }

        /**
         * Below this nothing here carves: the caverns' envelope starts 13 above the old floor, pits
         * stop 20 above it, and rivers, lakes, sinkholes, plateau walls and coasts cut down from
         * ground that never goes under {@code TERRAIN_FLOOR + 16}.
         */
        private int floor() {
            return mode == Mode.CAVERNS ? AtlasCore.TERRAIN_FLOOR + 13 : AtlasCore.TERRAIN_FLOOR - 64;
        }

        private float value(ColumnSample column, int x, int y, int z) {
            // Set pieces carve themselves, and keep every other cave out but the passages through
            // their unprotected walls (their entrances).
            if (core.setPieces().nearColumn(x, z)) {
                com.seaweed.grandatlas.core.SetPieces.Probe sp = core.setPieces().sample(x, y, z);
                if (sp.claimed) {
                    if (mode == Mode.CAVERNS) return (float) Caves.NONE;
                    if (sp.open) return -2f;
                    if (sp.protect) return (float) Caves.NONE;
                    return (float) Math.max(-64, Math.min(Caves.NONE, core.passages().carve(column, x, y, z)));
                }
            }
            if (mode == Mode.CAVERNS) {
                double mega = core.megaCaverns().carve(x, y, z); // M7c, at any depth
                return (float) Math.max(-64, Math.min(Caves.NONE, y < floor() ? mega : Math.min(mega, core.caves().cavern(column, x, y, z))));
            }
            double p = core.passages().carve(column, x, y, z); // M7b: at every depth
            if (y < floor()) return (float) Math.max(-64, Math.min(Caves.NONE, p));
            double d = Math.min(Math.min(Math.min(core.caves().fine(x, y, z), AtlasCore.riverCut(column, y)), Features.carve(column, x, y, z)),
                    Math.min(GreatPlateau.carveWall(column.wall(), x, y, z), CoastFeature.carve(column.shore(), x, y, z)));
            d = Math.min(d, p);
            return (float) Math.max(-64, Math.min(Caves.NONE, d));
        }
    }
}
