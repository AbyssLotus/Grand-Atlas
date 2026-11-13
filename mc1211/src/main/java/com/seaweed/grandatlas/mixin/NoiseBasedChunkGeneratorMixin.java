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

    @org.spongepowered.asm.mixin.Shadow @org.spongepowered.asm.mixin.Final
    private net.minecraft.core.Holder<NoiseGeneratorSettings> settings;

    /**
     * "How high is the ground here?", answered by the core.
     *
     * <p>Structures ask this for every place they might stand, and {@code /locate} for every
     * candidate in its search. The game answers by generating that one column of terrain from
     * the top of the world down: all 1,536 levels of it here, through every cave function. With
     * Integrated API's wide structure searches one locate ran past the server's 60-second
     * watchdog (seen 2026-11-11 with the Create Atlas pack). The core already knows the ground
     * and the water over it for any column, so in a Grand Atlas world it answers directly.
     */
    @Inject(method = "getBaseHeight", at = @At("HEAD"), cancellable = true)
    private void grandatlas$groundFromCore(int x, int z, net.minecraft.world.level.levelgen.Heightmap.Types type,
                                           net.minecraft.world.level.LevelHeightAccessor level,
                                           net.minecraft.world.level.levelgen.RandomState randomState, CallbackInfoReturnable<Integer> cir) {
        com.seaweed.grandatlas.core.AtlasCore core = com.seaweed.grandatlas.world.CoreHandle.coreFrom(randomState);
        if (core == null) return;
        com.seaweed.grandatlas.core.ColumnSample c = core.column(x, z);
        int free = c.surfaceY() + 1;
        // the two "surface" kinds count water as surface; the "ocean floor" kinds look through it
        boolean throughWater = type == net.minecraft.world.level.levelgen.Heightmap.Types.OCEAN_FLOOR_WG
                || type == net.minecraft.world.level.levelgen.Heightmap.Types.OCEAN_FLOOR;
        if (!throughWater) {
            // a river or lake over the column (its top water block is floor(water)), or the sea
            if (!Double.isNaN(c.water()) && c.water() > c.height()) free = Math.max(free, (int) Math.floor(c.water()) + 1);
            if (c.height() < com.seaweed.grandatlas.core.AtlasCore.SEA_LEVEL) free = Math.max(free, com.seaweed.grandatlas.core.AtlasCore.SEA_LEVEL);
        }
        cir.setReturnValue(Math.max(level.getMinBuildHeight(), Math.min(level.getMaxBuildHeight(), free)));
    }

    /**
     * Rules another mod wraps round the overworld's surface rule (Vanilla Backport's) are gated so
     * they don't ask the biome of every block: {@link com.seaweed.grandatlas.world.ForeignRules}.
     */
    @org.spongepowered.asm.mixin.injection.ModifyArg(
            method = "buildSurface(Lnet/minecraft/world/level/chunk/ChunkAccess;Lnet/minecraft/world/level/levelgen/WorldGenerationContext;Lnet/minecraft/world/level/levelgen/RandomState;Lnet/minecraft/world/level/StructureManager;Lnet/minecraft/world/level/biome/BiomeManager;Lnet/minecraft/core/Registry;Lnet/minecraft/world/level/levelgen/blending/Blender;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/levelgen/SurfaceSystem;buildSurface(Lnet/minecraft/world/level/levelgen/RandomState;Lnet/minecraft/world/level/biome/BiomeManager;Lnet/minecraft/core/Registry;ZLnet/minecraft/world/level/levelgen/WorldGenerationContext;Lnet/minecraft/world/level/chunk/ChunkAccess;Lnet/minecraft/world/level/levelgen/NoiseChunk;Lnet/minecraft/world/level/levelgen/SurfaceRules$RuleSource;)V"),
            index = 7)
    private net.minecraft.world.level.levelgen.SurfaceRules.RuleSource grandatlas$gateForeignRules(net.minecraft.world.level.levelgen.SurfaceRules.RuleSource rule) {
        return com.seaweed.grandatlas.world.ForeignRules.gate(rule, settings.value().surfaceRule());
    }
}
