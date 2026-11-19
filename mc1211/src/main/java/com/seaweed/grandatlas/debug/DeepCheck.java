package com.seaweed.grandatlas.debug;

import com.seaweed.grandatlas.GrandAtlas;
import com.seaweed.grandatlas.core.AtlasCore;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * The deepCheck dev run (system property grandatlas.deepCheck): on a headless server, finds falls of
 * the deep network (core/Deep, cave rework R5) on fresh ground (grandatlas.deepAt, "x,z", default
 * 40000,40000), force-loads the chunks round them so they generate, lets the world tick so water and
 * lava flow, then reports what is really there: whether each fall's source appeared and reached its
 * pool, fluid hanging where it shouldn't, magma rivers, hanging forests and the R4 vignettes' blocks.
 * Writes grandatlas/deep_check.txt and stops the server.
 */
public final class DeepCheck {
    private DeepCheck() {}

    private static final int R = 1;            // chunks forced round each fall
    private static final int SETTLE = 500;      // ticks to let fluids run once everything is loaded
    private static final int GIVE_UP = 24000;

    private static List<double[]> falls;
    private static final List<long[]> forced = new ArrayList<>();
    /** Chunks still to force: one a tick, as forcing generates the chunk there and then (the watchdog stopped a whole batch). */
    private static final java.util.ArrayDeque<long[]> queue = new java.util.ArrayDeque<>();
    private static int t, loadedAt = -1;
    private static Runnable done;

    public static void start(MinecraftServer server, Runnable onDone) {
        done = onDone;
        ServerLevel level = server.overworld();
        AtlasCore core = com.seaweed.grandatlas.world.CoreHandle.coreOf(level);
        if (core == null) {
            GrandAtlas.LOG.error("[deep check] no Grand Atlas core");
            onDone.run();
            return;
        }
        String[] at = System.getProperty("grandatlas.deepAt", "30000,-60000").split(",");
        double cx = Double.parseDouble(at[0]), cz = Double.parseDouble(at[1]);
        List<double[]> all = new ArrayList<>();
        core.deep().falls(cx - 1500, cz - 1500, cx + 1500, cz + 1500, all);
        all.sort((a, b) -> Double.compare(Math.hypot(a[0] - cx, a[2] - cz), Math.hypot(b[0] - cx, b[2] - cz)));
        falls = new ArrayList<>();
        int water = 0, lava = 0;
        for (double[] f : all) {
            boolean isLava = f[3] > 0.5;
            if (isLava ? lava >= 2 : water >= 3) continue;
            falls.add(f);
            if (isLava) lava++;
            else water++;
        }
        GrandAtlas.LOG.info("[deep check] {} falls found within 1500 of {} {}; checking {} ({} water, {} lava)", all.size(), (int) cx, (int) cz, falls.size(), water, lava);
        for (double[] f : falls) {
            int fx = (int) Math.floor(f[0]) >> 4, fz = (int) Math.floor(f[2]) >> 4;
            for (int dx = -R; dx <= R; dx++) {
                for (int dz = -R; dz <= R; dz++) {
                    queue.add(new long[] {fx + dx, fz + dz});
                    forced.add(new long[] {fx + dx, fz + dz});
                }
            }
        }
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(ServerTickEvent.Post.class, e -> tick(e.getServer()));
    }

    private static void tick(MinecraftServer server) {
        if (done == null) return;
        t++;
        ServerLevel level = server.overworld();
        while (!queue.isEmpty()) {
            // The forced ticket without setChunkForced's wait on the server thread: a cold region's first
            // chunk can take longer than the dev server's 60 s watchdog allows. Polled below.
            long[] c = queue.poll();
            net.minecraft.world.level.ChunkPos cp = new net.minecraft.world.level.ChunkPos((int) c[0], (int) c[1]);
            level.getChunkSource().addRegionTicket(net.minecraft.server.level.TicketType.FORCED, cp, 2, cp);
        }
        if (loadedAt < 0) {
            boolean all = true;
            for (long[] c : forced) all &= level.getChunkSource().hasChunk((int) c[0], (int) c[1]);
            if (all) {
                loadedAt = t;
                GrandAtlas.LOG.info("[deep check] all {} chunks loaded after {} ticks; letting fluids run", forced.size(), t);
            } else if (t > GIVE_UP) {
                loadedAt = t;
                GrandAtlas.LOG.warn("[deep check] not every chunk loaded after {} ticks; reporting anyway", t);
            }
            return;
        }
        if (t < loadedAt + SETTLE) return;
        try {
            report(level);
        } catch (Exception ex) {
            GrandAtlas.LOG.error("[deep check] report failed", ex);
        }
        Runnable d = done;
        done = null;
        d.run();
    }

