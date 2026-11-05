package com.seaweed.grandatlas.world;

import com.mojang.serialization.MapCodec;
import com.seaweed.grandatlas.core.AtlasCore;
import com.seaweed.grandatlas.core.BiomeKind;
import com.seaweed.grandatlas.core.ColumnSample;
import com.seaweed.grandatlas.core.micro.Features;
import com.seaweed.grandatlas.core.micro.MesoKind;
import com.seaweed.grandatlas.core.micro.MesoSite;
import com.seaweed.grandatlas.core.micro.MicroFeature;
import com.seaweed.grandatlas.core.micro.MicroKind;
import com.seaweed.grandatlas.core.noise.GradientNoise;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.MultifaceBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.VineBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.densityfunction.SamplerContext;
import net.minecraft.world.level.levelgen.feature.Feature;
import org.jspecify.annotations.Nullable;

/**
 * {@code grandatlas:micro_detail}: the detail level of the Macro → Meso → Micro → Detail chain
 * (docs/MICRO_FEATURES.md). It dresses each meso site's micro features and scatters the site's
 * own detail: lily pads and reeds round ponds, vines and moss down sinkholes and glow lichen over
 * cenote pools, cinders on cones, basalt columns, boulders and fallen logs.
 *
 * <p>Runs at the local_modifications step, after {@code perched_water} filled the water and
 * before trees and plants. Everything is chosen from (site, position) hashes and the core's
 * columns, and each chunk writes only its own blocks, so an object across a chunk border comes
 * out whole whichever side generates first.
 */
public record MicroDetail() implements Feature {
    public static final MapCodec<MicroDetail> CODEC = MapCodec.unit(MicroDetail::new);
    private static final int[][] SIDES = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

    private static volatile @Nullable ServerLevel lastLevel;
    private static volatile @Nullable AtlasCore lastCore;

    @Override
    public MapCodec<MicroDetail> codec() {
        return CODEC;
    }

    @Override
    public boolean place(WorldGenLevel level, ChunkGenerator chunkGenerator, RandomSource random, BlockPos origin) {
        AtlasCore core = core(level.getLevel());
        if (core == null) return false;
        int x0 = origin.getX() & ~15, z0 = origin.getZ() & ~15;
        // Cells are 800 blocks, a multiple of 16: a chunk lies in one cell, so in at most one site.
        MesoSite site = core.features().site(Features.cell(x0), Features.cell(z0));
        if (site == null) return false;
        double nx = Math.max(x0, Math.min(x0 + 15, site.x())), nz = Math.max(z0, Math.min(z0 + 15, site.z()));
        if (Math.hypot(nx - site.x(), nz - site.z()) > site.radius() + 2) return false;
        Ctx ctx = new Ctx(level, core, site, x0, z0);
        for (int dz = 0; dz < 16; dz++) {
            for (int dx = 0; dx < 16; dx++) {
                int x = x0 + dx, z = z0 + dz;
                ColumnSample c = core.column(x, z);
                MicroFeature m = c.micro();
                if (m != null && m.present()) {
                    switch (m.kind) {
                        case POND -> pond(ctx, x, z, c, m);
                        case SINKHOLE, CENOTE -> pit(ctx, x, z, c, m);
                        case GROTTO -> grotto(ctx, x, z, c, m);
                        case TRAVERTINE -> travertine(ctx, x, z, c, m);
                        case CINDER_CONE -> cinders(ctx, x, z, c, m);
                        case HOODOO -> hoodoo(ctx, x, z, c, m);
                        case ERRATIC -> erratic(ctx, x, z, m);
                        case TOR -> tor(ctx, x, z, m);
                        case GIANT_TREE -> forestFloor(ctx, x, z, c, 0.75);
                        case GULLY -> gully(ctx, x, z, c, m);
                        case BASALT_COLUMNS -> basalt(ctx, x, z, c, m);
                        case CALDERA -> caldera(ctx, x, z, c, m);
                        default -> {
                        }
                    }
                } else if (site.kind() == MesoKind.CINDER_FIELD && site.covers(x, z)) {
                    ash(ctx, x, z, c);
                } else if (site.covers(x, z)) {
                    switch (site.kind()) {
                        case DEADFALL -> forestFloor(ctx, x, z, c, 0.2);
                        case MUSHROOM_HOLLOW -> mycelium(ctx, x, z, c);
                        case GLADE -> fairyRing(ctx, x, z, c);
                        default -> {
                        }
                    }
                }
            }
        }
        structures(ctx);
        scatter(ctx);
        return true;
    }

    /** One chunk's work on one site. */
    private record Ctx(WorldGenLevel level, AtlasCore core, MesoSite site, int x0, int z0) {
        boolean mine(int x, int z) {
            return x >= x0 && x < x0 + 16 && z >= z0 && z < z0 + 16;
        }

        long hash(int x, int y, int z, int salt) {
            return GradientNoise.hash(site.seed() + salt * 0x9E3779B97F4A7C15L, x, y, z);
        }

        /** 0..1 from a hash. */
        double roll(int x, int y, int z, int salt) {
            return (hash(x, y, z, salt) >>> 11) * 0x1.0p-53;
        }

        BlockState get(int x, int y, int z) {
            return level.getBlockState(new BlockPos(x, y, z));
        }

        void set(int x, int y, int z, BlockState state) {
            level.setBlock(new BlockPos(x, y, z), state, 2);
        }

        /** The column's top solid block near the core's surface, or MIN_VALUE. */
        int top(int x, int z, ColumnSample c) {
            for (int y = c.surfaceY() + 5; y >= c.surfaceY() - 8; y--) {
                BlockState s = get(x, y, z);
                if (!s.isAir() && s.getFluidState().isEmpty() && !s.canBeReplaced()) return y;
            }
            return Integer.MIN_VALUE;
        }
    }

    private static boolean open(BlockState s) {
        return s.isAir() || s.canBeReplaced() && s.getFluidState().isEmpty();
    }

    // ---- micro features

    /** Lily pads out on the water (over still vanilla water, which they need), reeds and grass on the shore. */
    private static void pond(Ctx ctx, int x, int z, ColumnSample c, MicroFeature m) {
        if (ctx.site().kind() == MesoKind.TARN_BASIN || ctx.site().kind() == MesoKind.LAKE_DISTRICT) {
            tarnShore(ctx, x, z, c, m);
            return;
        }
        if (!Double.isNaN(c.water())) {
            int wy = (int) Math.floor(c.water());
            if (ctx.roll(x, wy, z, 1) < 0.07 && ctx.get(x, wy + 1, z).isAir() && !ctx.get(x, wy, z).getFluidState().isEmpty()) {
                ctx.set(x, wy, z, Blocks.WATER.defaultBlockState());
                ctx.set(x, wy + 1, z, Blocks.LILY_PAD.defaultBlockState());
            }
            return;
        }
        int shore = Integer.MIN_VALUE;
        for (int[] s : SIDES) {
            ColumnSample n = ctx.core().column(x + s[0], z + s[1]);
            if (!Double.isNaN(n.water())) shore = Math.max(shore, (int) Math.floor(n.water()));
        }
        int ty = ctx.top(x, z, c);
        if (ty == Integer.MIN_VALUE || !ctx.get(x, ty + 1, z).isAir()) return;
        BlockState ground = ctx.get(x, ty, z);
        boolean soil = ground.is(BlockTags.DIRT) || ground.is(BlockTags.SAND);
        double roll = ctx.roll(x, ty, z, 2);
        if (shore == ty && soil && roll < 0.3) {
            int tall = 1 + (int) (ctx.roll(x, ty, z, 3) * 3);
            for (int k = 1; k <= tall; k++) ctx.set(x, ty + k, z, Blocks.SUGAR_CANE.defaultBlockState());
        } else if (shore >= ty - 1 && soil && roll < 0.65) {
            ctx.set(x, ty + 1, z, (roll < 0.45 ? Blocks.SHORT_GRASS : Blocks.FERN).defaultBlockState());
        }
    }

