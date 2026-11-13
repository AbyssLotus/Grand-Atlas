package com.seaweed.grandatlas.world;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.state.BlockState;

/**
 * {@code grandatlas:aurora_veil_<colour>}: glowing curtains of light hanging in ribbons from the
 * underside of an Aurora Vault's lake, bright at the top and fading to nothing at the tip. Hangs from
 * a ceiling, from more veil, or from (held) water above.
 */
public class AuroraVeilBlock extends GlowwormSilkBlock {
    public AuroraVeilBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        return super.canSurvive(state, level, pos) || level.getFluidState(pos.above()).is(FluidTags.WATER);
    }
}
