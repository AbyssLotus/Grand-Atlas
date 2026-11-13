import java.io.*;
import java.util.*;
import java.util.zip.GZIPOutputStream;

/**
 * Converts the approved third-party asset packs (docs/CREDITS.md) into Grand Atlas's packed asset
 * files under src/main/resources/data/grandatlas/atlas_assets/.
 *
 * <pre>
 *   javac -d build/assettools tools/assets/*.java
 *   java -Xmx6g -cp build/assettools Import assets-src src/main/resources/data/grandatlas/atlas_assets
 * </pre>
 *
 * Sources (not in the repository's resources; download them into assets-src/ first):
 * <ul>
 *   <li>assets-src/h2co3/h2co3_1k_treepack.schematic: "1k - Treepack" by H2CO3 (FeH2). One legacy
 *       schematic holding about a thousand trees on a grid; split here into single trees.</li>
 *   <li>assets-src/godtree/*.schematic: "The God Tree-Pack" by Plutouthere.</li>
 *   <li>assets-src/dannypan/schem/**: "Landscaping Mega Schematic Pack" by Dannypan (trees in it by
 *       paleozoey).</li>
 * </ul>
 * The packed format is the mod's own (a palette of block states, then per asset its columns of
 * blocks), not a schematic: H2CO3 asks that the schematics not be passed on as they are.
 */
public final class Import {
    /** One asset: sparse blocks by column. */
    static final class Asset {
        String name, tags;
        int sx, sy, sz, baseY;
        // parallel lists, sorted by (x, z, y) on write
        int n;
        int[] xs = new int[1024], ys = new int[1024], zs = new int[1024], st = new int[1024];

        void add(int x, int y, int z, int state) {
            if (n == xs.length) { xs = Arrays.copyOf(xs, n * 2); ys = Arrays.copyOf(ys, n * 2); zs = Arrays.copyOf(zs, n * 2); st = Arrays.copyOf(st, n * 2); }
            xs[n] = x; ys[n] = y; zs[n] = z; st[n] = state; n++;
        }
    }

    static final List<String> palette = new ArrayList<>();
    static final Map<String, Integer> paletteIndex = new HashMap<>();

    static int state(String s) {
        Integer i = paletteIndex.get(s);
        if (i == null) { i = palette.size(); palette.add(s); paletteIndex.put(s, i); }
        return i;
    }

    // ---- legacy (pre-1.13) block ids

    static final String[] WOODS = {"oak", "spruce", "birch", "jungle"}, WOODS2 = {"acacia", "dark_oak"};
    static final String[] COLOURS = {"white", "orange", "magenta", "light_blue", "yellow", "lime", "pink", "gray", "light_gray", "cyan", "purple", "blue",
            "brown", "green", "red", "black"};
    static final String[] PLANKS = {"oak", "spruce", "birch", "jungle", "acacia", "dark_oak"};

    /** A legacy block as a modern block state, or null to leave it out. {@code blossom}: wool stands for blossom. */
    static String legacy(int id, int d, boolean blossom) {
        switch (id) {
            case 1: return d == 1 ? "minecraft:granite" : d == 3 ? "minecraft:diorite" : d == 5 ? "minecraft:andesite" : "minecraft:stone";
            case 2: return "minecraft:grass_block";
            case 3: return d == 1 ? "minecraft:coarse_dirt" : d == 2 ? "minecraft:podzol" : "minecraft:dirt";
            case 4: return "minecraft:cobblestone";
            case 5: return "minecraft:" + PLANKS[Math.min(5, d)] + "_planks";
            case 12: return "minecraft:sand";
            case 17: return log(WOODS[d & 3], d >> 2);
            case 162: return log(WOODS2[d & 1], d >> 2);
            case 18: return "minecraft:" + WOODS[d & 3] + "_leaves[persistent=true]";
            case 161: return "minecraft:" + WOODS2[d & 1] + "_leaves[persistent=true]";
            case 35: return blossom ? (d == 2 || d == 10 ? "minecraft:flowering_azalea_leaves[persistent=true]" : "minecraft:cherry_leaves[persistent=true]") : null;
            case 48: return "minecraft:mossy_cobblestone";
            case 78: return "minecraft:snow[layers=" + Math.min(8, (d & 7) + 1) + "]";
            case 85: return "minecraft:oak_fence";
            case 188: return "minecraft:spruce_fence";
            case 189: return "minecraft:birch_fence";
            case 190: return "minecraft:jungle_fence";
            case 191: return "minecraft:dark_oak_fence";
            case 192: return "minecraft:acacia_fence";
            case 99: return d == 15 || d == 10 ? "minecraft:mushroom_stem" : "minecraft:brown_mushroom_block";
            case 100: return d == 15 || d == 10 ? "minecraft:mushroom_stem" : "minecraft:red_mushroom_block";
            case 106: return null; // vines need faces; left out
            case 126: return "minecraft:" + PLANKS[Math.min(5, d & 7)] + "_slab[type=" + ((d & 8) != 0 ? "top" : "bottom") + "]";
            case 125: return "minecraft:" + PLANKS[Math.min(5, d & 7)] + "_planks";
            default: return null;
        }
    }

