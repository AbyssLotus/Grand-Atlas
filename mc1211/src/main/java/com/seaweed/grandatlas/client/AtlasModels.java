package com.seaweed.grandatlas.client;

import com.seaweed.grandatlas.world.GlowwormSilkBlock;
import com.seaweed.grandatlas.world.ModBlocks;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.AmethystClusterBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CandleBlock;
import net.minecraft.world.level.block.CaveVines;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SeaPickleBlock;
import net.minecraft.world.level.block.state.BlockState;
import javax.annotation.Nullable;

/**
 * The Grand Atlas's pictures: small scenes built of real blocks, drawn turning on the page
 * (client/AtlasScreen). Each is built once, from a fixed seed, and kept. Water is drawn as light
 * blue glass and lava as magma, since the book draws block models, not fluids.
 */
public final class AtlasModels {
    private AtlasModels() {}

    /** A built scene: its blocks, and the tints its leaves and grass take. */
    public static final class Model {
        public final Map<Long, BlockState> blocks = new HashMap<>();
        public int foliage = 0x48B518, grass = 0x79C05A;
        /** Filled by {@link #visible}: the blocks with at least one face open to the air. */
        private List<Map.Entry<Long, BlockState>> visible;
        public int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        public int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;

        void set(int x, int y, int z, BlockState s) {
            if (s == null) return;
            blocks.put(BlockPos.asLong(x, y, z), s);
        }

        void set(int x, int y, int z, Block b) {
            set(x, y, z, b.defaultBlockState());
        }

        void setIfEmpty(int x, int y, int z, BlockState s) {
            blocks.putIfAbsent(BlockPos.asLong(x, y, z), s);
        }

        boolean has(int x, int y, int z) {
            return blocks.containsKey(BlockPos.asLong(x, y, z));
        }

        public List<Map.Entry<Long, BlockState>> visible() {
            if (visible == null) {
                visible = new ArrayList<>();
                for (var e : blocks.entrySet()) {
                    BlockPos p = BlockPos.of(e.getKey());
                    minX = Math.min(minX, p.getX()); minY = Math.min(minY, p.getY()); minZ = Math.min(minZ, p.getZ());
                    maxX = Math.max(maxX, p.getX()); maxY = Math.max(maxY, p.getY()); maxZ = Math.max(maxZ, p.getZ());
                    boolean open = false;
                    for (Direction d : Direction.values()) {
                        BlockState n = blocks.get(BlockPos.asLong(p.getX() + d.getStepX(), p.getY() + d.getStepY(), p.getZ() + d.getStepZ()));
                        if (n == null || !n.canOcclude()) {
                            open = true;
                            break;
                        }
                    }
                    if (open || !e.getValue().canOcclude()) visible.add(e);
                }
            }
            return visible;
        }
    }

    private static final Map<String, Supplier<Model>> BUILDERS = new HashMap<>();
    private static final Map<String, Model> BUILT = new HashMap<>();

    static {
        BUILDERS.put("great_geode", AtlasModels::geode);
        BUILDERS.put("aurora_vault", AtlasModels::auroraSpike);
        BUILDERS.put("crystal_garden", AtlasModels::crystalGarden);
        BUILDERS.put("worldroot", AtlasModels::worldroot);
        BUILDERS.put("titans_grave", AtlasModels::titan);
        BUILDERS.put("the_hollow", AtlasModels::hollow);
        BUILDERS.put("hanging_mountains", AtlasModels::hangingMountains);
        BUILDERS.put("sky_isles", AtlasModels::skyIsle);
        BUILDERS.put("volcanic_wastes", AtlasModels::volcano);
        BUILDERS.put("misty_peaks", AtlasModels::needles);
        BUILDERS.put("silverwood", AtlasModels::silverTree);
        BUILDERS.put("glimmer_vale", AtlasModels::glimmerVale);
        BUILDERS.put("megarange", AtlasModels::mountain);
        BUILDERS.put("standing_stones", AtlasModels::standingStones);
        BUILDERS.put("floating_rocks", AtlasModels::floatingRocks);
        BUILDERS.put("bone_field", AtlasModels::boneField);
        BUILDERS.put("petrified_forest", AtlasModels::petrified);
        BUILDERS.put("fungal_forest", AtlasModels::fungal);
        BUILDERS.put("crystal_caves", AtlasModels::crystalCave);
        BUILDERS.put("hoodoo_garden", AtlasModels::hoodoos);
        BUILDERS.put("caldera", AtlasModels::caldera);
        BUILDERS.put("canyon_plateau", AtlasModels::canyon);
        BUILDERS.put("tepui", AtlasModels::tepui);
        BUILDERS.put("rift_valley", AtlasModels::rift);
        BUILDERS.put("rainbow_mountains", AtlasModels::rainbow);
        BUILDERS.put("spotted_lake", AtlasModels::spottedLake);
    }

    /** The scene for an entry, or null if it has none (the page shows its icon instead). */
    public static @Nullable Model get(String id) {
        Model m = BUILT.get(id);
        if (m == null && BUILDERS.containsKey(id)) {
            m = BUILDERS.get(id).get();
            BUILT.put(id, m);
        }
        return m;
    }

    // ---- helpers

    private static BlockState aurora(int band) {
        return ModBlocks.AURORA_CRYSTAL[Math.floorMod(band, 5)].get().defaultBlockState();
    }

    private static BlockState shard(int colour, Direction facing) {
        return ModBlocks.AURORA_SHARD[Math.floorMod(colour, 5)].get().defaultBlockState().setValue(AmethystClusterBlock.FACING, facing);
    }

    private static BlockState log(Block b, Direction.Axis axis) {
        return b.defaultBlockState().setValue(RotatedPillarBlock.AXIS, axis);
    }

