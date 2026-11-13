package com.seaweed.grandatlas.world;

import com.mojang.serialization.MapCodec;
import com.seaweed.grandatlas.core.AtlasCore;
import com.seaweed.grandatlas.core.SetPieces;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.feature.Feature;
import javax.annotation.Nullable;

/**
 * {@code grandatlas:set_piece_dressing} (M7e): the set pieces' own rock, laid into the solid blocks
 * they claim ({@link SetPieces#sample}): bone for Titan's grave, amethyst, calcite and basalt for
 * the Great Geode's wall and selenite for its pillars, banded tuff and calcite in the Hollow's
 * islands. Only bare stone is replaced, so moss the cave rules grew over a bone or an island top
 * stays: overgrown bones. It also clears vanilla's aquifer lava, water and barrier walls out of the
 * open, dry space of the set pieces and mega-caverns. Runs at local_modifications, before ores and the cave dressing; writes
 * only into its own chunk.
 */
public record SetPieceDressing() implements AtlasFeature {
    public static final MapCodec<SetPieceDressing> CODEC = MapCodec.unit(SetPieceDressing::new);

    @Override
    public MapCodec<SetPieceDressing> codec() {
        return CODEC;
    }

    @Override
    public boolean place(WorldGenLevel level, ChunkGenerator chunkGenerator, RandomSource random, BlockPos origin) {
        AtlasCore core = core(level.getLevel());
        if (core == null) return false;
        int x0 = origin.getX() & ~15, z0 = origin.getZ() & ~15;
        SetPieces sp = core.setPieces();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        boolean placed = false;
        for (int dz = 0; dz < 16; dz++) {
            for (int dx = 0; dx < 16; dx++) {
                int x = x0 + dx, z = z0 + dz;
                placed |= clearCavern(level, core, x, z, pos);
                if (!sp.nearColumn(x, z)) continue;
                boolean well = sp.nearWell(x, z);
                if (sp.nearTree(x, z)) placed |= tree(level, sp, x, z, pos);
                for (int[] room : sp.roomFloors(x, z)) placed |= roomDetails(level, random, room, pos);
                int top = well || sp.nearTree(x, z) ? core.column(x, z).surfaceY() + 4 : Math.min(core.column(x, z).surfaceY() - 20, 300);
                if (well) placed |= wellSpires(level, sp, x, z, pos);
                for (int y = AtlasCore.MIN_Y + 6; y <= top; y++) {
                    SetPieces.Probe p = sp.sample(x, y, z);
                    if (!p.claimed) continue;
                    pos.set(x, y, z);
                    if (p.open && p.kind == SetPieces.ROOT && sp.inTaproot(x, y, z)) {
                        // Vines up the inside of the hollow taproot, to climb (or slow a fall).
                        BlockState here0 = level.getBlockState(pos);
                        if (!here0.isAir() && here0.getFluidState().isEmpty()) level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
                        placed |= taprootVine(level, sp, x, y, z, pos);
                        continue;
                    }
                    if (p.open && p.kind == SetPieces.VAULT) {
                        // Aurora shards hanging from the crystal bed into the lake (waterlogged, so
                        // DeepWater fills round them), and a few rising from the pool's bed.
                        placed |= vaultShard(level, sp, x, y, z, p, pos);
                        continue;
                    }
                    if (p.open) {
                        // Open: clear what vanilla's aquifers put there: their lava and water (which met
                        // and crusted the place in obsidian) and their barriers, thin stone walls between
                        // pockets of different water level that hung as floating sheets across the Great
                        // Geode. The piece's own water (lakes, pools) is placed later by DeepWater, which
                        // fills air but leaves rock alone, so the lakes are cleared too.
                        if (stray(level.getBlockState(pos), p.water)) {
                            level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
                            placed = true;
                        }
                        continue;
                    }
                    if (p.material == SetPieces.ROCK) continue;
                    BlockState state = p.material == SetPieces.AURORA
                            ? ModBlocks.AURORA_CRYSTAL[sp.auroraBand(x, z)].get().defaultBlockState() : block(p.material);
                    BlockState here = level.getBlockState(pos);
                    if (!bare(here) && !((p.material == SetPieces.WOOD || p.material == SetPieces.SAP)
                            && (here.is(net.minecraft.tags.BlockTags.DIRT) || here.is(net.minecraft.tags.BlockTags.SAND)))) continue;
                    level.setBlock(pos, state, 2);
                    placed = true;
                }
            }
        }
        return placed;
    }

