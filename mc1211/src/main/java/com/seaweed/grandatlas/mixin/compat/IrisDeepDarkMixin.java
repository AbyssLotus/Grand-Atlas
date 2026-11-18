package com.seaweed.grandatlas.mixin.compat;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The deep's darkness under shader packs (client/DeepDark). Packs do their own lighting and never see
 * the lightmap, so the darkness vanished with shaders on (the user, 2026-11-18). Packs like Lumina
 * scale the light in places without skylight by the Brightness setting, which Iris hands them as the
 * {@code screenBrightness} uniform; in the deep it's turned down to nothing, so unlit rock goes dark
 * and only block light (torches, lava, glowing blocks, dynamic lights) shows it. The saved setting is
 * untouched, and so is the pack. Iris's uniform is read in a lambda, matched by name: if Iris changes
 * it, this simply doesn't apply.
 */
@Pseudo
@Mixin(targets = "net.irisshaders.iris.uniforms.CommonUniforms", remap = false)
public abstract class IrisDeepDarkMixin {
    @Inject(method = "lambda$generalCommonUniforms$7", at = @At("RETURN"), cancellable = true, require = 0)
    private static void grandatlas$deepBrightness(CallbackInfoReturnable<Double> cir) {
        float s = com.seaweed.grandatlas.client.DeepDark.strength();
        if (s > 0) cir.setReturnValue(cir.getReturnValue() * (1 - s));
    }
}
