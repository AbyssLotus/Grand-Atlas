package com.seaweed.grandatlas.myths.block;

import com.seaweed.grandatlas.myths.labyrinth.Labyrinth;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * The Exit Hatch: set into the Labyrinth's ceiling at the top of an exit ladder. Using it
 * (right-click) sends the player home, to where they fell in.
 *
 * <p>A custom block is a {@link Block} subclass that overrides the behaviour it changes:
 * {@code useWithoutItem} for the click, {@code animateTick} for the sparks.
 */
public class ExitHatchBlock extends Block {

    public ExitHatchBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        // The client predicts the click (arm swing); only the server moves the player.
        if (player instanceof ServerPlayer serverPlayer) Labyrinth.queueLeave(serverPlayer);
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /**
     * CLIENT only: vanilla calls this now and then for blocks near the player, for looks (torch
     * flames, portal swirls). Here: daylight sparks drifting down from the hatch, so it can be
     * picked out down a dark corridor.
     */
    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        for (int i = 0; i < 2; i++) {
            double x = pos.getX() + 0.15 + random.nextDouble() * 0.7;
            double z = pos.getZ() + 0.15 + random.nextDouble() * 0.7;
            level.addParticle(ParticleTypes.END_ROD, x, pos.getY() - 0.05, z, 0.0, -0.02 - random.nextDouble() * 0.03, 0.0);
        }
    }
}
