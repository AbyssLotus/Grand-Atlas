package com.seaweed.grandatlas.debug;

import com.seaweed.grandatlas.GrandAtlas;
import com.seaweed.grandatlas.core.AtlasCore;
import com.seaweed.grandatlas.core.BiomeKind;
import com.seaweed.grandatlas.world.AtlasBiomeSource;
import com.seaweed.grandatlas.world.CoreHandle;
import com.seaweed.grandatlas.world.ModdedBiomes;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * The dev "biomeModsCheck" run (M8): with TerraBlender biome mods installed (run-biomes/mods), which of
 * their biomes Grand Atlas took and on which of its own, their share of the land, then a few chunks
 * of each modded biome generated in full (features, surface rules), with the top block noted.
 * Writes run-biomes/grandatlas/biome_mods.txt, then stops.
 */
public final class BiomeModsCheck {
    private BiomeModsCheck() {}

    public static void run(MinecraftServer server) {
        long t0 = System.currentTimeMillis();
        ServerLevel level = server.overworld();
        StringBuilder out = new StringBuilder("Biome mods check (M8)\n\n");
        if (!(level.getChunkSource().getGenerator().getBiomeSource() instanceof AtlasBiomeSource source)) {
            out.append("The overworld isn't Grand Atlas's.\n");
            write(server, out);
            return;
        }
        AtlasCore core = CoreHandle.coreOf(level);
        ModdedBiomes modded = source.modded();
        out.append("== Hosts and their modded biomes (share of the host's land)\n");
        for (Map.Entry<BiomeKind, ModdedBiomes.Variant[]> e : modded.variants().entrySet()) {
            double sum = 0;
            StringBuilder l = new StringBuilder();
            for (ModdedBiomes.Variant v : e.getValue()) {
                sum += v.share();
                l.append(String.format(" %s %.0f%%", id(v.biome()), 100 * v.share()));
            }
            out.append(String.format("  %-26s %3.0f%% modded:%s%n", e.getKey().id(), 100 * sum, l));
        }
        // Land shares, by biome, over 24 km.
        Map<String, Integer> byBiome = new TreeMap<>(), byMod = new TreeMap<>();
        Map<String, BlockPos> where = new LinkedHashMap<>();
        int land = 0;
        var sampler = level.getChunkSource().randomState().sampler();
        for (int z = -12000; z <= 12000; z += 192) {
            for (int x = -12000; x <= 12000; x += 192) {
                var c = core.column(x, z);
                if (c.isOcean()) continue;
                land++;
                Holder<Biome> b = source.getNoiseBiome(QuartPos.fromBlock(x), QuartPos.fromBlock(c.surfaceY()), QuartPos.fromBlock(z), sampler);
                String id = id(b);
                byBiome.merge(id, 1, Integer::sum);
                byMod.merge(id.substring(0, id.indexOf(':')), 1, Integer::sum);
                if (!id.startsWith("minecraft:") && !id.startsWith(GrandAtlas.MOD_ID)) where.putIfAbsent(id, new BlockPos(x, c.surfaceY(), z));
            }
        }
        out.append("\n== Land by mod\n");
        final int total = land;
        byMod.forEach((k, v) -> out.append(String.format("  %-20s %5.1f%%%n", k, 100.0 * v / total)));
        out.append("\n== Modded biomes on the land\n");
        byBiome.entrySet().stream().filter(e -> !e.getKey().startsWith("minecraft:") && !e.getKey().startsWith(GrandAtlas.MOD_ID))
                .sorted((a, b) -> b.getValue() - a.getValue())
                .forEach(e -> out.append(String.format("  %-45s %5.2f%%  e.g. %s%n", e.getKey(), 100.0 * e.getValue() / total, where.get(e.getKey()).toShortString())));
        // Generate one chunk of each in full: features and surface rules must run without trouble.
        out.append("\n== One chunk of each, generated in full (top block at the sample point)\n");
        List<String> failed = new ArrayList<>();
        int n = 0;
        for (Map.Entry<String, BlockPos> e : where.entrySet()) {
            if (n++ >= 40) break;
            BlockPos p = e.getValue();
            try {
                ChunkAccess chunk = level.getChunk(p.getX() >> 4, p.getZ() >> 4, ChunkStatus.FULL, true);
                int y = chunk.getHeight(Heightmap.Types.WORLD_SURFACE_WG, p.getX() & 15, p.getZ() & 15);
                var top = chunk.getBlockState(new BlockPos(p.getX(), y, p.getZ()));
                var below = chunk.getBlockState(new BlockPos(p.getX(), y - 1, p.getZ()));
                out.append(String.format("  %-45s %s on %s%n", e.getKey(), top.getBlock().getDescriptionId().replace("block.", ""),
                        below.getBlock().getDescriptionId().replace("block.", "")));
            } catch (Exception ex) {
                failed.add(e.getKey() + ": " + ex);
                GrandAtlas.LOG.error("[biome mods] chunk of {} failed", e.getKey(), ex);
            }
        }
        out.append("\n== Failed: ").append(failed.size()).append('\n');
        failed.forEach(f -> out.append("  ").append(f).append('\n'));
        out.append(String.format("%ntook %d s%n", (System.currentTimeMillis() - t0) / 1000));
        write(server, out);
        GrandAtlas.LOG.info("[biome mods] == done: {} modded biomes on the land, {} failed", where.size(), failed.size());
    }

    private static String id(Holder<Biome> b) {
        return b.unwrapKey().map(k -> k.location().toString()).orElse("?:?");
    }

    private static void write(MinecraftServer server, StringBuilder out) {
        try {
            Path f = server.getServerDirectory().resolve("grandatlas").resolve("biome_mods.txt");
            Files.createDirectories(f.getParent());
            Files.writeString(f, out);
        } catch (Exception e) {
            GrandAtlas.LOG.error("[biome mods] write failed", e);
        }
    }
}