    /**
     * The same clearing in a mega-cavern's open span: aquifer barriers and fluids out of the dry
     * open space and out of its lake (what the cavern's own carve says is open; DeepWater fills the lake).
     */
    private static boolean clearCavern(WorldGenLevel level, AtlasCore core, int x, int z, BlockPos.MutableBlockPos pos) {
        double[] span = core.megaCaverns().span(x, z);
        if (span == null) return false;
        double lake = core.megaCaverns().water(x, z);
        boolean placed = false;
        int lo = (int) Math.floor(span[0]) + 1, hi = (int) Math.ceil(span[1]);
        for (int y = Math.max(lo, AtlasCore.MIN_Y + 6); y <= hi; y++) {
            double carve = core.megaCaverns().carve(x, y, z);
            if (carve >= 0.3) continue;
            pos.set(x, y, z);
            BlockState here = level.getBlockState(pos);
            // Fluids go wherever the cavern is open or nearly so (lava left at the walls flows back);
            // stone only where it is clearly inside, so the walls keep their shape.
            if (here.getFluidState().isEmpty() ? carve >= -0.15 : false) continue;
            if (!stray(here, !Double.isNaN(lake) && y <= lake)) continue;
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
            placed = true;
        }
        // Aquifer lava and water in the pockets just over the roof: it hung there as a sheet of
        // fluid above the open cavern (seen 2026-11-09 at 732 -150 -868).
        for (int y = hi + 1; y <= hi + 8; y++) {
            BlockState here = level.getBlockState(pos.set(x, y, z));
            if (here.getFluidState().isEmpty()) {
                if (!here.isAir()) break; // rock: the roof proper
                continue;
            }
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
            placed = true;
        }
        return placed;
    }

    /** Something vanilla left in open space: anything but air, or, under a lake, anything but water. */
    private static boolean stray(BlockState here, boolean underWater) {
        if (here.isAir()) return false;
        return !(underWater && here.getFluidState().is(net.minecraft.tags.FluidTags.WATER));
    }

    /**
     * The Worldroot's tree over this column: its wood (veined with glowing sap) wherever it stands,
     * ground included, and its leaves (dark oak, flowering azalea in patches; persistent, so the
     * canopy never decays however far it is from a log) into the open air.
     */
    private static boolean tree(WorldGenLevel level, SetPieces sp, int x, int z, BlockPos.MutableBlockPos pos) {
        double[] span = sp.treeSpan(x, z);
        if (span == null) return false;
        boolean placed = false;
        BlockState leaves = Blocks.DARK_OAK_LEAVES.defaultBlockState().setValue(net.minecraft.world.level.block.LeavesBlock.PERSISTENT, true);
        BlockState blossom = Blocks.FLOWERING_AZALEA_LEAVES.defaultBlockState().setValue(net.minecraft.world.level.block.LeavesBlock.PERSISTENT, true);
        for (int y = (int) span[0] - 8; y <= (int) span[1]; y++) {
            int m = sp.treeAt(x, y, z);
            if (m == SetPieces.NONE) continue;
            pos.set(x, y, z);
            BlockState here = level.getBlockState(pos);
            if (m == SetPieces.WOOD || m == SetPieces.SAP) {
                if (here.is(Blocks.BEDROCK)) continue;
                if (y <= span[0] + 1 && sp.sample(x, y, z).open) continue; // keep the gate and the shaft open
                level.setBlock(pos, m == SetPieces.SAP ? ModBlocks.AMBER_SAP.get().defaultBlockState() : Blocks.DARK_OAK_WOOD.defaultBlockState(), 2);
            } else {
                if (!here.isAir() && !here.canBeReplaced() && !here.is(net.minecraft.tags.BlockTags.LEAVES)) continue;
                level.setBlock(pos, m == SetPieces.BLOSSOM ? blossom : leaves, 2);
            }
            placed = true;
        }
        return placed;
    }

