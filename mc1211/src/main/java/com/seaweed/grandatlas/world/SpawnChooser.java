package com.seaweed.grandatlas.world;

import com.seaweed.grandatlas.GrandAtlas;
import com.seaweed.grandatlas.core.AtlasCore;
import com.seaweed.grandatlas.core.BiomeKind;
import com.seaweed.grandatlas.core.ColumnSample;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.LevelData;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.LevelEvent;

/**
 * Where a new world begins. The game's own choice only asks for land with a middling climate, and
 * on the dev seed that was a bare beach at the water's edge (2026-11-10). A first view should be
 * chosen (the user: a more "bespoke" vibe): gentle green country beside a river or lake, with
 * high ground in sight, as near the middle of the world as such a place can be found.
 *
 * <p>Runs once, when the world is created; worlds that aren't Grand Atlas's are left to the game.
 */
@EventBusSubscriber(modid = GrandAtlas.MOD_ID)
public final class SpawnChooser {
    private SpawnChooser() {}

    @SubscribeEvent
    static void choose(LevelEvent.CreateSpawnPosition event) {
        if (!(event.getLevel() instanceof ServerLevel level) || level.dimension() != Level.OVERWORLD) return;
        AtlasCore core = CoreHandle.coreOf(level);
        if (core == null) return;
        try {
            BlockPos spot = find(core);
            if (spot == null) return; // nothing suitable within reach: the game's choice stands
            event.getSettings().setSpawn(spot, 0f);
            event.setCanceled(true);
            GrandAtlas.LOG.info("Grand Atlas chose the world spawn: {} {} {}", spot.getX(), spot.getY(), spot.getZ());
        } catch (RuntimeException e) {
            GrandAtlas.LOG.warn("Grand Atlas could not choose a spawn; the game's own choice stands", e);
        }
    }

    private static boolean pleasant(BiomeKind b) {
        return switch (b) {
            case FOREST, BIRCH_FOREST, FLOWER_FOREST, PLAINS, SUNFLOWER_PLAINS, MEADOW, CHERRY_GROVE, TAIGA, OLD_GROWTH_BIRCH_FOREST -> true;
            default -> false;
        };
    }

    private static boolean water(ColumnSample c) {
        return !Double.isNaN(c.water()) && !c.biome().name().endsWith("OCEAN") || c.biome() == BiomeKind.RIVER;
    }

    /** The best spot within about 5 km of the origin, searched on a 48-block grid, or null. */
    private static BlockPos find(AtlasCore core) {
        BlockPos best = null;
        double bestScore = -1e9;
        for (int x = -5040; x <= 5040; x += 48) {
            for (int z = -5040; z <= 5040; z += 48) {
                ColumnSample c = core.column(x, z);
                if (!Double.isNaN(c.water()) || c.height() < AtlasCore.SEA_LEVEL + 4 || c.height() > 150 || !pleasant(c.biome())) continue;
                if (c.site() != null || c.rock() > 0 || c.wall() != null) continue;
                double slope = Math.max(Math.abs(core.column(x + 3, z).height() - core.column(x - 3, z).height()),
                        Math.abs(core.column(x, z + 3).height() - core.column(x, z - 3).height())) / 6;
                if (slope > 0.45) continue;
                // a river or lake close by, but not underfoot
                double nearest = Double.MAX_VALUE;
                for (int r = 16; r <= 64 && nearest == Double.MAX_VALUE; r += 16) {
                    for (int k = 0; k < 12; k++) {
                        double a = k * Math.PI / 6;
                        if (water(core.column(x + (int) (Math.cos(a) * r), z + (int) (Math.sin(a) * r)))) {
                            nearest = r;
                            break;
                        }
                    }
                }
                if (nearest == Double.MAX_VALUE) continue;
                // something to look at: how much the ground rises within a few hundred blocks
                double high = c.height();
                for (int k = 0; k < 8; k++) {
                    double a = k * Math.PI / 4;
                    high = Math.max(high, core.column(x + (int) (Math.cos(a) * 320), z + (int) (Math.sin(a) * 320)).height());
                }
                double view = Math.min(3, (high - c.height()) / 70);
                double fancy = c.biome() == BiomeKind.CHERRY_GROVE || c.biome() == BiomeKind.FLOWER_FOREST || c.biome() == BiomeKind.MEADOW ? 0.8 : 0;
                double score = view + fancy - Math.hypot(x, z) / 1400 - slope;
                if (score > bestScore) {
                    bestScore = score;
                    best = new BlockPos(x, c.surfaceY() + 1, z);
                }
            }
        }
        return best;
    }
}
