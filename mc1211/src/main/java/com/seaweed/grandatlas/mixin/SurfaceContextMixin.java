package com.seaweed.grandatlas.mixin;

import com.seaweed.grandatlas.core.AtlasCore;
import com.seaweed.grandatlas.world.CoreHandle;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.SurfaceRules;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The surface rules' "how deep does the surface go here" level, per column from the core.
 *
 * <p>1.21.1 blends the preliminary surface across each 16-block chunk section from its four
 * corners. On Grand Atlas's tall walls that overshoots, so the surface rules stop short and leave
 * bare stone where soil should be. The 26.3 build feeds the game the core's level for every
 * column; this does the same.
 */
@Mixin(SurfaceRules.Context.class)
public abstract class SurfaceContextMixin {
    @Shadow @Final RandomState randomState;
    @Shadow int blockX;
    @Shadow int blockZ;
    @Shadow int surfaceDepth;
    @Shadow long lastUpdateXZ;

    @Unique private AtlasCore grandatlas$core;
    @Unique private boolean grandatlas$looked;
    @Unique private long grandatlas$at = Long.MIN_VALUE;
    @Unique private int grandatlas$level;

    @Inject(method = "getMinSurfaceLevel", at = @At("HEAD"), cancellable = true)
    private void grandatlas$columnSurface(CallbackInfoReturnable<Integer> cir) {
        if (!grandatlas$looked) {
            grandatlas$looked = true;
            grandatlas$core = CoreHandle.coreFrom(randomState);
        }
        if (grandatlas$core == null) return;
        if (grandatlas$at != lastUpdateXZ) {
            grandatlas$at = lastUpdateXZ;
            grandatlas$level = (int) Math.floor(grandatlas$core.preliminarySurface(blockX, blockZ)) + surfaceDepth - 8;
        }
        cir.setReturnValue(grandatlas$level);
    }
}
