package com.seaweed.grandatlas.world;

import com.seaweed.grandatlas.GrandAtlas;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

/**
 * A rising fall carries whatever is in it up: swimmers, mobs, items and boats ride the column into
 * the hanging lake above. (Vanilla's own push from a fluid's current is too weak to beat sinking.)
 */
@EventBusSubscriber(modid = GrandAtlas.MOD_ID)
public final class RisingWater {
    private RisingWater() {}

    @SubscribeEvent
    static void lift(EntityTickEvent.Post event) {
        Entity e = event.getEntity();
        if (!e.isInWater() || e.isSpectator()) return;
        FluidState f = e.level().getFluidState(e.blockPosition());
        if (!f.is(RiverWater.FLUID.get()) || f.getValue(RiverWater.FLOW) != RiverFlow.UP) return;
        Vec3 v = e.getDeltaMovement();
        if (e.isShiftKeyDown()) return; // sneak to stay put or sink out of it
        if (e instanceof net.minecraft.world.entity.player.Player pl && pl.getAbilities().flying) return; // creative flight goes where it likes
        e.setDeltaMovement(v.x, Math.min(0.55, v.y + 0.09), v.z);
        e.resetFallDistance();
    }
}
