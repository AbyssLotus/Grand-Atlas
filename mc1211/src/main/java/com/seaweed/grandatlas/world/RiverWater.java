package com.seaweed.grandatlas.world;

import com.seaweed.grandatlas.GrandAtlas;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * {@code grandatlas:river_water}, the water of Grand Atlas's rivers and lakes ({@link RiverWaterFluid},
 * {@link RiverWaterBlock}), and {@link #current}: the way to ask how water moves at a spot.
 */
public final class RiverWater {
    private RiverWater() {}

    /** Downstream direction of the water in a river block. */
    public static final EnumProperty<RiverFlow> FLOW = EnumProperty.create("flow", RiverFlow.class);

    /**
     * How high the water's surface is drawn in its block, in ninths: 8 is a full block of still water
     * (a source's height), 1 a skim over the bed. A river's surface slopes down its length through
     * these, 8, 7 ... 1 and then full again on the block below, instead of stepping a block at a time.
     * The water still never spreads, whatever its height.
     */
    public static final IntegerProperty SURFACE = IntegerProperty.create("surface", 1, 8);

    public static final DeferredRegister<Fluid> FLUIDS = DeferredRegister.create(Registries.FLUID, GrandAtlas.MOD_ID);
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(GrandAtlas.MOD_ID);

    public static final DeferredHolder<Fluid, RiverWaterFluid> FLUID = FLUIDS.register("river_water", RiverWaterFluid::new);
    public static final DeferredBlock<RiverWaterBlock> BLOCK = BLOCKS.registerBlock("river_water",
            p -> new RiverWaterBlock(FLUID.get(), p),
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.WATER)
                    .replaceable()
                    .noCollission()
                    .strength(100.0F)
                    .pushReaction(PushReaction.DESTROY)
                    .noLootTable()
                    .liquid()
                    .sound(SoundType.EMPTY));

    /**
     * The current at a block: a unit vector downstream in river water, vanilla's own flow in other
     * water, zero elsewhere. For physics mods (Sable-style ships and contraptions) to drift with
     * the rivers; entities already are.
     */
    public static Vec3 current(BlockGetter level, BlockPos pos) {
        FluidState fluid = level.getFluidState(pos);
        return fluid.isEmpty() ? Vec3.ZERO : fluid.getFlow(level, pos);
    }

    /** River water running {@code flow}, its surface drawn {@code surface} ninths up its block (1-8). */
    public static BlockState state(RiverFlow flow, int surface) {
        return BLOCK.get().defaultBlockState().setValue(FLOW, flow).setValue(SURFACE, Math.max(1, Math.min(8, surface)));
    }

    /**
     * The top water block and its surface (in ninths, 1-8) that draw a water surface at height
     * {@code level}: {y, surface}. The top block is always floor(level), as everything else that reads
     * the water expects; every block under it is full.
     */
    public static int[] surfaceAt(double level) {
        int y = (int) Math.floor(level);
        return new int[] {y, Math.max(1, Math.min(8, (int) Math.round((level - y) * 9)))};
    }
}