    /** A round plate of ground: grass on dirt, radius r, at y 0 (and y -1). */
    private static void plate(Model m, int r, Block top, Block under) {
        for (int z = -r; z <= r; z++) {
            for (int x = -r; x <= r; x++) {
                if (x * x + z * z > r * r + r) continue;
                m.set(x, 0, z, top);
                m.set(x, -1, z, under);
            }
        }
    }

    /** A leaf blob of radius r round (cx, cy, cz). */
    private static void blob(Model m, int cx, int cy, int cz, double r, BlockState leaf, Random rnd) {
        int n = (int) Math.ceil(r);
        for (int y = -n; y <= n; y++) {
            for (int z = -n; z <= n; z++) {
                for (int x = -n; x <= n; x++) {
                    double d = Math.sqrt(x * x + y * y * 1.6 + z * z);
                    if (d <= r - 0.3 * rnd.nextDouble()) m.setIfEmpty(cx + x, cy + y, cz + z, leaf);
                }
            }
        }
    }

    /** A tapered spike along a direction from (x, y, z), length len, base radius r. */
    private static void spike(Model m, double x, double y, double z, double dx, double dy, double dz, double len, double r,
                              java.util.function.IntFunction<BlockState> byStep) {
        double norm = Math.sqrt(dx * dx + dy * dy + dz * dz);
        dx /= norm; dy /= norm; dz /= norm;
        for (double t = 0; t <= len; t += 0.4) {
            double rad = r * (1 - t / len) + 0.4;
            int n = (int) Math.ceil(rad);
            double px = x + dx * t, py = y + dy * t, pz = z + dz * t;
            for (int a = -n; a <= n; a++) {
                for (int b = -n; b <= n; b++) {
                    for (int c = -n; c <= n; c++) {
                        if (a * a + b * b + c * c > rad * rad) continue;
                        m.set((int) Math.floor(px + a), (int) Math.floor(py + b), (int) Math.floor(pz + c), byStep.apply((int) t));
                    }
                }
            }
        }
    }

    // ---- the scenes

    /** A great geode, cut in half: basalt shell, calcite, amethyst walls, crystals growing inward. */
    private static Model geode() {
        Model m = new Model();
        Random rnd = new Random(2);
        int R = 9;
        for (int y = -R; y <= R; y++) {
            for (int z = -R; z <= 0; z++) {
                for (int x = -R; x <= R; x++) {
                    double d = Math.sqrt(x * x + y * y * 1.15 + z * z);
                    if (d > R) continue;
                    if (d >= R - 1) m.set(x, y, z, Blocks.SMOOTH_BASALT);
                    else if (d >= R - 2) m.set(x, y, z, Blocks.CALCITE);
                    else if (d >= R - 3) m.set(x, y, z, rnd.nextDouble() < 0.15 ? Blocks.BUDDING_AMETHYST : Blocks.AMETHYST_BLOCK);
                    else if (d >= R - 4 && rnd.nextDouble() < 0.3) {
                        // a crystal growing inward, off the wall toward the middle
                        Direction f = Direction.getNearest(-x, -y, -z);
                        Block c = rnd.nextDouble() < 0.5 ? Blocks.AMETHYST_CLUSTER : rnd.nextDouble() < 0.5 ? Blocks.LARGE_AMETHYST_BUD : Blocks.MEDIUM_AMETHYST_BUD;
                        m.set(x, y, z, c.defaultBlockState().setValue(AmethystClusterBlock.FACING, f));
                    }
                }
            }
        }
        return m;
    }

    /**
     * The Aurora Vault's crystal spike: a chunk of the crystal bed under the hanging lake (glass for its
     * water), and from it a great spike of aurora crystal hanging down, banded through the aurora's
     * colours, shards bristling along it.
     */
    private static Model auroraSpike() {
        Model m = new Model();
        Random rnd = new Random(4);
        int top = 18;
        // the lake and the crystal bed it lies on
        for (int z = -7; z <= 7; z++) {
            for (int x = -7; x <= 7; x++) {
                if (x * x + z * z > 52) continue;
                m.set(x, top + 2, z, Blocks.LIGHT_BLUE_STAINED_GLASS);
                m.set(x, top + 1, z, Blocks.LIGHT_BLUE_STAINED_GLASS);
                m.set(x, top, z, aurora((x + 2 * z + 20) / 4));
                if (rnd.nextDouble() < 0.45) m.set(x, top - 1, z, aurora((x - z + 20) / 3));
            }
        }
        // the spike: banded by height, green at the root to rose at the point
        spike(m, 0.5, top, 0.5, 0.15, -1, 0.1, 18, 3.6, t -> aurora(t / 4));
        // smaller spikes round it
        spike(m, 4.5, top, -2.5, 0.25, -1, -0.1, 8, 1.6, t -> aurora(1 + t / 3));
        spike(m, -4.5, top, 3.5, -0.2, -1, 0.2, 10, 1.8, t -> aurora(2 + t / 3));
        spike(m, -2.5, top, -5.5, -0.1, -1, -0.2, 6, 1.2, t -> aurora(3 + t / 2));
        // shards along the big spike and under the bed
        for (int k = 0; k < 26; k++) {
            int x = rnd.nextInt(13) - 6, z = rnd.nextInt(13) - 6;
            for (int y = top - 1; y > 0; y--) {
                if (m.has(x, y, z) && !m.has(x, y - 1, z)) {
                    if (rnd.nextDouble() < 0.6) m.set(x, y - 1, z, shard(rnd.nextInt(5), Direction.DOWN));
                    break;
                }
            }
        }
        return m;
    }

