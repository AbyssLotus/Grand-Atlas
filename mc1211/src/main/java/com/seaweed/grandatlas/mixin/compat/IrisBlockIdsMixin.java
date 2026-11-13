package com.seaweed.grandatlas.mixin.compat;

import com.seaweed.grandatlas.world.RiverWater;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Shaders (Iris) tell water apart by the block ids a shader pack lists for {@code minecraft:water}
 * in its block.properties. Grand Atlas's river water isn't in any pack's list, so with shaders on it
 * was drawn as a plain translucent block: no waves, reflections or fog, out of place beside real
 * water. Whenever a pack loads, each river water state gets the id of the water state with the same
 * level, so packs shade it exactly as water. Applies only when Iris is installed.
 */
@Pseudo
@Mixin(targets = "net.irisshaders.iris.shaderpack.materialmap.WorldRenderingSettings", remap = false)
public abstract class IrisBlockIdsMixin {
    @ModifyVariable(method = "setBlockStateIds", at = @At("HEAD"), argsOnly = true, require = 0)
    private Object2IntMap<BlockState> grandatlas$riverWaterIsWater(Object2IntMap<BlockState> ids) {
        if (ids == null || ids.isEmpty()) return ids;
        Object2IntMap<BlockState> out = new Object2IntOpenHashMap<>(ids);
        out.defaultReturnValue(ids.defaultReturnValue());
        for (BlockState river : RiverWater.BLOCK.get().getStateDefinition().getPossibleStates()) {
            BlockState water = Blocks.WATER.defaultBlockState().setValue(LiquidBlock.LEVEL, river.getValue(LiquidBlock.LEVEL));
            if (ids.containsKey(water)) out.put(river, ids.getInt(water));
            else if (ids.containsKey(Blocks.WATER.defaultBlockState())) out.put(river, ids.getInt(Blocks.WATER.defaultBlockState()));
        }
        // Packs colour block light by id; ours borrow the id of a vanilla light of the same colour, so
        // the aurora crystals cast green, cyan, blue, violet and rose light (where the pack colours light).
        net.minecraft.world.level.block.Block[] like = {Blocks.VERDANT_FROGLIGHT, Blocks.SEA_LANTERN, Blocks.SOUL_LANTERN,
                Blocks.AMETHYST_CLUSTER, Blocks.PEARLESCENT_FROGLIGHT};
        for (int i = 0; i < like.length; i++) {
            BlockState ref = like[i].defaultBlockState();
            if (!ids.containsKey(ref)) continue;
            int id = ids.getInt(ref);
            for (var b : java.util.List.of(com.seaweed.grandatlas.world.ModBlocks.AURORA_CRYSTAL[i].get(),
                    com.seaweed.grandatlas.world.ModBlocks.SHATTERED_AURORA[i].get(), com.seaweed.grandatlas.world.ModBlocks.AURORA_SHARD[i].get())) {
                for (BlockState s : b.getStateDefinition().getPossibleStates()) out.putIfAbsent(s, id);
            }
        }
        if (ids.containsKey(Blocks.OCHRE_FROGLIGHT.defaultBlockState())) {
            out.putIfAbsent(com.seaweed.grandatlas.world.ModBlocks.AMBER_SAP.get().defaultBlockState(), ids.getInt(Blocks.OCHRE_FROGLIGHT.defaultBlockState()));
        }
        return out;
    }
}
