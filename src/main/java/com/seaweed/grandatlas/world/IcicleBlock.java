package com.seaweed.grandatlas.world;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

/**
 * {@code grandatlas:icicle}: hanging ice in the pointed-dripstone shape, from a ceiling or from more
 * icicle. Each piece is a {@code base} (against the ceiling), {@code middle} or {@code tip} (the
 * lowest), recomputed as the icicle grows or breaks, so an icicle can be any length.
 */
public class IcicleBlock extends Block {

    public enum Part implements StringRepresentable {
        TIP, MIDDLE, BASE;

        @Override
        public String getSerializedName() {
            return name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    public static final EnumProperty<Part> PART = EnumProperty.create("part", Part.class);
    private static final VoxelShape TIP_SHAPE = Block.column(6.0, 3.0, 16.0);
    private static final VoxelShape MIDDLE_SHAPE = Block.column(8.0, 0.0, 16.0);
    private static final VoxelShape BASE_SHAPE = Block.column(10.0, 0.0, 16.0);

    public IcicleBlock(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(PART, Part.TIP));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(PART);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return switch (state.getValue(PART)) {
            case TIP -> TIP_SHAPE;
            case MIDDLE -> MIDDLE_SHAPE;
            case BASE -> BASE_SHAPE;
        };
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        BlockState above = level.getBlockState(pos.above());
        return above.is(this) || above.isFaceSturdy(level, pos.above(), Direction.DOWN);
    }

    /** The piece this block should be, from what is above and below it. */
    public static Part partFor(boolean iceAbove, boolean iceBelow) {
        if (!iceBelow) return Part.TIP;
        return iceAbove ? Part.MIDDLE : Part.BASE;
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        LevelReader level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        BlockState state = defaultBlockState().setValue(PART, partFor(level.getBlockState(pos.above()).is(this), level.getBlockState(pos.below()).is(this)));
        return state.canSurvive(level, pos) ? state : null;
    }

    @Override
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos, Direction direction,
                                     BlockPos neighbourPos, BlockState neighbourState, RandomSource random) {
        if (direction == Direction.UP && !canSurvive(state, level, pos)) return Blocks.AIR.defaultBlockState();
        if (direction.getAxis() == Direction.Axis.Y) {
            return state.setValue(PART, partFor(level.getBlockState(pos.above()).is(this), level.getBlockState(pos.below()).is(this)));
        }
        return state;
    }
}