    /** The Aurora Well's crystal spires, from just under the ground up, coloured like the lake's bed. */
    private static boolean wellSpires(WorldGenLevel level, SetPieces sp, int x, int z, BlockPos.MutableBlockPos pos) {
        int ground = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.OCEAN_FLOOR_WG, x, z);
        boolean placed = false;
        BlockState crystal = ModBlocks.AURORA_CRYSTAL[sp.auroraBand(x, z)].get().defaultBlockState();
        int topSpire = Integer.MIN_VALUE;
        for (int y = ground - 5; y <= ground + 22; y++) {
            if (!sp.wellSpire(x, y, z)) continue;
            BlockState here = level.getBlockState(pos.set(x, y, z));
            if (!here.isAir() && !here.canBeReplaced() && !here.is(net.minecraft.tags.BlockTags.DIRT)
                    && !here.is(net.minecraft.tags.BlockTags.BASE_STONE_OVERWORLD) && !here.is(net.minecraft.tags.BlockTags.SAND)) continue;
            level.setBlock(pos, crystal, 2);
            topSpire = y;
            placed = true;
        }
        // A shard on some spire tips, and scattered on the ground round the mouth.
        long h = com.seaweed.grandatlas.core.noise.GradientNoise.mix(com.seaweed.grandatlas.core.noise.GradientNoise.hash(0x3E11L, x, 0, z));
        double roll = (h >>> 11) * 0x1.0p-53;
        int at = topSpire != Integer.MIN_VALUE ? topSpire + 1 : roll < 0.06 ? ground : Integer.MIN_VALUE;
        if (at != Integer.MIN_VALUE && (topSpire != Integer.MIN_VALUE ? roll < 0.5 : true)) {
            pos.set(x, at, z);
            if (level.getBlockState(pos).isAir() && level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), net.minecraft.core.Direction.UP)) {
                level.setBlock(pos, ModBlocks.AURORA_SHARD[sp.auroraBand(x, z)].get().defaultBlockState()
                        .setValue(net.minecraft.world.level.block.AmethystClusterBlock.FACING, net.minecraft.core.Direction.UP), 2);
                placed = true;
            }
        }
        return placed;
    }

    private static boolean taprootVine(WorldGenLevel level, SetPieces sp, int x, int y, int z, BlockPos.MutableBlockPos pos) {
        long h = com.seaweed.grandatlas.core.noise.GradientNoise.mix(com.seaweed.grandatlas.core.noise.GradientNoise.hash(0x7A11L, x, 0, z));
        if ((h & 3) != 0) return false; // in strands, a quarter of the columns
        for (net.minecraft.core.Direction d : net.minecraft.core.Direction.Plane.HORIZONTAL) {
            if (sp.inTaproot(x + d.getStepX(), y, z + d.getStepZ())) continue;
            BlockState wall = level.getBlockState(pos.set(x + d.getStepX(), y, z + d.getStepZ()));
            if (!wall.isSolid()) continue;
            level.setBlock(pos.set(x, y, z), Blocks.VINE.defaultBlockState().setValue(net.minecraft.world.level.block.VineBlock.getPropertyForFace(d), true), 2);
            return true;
        }
        return false;
    }

    /** A room off the taproot: moss underfoot, a lantern of amber sap overhead, and (in some) a chest. */
    private static boolean roomDetails(WorldGenLevel level, RandomSource random, int[] room, BlockPos.MutableBlockPos pos) {
        int x = room[0], floor = room[1], z = room[2], ceil = room[3];
        if (level.getBlockState(pos.set(x, ceil + 1, z)).isSolid()) level.setBlock(pos, ModBlocks.AMBER_SAP.get().defaultBlockState(), 2);
        if (!level.getBlockState(pos.set(x, floor, z)).isAir()) return false;
        long h = com.seaweed.grandatlas.core.noise.GradientNoise.mix(com.seaweed.grandatlas.core.noise.GradientNoise.hash(0xC4E5L, x, floor, z));
        if ((h & 1) == 0) {
            level.setBlock(pos, Blocks.CHEST.defaultBlockState(), 2);
            if (level.getBlockEntity(pos) instanceof net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity chest) {
                chest.setLootTable(net.minecraft.world.level.storage.loot.BuiltInLootTables.SIMPLE_DUNGEON, random.nextLong());
            }
        } else {
            level.setBlock(pos, Blocks.MOSS_CARPET.defaultBlockState(), 2);
        }
        return true;
    }

    private static boolean vaultShard(WorldGenLevel level, SetPieces sp, int x, int y, int z, SetPieces.Probe p, BlockPos.MutableBlockPos pos) {
        boolean hang = p.hang, pool = p.water && !p.hang && !p.rise;
        BlockState here = level.getBlockState(pos);
        // Clear what vanilla left in the open space first (as for the other pieces).
        if (!here.isAir() && !(p.water && here.getFluidState().is(net.minecraft.tags.FluidTags.WATER) && here.getBlock() instanceof net.minecraft.world.level.block.LiquidBlock)) {
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
        }
        if (!hang && !pool) {
            // (Aurora veil blocks no longer hang here: the client-side aurora ribbons replaced them,
            // the user 2026-11-09. The blocks stay registered so older worlds still load.)
            return true;
        }
        long h = com.seaweed.grandatlas.core.noise.GradientNoise.mix(com.seaweed.grandatlas.core.noise.GradientNoise.hash(0x5A11L, x, y, z));
        double roll = (h >>> 11) * 0x1.0p-53;
        net.minecraft.core.Direction facing;
        if (hang) {
            SetPieces.Probe above = sp.sample(x, y + 1, z);
            if (above.open || above.material != SetPieces.AURORA || roll > 0.4) return true;
            facing = net.minecraft.core.Direction.DOWN;
        } else {
            SetPieces.Probe below = sp.sample(x, y - 1, z);
            if (below.open || roll > 0.12) return true;
            facing = net.minecraft.core.Direction.UP;
        }
        int band = hang ? sp.auroraBand(x, z) : (int) ((h >>> 3) % ModBlocks.AURORA_COLOURS.length);
        level.setBlock(pos, ModBlocks.AURORA_SHARD[band].get().defaultBlockState()
                .setValue(net.minecraft.world.level.block.AmethystClusterBlock.FACING, facing)
                .setValue(net.minecraft.world.level.block.AmethystClusterBlock.WATERLOGGED, true), 2);
        return true;
    }

    /** Bare stone, which may become the set piece's rock (moss, dirt, plants and fluids stay). */
    private static boolean bare(BlockState s) {
        return s.is(BlockTags.BASE_STONE_OVERWORLD) || s.is(Blocks.BLACKSTONE) || s.is(Blocks.BASALT) || s.is(Blocks.SMOOTH_BASALT)
                || s.is(Blocks.CALCITE) || s.is(Blocks.GRAVEL) || s.is(Blocks.CLAY) || s.is(Blocks.PACKED_ICE);
    }

    private static BlockState block(int material) {
        return switch (material) {
            case SetPieces.BONE -> Blocks.BONE_BLOCK.defaultBlockState();
            case SetPieces.AMETHYST -> Blocks.AMETHYST_BLOCK.defaultBlockState();
            case SetPieces.BUDDING -> Blocks.BUDDING_AMETHYST.defaultBlockState();
            case SetPieces.CALCITE -> Blocks.CALCITE.defaultBlockState();
            case SetPieces.BASALT -> Blocks.SMOOTH_BASALT.defaultBlockState();
            case SetPieces.TUFF -> Blocks.TUFF.defaultBlockState();
            case SetPieces.SELENITE -> ModBlocks.SELENITE.get().defaultBlockState();
            case SetPieces.WOOD -> Blocks.DARK_OAK_WOOD.defaultBlockState();
            case SetPieces.SAP -> ModBlocks.AMBER_SAP.get().defaultBlockState();
            case SetPieces.PACKED_ICE -> Blocks.PACKED_ICE.defaultBlockState();
            case SetPieces.BLUE_ICE -> Blocks.BLUE_ICE.defaultBlockState();
            default -> Blocks.STONE.defaultBlockState();
        };
    }

    private static @Nullable AtlasCore core(ServerLevel level) {
        return CoreHandle.coreOf(level);
    }
}
