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
        builder.add(RiverWater.FLOW, RiverWater.SURFACE);
    }

    @Override
    public Fluid getSource() {
        return this;
    }

    @Override
    public int getAmount(FluidState fluidState) {
        return fluidState.getValue(RiverWater.SURFACE); // the drawn height: the renderer slopes the surface between blocks
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
        if (fluidState.getValue(RiverWater.FLOW).held()) return; // a hanging lake, a rising fall
        for (Direction d : LiquidBlock.POSSIBLE_FLOW_DIRECTIONS) {
            BlockState next = level.getBlockState(pos.relative(d));
            // Air over lower water of its own (the next block down a sloping river, or the pool below
            // a lip) isn't an opening: the surface just runs on down. Air over anything else is.
            // Nor is air on a bank level with this block's floor, beside a thin skim: a sloping river's
            // shallow edge (com.seaweed.grandatlas.core.AtlasCore.SKIM).
            BlockState under = d == Direction.DOWN ? null : level.getBlockState(pos.relative(d).below());
            boolean skimEdge = under != null && fluidState.getValue(RiverWater.SURFACE) <= com.seaweed.grandatlas.core.AtlasCore.SKIM
                    && under.isSolid() && under.getFluidState().isEmpty();
            if (next.getFluidState().isEmpty() && next.canBeReplaced(Fluids.FLOWING_WATER) && !skimEdge
                    && !(d != Direction.DOWN && isSame(level.getFluidState(pos.relative(d).below()).getType()))) {
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
        if (fluidState.getValue(RiverWater.FLOW) == RiverFlow.UP) {
            // A rising fall: streams of bubbles climbing, and its rush heard close by.
            if (random.nextInt(3) == 0) {
                level.addParticle(net.minecraft.core.particles.ParticleTypes.BUBBLE_COLUMN_UP, pos.getX() + random.nextDouble(),
                        pos.getY() + random.nextDouble(), pos.getZ() + random.nextDouble(), 0.0, 0.04, 0.0);
            }
            if (random.nextInt(200) == 0) {
                level.playLocalSound(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, SoundEvents.BUBBLE_COLUMN_UPWARDS_AMBIENT,
                        SoundSource.AMBIENT, 0.6F + random.nextFloat() * 0.3F, 0.8F + random.nextFloat() * 0.3F, false);
            }
            return;
        }
        if (fluidState.getValue(RiverWater.FLOW) == RiverFlow.DOWN) {
            // A waterfall: spray off its foot, and its rush heard close by.
            if (random.nextInt(4) == 0 && !level.getBlockState(pos.below()).is(RiverWater.BLOCK.get())) {
                level.addParticle(net.minecraft.core.particles.ParticleTypes.SPLASH, pos.getX() + random.nextDouble(), pos.getY() + 0.1,
                        pos.getZ() + random.nextDouble(), 0.0, 0.1, 0.0);
            }
            if (random.nextInt(40) == 0) {
                level.playLocalSound(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, SoundEvents.WATER_AMBIENT, SoundSource.AMBIENT,
                        0.5F + random.nextFloat() * 0.3F, 0.8F + random.nextFloat() * 0.3F, false);
            }
            return;
        }
        // A running river is heard at its surface; a still pool or lake isn't.
        if (fluidState.getValue(RiverWater.FLOW) != RiverFlow.NONE && random.nextInt(48) == 0 && level.isEmptyBlock(pos.above())) {
            level.playLocalSound(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, SoundEvents.WATER_AMBIENT, SoundSource.AMBIENT,
                    random.nextFloat() * 0.25F + 0.6F, random.nextFloat() + 0.5F, false);
        }
    }

    @Override
    public BlockState createLegacyBlock(FluidState fluidState) {
        return RiverWater.BLOCK.get().defaultBlockState().setValue(RiverWater.FLOW, fluidState.getValue(RiverWater.FLOW))
                .setValue(RiverWater.SURFACE, fluidState.getValue(RiverWater.SURFACE));
    }
}
