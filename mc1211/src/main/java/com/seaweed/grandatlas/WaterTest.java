package com.seaweed.grandatlas;

import com.seaweed.grandatlas.world.RiverFlow;
import com.seaweed.grandatlas.world.RiverWater;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * {@code /grandatlas watertest}: builds test channels in front of you, to judge sloped river water
 * (docs/WATER_REWORK.md, step 1). Three troughs side by side, 64 blocks long, running away from
 * you, the water 5 wide:
 * <ul>
 *   <li><b>left</b>: dropping 4 blocks, sloped (1 in 16, a gentle river);</li>
 *   <li><b>middle</b>: the same drop as flat pools stepping a block at a time (rivers today);</li>
 *   <li><b>right</b>: dropping 8 blocks, sloped (1 in 8, a steep stream).</li>
 * </ul>
 * Placed with block updates on, so a channel that leaks shows it.
 */
final class WaterTest {
    private WaterTest() {}

    private static final int LENGTH = 64, HALF = 2, SPACING = 8;

    /**
     * A waterfall test: a channel 16 long ending at a cliff, a 6-block drop to a ledge 3 wide, an 8-block
     * drop into a pool 8 long, poured with the worldgen's cascade ({@code PerchedWater.pour}): a spout of
     * ordinary water at the lip and a groove cut across the ledge.
     */
    private static void fall(ServerLevel level, BlockPos origin, Direction ahead, Direction right) {
        BlockState stone = Blocks.STONE_BRICKS.defaultBlockState(), air = Blocks.AIR.defaultBlockState();
        RiverFlow flow = RiverFlow.of(ahead.getStepX(), ahead.getStepZ());
        int top = origin.getY() + 6, ledge = top - 6, pool = ledge - 8; // water surfaces
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int i = -1; i <= 28; i++) {
            for (int l = -3; l <= 3; l++) {
                // Ground: the channel's bed under its water, the ledge, then the pool's bed.
                int ground = i < 16 ? top - 2 : i < 19 ? ledge : pool - 3;
                boolean inside = Math.abs(l) <= 1 && i >= 0 && i <= 27;
                int bank = i < 16 ? top + 1 : i < 19 ? ledge + 1 : pool + 1;
                for (int y = pool - 5; y <= top + 6; y++) {
                    BlockState s;
                    if (y <= ground) s = stone;
                    else if (!inside) s = y <= bank ? stone : air;
                    else if (i < 16 && y <= top) s = RiverWater.state(flow, y == top ? 8 : 8);
                    else if (i >= 19 && y <= pool) s = RiverWater.state(RiverFlow.NONE, 8);
                    else s = air;
                    level.setBlock(pos.set(origin.getX() + ahead.getStepX() * i + right.getStepX() * l, y,
                            origin.getZ() + ahead.getStepZ() * i + right.getStepZ() * l), s, net.minecraft.world.level.block.Block.UPDATE_ALL);
                }
            }
        }
        for (int l = -1; l <= 1; l++) {
            com.seaweed.grandatlas.world.PerchedWater.pour(level, origin.getX() + ahead.getStepX() * 16 + right.getStepX() * l, top,
                    origin.getZ() + ahead.getStepZ() * 16 + right.getStepZ() * l, ahead.getStepX(), ahead.getStepZ());
        }
    }

    static int build(CommandSourceStack source) {
        ServerLevel level = source.getLevel();
        BlockPos feet = BlockPos.containing(source.getPosition());
        Direction ahead = Direction.fromYRot(source.getRotation().y);
        Direction right = ahead.getClockWise();
        RiverFlow flow = RiverFlow.of(ahead.getStepX(), ahead.getStepZ());
        double start = feet.getY() + 1.5;
        // {lateral centre, drop, stepped}
        double[][] channels = {{-SPACING, 4, 0}, {0, 4, 1}, {SPACING, 8, 0}};
        int lowest = feet.getY() - 12, clear = feet.getY() + 10;
        BlockState stone = Blocks.STONE_BRICKS.defaultBlockState(), bedRock = Blocks.SMOOTH_STONE.defaultBlockState(), air = Blocks.AIR.defaultBlockState();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int i = -1; i <= LENGTH; i++) {
            for (int l = -SPACING - HALF - 2; l <= SPACING + HALF + 2; l++) {
                double[] ch = channels[l < -SPACING / 2 ? 0 : l > SPACING / 2 ? 2 : 1];
                int across = Math.abs(l - (int) ch[0]);
                int at = Math.max(0, Math.min(LENGTH - 1, i));
                double surface = ch[2] > 0
                        ? start - Math.floor((at + 0.5) * ch[1] / LENGTH) // a flat pool per block of drop
                        : start - ch[1] * (at + 0.5) / LENGTH;
                int[] top = RiverWater.surfaceAt(surface);
                int y = top[0], ninths = ch[2] > 0 ? 8 : top[1], bed = y - 2;
                boolean water = across <= HALF && i >= 0 && i < LENGTH;
                int bank = y + 1;
                int x = feet.getX() + ahead.getStepX() * (4 + i) + right.getStepX() * l;
                int z = feet.getZ() + ahead.getStepZ() * (4 + i) + right.getStepZ() * l;
                for (int yy = lowest; yy <= clear; yy++) {
                    BlockState s;
                    if (yy <= bed) s = water ? bedRock : stone;
                    else if (!water) s = yy <= bank ? stone : air;
                    else if (yy < y) s = RiverWater.state(flow, 8);
                    else if (yy == y) s = RiverWater.state(flow, ninths);
                    else s = air;
                    level.setBlock(pos.set(x, yy, z), s, Block.UPDATE_ALL);
                }
            }
        }
        fall(level, feet.offset(ahead.getStepX() * 4, 0, ahead.getStepZ() * 4).relative(right, SPACING * 2 + 4), ahead, right);
        source.sendSuccess(() -> Component.literal("Built test channels ahead of you, running " + ahead.getName()
                + ": left sloped 1 in 16, middle the same drop in flat steps, right sloped 1 in 8, and to the right a waterfall"
                + " over a cliff with a ledge part way down into a pool."), true);
        return 1;
    }
}
