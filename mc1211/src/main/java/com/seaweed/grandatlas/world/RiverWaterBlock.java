package com.seaweed.grandatlas.world;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.material.FluidState;

/** The block form of {@link RiverWaterFluid}: a water block with the current's direction and its surface height on it. */
public class RiverWaterBlock extends LiquidBlock {
    private final FluidState[][] states;

    public RiverWaterBlock(RiverWaterFluid fluid, Properties properties) {
        super(fluid, properties);
        registerDefaultState(stateDefinition.any().setValue(LEVEL, 0).setValue(RiverWater.FLOW, RiverFlow.NONE).setValue(RiverWater.SURFACE, 8));
        RiverFlow[] flows = RiverFlow.values();
        states = new FluidState[flows.length][9];
        for (RiverFlow f : flows) {
            for (int h = 1; h <= 8; h++) {
                states[f.ordinal()][h] = fluid.defaultFluidState().setValue(RiverWater.FLOW, f).setValue(RiverWater.SURFACE, h);
            }
        }
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(RiverWater.FLOW, RiverWater.SURFACE);
    }

    @Override
    protected FluidState getFluidState(BlockState state) {
        return states[state.getValue(RiverWater.FLOW).ordinal()][state.getValue(RiverWater.SURFACE)];
    }
}
