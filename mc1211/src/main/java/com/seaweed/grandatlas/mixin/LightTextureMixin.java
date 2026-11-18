package com.seaweed.grandatlas.mixin;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.renderer.LightTexture;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * True darkness in the deep (client/DeepDark): just before the lightmap goes to the GPU, each of its
 * 16 x 16 entries (sky light by block light) is darkened by its light level, so unlit rock is black and
 * only light opens it up: torches, lava, glowing blocks, and anything a dynamic-lights mod adds, since
 * they all work through light levels.
 */
@Mixin(LightTexture.class)
public abstract class LightTextureMixin {
    @Shadow @Final private NativeImage lightPixels;

    @Inject(method = "updateLightTexture", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/texture/DynamicTexture;upload()V"))
    private void grandatlas$deepDark(float partialTick, CallbackInfo ci) {
        float s = com.seaweed.grandatlas.client.DeepDark.strength();
        if (s <= 0) return;
        for (int sky = 0; sky < 16; sky++) {
            for (int block = 0; block < 16; block++) {
                float f = 1 - s * (1 - com.seaweed.grandatlas.client.DeepDark.factor(Math.max(sky, block)));
                int c = lightPixels.getPixelRGBA(block, sky); // ABGR
                int r = (int) ((c & 0xFF) * f), g = (int) ((c >> 8 & 0xFF) * f), b = (int) ((c >> 16 & 0xFF) * f);
                lightPixels.setPixelRGBA(block, sky, 0xFF000000 | b << 16 | g << 8 | r);
            }
        }
    }
}
