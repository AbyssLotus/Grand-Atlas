package com.seaweed.grandatlas.myths.item;

import com.seaweed.grandatlas.myths.block.MythsBlocks;
import com.seaweed.grandatlas.myths.labyrinth.Labyrinth;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Ariadne's thread: while you hold it (main hand or off hand) in the Labyrinth, it unwinds
 * behind you as a faint glowing strand on the floor, so you can always find your way back.
 * Each block of trail uses one point of durability.
 *
 * <p>{@code inventoryTick} is vanilla's hook for "this item is in a player's inventory", called
 * every tick for every stack; it's how compasses and maps update too.
 */
public class AriadneThreadItem extends Item {

    public AriadneThreadItem(Properties properties) {
        super(properties);
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
        if (!(level instanceof ServerLevel server) || !(entity instanceof ServerPlayer player)) return;
        if (level.dimension() != Labyrinth.LABYRINTH) return;
        boolean held = selected || player.getOffhandItem() == stack;
        if (!held || !player.onGround()) return;

        BlockPos feet = player.blockPosition();
        BlockState here = level.getBlockState(feet);
        if (!here.isAir()) return; // already a strand here (or something else)
        BlockState strand = MythsBlocks.ARIADNE_THREAD.get().defaultBlockState();
        if (!strand.canSurvive(level, feet)) return;

        level.setBlock(feet, strand, 3);
        stack.hurtAndBreak(1, server, player, item -> {});
    }
}