    private static Model crystalGarden() {
        Model m = new Model();
        Random rnd = new Random(6);
        plate(m, 9, Blocks.GRASS_BLOCK, Blocks.DIRT);
        spike(m, 0.5, -1, 0.5, 0.3, 1, 0.15, 17, 2.4, t -> aurora(1));
        spike(m, -4.5, -1, 3.5, -0.4, 1, 0.3, 10, 1.6, t -> aurora(0));
        spike(m, 4.5, -1, -3.5, 0.3, 1, -0.45, 8, 1.4, t -> aurora(2));
        spike(m, -3.5, -1, -4.5, -0.2, 1, -0.5, 6, 1.1, t -> aurora(1));
        for (int k = 0; k < 14; k++) {
            int x = rnd.nextInt(17) - 8, z = rnd.nextInt(17) - 8;
            if (m.has(x, 0, z) && !m.has(x, 1, z)) m.set(x, 1, z, shard(rnd.nextInt(3), Direction.UP));
        }
        return m;
    }

    private static Model worldroot() {
        Model m = new Model();
        Random rnd = new Random(8);
        // ground, cut away to show the hollow taproot going down
        for (int y = -8; y <= 0; y++) {
            for (int z = -9; z <= 9; z++) {
                for (int x = -9; x <= 9; x++) {
                    if (x * x + z * z > 85 || z > 2) continue;
                    m.set(x, y, z, y == 0 ? Blocks.GRASS_BLOCK : y > -3 ? Blocks.DIRT : Blocks.STONE);
                }
            }
        }
        BlockState wood = Blocks.DARK_OAK_WOOD.defaultBlockState();
        for (int y = -8; y <= 16; y++) {
            double r = y < 0 ? 2.2 : 2.6 - y * 0.06 + (y < 3 ? (3 - y) * 0.5 : 0);
            int n = (int) Math.ceil(r);
            for (int z = -n; z <= n; z++) {
                for (int x = -n; x <= n; x++) {
                    double d = Math.hypot(x, z);
                    if (d > r) continue;
                    if (y < 0 && d < r - 1.1) {
                        m.blocks.remove(BlockPos.asLong(x, y, z)); // the hollow taproot
                        continue;
                    }
                    m.set(x, y, z, rnd.nextDouble() < 0.08 ? ModBlocks.AMBER_SAP.get().defaultBlockState() : wood);
                }
            }
        }
        // roots over the ground
        for (int k = 0; k < 6; k++) {
            double a = k * Math.PI / 3 + 0.3;
            for (double s = 2; s < 8; s += 0.5) {
                int x = (int) Math.round(Math.cos(a) * s), z = (int) Math.round(Math.sin(a) * s);
                m.set(x, 1 - (s > 5 ? 1 : 0), z, log(Blocks.DARK_OAK_LOG, Math.abs(Math.cos(a)) > 0.7 ? Direction.Axis.X : Direction.Axis.Z));
            }
        }
        // branches and the crown
        BlockState leaves = Blocks.DARK_OAK_LEAVES.defaultBlockState();
        BlockState blossom = Blocks.FLOWERING_AZALEA_LEAVES.defaultBlockState();
        for (int k = 0; k < 5; k++) {
            double a = k * 1.3;
            int bx = (int) Math.round(Math.cos(a) * 5), bz = (int) Math.round(Math.sin(a) * 5), by = 11 + k % 3;
            spike(m, 0.5, 10 + k % 3, 0.5, bx, 3, bz, 6, 0.8, t -> wood);
            blob(m, bx, by + 3, bz, 3.4, k % 2 == 0 ? leaves : blossom, rnd);
        }
        blob(m, 0, 18, 0, 4.5, leaves, rnd);
        m.foliage = 0x3F7A2A;
        return m;
    }

    private static Model titan() {
        Model m = new Model();
        Random rnd = new Random(10);
        plate(m, 11, Blocks.MOSS_BLOCK, Blocks.ROOTED_DIRT);
        BlockState bone = log(Blocks.BONE_BLOCK, Direction.Axis.Y);
        for (int x = -9; x <= 9; x++) m.set(x, 1, 0, log(Blocks.BONE_BLOCK, Direction.Axis.X));
        for (int x = -7; x <= 7; x += 2) {
            double s = Math.sqrt(1 - Math.pow(x / 9.0, 2));
            for (int side = -1; side <= 1; side += 2) {
                for (double th = 0; th < Math.PI * 0.85; th += 0.05) {
                    int z = (int) Math.round(side * 7 * s * Math.sin(th)), y = (int) Math.round(1 + 6 * s * (1 - Math.cos(th)));
                    m.set(x, y, z, bone);
                }
            }
        }
        // the skull
        for (int y = 0; y <= 4; y++) for (int z = -3; z <= 3; z++) for (int x = 10; x <= 15; x++) {
            double d = Math.sqrt(Math.pow(x - 12.5, 2) + Math.pow(y - 2, 2) * 1.4 + z * z);
            boolean eye = x == 15 && y == 3 && Math.abs(z) == 1;
            if (d <= 3.2 && !eye) m.set(x, y, z, Blocks.BONE_BLOCK);
        }
        for (int k = 0; k < 40; k++) {
            int x = rnd.nextInt(21) - 10, z = rnd.nextInt(21) - 10;
            if (m.has(x, 0, z) && !m.has(x, 1, z)) m.set(x, 1, z, rnd.nextDouble() < 0.7 ? Blocks.MOSS_CARPET : Blocks.AZALEA);
        }
        return m;
    }