    static String log(String wood, int axisBits) {
        return axisBits == 3 ? "minecraft:" + wood + "_wood" : "minecraft:" + wood + "_log[axis=" + (axisBits == 0 ? "y" : axisBits == 1 ? "x" : "z") + "]";
    }

    /** Blocks that make a tree a tree: counted when judging whether a legacy tree is natural. */
    static boolean natural(int id) {
        return id == 17 || id == 162 || id == 18 || id == 161 || id == 85 || (id >= 188 && id <= 192) || id == 99 || id == 100 || id == 78 || id == 106
                || id == 2 || id == 3 || id == 48 || id == 4 || id == 1 || id == 5 || id == 126 || id == 125;
    }

    /** Decoration round a tree's foot that isn't part of it (flowers, grass, saplings, signs, buttons, pots). */
    static boolean litter(int id) {
        return id == 31 || id == 38 || id == 37 || id == 175 || id == 6 || id == 39 || id == 40 || id == 63 || id == 68 || id == 77 || id == 143 || id == 140
                || id == 144 || id == 166 || id == 127 || id == 171 || id == 118;
    }

    // ---- fences: connections baked from the asset's own neighbours

    static void bakeFences(Asset a) {
        Map<Long, Integer> at = new HashMap<>();
        for (int i = 0; i < a.n; i++) at.put(key(a.xs[i], a.ys[i], a.zs[i]), a.st[i]);
        int[][] dirs = {{0, -1}, {1, 0}, {0, 1}, {-1, 0}};
        String[] names = {"north", "east", "south", "west"};
        for (int i = 0; i < a.n; i++) {
            String s = palette.get(a.st[i]);
            if (!s.endsWith("_fence")) continue;
            StringBuilder sb = new StringBuilder(s).append('[');
            for (int k = 0; k < 4; k++) {
                Integer nb = at.get(key(a.xs[i] + dirs[k][0], a.ys[i], a.zs[i] + dirs[k][1]));
                boolean join = false;
                if (nb != null) {
                    String ns = palette.get(nb);
                    join = ns.contains("_fence") || ns.contains("_log") || ns.contains("_wood") || ns.contains("planks") || ns.contains("stone") || ns.contains("dirt");
                }
                sb.append(k > 0 ? "," : "").append(names[k]).append('=').append(join);
            }
            a.st[i] = state(sb.append(']').toString());
        }
    }

    static long key(int x, int y, int z) {
        return ((long) (x + 2048) << 40) | ((long) (y + 2048) << 20) | (z + 2048);
    }

    // ---- sources

