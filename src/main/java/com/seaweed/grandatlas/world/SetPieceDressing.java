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
import net.minecraft.world.level.levelgen.densityfunction.SamplerContext;
import net.minecraft.world.level.levelgen.feature.Feature;
import org.jspecify.annotations.Nullable;

/**
 * {@code grandatlas:set_piece_dressing} (M7e): the set pieces' own rock, laid into the solid blocks
 * they claim ({@link SetPieces#sample}): bone for Titan's grave, amethyst, calcite and basalt for
 * the Great Geode's wall and selenite for its pillars, banded tuff and calcite in the Hollow's
 * islands. Only bare stone is replaced, so moss the cave rules grew over a bone or an island top
 * stays: overgrown bones. It also clears vanilla's aquifer lava, water and barrier walls out of the
 * open, dry space of the set pieces and mega-caverns. Runs at local_modifications, before ores and the cave dressing; writes
 * only into its own chunk.
 */
public record SetPieceDressing() implements Feature {
    public static final MapCodec<SetPieceDressing> CODEC = MapCodec.unit(SetPieceDressing::new);

    private static volatile @Nullable ServerLevel lastLevel;
    private static volatile @Nullable AtlasCore lastCore;

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
                int top = Math.min(core.column(x, z).surfaceY() - 20, 300);
                for (int y = AtlasCore.MIN_Y + 6; y <= top; y++) {
                    SetPieces.Probe p = sp.sample(x, y, z);
                    if (!p.claimed) continue;
                    pos.set(x, y, z);
                    if (p.open) {
                        // Open and dry: clear what vanilla's aquifers put there: their lava and water
                        // (which met and crusted the place in obsidian) and their barriers, thin stone
                        // walls between pockets of different water level that hung as floating sheets
                        // across the Great Geode. The piece's own water comes later (DeepWater).
                        if (!p.water && !level.getBlockState(pos).isAir()) {
                            level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
                            placed = true;
                        }
                        continue;
                    }
                    if (p.material == SetPieces.ROCK) continue;
                    BlockState state = block(p.material);
                    BlockState here = level.getBlockState(pos);
                    if (!bare(here)) continue;
                    level.setBlock(pos, state, 2);
                    placed = true;
                }
            }
        }
        return placed;
    }

    /**
     * The same clearing in a mega-cavern's open span: aquifer barriers and fluids out of the dry
     * open space above its lake (what the cavern's own carve says is open; its lakes are DeepWater's).
     */
    private static boolean clearCavern(WorldGenLevel level, AtlasCore core, int x, int z, BlockPos.MutableBlockPos pos) {
        double[] span = core.megaCaverns().span(x, z);
        if (span == null) return false;
        double lake = core.megaCaverns().water(x, z);
        boolean placed = false;
        int lo = (int) Math.floor(span[0]) + 1, hi = (int) Math.ceil(span[1]);
        for (int y = Math.max(lo, AtlasCore.MIN_Y + 6); y <= hi; y++) {
            if (!Double.isNaN(lake) && y <= lake) continue;
            if (core.megaCaverns().carve(x, y, z) >= -0.3) continue; // clearly inside, not at a wall
            pos.set(x, y, z);
            BlockState here = level.getBlockState(pos);
            if (here.isAir()) continue;
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
            placed = true;
        }
        return placed;
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
            default -> Blocks.STONE.defaultBlockState();
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
