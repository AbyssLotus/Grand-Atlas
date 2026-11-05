package com.seaweed.grandatlas.mixin;

import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Aquifer;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Vanilla hard-codes the global lava level at y &lt; -54, which suits a world that ends at
 * -64. In a world that goes down to -128, that would turn every open cave below -54 into
 * a lava lake. For worlds deeper than vanilla, the lava level follows the floor
 * instead (10 blocks up, the same margin vanilla uses). Vanilla-depth worlds are untouched.
 */
@Mixin(NoiseBasedChunkGenerator.class)
public abstract class NoiseBasedChunkGeneratorMixin {
    @Inject(method = "createFluidPicker", at = @At("HEAD"), cancellable = true)
    private static void grandatlas$deepLava(NoiseGeneratorSettings settings, CallbackInfoReturnable<Aquifer.FluidPicker> cir) {
        int minY = settings.noiseSettings().minY();
        if (minY >= -64) {
            return;
        }
        int lavaLevel = minY + 10;
        int seaLevel = settings.seaLevel();
        Aquifer.FluidStatus lava = new Aquifer.FluidStatus(lavaLevel, Blocks.LAVA.defaultBlockState());
        Aquifer.FluidStatus water = new Aquifer.FluidStatus(seaLevel, settings.defaultFluid());
        int cutoff = Math.min(lavaLevel, seaLevel);
        cir.setReturnValue((x, y, z) -> y < cutoff ? lava : water);
    }
}
