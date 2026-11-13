package com.seaweed.grandatlas.world;

import com.seaweed.grandatlas.core.AtlasCore;
import com.seaweed.grandatlas.core.micro.MicroFeature;
import com.seaweed.grandatlas.core.noise.GradientNoise;
import java.util.SplittableRandom;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.AmethystClusterBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CandleBlock;
import net.minecraft.world.level.block.CaveVines;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The M6 fantasy micro features, each built whole into a {@link MicroDetail.Voxels} (which then
 * places only the calling chunk's part, and only into open air or plants): crystal spires, standing
 * stones, floating rocks, giant rib cages and petrified trunks. Their sites and layout are the
 * core's ({@code core/micro/MesoKind}).
 */
final class FantasyBuilds {
    private FantasyBuilds() {}

    // ---- crystal spires

    /**
     * A leaning spire of aurora crystal: a thick shaft from below the ground, tapering to a point in
     * its last third, tilted 10 to 35 degrees toward its heading, with shards scattered at its foot.
     */
    static void crystalSpire(MicroDetail.Voxels v, AtlasCore core, MicroFeature m, int gy, SplittableRandom rnd) {
        int colour = (int) m.extra % ModBlocks.AURORA_CRYSTAL.length;
        BlockState crystal = ModBlocks.AURORA_CRYSTAL[colour].get().defaultBlockState();
        BlockState shattered = ModBlocks.SHATTERED_AURORA[colour].get().defaultBlockState();
        double tilt = Math.toRadians(10 + 25 * rnd.nextDouble()), a = m.r2;
        double ux = Math.sin(tilt) * Math.cos(a), uy = Math.cos(tilt), uz = Math.sin(tilt) * Math.sin(a);
        double bx = m.x, by = gy - 4, bz = m.z, len = m.h + 4, half = m.r;
        int reach = (int) Math.ceil(half + len * Math.sin(tilt) + 2);
        int cx = GradientNoise.floor(bx), cz = GradientNoise.floor(bz);
        for (int dy = -1; dy <= (int) Math.ceil(len) + 1; dy++) {
            for (int dz = -reach; dz <= reach; dz++) {
                for (int dx = -reach; dx <= reach; dx++) {
                    double px = cx + dx + 0.5 - bx, py = by + dy + 0.5 - by, pz = cz + dz + 0.5 - bz;
                    double t = px * ux + py * uy + pz * uz;
                    if (t < 0 || t > len) continue;
                    double ex = px - ux * t, ey = py - uy * t, ez = pz - uz * t;
                    double perp = Math.sqrt(ex * ex + ey * ey + ez * ez);
                    double rad = half * Math.min(1, (len - t) / (0.33 * len)) + 0.35;
                    if (perp > rad) continue;
                    // a crazed skin of shattered crystal near the tip
                    boolean tip = t > len * 0.82 && perp > rad - 1;
                    v.wood(cx + dx, (int) by + dy, cz + dz, tip ? shattered : crystal);
                }
            }
        }
        BlockState shard = ModBlocks.AURORA_SHARD[colour].get().defaultBlockState().setValue(AmethystClusterBlock.FACING, Direction.UP);
        int n = 4 + rnd.nextInt(8);
        for (int k = 0; k < n; k++) {
            double ang = rnd.nextDouble() * Math.PI * 2, d = half + 1.5 + 4 * rnd.nextDouble();
            int x = GradientNoise.floor(m.x + Math.cos(ang) * d), z = GradientNoise.floor(m.z + Math.sin(ang) * d);
            v.leaf(x, core.column(x, z).surfaceY() + 1, z, shard);
        }
    }

    // ---- standing stones

    /** A rune-cut standing stone (extra 0), one fallen on its face (1), or the altar at the ring's heart (2). */
    static void monolith(MicroDetail.Voxels v, AtlasCore core, MicroFeature m, int gy, SplittableRandom rnd) {
        int cx = GradientNoise.floor(m.x), cz = GradientNoise.floor(m.z);
        if (m.extra >= 2) {
            // The altar: a low platform of dark stone, a carved block at its heart, candles burning at its corners.
            for (int dz = -2; dz <= 2; dz++) {
                for (int dx = -2; dx <= 2; dx++) {
                    if (Math.abs(dx) == 2 && Math.abs(dz) == 2) continue;
                    v.wood(cx + dx, gy, cz + dz, Blocks.POLISHED_DEEPSLATE.defaultBlockState());
                    v.wood(cx + dx, gy - 1, cz + dz, Blocks.DEEPSLATE_TILES.defaultBlockState());
                }
            }
            v.wood(cx, gy + 1, cz, Blocks.CHISELED_DEEPSLATE.defaultBlockState());
            BlockState candle = Blocks.CANDLE.defaultBlockState().setValue(CandleBlock.CANDLES, 3).setValue(CandleBlock.LIT, true);
            for (int[] c : new int[][] {{-1, -1}, {1, -1}, {-1, 1}, {1, 1}}) v.leaf(cx + c[0], gy + 1, cz + c[1], candle);
            return;
        }
        // Faces toward the ring's centre: two blocks wide across it, one deep.
        boolean alongX = Math.abs(Math.cos(m.r2)) < Math.abs(Math.sin(m.r2));
        int tall = (int) Math.round(m.h);
        if (m.extra >= 1) {
            // Fallen outward, half sunk, moss over it.
            int sx = alongX ? 0 : (int) Math.signum(Math.cos(m.r2)), sz = alongX ? (int) Math.signum(Math.sin(m.r2)) : 0;
            for (int s = 0; s < tall; s++) {
                for (int w = 0; w < 2; w++) {
                    int x = cx + sx * s + (alongX ? w : 0), z = cz + sz * s + (alongX ? 0 : w);
                    int y = core.column(x, z).surfaceY();
                    v.wood(x, y, z, stone(rnd));
                    if (rnd.nextDouble() < 0.4) v.leaf(x, y + 1, z, Blocks.MOSS_CARPET.defaultBlockState());
                }
            }
            return;
        }
        int runeRow = 2 + rnd.nextInt(3);
        for (int y = -3; y < tall; y++) {
            boolean narrow = y >= tall - 2; // the top steps in
            for (int w = 0; w < 2; w++) {
                if (narrow && w == 1 && y == tall - 1) continue;
                int x = cx + (alongX ? w : 0), z = cz + (alongX ? 0 : w);
                boolean rune = y > 0 && (y % runeRow == 0) && rnd.nextDouble() < 0.7;
                v.wood(x, gy + y, z, rune ? Blocks.CHISELED_STONE_BRICKS.defaultBlockState() : stone(rnd));
            }
        }
        if (rnd.nextDouble() < 0.5) v.leaf(cx, gy + tall, cz, Blocks.MOSS_CARPET.defaultBlockState());
    }

    private static BlockState stone(SplittableRandom rnd) {
        double r = rnd.nextDouble();
        return (r < 0.45 ? Blocks.STONE : r < 0.7 ? Blocks.ANDESITE : r < 0.88 ? Blocks.MOSSY_STONE_BRICKS : Blocks.COBBLESTONE).defaultBlockState();
    }

    // ---- floating rocks

    /**
     * A boulder floating over the ground: a flattened dome of turf on top, a root of rock tapering
     * to a point beneath, glow berries and roots hanging from it. The biggest carry flowers.
     */
    static void floatRock(MicroDetail.Voxels v, MicroFeature m, int gy, SplittableRandom rnd) {
        double r = m.r, cy = gy + m.h;
        int cx = GradientNoise.floor(m.x), cz = GradientNoise.floor(m.z), rr = (int) Math.ceil(r * 1.2) + 1;
        double ph = rnd.nextDouble() * Math.PI * 2, ph2 = rnd.nextDouble() * Math.PI * 2;
        for (int dz = -rr; dz <= rr; dz++) {
            for (int dx = -rr; dx <= rr; dx++) {
                double d = Math.hypot(dx, dz), ang = Math.atan2(dz, dx);
                double edge = r * (1 + 0.18 * Math.sin(3 * ang + ph) + 0.1 * Math.sin(5 * ang + ph2));
                if (d > edge) continue;
                double q = d / edge;
                int top = (int) Math.floor(cy + 0.45 * r * Math.sqrt(Math.max(0, 1 - q * q)));
                int bottom = (int) Math.floor(cy - 1.4 * r * Math.pow(1 - q, 0.9) - (rnd.nextDouble() < 0.15 ? 2 : 0));
                int x = cx + dx, z = cz + dz;
                for (int y = bottom; y <= top; y++) {
                    BlockState s = y == top ? (r >= 4 ? Blocks.GRASS_BLOCK : Blocks.MOSS_BLOCK).defaultBlockState()
                            : y >= top - 2 ? Blocks.DIRT.defaultBlockState()
                            : (rnd.nextDouble() < 0.25 ? Blocks.ANDESITE : rnd.nextDouble() < 0.15 ? Blocks.MOSSY_COBBLESTONE : Blocks.STONE).defaultBlockState();
                    v.wood(x, y, z, s);
                }
                double roll = rnd.nextDouble();
                if (r >= 4 && roll < 0.35) v.leaf(x, top + 1, z, Blocks.SHORT_GRASS.defaultBlockState());
                else if (r >= 4 && roll < 0.42) v.leaf(x, top + 1, z, (rnd.nextBoolean() ? Blocks.CORNFLOWER : Blocks.OXEYE_DAISY).defaultBlockState());
                // What hangs underneath.
                double hang = rnd.nextDouble();
                if (hang < 0.12) {
                    int len = 2 + rnd.nextInt(6);
                    for (int k = 1; k <= len; k++) {
                        BlockState vine = (k == len ? Blocks.CAVE_VINES : Blocks.CAVE_VINES_PLANT).defaultBlockState()
                                .setValue(CaveVines.BERRIES, rnd.nextDouble() < 0.3);
                        v.leaf(x, bottom - k, z, vine);
                    }
                } else if (hang < 0.2) {
                    v.leaf(x, bottom - 1, z, Blocks.HANGING_ROOTS.defaultBlockState());
                }
            }
        }
    }

    // ---- bones of giants

    /**
     * A giant's rib cage, lying on its back: the spine along the ground, ribs arching up from it on
     * both sides and curling in over the top, biggest in the middle; a skull at the head end.
     */
    static void ribCage(MicroDetail.Voxels v, AtlasCore core, MicroFeature m, int gy, SplittableRandom rnd) {
        double a = m.extra, L = m.r2, H = m.h;
        double ax = Math.cos(a), az = Math.sin(a), px = -az, pz = ax;
        Direction.Axis along = Math.abs(ax) > Math.abs(az) ? Direction.Axis.X : Direction.Axis.Z;
        BlockState spine = Blocks.BONE_BLOCK.defaultBlockState().setValue(RotatedPillarBlock.AXIS, along);
        BlockState rib = Blocks.BONE_BLOCK.defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.Y);
        for (double s = -L / 2; s <= L / 2; s += 0.5) {
            int x = GradientNoise.floor(m.x + ax * s), z = GradientNoise.floor(m.z + az * s);
            int y = core.column(x, z).surfaceY();
            v.wood(x, y, z, spine);
            if (Math.abs(s) < L / 2 - 4) v.wood(x, y + 1, z, spine);
        }
        for (double s = -L / 2 + 5; s <= L / 2 - 6; s += 3) {
            double scale = Math.pow(Math.sin(Math.PI * (s + L / 2) / L), 0.6);
            double R = H * 0.75 * scale, up = H * scale;
            for (int side = -1; side <= 1; side += 2) {
                if (rnd.nextDouble() < 0.12) continue; // a broken rib
                double end = 0.75 + 0.15 * rnd.nextDouble();
                for (double th = 0; th <= Math.PI * end; th += 0.04) {
                    double lat = side * R * Math.sin(th), h = up * 0.5 * (1 - Math.cos(th));
                    int x = GradientNoise.floor(m.x + ax * s + px * lat), z = GradientNoise.floor(m.z + az * s + pz * lat);
                    int y = (int) Math.floor(gy + 1 + h);
                    v.wood(x, y, z, rib);
                }
            }
        }
        // The skull at the head: a lumpy blob of bone half sunk in the ground, eye sockets left open.
        double sx = m.x + ax * (L / 2 + 3), sz = m.z + az * (L / 2 + 3), sr = 2.5 + H * 0.12;
        int sy = core.column(GradientNoise.floor(sx), GradientNoise.floor(sz)).surfaceY();
        int n = (int) Math.ceil(sr) + 1;
        for (int dy = -n; dy <= n; dy++) {
            for (int dz = -n; dz <= n; dz++) {
                for (int dx = -n; dx <= n; dx++) {
                    double e = Math.sqrt(dx * dx + dy * dy * 1.4 + dz * dz);
                    if (e > sr) continue;
                    double fx = dx * ax + dz * az, fl = dx * px + dz * pz; // forward, lateral
                    boolean socket = fx > sr * 0.4 && Math.abs(Math.abs(fl) - sr * 0.45) < 0.9 && dy >= 0 && dy <= 1;
                    if (!socket) v.wood(GradientNoise.floor(sx) + dx, sy + dy, GradientNoise.floor(sz) + dz, Blocks.BONE_BLOCK.defaultBlockState());
                }
            }
        }
    }

    // ---- petrified trunks

    /** A trunk turned to stone: tuff and dripstone bark round a calcite heart, standing broken or fallen in pieces. */
    static void petrifiedTrunk(MicroDetail.Voxels v, AtlasCore core, MicroFeature m, int gy, SplittableRandom rnd) {
        double r = m.r;
        int cx = GradientNoise.floor(m.x), cz = GradientNoise.floor(m.z), n = (int) Math.ceil(r) + 1;
        if (m.extra < 0) {
            int top = gy + (int) m.h;
            for (int y = gy - 2; y <= top; y++) {
                for (int dz = -n; dz <= n; dz++) {
                    for (int dx = -n; dx <= n; dx++) {
                        double d = Math.hypot(dx, dz);
                        double rad = r + (y < gy + 2 ? 0.7 : 0);
                        if (d > rad) continue;
                        if (y >= top - 1 && rnd.nextDouble() < 0.5) continue; // a broken, jagged top
                        v.wood(cx + dx, y, cz + dz, petrified(d, rad, rnd));
                    }
                }
            }
            return;
        }
        double ax = Math.cos(m.extra), az = Math.sin(m.extra);
        double gap = 2 + rnd.nextInt(3), breakAt = m.h * (0.35 + 0.3 * rnd.nextDouble());
        for (double s = 0; s <= m.h; s += 0.5) {
            if (s > breakAt && s < breakAt + gap) continue; // broken in two
            double bx = m.x + ax * s, bz = m.z + az * s;
            int gx = GradientNoise.floor(bx), gz = GradientNoise.floor(bz);
            double y0 = core.column(gx, gz).surfaceY() + 0.5 + 0.6 * r; // a third sunk: the centre stands over the ground
            for (int dy = -n; dy <= n; dy++) {
                for (int dl = -n; dl <= n; dl++) {
                    double d = Math.hypot(dy, dl);
                    if (d > r + 0.35) continue;
                    int x = GradientNoise.floor(bx - az * dl), z = GradientNoise.floor(bz + ax * dl);
                    v.wood(x, (int) Math.floor(y0 + dy), z, petrified(d, r, rnd));
                }
            }
        }
    }

    private static BlockState petrified(double d, double r, SplittableRandom rnd) {
        if (d < r - 1.2) return Blocks.CALCITE.defaultBlockState();
        Block b = rnd.nextDouble() < 0.55 ? Blocks.TUFF : rnd.nextDouble() < 0.6 ? Blocks.DRIPSTONE_BLOCK : Blocks.POLISHED_TUFF;
        return b.defaultBlockState();
    }
}
