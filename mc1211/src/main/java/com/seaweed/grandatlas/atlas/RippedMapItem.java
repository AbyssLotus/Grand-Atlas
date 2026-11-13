package com.seaweed.grandatlas.atlas;

import com.seaweed.grandatlas.core.AtlasCore;
import com.seaweed.grandatlas.world.CoreHandle;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

/**
 * A ripped map: used, it fills in one page of the Grand Atlas the player has not found yet, picked
 * at random, and (in the overworld) marks where the nearest one lies.
 */
public final class RippedMapItem extends Item {
    public RippedMapItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide || !(player instanceof ServerPlayer sp)) return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
        List<String> missing = new ArrayList<>();
        var found = AtlasData.progress(sp).found();
        for (AtlasEntries.Entry e : AtlasEntries.all()) if (!found.contains(e.id())) missing.add(e.id());
        if (missing.isEmpty()) {
            sp.displayClientMessage(Component.literal("Your Grand Atlas is complete. The map shows nothing new.").withStyle(ChatFormatting.GRAY), true);
            return InteractionResultHolder.fail(stack);
        }
        String id = missing.get(sp.getRandom().nextInt(missing.size()));
        AtlasEntries.Entry entry = AtlasEntries.get(id);
        Component msg = Component.literal("The torn map shows ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal(entry.title()).withStyle(ChatFormatting.GOLD));
        AtlasCore core = level instanceof ServerLevel sl ? CoreHandle.coreOf(sl) : null;
        if (core != null) {
            int[] at;
            try {
                at = AtlasEntries.hint(core, id, sp.getBlockX(), sp.getBlockZ());
            } catch (RuntimeException ex) {
                at = null;
            }
            if (at != null) {
                AtlasData.notePlace(sp, id, at[0], core.column(at[0], at[1]).surfaceY(), at[1]);
                msg = msg.copy().append(Component.literal(", marked near " + at[0] + ", " + at[1]).withStyle(ChatFormatting.GRAY));
            }
        }
        AtlasData.discover(sp, id, false); // after the mark is noted, so the sync carries it
        sp.sendSystemMessage(msg);
        level.playSound(null, sp.blockPosition(), SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 1.0F, 0.8F);
        level.playSound(null, sp.blockPosition(), SoundEvents.VILLAGER_WORK_CARTOGRAPHER, SoundSource.PLAYERS, 0.8F, 1.0F);
        if (!sp.getAbilities().instabuild) stack.shrink(1);
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.literal("A scrap of an old map.").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.literal("Use it to fill in a page of the Grand Atlas.").withStyle(ChatFormatting.DARK_GRAY));
    }
}
