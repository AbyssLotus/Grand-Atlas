package com.seaweed.grandatlas.debug;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import com.seaweed.grandatlas.GrandAtlas;
import com.seaweed.grandatlas.core.AtlasCore;
import com.seaweed.grandatlas.core.ColumnSample;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Enumeration;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.Consumer;
import java.util.stream.IntStream;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.RandomState;

/**
 * The cave volume survey (cave rework C0, docs/CAVE_RESEARCH.md section 4). The user's hard
 * requirement: by the end of the rework Grand Atlas's caves, with vanilla's off, open at least 75%
 * as much space as vanilla's caves do, band by band.
 *
 * <p>Vanilla's caves aren't in the core, so they're measured here, in a running server: vanilla's
 * own overworld terrain (its noise settings read from the Minecraft jar, since Grand Atlas replaces
 * them in the data) is built for this world's seed, and its final density is sampled every 4
 * blocks in the four boxes the research measured. A sample is open cave if the density is at or
 * under zero and it lies 10+ blocks under that column's ground. Grand Atlas's own caves are counted
 * the same way in the same boxes, on its own terrain (as {@code CaveStats} did).
 *
 * <p>Run with {@code /grandatlas survey caves}, or the {@code caveSurvey} dev run, which runs it
 * when the server has started and then stops the server. Writes {@code grandatlas/cave_survey.txt}
 * in the server folder.
 */
public final class CaveSurvey {
    private CaveSurvey() {}

    static final int[][] BOXES = {{0, 0}, {4573, 3347}, {-1400, 5100}, {9000, 6000}};
    static final int HALF = 650, STEP = 4;
    /** Bands: the shell (top 40 under the ground), upper (over 0), low (-64 to 0: vanilla's deepest), deep (to -250), abyss. */
    static final String[] BANDS = {"shell", "upper", "low", "deep", "abyss"};

    static int band(int y, int top) {
        return y > top - 40 ? 0 : y > 0 ? 1 : y > -64 ? 2 : y > -250 ? 3 : 4;
    }

    private static volatile boolean running;

    /** Runs the survey on its own thread; each line of the report goes to {@code out} as it's ready. Then {@code done}. */
    public static boolean start(MinecraftServer server, Consumer<String> out, Runnable done) {
        if (running) return false;
        running = true;
        Thread t = new Thread(() -> {
            try {
                run(server, out);
            } catch (Throwable e) {
                GrandAtlas.LOG.error("Cave survey failed", e);
                out.accept("Cave survey failed: " + e);
            } finally {
                running = false;
                done.run();
            }
        }, "Grand Atlas cave survey");
        t.setDaemon(true);
        t.start();
        return true;
    }

    static void run(MinecraftServer server, Consumer<String> out) throws Exception {
        long seed = server.getWorldData().worldGenOptions().seed();
        NoiseGeneratorSettings vanilla = vanillaSettings(server);
        RandomState state = RandomState.create(vanilla, server.registryAccess().lookupOrThrow(Registries.NOISE), seed);
        DensityFunction density = state.router().finalDensity();
        AtlasCore core = com.seaweed.grandatlas.world.CoreHandle.coreOf(server.overworld());
        StringBuilder report = new StringBuilder();
        Consumer<String> say = s -> {
            report.append(s).append('\n');
            out.accept(s);
            GrandAtlas.LOG.info("[cave survey] {}", s);
        };
        say.accept(String.format("Cave survey, world seed %d, four %d m boxes sampled every %d blocks", seed, 2 * HALF, STEP));
        long[][] vRock = new long[BOXES.length][BANDS.length], vOpen = new long[BOXES.length][BANDS.length];
        long[][] gRock = new long[BOXES.length][BANDS.length], gOpen = new long[BOXES.length][BANDS.length];
        for (int b = 0; b < BOXES.length; b++) {
            int cx = BOXES[b][0], cz = BOXES[b][1];
            long t0 = System.nanoTime();
            vanillaBox(density, cx, cz, vRock[b], vOpen[b]);
            long t1 = System.nanoTime();
            if (core != null) atlasBox(core, cx, cz, gRock[b], gOpen[b]);
            long t2 = System.nanoTime();
            say.accept(String.format("box %d, %d (vanilla %.0f s, Grand Atlas %.0f s)", cx, cz, (t1 - t0) / 1e9, (t2 - t1) / 1e9));
            for (int k = 0; k < BANDS.length; k++) {
                if (vRock[b][k] == 0 && gRock[b][k] == 0) continue;
                say.accept(String.format("  %-6s vanilla %6.2f%%   Grand Atlas %6.2f%%", BANDS[k], pct(vOpen[b][k], vRock[b][k]), pct(gOpen[b][k], gRock[b][k])));
            }
        }
        say.accept("All four boxes; the bar is 75% of vanilla (deep and abyss: 75% of vanilla's low band, -64 to 0):");
        double vLow = 0;
        for (int k = 0; k < BANDS.length; k++) {
            long vr = 0, vo = 0, gr = 0, go = 0;
            for (int b = 0; b < BOXES.length; b++) {
                vr += vRock[b][k];
                vo += vOpen[b][k];
                gr += gRock[b][k];
                go += gOpen[b][k];
            }
            double v = pct(vo, vr), g = pct(go, gr);
            if (k == 2) vLow = v;
            double ref = k >= 3 ? vLow : v;
            double bar = 0.75 * ref;
            say.accept(String.format("  %-6s vanilla %6.2f%%   Grand Atlas %6.2f%%   bar %6.2f%%   %s (%.0f%% of the reference)",
                    BANDS[k], k >= 3 ? Double.NaN : v, g, bar, g >= bar ? "MEETS" : "short", ref > 0 ? 100 * g / ref : 0));
        }
        Path file = server.getServerDirectory().resolve("grandatlas").resolve("cave_survey.txt");
        Files.createDirectories(file.getParent());
        Files.writeString(file, report.toString());
        say.accept("Written to " + file.toAbsolutePath());
    }

