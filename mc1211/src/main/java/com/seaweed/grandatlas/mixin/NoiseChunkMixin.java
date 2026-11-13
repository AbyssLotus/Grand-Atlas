package com.seaweed.grandatlas.mixin;

import com.seaweed.grandatlas.core.AtlasCore;
import com.seaweed.grandatlas.world.CoreHandle;
import net.minecraft.server.level.ColumnPos;
import net.minecraft.world.level.levelgen.NoiseChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The preliminary surface level, straight from the core.
 *
 * <p>1.21.1 finds it by scanning a density function down from the top of the world, eight
 * blocks at a time: 190 samples a column in a 1,536-block world, and an answer only good to
 * eight blocks. The aquifers and the surface rules both read it. In a Grand Atlas world the core
 * knows the level exactly ({@link AtlasCore#preliminarySurface}), which is what the 26.3 build
 * feeds the game through its own surface-level slot.
 */
@Mixin(NoiseChunk.class)
public abstract class NoiseChunkMixin implements com.seaweed.grandatlas.world.AtlasNoiseChunk {
    @Unique private AtlasCore grandatlas$core;

    /**
     * Taken from the random state the chunk is built with, at the very start of its constructor
     * (before the aquifer and anything else in there can ask for a surface level).
     */
    @Inject(method = "<init>", at = @At(value = "FIELD", opcode = org.objectweb.asm.Opcodes.PUTFIELD, shift = At.Shift.AFTER,
            target = "Lnet/minecraft/world/level/levelgen/NoiseChunk;noiseSettings:Lnet/minecraft/world/level/levelgen/NoiseSettings;"))
    private void grandatlas$learnCore(int cellCountXZ, net.minecraft.world.level.levelgen.RandomState randomState, int firstNoiseX, int firstNoiseZ,
                                      net.minecraft.world.level.levelgen.NoiseSettings noiseSettings,
                                      net.minecraft.world.level.levelgen.DensityFunctions.BeardifierOrMarker beardifier,
                                      net.minecraft.world.level.levelgen.NoiseGeneratorSettings settings,
                                      net.minecraft.world.level.levelgen.Aquifer.FluidPicker fluidPicker,
                                      net.minecraft.world.level.levelgen.blending.Blender blender,
                                      org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {
        grandatlas$core = CoreHandle.coreFrom(randomState);
    }

    @Override
    public AtlasCore grandatlas$core() {
        return grandatlas$core;
    }

    @Inject(method = "computePreliminarySurfaceLevel", at = @At("HEAD"), cancellable = true)
    private void grandatlas$exactSurface(long packedColumn, CallbackInfoReturnable<Integer> cir) {
        if (grandatlas$core() != null) {
            cir.setReturnValue((int) grandatlas$core.preliminarySurface(ColumnPos.getX(packedColumn), ColumnPos.getZ(packedColumn)));
        }
    }
}
