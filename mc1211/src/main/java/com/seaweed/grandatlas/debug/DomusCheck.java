package com.seaweed.grandatlas.debug;

import com.seaweed.grandatlas.GrandAtlas;
import com.seaweed.grandatlas.mortus.MortusMap;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * The dev "domusCheck" run (system property grandatlas.domusCheck): loads Domus Mortus, generates a
 * chunk at each of its places and reports what stands there (the top block, the fluid, the biome, the
 * ceiling), then stops. Writes run/grandatlas/domus_check.txt.
 */
public final class DomusCheck {
    private DomusCheck() {}

    private static final Object[][] PLACES = {
            {"Hades' platform", -324, -476}, {"Groves of Persephone", -324, -330}, {"Lethe", -200, 120}, {"Mnemosyne", -80, 120},
            {"Tartarus (pit)", 500, -150}, {"Phlegethon", 613, -150}, {"Cocytus", 430, 700}, {"Elysium", 250, -560},
            {"Asphodel", 300, 300}, {"the mountain ring", 0, -1050}, {"Acheron swamp", -800, 60}, {"the entrance (on top)", -1040, 260},
            {"the Styx", -950, -300}, {"the outer sea", 1200, 900}};

    public static void run(MinecraftServer server) {
        StringBuilder out = new StringBuilder("Domus Mortus check\n");
        ServerLevel level = server.getLevel(com.seaweed.grandatlas.AtlasCommands.DOMUS);
        if (level == null) {
            out.append("NOT LOADED: no level grandatlas:domus_mortus\n");
        } else {
            out.append(String.format("loaded: min y %d, height %d%n", level.getMinBuildHeight(), level.getHeight()));
            for (Object[] pl : PLACES) {
                int x = (Integer) pl[1], z = (Integer) pl[2];
                level.getChunk(x >> 4, z >> 4, ChunkStatus.FULL, true);
                int top = level.getHeight(Heightmap.Types.OCEAN_FLOOR, x, z) - 1, surf = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1;
                String ground = BuiltInRegistries.BLOCK.getKey(level.getBlockState(new BlockPos(x, top, z)).getBlock()).getPath();
                // the world-surface heightmap counts the barrier ceiling: look down from just under it instead
                int y = level.getMaxBuildHeight() - 2;
                while (y > level.getMinBuildHeight() && level.getBlockState(new BlockPos(x, y, z)).isAir()) y--;
                String upper = BuiltInRegistries.BLOCK.getKey(level.getBlockState(new BlockPos(x, y, z)).getBlock()).getPath();
                String ceiling = BuiltInRegistries.BLOCK.getKey(level.getBlockState(new BlockPos(x, level.getMaxBuildHeight() - 1, z)).getBlock()).getPath();
                String biome = level.getBiome(new BlockPos(x, Math.max(63, (int) MortusMap.height(x, z)) + 1, z)).unwrapKey().map(k -> k.location().getPath()).orElse("?");
                out.append(String.format("%-22s %6d %6d: region %-18s biome %-18s ground %-14s at y %4d; highest %-12s at y %4d; ceiling %s (map height %.0f)%n",
                        pl[0], x, z, MortusMap.region(x, z).id, biome, ground, top, upper, y, ceiling, MortusMap.height(x, z)));
            }
            // the entrance cave: air and the river along it, under the basalt
            int[][] probes = {{-1100, 167}, {-1072, 167}, {MortusMap.SPAWN_X, MortusMap.SPAWN_Z}, {-1030, 167}, {-995, 167}, {-900, 167}, {-760, 167}, {-650, 167}};
            String[] what = {"behind the chamber (sealed?)", "chamber middle (river)", "spawn ledge", "tunnel", "tunnel mouth", "Acheron in the swamp", "Acheron further", "toward the Styx"};
            for (int k = 0; k < probes.length; k++) {
                int px = probes[k][0], pz = probes[k][1];
                level.getChunk(px >> 4, pz >> 4, ChunkStatus.FULL, true);
                StringBuilder col = new StringBuilder();
                for (int y = 52; y <= 80; y += 2) col.append(y).append(':').append(BuiltInRegistries.BLOCK.getKey(level.getBlockState(new BlockPos(px, y, pz)).getBlock()).getPath()).append(' ');
                out.append(String.format("%-28s %6d %4d %-12s %s%n", what[k], px, pz, MortusMap.region(px, pz).id, col));
            }
        }
        try {
            Path f = server.getServerDirectory().resolve("grandatlas").resolve("domus_check.txt");
            Files.createDirectories(f.getParent());
            Files.writeString(f, out);
        } catch (Exception e) {
            GrandAtlas.LOG.error("[domus check] write failed", e);
        }
        for (String line : out.toString().split("\n")) GrandAtlas.LOG.info("[domus check] {}", line);
    }
}
