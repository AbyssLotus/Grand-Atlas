package com.seaweed.grandatlas.world;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.material.WaterFluid;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForgeMod;
import net.neoforged.neoforge.fluids.FluidType;

/**
 * River water: water in every way that matters (NeoForge's water type, the {@code #minecraft:water}
 * tag, a water bucket when scooped up, and vanilla water counts it as its own), except that it
 * stands still where generation put it instead of spreading, and it carries a current
 * ({@link RiverWater#FLOW}) that pushes swimmers, mobs, items and boats downstream.
 *
 * <p>Vanilla water above sea level runs out of any gap, so rivers and lakes on high ground can't be
 * made of it (the old perched water leaked off every step). This doesn't: it is always a source
 * and never spreads. Opened up (a bank dug away, a bucket taken out next to it), it turns into
 * ordinary water there and flows out like any water would.
 */
public class RiverWaterFluid extends WaterFluid {
    @Override
    protected void createFluidStateDefinition(StateDefinition.Builder<Fluid, FluidState> builder) {
        super.createFluidStateDefinition(builder);
        builder.add(RiverWater.FLOW);
    }

    @Override
    public Fluid getSource() {
        return this;
    }

    @Override
    public int getAmount(FluidState fluidState) {
        return 8;
    }

    @Override
    public boolean isSource(FluidState fluidState) {
        return true;
    }

    @Override
    public boolean isSame(Fluid other) {
        return other == this || other == Fluids.WATER || other == Fluids.FLOWING_WATER;
    }

    @Override
    public FluidType getFluidType() {
        return NeoForgeMod.WATER_TYPE.value();
    }

    /** Never spreads. Ticked by a neighbour change: if that opened it to the air, it becomes ordinary water. */
    @Override
    public void tick(ServerLevel level, BlockPos pos, BlockState blockState, FluidState fluidState) {
        for (Direction d : LiquidBlock.POSSIBLE_FLOW_DIRECTIONS) {
            BlockState next = level.getBlockState(pos.relative(d));
            if (next.getFluidState().isEmpty() && next.canBeReplaced(Fluids.FLOWING_WATER)) {
                level.setBlockAndUpdate(pos, Blocks.WATER.defaultBlockState());
                return;
            }
        }
    }

    @Override
    public boolean canBeReplacedWith(FluidState state, BlockGetter level, BlockPos pos, Fluid other, Direction direction) {
        return false; // a waterfall stops on the pool below instead of washing it away
    }

    @Override
    public Vec3 getFlow(BlockGetter level, BlockPos pos, FluidState fluidState) {
        return fluidState.getValue(RiverWater.FLOW).vector();
    }

    @Override
    public void animateTick(Level level, BlockPos pos, FluidState fluidState, RandomSource random) {
        super.animateTick(level, pos, fluidState, random);
        // A running river is heard at its surface; a still pool or lake isn't.
        if (fluidState.getValue(RiverWater.FLOW) != RiverFlow.NONE && random.nextInt(48) == 0 && level.isEmptyBlock(pos.above())) {
            level.playLocalSound(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, SoundEvents.WATER_AMBIENT, SoundSource.AMBIENT,
                    random.nextFloat() * 0.25F + 0.6F, random.nextFloat() + 0.5F, false);
        }
    }

    @Override
    public BlockState createLegacyBlock(FluidState fluidState) {
        return RiverWater.BLOCK.get().defaultBlockState().setValue(RiverWater.FLOW, fluidState.getValue(RiverWater.FLOW));
    }
}