    /**
     * A caldera's inner wall: the cliff's faces in the banded rock of old lava flows and ash (grey
     * andesite and tuff, dark basalt, the odd pale or rust-brown layer, in wavy level bands), and
     * the talus under it and the lake's shore loose gravel and stone in patches, nothing growing.
     * The crest beyond the wall keeps its own ground, so the forest runs to the edge.
     */
    private static void caldera(Ctx ctx, int x, int z, ColumnSample c, MicroFeature m) {
        double dx = x + 0.5 - m.x, dz = z + 0.5 - m.z, a = Math.atan2(dz, dx), s = m.calderaRim(a) - Math.hypot(dx, dz);
        if (s < 0) return;
        int ty = ctx.top(x, z, c);
        if (ty == Integer.MIN_VALUE) return;
        long seed = ctx.site().seed();
        boolean cliff = s < m.calderaRun(a) + 1;
        int bottom = cliff ? Math.max(ty - 70, (int) Math.floor(m.level()) - 3) : ty - 1;
        for (int y = ty; y >= bottom; y--) {
            BlockState st = ctx.get(x, y, z);
            if (st.isAir() || !st.getFluidState().isEmpty() || !st.isSolid()) continue;
            boolean exposed = y >= ty - 1;
            for (int[] o : SIDES) {
                if (exposed) break;
                BlockState n = ctx.get(x + o[0], y, z + o[1]);
                exposed = n.isAir() || !n.getFluidState().isEmpty();
            }
            if (!exposed) continue;
            Block b;
            if (!cliff) {
                double n = GradientNoise.noise2(seed ^ 0xCA5L, x / 6.0, z / 6.0) + 0.3 * GradientNoise.noise2(seed ^ 0xCA6L, x / 2.5, z / 2.5);
                b = n < -0.25 ? Blocks.GRAVEL : n < 0.15 ? Blocks.ANDESITE : n < 0.45 ? Blocks.TUFF : Blocks.COBBLESTONE;
                if (b == Blocks.GRAVEL && ctx.get(x, y - 1, z).isAir()) b = Blocks.ANDESITE;
            } else {
                double band = GradientNoise.noise3(seed ^ 0xCA7L, x / 70.0, y / 3.2, z / 70.0) + 0.25 * GradientNoise.noise3(seed ^ 0xCA8L, x / 9.0, y / 2.0, z / 9.0);
                b = band < -0.45 ? Blocks.SMOOTH_BASALT : band < -0.1 ? Blocks.ANDESITE : band < 0.25 ? Blocks.TUFF : band < 0.5 ? Blocks.STONE
                        : band < 0.62 ? Blocks.DYED_TERRACOTTA.lightGray() : Blocks.DYED_TERRACOTTA.brown();
            }
            ctx.set(x, y, z, b.defaultBlockState());
        }
        BlockState above = ctx.get(x, ty + 1, z);
        if (above.canBeReplaced() && above.getFluidState().isEmpty() && !above.isAir()) ctx.set(x, ty + 1, z, Blocks.AIR.defaultBlockState());
    }

    /** Pale limestone where a sinkhole's rock is exposed: calcite, diorite and stone in broad patches. */
    private static void limestone(Ctx ctx, int x, int z, ColumnSample c, MicroFeature m) {
        int bottom = (int) Math.floor(m.ground - m.h), top = (int) Math.floor(m.ground) - 3;
        long seed = ctx.site().seed();
        for (int y = bottom; y <= top; y++) {
            BlockState s = ctx.get(x, y, z);
            if (s.isAir() || !s.getFluidState().isEmpty() || !s.isSolid()) continue;
            boolean exposed = false;
            for (int[] o : SIDES) {
                BlockState n = ctx.get(x + o[0], y, z + o[1]);
                if (n.isAir() || !n.getFluidState().isEmpty() || n.is(Blocks.VINE)) {
                    exposed = true;
                    break;
                }
            }
            if (!exposed && !ctx.get(x, y + 1, z).isAir()) continue;
            double n = GradientNoise.noise3(seed ^ 0x41L, x / 6.0, y / 4.0, z / 6.0);
            ctx.set(x, y, z, (n < -0.2 ? Blocks.DIORITE : n < 0.35 ? Blocks.CALCITE : Blocks.STONE).defaultBlockState());
        }
    }

    /** A mountain tarn's shore: bare stone, gravel and cobbles, no reeds. */
    private static void tarnShore(Ctx ctx, int x, int z, ColumnSample c, MicroFeature m) {
        if (!Double.isNaN(c.water())) return;
        double d = Math.hypot(x + 0.5 - m.x, z + 0.5 - m.z);
        if (d > m.outline(Math.atan2(z + 0.5 - m.z, x + 0.5 - m.x)) * 1.3) return;
        int ty = ctx.top(x, z, c);
        if (ty == Integer.MIN_VALUE) return;
        double n = GradientNoise.noise2(ctx.site().seed() ^ 0x51L, x / 5.0, z / 5.0);
        Block b = n < -0.2 ? Blocks.GRAVEL : n < 0.25 ? Blocks.STONE : n < 0.5 ? Blocks.COBBLESTONE : Blocks.ANDESITE;
        for (int k = 0; k < 2; k++) {
            BlockState s = ctx.get(x, ty - k, z);
            if (s.isAir() || !s.getFluidState().isEmpty()) break;
            if (b == Blocks.GRAVEL && ctx.get(x, ty - k - 1, z).isAir()) break;
            ctx.set(x, ty - k, z, b.defaultBlockState());
        }
        if (ctx.get(x, ty + 1, z).canBeReplaced() && ctx.get(x, ty + 1, z).getFluidState().isEmpty()) ctx.set(x, ty + 1, z, Blocks.AIR.defaultBlockState());
    }

    /**
     * Fills one column's slice of a rounded rock body, {@code blob(dx, dy, dz)} under 1 inside,
     * with stone chosen by smooth 3D noise (patches, not speckle), then props it down to the ground.
     */
    private static void rockColumn(Ctx ctx, int x, int z, int y0, int y1, Blob blob, Block[] stones, long salt) {
        int lowest = Integer.MAX_VALUE;
        Block lowestBlock = stones[0];
        for (int y = y0; y <= y1; y++) {
            if (blob.at(x + 0.5, y + 0.5, z + 0.5) > 1) continue;
            if (!open(ctx.get(x, y, z))) continue;
            double n = GradientNoise.noise3(ctx.site().seed() ^ salt, x / 3.5, y / 3.5, z / 3.5);
            Block b = stones[Math.min(stones.length - 1, (int) ((n * 0.5 + 0.5) * stones.length))];
            if (ctx.get(x, y + 1, z).isAir() && n > 0.25 && stones == STONES_MOSSY) b = Blocks.MOSSY_COBBLESTONE;
            ctx.set(x, y, z, b.defaultBlockState());
            if (y < lowest) {
                lowest = y;
                lowestBlock = b;
            }
        }
        for (int y = lowest - 1, k = 0; lowest != Integer.MAX_VALUE && k < 6 && open(ctx.get(x, y, z)); y--, k++) {
            ctx.set(x, y, z, lowestBlock.defaultBlockState());
        }
    }

    @FunctionalInterface
    private interface Blob {
        double at(double x, double y, double z);
    }

