package com.seaweed.grandatlas.mixin;

import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Aquifer;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.NoiseChunk;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * No aquifer search in the open sky.
 *
 * <p>For every block that isn't rock, 1.21.1's aquifer looks through twelve neighbouring aquifer
 * cells to decide what fills it, even a thousand blocks above the ground where the answer can
 * only be air. In a world that reaches y 1024 that was the largest single cost of chunk
 * generation (12%, profiled 2026-11-11). 26.3 skips it above a level worked out from the highest
 * ground under the chunk ({@code skipSamplingAboveY}); this is that rule, with the same numbers,
 * for Grand Atlas worlds, where the ground level is known exactly.
 */
@Mixin(Aquifer.NoiseBasedAquifer.class)
public abstract class AquiferMixin {
    @Shadow @Final private NoiseChunk noiseChunk;
    @Shadow @Final private Aquifer.FluidPicker globalFluidPicker;
    @Shadow @Final private int minGridX;
    @Shadow @Final private int minGridZ;
    @Shadow @Final private int gridSizeX;
    @Shadow @Final private int gridSizeZ;
    @Shadow private boolean shouldScheduleFluidUpdate;

    @Unique private boolean grandatlas$known;
    @Unique private int grandatlas$skipAbove = Integer.MAX_VALUE;
    /** The height band of the mega-caverns within reach of this chunk, where aquifers place no fluid (empty: lo > hi). */
    @Unique private int grandatlas$dryLo = 1, grandatlas$dryHi = 0;

    @Inject(method = "computeSubstance", at = @At("HEAD"), cancellable = true)
    private void grandatlas$openSky(DensityFunction.FunctionContext context, double density, CallbackInfoReturnable<BlockState> cir) {
        if (density > 0.0) return;
        if (!grandatlas$known) {
            grandatlas$known = true;
            if (((com.seaweed.grandatlas.world.AtlasNoiseChunk) noiseChunk).grandatlas$core() != null) {
                // the highest ground under the aquifer cells this chunk can read (cells are 16 wide, their points up to 9 in)
                int highest = Integer.MIN_VALUE;
                int x1 = (minGridX + gridSizeX - 1) * 16 + 9, z1 = (minGridZ + gridSizeZ - 1) * 16 + 9;
                for (int z = minGridZ * 16; z <= z1; z += 4) {
                    for (int x = minGridX * 16; x <= x1; x += 4) {
                        highest = Math.max(highest, noiseChunk.preliminarySurfaceLevel(x, z));
                    }
                }
                // 26.3: adjusted level (+8), 12 more, up to the top of the next 12-block cell
                grandatlas$skipAbove = (Math.floorDiv(highest + 8 + 12, 12) + 1) * 12 + 10;
                // Mega-caverns: no aquifer water or lava anywhere in their height band near them. The
                // dressing pass (world/SetPieceDressing) clears the cavern's own open space, but the
                // vanilla caves round it kept their aquifer fluid, which poured into the cavern as
                // falls and floods (the user, 2026-11-12: "multiple lava and water leakages").
                // Caverns hold only the lakes DeepWater gives them.
                com.seaweed.grandatlas.core.AtlasCore core = ((com.seaweed.grandatlas.world.AtlasNoiseChunk) noiseChunk).grandatlas$core();
                double lo = Double.MAX_VALUE, hi = -Double.MAX_VALUE;
                for (int z = minGridZ * 16 - 32; z <= z1 + 32; z += 8) {
                    for (int x = minGridX * 16 - 32; x <= x1 + 32; x += 8) {
                        double[] span = core.megaCaverns().span(x, z);
                        if (span == null) continue;
                        lo = Math.min(lo, span[0]);
                        hi = Math.max(hi, span[1]);
                    }
                }
                if (lo <= hi) {
                    grandatlas$dryLo = (int) Math.floor(lo) - 24;
                    grandatlas$dryHi = (int) Math.ceil(hi) + 24;
                }
            }
        }
        int y = context.blockY();
        if (y >= grandatlas$dryLo && y <= grandatlas$dryHi) {
            shouldScheduleFluidUpdate = false;
            cir.setReturnValue(net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
            return;
        }
        if (y > grandatlas$skipAbove) {
            shouldScheduleFluidUpdate = false;
            cir.setReturnValue(globalFluidPicker.computeFluid(context.blockX(), y, context.blockZ()).at(y));
        }
    }
}
