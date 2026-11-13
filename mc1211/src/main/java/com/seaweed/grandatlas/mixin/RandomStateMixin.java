package com.seaweed.grandatlas.mixin;

import com.seaweed.grandatlas.world.CoreHandle;
import net.minecraft.world.level.levelgen.PositionalRandomFactory;
import net.minecraft.world.level.levelgen.RandomState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Tells Grand Atlas's density functions which world they are being wired for.
 *
 * <p>A {@link RandomState} is built once per world and dimension; while it wires the noise
 * router it visits every density function. 1.21.1 gives a function no way to reach the world's
 * random source there (26.3 does), so this hands it over for the length of that wiring
 * ({@link CoreHandle#beginWiring}); the functions bind themselves to the core for that seed.
 */
@Mixin(RandomState.class)
public abstract class RandomStateMixin implements com.seaweed.grandatlas.world.AtlasRandomState {
    @Shadow @Final private PositionalRandomFactory random;

    @org.spongepowered.asm.mixin.Unique private com.seaweed.grandatlas.core.AtlasCore grandatlas$core;

    @Override
    public com.seaweed.grandatlas.core.AtlasCore grandatlas$core() {
        return grandatlas$core;
    }

    @Inject(method = "<init>", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/levelgen/NoiseRouter;mapAll(Lnet/minecraft/world/level/levelgen/DensityFunction$Visitor;)Lnet/minecraft/world/level/levelgen/NoiseRouter;"))
    private void grandatlas$beginWiring(CallbackInfo ci) {
        CoreHandle.beginWiring(random);
    }

    @Inject(method = "<init>", at = @At("TAIL"))
    private void grandatlas$endWiring(CallbackInfo ci) {
        grandatlas$core = CoreHandle.endWiring();
    }
}