    /** The 1k treepack: one schematic, trees standing on a ground slab; split by connected footprints. */
    static List<Asset> h2co3(File f) throws IOException {
        Map<String, Object> d = Nbt.read(new FileInputStream(f));
        int W = num(d, "Width"), L = num(d, "Length"), H = num(d, "Height");
        byte[] B = (byte[]) d.get("Blocks"), D = (byte[]) d.get("Data");
        final int GROUND = 3; // bedrock, dirt, dirt, then the grass or marker floor
        // footprint: columns with tree blocks above the floor
        int[] comp = new int[W * L];
        boolean[] occ = new boolean[W * L];
        for (int y = GROUND + 1; y < H; y++) {
            int base = y * L * W;
            for (int i = 0; i < L * W; i++) {
                int id = B[base + i] & 255;
                if (id != 0 && !litter(id)) occ[i] = true;
            }
        }
        List<Asset> out = new ArrayList<>();
        int next = 0, dropped = 0, unnatural = 0, planked = 0;
        int[] stack = new int[W * L];
        for (int start = 0; start < W * L; start++) {
            if (!occ[start] || comp[start] != 0) continue;
            int id = ++next, sp = 0, minX = W, maxX = 0, minZ = L, maxZ = 0;
            stack[sp++] = start;
            comp[start] = id;
            while (sp > 0) {
                int c = stack[--sp], x = c % W, z = c / W;
                minX = Math.min(minX, x); maxX = Math.max(maxX, x); minZ = Math.min(minZ, z); maxZ = Math.max(maxZ, z);
                for (int dz = -1; dz <= 1; dz++) for (int dx = -1; dx <= 1; dx++) {
                    int nx = x + dx, nz = z + dz;
                    if (nx < 0 || nz < 0 || nx >= W || nz >= L) continue;
                    int nc = nz * W + nx;
                    if (occ[nc] && comp[nc] == 0) { comp[nc] = id; stack[sp++] = nc; }
                }
            }
            // gather
            Asset a = new Asset();
            int[] logs = new int[6], leaves = new int[6];
            int nat = 0, all = 0, snow = 0, top = 0, planks = 0;
            for (int z = minZ; z <= maxZ; z++) for (int x = minX; x <= maxX; x++) {
                if (comp[z * W + x] != id) continue;
                for (int y = GROUND + 1; y < H; y++) {
                    int i = (y * L + z) * W + x, bid = B[i] & 255, bd = D[i] & 15;
                    if (bid == 0 || litter(bid)) continue;
                    all++;
                    if (natural(bid)) nat++;
                    if (bid == 5 || bid == 125 || bid == 126 || bid == 53 || bid == 134 || bid == 135 || bid == 136 || bid == 163 || bid == 164) planks++;
                    if (bid == 17) logs[bd & 3]++; else if (bid == 162) logs[4 + (bd & 1)]++;
                    else if (bid == 18) leaves[bd & 3]++; else if (bid == 161) leaves[4 + (bd & 1)]++;
                    else if (bid == 78) snow++;
                    String s = legacy(bid, bd, false);
                    if (s == null) continue;
                    a.add(x - minX, y - GROUND - 1, z - minZ, state(s));
                    top = Math.max(top, y - GROUND - 1);
                }
            }
            int nLogs = Arrays.stream(logs).sum(), nLeaves = Arrays.stream(leaves).sum();
            if (all < 25 || nLogs < 3) { dropped++; continue; }
            if (nat < 0.93 * all) { unnatural++; continue; }
            if (planks > 0) { planked++; continue; } // no built wood in a tree (the user, 2026-11-10: "any tree structures that include planks get rid of")
            a.sx = maxX - minX + 1; a.sz = maxZ - minZ + 1; a.sy = top + 1; a.baseY = 0;
            String lw = name(argmax(logs)), lf = nLeaves < 0.15 * nLogs ? "bare" : name(argmax(leaves));
            String size = a.sy < 11 ? "small" : a.sy < 22 ? "medium" : a.sy < 40 ? "large" : "giant";
            a.name = "h2co3/" + lw + "_" + lf + "_" + out.size();
            a.tags = "tree," + size + ",wood_" + lw + ",leaf_" + lf + (snow > 8 ? ",snowy" : "");
            bakeFences(a);
            out.add(a);
        }
        System.out.println("h2co3: " + next + " footprints, " + out.size() + " trees kept, " + dropped + " scraps dropped, " + unnatural + " left out (not natural blocks), " + planked + " left out (planks, wood slabs or stairs)");
        return out;
    }

    static String name(int i) {
        return i < 4 ? WOODS[i] : WOODS2[i - 4];
    }

    static int argmax(int[] v) {
        int b = 0;
        for (int i = 1; i < v.length; i++) if (v[i] > v[b]) b = i;
        return b;
    }

