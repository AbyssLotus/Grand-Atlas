package com.seaweed.grandatlas.mixin;

import it.unimi.dsi.fastutil.objects.ObjectListIterator;
import net.minecraft.world.level.levelgen.Beardifier;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.structure.pools.JigsawJunction;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Structures' terrain blending, skipped at heights it cannot reach.
 *
 * <p>The beardifier shapes the ground round villages, ancient cities and the like. It is asked
 * about every block of a chunk near a structure, and for each one walks every piece of the
 * structure, though it can only change blocks within 12 of a piece's height. In a world 1,536
 * blocks tall almost all of that work is for nothing: it was 15% of chunk generation near
 * structures (profiled 2026-11-11). Here the pieces' height range is worked out once per chunk
 * and anything outside it (with a block to spare) answers 0 at once, which is what the full walk
 * would have returned. Nothing that is generated changes.
 */
@Mixin(Beardifier.class)
public abstract class BeardifierMixin {
    @Shadow @Final protected ObjectListIterator<Beardifier.Rigid> pieceIterator;
    @Shadow @Final protected ObjectListIterator<JigsawJunction> junctionIterator;

    @Unique private boolean grandatlas$known;
    @Unique private int grandatlas$lo = Integer.MAX_VALUE, grandatlas$hi = Integer.MIN_VALUE;

    @Inject(method = "compute", at = @At("HEAD"), cancellable = true)
    private void grandatlas$outsideItsReach(DensityFunction.FunctionContext context, CallbackInfoReturnable<Double> cir) {
        if (!grandatlas$known) {
            grandatlas$known = true;
            while (pieceIterator.hasNext()) {
                Beardifier.Rigid piece = pieceIterator.next();
                int ground = piece.box().minY() + piece.groundLevelDelta();
                grandatlas$lo = Math.min(grandatlas$lo, Math.min(piece.box().minY(), ground));
                grandatlas$hi = Math.max(grandatlas$hi, Math.max(piece.box().maxY(), ground));
            }
            pieceIterator.back(Integer.MAX_VALUE);
            while (junctionIterator.hasNext()) {
                int y = junctionIterator.next().getSourceGroundY();
                grandatlas$lo = Math.min(grandatlas$lo, y);
                grandatlas$hi = Math.max(grandatlas$hi, y);
            }
            junctionIterator.back(Integer.MAX_VALUE);
            // every term reaches at most 12 blocks from a piece's height; one more to be sure
            if (grandatlas$lo <= grandatlas$hi) {
                grandatlas$lo -= 13;
                grandatlas$hi += 13;
            }
        }
        int y = context.blockY();
        if (y < grandatlas$lo || y > grandatlas$hi) cir.setReturnValue(0.0);
    }
}