    private static Model hollow() {
        Model m = new Model();
        Random rnd = new Random(12);
        for (int z = -10; z <= 10; z++) for (int x = -10; x <= 10; x++) if (x * x + z * z <= 100) m.set(x, -10, z, Blocks.LIGHT_BLUE_STAINED_GLASS);
        Block[] bands = {Blocks.STONE, Blocks.ANDESITE, Blocks.TUFF, Blocks.STONE, Blocks.CALCITE, Blocks.DEEPSLATE};
        for (int y = -7; y <= 0; y++) {
            for (int z = -7; z <= 7; z++) for (int x = -7; x <= 7; x++) {
                double q = Math.hypot(x, z) / 7.0;
                if (q > 1) continue;
                double bottom = -7 * Math.pow(1 - q * q, 0.8);
                if (y < bottom) continue;
                m.set(x, y, z, y == 0 ? Blocks.MOSS_BLOCK : bands[Math.floorMod(y + (x + z) / 6, bands.length)]);
            }
        }
        for (int k = 0; k < 7; k++) {
            int x = rnd.nextInt(11) - 5, z = rnd.nextInt(11) - 5;
            if (m.has(x, 0, z)) m.set(x, 1, z, ModBlocks.GLOWCAP.get());
        }
        // a huge glowcap
        for (int y = 1; y <= 5; y++) m.set(2, y, -1, Blocks.MUSHROOM_STEM);
        for (int z = -4; z <= 2; z++) for (int x = -1; x <= 5; x++) if (Math.hypot(x - 2, z + 1) <= 3.2) m.set(x, 6, z, ModBlocks.GLOWCAP_BLOCK.get());
        return m;
    }

    private static Model hangingMountains() {
        Model m = new Model();
        for (int z = -10; z <= 10; z++) for (int x = -10; x <= 10; x++) if (x * x + z * z <= 110) { m.set(x, 14, z, Blocks.STONE); m.set(x, 15, z, Blocks.DEEPSLATE); }
        int[][] peaks = {{0, 0, 13}, {-6, 3, 8}, {5, -4, 9}, {4, 6, 6}, {-4, -6, 7}};
        for (int[] p : peaks) {
            int h = p[2];
            for (int d = 0; d < h; d++) {
                double r = (h - d) * 0.55;
                int n = (int) Math.ceil(r);
                for (int z = -n; z <= n; z++) for (int x = -n; x <= n; x++) {
                    if (Math.hypot(x, z) > r) continue;
                    m.set(p[0] + x, 13 - d, p[1] + z, d > h - 4 ? Blocks.PACKED_ICE : d > h - 6 && (x + z) % 2 == 0 ? Blocks.SNOW_BLOCK : Blocks.STONE);
                }
            }
        }
        return m;
    }

    private static Model skyIsle() {
        Model m = new Model();
        Random rnd = new Random(14);
        for (int z = -9; z <= 9; z++) for (int x = -9; x <= 9; x++) {
            double q = Math.hypot(x, z) / (9 * (1 + 0.15 * Math.sin(3 * Math.atan2(z, x))));
            if (q > 1) continue;
            int bottom = (int) Math.round(-12 * Math.pow(1 - Math.pow(q, 1.6), 0.8)) - (rnd.nextDouble() < 0.12 ? 2 : 0);
            for (int y = bottom; y <= 0; y++) m.set(x, y, z, y == 0 ? Blocks.GRASS_BLOCK : y > -3 ? Blocks.DIRT : rnd.nextDouble() < 0.2 ? Blocks.ANDESITE : Blocks.STONE);
            if (rnd.nextDouble() < 0.08) for (int k = 1; k <= 3; k++) m.set(x, bottom - k, z, (k == 3 ? Blocks.CAVE_VINES : Blocks.CAVE_VINES_PLANT).defaultBlockState().setValue(CaveVines.BERRIES, k == 2));
        }
        for (int y = 1; y <= 5; y++) m.set(-2, y, 1, log(Blocks.OAK_LOG, Direction.Axis.Y));
        blob(m, -2, 6, 1, 2.8, Blocks.OAK_LEAVES.defaultBlockState(), rnd);
        for (int k = 0; k < 10; k++) {
            int x = rnd.nextInt(15) - 7, z = rnd.nextInt(15) - 7;
            if (m.has(x, 0, z) && !m.has(x, 1, z)) m.set(x, 1, z, rnd.nextBoolean() ? Blocks.POPPY : Blocks.OXEYE_DAISY);
        }
        // a stream falling off the rim
        for (int y = 0; y >= -16; y--) m.set(8, y, 0, Blocks.LIGHT_BLUE_STAINED_GLASS);
        for (int x = 5; x <= 8; x++) m.set(x, 0, 0, Blocks.LIGHT_BLUE_STAINED_GLASS);
        // a small isle off to the side
        for (int z = -2; z <= 2; z++) for (int x = 11; x <= 15; x++) if (Math.hypot(x - 13, z) <= 2.3) { m.set(x, 4, z, Blocks.GRASS_BLOCK); m.set(x, 3, z, Blocks.STONE); }
        m.set(13, 2, 0, Blocks.STONE);
        return m;
    }

    private static Model volcano() {
        Model m = new Model();
        Random rnd = new Random(16);
        for (int z = -12; z <= 12; z++) for (int x = -12; x <= 12; x++) {
            double d = Math.hypot(x, z);
            if (d > 12) continue;
            double h = 13 * Math.pow(1 - d / 12, 1.4);
            if (d < 3) h = 13 * Math.pow(1 - 3 / 12.0, 1.4) - 3 + d;
            for (int y = 0; y <= (int) h; y++) {
                Block b = y == (int) h ? (d < 3 ? Blocks.MAGMA_BLOCK : rnd.nextDouble() < 0.3 ? Blocks.TUFF : Blocks.BLACKSTONE) : Blocks.BASALT;
                m.set(x, y, z, b);
            }
        }
        // a lava channel down one flank
        for (int s = 3; s < 12; s++) {
            int x = s, z = (int) Math.round(Math.sin(s * 0.6) * 1.2);
            for (int y = 13; y >= 0; y--) if (m.has(x, y, z)) { m.set(x, y, z, Blocks.MAGMA_BLOCK); break; }
        }
        return m;
    }

