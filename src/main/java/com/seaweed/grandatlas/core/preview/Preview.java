package com.seaweed.grandatlas.core.preview;

import com.seaweed.grandatlas.core.AtlasCore;
import com.seaweed.grandatlas.core.BiomeKind;
import com.seaweed.grandatlas.core.ColumnSample;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.stream.IntStream;
import javax.imageio.ImageIO;

/**
 * Renders the terrain core to PNGs without Minecraft:
 * <ul>
 *   <li>{@code map.png}: hillshaded elevation with a hypsometric tint</li>
 *   <li>{@code biomes.png}: surface biomes, hillshaded</li>
 *   <li>{@code section.png}: a true-height vertical cut along z = centre (1 px = 2 blocks vertically)</li>
 * </ul>
 * Run with {@code gradlew preview -Pargs="--seed 1 --x 0 --z 0 --size 16384 --scale 16 --out preview"}.
 * The seed here is the core's own seed, not a Minecraft world seed (in game the core seed is
 * derived from the world seed).
 */
public final class Preview {
    private Preview() {}

    public static void main(String[] args) throws IOException {
        long seed = 1;
        int cx = 0, cz = 0, size = 16384, scale = 16;
        String out = "preview";
        for (int i = 0; i + 1 < args.length; i += 2) {
            String v = args[i + 1];
            switch (args[i]) {
                case "--seed" -> seed = parseSeed(v);
                case "--x" -> cx = Integer.parseInt(v);
                case "--z" -> cz = Integer.parseInt(v);
                case "--size" -> size = Integer.parseInt(v);
                case "--scale" -> scale = Integer.parseInt(v);
                case "--out" -> out = v;
                default -> throw new IllegalArgumentException("unknown option " + args[i]);
            }
        }
        AtlasCore core = new AtlasCore(seed);
        int w = size / scale;
        int x0 = cx - size / 2, z0 = cz - size / 2;
        final int sc = scale;

        long start = System.nanoTime();
        ColumnSample[][] cols = new ColumnSample[w][w];
        IntStream.range(0, w).parallel().forEach(row -> {
            for (int col = 0; col < w; col++) {
                cols[row][col] = core.sample(x0 + col * sc, z0 + row * sc);
            }
        });
        double nsPerColumn = (System.nanoTime() - start) / (double) (w * w) * Runtime.getRuntime().availableProcessors();

        File dir = new File(out);
        dir.mkdirs();
        BufferedImage map = new BufferedImage(w, w, BufferedImage.TYPE_INT_RGB);
        BufferedImage biomes = new BufferedImage(w, w, BufferedImage.TYPE_INT_RGB);
        BufferedImage landforms = new BufferedImage(w, w, BufferedImage.TYPE_INT_RGB);
        for (int row = 0; row < w; row++) {
            for (int col = 0; col < w; col++) {
                ColumnSample c = cols[row][col];
                double shade = hillshade(cols, row, col, scale);
                map.setRGB(col, row, c.isOcean() ? water(c.height()) : shadeRgb(hypsometric(c.height()), shade));
                int biome = c.biome().previewColor();
                biomes.setRGB(col, row, c.isOcean() ? biome : shadeRgb(biome, shade));
                landforms.setRGB(col, row, c.isOcean() ? water(c.height()) : landformPixel(cols, row, col, shade));
            }
        }
        ImageIO.write(map, "png", new File(dir, "map.png"));
        ImageIO.write(biomes, "png", new File(dir, "biomes.png"));
        ImageIO.write(landforms, "png", new File(dir, "landforms.png"));
        ImageIO.write(section(core, x0, cz, w, scale), "png", new File(dir, "section.png"));
        stats(cols, nsPerColumn, size, scale, dir);
        census(cols, x0, z0, scale);
        macros(core, x0, z0, size);
    }

    /** Landform colour, shaded, with province borders drawn dark and escarpments red. */
    private static int landformPixel(ColumnSample[][] cols, int row, int col, double shade) {
        ColumnSample c = cols[row][col];
        int w = cols.length;
        ColumnSample right = cols[row][Math.min(w - 1, col + 1)], down = cols[Math.min(w - 1, row + 1)][col];
        if (macroSide(c) != macroSide(right) || macroSide(c) != macroSide(down)) {
            ColumnSample m = c.macro() != null ? c : right.macro() != null ? right : down;
            return m.macro().color(); // macro outline, in the macro's colour
        }
        boolean border = right.provinceX() != c.provinceX() || right.provinceZ() != c.provinceZ()
                || down.provinceX() != c.provinceX() || down.provinceZ() != c.provinceZ();
        if (border) {
            return 0x202020;
        }
        if (c.transition() == com.seaweed.grandatlas.core.Transition.ESCARPMENT) {
            return 0xB01818;
        }
        return shadeRgb(c.landform().color(), shade);
    }