    private static double pct(long open, long rock) {
        return rock == 0 ? Double.NaN : 100.0 * open / rock;
    }

    /** Vanilla's overworld noise settings, from the Minecraft jar (Grand Atlas's data replaces them). */
    static NoiseGeneratorSettings vanillaSettings(MinecraftServer server) throws Exception {
        String path = "data/minecraft/worldgen/noise_settings/overworld.json";
        URL chosen = null;
        Enumeration<URL> all = NoiseGeneratorSettings.class.getClassLoader().getResources(path);
        while (all.hasMoreElements()) {
            URL u = all.nextElement();
            String s = u.toString();
            if (s.contains("grand-atlas") || s.contains("grandatlas") || s.contains("mc1211")) continue;
            chosen = u;
        }
        InputStream in = chosen != null ? chosen.openStream() : NoiseGeneratorSettings.class.getResourceAsStream("/" + path);
        if (in == null) throw new IllegalStateException("vanilla overworld noise settings not found");
        JsonElement json;
        try (InputStreamReader r = new InputStreamReader(in, StandardCharsets.UTF_8)) {
            json = JsonParser.parseReader(r);
        }
        if (json.toString().contains("grandatlas")) throw new IllegalStateException("found Grand Atlas's noise settings, not vanilla's (" + chosen + ")");
        RegistryOps<JsonElement> ops = RegistryOps.create(JsonOps.INSTANCE, server.registryAccess());
        return NoiseGeneratorSettings.DIRECT_CODEC.parse(ops, json).getOrThrow();
    }

    static void vanillaBox(DensityFunction f, int cx, int cz, long[] rock, long[] open) {
        int n = 2 * HALF / STEP + 1;
        LongAdder[] r = adders(), o = adders();
        IntStream.range(0, n * n).parallel().forEach(k -> {
            int x = cx - HALF + (k % n) * STEP, z = cz - HALF + (k / n) * STEP;
            int top = Integer.MIN_VALUE;
            for (int y = 316; y >= -60; y -= STEP) {
                if (f.compute(new DensityFunction.SinglePointContext(x, y, z)) > 0) {
                    top = y;
                    break;
                }
            }
            if (top == Integer.MIN_VALUE) return;
            for (int y = -60; y < top - 10; y += STEP) {
                int b = band(y, top);
                r[b].increment();
                if (f.compute(new DensityFunction.SinglePointContext(x, y, z)) <= 0) o[b].increment();
            }
        });
        for (int b = 0; b < BANDS.length; b++) {
            rock[b] += r[b].sum();
            open[b] += o[b].sum();
        }
    }

    static void atlasBox(AtlasCore core, int cx, int cz, long[] rock, long[] open) {
        int n = 2 * HALF / STEP + 1;
        LongAdder[] r = adders(), o = adders();
        IntStream.range(0, n * n).parallel().forEach(k -> {
            int x = cx - HALF + (k % n) * STEP, z = cz - HALF + (k / n) * STEP;
            ColumnSample c = core.column(x, z);
            int top = c.surfaceY();
            boolean megaCol = core.megaCaverns().openColumn(x, z), setCol = core.setPieces().nearColumn(x, z);
            boolean fineCol = !core.caves().fineNone(x, z);
            for (int y = -508; y < top - 10; y += STEP) {
                int b = band(y, top);
                r[b].increment();
                boolean any = core.passages().carve(c, x, y, z) < 0
                        || megaCol && core.megaCaverns().carve(x, y, z) < 0
                        || y > AtlasCore.TERRAIN_FLOOR + 13 && core.caves().cavern(c, x, y, z) < 0
                        || fineCol && core.caves().fine(x, y, z) < 0
                        || setCol && core.setPieces().sample(x, y, z).open;
                if (any) o[b].increment();
            }
        });
        for (int b = 0; b < BANDS.length; b++) {
            rock[b] += r[b].sum();
            open[b] += o[b].sum();
        }
    }

    private static LongAdder[] adders() {
        LongAdder[] a = new LongAdder[BANDS.length];
        for (int i = 0; i < a.length; i++) a[i] = new LongAdder();
        return a;
    }
}
