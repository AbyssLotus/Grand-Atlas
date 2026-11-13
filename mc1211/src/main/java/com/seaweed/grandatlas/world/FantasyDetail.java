package com.seaweed.grandatlas.world;

import com.mojang.serialization.MapCodec;
import com.seaweed.grandatlas.core.AtlasCore;
import com.seaweed.grandatlas.core.ColumnSample;
import com.seaweed.grandatlas.core.SkyIsles;
import com.seaweed.grandatlas.core.landform.Tepui;
import com.seaweed.grandatlas.core.landform.VolcanicWastes;
import com.seaweed.grandatlas.core.noise.GradientNoise;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CaveVines;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.material.Fluids;

/**
 * {@code grandatlas:fantasy_detail} (M6): what the fantasy terrain holds that its shape can't.
 * <ul>
 *   <li><b>Volcanic wastes</b>: each crater's lake of lava, filled to just under the lowest point of
 *       its bowl, and lava in the channels cut down the cones, a block or so under their banks.</li>
 *   <li><b>Tepuis</b>: streams running across the summit to the rim, where they pour off the sheer
 *       wall in falls hundreds of blocks tall.</li>
 *   <li><b>Sky isles</b>: springs on their rims that pour off in falls to the ground far below, and
 *       glow berries and roots hanging from their undersides.</li>
 * </ul>
 * Each chunk writes only its own blocks.
 */
public record FantasyDetail() implements AtlasFeature {
    public static final MapCodec<FantasyDetail> CODEC = MapCodec.unit(FantasyDetail::new);
    private static final ConcurrentHashMap<Long, Integer> LAKES = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<Long, int[][]> SPRINGS = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<Long, int[][]> FALLS = new ConcurrentHashMap<>();

    @Override
    public MapCodec<FantasyDetail> codec() {
        return CODEC;
    }

