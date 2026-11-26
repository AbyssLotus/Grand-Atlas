package com.seaweed.grandatlas.myths.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * One step of Ariadne's thread: a thin glowing strand lying on the floor, placed by the
 * thread item as you walk ({@code item/AriadneThreadItem}). Walk-through, breaks instantly,
 * and drops nothing, so a trail costs nothing to clear.
 */
public class AriadneThreadBlock extends Block {

    /** One pixel tall, the full block wide: what you see and what your cursor hits. */
    private static final VoxelShape SHAPE = Block.box(0, 0, 0, 16, 1, 16);

    public AriadneThreadBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    /** It only stays where there's solid floor under it. */
    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        return level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), Direction.UP);
    }

    /** Called when a neighbour changes: if the floor went away, the strand goes too. */
    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighbour,
                                     LevelAccessor level, BlockPos pos, BlockPos neighbourPos) {
        return canSurvive(state, level, pos) ? state : Blocks.AIR.defaultBlockState();
    }
}
