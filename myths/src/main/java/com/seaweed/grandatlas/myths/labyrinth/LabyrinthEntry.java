package com.seaweed.grandatlas.myths.labyrinth;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.level.BlockEvent;

/**
 * The way in (design 5.4): every stone block a player breaks has a 1 in {@link #ODDS} chance
 * of dropping them into the Labyrinth, once the world is a day old.
 *
 * <p>A chain of guard clauses, cheapest first; each returns early when it fails, so the roll
 * at the bottom only happens when everything else passed.
 */
public final class LabyrinthEntry {

    /** One Minecraft day, in ticks (20 per second, 20 minutes). */
    public static final long ONE_DAY = 24_000;
    /** 1 in 1,000 = 0.1% per stone block. */
    public static final int ODDS = 1_000;

    private LabyrinthEntry() {}

    /** GAME bus: a player is breaking a block (fired before it's gone). */
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        // Server only. (On the client the level isn't a ServerLevel.)
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        // The world's age, decided 2026-11-25. getGameTime() counts every tick the world has
        // run and is never skipped; getDayTime() is the clock face, which sleeping and
        // /time set move, so it would let anyone skip the wait.
        if (level.getGameTime() < ONE_DAY) return;
        // Already in the maze.
        if (level.dimension() == Labyrinth.LABYRINTH) return;
        // Real players only: machines that break blocks pose as FakePlayers. Creative and
        // spectator players are left alone so building isn't interrupted.
        if (!(event.getPlayer() instanceof ServerPlayer player) || player instanceof FakePlayer) return;
        if (player.isCreative() || player.isSpectator()) return;
        // Stone: stone, deepslate, granite, diorite, andesite and tuff (vanilla's tag).
        if (!event.getState().is(BlockTags.BASE_STONE_OVERWORLD)) return;
        // The roll: 0..999, one winning value.
        if (level.getRandom().nextInt(ODDS) != 0) return;

        Labyrinth.queueEnter(player);
    }
}
