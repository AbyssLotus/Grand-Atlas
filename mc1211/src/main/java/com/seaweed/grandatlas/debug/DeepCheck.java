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
    /** The spiral galleries (R7) checked: {x, top y, z, bottom y, radius}. */
    private static List<double[]> spirals = new ArrayList<>();
    private static final List<long[]> forced = new ArrayList<>();
    private static int[] mountain;
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
        String[] at = System.getProperty("grandatlas.deepAt", "88000,-90001").split(",");
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
        // A mountain nearby (ground over y 250), for the upper caves' andesite, diorite and granite.
        mountain = null;
        search:
        for (int ring = 0; ring < 30; ring++) {
            for (int k = 0; k < Math.max(1, ring * 8); k++) {
                double a = k * 2 * Math.PI / Math.max(1, ring * 8), mx = cx + Math.cos(a) * ring * 128, mz = cz + Math.sin(a) * ring * 128;
                if (core.column((int) mx, (int) mz).height() > 250) {
                    mountain = new int[] {(int) mx >> 4, (int) mz >> 4};
                    break search;
                }
            }
        }
        if (mountain != null) {
            for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
                queue.add(new long[] {mountain[0] + dx, mountain[1] + dz});
                forced.add(new long[] {mountain[0] + dx, mountain[1] + dz});
            }
        }
        // R7: the two nearest spiral galleries, their whole drum loaded, walked top to bottom in the world.
        List<double[]> sps = new ArrayList<>();
        core.deep().spirals(cx - 3000, cz - 3000, cx + 3000, cz + 3000, sps);
        sps.sort((a, b) -> Double.compare(Math.hypot(a[0] - cx, a[2] - cz), Math.hypot(b[0] - cx, b[2] - cz)));
        spirals = new ArrayList<>(sps.subList(0, Math.min(2, sps.size())));
        GrandAtlas.LOG.info("[deep check] {} spirals within 3000; checking {}", sps.size(), spirals.size());
        for (double[] f : spirals) {
            int fx = (int) Math.floor(f[0]) >> 4, fz = (int) Math.floor(f[2]) >> 4;
            for (int dx = -3; dx <= 3; dx++) {
                for (int dz = -3; dz <= 3; dz++) {
                    queue.add(new long[] {fx + dx, fz + dz});
                    forced.add(new long[] {fx + dx, fz + dz});
                }
            }
        }
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(ServerTickEvent.Post.class, e -> tick(e.getServer()));
    }

    /**
     * Walks a spiral gallery in the world: from a standing spot on the ledge near the top, over floor
     * with two blocks of headroom, stepping at most one block up or down. How far down it gets, and
     * how many places along the way there are to stand.
     */
    /** The upper caves' rock (chalk, clay, soil): the wall blocks round a few passages in those regions near the forced chunks. */
    private static String rockCheck(ServerLevel level, AtlasCore core) {
        StringBuilder sb = new StringBuilder("upper cave rock:");
        // andesite, diorite and granite above y 60 (in the rock and showing on walls)
        int[] inRock = new int[3], shown = new int[3];
        String[] names = {"andesite", "diorite", "granite"};
        for (long[] c : forced) {
            if (!level.getChunkSource().hasChunk((int) c[0], (int) c[1])) continue;
            if (mountain == null || Math.abs(c[0] - mountain[0]) > 1 || Math.abs(c[1] - mountain[1]) > 1) continue; // the mountain's chunks only
            for (int x = (int) c[0] * 16; x < (int) c[0] * 16 + 16; x++) {
                for (int z = (int) c[1] * 16; z < (int) c[1] * 16 + 16; z++) {
                    for (int y = 60; y < 700; y++) {
                        String id = BuiltInRegistries.BLOCK.getKey(level.getBlockState(new BlockPos(x, y, z)).getBlock()).getPath();
                        for (int k = 0; k < 3; k++) {
                            if (!id.equals(names[k])) continue;
                            inRock[k]++;
                            for (net.minecraft.core.Direction d : net.minecraft.core.Direction.values()) {
                                if (level.getBlockState(new BlockPos(x, y, z).relative(d)).isAir()) {
                                    shown[k]++;
                                    break;
                                }
                            }
                        }
                    }
                }
            }
        }
        sb.append(mountain == null ? " (no mountain nearby)" : String.format(" mountain at chunk %d %d,", mountain[0], mountain[1]));
        sb.append(String.format(" above y 60: andesite %d (%d showing), diorite %d (%d), granite %d (%d)", inRock[0], shown[0], inRock[1], shown[1], inRock[2], shown[2]));
        // the locate command's cave targets, from the first checked chunk
        if (!forced.isEmpty()) {
            int lx = (int) forced.get(0)[0] * 16, lz = (int) forced.get(0)[1] * 16;
            for (String id : new java.util.TreeSet<>(CaveLocate.IDS)) {
                long t0 = System.nanoTime();
                String found = CaveLocate.find(core, id, lx, lz);
                sb.append(String.format("%n  locate %s: %s (%d ms)", id, found == null ? "NOTHING FOUND" : found, (System.nanoTime() - t0) / 1_000_000));
            }
        }
        int done = 0;
        for (long[] c : forced) {
            if (done >= 30) break;
            int x = (int) c[0] * 16 + 8, z = (int) c[1] * 16 + 8;
            int prov = core.passages().rockProvince(x, z);
            if (prov == 0) continue;
            List<double[]> pieces = new ArrayList<>();
            core.passages().piecesNear(x, z, 8, pieces);
            for (double[] g : pieces) {
                double mx = (g[0] + g[3]) / 2, my = (g[1] + g[4]) / 2, mz = (g[2] + g[5]) / 2;
                double ground = core.column((int) mx, (int) mz).height();
                if (my < -60 || my > ground - 14 || g[6] == 8 || g[6] == 9) continue;
                if (!level.getChunkSource().hasChunk((int) mx >> 4, (int) mz >> 4)) continue;
                java.util.Map<String, Integer> walls = new java.util.TreeMap<>();
                for (int dx = -4; dx <= 4; dx++) for (int dy = -4; dy <= 4; dy++) for (int dz = -4; dz <= 4; dz++) {
                    BlockPos q = BlockPos.containing(mx + dx, my + dy, mz + dz);
                    net.minecraft.world.level.block.state.BlockState st = level.getBlockState(q);
                    if (st.isAir() || !st.getFluidState().isEmpty()) continue;
                    boolean open = false;
                    for (net.minecraft.core.Direction d : net.minecraft.core.Direction.values()) open |= level.getBlockState(q.relative(d)).isAir();
                    if (open) walls.merge(BuiltInRegistries.BLOCK.getKey(st.getBlock()).getPath(), 1, Integer::sum);
                }
                int skin = 0, all = 0;
                for (var e : walls.entrySet()) {
                    all += e.getValue();
                    String k = e.getKey();
                    if (k.equals("calcite") || k.equals("diorite") || k.contains("terracotta") || k.equals("clay") || k.equals("packed_mud")
                            || k.equals("dirt") || k.equals("rooted_dirt") || k.equals("coarse_dirt")) skin += e.getValue();
                }
                sb.append(String.format("%n  %s at %d %d %d (%.0f under the ground): %d of %d wall blocks in the region's rock", prov == 1 ? "chalk" : prov == 2 ? "clay" : "soil",
                        (int) mx, (int) my, (int) mz, ground - my, skin, all));
                done++;
                break;
            }
        }
        return sb.toString();
    }

    private static String walkSpiral(ServerLevel level, double[] f) {
        AtlasCore core = com.seaweed.grandatlas.world.CoreHandle.coreOf(level);
        // the core's own carve (the spiral's shape, before anything is placed in it) ...
        java.util.function.Predicate<BlockPos> coreOpen = q -> core.passages().carve(core.column(q.getX(), q.getZ()), q.getX(), q.getY(), q.getZ()) < 0;
        // ... and the world (anything a player walks through counts as open: vines, carpets, plants)
        java.util.function.Predicate<BlockPos> worldOpen = q -> !level.getBlockState(q).blocksMotion();
        java.util.function.Predicate<BlockPos> shapeOpen = q -> core.deep().spiralOnly(q.getX(), q.getY(), q.getZ()) < 0;
        // what the world has put where the core left the ledge open (what blocks a walker)
        java.util.Map<String, Integer> blockers = new java.util.TreeMap<>();
        int cx = (int) Math.floor(f[0]), cz = (int) Math.floor(f[2]), r = (int) Math.ceil(f[4] + 6);
        for (int x = cx - r; x <= cx + r; x++) {
            for (int z = cz - r; z <= cz + r; z++) {
                for (int y = (int) f[3]; y <= (int) f[1] + 4; y++) {
                    BlockPos q = new BlockPos(x, y, z);
                    if (!coreOpen.test(q) || !coreOpen.test(q.above()) || coreOpen.test(q.below())) continue; // a core standing spot
                    for (BlockPos b : new BlockPos[] {q, q.above()}) {
                        net.minecraft.world.level.block.state.BlockState st = level.getBlockState(b);
                        if (st.blocksMotion()) blockers.merge(BuiltInRegistries.BLOCK.getKey(st.getBlock()).getPath(), 1, Integer::sum);
                    }
                }
            }
        }
        return walk(f, shapeOpen, "shape") + "; " + walk(f, coreOpen, "core") + "; " + walk(f, worldOpen, "world") + "; blockers " + blockers;
    }

    private static String walk(double[] f, java.util.function.Predicate<BlockPos> open, String what) {
        int cx = (int) Math.floor(f[0]), cz = (int) Math.floor(f[2]), top = (int) Math.floor(f[1]), bottom = (int) Math.floor(f[3]);
        double rad = f[4];
        java.util.function.Predicate<BlockPos> stand = q -> open.test(q) && open.test(q.above()) && !open.test(q.below());
        // start from every standing spot on the ledge near the entrance (angle 0, the top turn)
        java.util.HashSet<Long> seen = new java.util.HashSet<>();
        java.util.ArrayDeque<BlockPos> q = new java.util.ArrayDeque<>();
        BlockPos start = null;
        for (int a = -25; a <= 25; a += 2) {
            for (double rr = rad - 3; rr <= rad + 3; rr += 1) {
                for (int y = top - 2; y <= top + 2; y++) {
                    BlockPos c = new BlockPos((int) Math.floor(cx + Math.cos(Math.toRadians(a)) * rr), y, (int) Math.floor(cz + Math.sin(Math.toRadians(a)) * rr));
                    if (stand.test(c) && seen.add(c.asLong())) {
                        q.add(c);
                        if (start == null) start = c;
                    }
                }
            }
        }
        String head = String.format("spiral at %d %d (top %d, bottom %d, radius %.0f) %s: ", cx, cz, top, bottom, rad, what);
        if (start == null) return head + "no standing spot at the top";
        int lowest = start.getY(), starts = seen.size();
        int lim = (int) Math.ceil(rad + 8);
        while (!q.isEmpty() && seen.size() < 400000) {
            BlockPos c = q.poll();
            lowest = Math.min(lowest, c.getY());
            for (net.minecraft.core.Direction d : net.minecraft.core.Direction.Plane.HORIZONTAL) {
                for (int dy = 1; dy >= -1; dy--) {
                    BlockPos n = c.relative(d).above(dy);
                    if (Math.abs(n.getX() - cx) > lim || Math.abs(n.getZ() - cz) > lim || n.getY() < bottom - 4) continue;
                    if (!stand.test(n)) continue;
                    if (dy == 1 && !open.test(c.above(2))) continue;
                    if (seen.add(n.asLong())) q.add(n);
                }
            }
        }
        return head + String.format("from y %d down to y %d, %d spots, %s", start.getY(), lowest, seen.size(), lowest <= bottom + 8 ? "WALKABLE" : "BROKEN at y " + lowest);
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
        for (double[] f : spirals) out.append(walkSpiral(level, f)).append('\n');
        out.append(rockCheck(level, core)).append('\n');
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
                            int held = overAir && !nearFall ? core.waters().state(core.column(x, z), x, y, z) : 0;
                            if (held == com.seaweed.grandatlas.core.Waters.HELD || held == com.seaweed.grandatlas.core.Waters.RISING) continue; // a set piece's hanging lake: on purpose
                            if (!nearFall && overAir) {
                                hanging++;
                                if (hangingAt.size() < 8) {
                                    // what the core makes of it and of the block under it, and what's near (for the leaks hunt)
                                    com.seaweed.grandatlas.core.ColumnSample hc = core.column(x, z);
                                    hangingAt.add(x + " " + y + " " + z + " " + BuiltInRegistries.BLOCK.getKey(s.getBlock()).getPath()
                                            + " [state " + core.waters().state(hc, x, y, z) + "/" + core.waters().state(hc, x, y - 1, z)
                                            + " mega " + (core.megaCaverns().carve(x, y - 1, z) < 0) + " set " + core.setPieces().nearColumn(x, z)
                                            + " table " + core.waters().table(x, z) + " kind " + com.seaweed.grandatlas.core.Passages.kindName(core.passages().kindAt(x, y - 1, z)) + "]");
                                }
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
            "deepslate_copper_ore", "deepslate_iron_ore", "deepslate_gold_ore", "mossy_cobblestone", "weeping_vines", "weeping_vines_plant",
            "clay", "cobbled_deepslate", "dripstone_block", "coarse_dirt",
            "chest", "rail", "mangrove_roots", "mycelium", "red_mushroom_block", "brown_mushroom_block", "crafting_table", "moss_block");
}