    private static Model needles() {
        Model m = new Model();
        Random rnd = new Random(18);
        plate(m, 10, Blocks.GRASS_BLOCK, Blocks.STONE);
        int[][] n = {{0, 0, 22, 3}, {-6, 4, 15, 2}, {5, -5, 18, 2}, {6, 5, 11, 2}, {-5, -6, 13, 2}};
        for (int[] p : n) {
            for (int y = 1; y <= p[2]; y++) for (int z = -p[3]; z <= p[3]; z++) for (int x = -p[3]; x <= p[3]; x++) {
                if (Math.hypot(x, z) > p[3] + 0.4) continue;
                m.set(p[0] + x, y, p[1] + z, rnd.nextDouble() < 0.3 ? Blocks.ANDESITE : Blocks.STONE);
            }
            m.set(p[0], p[2] + 1, p[1], Blocks.GRASS_BLOCK);
            for (int y = p[2] + 1; y <= p[2] + 3; y++) m.set(p[0], y, p[1], log(Blocks.SPRUCE_LOG, Direction.Axis.Y));
            blob(m, p[0], p[2] + 4, p[1], 1.8, Blocks.SPRUCE_LEAVES.defaultBlockState(), rnd);
        }
        // a wisp of cloud between them
        for (int x = -9; x <= 9; x++) for (int z = -3; z <= 3; z++) if (rnd.nextDouble() < 0.5 && !m.has(x, 8, z + 1)) m.set(x, 8, z + 1, Blocks.WHITE_STAINED_GLASS);
        return m;
    }

    private static Model silverTree() {
        Model m = new Model();
        Random rnd = new Random(20);
        plate(m, 10, Blocks.GRASS_BLOCK, Blocks.DIRT);
        for (int y = 1; y <= 13; y++) for (int z = 0; z <= 1; z++) for (int x = 0; x <= 1; x++) m.set(x, y, z, log(Blocks.BIRCH_LOG, Direction.Axis.Y));
        BlockState leaf = Blocks.OAK_LEAVES.defaultBlockState();
        for (int k = 0; k < 5; k++) {
            double a = k * 1.25;
            int bx = (int) Math.round(Math.cos(a) * 5), bz = (int) Math.round(Math.sin(a) * 5);
            spike(m, 0.5, 8 + k, 0.5, bx, 3, bz, 5, 0.6, t -> Blocks.BIRCH_WOOD.defaultBlockState());
            blob(m, bx, 12 + k, bz, 3.2, leaf, rnd);
        }
        blob(m, 0, 16, 0, 4, leaf, rnd);
        for (int k = 0; k < 16; k++) {
            int x = rnd.nextInt(19) - 9, z = rnd.nextInt(19) - 9;
            if (m.has(x, 0, z) && !m.has(x, 1, z)) m.set(x, 1, z, rnd.nextBoolean() ? Blocks.LILY_OF_THE_VALLEY : Blocks.SHORT_GRASS);
        }
        m.foliage = 0xE8B444;
        m.grass = 0xA6C45C;
        return m;
    }

    private static Model glimmerVale() {
        Model m = new Model();
        Random rnd = new Random(22);
        for (int z = -9; z <= 9; z++) for (int x = -10; x <= 10; x++) {
            double bank = Math.abs(z - 2 * Math.sin(x * 0.35));
            int h = bank < 2.2 ? -2 : (int) Math.min(7, (bank - 2) * 1.4);
            for (int y = -3; y <= h; y++) m.set(x, y, z, y == h && h >= 0 ? (rnd.nextDouble() < 0.3 ? Blocks.MOSS_BLOCK : Blocks.GRASS_BLOCK) : y > h - 3 ? Blocks.DIRT : Blocks.STONE);
            if (h < 0) {
                m.set(x, -2, z, Blocks.SAND);
                if (rnd.nextDouble() < 0.4) m.set(x, -1, z, Blocks.SEA_PICKLE.defaultBlockState().setValue(SeaPickleBlock.PICKLES, 1 + rnd.nextInt(4)));
                else m.set(x, -1, z, Blocks.LIGHT_BLUE_STAINED_GLASS);
                m.set(x, 0, z, Blocks.LIGHT_BLUE_STAINED_GLASS);
            } else if (rnd.nextDouble() < 0.12 && h < 7) {
                m.set(x, h + 1, z, rnd.nextDouble() < 0.5 ? Blocks.FLOWERING_AZALEA : Blocks.ALLIUM);
            }
        }
        m.grass = 0x5FD27A;
        return m;
    }

    private static Model mountain() {
        Model m = new Model();
        Random rnd = new Random(24);
        for (int z = -12; z <= 12; z++) for (int x = -12; x <= 12; x++) {
            double ridge = 18 - Math.abs(z) * 1.5 - Math.abs(x) * 0.6 + 3 * Math.sin(x * 0.7) + rnd.nextDouble();
            int h = (int) Math.max(0, ridge);
            for (int y = 0; y <= h; y++) m.set(x, y, z, y == h ? (y > 11 ? Blocks.SNOW_BLOCK : y > 6 ? Blocks.STONE : Blocks.GRASS_BLOCK) : Blocks.STONE);
        }
        return m;
    }