    /** The god trees: one legacy schematic each; variants -1 green, -2 blossom (wool), -3 autumn (terracotta, left out), -4 bare. */
    static List<Asset> god(File dir) throws IOException {
        List<Asset> out = new ArrayList<>();
        File[] files = dir.listFiles((dd, n) -> n.endsWith(".schematic"));
        Arrays.sort(files);
        for (File f : files) {
            String base = f.getName().replace(".schematic", "").replace("pluto_", "").toLowerCase(Locale.ROOT);
            int variant = base.charAt(base.length() - 1) - '0';
            if (variant == 3) continue; // autumn foliage in terracotta: no natural block stands for it
            Map<String, Object> d = Nbt.read(new FileInputStream(f));
            int W = num(d, "Width"), L = num(d, "Length"), H = num(d, "Height");
            byte[] B = (byte[]) d.get("Blocks"), D = (byte[]) d.get("Data");
            Asset a = new Asset();
            int minY = H, wool = 0, built = 0;
            for (int i = 0; i < B.length; i++) {
                int id = B[i] & 255;
                if (id == 35) wool++;
                if (id == 5 || id == 125 || id == 126 || id == 43 || id == 44) built++;
            }
            if (built > 0) { System.out.println("  god tree left out (planks or slabs): " + f.getName()); continue; }
            boolean blossom = variant == 2 || wool > 500;
            // the trunk's foot: the lowest level holding logs near the middle
            for (int y = 0; y < H; y++) for (int z = 0; z < L; z++) for (int x = 0; x < W; x++) {
                int i = (y * L + z) * W + x, id = B[i] & 255;
                if (id == 0 || litter(id)) continue;
                String s = legacy(id, D[i] & 15, blossom);
                if (s == null) continue;
                a.add(x, y, z, state(s));
                if (id == 17 || id == 162) minY = Math.min(minY, y);
            }
            a.sx = W; a.sy = H; a.sz = L; a.baseY = minY + 2; // sunk a little: the roots bite into the ground
            String kind = base.startsWith("willow") ? "willow" : base.startsWith("jungle") ? "jungle" : base.startsWith("dead") ? "dead"
                    : base.startsWith("bonsai") ? "bonsai" : "oak";
            a.name = "god/" + base;
            a.tags = "godtree," + kind + (variant == 4 || kind.equals("dead") ? ",bare" : blossom ? ",blossom" : ",green");
            bakeFences(a);
            out.add(a);
        }
        System.out.println("god trees: " + out.size());
        return out;
    }

    /** A Sponge schematic (v2 or v3): palette of modern block states. */
    static Asset sponge(File f, String name, String tags, double sink) throws IOException {
        Map<String, Object> d = Nbt.read(new FileInputStream(f));
        if (d.containsKey("Schematic")) d = (Map<String, Object>) d.get("Schematic");
        int W = num(d, "Width"), L = num(d, "Length"), H = num(d, "Height");
        Map<String, Object> pal;
        byte[] data;
        if (d.containsKey("Blocks")) {
            Map<String, Object> b = (Map<String, Object>) d.get("Blocks");
            pal = (Map<String, Object>) b.get("Palette");
            data = (byte[]) b.get("Data");
        } else {
            pal = (Map<String, Object>) d.get("Palette");
            data = (byte[]) d.get("BlockData");
        }
        int[] map = new int[pal.size() + 1];
        Arrays.fill(map, -1);
        Set<Integer> plankIds = new HashSet<>();
        boolean hasPlanks = false;
        for (Map.Entry<String, Object> e : pal.entrySet()) {
            String s = e.getKey();
            int id = ((Number) e.getValue()).intValue();
            if (id >= map.length) map = Arrays.copyOf(map, id + 1);
            if (s.equals("minecraft:air") || s.equals("minecraft:cave_air") || s.equals("minecraft:void_air") || s.contains("structure_void")) { map[id] = -1; continue; }
            // No loose water or lava: a sheet of it at the foot of a build would be carried down every slope.
            if (s.startsWith("minecraft:water") || s.startsWith("minecraft:lava") || s.contains("bubble_column")) { map[id] = -1; continue; }
            s = s.replace("waterlogged=true", "waterlogged=false");
            if (s.contains("_leaves")) s = leaves(s);
            if (s.contains("_planks") || s.contains("_stairs") && !s.contains("stone") && !s.contains("brick") && !s.contains("andesite") && !s.contains("diorite") && !s.contains("granite") && !s.contains("deepslate") && !s.contains("tuff") && !s.contains("blackstone") && !s.contains("sandstone") && !s.contains("quartz")) plankIds.add(id);
            map[id] = state(s);
        }
        Asset a = new Asset();
        int pos = 0, idx = 0;
        while (pos < data.length) {
            int v = 0, shift = 0;
            while (true) {
                int b = data[pos++] & 255;
                v |= (b & 127) << shift;
                if ((b & 128) == 0) break;
                shift += 7;
            }
            int s = v < map.length ? map[v] : -1;
            if (plankIds.contains(v)) hasPlanks = true;
            if (s >= 0) a.add(idx % W, idx / (W * L), (idx / W) % L, s);
            idx++;
        }
        a.sx = W; a.sy = H; a.sz = L;
        if (hasPlanks) { a.n = 0; return a; } // a decorated piece whose tree is built with planks: left out
        core(a);
        a.baseY = (int) Math.round(sink < 1 ? sink * H : sink);
        a.name = name;
        a.tags = tags;
        return a;
    }

