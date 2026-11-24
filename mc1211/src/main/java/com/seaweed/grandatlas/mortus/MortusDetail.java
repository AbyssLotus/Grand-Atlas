package com.seaweed.grandatlas.mortus;

import com.mojang.serialization.MapCodec;
import com.seaweed.grandatlas.core.noise.GradientNoise;
import com.seaweed.grandatlas.world.AtlasFeature;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.MultifaceBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;

/**
 * {@code grandatlas:mortus_detail}: the realm's own landscape detail, where vanilla's features don't
 * have the shape (the polish pass, 2026-11-23). By region (MortusMap):
 * <ul>
 *   <li>Asphodel: grey poplars in loose stands, drifts of white asphodel flowers, pale boulders.</li>
 *   <li>Elysium: cherry groves with open flowered meadow between, the odd great oak.</li>
 *   <li>The springs: the white cypress on Lethe's bank (the Orphic tablets' landmark), calcite stones.</li>
 *   <li>The Groves of Persephone: black poplars and low pomegranate shrubs.</li>
 *   <li>The landing: driftwood; the Acheron swamp: moss on the banks.</li>
 *   <li>The entrance: basalt spires on its mountain; Phlegethon: magma vents on its banks.</li>
 *   <li>The spawn cave: soul lanterns over the ledge, glow lichen marking the passage out.</li>
 * </ul>
 * Hades' platform is left clear for the palace. Nothing grows out of the realm's water. Once a chunk;
 * everything stays in this chunk and its neighbours.
 */
public record MortusDetail() implements AtlasFeature {
    public static final MapCodec<MortusDetail> CODEC = MapCodec.unit(MortusDetail::new);
    private static final long SEED = 0x5EAF_DE7AL;

    @Override
    public MapCodec<MortusDetail> codec() {
        return CODEC;
    }

    @Override
    public boolean place(WorldGenLevel level, ChunkGenerator generator, RandomSource r, BlockPos origin) {
        int x0 = origin.getX() & ~15, z0 = origin.getZ() & ~15;
        boolean any = cave(level, r, x0, z0);
        for (int t = 0; t < 24; t++) {
            int x = x0 + r.nextInt(16), z = z0 + r.nextInt(16);
            BlockPos g = ground(level, x, z);
            if (g == null) continue;
            MortusMap.Region reg = MortusMap.region(x, z);
            double clump = GradientNoise.noise2(SEED, x / 48.0, z / 48.0);
            switch (reg) {
                case ASPHODEL, LANDING -> {
                    if (reg == MortusMap.Region.ASPHODEL && clump > 0.25 && t < 3 && r.nextInt(3) == 0) any |= poplar(level, r, g, Blocks.BIRCH_LOG.defaultBlockState(), Blocks.BIRCH_LEAVES.defaultBlockState(), 9 + r.nextInt(6));
                    else if (reg == MortusMap.Region.ASPHODEL && clump > -0.2) any |= flowers(level, r, g, 0);
                    else if (reg == MortusMap.Region.LANDING && t < 2 && r.nextInt(3) == 0) any |= driftwood(level, r, g);
                    if (t == 0 && r.nextInt(5) == 0) any |= boulder(level, r, g, r.nextBoolean() ? Blocks.TUFF.defaultBlockState() : Blocks.CALCITE.defaultBlockState());
                }
                case GROVES -> {
                    if (t < 3 && r.nextInt(2) == 0) any |= poplar(level, r, g, Blocks.DARK_OAK_LOG.defaultBlockState(), Blocks.SPRUCE_LEAVES.defaultBlockState(), 10 + r.nextInt(6));
                    else if (t < 7 && r.nextInt(3) == 0) any |= shrub(level, r, g);
                    else any |= flowers(level, r, g, 1);
                }
                case LETHE -> {
                    if (t == 0 && r.nextInt(3) == 0) any |= boulder(level, r, g, Blocks.CALCITE.defaultBlockState());
                }
                case ELYSIUM -> {
                    // cherry groves with open meadow between (vanilla's cherry forest was a solid wall)
                    if (t < 2 && clump > 0.15 && r.nextInt(2) == 0) any |= tree(level, generator, r, g, "cherry_bees_005");
                    else if (t == 2 && r.nextInt(6) == 0) any |= tree(level, generator, r, g, r.nextBoolean() ? "cherry_bees_005" : "fancy_oak_bees_005");
                    else any |= flowers(level, r, g, 2);
                }
                case ACHERON -> {
                    if (r.nextInt(3) == 0 && level.getBlockState(g).isAir() && level.getBlockState(g.below()).is(Blocks.MUD)) {
                        level.setBlock(g, Blocks.MOSS_CARPET.defaultBlockState(), 2);
                        any = true;
                    }
                }
                case ENTRANCE -> {
                    if (t < 4 && g.getY() > 100 && r.nextInt(2) == 0) any |= spire(level, r, g);
                }
                case PHLEGETHON -> {
                    if (r.nextInt(3) == 0 && level.getFluidState(g).isEmpty()) {
                        level.setBlock(g.below(), Blocks.MAGMA_BLOCK.defaultBlockState(), 2);
                        any = true;
                    }
                }
                default -> { }
            }
        }
        any |= cypress(level, x0, z0);
        return any;
    }