    private static Model standingStones() {
        Model m = new Model();
        Random rnd = new Random(26);
        plate(m, 11, Blocks.GRASS_BLOCK, Blocks.DIRT);
        int n = 9;
        for (int k = 0; k < n; k++) {
            double a = k * Math.PI * 2 / n + 0.3;
            int x = (int) Math.round(Math.cos(a) * 8), z = (int) Math.round(Math.sin(a) * 8), h = 5 + rnd.nextInt(3);
            boolean alongX = Math.abs(Math.cos(a)) < 0.7;
            for (int y = 1; y <= h; y++) for (int w = 0; w < 2; w++) {
                Block b = y % 3 == 0 ? Blocks.CHISELED_STONE_BRICKS : rnd.nextDouble() < 0.3 ? Blocks.MOSSY_STONE_BRICKS : rnd.nextDouble() < 0.5 ? Blocks.ANDESITE : Blocks.STONE;
                m.set(x + (alongX ? w : 0), y, z + (alongX ? 0 : w), b);
            }
        }
        for (int z = -1; z <= 1; z++) for (int x = -1; x <= 1; x++) m.set(x, 1, z, Blocks.POLISHED_DEEPSLATE);
        m.set(0, 2, 0, Blocks.CHISELED_DEEPSLATE);
        BlockState candle = Blocks.CANDLE.defaultBlockState().setValue(CandleBlock.CANDLES, 3).setValue(CandleBlock.LIT, true);
        m.set(1, 2, 1, candle);
        m.set(-1, 2, -1, candle);
        return m;
    }

    private static Model floatingRocks() {
        Model m = new Model();
        Random rnd = new Random(28);
        plate(m, 10, Blocks.GRASS_BLOCK, Blocks.DIRT);
        int[][] rocks = {{0, 9, 0, 4}, {-6, 6, 4, 2}, {5, 12, 4, 3}, {4, 5, -6, 2}};
        for (int[] r : rocks) {
            for (int z = -r[3]; z <= r[3]; z++) for (int x = -r[3]; x <= r[3]; x++) {
                double q = Math.hypot(x, z) / (r[3] + 0.4);
                if (q > 1) continue;
                int bottom = (int) Math.round(-r[3] * 1.4 * Math.pow(1 - q, 0.9));
                for (int y = bottom; y <= 0; y++) m.set(r[0] + x, r[1] + y, r[2] + z, y == 0 ? Blocks.GRASS_BLOCK : y == -1 ? Blocks.DIRT : rnd.nextDouble() < 0.2 ? Blocks.MOSSY_COBBLESTONE : Blocks.STONE);
                if (rnd.nextDouble() < 0.15) for (int k = 1; k <= 2; k++) m.set(r[0] + x, r[1] + bottom - k, r[2] + z, (k == 2 ? Blocks.CAVE_VINES : Blocks.CAVE_VINES_PLANT).defaultBlockState().setValue(CaveVines.BERRIES, true));
            }
        }
        return m;
    }

    private static Model boneField() {
        Model m = new Model();
        plate(m, 11, Blocks.SAND, Blocks.SANDSTONE);
        BlockState bone = log(Blocks.BONE_BLOCK, Direction.Axis.Y);
        for (int x = -8; x <= 8; x++) m.set(x, 1, 0, log(Blocks.BONE_BLOCK, Direction.Axis.X));
        for (int x = -6; x <= 6; x += 3) {
            double s = Math.sqrt(1 - Math.pow(x / 8.5, 2));
            for (int side = -1; side <= 1; side += 2) for (double th = 0; th < Math.PI * 0.8; th += 0.05) {
                m.set(x, (int) Math.round(1 + 6 * s * (1 - Math.cos(th))), (int) Math.round(side * 6 * s * Math.sin(th)), bone);
            }
        }
        for (int y = 0; y <= 3; y++) for (int z = -2; z <= 2; z++) for (int x = 9; x <= 13; x++)
            if (Math.sqrt(Math.pow(x - 11, 2) + Math.pow(y - 1.5, 2) * 1.4 + z * z) <= 2.6 && !(x == 13 && y == 2 && Math.abs(z) == 1)) m.set(x, y, z, Blocks.BONE_BLOCK);
        return m;
    }

    private static Model petrified() {
        Model m = new Model();
        Random rnd = new Random(30);
        plate(m, 10, Blocks.RED_SAND, Blocks.TERRACOTTA);
        for (int y = 1; y <= 9; y++) for (int z = -2; z <= 2; z++) for (int x = -2; x <= 2; x++) {
            double d = Math.hypot(x, z);
            if (d > 2.1 || y > 7 && rnd.nextDouble() < 0.5) continue;
            m.set(x - 3, y, z - 2, d < 1 ? Blocks.CALCITE : rnd.nextDouble() < 0.6 ? Blocks.TUFF : Blocks.DRIPSTONE_BLOCK);
        }
        for (int s = 0; s < 12; s++) {
            if (s == 6 || s == 7) continue;
            for (int b = -1; b <= 1; b++) for (int c = 0; c <= 2; c++) {
                if (Math.hypot(b, c - 1) > 1.5) continue;
                m.set(s - 4, 1 + c, 5 + b, (b == 0 && c == 1) ? Blocks.CALCITE : rnd.nextDouble() < 0.6 ? Blocks.TUFF : Blocks.DRIPSTONE_BLOCK);
            }
        }
        return m;
    }

    private static Model fungal() {
        Model m = new Model();
        Random rnd = new Random(32);
        plate(m, 10, Blocks.MYCELIUM, Blocks.DIRT);
        for (int y = 1; y <= 10; y++) m.set(0, y, 0, Blocks.MUSHROOM_STEM);
        for (int z = -5; z <= 5; z++) for (int x = -5; x <= 5; x++) {
            double d = Math.hypot(x, z);
            if (d <= 5) m.set(x, 11, z, ModBlocks.GLOWCAP_BLOCK.get());
            if (d > 3.8 && d <= 5.2) m.set(x, 10, z, ModBlocks.GLOWCAP_BLOCK.get());
        }
        for (int y = 1; y <= 6; y++) m.set(6, y, 5, Blocks.MUSHROOM_STEM);
        for (int z = 2; z <= 8; z++) for (int x = 3; x <= 9; x++) for (int y = 6; y <= 9; y++) {
            double e = Math.sqrt(Math.pow(x - 6, 2) + Math.pow((y - 6) * 1.3, 2) + Math.pow(z - 5, 2));
            if (e <= 3.3 && e > 2.2) m.set(x, y, z, Blocks.RED_MUSHROOM_BLOCK);
        }
        for (int k = 0; k < 12; k++) {
            int x = rnd.nextInt(19) - 9, z = rnd.nextInt(19) - 9;
            if (m.has(x, 0, z) && !m.has(x, 1, z)) m.set(x, 1, z, ModBlocks.GLOWCAP.get());
        }
        return m;
    }

