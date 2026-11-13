package com.seaweed.grandatlas.atlas;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

/** The Grand Atlas: opens the book (client/AtlasScreen) on use. */
public final class GrandAtlasItem extends Item {
    public GrandAtlasItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        if (level.isClientSide) {
            com.seaweed.grandatlas.client.ClientAtlas.open();
        } else {
            level.playSound(null, player.blockPosition(), SoundEvents.BOOK_PAGE_TURN, net.minecraft.sounds.SoundSource.PLAYERS, 1.0F, 1.0F);
        }
        return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.literal("A record of the wonders of the world.").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.literal("Fills in as you explore, or from ripped maps.").withStyle(ChatFormatting.DARK_GRAY));
    }
}