    /** Which side of a macro edge a column is on: 0 outside (or no macro), 1 on the macro's own terrain. */
    private static int macroSide(ColumnSample c) {
        return c.macro() != null && c.macroInside() >= 0.5 ? 1 : 0;
    }

    /** Every macro whose centre is in the area, with its size. */
    private static void macros(AtlasCore core, int x0, int z0, int size) {
        double cell = com.seaweed.grandatlas.core.macro.MacroField.CELL;
        int c0x = (int) Math.floor(x0 / cell), c1x = (int) Math.floor((x0 + size) / cell);
        int c0z = (int) Math.floor(z0 / cell), c1z = (int) Math.floor((z0 + size) / cell);
        for (int cz = c0z; cz <= c1z; cz++) {
            for (int cx = c0x; cx <= c1x; cx++) {
                var site = core.macros().site(cx, cz);
                if (site == null || site.x() < x0 || site.x() > x0 + size || site.z() < z0 || site.z() > z0 + size) continue;
                System.out.printf("  macro %-14s centre %6.0f %6.0f  ~%.1f km across%n",
                        site.kind().id(), site.x(), site.z(), site.radius() * 2 / 1000);
            }
        }
    }

    /** Share of land per landform, and one sample location for each (to visit in game). */
    private static void census(ColumnSample[][] cols, int x0, int z0, int scale) {
        java.util.Map<String, int[]> counts = new java.util.TreeMap<>();
        java.util.Map<String, String> where = new java.util.HashMap<>();
        int land = 0;
        for (int row = 0; row < cols.length; row++) {
            for (int col = 0; col < cols.length; col++) {
                ColumnSample c = cols[row][col];
                if (c.isOcean()) continue;
                land++;
                String id = c.landform().id();
                counts.computeIfAbsent(id, k -> new int[1])[0]++;
                if (c.transition() == com.seaweed.grandatlas.core.Transition.INTERIOR) {
                    where.putIfAbsent(id, (x0 + col * scale) + " " + (z0 + row * scale));
                }
            }
        }
        final int total = land;
        counts.forEach((id, n) -> System.out.printf("  %-15s %5.1f%% of land   e.g. %s%n", id, 100.0 * n[0] / total,
                where.getOrDefault(id, "-")));
    }

    private static long parseSeed(String v) {
        try {
            return Long.parseLong(v);
        } catch (NumberFormatException e) {
            return v.hashCode();
        }
    }

    /** Lambert shading from the north-west, 45° up, at true vertical scale. */
    private static double hillshade(ColumnSample[][] cols, int row, int col, int scale) {
        int w = cols.length;
        double hl = cols[row][Math.max(0, col - 1)].height(), hr = cols[row][Math.min(w - 1, col + 1)].height();
        double hu = cols[Math.max(0, row - 1)][col].height(), hd = cols[Math.min(w - 1, row + 1)][col].height();
        double dx = (hr - hl) / (2.0 * scale), dz = (hd - hu) / (2.0 * scale);
        double nx = -dx, ny = 1, nz = -dz;
        double len = Math.sqrt(nx * nx + ny * ny + nz * nz);
        double lx = -0.5, ly = 0.7071, lz = -0.5;
        return Math.max(0, (nx * lx + ny * ly + nz * lz) / len);
    }

    private static int shadeRgb(int rgb, double shade) {
        double k = 0.35 + 0.85 * shade;
        int r = (int) Math.min(255, ((rgb >> 16) & 255) * k);
        int g = (int) Math.min(255, ((rgb >> 8) & 255) * k);
        int b = (int) Math.min(255, (rgb & 255) * k);
        return (r << 16) | (g << 8) | b;
    }

    private static final double[] STOPS = {AtlasCore.SEA_LEVEL, 100, 180, 300, 450, 600, 760, 900};
    private static final int[] COLORS = {0xC8C08A, 0x5A8C3C, 0x7FA050, 0xA89A62, 0x8C7458, 0x86807A, 0xC8C8C8, 0xFFFFFF};

