package com.seaweed.grandatlas.world;

import com.seaweed.grandatlas.GrandAtlas;
import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The hand-built assets Grand Atlas places in the world: trees, god trees and rock formations
 * from the approved community packs (docs/CREDITS.md), converted by tools/assets/Import.java into
 * the mod's own packed files under data/grandatlas/atlas_assets/.
 *
 * <p>A file is read the first time something asks for it and kept. An asset is its blocks as
 * runs up each column (first y, length, block), with an index by column, so a chunk can ask for
 * just the columns it owns.
 */
public final class AssetLibrary {
    private AssetLibrary() {}

    public static final String TREES = "trees.bin", GOD_TREES = "god_trees.bin", ROCKS = "rocks.bin";
    private static final Rotation[] ROTATIONS = {Rotation.NONE, Rotation.CLOCKWISE_90, Rotation.CLOCKWISE_180, Rotation.COUNTERCLOCKWISE_90};

    /** One asset. Local x, z run 0..sx-1, 0..sz-1; {@code baseY} is the local level that sits on the ground. */
    public static final class Asset {
        public String name;
        public String[] tags;
        public int sx, sy, sz, baseY;
        /** Runs sorted by column: run i covers y0[i] .. y0[i] + len[i] - 1 in the block state[i]. */
        short[] y0, len, state;
        /** For column x * sz + z, its runs are [colStart[c], colStart[c + 1]). */
        int[] colStart;
        /** The file's block states, in each of the four turns. */
        BlockState[][] states;

        public boolean has(String tag) {
            for (String t : tags) if (t.equals(tag)) return true;
            return false;
        }

        /** Half the longer side: how far from its middle the asset can reach, turned any way. */
        public int reach() {
            return (Math.max(sx, sz) + 1) / 2;
        }

        public int firstRun(int ax, int az) {
            return colStart[ax * sz + az];
        }

        public int endRun(int ax, int az) {
            return colStart[ax * sz + az + 1];
        }

        public int runY(int i) {
            return y0[i];
        }

        public int runLength(int i) {
            return len[i];
        }

        public BlockState runState(int i, int turn) {
            return states[turn][state[i]];
        }
    }

    /** A file's assets, with lookups by a set of tags. */
    public static final class Group {
        public final List<Asset> all = new ArrayList<>();
        // Concurrent: C2ME places features on many threads at once, and a plain HashMap's
        // computeIfAbsent threw ConcurrentModificationException there ("Feature placement",
        // chunks failed to load on the Create Atlas server, 2026-11-12).
        private final Map<String, Asset[]> byTags = new java.util.concurrent.ConcurrentHashMap<>();

        /** The assets carrying every one of these tags (comma separated), remembered per query. Possibly empty. */
        public Asset[] with(String tags) {
            return byTags.computeIfAbsent(tags, q -> {
                String[] want = q.split(",");
                List<Asset> found = new ArrayList<>();
                for (Asset a : all) {
                    boolean ok = true;
                    for (String w : want) ok &= a.has(w);
                    if (ok) found.add(a);
                }
                return found.toArray(new Asset[0]);
            });
        }

        /** The widest reach of any asset with these tags: how far a chunk must look for one that touches it. */
        public int reach(String tags) {
            int r = 0;
            for (Asset a : with(tags)) r = Math.max(r, a.reach());
            return r;
        }
    }

    private static final Map<String, Group> GROUPS = new HashMap<>();

    public static synchronized Group group(String file) {
        Group g = GROUPS.get(file);
        if (g == null) {
            long t0 = System.nanoTime();
            g = load(file);
            GROUPS.put(file, g);
            GrandAtlas.LOG.info("Grand Atlas assets: {} loaded, {} assets in {} ms", file, g.all.size(), (System.nanoTime() - t0) / 1_000_000);
        }
        return g;
    }

    private static Group load(String file) {
        Group g = new Group();
        try (InputStream raw = AssetLibrary.class.getResourceAsStream("/data/grandatlas/atlas_assets/" + file)) {
            if (raw == null) {
                GrandAtlas.LOG.warn("Grand Atlas assets: {} is missing from the mod jar; nothing from it will be placed", file);
                return g;
            }
            DataInputStream in = new DataInputStream(new BufferedInputStream(new GZIPInputStream(raw), 1 << 16));
            if (in.readInt() != 0x47414132) throw new IOException("not a Grand Atlas asset file (GAA2)");
            int np = in.readInt();
            BlockState[][] states = new BlockState[4][np];
            int unknown = 0;
            for (int i = 0; i < np; i++) {
                String s = in.readUTF();
                BlockState state;
                try {
                    state = BlockStateParser.parseForBlock(BuiltInRegistries.BLOCK, s, false).blockState();
                } catch (Exception e) {
                    state = Blocks.AIR.defaultBlockState(); // a block this version doesn't have: left out
                    unknown++;
                    if (unknown <= 5) GrandAtlas.LOG.warn("Grand Atlas assets: {} names a block this game doesn't know: {}", file, s);
                }
                for (int r = 0; r < 4; r++) states[r][i] = state.rotate(ROTATIONS[r]);
            }
            int na = in.readInt();
            for (int k = 0; k < na; k++) {
                Asset a = new Asset();
                a.name = in.readUTF();
                a.tags = in.readUTF().split(",");
                a.sx = in.readShort();
                a.sy = in.readShort();
                a.sz = in.readShort();
                a.baseY = in.readShort();
                a.states = states;
                int n = in.readInt();
                a.y0 = new short[n];
                a.len = new short[n];
                a.state = new short[n];
                a.colStart = new int[a.sx * a.sz + 1];
                int col = -1;
                for (int i = 0; i < n; i++) {
                    int x = in.readShort(), z = in.readShort();
                    a.y0[i] = in.readShort();
                    a.len[i] = in.readShort();
                    a.state[i] = in.readShort();
                    int c = x * a.sz + z; // runs arrive sorted by (x, z, y)
                    while (col < c) a.colStart[++col] = i;
                }
                while (col < a.sx * a.sz) a.colStart[++col] = n;
                g.all.add(a);
            }
        } catch (IOException e) {
            GrandAtlas.LOG.error("Grand Atlas assets: could not read {}", file, e);
        }
        return g;
    }

    public static Rotation rotation(int turn) {
        return ROTATIONS[turn];
    }
}