    @Override
    public boolean place(WorldGenLevel level, ChunkGenerator chunkGenerator, RandomSource random, BlockPos origin) {
        AtlasCore core = CoreHandle.coreOf(level.getLevel());
        if (core == null) return false;
        int x0 = origin.getX() & ~15, z0 = origin.getZ() & ~15;
        boolean placed = false;
        VolcanicWastes wastes = (VolcanicWastes) core.landform("volcanic_wastes");
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int dz = 0; dz < 16; dz++) {
            for (int dx = 0; dx < 16; dx++) {
                int x = x0 + dx, z = z0 + dz;
                ColumnSample c = core.column(x, z);
                if (wastes != null && c.landform() == wastes && Double.isNaN(c.water())) placed |= lava(level, core, wastes, c, x, z, pos);
                List<SkyIsles.Isle> isles = core.skyIsles().near(x, z);
                if (!isles.isEmpty()) placed |= underside(level, core, isles, x, z, pos);
            }
        }
        placed |= springs(level, core, x0, z0);
        placed |= tepuiFalls(level, core, x0, z0);
        return placed;
    }

    // ---- lava

    private static boolean lava(WorldGenLevel level, AtlasCore core, VolcanicWastes wastes, ColumnSample c, int x, int z, BlockPos.MutableBlockPos pos) {
        VolcanicWastes.Volcano v = wastes.volcano(x, z);
        if (v == null) return false;
        BlockState lava = Blocks.LAVA.defaultBlockState();
        int ground = c.surfaceY();
        double d = Math.hypot(x - v.x(), z - v.z());
        int top = Integer.MIN_VALUE;
        if (d < v.crater() * 1.05) {
            // everything inside the rim that lies under the level: the bowl, filled edge to edge (filling
            // only the middle left a wall of lava standing over the lower ground round it)
            top = lakeLevel(core, v);
        } else {
            double depth = wastes.channel(x, z);
            if (depth >= 1.5) {
                // a block under the lower bank, never more than three deep
                int bank = Integer.MAX_VALUE;
                for (int[] o : new int[][] {{4, 0}, {-4, 0}, {0, 4}, {0, -4}}) bank = Math.min(bank, core.column(x + o[0], z + o[1]).surfaceY());
                top = Math.min(bank - 1, ground + 3);
            }
        }
        if (top <= ground) return false;
        boolean any = false;
        for (int y = ground + 1; y <= top; y++) {
            pos.set(x, y, z);
            BlockState here = level.getBlockState(pos);
            if (!here.isAir() && !here.canBeReplaced()) break;
            level.setBlock(pos, lava, 2);
            any = true;
        }
        if (any) {
            pos.set(x, ground, z);
            if (level.getBlockState(pos).isSolid()) level.setBlock(pos, Blocks.MAGMA_BLOCK.defaultBlockState(), 2);
        }
        return any;
    }

    /** A crater's lava level: a block under the lowest ground on a ring inside its rim, so the bowl holds it. */
    private static int lakeLevel(AtlasCore core, VolcanicWastes.Volcano v) {
        return LAKES.computeIfAbsent(v.key() ^ core.seed(), k -> {
            int low = Integer.MAX_VALUE;
            // the lowest point of the rim: the highest ground on each spoke out to past the rim, lowest over all spokes
            for (int i = 0; i < 96; i++) {
                double a = i * Math.PI * 2 / 96;
                int crest = Integer.MIN_VALUE;
                for (double f = 0.85; f <= 1.4; f += 0.05) {
                    int x = GradientNoise.floor(v.x() + Math.cos(a) * v.crater() * f), z = GradientNoise.floor(v.z() + Math.sin(a) * v.crater() * f);
                    crest = Math.max(crest, core.column(x, z).surfaceY());
                }
                low = Math.min(low, crest);
            }
            if (LAKES.size() > 2048) LAKES.clear();
            return low - 3;
        });
    }

    // ---- sky isles

    /** Glow berries and hanging roots under an isle's rock. */
    private static boolean underside(WorldGenLevel level, AtlasCore core, List<SkyIsles.Isle> isles, int x, int z, BlockPos.MutableBlockPos pos) {
        boolean any = false;
        for (SkyIsles.Isle isle : isles) {
            double[] s = core.skyIsles().span(isle, x, z);
            if (s == null) continue;
            long h = GradientNoise.hash(isle.seed(), x, z);
            double roll = (h >>> 11) * 0x1.0p-53;
            if (roll > 0.09) continue;
            // the lowest rock: scan up from under the computed bottom
            int y = (int) Math.floor(s[1]) - 3, end = (int) Math.floor(s[0]);
            while (y < end && level.getBlockState(pos.set(x, y, z)).isAir()) y++;
            if (y >= end || !level.getBlockState(pos.set(x, y, z)).isSolid()) continue;
            if (roll < 0.06) {
                int len = 2 + (int) ((h >>> 3) & 7);
                for (int k = 1; k <= len; k++) {
                    pos.set(x, y - k, z);
                    if (!level.getBlockState(pos).isAir()) break;
                    BlockState vine = (k == len ? Blocks.CAVE_VINES : Blocks.CAVE_VINES_PLANT).defaultBlockState()
                            .setValue(CaveVines.BERRIES, ((h >>> (12 + k)) & 3) == 0);
                    level.setBlock(pos, vine, 2);
                }
            } else if (level.getBlockState(pos.set(x, y - 1, z)).isAir()) {
                level.setBlock(pos, Blocks.HANGING_ROOTS.defaultBlockState(), 2);
            }
            any = true;
        }
        return any;
    }

    /** Springs on an isle's rim: a water source let into the turf at the edge, ticked so it pours off. */
    private static boolean springs(WorldGenLevel level, AtlasCore core, int x0, int z0) {
        boolean any = false;
        for (SkyIsles.Isle isle : core.skyIsles().near(x0 + 8, z0 + 8)) {
            for (int[] sp : springsOf(core, isle)) {
                int x = sp[0], z = sp[1];
                if (x < x0 || x >= x0 + 16 || z < z0 || z >= z0 + 16) continue;
                BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(x, sp[2] + 6, z);
                while (pos.getY() > sp[2] - 10 && !level.getBlockState(pos).isSolid()) pos.move(0, -1, 0);
                if (!level.getBlockState(pos).isSolid()) continue;
                level.setBlock(pos, Blocks.WATER.defaultBlockState(), 2);
                level.scheduleTick(pos.immutable(), Fluids.WATER, 1);
                any = true;
            }
        }
        return any;
    }

    /** {x, z, approximate top} for each of an isle's springs: two or three on a main isle, one on a third of the small ones. */
    private static int[][] springsOf(AtlasCore core, SkyIsles.Isle isle) {
        return SPRINGS.computeIfAbsent(isle.seed() ^ core.seed(), k -> {
            long h = GradientNoise.mix(isle.seed() ^ 0x5B1A6L);
            int n = isle.main() ? 2 + (int) (h & 1) : ((h & 3) == 0 ? 1 : 0);
            int[][] out = new int[n][];
            int m = 0;
            for (int i = 0; i < n; i++) {
                double a = ((GradientNoise.mix(h + i) >>> 11) * 0x1.0p-53) * Math.PI * 2;
                // From the rim inward to the first column the isle covers, then two blocks further in.
                for (double r = isle.r() * 1.3; r > 0; r -= 1) {
                    double x = isle.x() + Math.cos(a) * r, z = isle.z() + Math.sin(a) * r;
                    double[] s = core.skyIsles().span(isle, x, z);
                    if (s == null) continue;
                    double x2 = isle.x() + Math.cos(a) * (r - 2), z2 = isle.z() + Math.sin(a) * (r - 2);
                    out[m++] = new int[] {GradientNoise.floor(x2), GradientNoise.floor(z2), (int) Math.floor(s[0])};
                    break;
                }
            }
            if (SPRINGS.size() > 4096) SPRINGS.clear();
            return java.util.Arrays.copyOf(out, m);
        });
    }

    // ---- tepui falls

    /**
     * A tepui's falls: for each, a stream three wide let into the summit for its last 14 blocks to
     * the rim, its water sources set a block into the rock so the rock holds them on both sides,
     * the ones at the lip ticked so they pour over the wall.
     */
    private static boolean tepuiFalls(WorldGenLevel level, AtlasCore core, int x0, int z0) {
        if (!(core.landform("tepui") instanceof Tepui tepui)) return false;
        ColumnSample mid = core.column(x0 + 8, z0 + 8);
        if (mid.landform() != tepui) return false;
        Tepui.Mesa m = tepui.tepui(core.mosaic().province(mid.provinceX(), mid.provinceZ()), x0 + 8, z0 + 8);
        if (m == null) return false;
        boolean any = false;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int[] f : fallsOf(core, tepui, m)) {
            int x = f[0], z = f[1];
            if (x < x0 || x >= x0 + 16 || z < z0 || z >= z0 + 16) continue;
            pos.set(x, f[2] + 8, z);
            while (pos.getY() > f[2] - 14 && !level.getBlockState(pos).isSolid()) pos.move(0, -1, 0);
            if (!level.getBlockState(pos).isSolid()) continue;
            BlockPos at = pos.immutable();
            // Clear what grows over it, then the water.
            for (int k = 1; k <= 2; k++) {
                BlockState above = level.getBlockState(at.above(k));
                if (!above.isAir() && above.canBeReplaced()) level.setBlock(at.above(k), Blocks.AIR.defaultBlockState(), 2);
            }
            level.setBlock(at, Blocks.WATER.defaultBlockState(), 2);
            if (f[3] == 1) level.scheduleTick(at, Fluids.WATER, 1);
            any = true;
        }
        return any;
    }

    /** {x, z, approximate top, at the lip (1) or not} for every block of a tepui's fall streams. */
    private static int[][] fallsOf(AtlasCore core, Tepui tepui, Tepui.Mesa m) {
        return FALLS.computeIfAbsent(m.seed() ^ core.seed(), k -> {
            java.util.List<int[]> out = new java.util.ArrayList<>();
            double[] at = tepui.centre(m); // in real blocks
            for (int i = 0; i < m.falls(); i++) {
                double a = m.fallAngle(i), ux = Math.cos(a), uz = Math.sin(a);
                // The rim along this heading: the outermost column still on the summit.
                double rim = -1;
                for (double r = tepui.rim(m, a) + 60; r > m.radius() * 0.3; r -= 1) { // the warp bends the outline: start well out
                    ColumnSample c = core.column(GradientNoise.floor(at[0] + ux * r), GradientNoise.floor(at[1] + uz * r));
                    if (c.landform() == tepui && c.zone() == com.seaweed.grandatlas.core.landform.Zone.MESA_TOP) {
                        rim = r;
                        break;
                    }
                }
                if (rim < 0) continue;
                java.util.Set<Long> seen = new java.util.HashSet<>();
                for (double r = rim - 14; r <= rim + 0.01; r += 0.5) {
                    for (int w = -1; w <= 1; w++) {
                        int x = GradientNoise.floor(at[0] + ux * r - uz * w), z = GradientNoise.floor(at[1] + uz * r + ux * w);
                        if (!seen.add(((long) x << 32) ^ (z & 0xFFFFFFFFL))) continue;
                        boolean lip = r > rim - 1.5;
                        out.add(new int[] {x, z, core.column(x, z).surfaceY(), lip ? 1 : 0});
                    }
                }
            }
            if (FALLS.size() > 4096) FALLS.clear();
            return out.toArray(new int[0][]);
        });
    }
}
