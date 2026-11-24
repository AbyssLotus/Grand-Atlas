package com.seaweed.grandatlas.core.preview;

import com.seaweed.grandatlas.core.AtlasCore;
import com.seaweed.grandatlas.core.ColumnSample;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

/**
 * Surface biome shares of the land over a large square, and the land's temperature and humidity
 * spread, for tuning the climate table (Biomes). gradlew biomeStats -Pargs="--seed S --size 48000 --step 160"
 */
public final class BiomeStats {
    private BiomeStats() {}

    public static void main(String[] args) {
        long seed = -8990673802377761953L;
        int size = 48000, step = 160;
        for (int i = 0; i + 1 < args.length; i += 2) {
            switch (args[i]) {
                case "--seed" -> seed = Long.parseLong(args[i + 1]);
                case "--size" -> size = Integer.parseInt(args[i + 1]);
                case "--step" -> step = Integer.parseInt(args[i + 1]);
                default -> { }
            }
        }
        AtlasCore core = new AtlasCore(seed);
        Map<String, AtomicInteger> biomes = new ConcurrentHashMap<>();
        int bins = 10;
        AtomicInteger[][] tw = new AtomicInteger[bins][bins];
        for (int a = 0; a < bins; a++) for (int b = 0; b < bins; b++) tw[a][b] = new AtomicInteger();
        AtomicInteger land = new AtomicInteger(), all = new AtomicInteger();
        int half = size / 2, n = size / step, st = step;
        IntStream.range(0, n).parallel().forEach(row -> {
            int z = -half + row * st;
            for (int col = 0; col < n; col++) {
                int x = -half + col * st;
                ColumnSample c = core.column(x, z);
                all.incrementAndGet();
                if (c.biome().name().endsWith("OCEAN")) continue;
                land.incrementAndGet();
                biomes.computeIfAbsent(c.biome().id(), k -> new AtomicInteger()).incrementAndGet();
                int ti = Math.max(0, Math.min(bins - 1, (int) ((c.temperature() + 1) / 2 * bins)));
                int wi = Math.max(0, Math.min(bins - 1, (int) ((c.humidity() + 1) / 2 * bins)));
                tw[ti][wi].incrementAndGet();
            }
        });
        System.out.printf("land %.1f%% of %d columns%n", 100.0 * land.get() / all.get(), all.get());
        Map<String, Integer> sorted = new TreeMap<>();
        biomes.forEach((k, v) -> sorted.put(k, v.get()));
        sorted.entrySet().stream().sorted((p, q) -> q.getValue() - p.getValue())
                .forEach(e -> System.out.printf("  %-28s %5.2f%% of land%n", e.getKey(), 100.0 * e.getValue() / land.get()));
        System.out.println("temperature (rows, -1 .. 1) by humidity (columns, -1 .. 1), % of land:");
        for (int a = 0; a < bins; a++) {
            StringBuilder b = new StringBuilder(String.format("  t %+.1f:", -1 + 2.0 * a / bins));
            for (int w = 0; w < bins; w++) b.append(String.format(" %5.1f", 100.0 * tw[a][w].get() / land.get()));
            System.out.println(b);
        }
    }
}