    private static final Block[] STONES_MOSSY = {Blocks.ANDESITE, Blocks.STONE, Blocks.STONE, Blocks.COBBLESTONE, Blocks.DIORITE};
    private static final Block[] GRANITE = {Blocks.DIORITE, Blocks.GRANITE, Blocks.GRANITE, Blocks.GRANITE, Blocks.ANDESITE};

    /** A glacial erratic: one great lumpy boulder, a quarter sunk in the ground, mossy on top. */
    private static void erratic(Ctx ctx, int x, int z, MicroFeature m) {
        int gy = ctx.core().column(GradientNoise.floor(m.x), GradientNoise.floor(m.z)).surfaceY();
        double cy = gy + 0.3 * m.r, seed = ctx.site().seed() ^ 0x61L;
        Blob blob = (px, py, pz) -> {
            double dx = (px - m.x) / m.r, dy = (py - cy) / (0.78 * m.r), dz = (pz - m.z) / (m.r * 0.85);
            double lump = 1 + 0.2 * GradientNoise.noise3((long) seed, px / 3.0, py / 3.0, pz / 3.0);
            return (dx * dx + dy * dy + dz * dz) / (lump * lump);
        };
        rockColumn(ctx, x, z, gy - 2, gy + (int) Math.ceil(2 * m.r) + 1, blob, STONES_MOSSY, 0x62L);
    }

    /**
     * A tor: one to three pillars side by side, each two to four rounded, squarish granite blocks
     * stacked up, a little smaller and a little offset each time.
     */
    private static void tor(Ctx ctx, int x, int z, MicroFeature m) {
        int gy = ctx.core().column(GradientNoise.floor(m.x), GradientNoise.floor(m.z)).surfaceY();
        int pillars = Math.max(1, (int) m.r2), stack = (int) m.h, n = 0;
        double[] bx = new double[pillars * stack], bz = new double[pillars * stack], by = new double[pillars * stack], br = new double[pillars * stack];
        double y = gy;
        for (int p = 0; p < pillars; p++) {
            double a = ctx.roll(p, 2, (int) m.x, 17) * Math.PI * 2, off = p == 0 ? 0 : m.r * 1.1;
            double px = m.x + Math.cos(a) * off, pz = m.z + Math.sin(a) * off, scale = p == 0 ? 1 : 0.75;
            int levels = p == 0 ? stack : Math.max(1, stack - 1);
            double py = gy + 0.2 * m.r * scale;
            for (int k = 0; k < levels; k++, n++) {
                br[n] = m.r * scale * (1 - 0.17 * k);
                bx[n] = px + (k == 0 ? 0 : (ctx.roll(k, p, (int) m.x, 18) - 0.5) * 0.6 * br[n]);
                bz[n] = pz + (k == 0 ? 0 : (ctx.roll(k, p, (int) m.z, 19) - 0.5) * 0.6 * br[n]);
                if (k > 0) py += 0.62 * br[n - 1] + 0.55 * br[n];
                by[n] = py;
                y = Math.max(y, py + br[n]);
            }
        }
        int count = n;
        Blob blob = (px, py, pz) -> {
            double best = Double.MAX_VALUE;
            for (int k = 0; k < count; k++) {
                double dx = Math.abs(px - bx[k]) / br[k], dy = Math.abs(py - by[k]) / (0.62 * br[k]), dz = Math.abs(pz - bz[k]) / br[k];
                best = Math.min(best, Math.pow(dx * dx * dx + dy * dy * dy + dz * dz * dz, 2.0 / 3)); // blocky-round
            }
            return best;
        };
        rockColumn(ctx, x, z, gy - 2, (int) Math.ceil(y) + 1, blob, GRANITE, 0x63L);
    }

