package com.seaweed.grandatlas.world;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.seaweed.grandatlas.core.AtlasCore;
import com.seaweed.grandatlas.core.BiomeKind;
import java.util.EnumSet;
import java.util.HashMap;
import javax.annotation.Nullable;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.SurfaceRules;

/**
 * {@code grandatlas:cave_gate}: runs the cave biomes' rock rules only where one of those biomes
 * can be.
 *
 * <p>The cave rules are a list of "in this biome, do that", one per cave biome, and they run on
 * every block of rock in the world. Asking a block's biome is costly (the game blurs biome edges
 * with eight hashed samples a block), and in a chunk holding any cave biome every block of rock
 * asked: with the 1,536-block-deep world that was about a quarter of all chunk generation
 * (profiled 2026-11-10 on 26.3).
 *
 * <p>This wrapper asks the core first, per biome cell (4x4x4 blocks, remembered per chunk): a
 * block's blurred biome can only come from the eight cells round it, so if none of those is a cave
 * biome with a rule, the rules can't apply and the block is skipped without asking. Where one is,
 * the rules run exactly as before, on the game's own (blurred) biome, so nothing changes in what
 * is generated.
 */
public record CaveGate(SurfaceRules.RuleSource rule) implements SurfaceRules.RuleSource {
    public static final MapCodec<CaveGate> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            SurfaceRules.RuleSource.CODEC.fieldOf("rule").forGetter(CaveGate::rule)
    ).apply(i, CaveGate::new));
    public static final KeyDispatchDataCodec<CaveGate> CODEC = KeyDispatchDataCodec.of(MAP_CODEC);

    /** The cave biomes that have a rule (tools/gen_data.py CAVE_RULES). */
    private static final EnumSet<BiomeKind> RULED = EnumSet.of(BiomeKind.ICE_CAVES, BiomeKind.SANDSTONE_CAVES, BiomeKind.CRYSTAL_CAVES,
            BiomeKind.MAGMA_CHAMBERS, BiomeKind.ABYSSAL_SHORES, BiomeKind.FUNGAL_FOREST, BiomeKind.ROOT_CAVES, BiomeKind.GLOWWORM_GROTTO,
            BiomeKind.WORLDROOT_HALL, BiomeKind.HANGING_PEAKS, BiomeKind.AURORA_VAULT, BiomeKind.THE_HOLLOW, BiomeKind.TITANS_GRAVE,
            BiomeKind.SULFUR_CAVES);

    @Override
    public KeyDispatchDataCodec<? extends SurfaceRules.RuleSource> codec() {
        return CODEC;
    }

    @Override
    public SurfaceRules.SurfaceRule apply(SurfaceRules.Context context) {
        SurfaceRules.SurfaceRule inner = rule.apply(context);
        AtlasCore core = CoreHandle.coreFrom(context.randomState);
        return core == null ? inner : new Evaluator(core, inner, RULED);
    }

    static final class Evaluator implements SurfaceRules.SurfaceRule {
        private static final int QY0 = AtlasCore.MIN_Y >> 2, QN = ((1024 - AtlasCore.MIN_Y) >> 2) + 2;
        private final AtlasCore core;
        private final SurfaceRules.SurfaceRule inner;
        private final EnumSet<BiomeKind> kinds;
        /** Per biome-cell column: for each cell up it, 0 not asked yet, 1 no ruled biome, 2 one is there. */
        private final HashMap<Long, byte[]> cells = new HashMap<>();
        // the four cell columns round the block column being walked
        private int bx = Integer.MIN_VALUE, bz = Integer.MIN_VALUE;
        private final byte[][] near = new byte[4][];
        private final int[] nearX = new int[4], nearZ = new int[4];

        Evaluator(AtlasCore core, SurfaceRules.SurfaceRule inner, EnumSet<BiomeKind> kinds) {
            this.kinds = kinds;
            this.core = core;
            this.inner = inner;
        }

        @Override
        public @Nullable BlockState tryApply(int x, int y, int z) {
            if (x != bx || z != bz) {
                bx = x;
                bz = z;
                int qx = (x - 2) >> 2, qz = (z - 2) >> 2;
                for (int k = 0; k < 4; k++) {
                    nearX[k] = qx + (k & 1);
                    nearZ[k] = qz + (k >> 1);
                    long key = ((long) nearX[k] << 32) ^ (nearZ[k] & 0xFFFFFFFFL);
                    near[k] = cells.computeIfAbsent(key, q -> new byte[QN]);
                }
            }
            int qy = ((y - 2) >> 2) - QY0;
            if (qy < 0 || qy + 1 >= QN) return inner.tryApply(x, y, z);
            for (int k = 0; k < 4; k++) {
                byte[] col = near[k];
                for (int dy = 0; dy < 2; dy++) {
                    byte v = col[qy + dy];
                    if (v == 0) {
                        int cx = nearX[k] << 2, cy = (qy + dy + QY0) << 2, cz = nearZ[k] << 2;
                        v = kinds.contains(core.biomeAt(core.column(cx, cz), cx, cy, cz)) ? (byte) 2 : (byte) 1;
                        col[qy + dy] = v;
                    }
                    if (v == 2) return inner.tryApply(x, y, z);
                }
            }
            return null;
        }
    }
}
