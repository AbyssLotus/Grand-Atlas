package com.seaweed.grandatlas.mixin;

import com.seaweed.grandatlas.core.AtlasCore;
import com.seaweed.grandatlas.core.noise.GradientNoise;
import com.seaweed.grandatlas.world.CoreHandle;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.feature.SpringFeature;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Vanilla's springs break out of any rock face with stone round them. The hand-built rock
 * formations ({@code world/AtlasAssets}) are exactly that, standing above the ground, and the
 * deep world tries 69 springs a chunk: every outcrop leaked a sheet of water across the country
 * round it (seen 2026-11-10 on the plains at 2238 2205). Above the core's own ground only one
 * spring in eight is kept: enough for the odd fall down a pillar mountain, which looks right.
 * Springs at or under the ground, in caves and cliffs of the terrain itself, are untouched.
 */
@Mixin(SpringFeature.class)
public abstract class SpringFeatureMixin {
    @Inject(method = "place", at = @At("HEAD"), cancellable = true, require = 0)
    private void grandatlas$fewSpringsInFormations(WorldGenLevel level, ChunkGenerator generator, RandomSource random, BlockPos origin,
                                                   CallbackInfoReturnable<Boolean> cir) {
        AtlasCore core = CoreHandle.coreOf(level.getLevel());
        if (core == null) return;
        if (origin.getY() <= core.column(origin.getX(), origin.getZ()).surfaceY() + 2) return;
        if ((GradientNoise.mix(GradientNoise.hash(0x5F121L, origin.getX(), origin.getY(), origin.getZ())) & 7) != 0) cir.setReturnValue(false);
    }
}