    /** A sinkhole or cenote: moss on the rim, vines hanging down the walls into the bell, rubble and ferns on a dry floor, glow lichen over a pool. */
    private static void pit(Ctx ctx, int x, int z, ColumnSample c, MicroFeature m) {
        double d = Math.hypot(x + 0.5 - m.x, z + 0.5 - m.z), r = m.outline(Math.atan2(z + 0.5 - m.z, x + 0.5 - m.x));
        limestone(ctx, x, z, c, m);
        if (d >= r && d < r + 4) {
            int ty = ctx.top(x, z, c);
            if (ty == Integer.MIN_VALUE) return;
            BlockState ground = ctx.get(x, ty, z);
            double roll = ctx.roll(x, ty, z, 4);
            if (ground.is(BlockTags.DIRT) && roll < 0.45) ctx.set(x, ty, z, Blocks.MOSS_BLOCK.defaultBlockState());
            if (ctx.get(x, ty + 1, z).isAir()) {
                if (roll < 0.15) ctx.set(x, ty + 1, z, Blocks.FERN.defaultBlockState());
                else if (roll < 0.3) ctx.set(x, ty + 1, z, Blocks.MOSS_CARPET.defaultBlockState());
            }
            return;
        }
        if (d >= r - 0.3) return;
        int floor = (int) Math.floor(c.height());
        int bottom = m.wet() ? (int) Math.floor(m.level()) + 1 : floor + 1;
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            int wx = x + dir.getStepX(), wz = z + dir.getStepZ();
            ColumnSample wall = ctx.core().column(wx, wz);
            if (wall.height() < c.height() + 4) continue; // not the shaft's wall
            int start = (int) Math.floor(wall.height()) - 1;
            if (ctx.roll(x, dir.ordinal(), z, 5) < 0.75) {
                int length = 4 + (int) (ctx.roll(x, dir.ordinal(), z, 6) * 18);
                BlockState vine = Blocks.VINE.defaultBlockState().setValue(VineBlock.getPropertyForFace(dir), true);
                boolean anchored = false;
                for (int y = start; y > start - length && y > bottom; y--) {
                    if (!ctx.get(x, y, z).isAir()) {
                        if (anchored) break;
                        continue;
                    }
                    if (!anchored && !ctx.get(wx, y, wz).isSolid()) continue; // a vine starts on rock
                    ctx.set(x, y, z, vine);
                    anchored = true;
                }
            }
            if (m.wet()) {
                for (int y = bottom + 1; y <= bottom + 4; y++) {
                    if (ctx.roll(x, y, z, 7 + dir.ordinal()) < 0.22 && ctx.get(x, y, z).isAir() && ctx.get(wx, y, wz).isSolid()) {
                        ctx.set(x, y, z, Blocks.GLOW_LICHEN.defaultBlockState().setValue(MultifaceBlock.getFaceProperty(dir), true));
                    }
                }
            }
        }
        if (!m.wet() && ctx.get(x, floor + 1, z).isAir()) {
            double roll = ctx.roll(x, floor, z, 8);
            if (ctx.get(x, floor, z).is(BlockTags.DIRT) && roll < 0.35) ctx.set(x, floor, z, Blocks.MOSS_BLOCK.defaultBlockState());
            if (roll < 0.12) ctx.set(x, floor + 1, z, Blocks.MOSSY_COBBLESTONE.defaultBlockState());
            else if (roll < 0.3) ctx.set(x, floor + 1, z, Blocks.FERN.defaultBlockState());
            else if (roll < 0.42) ctx.set(x, floor + 1, z, Blocks.MOSS_CARPET.defaultBlockState());
        }
    }

    /**
     * A grotto: the pit is dressed like a cenote's (vines from the rim, lichen by the water, moss on
     * dry floor); under the overhang, dripstone, glow lichen and vines hang from the shelf, and the
     * dry ledge is sand and gravel with moss where the light reaches.
     */
    private static void grotto(Ctx ctx, int x, int z, ColumnSample c, MicroFeature m) {
        if (!m.wet()) return;
        double d = Math.hypot(x + 0.5 - m.x, z + 0.5 - m.z), r = m.outline(Math.atan2(z + 0.5 - m.z, x + 0.5 - m.x));
        if (d < r + 4) pit(ctx, x, z, c, m);
        double dc = Math.hypot(x + 0.5 - m.cavityX(), z + 0.5 - m.cavityZ());
        if (d < r || dc > m.cavityR()) return;
        int bottom = (int) Math.floor(m.level()) + 1, top = (int) Math.floor(m.shelf()) + 1;
        // The cavity's ceiling and floor in this column.
        int ceiling = Integer.MIN_VALUE, floor = Integer.MIN_VALUE;
        for (int y = bottom; y <= top; y++) {
            boolean air = ctx.get(x, y, z).isAir();
            if (air && floor == Integer.MIN_VALUE) floor = y - 1;
            if (air && !ctx.get(x, y + 1, z).isAir()) {
                ceiling = y + 1;
                break;
            }
        }
        if (floor != Integer.MIN_VALUE) {
            double patch = GradientNoise.noise2(ctx.site().seed() ^ 0x6A1L, x / 5.0, z / 5.0);
            Block ground = patch > 0.3 ? Blocks.GRAVEL : patch < -0.45 && d < r + 4 ? Blocks.MOSS_BLOCK : Blocks.SAND;
            if (!ctx.get(x, floor, z).isAir()) ctx.set(x, floor, z, ground.defaultBlockState());
        }
        if (ceiling == Integer.MIN_VALUE || ceiling - 1 <= bottom + 1) return;
        int hang = ceiling - 1;
        double roll = ctx.roll(x, hang, z, 41);
        if (roll < 0.12) {
            // A dripstone, one to three blocks, tip down.
            int len = 1 + (int) (ctx.roll(x, hang, z, 42) * 3);
            for (int k = 0; k < len && hang - k > bottom + 1 && ctx.get(x, hang - k, z).isAir(); k++) {
                net.minecraft.world.level.block.state.properties.SpeleothemThickness th = k == len - 1
                        ? net.minecraft.world.level.block.state.properties.SpeleothemThickness.TIP
                        : k == 0 && len == 3 ? net.minecraft.world.level.block.state.properties.SpeleothemThickness.BASE
                        : net.minecraft.world.level.block.state.properties.SpeleothemThickness.MIDDLE;
                ctx.set(x, hang - k, z, Blocks.POINTED_DRIPSTONE.defaultBlockState()
                        .setValue(net.minecraft.world.level.block.SpeleothemBlock.TIP_DIRECTION, Direction.DOWN)
                        .setValue(net.minecraft.world.level.block.SpeleothemBlock.THICKNESS, th));
            }
        } else if (roll < 0.3) {
            ctx.set(x, hang, z, Blocks.GLOW_LICHEN.defaultBlockState().setValue(MultifaceBlock.getFaceProperty(Direction.UP), true));
        } else if (roll < 0.42 && d < r + 5) {
            // Vines trailing down from the overhang's edge, toward the light.
            int len = 2 + (int) (ctx.roll(x, hang, z, 43) * 7);
            for (Direction dir : Direction.Plane.HORIZONTAL) {
                if (ctx.get(x + dir.getStepX(), hang, z + dir.getStepZ()).isSolid()) {
                    BlockState vine = Blocks.VINE.defaultBlockState().setValue(VineBlock.getPropertyForFace(dir), true);
                    for (int k = 0; k < len && hang - k > bottom + 1 && ctx.get(x, hang - k, z).isAir(); k++) ctx.set(x, hang - k, z, vine);
                    break;
                }
            }
        }
    }

    /**
     * Terraced cascades: rims, beds and built-up walls of white travertine (calcite, with smooth
     * patches of diorite), three blocks deep so their faces show it; moss and ferns on the dry walls.
     */
    private static void travertine(Ctx ctx, int x, int z, ColumnSample c, MicroFeature m) {
        double[] tr = Features.terrace(m, x + 0.5, z + 0.5);
        if (tr == null || !m.wet()) return;
        int top = c.surfaceY();
        for (int y = top; y >= top - 3; y--) {
            BlockState s = ctx.get(x, y, z);
            if (s.isAir() || !s.getFluidState().isEmpty() || !s.isSolid()) continue;
            double n = GradientNoise.noise3(ctx.site().seed() ^ 0x7A1L, x / 6.0, y / 3.0, z / 6.0);
            ctx.set(x, y, z, (n > 0.45 ? Blocks.DIORITE : Blocks.CALCITE).defaultBlockState());
        }
        // The rims' and walls' faces where they stand over a lower pool beside them.
        for (int[] o : SIDES) {
            int nx = x + o[0], nz = z + o[1];
            int below = ctx.core().column(nx, nz).surfaceY();
            for (int y = top - 4; y > below && y > top - 9; y--) {
                BlockState s = ctx.get(x, y, z);
                if (s.isSolid() && s.getFluidState().isEmpty()) ctx.set(x, y, z, Blocks.CALCITE.defaultBlockState());
            }
        }
        if (tr[0] == 3 && ctx.get(x, top + 1, z).isAir()) {
            double roll = ctx.roll(x, top, z, 51);
            if (roll < 0.25) ctx.set(x, top, z, Blocks.MOSS_BLOCK.defaultBlockState());
            if (roll < 0.12) ctx.set(x, top + 1, z, Blocks.FERN.defaultBlockState());
            else if (roll < 0.2) ctx.set(x, top + 1, z, Blocks.MOSS_CARPET.defaultBlockState());
        }
    }

    /**
     * A cinder cone's ground, three blocks deep: dark cinders in broad patches (blackstone, basalt,
     * tuff), and oxidised red scoria around the crater, all in smooth patches, never speckled.
     */
    private static void cinders(Ctx ctx, int x, int z, ColumnSample c, MicroFeature m) {
        double d = Math.hypot(x + 0.5 - m.x, z + 0.5 - m.z);
        if (d > m.r * 1.05) return;
        int ty = ctx.top(x, z, c);
        if (ty == Integer.MIN_VALUE) return;
        long seed = ctx.site().seed();
        double patch = GradientNoise.noise2(seed ^ 0x31L, x / 7.0, z / 7.0) + 0.3 * GradientNoise.noise2(seed ^ 0x32L, x / 2.5, z / 2.5);
        double red = smooth(m.r2 * 2.0, m.r2 * 0.9, d) + 0.35 * GradientNoise.noise2(seed ^ 0x33L, x / 5.0, z / 5.0);
        for (int k = 0; k < 3; k++) {
            BlockState s = ctx.get(x, ty - k, z);
            if (s.isAir() || !s.getFluidState().isEmpty()) break;
            Block b = red > 0.55 ? (patch > 0.1 ? Blocks.DYED_TERRACOTTA.red() : Blocks.DYED_TERRACOTTA.brown())
                    : patch < -0.3 ? Blocks.TUFF : patch < 0.3 ? Blocks.BLACKSTONE : Blocks.BASALT;
            ctx.set(x, ty - k, z, b.defaultBlockState());
        }
    }

    private static double smooth(double edge0, double edge1, double v) {
        double t = Math.max(0, Math.min(1, (v - edge0) / (edge1 - edge0)));
        return t * t * (3 - 2 * t);
    }

    /** A hoodoo's caprock: the harder grey layer on top that keeps the soft banded rock under it standing. */
    private static void hoodoo(Ctx ctx, int x, int z, ColumnSample c, MicroFeature m) {
        double dx = x + 0.5 - m.x, dz = z + 0.5 - m.z;
        if (Math.hypot(dx, dz) > m.outline(Math.atan2(dz, dx))) return;
        int ty = ctx.top(x, z, c);
        if (ty == Integer.MIN_VALUE) return;
        int cap = m.h < 12 ? 2 : 3;
        for (int k = 0; k < cap; k++) {
            if (ctx.get(x, ty - k, z).isAir()) break;
            double roll = ctx.roll(x, ty - k, z, 16);
            ctx.set(x, ty - k, z, (roll < 0.55 ? Blocks.ANDESITE : roll < 0.85 ? Blocks.STONE : Blocks.DYED_TERRACOTTA.lightGray()).defaultBlockState());
        }
        if (!ctx.get(x, ty + 1, z).isAir()) ctx.set(x, ty + 1, z, Blocks.AIR.defaultBlockState());
    }

    /** Patches of ash and cinder on the volcanic field's ground between its cones. */
    private static void ash(Ctx ctx, int x, int z, ColumnSample c) {
        if (!Double.isNaN(c.water())) return;
        double n = GradientNoise.noise2(ctx.site().seed(), x / 9.0, z / 9.0) + 0.35 * GradientNoise.noise2(ctx.site().seed() ^ 0x5DL, x / 3.5, z / 3.5);
        if (n < 0.3) return;
        int ty = ctx.top(x, z, c);
        if (ty == Integer.MIN_VALUE) return;
        BlockState s = ctx.get(x, ty, z);
        if (!(s.is(BlockTags.DIRT) || s.is(BlockTags.SAND) || s.is(Blocks.STONE))) return;
        double roll = ctx.roll(x, ty, z, 10);
        Block b = roll < 0.4 ? Blocks.TUFF : roll < 0.65 ? Blocks.COARSE_DIRT : roll < 0.85 ? Blocks.BLACKSTONE : Blocks.BASALT;
        ctx.set(x, ty, z, b.defaultBlockState());
    }

    /** Upright basalt columns, tallest in the middle of the cluster, a few missing. */
    private static void basalt(Ctx ctx, int x, int z, ColumnSample c, MicroFeature m) {
        double d = Math.hypot(x + 0.5 - m.x, z + 0.5 - m.z);
        if (d >= m.r || ctx.roll(x, 0, z, 11) < 0.15 || !Double.isNaN(c.water())) return;
        int ty = ctx.top(x, z, c);
        if (ty == Integer.MIN_VALUE) return;
        int tall = 1 + (int) Math.round((1 - d / m.r) * 4.5 + ctx.roll(x, 1, z, 12) * 2.5);
        ctx.set(x, ty, z, Blocks.SMOOTH_BASALT.defaultBlockState());
        for (int k = 1; k <= tall; k++) {
            if (!open(ctx.get(x, ty + k, z))) break;
            ctx.set(x, ty + k, z, Blocks.BASALT.defaultBlockState());
        }
    }

    // ---- forests

    /** Old forest floor: podzol, coarse dirt and moss in patches, leaf litter and ferns on it. {@code moss} is how much is moss. */
    private static void forestFloor(Ctx ctx, int x, int z, ColumnSample c, double moss) {
        if (!Double.isNaN(c.water())) return;
        int ty = ctx.top(x, z, c);
        if (ty == Integer.MIN_VALUE) return;
        BlockState s = ctx.get(x, ty, z);
        if (!s.is(BlockTags.DIRT)) return;
        long seed = ctx.site().seed();
        double n = GradientNoise.noise2(seed ^ 0x71L, x / 6.0, z / 6.0) + 0.3 * GradientNoise.noise2(seed ^ 0x72L, x / 2.0, z / 2.0);
        if (n > 0.55 - moss * 0.6) ctx.set(x, ty, z, Blocks.MOSS_BLOCK.defaultBlockState());
        else if (n > -0.05) ctx.set(x, ty, z, Blocks.PODZOL.defaultBlockState());
        else if (n > -0.3) ctx.set(x, ty, z, Blocks.COARSE_DIRT.defaultBlockState());
        if (!ctx.get(x, ty + 1, z).isAir()) return;
        double roll = ctx.roll(x, ty, z, 40);
        if (roll < 0.22) ctx.set(x, ty + 1, z, Blocks.LEAF_LITTER.defaultBlockState());
        else if (roll < 0.3) ctx.set(x, ty + 1, z, Blocks.FERN.defaultBlockState());
        else if (roll < 0.33) ctx.set(x, ty + 1, z, Blocks.BROWN_MUSHROOM.defaultBlockState());
    }

    /** A mushroom hollow's floor: mycelium and podzol in patches, small mushrooms everywhere. */
    private static void mycelium(Ctx ctx, int x, int z, ColumnSample c) {
        if (!Double.isNaN(c.water())) return;
        int ty = ctx.top(x, z, c);
        if (ty == Integer.MIN_VALUE || !ctx.get(x, ty, z).is(BlockTags.DIRT)) return;
        double n = GradientNoise.noise2(ctx.site().seed() ^ 0x73L, x / 7.0, z / 7.0);
        if (n > 0.15) ctx.set(x, ty, z, Blocks.MYCELIUM.defaultBlockState());
        else if (n > -0.25) ctx.set(x, ty, z, Blocks.PODZOL.defaultBlockState());
        if (ctx.get(x, ty + 1, z).isAir()) {
            double roll = ctx.roll(x, ty, z, 41);
            if (roll < 0.06) ctx.set(x, ty + 1, z, Blocks.RED_MUSHROOM.defaultBlockState());
            else if (roll < 0.13) ctx.set(x, ty + 1, z, Blocks.BROWN_MUSHROOM.defaultBlockState());
        }
    }

    /** A glade's fairy ring: mushrooms in a circle in the grass, near the middle. */
    private static void fairyRing(Ctx ctx, int x, int z, ColumnSample c) {
        MesoSite site = ctx.site();
        double a = (site.seed() & 0xFFFF) / 65536.0 * Math.PI * 2, off = site.radius() * 0.2;
        double cx = site.x() + Math.cos(a) * off, cz = site.z() + Math.sin(a) * off, radius = 5 + (site.seed() >>> 20 & 3);
        if (Math.abs(Math.hypot(x + 0.5 - cx, z + 0.5 - cz) - radius) > 0.6) return;
        int ty = ctx.top(x, z, c);
        if (ty == Integer.MIN_VALUE || !ctx.get(x, ty, z).is(Blocks.GRASS_BLOCK) || !open(ctx.get(x, ty + 1, z))) return;
        if (ctx.roll(x, ty, z, 42) < 0.75) {
            ctx.set(x, ty + 1, z, (ctx.roll(x, ty, z, 43) < 0.5 ? Blocks.RED_MUSHROOM : Blocks.BROWN_MUSHROOM).defaultBlockState());
        }
    }

    /** A fern gully's floor: moss, ferns and tall ferns, the odd mossy stone. */
    private static void gully(Ctx ctx, int x, int z, ColumnSample c, MicroFeature m) {
        if (!Double.isNaN(c.water())) return;
        int ty = ctx.top(x, z, c);
        if (ty == Integer.MIN_VALUE) return;
        BlockState s = ctx.get(x, ty, z);
        double n = GradientNoise.noise2(ctx.site().seed() ^ 0x74L, x / 4.0, z / 4.0);
        if (s.is(BlockTags.DIRT) && n > 0.0) ctx.set(x, ty, z, Blocks.MOSS_BLOCK.defaultBlockState());
        if (!ctx.get(x, ty + 1, z).isAir()) return;
        double roll = ctx.roll(x, ty, z, 44);
        if (roll < 0.12 && ctx.get(x, ty + 2, z).isAir()) {
            ctx.set(x, ty + 1, z, Blocks.LARGE_FERN.defaultBlockState().setValue(DoublePlantBlock.HALF, DoubleBlockHalf.LOWER));
            ctx.set(x, ty + 2, z, Blocks.LARGE_FERN.defaultBlockState().setValue(DoublePlantBlock.HALF, DoubleBlockHalf.UPPER));
        } else if (roll < 0.5) {
            ctx.set(x, ty + 1, z, Blocks.FERN.defaultBlockState());
        } else if (roll < 0.62) {
            ctx.set(x, ty + 1, z, Blocks.MOSS_CARPET.defaultBlockState());
        } else if (roll < 0.65) {
            ctx.set(x, ty + 1, z, Blocks.MOSSY_COBBLESTONE.defaultBlockState());
        }
    }

    /** Builds each big object (giant trees, giant mushrooms, snags) whole from its feature, and places this chunk's part. */
    private static void structures(Ctx ctx) {
        for (MicroFeature m : ctx.site().micros()) {
            if (!m.present() || m.kind != MicroKind.GIANT_TREE && m.kind != MicroKind.GIANT_MUSHROOM && m.kind != MicroKind.SNAG) continue;
            double reach = m.reach() + 2;
            if (m.x + reach < ctx.x0() || m.x - reach > ctx.x0() + 15 || m.z + reach < ctx.z0() || m.z - reach > ctx.z0() + 15) continue;
            ColumnSample foot = ctx.core().column(GradientNoise.floor(m.x), GradientNoise.floor(m.z));
            if (foot.height() < AtlasCore.SEA_LEVEL - 0.5) continue; // a site reaching the coast: nothing built in the sea
            Voxels v = new Voxels();
            int gy = foot.surfaceY();
            java.util.SplittableRandom rnd = new java.util.SplittableRandom(ctx.hash(GradientNoise.floor(m.x), 0, GradientNoise.floor(m.z), 50));
            switch (m.kind) {
                case GIANT_TREE -> giantTree(ctx, v, m, gy, rnd);
                case GIANT_MUSHROOM -> giantMushroom(v, m, gy);
                case SNAG -> snag(ctx, v, m, gy, rnd);
                default -> {
                }
            }
            v.place(ctx);
        }
    }

    /** One object's blocks, built in full; logs win over leaves, nothing goes over the ground. */
    private static final class Voxels {
        final java.util.Map<Long, BlockState> blocks = new java.util.HashMap<>();
        final java.util.Set<Long> solid = new java.util.HashSet<>();

        void wood(int x, int y, int z, BlockState s) {
            long k = BlockPos.asLong(x, y, z);
            blocks.put(k, s);
            solid.add(k);
        }

        void leaf(int x, int y, int z, BlockState s) {
            long k = BlockPos.asLong(x, y, z);
            if (!solid.contains(k)) blocks.putIfAbsent(k, s);
        }

        void place(Ctx ctx) {
            for (var e : blocks.entrySet()) {
                BlockPos pos = BlockPos.of(e.getKey());
                if (!ctx.mine(pos.getX(), pos.getZ())) continue;
                BlockState here = ctx.level().getBlockState(pos);
                boolean wood = solid.contains(e.getKey());
                if (open(here) || wood && here.is(BlockTags.LEAVES)) ctx.level().setBlock(pos, e.getValue(), 2);
            }
        }
    }

    private record Species(Block log, Block wood, Block leaves) {}

    private static Species species(MesoSite site) {
        return switch (site.biome()) {
            case BIRCH_FOREST, OLD_GROWTH_BIRCH_FOREST -> new Species(Blocks.BIRCH_LOG, Blocks.BIRCH_WOOD, Blocks.BIRCH_LEAVES);
            case TAIGA, SNOWY_TAIGA, OLD_GROWTH_SPRUCE_TAIGA, OLD_GROWTH_PINE_TAIGA -> new Species(Blocks.SPRUCE_LOG, Blocks.SPRUCE_WOOD, Blocks.SPRUCE_LEAVES);
            case DARK_FOREST -> new Species(Blocks.DARK_OAK_LOG, Blocks.DARK_OAK_WOOD, Blocks.DARK_OAK_LEAVES);
            case JUNGLE, SPARSE_JUNGLE -> new Species(Blocks.JUNGLE_LOG, Blocks.JUNGLE_WOOD, Blocks.JUNGLE_LEAVES);
            default -> new Species(Blocks.OAK_LOG, Blocks.OAK_WOOD, Blocks.OAK_LEAVES);
        };
    }

    /**
     * An ancient tree: a trunk tapering up from four buttress roots, great branches reaching out and
     * up from its upper half, each ending in a cloud of leaves, a crown over the top, and roots
     * snaking out over the ground. Leaves are persistent, so the crown never decays.
     */
    private static void giantTree(Ctx ctx, Voxels v, MicroFeature m, int gy, java.util.SplittableRandom rnd) {
        Species sp = species(ctx.site());
        BlockState wood = sp.wood().defaultBlockState();
        BlockState leaves = sp.leaves().defaultBlockState().setValue(LeavesBlock.PERSISTENT, true);
        double r0 = m.r, tall = m.h, phase = rnd.nextDouble() * Math.PI;
        int cx = GradientNoise.floor(m.x), cz = GradientNoise.floor(m.z), top = gy + (int) (tall * 0.8);
        int span = (int) Math.ceil(r0 + 5);
        for (int y = gy - 3; y <= top; y++) {
            double rel = (y - gy) / tall, radius = r0 * (1 - 0.45 * rel);
            double flare = Math.max(0, 1 - (y - gy + 3) / 9.0);
            for (int dz = -span; dz <= span; dz++) {
                for (int dx = -span; dx <= span; dx++) {
                    double d = Math.hypot(dx, dz), a = Math.atan2(dz, dx);
                    double buttress = Math.pow(Math.max(0, Math.cos(4 * a + phase)), 3);
                    if (d <= radius + flare * (0.6 + 3.2 * buttress)) v.wood(cx + dx, y, cz + dz, wood);
                }
            }
        }
        java.util.List<double[]> clusters = new java.util.ArrayList<>();
        clusters.add(new double[] {cx, top + 2, cz, m.r2 * 0.5});
        int branches = 5 + rnd.nextInt(4);
        for (int b = 0; b < branches; b++) {
            double a = b * Math.PI * 2 / branches + rnd.nextDouble() * 0.6, len = m.r2 * (0.6 + 0.4 * rnd.nextDouble());
            double y0 = gy + tall * (0.42 + 0.38 * rnd.nextDouble()), rise = 0.35 + 0.35 * rnd.nextDouble();
            BlockState log = sp.log().defaultBlockState().setValue(RotatedPillarBlock.AXIS, Math.abs(Math.cos(a)) > Math.abs(Math.sin(a)) ? Direction.Axis.X : Direction.Axis.Z);
            double ex = cx, ey = y0, ez = cz;
            for (double s = 0; s <= len; s += 0.5) {
                ex = cx + 0.5 + Math.cos(a) * (r0 * 0.5 + s);
                ey = y0 + s * rise;
                ez = cz + 0.5 + Math.sin(a) * (r0 * 0.5 + s);
                double br = Math.max(0.5, r0 * 0.5 * (1 - s / len));
                int rr = (int) Math.ceil(br);
                for (int dy = -rr; dy <= rr; dy++) {
                    for (int dz = -rr; dz <= rr; dz++) {
                        for (int dx = -rr; dx <= rr; dx++) {
                            if (dx * dx + dy * dy + dz * dz <= br * br + 0.25) {
                                v.wood(GradientNoise.floor(ex) + dx, GradientNoise.floor(ey) + dy, GradientNoise.floor(ez) + dz, br > 1 ? wood : log);
                            }
                        }
                    }
                }
            }
            clusters.add(new double[] {ex, ey + 1, ez, m.r2 * 0.34 + rnd.nextDouble() * 1.5});
        }
        for (double[] cl : clusters) {
            double lr = cl[3];
            int rr = (int) Math.ceil(lr);
            for (int dy = -rr; dy <= rr; dy++) {
                for (int dz = -rr; dz <= rr; dz++) {
                    for (int dx = -rr; dx <= rr; dx++) {
                        int x = GradientNoise.floor(cl[0]) + dx, y = GradientNoise.floor(cl[1]) + dy, z = GradientNoise.floor(cl[2]) + dz;
                        double e = (dx * dx + dz * dz) / (lr * lr) + (dy * dy) / (0.62 * lr * 0.62 * lr);
                        if (e <= 1 - 0.3 * ctx.roll(x, y, z, 51)) v.leaf(x, y, z, leaves);
                    }
                }
            }
        }
        // Surface roots out along the buttresses.
        for (int k = 0; k < 4; k++) {
            double dir = (2 * Math.PI * k - phase) / 4; // where cos(4a + phase) peaks: a buttress
            double len = 4 + rnd.nextDouble() * 5;
            BlockState log = sp.log().defaultBlockState().setValue(RotatedPillarBlock.AXIS, Math.abs(Math.cos(dir)) > Math.abs(Math.sin(dir)) ? Direction.Axis.X : Direction.Axis.Z);
            for (double s = r0 + 1; s <= r0 + 1 + len; s += 0.7) {
                int x = GradientNoise.floor(cx + 0.5 + Math.cos(dir) * s), z = GradientNoise.floor(cz + 0.5 + Math.sin(dir) * s);
                int y = ctx.core().column(x, z).surfaceY();
                v.wood(x, y + (s < r0 + 3 ? 1 : 0), z, log);
            }
        }
    }

    /** A giant mushroom: a pale stem, then a red dome with a hollow underside or a broad flat brown cap. */
    private static void giantMushroom(Voxels v, MicroFeature m, int gy) {
        BlockState stem = Blocks.MUSHROOM_STEM.defaultBlockState();
        boolean brown = m.extra > 0.5;
        BlockState cap = (brown ? Blocks.BROWN_MUSHROOM_BLOCK : Blocks.RED_MUSHROOM_BLOCK).defaultBlockState();
        int cx = GradientNoise.floor(m.x), cz = GradientNoise.floor(m.z), top = gy + (int) m.h;
        int sr = (int) Math.ceil(m.r);
        for (int y = gy - 1; y <= top; y++) {
            double radius = m.r + (y < gy + 2 ? 0.6 : 0);
            for (int dz = -sr - 1; dz <= sr + 1; dz++) {
                for (int dx = -sr - 1; dx <= sr + 1; dx++) {
                    if (dx * dx + dz * dz <= radius * radius + 0.3) v.wood(cx + dx, y, cz + dz, stem);
                }
            }
        }
        double cr = m.r2;
        int rr = (int) Math.ceil(cr) + 1;
        for (int dz = -rr; dz <= rr; dz++) {
            for (int dx = -rr; dx <= rr; dx++) {
                double d = Math.hypot(dx, dz);
                if (brown) {
                    if (d <= cr) v.leaf(cx + dx, top + 1, cz + dz, cap);
                    if (d > cr - 1.6 && d <= cr + 0.6) v.leaf(cx + dx, top, cz + dz, cap); // a drooping rim
                } else {
                    double centre = top + 1 - cr * 0.3;
                    for (int dy = 0; dy <= rr; dy++) {
                        double e = Math.sqrt(d * d + (dy + 0.5) * (dy + 0.5) * 1.3);
                        if (e <= cr && e > cr - 1.3 && centre + dy >= top - 1) v.leaf(cx + dx, (int) Math.floor(centre + dy), cz + dz, cap);
                    }
                }
            }
        }
    }

    /** A snag: a dead trunk, bark low down and weathered bare wood above, a broken top and stubs of branches. */
    private static void snag(Ctx ctx, Voxels v, MicroFeature m, int gy, java.util.SplittableRandom rnd) {
        Species sp = species(ctx.site());
        Block bare = sp.log() == Blocks.SPRUCE_LOG ? Blocks.STRIPPED_SPRUCE_LOG : Blocks.STRIPPED_OAK_LOG;
        int cx = GradientNoise.floor(m.x), cz = GradientNoise.floor(m.z), top = gy + (int) m.h, w = m.r >= 1 ? 2 : 1;
        for (int y = gy; y <= top; y++) {
            for (int dx = 0; dx < w; dx++) {
                for (int dz = 0; dz < w; dz++) {
                    if (y == top && rnd.nextDouble() < 0.5) continue; // jagged break
                    v.wood(cx + dx, y, cz + dz, (y < gy + 3 ? sp.log() : bare).defaultBlockState());
                }
            }
        }
        int stubs = 2 + rnd.nextInt(3);
        for (int k = 0; k < stubs; k++) {
            Direction d = Direction.Plane.HORIZONTAL.getRandomDirection(net.minecraft.util.RandomSource.create(rnd.nextLong()));
            int y = gy + (int) (m.h * (0.5 + 0.4 * rnd.nextDouble())), len = 1 + rnd.nextInt(3);
            BlockState log = bare.defaultBlockState().setValue(RotatedPillarBlock.AXIS, d.getAxis());
            for (int s = 1; s <= len; s++) v.wood(cx + d.getStepX() * (s + (d.getStepX() > 0 ? w - 1 : 0)), y + (s > 1 ? 1 : 0), cz + d.getStepZ() * (s + (d.getStepZ() > 0 ? w - 1 : 0)), log);
        }
    }

    // ---- the site's own scatter

    /** Boulders and fallen logs over the site, on a jittered grid, each built whole from its own hash. */
    private static void scatter(Ctx ctx) {
        MesoSite site = ctx.site();
        double boulderCell, boulders, logCell, logs;
        switch (site.kind()) {
            case POND_COUNTRY -> { boulderCell = 15; boulders = 0.22; logCell = 22; logs = 0.06; }
            case SINKHOLE_FIELD -> { boulderCell = 14; boulders = 0.25; logCell = 22; logs = 0.08; }
            case HOODOO_GARDEN -> { boulderCell = 12; boulders = 0.3; logCell = 1; logs = 0; }
            case CINDER_FIELD -> { boulderCell = 13; boulders = 0.22; logCell = 1; logs = 0; }
            case GLADE -> { boulderCell = 12; boulders = 0.3; logCell = 18; logs = 0.1; }
            case ERRATIC_FIELD -> { boulderCell = 12; boulders = 0.25; logCell = 1; logs = 0; }
            case TOR_HILLS -> { boulderCell = 14; boulders = 0.2; logCell = 1; logs = 0; }
            case TARN_BASIN -> { boulderCell = 11; boulders = 0.3; logCell = 1; logs = 0; }
            case BUTTE_FIELD -> { boulderCell = 13; boulders = 0.2; logCell = 1; logs = 0; }
            case ANCIENT_GROVE -> { boulderCell = 16; boulders = 0.2; logCell = 20; logs = 0.1; }
            case MUSHROOM_HOLLOW -> { boulderCell = 18; boulders = 0.12; logCell = 20; logs = 0.1; }
            case DEADFALL -> { boulderCell = 18; boulders = 0.1; logCell = 12; logs = 0.28; }
            case PALE_GROVE -> { boulderCell = 18; boulders = 0.1; logCell = 22; logs = 0.06; }
            case FERN_GULLY -> { boulderCell = 14; boulders = 0.25; logCell = 22; logs = 0.08; }
            default -> { return; }
        }
        objects(ctx, boulderCell, boulders, 20, 4, MicroDetail::boulder);
        if (logs > 0) objects(ctx, logCell, logs, 21, 9, MicroDetail::log);
    }

    @FunctionalInterface
    private interface Builder {
        void build(Ctx ctx, double px, double pz, ColumnSample c, int salt);
    }

    private static void objects(Ctx ctx, double cell, double chance, int salt, int margin, Builder builder) {
        int i0 = GradientNoise.floor((ctx.x0() - margin) / cell), i1 = GradientNoise.floor((ctx.x0() + 15 + margin) / cell);
        int j0 = GradientNoise.floor((ctx.z0() - margin) / cell), j1 = GradientNoise.floor((ctx.z0() + 15 + margin) / cell);
        for (int j = j0; j <= j1; j++) {
            for (int i = i0; i <= i1; i++) {
                if (ctx.roll(i, salt, j, 30) >= chance) continue;
                double px = (i + ctx.roll(i, salt, j, 31)) * cell, pz = (j + ctx.roll(i, salt, j, 32)) * cell;
                if (Math.hypot(px - ctx.site().x(), pz - ctx.site().z()) > ctx.site().radius() * 0.92) continue;
                ColumnSample c = ctx.core().column(GradientNoise.floor(px), GradientNoise.floor(pz));
                if (!Double.isNaN(c.water()) || c.height() < AtlasCore.SEA_LEVEL - 0.5) continue; // no boulders or logs in the water
                if (c.micro() != null && c.micro().present() && c.micro().kind != MicroKind.CINDER_CONE) continue;
                builder.build(ctx, px, pz, c, (int) (ctx.hash(i, salt, j, 33) & 0x7FFFFFFF));
            }
        }
    }

    /** A rounded boulder sitting a third sunk in the ground, propped down to it where the ground dips. */
    private static void boulder(Ctx ctx, double px, double pz, ColumnSample c, int salt) {
        double size = 1.1 + 1.5 * Math.pow((salt & 0xFFFF) / 65536.0, 1.5);
        int gy = c.surfaceY();
        double cy = gy + 0.35 * size;
        Block[] stones = stones(ctx.site());
        int r = (int) Math.ceil(size) + 1;
        for (int x = GradientNoise.floor(px) - r; x <= GradientNoise.floor(px) + r; x++) {
            for (int z = GradientNoise.floor(pz) - r; z <= GradientNoise.floor(pz) + r; z++) {
                if (!ctx.mine(x, z)) continue;
                int lowest = Integer.MAX_VALUE;
                Block lowestBlock = stones[0];
                for (int y = gy - 1; y <= gy + 2 * r; y++) {
                    double ex = (x + 0.5 - px) / size, ey = (y + 0.5 - cy) / (0.78 * size), ez = (z + 0.5 - pz) / size;
                    if (ex * ex + ey * ey + ez * ez > 1 + 0.25 * (ctx.roll(x, y, z, 13) - 0.5)) continue;
                    if (!open(ctx.get(x, y, z))) continue;
                    Block b = stones[(int) (ctx.roll(x, y, z, 14) * stones.length)];
                    ctx.set(x, y, z, b.defaultBlockState());
                    if (y < lowest) {
                        lowest = y;
                        lowestBlock = b;
                    }
                }
                for (int y = lowest - 1, k = 0; lowest != Integer.MAX_VALUE && k < 4 && ctx.get(x, y, z).isAir(); y--, k++) {
                    ctx.set(x, y, z, lowestBlock.defaultBlockState());
                }
            }
        }
    }

    private static Block[] stones(MesoSite site) {
        return switch (site.kind()) {
            case CINDER_FIELD -> new Block[] {Blocks.BLACKSTONE, Blocks.BLACKSTONE, Blocks.BASALT, Blocks.TUFF};
            case TARN_BASIN, ERRATIC_FIELD -> new Block[] {Blocks.STONE, Blocks.ANDESITE, Blocks.COBBLESTONE, Blocks.STONE};
            case TOR_HILLS -> GRANITE;
            case HOODOO_GARDEN, BUTTE_FIELD -> switch (site.biome()) {
                case BADLANDS, WOODED_BADLANDS, ERODED_BADLANDS, RED_DESERT -> new Block[] {Blocks.TERRACOTTA, Blocks.RED_SANDSTONE, Blocks.RED_SANDSTONE, Blocks.DYED_TERRACOTTA.orange()};
                case DESERT -> new Block[] {Blocks.SANDSTONE, Blocks.SANDSTONE, Blocks.SMOOTH_SANDSTONE, Blocks.CUT_SANDSTONE};
                default -> new Block[] {Blocks.STONE, Blocks.COBBLESTONE, Blocks.ANDESITE, Blocks.STONE};
            };
            default -> new Block[] {Blocks.MOSSY_COBBLESTONE, Blocks.MOSSY_COBBLESTONE, Blocks.COBBLESTONE, Blocks.STONE, Blocks.ANDESITE};
        };
    }

    /** A fallen trunk lying on level ground, with moss and the odd mushroom on it. */
    private static void log(Ctx ctx, double px, double pz, ColumnSample c, int salt) {
        int length = 4 + (salt & 7) % 5;
        boolean alongX = (salt & 8) != 0;
        int sx = GradientNoise.floor(px), sz = GradientNoise.floor(pz), gy = c.surfaceY();
        for (int k = 0; k < length; k++) {
            ColumnSample under = ctx.core().column(alongX ? sx + k : sx, alongX ? sz : sz + k);
            if (Math.abs(under.surfaceY() - gy) > 1 || !Double.isNaN(under.water())) return; // only on level ground
        }
        BlockState log = (ctx.site().kind() == MesoKind.PALE_GROVE ? Blocks.PALE_OAK_LOG : logFor(ctx.site().biome())).defaultBlockState()
                .setValue(RotatedPillarBlock.AXIS, alongX ? Direction.Axis.X : Direction.Axis.Z);
        int y = gy + 1;
        for (int k = 0; k < length; k++) {
            int x = alongX ? sx + k : sx, z = alongX ? sz : sz + k;
            if (!ctx.mine(x, z) || !open(ctx.get(x, y, z))) continue;
            ctx.set(x, y, z, log);
            double roll = ctx.roll(x, y, z, 15);
            if (ctx.get(x, y + 1, z).isAir()) {
                if (roll < 0.3) ctx.set(x, y + 1, z, Blocks.MOSS_CARPET.defaultBlockState());
                else if (roll < 0.38) ctx.set(x, y + 1, z, Blocks.BROWN_MUSHROOM.defaultBlockState());
            }
        }
    }

    private static Block logFor(BiomeKind biome) {
        return switch (biome) {
            case TAIGA, SNOWY_TAIGA, OLD_GROWTH_SPRUCE_TAIGA, GROVE, SNOWY_PLAINS, SNOWY_SLOPES -> Blocks.SPRUCE_LOG;
            case BIRCH_FOREST -> Blocks.BIRCH_LOG;
            case DARK_FOREST -> Blocks.DARK_OAK_LOG;
            case JUNGLE, SPARSE_JUNGLE -> Blocks.JUNGLE_LOG;
            default -> Blocks.OAK_LOG;
        };
    }

    private static @Nullable AtlasCore core(ServerLevel level) {
        if (level == lastLevel) return lastCore;
        AtlasCore core = CoreHandle.coreFrom(level.getChunkSource().randomState().createClimateSampler(SamplerContext.EMPTY_UNCACHED));
        lastCore = core;
        lastLevel = level;
        return core;
    }
}