    /** A vanilla configured tree at g (on grass or dirt). */
    private static boolean tree(WorldGenLevel level, ChunkGenerator generator, RandomSource r, BlockPos g, String id) {
        if (!level.getBlockState(g.below()).is(net.minecraft.tags.BlockTags.DIRT)) return false;
        var key = net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.CONFIGURED_FEATURE,
                net.minecraft.resources.ResourceLocation.withDefaultNamespace(id));
        return level.registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.CONFIGURED_FEATURE).getHolder(key)
                .map(h -> h.value().place(level, generator, r, g)).orElse(false);
    }

    /** The air block on the ground at (x, z), from the realm's height, or null (water, the platform, a cliff). */
    private static BlockPos ground(WorldGenLevel level, int x, int z) {
        int h = (int) Math.floor(MortusMap.height(x, z));
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos(x, h + 4, z);
        for (int i = 0; i < 10; i++, p.move(0, -1, 0)) {
            BlockState here = level.getBlockState(p), below = level.getBlockState(p.below());
            if (here.isAir() && below.isSolid() && below.getFluidState().isEmpty()) return p.immutable();
            if (!here.getFluidState().isEmpty()) return null;
        }
        return null;
    }

    private static boolean place(WorldGenLevel level, BlockPos p, BlockState s) {
        BlockState here = level.getBlockState(p);
        if (!here.isAir() && !here.canBeReplaced()) return false;
        level.setBlock(p, s, 2);
        return true;
    }

    /** A poplar: a tall straight trunk, a narrow column of leaves widest a third of the way down. */
    private static boolean poplar(WorldGenLevel level, RandomSource r, BlockPos g, BlockState log, BlockState leaves, int h) {
        if (!level.getBlockState(g.below()).is(Blocks.GRASS_BLOCK) && !level.getBlockState(g.below()).is(Blocks.PODZOL)
                && !level.getBlockState(g.below()).is(Blocks.COARSE_DIRT)) return false;
        for (int i = 1; i <= h + 2; i++) if (!level.getBlockState(g.above(i)).isAir()) return false;
        BlockState lv = leaves.setValue(LeavesBlock.PERSISTENT, true);
        for (int i = 0; i < h; i++) level.setBlock(g.above(i), log, 2);
        int leafFrom = h / 3;
        for (int i = leafFrom; i <= h + 1; i++) {
            double t = (double) (i - leafFrom) / (h + 1 - leafFrom);
            double rad = i > h ? 0.5 : 1.2 + 0.9 * Math.sin(Math.PI * Math.min(1, t * 1.3));
            int rr = (int) Math.ceil(rad);
            for (int dx = -rr; dx <= rr; dx++) for (int dz = -rr; dz <= rr; dz++) {
                if (dx * dx + dz * dz > rad * rad + 0.3 || (dx == 0 && dz == 0 && i < h)) continue;
                if (r.nextInt(9) == 0) continue;
                place(level, g.offset(dx, i, dz), lv);
            }
        }
        return true;
    }

    /** Asphodel's white flowers (0), the Groves' red undergrowth (1), Elysium's gold and white (2), in small drifts. */
    private static boolean flowers(WorldGenLevel level, RandomSource r, BlockPos g, int kind) {
        BlockState[] pick = switch (kind) {
            case 0 -> new BlockState[] {Blocks.LILY_OF_THE_VALLEY.defaultBlockState(), Blocks.OXEYE_DAISY.defaultBlockState(),
                    Blocks.WHITE_TULIP.defaultBlockState(), Blocks.AZURE_BLUET.defaultBlockState()};
            case 1 -> new BlockState[] {Blocks.POPPY.defaultBlockState(), Blocks.RED_TULIP.defaultBlockState(), Blocks.RED_MUSHROOM.defaultBlockState()};
            default -> new BlockState[] {Blocks.DANDELION.defaultBlockState(), Blocks.OXEYE_DAISY.defaultBlockState(), Blocks.LILY_OF_THE_VALLEY.defaultBlockState()};
        };
        boolean any = false;
        for (int k = 0; k < 6; k++) {
            BlockPos q = g.offset(r.nextInt(5) - 2, 0, r.nextInt(5) - 2);
            BlockPos f = ground(level, q.getX(), q.getZ());
            if (f == null || !level.getBlockState(f.below()).is(Blocks.GRASS_BLOCK) && !level.getBlockState(f.below()).is(Blocks.PODZOL)
                    && !level.getBlockState(f.below()).is(Blocks.MOSS_BLOCK)) continue;
            any |= place(level, f, pick[r.nextInt(pick.length)]);
        }
        return any;
    }

    /** A low pomegranate shrub: a short dark trunk under a round head of leaves, red berries at its foot. */
    private static boolean shrub(WorldGenLevel level, RandomSource r, BlockPos g) {
        if (!level.getBlockState(g.below()).isSolid()) return false;
        int h = 2 + r.nextInt(2);
        for (int i = 0; i < h; i++) if (!place(level, g.above(i), Blocks.DARK_OAK_LOG.defaultBlockState())) return false;
        BlockState lv = Blocks.DARK_OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true);
        for (int dx = -2; dx <= 2; dx++) for (int dy = -1; dy <= 1; dy++) for (int dz = -2; dz <= 2; dz++) {
            if (dx * dx + dy * dy * 2 + dz * dz > 5 || r.nextInt(6) == 0) continue;
            place(level, g.offset(dx, h + dy, dz), lv);
        }
        for (int k = 0; k < 3; k++) {
            BlockPos b = g.offset(r.nextInt(3) - 1, 0, r.nextInt(3) - 1);
            if (level.getBlockState(b).isAir() && level.getBlockState(b.below()).is(Blocks.GRASS_BLOCK)) level.setBlock(b, Blocks.SWEET_BERRY_BUSH.defaultBlockState(), 2);
        }
        return true;
    }

    private static boolean boulder(WorldGenLevel level, RandomSource r, BlockPos g, BlockState s) {
        double rad = 1.3 + r.nextDouble() * 1.4;
        int n = (int) Math.ceil(rad);
        for (int dx = -n; dx <= n; dx++) for (int dy = -1; dy <= n; dy++) for (int dz = -n; dz <= n; dz++) {
            if (dx * dx + dy * dy * 1.4 + dz * dz > rad * rad) continue;
            BlockPos q = g.offset(dx, dy, dz);
            if (level.getBlockState(q).isAir() || level.getBlockState(q).canBeReplaced()) level.setBlock(q, s, 2);
        }
        return true;
    }

    private static boolean driftwood(WorldGenLevel level, RandomSource r, BlockPos g) {
        Direction d = r.nextBoolean() ? Direction.EAST : Direction.SOUTH;
        BlockState log = Blocks.STRIPPED_OAK_LOG.defaultBlockState().setValue(RotatedPillarBlock.AXIS, d.getAxis());
        int len = 3 + r.nextInt(4);
        boolean any = false;
        for (int i = 0; i < len; i++) {
            BlockPos q = g.relative(d, i);
            if (level.getBlockState(q).isAir() && level.getBlockState(q.below()).isSolid()) any |= place(level, q, log);
        }
        return any;
    }

    /** A spire of basalt on the entrance mountain. */
    private static boolean spire(WorldGenLevel level, RandomSource r, BlockPos g) {
        int h = 3 + r.nextInt(8);
        for (int i = 0; i < h; i++) {
            place(level, g.above(i), Blocks.BASALT.defaultBlockState());
            if (i < h / 2 && r.nextInt(3) == 0) place(level, g.above(i).relative(Direction.Plane.HORIZONTAL.getRandomDirection(r)), Blocks.BASALT.defaultBlockState());
        }
        return true;
    }

    /** The white cypress by Lethe: a tall pale tree on the far bank of the first spring, alone. */
    private static boolean cypress(WorldGenLevel level, int x0, int z0) {
        int cx = (int) (MortusMap.LX1 - MortusMap.LAKE_R - 6), cz = (int) MortusMap.LZ;
        if (cx < x0 || cx > x0 + 15 || cz < z0 || cz > z0 + 15) return false;
        BlockPos g = ground(level, cx, cz);
        if (g == null) return false;
        for (int i = 0; i < 18; i++) level.setBlock(g.above(i), Blocks.BIRCH_WOOD.defaultBlockState(), 2);
        BlockState lv = Blocks.BIRCH_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true);
        for (int i = 4; i <= 20; i++) {
            double t = (i - 4) / 16.0, rad = 2.6 * Math.sin(Math.PI * Math.min(1, t * 1.15)) + 0.6;
            int rr = (int) Math.ceil(rad);
            for (int dx = -rr; dx <= rr; dx++) for (int dz = -rr; dz <= rr; dz++) {
                if (dx * dx + dz * dz > rad * rad) continue;
                place(level, g.offset(dx, i, dz), lv);
            }
        }
        for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) {
            BlockPos q = g.offset(dx, -1, dz);
            if (level.getBlockState(q).is(Blocks.GRASS_BLOCK)) level.setBlock(q, Blocks.CALCITE.defaultBlockState(), 2);
        }
        return true;
    }

    /** The spawn cave: soul lanterns hung over the ledge, glow lichen down the passage's walls so the way out reads. */
    private static boolean cave(WorldGenLevel level, RandomSource r, int x0, int z0) {
        if (x0 > MortusMap.CAVE_X1 + 16 || x0 + 15 < MortusMap.CHX - MortusMap.CH_R - 2 || z0 > MortusMap.CHZ + 32 || z0 + 15 < MortusMap.CHZ - 32) return false;
        boolean any = false;
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        // lanterns: three over the ledge, hanging from the roof
        for (int k = -1; k <= 1; k++) {
            int x = MortusMap.SPAWN_X + k * 6, z = MortusMap.SPAWN_Z - 1;
            if (x < x0 || x > x0 + 15 || z < z0 || z > z0 + 15) continue;
            int y = MortusMap.SPAWN_Y + 2;
            while (y < MortusMap.SPAWN_Y + 20 && level.getBlockState(p.set(x, y + 1, z)).isAir()) y++;
            if (level.getBlockState(p.set(x, y + 1, z)).isAir()) continue;
            level.setBlock(p.set(x, y, z), Blocks.CHAIN.defaultBlockState(), 2);
            level.setBlock(p.set(x, y - 1, z), Blocks.CHAIN.defaultBlockState(), 2);
            level.setBlock(p.set(x, y - 2, z), Blocks.SOUL_LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true), 2);
            any = true;
        }
        // glow lichen on the walls of the chamber and the tunnel, a little above the water
        for (int t = 0; t < 80; t++) {
            int x = x0 + r.nextInt(16), z = z0 + r.nextInt(16), y = 63 + r.nextInt(6);
            if (!MortusMap.cave(x, y, z) || !level.getBlockState(p.set(x, y, z)).isAir()) continue;
            for (Direction d : Direction.Plane.HORIZONTAL) {
                BlockPos w = p.relative(d);
                if (level.getBlockState(w).isSolid()) {
                    level.setBlock(p, Blocks.GLOW_LICHEN.defaultBlockState().setValue(MultifaceBlock.getFaceProperty(d), true), 2);
                    any = true;
                    break;
                }
            }
        }
        return any;
    }
}