    private static Model crystalCave() {
        Model m = new Model();
        for (int y = -1; y <= 12; y++) for (int z = -8; z <= 0; z++) for (int x = -10; x <= 10; x++) {
            boolean wall = y == -1 || z == -8 || Math.abs(x) == 10;
            if (wall) m.set(x, y, z, Blocks.STONE);
        }
        BlockState sel = ModBlocks.SELENITE.get().defaultBlockState();
        spike(m, -9.5, 1, -6, 1, 0.45, 0.35, 21, 1.3, t -> sel);
        spike(m, 9.5, 3, -7, -1, 0.3, 0.5, 18, 1.1, t -> sel);
        spike(m, -3, 0, -7.5, 0.3, 1, 0.4, 12, 1.0, t -> sel);
        for (int x = -9; x <= 9; x += 3) m.set(x, 0, -2, ModBlocks.SELENITE_SHARD.get().defaultBlockState().setValue(AmethystClusterBlock.FACING, Direction.UP));
        return m;
    }

    private static Model hoodoos() {
        Model m = new Model();
        Random rnd = new Random(34);
        plate(m, 10, Blocks.RED_SAND, Blocks.TERRACOTTA);
        Block[] bands = {Blocks.ORANGE_TERRACOTTA, Blocks.TERRACOTTA, Blocks.WHITE_TERRACOTTA, Blocks.RED_TERRACOTTA, Blocks.YELLOW_TERRACOTTA};
        int[][] h = {{0, 0, 12}, {-5, 3, 8}, {4, -4, 10}, {5, 5, 6}, {-4, -5, 7}};
        for (int[] p : h) {
            for (int y = 1; y <= p[2]; y++) {
                double r = y > p[2] - 2 ? 1.8 : y > p[2] - 4 ? 0.6 : 1.2;
                int n = (int) Math.ceil(r);
                for (int z = -n; z <= n; z++) for (int x = -n; x <= n; x++)
                    if (Math.hypot(x, z) <= r) m.set(p[0] + x, y, p[1] + z, y > p[2] - 2 ? Blocks.BROWN_TERRACOTTA : bands[(y + rnd.nextInt(1)) % bands.length]);
            }
        }
        return m;
    }

    private static Model caldera() {
        Model m = new Model();
        for (int z = -12; z <= 12; z++) for (int x = -12; x <= 12; x++) {
            double d = Math.hypot(x, z);
            if (d > 12) continue;
            int h = d < 7 ? 1 : (int) Math.round(9 - Math.abs(d - 8.5) * 2.2);
            h = Math.max(h, d < 7 ? 1 : 0);
            for (int y = 0; y <= h; y++) m.set(x, y, z, y == h ? (d < 7 ? Blocks.STONE : h > 3 ? Blocks.GRASS_BLOCK : Blocks.STONE) : Blocks.STONE);
            if (d < 7) for (int y = 2; y <= 5; y++) m.set(x, y, z, Blocks.BLUE_STAINED_GLASS);
        }
        for (int z = -1; z <= 1; z++) for (int x = 2; x <= 4; x++) for (int y = 2; y <= 6; y++) if (Math.hypot(x - 3, z) <= (7 - y) * 0.5) m.set(x, y, z, Blocks.BASALT);
        return m;
    }

    private static Model canyon() {
        Model m = new Model();
        Block[] bands = {Blocks.TERRACOTTA, Blocks.ORANGE_TERRACOTTA, Blocks.WHITE_TERRACOTTA, Blocks.RED_TERRACOTTA, Blocks.YELLOW_TERRACOTTA, Blocks.BROWN_TERRACOTTA};
        for (int z = -10; z <= 10; z++) for (int x = -10; x <= 10; x++) {
            double c = Math.abs(z - 2.5 * Math.sin(x * 0.3));
            int floor = c < 2 ? 1 : c < 4 ? (int) ((c - 2) * 5) : 12;
            for (int y = 0; y <= floor; y++) m.set(x, y, z, y == floor && floor == 12 ? Blocks.RED_SAND : bands[y % bands.length]);
            if (c < 1.2) m.set(x, 2, z, Blocks.LIGHT_BLUE_STAINED_GLASS);
        }
        return m;
    }