    /**
     * A big rock's inside, more than two blocks from any face, becomes one kind of stone (its most
     * common full block): nobody sees it until they dig, and whole columns of one block pack into
     * single runs. The outer two blocks keep the builder's mix exactly.
     */
    static void core(Asset a) {
        int W = a.sx, H = a.sy, L = a.sz;
        if ((long) W * H * L > 400_000_000L || a.n < 4000) return;
        byte[] depth = new byte[W * H * L]; // 0 empty, 1 block, then 2 = face, 3 = one in, 4 = deeper
        for (int i = 0; i < a.n; i++) depth[(a.ys[i] * L + a.zs[i]) * W + a.xs[i]] = 1;
        int[] step = {1, -1, W, -W, W * L, -W * L};
        for (int pass = 2; pass <= 3; pass++) {
            for (int i = 0; i < a.n; i++) {
                int x = a.xs[i], y = a.ys[i], z = a.zs[i], c = (y * L + z) * W + x;
                if (depth[c] != 1) continue;
                boolean edge = x == 0 || y == 0 || z == 0 || x == W - 1 || y == H - 1 || z == L - 1;
                boolean hit = false;
                if (pass == 2) {
                    hit = edge && y != 0; // the bottom face sits in the ground: not a face
                    for (int k = 0; k < 6 && !hit; k++) {
                        int nx = x + (k == 0 ? 1 : k == 1 ? -1 : 0), nz = z + (k == 2 ? 1 : k == 3 ? -1 : 0), ny = y + (k == 4 ? 1 : k == 5 ? -1 : 0);
                        if (nx < 0 || ny < 0 || nz < 0 || nx >= W || ny >= H || nz >= L) continue;
                        hit = depth[c + step[k]] == 0;
                    }
                } else {
                    for (int k = 0; k < 6 && !hit; k++) {
                        int nx = x + (k == 0 ? 1 : k == 1 ? -1 : 0), nz = z + (k == 2 ? 1 : k == 3 ? -1 : 0), ny = y + (k == 4 ? 1 : k == 5 ? -1 : 0);
                        if (nx < 0 || ny < 0 || nz < 0 || nx >= W || ny >= H || nz >= L) continue;
                        hit = depth[c + step[k]] == 2;
                    }
                }
                if (hit) depth[c] = (byte) -pass; // marked after the sweep, so a pass reads only the last one's marks
            }
            for (int i = 0; i < depth.length; i++) if (depth[i] == -pass) depth[i] = (byte) pass;
        }
        Map<Integer, Integer> count = new HashMap<>();
        for (int i = 0; i < a.n; i++) {
            String s = palette.get(a.st[i]);
            if (s.contains("[") || s.contains("leaves") || s.contains("grass") || s.contains("ore")) continue;
            count.merge(a.st[i], 1, Integer::sum);
        }
        if (count.isEmpty()) return;
        int fill = Collections.max(count.entrySet(), Map.Entry.comparingByValue()).getKey();
        for (int i = 0; i < a.n; i++) if (depth[(a.ys[i] * L + a.zs[i]) * W + a.xs[i]] == 1) a.st[i] = fill;
    }

    /** Leaves never decay: persistent, whatever the source said. */
    static String leaves(String s) {
        String id = s.contains("[") ? s.substring(0, s.indexOf('[')) : s;
        boolean water = s.contains("waterlogged=true");
        return id + "[persistent=true" + (water ? ",waterlogged=true" : "") + "]";
    }

