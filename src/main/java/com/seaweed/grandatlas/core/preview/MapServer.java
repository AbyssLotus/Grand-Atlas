package com.seaweed.grandatlas.core.preview;

import com.seaweed.grandatlas.core.AtlasCore;
import com.seaweed.grandatlas.core.BiomeKind;
import com.seaweed.grandatlas.core.ColumnSample;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.stream.IntStream;
import javax.imageio.ImageIO;

/**
 * A live map of the terrain core in a browser (no Minecraft): pan and zoom over hillshaded terrain
 * or biomes, click a spot for what's there. Tiles are 256 px, rendered on demand at 1 to 128
 * blocks a pixel and kept in memory.
 *
 * <p>Run with {@code gradlew mapServer -Pargs="--seed -2953816398943392853 --port 8765"}, then open
 * http://localhost:8765. The seed is the core seed ({@code /grandatlas seed} in game).
 */
public final class MapServer {
    private static final int TILE = 256;
    private final AtlasCore core;
    private final Map<String, byte[]> tiles = java.util.Collections.synchronizedMap(new LinkedHashMap<>(256, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, byte[]> e) {
            return size() > 3000;
        }
    });

    private MapServer(long seed) {
        this.core = new AtlasCore(seed);
    }

    public static void main(String[] args) throws IOException {
        long seed = -2953816398943392853L;
        int port = 8765;
        for (int i = 0; i + 1 < args.length; i += 2) {
            switch (args[i]) {
                case "--seed" -> seed = Long.parseLong(args[i + 1]);
                case "--port" -> port = Integer.parseInt(args[i + 1]);
                default -> throw new IllegalArgumentException("unknown option " + args[i]);
            }
        }
        MapServer map = new MapServer(seed);
        HttpServer http = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 64);
        http.setExecutor(Executors.newFixedThreadPool(Math.max(2, Runtime.getRuntime().availableProcessors() - 2)));
        http.createContext("/", map::page);
        http.createContext("/tile", map::tile);
        http.createContext("/info", map::info);
        http.start();
        System.out.println("Grand Atlas map on http://localhost:" + port + " (core seed " + seed + ")");
    }

    // ---- requests

    private void page(HttpExchange ex) throws IOException {
        try (InputStream in = MapServer.class.getResourceAsStream("/map/index.html")) {
            send(ex, 200, "text/html; charset=utf-8", in == null ? "missing map page".getBytes() : in.readAllBytes());
        }
    }

    private void tile(HttpExchange ex) throws IOException {
        Map<String, String> q = query(ex);
        String layer = q.getOrDefault("layer", "terrain");
        int level = Math.max(0, Math.min(7, Integer.parseInt(q.get("l"))));
        int tx = Integer.parseInt(q.get("x")), tz = Integer.parseInt(q.get("z"));
        String key = layer + "/" + level + "/" + tx + "/" + tz;
        byte[] png = tiles.get(key);
        if (png == null) {
            png = render(layer, level, tx, tz);
            tiles.put(key, png);
        }
        ex.getResponseHeaders().add("Cache-Control", "max-age=3600");
        send(ex, 200, "image/png", png);
    }

    private void info(HttpExchange ex) throws IOException {
        Map<String, String> q = query(ex);
        int x = (int) Math.floor(Double.parseDouble(q.get("x"))), z = (int) Math.floor(Double.parseDouble(q.get("z")));
        ColumnSample c = core.column(x, z);
        StringBuilder sb = new StringBuilder("{");
        field(sb, "x", x);
        field(sb, "z", z);
        field(sb, "surface", c.surfaceY());
        field(sb, "water", Double.isNaN(c.water()) ? null : round(c.water()));
        field(sb, "biome", c.biome().id());
        field(sb, "landform", c.landform().id());
        field(sb, "zone", c.zone().id());
        field(sb, "macro", c.macro() == null ? null : c.macro().id() + (c.macroZone() == null ? "" : " / " + c.macroZone().id()));
        field(sb, "site", c.site() == null ? null : c.site().kind().id());
        field(sb, "feature", c.micro() == null || !c.micro().present() ? null : c.micro().kind.id());
        field(sb, "shore", c.shore() == null ? null : c.shore().kind().name().toLowerCase(Locale.ROOT));
        field(sb, "temperature", round(c.temperature()));
        field(sb, "humidity", round(c.humidity()));
        field(sb, "continentalness", round(c.continentalness()));
        field(sb, "roughness", round(c.roughness()));
        sb.setLength(sb.length() - 1);
        sb.append('}');
        send(ex, 200, "application/json", sb.toString().getBytes(StandardCharsets.UTF_8));
    }

    // ---- rendering

    private byte[] render(String layer, int level, int tx, int tz) throws IOException {
        int s = 1 << level, n = TILE + 2;
        long x0 = (long) tx * TILE * s - s, z0 = (long) tz * TILE * s - s;
        ColumnSample[][] cols = new ColumnSample[n][n];
        IntStream.range(0, n).parallel().forEach(j -> {
            for (int i = 0; i < n; i++) cols[j][i] = core.column((int) (x0 + (long) i * s), (int) (z0 + (long) j * s));
        });
        BufferedImage img = new BufferedImage(TILE, TILE, BufferedImage.TYPE_INT_RGB);
        boolean biomes = layer.equals("biome"), contours = layer.equals("height");
        double step = contourStep(level);
        for (int j = 1; j <= TILE; j++) {
            for (int i = 1; i <= TILE; i++) {
                ColumnSample c = cols[j][i];
                double gx = (cols[j][i + 1].height() - cols[j][i - 1].height()) / (2.0 * s);
                double gz = (cols[j + 1][i].height() - cols[j - 1][i].height()) / (2.0 * s);
                double shade = Math.max(0.45, Math.min(1.25, 0.9 + 0.55 * (-gx - gz) / Math.sqrt(1 + gx * gx + gz * gz)));
                boolean wet = !Double.isNaN(c.water()) && c.water() > c.height();
                boolean sea = c.height() < AtlasCore.SEA_LEVEL - 0.5 && !wet;
                int rgb;
                if (wet || sea) {
                    double depth = (wet ? c.water() : AtlasCore.SEA_LEVEL) - c.height();
                    rgb = mix(0x4F8FE0, 0x123A7A, Math.min(1, depth / 40));
                    if (biomes && sea) rgb = mix(rgb, c.biome().previewColor(), 0.35);
                } else if (contours) {
                    // Elevation tint, lightly shaded, and a line wherever a contour passes between this
                    // pixel and the next one right or down; every fifth line darker.
                    rgb = shadeRgb(hypsometric(c.height()), 0.85 + 0.15 * shade);
                    double h = c.height();
                    long here = (long) Math.floor(h / step);
                    long right = (long) Math.floor(cols[j][i + 1].height() / step), down = (long) Math.floor(cols[j + 1][i].height() / step);
                    if (right != here || down != here) {
                        long line = Math.max(here, Math.max(right, down));
                        rgb = mix(rgb, 0x000000, line % 5 == 0 ? 0.75 : 0.4);
                    }
                } else if (biomes) {
                    rgb = shadeRgb(c.biome().previewColor(), shade);
                } else {
                    rgb = shadeRgb(hypsometric(c.height()), shade);
                }
                img.setRGB(i - 1, j - 1, rgb);
            }
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, "png", out);
        return out.toByteArray();
    }

    /** Contour interval at a zoom: 5 blocks close up, wider out so the lines stay apart. */
    static double contourStep(int level) {
        return level <= 1 ? 5 : level == 2 ? 10 : level == 3 ? 25 : level <= 5 ? 50 : 100;
    }

    private static final double[] STOPS = {AtlasCore.SEA_LEVEL, 100, 180, 300, 450, 600, 760, 900};
    private static final int[] COLORS = {0xC8C08A, 0x5A8C3C, 0x7FA050, 0xA89A62, 0x8C7458, 0x86807A, 0xC8C8C8, 0xFFFFFF};

    private static int hypsometric(double h) {
        if (h <= STOPS[0]) return COLORS[0];
        for (int k = 1; k < STOPS.length; k++) {
            if (h < STOPS[k]) return mix(COLORS[k - 1], COLORS[k], (h - STOPS[k - 1]) / (STOPS[k] - STOPS[k - 1]));
        }
        return COLORS[COLORS.length - 1];
    }

    private static int mix(int a, int b, double t) {
        t = Math.max(0, Math.min(1, t));
        int r = (int) Math.round(((a >> 16) & 255) * (1 - t) + ((b >> 16) & 255) * t);
        int g = (int) Math.round(((a >> 8) & 255) * (1 - t) + ((b >> 8) & 255) * t);
        int bl = (int) Math.round((a & 255) * (1 - t) + (b & 255) * t);
        return (r << 16) | (g << 8) | bl;
    }

    private static int shadeRgb(int rgb, double shade) {
        int r = (int) Math.min(255, ((rgb >> 16) & 255) * shade), g = (int) Math.min(255, ((rgb >> 8) & 255) * shade);
        int b = (int) Math.min(255, (rgb & 255) * shade);
        return (r << 16) | (g << 8) | b;
    }

    // ---- plumbing

    private static Map<String, String> query(HttpExchange ex) {
        Map<String, String> q = new HashMap<>();
        String raw = ex.getRequestURI().getRawQuery();
        if (raw == null) return q;
        for (String part : raw.split("&")) {
            int eq = part.indexOf('=');
            if (eq > 0) q.put(part.substring(0, eq), java.net.URLDecoder.decode(part.substring(eq + 1), StandardCharsets.UTF_8));
        }
        return q;
    }

    private static void send(HttpExchange ex, int code, String type, byte[] body) throws IOException {
        ex.getResponseHeaders().add("Content-Type", type);
        ex.sendResponseHeaders(code, body.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(body);
        }
    }

    private static double round(double v) {
        return Math.round(v * 1000) / 1000.0;
    }

    private static void field(StringBuilder sb, String name, Object v) {
        sb.append('"').append(name).append("\":");
        if (v == null) sb.append("null");
        else if (v instanceof Number) sb.append(v);
        else sb.append('"').append(v.toString().replace("\"", "'")).append('"');
        sb.append(',');
    }
}