    private static void report(ServerLevel level) throws Exception {
        AtlasCore core = com.seaweed.grandatlas.world.CoreHandle.coreOf(level);
        StringBuilder out = new StringBuilder("Deep check (cave rework R4/R5)\n");
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (double[] f : falls) {
            boolean lava = f[3] > 0.5;
            int cx = (int) Math.floor(f[0]), cz = (int) Math.floor(f[2]), top = (int) Math.floor(f[1]);
            double r = f[4];
            int ring = 0, column = 0, pool = 0, footY = Integer.MIN_VALUE;
            // the source ring, 6 under the top junction
            for (int dx = -(int) r - 3; dx <= r + 3; dx++) {
                for (int dz = -(int) r - 3; dz <= r + 3; dz++) {
                    FluidState fs = level.getFluidState(p.set(cx + dx, top - 6, cz + dz));
                    if (!fs.isEmpty() && fs.isSource() && fs.is(lava ? FluidTags.LAVA : FluidTags.WATER)) ring++;
                }
            }
            // fluid down the shaft, and where the shaft ends (the first floor under the middle)
            for (int y = top - 7; y > top - 220; y--) {
                boolean any = false;
                for (int dx = -(int) r - 1; dx <= r + 1 && !any; dx++) {
                    for (int dz = -(int) r - 1; dz <= r + 1 && !any; dz++) {
                        FluidState fs = level.getFluidState(p.set(cx + dx, y, cz + dz));
                        if (!fs.isEmpty()) any = true;
                    }
                }
                if (any) column++;
                BlockState mid = level.getBlockState(p.set(cx, y, cz));
                if (footY == Integer.MIN_VALUE && !mid.isAir() && mid.getFluidState().isEmpty() && y < top - 20) footY = y;
            }
            if (footY != Integer.MIN_VALUE) {
                for (int dx = -12; dx <= 12; dx++) {
                    for (int dz = -12; dz <= 12; dz++) {
                        for (int y = footY; y <= footY + 4; y++) {
                            FluidState fs = level.getFluidState(p.set(cx + dx, y, cz + dz));
                            if (!fs.isEmpty() && fs.is(lava ? FluidTags.LAVA : FluidTags.WATER)) pool++;
                        }
                    }
                }
            }
            // The middle column, top to bottom, as runs of each block: what the shaft actually holds.
            StringBuilder prof = new StringBuilder();
            String run = null;
            int n = 0, runTop = top + 4;
            for (int y = top + 4; y > top - 230; y--) {
                BlockState s = level.getBlockState(p.set(cx, y, cz));
                String id = BuiltInRegistries.BLOCK.getKey(s.getBlock()).getPath() + (s.getFluidState().isEmpty() ? "" : s.getFluidState().isSource() ? "(src)" : "(flow)");
                if (id.equals(run)) {
                    n++;
                    continue;
                }
                if (run != null) prof.append(runTop).append(':').append(run).append('x').append(n).append(' ');
                run = id;
                n = 1;
                runTop = y;
            }
            if (run != null) prof.append(runTop).append(':').append(run).append('x').append(n);
            out.append("  middle column: ").append(prof).append('\n');
            out.append(String.format("fall %s at %d %d: top y %d, radius %.1f; source blocks in the wall %d; levels of the shaft with fluid %d; floor under it at y %s; %s blocks round the foot %d%n",
                    lava ? "LAVA" : "WATER", cx, cz, top, r, ring, column, footY == Integer.MIN_VALUE ? "?" : String.valueOf(footY), lava ? "lava/magma-side" : "water", pool));
        }
        // Over every forced chunk, below y -64: fluid hanging over air away from any fall, and the R4/R5 blocks.
        Map<String, Integer> blocks = new TreeMap<>();
        int hanging = 0, lavaAbyss = 0, dripstone = 0, sources = 0, curtain = 0;
        List<String> hangingAt = new ArrayList<>();
        for (long[] c : forced) {
            int x0 = (int) c[0] * 16, z0 = (int) c[1] * 16;
            if (!level.getChunkSource().hasChunk((int) c[0], (int) c[1])) continue;
            for (int x = x0; x < x0 + 16; x++) {
                for (int z = z0; z < z0 + 16; z++) {
                    boolean nearFall = false;
                    for (double[] f : falls) nearFall |= Math.hypot(x - f[0], z - f[2]) < f[4] + 14;
                    for (int y = -500; y < -64; y++) {
                        BlockState s = level.getBlockState(p.set(x, y, z));
                        if (s.isAir()) continue;
                        FluidState fs = s.getFluidState();
                        if (!fs.isEmpty()) {
                            if (fs.isSource()) sources++;
                            if (fs.is(FluidTags.LAVA) && y < AtlasCore.ABYSS_TOP) lavaAbyss++;
                            boolean overAir = fs.isSource() && level.getBlockState(p.set(x, y - 1, z)).isAir();
                            if (!nearFall && overAir && fs.is(FluidTags.LAVA)) {
                                curtain++; // lava curtains pour from the roof on purpose
                                continue;
                            }
                            if (!nearFall && overAir) {
                                hanging++;
                                if (hangingAt.size() < 8) hangingAt.add(x + " " + y + " " + z + " " + BuiltInRegistries.BLOCK.getKey(s.getBlock()).getPath());
                            }
                            continue;
                        }
                        String id = BuiltInRegistries.BLOCK.getKey(s.getBlock()).getPath();
                        if (id.equals("pointed_dripstone")) dripstone++;
                        if (MARKERS.contains(id)) blocks.merge(id, 1, Integer::sum);
                    }
                }
            }
        }
        // Ores the player can see (an ore block with air beside it) against all cave wall blocks, and
        // light-giving blocks that aren't lava (the user, 2026-11-18: "no ores", "too many things glow").
        String[] bandName = {"y -64..60", "deep", "abyss"};
        int[] walls = new int[3], oresShown = new int[3], oresAll = new int[3], glowing = new int[3];
        Map<String, Integer> glowKinds = new TreeMap<>(), wallKinds = new TreeMap<>();
        for (long[] c : forced) {
            int x0 = (int) c[0] * 16, z0 = (int) c[1] * 16;
            if (!level.getChunkSource().hasChunk((int) c[0], (int) c[1])) continue;
            for (int x = x0; x < x0 + 16; x++) {
                for (int z = z0; z < z0 + 16; z++) {
                    for (int y = -500; y < 60; y++) {
                        BlockState s = level.getBlockState(p.set(x, y, z));
                        if (s.isAir() || !s.getFluidState().isEmpty()) continue;
                        int band = y >= -64 ? 0 : y >= AtlasCore.ABYSS_TOP ? 1 : 2;
                        String id = BuiltInRegistries.BLOCK.getKey(s.getBlock()).getPath();
                        boolean ore = id.endsWith("_ore");
                        if (ore) oresAll[band]++;
                        if (s.getLightEmission() > 0) {
                            glowing[band]++;
                            glowKinds.merge(bandName[band] + ":" + id, 1, Integer::sum);
                        }
                        boolean open = false;
                        for (net.minecraft.core.Direction d : net.minecraft.core.Direction.values()) {
                            if (level.getBlockState(p.set(x, y, z).move(d)).isAir()) {
                                open = true;
                                break;
                            }
                        }
                        if (!open) continue;
                        walls[band]++;
                        if (ore) oresShown[band]++;
                        wallKinds.merge(bandName[band] + ":" + id, 1, Integer::sum);
                    }
                }
            }
        }
        for (int b = 0; b < 3; b++) {
            out.append(String.format("ores %s: %d in the rock, %d showing on %d cave wall blocks (%.2f per 1000 wall blocks); %d light-giving blocks%n",
                    bandName[b], oresAll[b], oresShown[b], walls[b], walls[b] == 0 ? 0 : 1000.0 * oresShown[b] / walls[b], glowing[b]));
        }
        out.append("light-giving blocks by kind: ").append(glowKinds).append('\n');
        wallKinds.entrySet().stream().filter(e -> e.getValue() > 400).forEach(e -> out.append("  wall ").append(e.getKey()).append(' ').append(e.getValue()).append('\n'));
        out.append(String.format("%nIn the %d forced chunks below y -64: %d fluid sources, %d lava blocks in the abyss, %d pointed dripstone%n",
                forced.size(), sources, lavaAbyss, dripstone));
        out.append(String.format("water sources hanging over air away from the falls: %d %s%n", hanging, hangingAt));
        out.append(String.format("lava sources pouring from a roof (lava curtains): %d%n", curtain));
        out.append("vignette blocks: ").append(blocks).append('\n');
        Path file = level.getServer().getServerDirectory().resolve("grandatlas").resolve("deep_check.txt");
        Files.createDirectories(file.getParent());
        Files.writeString(file, out);
        for (String line : out.toString().split("\n")) GrandAtlas.LOG.info("[deep check] {}", line);
    }

    private static final java.util.Set<String> MARKERS = java.util.Set.of("skeleton_skull", "wither_skeleton_skull", "bone_block", "cobweb",
            "campfire", "barrel", "chain", "iron_bars", "soul_lantern", "candle", "wither_rose", "soul_fire", "soul_soil", "spore_blossom",
            "azalea", "flowering_azalea", "moss_carpet", "glow_lichen", "cave_vines", "cave_vines_plant", "hanging_roots", "rooted_dirt",
            "brown_mushroom", "red_mushroom", "mushroom_stem", "amethyst_cluster", "large_amethyst_bud", "calcite", "sculk_sensor",
            "sculk_shrieker", "sculk_catalyst", "sculk_vein", "infested_deepslate", "spawner", "obsidian", "crying_obsidian", "tinted_glass",
            "magma_block", "warped_nylium", "crimson_nylium", "warped_fungus", "crimson_fungus", "polished_basalt", "gilded_blackstone",
            "deepslate_copper_ore", "deepslate_iron_ore", "deepslate_gold_ore", "mossy_cobblestone", "weeping_vines", "weeping_vines_plant");
}