    /** A tepui: a sheer-walled table of rock out of jungle, moss on its top, a fall pouring off one side. */
    private static Model tepui() {
        Model m = new Model();
        m.foliage = 0x30BB0B;
        m.grass = 0x59C93C;
        Random rnd = new Random(31);
        for (int z = -14; z <= 14; z++) for (int x = -14; x <= 14; x++) {
            double a = Math.atan2(z, x), d = Math.hypot(x, z);
            double rim = 7.5 + 1.3 * Math.sin(3 * a + 0.7) + 0.7 * Math.sin(5 * a + 2.1);
            if (d > 14.2) continue;
            if (d < rim) {
                int top = 16 - (Math.abs(x * 3 + z * 5) % 7 == 0 ? 1 : 0);
                for (int y = 0; y <= top; y++) {
                    Block b = y == top ? ((x * 7 + z * 13) % 3 == 0 ? Blocks.MOSS_BLOCK : Blocks.TUFF)
                            : y % 4 == 0 ? Blocks.BROWN_TERRACOTTA : (x + y + z) % 5 == 0 ? Blocks.TUFF : Blocks.TERRACOTTA;
                    m.set(x, y, z, b);
                }
            } else {
                int h = (int) Math.max(1, 5 - (d - rim) * 0.9);
                for (int y = 0; y <= h; y++) m.set(x, y, z, y == h ? Blocks.GRASS_BLOCK : Blocks.DIRT);
                if (rnd.nextDouble() < 0.12 && d > rim + 1) blob(m, x, h + 3, z, 1.6, Blocks.JUNGLE_LEAVES.defaultBlockState(), rnd);
            }
        }
        // The fall: off the rim on the +x side, down the wall to a pool at its foot.
        int rx = 8;
        while (rx > 0 && !m.has(rx, 16, 0)) rx--;
        for (int z = -1; z <= 1; z++) {
            for (int x = rx - 3; x <= rx; x++) m.set(x, 16, z, Blocks.LIGHT_BLUE_STAINED_GLASS);
            for (int y = 2; y <= 16; y++) m.set(rx + 1, y, z, Blocks.LIGHT_BLUE_STAINED_GLASS);
            for (int x = rx + 1; x <= rx + 3; x++) m.set(x, 2, z, Blocks.LIGHT_BLUE_STAINED_GLASS);
        }
        return m;
    }

    /** A rift valley: a flat floor between walls stepping up in sheer scarps, a cone standing on the floor. */
    private static Model rift() {
        Model m = new Model();
        m.grass = 0xBFB755;
        for (int z = -12; z <= 12; z++) for (int x = -12; x <= 12; x++) {
            int s = Math.abs(z), h = s <= 4 ? 1 : s <= 7 ? 4 : s <= 10 ? 7 : 10;
            if (s > 4) h += (s == 5 || s == 8 || s == 11) ? 1 : 0; // the tilted shelves, highest at each scarp's lip
            for (int y = 0; y <= h; y++) m.set(x, y, z, y == h ? Blocks.GRASS_BLOCK : (y > h - 3 ? Blocks.DIRT : Blocks.STONE));
            if (s <= 4 && Math.abs(z - 1) <= 1 && x > 3) m.set(x, 1, z, Blocks.LIGHT_BLUE_STAINED_GLASS);
        }
        for (int z = -3; z <= 3; z++) for (int x = -7; x <= -1; x++) {
            double d = Math.hypot(x + 4, z);
            int h = (int) Math.round(7 - d * 2);
            for (int y = 2; y <= h; y++) m.set(x, y, z, d < 0.9 && y == h ? Blocks.MAGMA_BLOCK : Blocks.BASALT);
        }
        return m;
    }

    /** A rainbow ridge: a sharp crest striped in bands of colour from foot to top. */
    private static Model rainbow() {
        Model m = new Model();
        Block[] bands = {Blocks.RED_TERRACOTTA, Blocks.ORANGE_TERRACOTTA, Blocks.YELLOW_TERRACOTTA, Blocks.WHITE_TERRACOTTA, Blocks.LIME_TERRACOTTA,
                Blocks.CYAN_TERRACOTTA, Blocks.PINK_TERRACOTTA, Blocks.PURPLE_TERRACOTTA, Blocks.RED_TERRACOTTA, Blocks.YELLOW_TERRACOTTA, Blocks.ORANGE_TERRACOTTA};
        for (int z = -12; z <= 12; z++) for (int x = -12; x <= 12; x++) {
            double crest = Math.abs(z - 3 * Math.sin(x * 0.35)), second = Math.hypot(x - 6, z + 7);
            int h = (int) Math.max(Math.max(1, 13 - crest * 1.6), 9 - second * 1.5);
            for (int y = 0; y <= h; y++) m.set(x, y, z, bands[(y + (x > 0 ? 1 : 0)) % bands.length]);
        }
        return m;
    }

    /** Spotted Lake: round pools of every colour walled apart by white crust. */
    private static Model spottedLake() {
        Model m = new Model();
        m.grass = 0x91BD59;
        Block[] floors = {Blocks.LIME_TERRACOTTA, Blocks.GREEN_TERRACOTTA, Blocks.YELLOW_TERRACOTTA, Blocks.CYAN_TERRACOTTA,
                Blocks.LIGHT_BLUE_TERRACOTTA, Blocks.LIME_CONCRETE, Blocks.LIGHT_BLUE_CONCRETE};
        Random rnd = new Random(5);
        List<int[]> spots = new ArrayList<>();
        for (int k = 0; k < 22; k++) spots.add(new int[] {rnd.nextInt(21) - 10, rnd.nextInt(21) - 10, rnd.nextInt(floors.length)});
        for (int z = -13; z <= 13; z++) for (int x = -13; x <= 13; x++) {
            double d = Math.hypot(x, z);
            if (d > 13.4) continue;
            if (d > 11) {
                m.set(x, 0, z, Blocks.DIRT);
                m.set(x, 1, z, Blocks.GRASS_BLOCK);
                continue;
            }
            int[] best = null;
            double d1 = 99, d2 = 99;
            for (int[] s : spots) {
                double e = Math.hypot(x - s[0], z - s[1]);
                if (e < d1) { d2 = d1; d1 = e; best = s; } else if (e < d2) d2 = e;
            }
            m.set(x, 0, z, Blocks.CALCITE);
            if ((d2 - d1) / 2 < 0.7 || d > 10) m.set(x, 1, z, Blocks.CALCITE);
            else {
                m.set(x, 0, z, floors[best[2]]);
                m.set(x, 1, z, Blocks.LIGHT_BLUE_STAINED_GLASS);
            }
        }
        return m;
    }
}
