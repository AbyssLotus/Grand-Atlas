package com.seaweed.grandatlas.world;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.material.FluidState;

/** The block form of {@link RiverWaterFluid}: a water block with the current's direction on it. */
public class RiverWaterBlock extends LiquidBlock {
    private final FluidState[] states;

    public RiverWaterBlock(RiverWaterFluid fluid, Properties properties) {
        super(fluid, properties);
        registerDefaultState(stateDefinition.any().setValue(LEVEL, 0).setValue(RiverWater.FLOW, RiverFlow.NONE));
        RiverFlow[] flows = RiverFlow.values();
        states = new FluidState[flows.length];
        for (RiverFlow f : flows) states[f.ordinal()] = fluid.defaultFluidState().setValue(RiverWater.FLOW, f);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(RiverWater.FLOW);
    }

    @Override
    protected FluidState getFluidState(BlockState state) {
        return states[state.getValue(RiverWater.FLOW).ordinal()];
    }
}