    /** The landscaping pack: a spread of each kind and colouring. {folder under schem/, how many, tags, how far to sink}. */
    static List<Asset> rocks(File root) throws IOException {
        Object[][] pick = {
                {"Outcrops/Bare_Rock", 24, "outcrop,bare", 1.0}, {"Outcrops/Bare_Grass", 24, "outcrop,grass", 1.0}, {"Outcrops/Desert", 20, "outcrop,desert", 1.0},
                {"Outcrops/Plains", 15, "outcrop,plains", 1.0}, {"Outcrops/Forest_No_Trees", 15, "outcrop,forest", 1.0},
                {"Outcrops/Taiga_No_Trees", 15, "outcrop,taiga", 1.0}, {"Outcrops/Savanna_No_Trees", 15, "outcrop,savanna", 1.0},
                {"Outcrops/Snowy_Plains", 15, "outcrop,snowy", 1.0}, {"Outcrops/Jungle", 10, "outcrop,jungle", 1.0},
                {"Outcrops/Cherry_Grove_No_Trees", 10, "outcrop,cherry", 1.0}, {"Outcrops/Black_Sand_Coastal", 8, "outcrop,coast", 1.0},
                {"Natural_Arches/Bare", 14, "arch,bare", 1.0}, {"Natural_Arches/Grass-topped_no_decoration", 14, "arch,grass", 1.0},
                {"Natural_Arches/Desert", 14, "arch,desert", 1.0}, {"Natural_Arches/Plains", 8, "arch,plains", 1.0},
                {"Natural_Arches/Forest", 7, "arch,forest", 1.0}, {"Natural_Arches/Taiga", 7, "arch,taiga", 1.0},
                {"Natural_Arches/Savanna", 7, "arch,savanna", 1.0}, {"Natural_Arches/Snowy_Plains", 7, "arch,snowy", 1.0},
                {"Natural_Arches/Jungle", 6, "arch,jungle", 1.0},
                {"Spires/Stone", 14, "spire,stone", 3.0}, {"Spires/Dark_Stone", 14, "spire,dark", 3.0}, {"Spires/Reddish", 14, "spire,red", 3.0},
                {"Spires/Terracotta", 14, "spire,terracotta", 3.0}, {"Spires/White_Stone", 14, "spire,white", 3.0},
                {"Pillar_Mountains/Plain", 16, "pillar,plain", 4.0}, {"Pillar_Mountains/Lush", 16, "pillar,lush", 4.0},
                {"Pillar_Mountains/Overgrown", 16, "pillar,overgrown", 4.0},
                {"Bladed_Fins/Stone", 14, "fin,stone", 2.0}, {"Bladed_Fins/Dark_Stone", 14, "fin,dark", 2.0}, {"Bladed_Fins/Reddish", 14, "fin,red", 2.0},
                {"Bladed_Fins/Terracotta", 14, "fin,terracotta", 2.0}, {"Bladed_Fins/White_Stone", 14, "fin,white", 2.0},
                {"Spike_Cluster/Stone", 12, "spikes,stone", 0.3}, {"Spike_Cluster/Dark_Stone", 12, "spikes,dark", 0.3}, {"Spike_Cluster/Reddish", 12, "spikes,red", 0.3},
                {"Spike_Cluster/Terracotta", 12, "spikes,terracotta", 0.3}, {"Spike_Cluster/White_Stone", 12, "spikes,white", 0.3},
                {"Pandora_Rings/Stone", 8, "ring,stone", 2.0}, {"Pandora_Rings/Dark_Stone", 8, "ring,dark", 2.0}, {"Pandora_Rings/White_Stone", 8, "ring,white", 2.0},
        };
        List<Asset> out = new ArrayList<>();
        Map<String, Long> groupBlocks = new TreeMap<>();
        Map<String, String> groupMax = new TreeMap<>();
        int plankedRocks = 0;
        for (Object[] p : pick) {
            File dir = new File(root, (String) p[0]);
            File[] files = dir.listFiles((dd, n) -> n.endsWith(".schem"));
            if (files == null) { System.out.println("  missing " + dir); continue; }
            Arrays.sort(files, Comparator.comparing(File::getName));
            int want = (Integer) p[1];
            for (int k = 0; k < want; k++) {
                // spread through the folder so every size and shape class in it is sampled
                File f = files[(int) ((long) k * files.length / want)];
                String base = f.getName().replace(".schem", "");
                try {
                    Asset a = sponge(f, "rock/" + ((String) p[0]).toLowerCase(Locale.ROOT) + "/" + base, (String) p[2] + size(base), (Double) p[3]);
                    if (a.n == 0) plankedRocks++;
                    if (a.n > 0) { out.add(a); groupBlocks.merge((String) p[0], (long) a.n, Long::sum); groupMax.merge((String) p[0], a.sx + "x" + a.sy + "x" + a.sz, (u, v) -> Integer.parseInt(u.split("x")[1]) >= Integer.parseInt(v.split("x")[1]) ? u : v); }
                } catch (Exception e) {
                    System.out.println("  skipped " + f + ": " + e);
                }
            }
        }
        for (String g : groupBlocks.keySet()) System.out.println("  " + g + ": " + groupBlocks.get(g) + " blocks, tallest " + groupMax.get(g));
        System.out.println("rocks: " + out.size() + " (" + plankedRocks + " left out for planks)");
        return out;
    }

