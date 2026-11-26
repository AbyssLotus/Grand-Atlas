package com.seaweed.grandatlas.myths.block;

import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;

/**
 * The workmen's mark: a Labyrinth Stone floor tile with a verdigris copper arrow inlaid in it.
 * At every junction one points along the maze toward the nearest Exit Hatch (the generator
 * works the direction out by searching the maze from the exits).
 *
 * <p>A block with a PROPERTY: {@link #FACING} is part of its block state, so one block type has
 * four states, one per direction. The blockstate JSON turns the arrow model to match.
 */
public class LabyrinthMarkerBlock extends Block {

    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

    public LabyrinthMarkerBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    /** Declares which properties this block's states have. */
    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    /** Placed by hand (creative), it points the way the player is looking. */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection());
    }
}
