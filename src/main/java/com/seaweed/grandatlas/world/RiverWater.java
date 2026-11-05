package com.seaweed.grandatlas.world;

import com.seaweed.grandatlas.GrandAtlas;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.EnumProperty;
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

    public static final DeferredRegister<Fluid> FLUIDS = DeferredRegister.create(Registries.FLUID, GrandAtlas.MOD_ID);
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(GrandAtlas.MOD_ID);

    public static final DeferredHolder<Fluid, RiverWaterFluid> FLUID = FLUIDS.register("river_water", RiverWaterFluid::new);
    public static final DeferredBlock<RiverWaterBlock> BLOCK = BLOCKS.registerBlock("river_water",
            p -> new RiverWaterBlock(FLUID.get(), p),
            () -> BlockBehaviour.Properties.of()
                    .mapColor(MapColor.WATER)
                    .replaceable()
                    .noCollision()
                    .strength(100.0F)
                    .pushReaction(PushReaction.POPPED)
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
}