    private static int hypsometric(double h) {
        for (int i = 1; i < STOPS.length; i++) {
            if (h < STOPS[i]) {
                return mix(COLORS[i - 1], COLORS[i], (h - STOPS[i - 1]) / (STOPS[i] - STOPS[i - 1]));
            }
        }
        return COLORS[COLORS.length - 1];
    }

    private static int water(double h) {
        double depth = Math.min(1, (AtlasCore.SEA_LEVEL - h) / 120.0);
        return mix(0x4F9FD8, 0x0A1F55, depth);
    }

    private static int mix(int a, int b, double t) {
        t = Math.max(0, Math.min(1, t));
        int r = (int) (((a >> 16) & 255) + t * (((b >> 16) & 255) - ((a >> 16) & 255)));
        int g = (int) (((a >> 8) & 255) + t * (((b >> 8) & 255) - ((a >> 8) & 255)));
        int bl = (int) ((a & 255) + t * ((b & 255) - (a & 255)));
        return (r << 16) | (g << 8) | bl;
    }

    private static BufferedImage section(AtlasCore core, int x0, int z, int w, int scale) {
        int rows = (AtlasCore.MAX_Y - AtlasCore.MIN_Y) / 2;
        BufferedImage img = new BufferedImage(w, rows, BufferedImage.TYPE_INT_RGB);
        for (int col = 0; col < w; col++) {
            int x = x0 + col * scale;
            ColumnSample c = core.sample(x, z);
            int top = BiomeKindColors.top(c.biome());
            for (int row = 0; row < rows; row++) {
                int y = AtlasCore.MAX_Y - row * 2;
                int rgb;
                if (core.density(c, x, y, z, true) > 0) {
                    rgb = y > c.height() - 6 ? top : y < 0 ? 0x4A4A55 : 0x7A7A7A;
                } else if (y <= AtlasCore.SEA_LEVEL) {
                    rgb = 0x2F6FC0;
                } else {
                    rgb = mix(0x9FC8F0, 0x1E3A6A, (double) y / AtlasCore.MAX_Y);
                }
                if (y == AtlasCore.SEA_LEVEL - 1 || y == 192 || y % 128 == 0) {
                    rgb = mix(rgb, 0xFFFFFF, 0.35);
                }
                img.setRGB(col, row, rgb);
            }
        }
        return img;
    }

    /** Grass-ish tops for the section view. */
    private static final class BiomeKindColors {
        static int top(BiomeKind b) {
            return switch (b) {
                case DESERT, BEACH -> 0xE0D090;
                case BADLANDS, WOODED_BADLANDS, ERODED_BADLANDS -> 0xC05A2A;
                case SALT_FLAT -> 0xFAFAFA;
                case SNOWY_PLAINS, SNOWY_SLOPES, SNOWY_BEACH, FROZEN_PEAKS, JAGGED_PEAKS, GROVE, SNOWY_TAIGA -> 0xF4F8FF;
                case STONY_PEAKS, STONY_SHORE, WINDSWEPT_HILLS -> 0x9A9A9A;
                default -> 0x5E9E3A;
            };
        }
    }

    private static void stats(ColumnSample[][] cols, double nsPerColumn, int size, int scale, File dir) {
        int n = cols.length * cols.length, land = 0, mountains = 0;
        double[] heights = new double[n];
        int i = 0;
        for (ColumnSample[] row : cols) {
            for (ColumnSample c : row) {
                heights[i++] = c.height();
                if (!c.isOcean()) land++;
                if (c.mountain() > 0.5) mountains++;
            }
        }
        Arrays.sort(heights);
        System.out.printf("Area %d x %d blocks at 1:%d -> %s%n", size, size, scale, dir.getAbsolutePath());
        System.out.printf("Land %.1f%%   mountain cores %.1f%%%n", 100.0 * land / n, 100.0 * mountains / n);
        System.out.printf("Height  min %.0f  p10 %.0f  median %.0f  p90 %.0f  p99 %.0f  max %.0f%n",
                heights[0], heights[n / 10], heights[n / 2], heights[n * 9 / 10], heights[n * 99 / 100], heights[n - 1]);
        System.out.printf("Column cost ~%.1f us (single thread equivalent)%n", nsPerColumn / 1000);
    }
}
