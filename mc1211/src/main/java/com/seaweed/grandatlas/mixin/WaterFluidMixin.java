package com.seaweed.grandatlas.mixin;

import com.seaweed.grandatlas.world.RiverWaterFluid;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.WaterFluid;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Vanilla water counts Grand Atlas's river water as the same fluid, as river water counts it. So
 * where a river's lip pours off as ordinary water the two meet without a drawn face between
 * them, a waterfall stops on the pool below it, and flowing water fed by a river keeps running.
 */
@Mixin(WaterFluid.class)
public abstract class WaterFluidMixin {
    @Inject(method = "isSame", at = @At("HEAD"), cancellable = true)
    private void grandatlas$riverWater(Fluid other, CallbackInfoReturnable<Boolean> cir) {
        if (other instanceof RiverWaterFluid) {
            cir.setReturnValue(true);
        }
    }
}