    static String size(String fileBase) {
        return fileBase.contains("small") ? ",small" : fileBase.contains("medium") ? ",medium" : fileBase.contains("large") || fileBase.contains("huge") ? ",large" : "";
    }

    static int num(Map<String, Object> d, String k) {
        return ((Number) d.get(k)).intValue();
    }

    // ---- output

    static void write(File out, List<Asset> assets) throws IOException {
        // Each file has its own palette, of the states its assets use.
        Map<Integer, Integer> remap = new LinkedHashMap<>();
        for (Asset a : assets) for (int i = 0; i < a.n; i++) remap.computeIfAbsent(a.st[i], k -> remap.size());
        long blocks = 0, nruns = 0;
        try (DataOutputStream o = new DataOutputStream(new BufferedOutputStream(new GZIPOutputStream(new FileOutputStream(out)), 1 << 16))) {
            o.writeInt(0x47414132); // "GAA2"
            o.writeInt(remap.size());
            for (int k : remap.keySet()) o.writeUTF(palette.get(k));
            o.writeInt(assets.size());
            for (Asset a : assets) {
                o.writeUTF(a.name);
                o.writeUTF(a.tags);
                o.writeShort(a.sx); o.writeShort(a.sy); o.writeShort(a.sz); o.writeShort(a.baseY);
                // sort by column, then height
                Integer[] order = new Integer[a.n];
                for (int i = 0; i < a.n; i++) order[i] = i;
                Arrays.sort(order, (p, q) -> a.xs[p] != a.xs[q] ? a.xs[p] - a.xs[q] : a.zs[p] != a.zs[q] ? a.zs[p] - a.zs[q] : a.ys[p] - a.ys[q]);
                // runs up each column: (x, z, first y, length, state)
                List<int[]> runs = new ArrayList<>();
                int[] cur = null;
                for (int i : order) {
                    if (cur != null && cur[0] == a.xs[i] && cur[1] == a.zs[i] && cur[2] + cur[3] == a.ys[i] && cur[4] == a.st[i]) cur[3]++;
                    else runs.add(cur = new int[] {a.xs[i], a.zs[i], a.ys[i], 1, a.st[i]});
                }
                o.writeInt(runs.size());
                for (int[] r : runs) {
                    o.writeShort(r[0]); o.writeShort(r[1]); o.writeShort(r[2]); o.writeShort(r[3]); o.writeShort(remap.get(r[4]));
                }
                nruns += runs.size();
                blocks += a.n;
            }
        }
        System.out.println("wrote " + out + ": " + assets.size() + " assets, " + blocks + " blocks in " + nruns + " runs, " + remap.size() + " states, " + out.length() / 1024 + " KB");
    }

    public static void main(String[] args) throws IOException {
        File src = new File(args[0]), dst = new File(args[1]);
        dst.mkdirs();
        String only = args.length > 2 ? args[2] : "all";
        if (only.equals("all") || only.equals("h2co3")) {
            List<Asset> t = h2co3(new File(src, "h2co3/h2co3_1k_treepack.schematic"));
            Map<String, Integer> byTag = new TreeMap<>();
            for (Asset a : t) byTag.merge(a.tags, 1, Integer::sum);
            System.out.println(byTag);
            write(new File(dst, "trees.bin"), t);
        }
        if (only.equals("all") || only.equals("god")) write(new File(dst, "god_trees.bin"), god(new File(src, "godtree")));
        if (only.equals("all") || only.equals("rocks")) write(new File(dst, "rocks.bin"), rocks(new File(src, "dannypan/schem")));
    }
}
