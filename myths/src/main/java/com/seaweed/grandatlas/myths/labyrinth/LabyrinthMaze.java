package com.seaweed.grandatlas.myths.labyrinth;

import java.util.ArrayDeque;
import java.util.Random;

/**
 * The layout of the infinite, shifting maze: pure maths, no Minecraft classes.
 *
 * <h2>The grid</h2>
 * The world is cut into square <b>cells</b> of {@link #CELL} blocks. Inside a cell, the middle
 * 3x3 columns (local 2..4) are always open corridor; the 2-block bands round it are wall,
 * unless the maze opens a <b>passage</b> to the neighbouring cell. So every cell centre is
 * walkable, corridors are 3 wide, and walls are 4 thick.
 *
 * <h2>Regions, and why the maze can shift safely</h2>
 * Cells are grouped into square <b>regions</b> of {@link #REGION} x {@link #REGION} cells (147
 * blocks). Each region is a maze of its own (a perfect maze, then a share of walls knocked
 * through for loops), made from the world salt, the region's coordinates and the region's <b>epoch</b>.
 * When a region's epoch goes up, its whole inside is re-made: new walls, a new lab, new exits.
 *
 * <p>The <b>doors</b> between regions ({@link #DOORS} per border) come from a hash of the border
 * alone, never the epoch, so they never move. Because each region connects every one of its
 * cells, including its door cells, two neighbouring regions at different epochs still join up
 * through those doors. That's what makes shifting safe: whatever shifted, everything is still
 * reachable, and nobody can be sealed in. (LabyrinthShift only shifts regions nobody is in.)
 *
 * <h2>Places and signs in a region</h2>
 * <ul>
 *   <li>One <b>Daedalus's Lab</b>, a 3x3-cell hall (LabyrinthLab builds it).</li>
 *   <li>{@link #EXITS} <b>exits</b>: cells with a ladder up to an Exit Hatch.</li>
 *   <li>At every junction, an arrow in the floor pointing along the maze toward the nearest
 *       exit ({@link #exitArrow}); and cells near the lab get warm lanterns ({@link #labDistance}).</li>
 * </ul>
 */
public final class LabyrinthMaze {

    /** Blocks per cell. */
    public static final int CELL = 7;
    /** Cells per region side. 21 x 7 = 147 blocks. */
    public static final int REGION = 21;
    /** Openings in each border between two regions. They never move. */
    public static final int DOORS = 2;
    /** A lab is LAB x LAB cells. */
    public static final int LAB = 3;
    /** Exits per region. */
    public static final int EXITS = 5;
    /** Share of inner walls knocked through after the maze is made, for loops and shortcuts. */
    public static final double SHORTCUTS = 0.12;

    /** Directions, used for passages and arrows: 0 north (-z), 1 south (+z), 2 west (-x), 3 east (+x). */
    public static final int NORTH = 0, SOUTH = 1, WEST = 2, EAST = 3;
    private static final int[] DX = {0, 0, -1, 1}, DZ = {-1, 1, 0, 0};

    /** Where each region's epoch comes from. LabyrinthShift supplies it in game; tests use a constant. */
    @FunctionalInterface
    public interface Epochs {
        int of(int rx, int rz);
    }

    private final long salt;
    private final Epochs epochs;

    /** A few recently used regions per thread: a chunk usually touches only one or two. */
    private final ThreadLocal<Region[]> cache = ThreadLocal.withInitial(() -> new Region[6]);

    public LabyrinthMaze(long salt, Epochs epochs) {
        this.salt = salt;
        this.epochs = epochs;
    }

    public long salt() {
        return salt;
    }

    public int epoch(int rx, int rz) {
        return epochs.of(rx, rz);
    }

    // ── Column queries (block coordinates) ──────────────────────────────────────────────────

    /** Is the column at block (x, z) open corridor (true) or wall (false)? */
    public boolean isOpen(int x, int z) {
        if (inLab(x, z)) return true;
        int cx = Math.floorDiv(x, CELL), cz = Math.floorDiv(z, CELL);
        int lx = Math.floorMod(x, CELL), lz = Math.floorMod(z, CELL);
        boolean midX = lx >= 2 && lx <= 4, midZ = lz >= 2 && lz <= 4;
        if (midX && midZ) return true;
        if (midZ && lx >= 5) return passage(cx, cz, EAST);
        if (midZ && lx <= 1) return passage(cx, cz, WEST);
        if (midX && lz >= 5) return passage(cx, cz, SOUTH);
        if (midX && lz <= 1) return passage(cx, cz, NORTH);
        return false; // a corner of the cell: always wall
    }

    /** Is block (x, z) inside the open floor of a lab hall? */
    public boolean inLab(int x, int z) {
        int[] c = labCentre(regionOf(Math.floorDiv(x, CELL)), regionOf(Math.floorDiv(z, CELL)));
        return Math.abs(x - c[0]) <= LabyrinthLab.HALF && Math.abs(z - c[1]) <= LabyrinthLab.HALF;
    }

    // ── Passages (cell coordinates) ─────────────────────────────────────────────────────────

    /** Is there a passage from cell (cx, cz) in direction {@code dir}? */
    public boolean passage(int cx, int cz, int dir) {
        return switch (dir) {
            case EAST -> eastOpen(cx, cz);
            case WEST -> eastOpen(cx - 1, cz);
            case SOUTH -> southOpen(cx, cz);
            default -> southOpen(cx, cz - 1);
        };
    }

    /** Is there a passage from cell (cx, cz) to its east neighbour? */
    public boolean eastOpen(int cx, int cz) {
        int rx = regionOf(cx), rz = regionOf(cz);
        int lcx = Math.floorMod(cx, REGION), lcz = Math.floorMod(cz, REGION);
        if (lcx == REGION - 1) return isDoor(rx, rz, EAST, lcz);
        return region(rx, rz).east[lcx][lcz];
    }

    /** Is there a passage from cell (cx, cz) to its south neighbour? */
    public boolean southOpen(int cx, int cz) {
        int rx = regionOf(cx), rz = regionOf(cz);
        int lcx = Math.floorMod(cx, REGION), lcz = Math.floorMod(cz, REGION);
        if (lcz == REGION - 1) return isDoor(rx, rz, SOUTH, lcx);
        return region(rx, rz).south[lcx][lcz];
    }

    // ── Places ──────────────────────────────────────────────────────────────────────────────

    /** The lab's first cell, in global cell coordinates, for region (rx, rz). */
    public int[] labCell(int rx, int rz) {
        long h = hash(rx, rz, 0x1AB, epoch(rx, rz));
        int span = REGION - LAB - 3; // keep the lab off the region's edge cells
        return new int[]{rx * REGION + 2 + (int) Math.floorMod(h, span),
                         rz * REGION + 2 + (int) Math.floorMod(h >>> 20, span)};
    }

    /** The block column at the centre of region (rx, rz)'s lab. */
    public int[] labCentre(int rx, int rz) {
        int[] c = labCell(rx, rz);
        return new int[]{(c[0] + 1) * CELL + 3, (c[1] + 1) * CELL + 3};
    }

    /** Which of the lab's three themes region (rx, rz) has (LabyrinthLab.Theme order). */
    public int labTheme(int rx, int rz) {
        return (int) Math.floorMod(hash(rx, rz, 0x7E3E, epoch(rx, rz)), 3);
    }

    /** A hash for the lab's small random choices. */
    public long labHash(int rx, int rz, int purpose) {
        return hash(rx, rz, 0x1AB0 + purpose, epoch(rx, rz));
    }

    /** Is cell (cx, cz) one of the lab's cells? */
    public boolean isLabCell(int cx, int cz) {
        int[] lab = labCell(regionOf(cx), regionOf(cz));
        return cx >= lab[0] && cx < lab[0] + LAB && cz >= lab[1] && cz < lab[1] + LAB;
    }

    /**
     * Is cell (cx, cz) an exit? If so, returns the side of the closed wall its ladder leans on
     * (NORTH..EAST); otherwise -1.
     */
    public int exitSide(int cx, int cz) {
        Region r = region(regionOf(cx), regionOf(cz));
        nav(r);
        int lcx = Math.floorMod(cx, REGION), lcz = Math.floorMod(cz, REGION);
        return r.exitSide[lcx][lcz];
    }

    /** The exits of region (rx, rz), as {cellX, cellZ, side} each. */
    public int[][] exits(int rx, int rz) {
        Region r = region(rx, rz);
        nav(r);
        return r.exits;
    }

    /**
     * At a junction (a cell with three or more ways on), the direction of the next step along
     * the maze toward the nearest exit; -1 elsewhere (corridors, dead ends, exits, the lab).
     */
    public int exitArrow(int cx, int cz) {
        Region r = region(regionOf(cx), regionOf(cz));
        nav(r);
        return r.arrow[Math.floorMod(cx, REGION)][Math.floorMod(cz, REGION)];
    }

    /**
     * The lab hall's way out toward the nearest exit: {cellX, cellZ, dir}, a hall cell and the
     * direction of the doorway to leave by (LabyrinthLab puts an arrow just inside it).
     */
    public int[] labExit(int rx, int rz) {
        Region r = region(rx, rz);
        nav(r);
        return r.labExit;
    }

    /** Steps along the maze from cell (cx, cz) to its region's lab (0 inside the lab). */
    public int labDistance(int cx, int cz) {
        Region r = region(regionOf(cx), regionOf(cz));
        nav(r);
        return r.labDist[Math.floorMod(cx, REGION)][Math.floorMod(cz, REGION)];
    }

    /** A deterministic hash of cell (cx, cz) and a purpose (includes the region's epoch). */
    public long cellHash(int cx, int cz, int purpose) {
        return hash(cx, cz, purpose, epoch(regionOf(cx), regionOf(cz)));
    }

    public static int regionOf(int cell) {
        return Math.floorDiv(cell, REGION);
    }

    // ── Internals ───────────────────────────────────────────────────────────────────────────

    /** Does the border on side EAST/SOUTH of region (rx, rz) have a door at row/column {@code at}? Never depends on epochs. */
    private boolean isDoor(int rx, int rz, int side, int at) {
        long h = hash(rx, rz, side == EAST ? 0xD0E : 0xD05, 0);
        for (int i = 0; i < DOORS; i++) {
            if ((int) Math.floorMod(h >>> (i * 16), REGION) == at) return true;
        }
        return false;
    }

    private Region region(int rx, int rz) {
        int epoch = epoch(rx, rz);
        Region[] slots = cache.get();
        for (Region r : slots) if (r != null && r.rx == rx && r.rz == rz && r.epoch == epoch) return r;
        Region made = new Region(rx, rz, epoch, hash(rx, rz, 0x3A2E, epoch));
        System.arraycopy(slots, 0, slots, 1, slots.length - 1); // newest first
        slots[0] = made;
        return made;
    }

    /**
     * Works out a region's exits, its junction arrows and its distances to the lab, once.
     * Two breadth-first searches over the region's own passages (doors to other regions are
     * ignored, so every arrow leads to an exit in the same region, which always exists).
     */
    private void nav(Region r) {
        if (r.arrow != null) return;
        int n = REGION, ox = r.rx * REGION, oz = r.rz * REGION;
        int[] lab = labCell(r.rx, r.rz);

        // Exits: cells away from the lab, the region's edge and each other, with a closed side.
        r.exitSide = new int[n][n];
        for (int[] row : r.exitSide) java.util.Arrays.fill(row, -1);
        int[][] chosen = new int[EXITS][];
        int found = 0;
        for (int attempt = 0; attempt < 400 && found < EXITS; attempt++) {
            long h = hash(r.rx, r.rz, 0xE817 + attempt, r.epoch);
            int lcx = 1 + (int) Math.floorMod(h, n - 2), lcz = 1 + (int) Math.floorMod(h >>> 24, n - 2);
            int cx = ox + lcx, cz = oz + lcz;
            if (cx >= lab[0] - 1 && cx <= lab[0] + LAB && cz >= lab[1] - 1 && cz <= lab[1] + LAB) continue;
            boolean crowded = false;
            for (int i = 0; i < found; i++)
                if (Math.abs(chosen[i][0] - cx) + Math.abs(chosen[i][1] - cz) < 6) crowded = true;
            if (crowded) continue;
            int side = -1;
            for (int d = 0; d < 4 && side < 0; d++) if (!inRegionPassage(r, lcx, lcz, d)) side = d;
            if (side < 0) continue; // a four-way crossing has no wall for a ladder
            chosen[found++] = new int[]{cx, cz, side};
            r.exitSide[lcx][lcz] = side;
        }
        r.exits = java.util.Arrays.copyOf(chosen, found);

        // Distances to the nearest exit, then one arrow per junction pointing downhill.
        int[][] exitDist = bfs(r, (lcx, lcz) -> r.exitSide[lcx][lcz] >= 0);
        r.labDist = bfs(r, (lcx, lcz) -> {
            int cx = ox + lcx, cz = oz + lcz;
            return cx >= lab[0] && cx < lab[0] + LAB && cz >= lab[1] && cz < lab[1] + LAB;
        });
        r.arrow = new int[n][n];
        for (int lcx = 0; lcx < n; lcx++) {
            for (int lcz = 0; lcz < n; lcz++) {
                r.arrow[lcx][lcz] = -1;
                if (r.labDist[lcx][lcz] == 0 || r.exitSide[lcx][lcz] >= 0) continue;
                int ways = 0, best = -1, bestDist = Integer.MAX_VALUE;
                for (int d = 0; d < 4; d++) {
                    if (!inRegionPassage(r, lcx, lcz, d)) continue;
                    ways++;
                    int nd = exitDist[lcx + DX[d]][lcz + DZ[d]];
                    if (nd < bestDist) { bestDist = nd; best = d; }
                }
                if (ways >= 3) r.arrow[lcx][lcz] = best;
            }
        }

        // The hall: the doorway nearest an exit, marked by one arrow just inside it.
        int bestDist = Integer.MAX_VALUE;
        r.labExit = null;
        for (int lcx = 0; lcx < n; lcx++) for (int lcz = 0; lcz < n; lcz++) {
            if (r.labDist[lcx][lcz] != 0) continue;
            for (int d = 0; d < 4; d++) {
                if (!inRegionPassage(r, lcx, lcz, d)) continue;
                int nx = lcx + DX[d], nz = lcz + DZ[d];
                if (r.labDist[nx][nz] == 0) continue; // another hall cell
                if (exitDist[nx][nz] < bestDist) {
                    bestDist = exitDist[nx][nz];
                    r.labExit = new int[]{ox + lcx, oz + lcz, d};
                }
            }
        }
    }

    private interface CellTest { boolean test(int lcx, int lcz); }

    /** Breadth-first distances (in cells) from every cell matching {@code source}, inside one region. */
    private int[][] bfs(Region r, CellTest source) {
        int n = REGION;
        int[][] dist = new int[n][n];
        for (int[] row : dist) java.util.Arrays.fill(row, Integer.MAX_VALUE);
        ArrayDeque<int[]> queue = new ArrayDeque<>();
        for (int x = 0; x < n; x++) for (int z = 0; z < n; z++)
            if (source.test(x, z)) { dist[x][z] = 0; queue.add(new int[]{x, z}); }
        while (!queue.isEmpty()) {
            int[] c = queue.poll();
            for (int d = 0; d < 4; d++) {
                if (!inRegionPassage(r, c[0], c[1], d)) continue;
                int nx = c[0] + DX[d], nz = c[1] + DZ[d];
                if (dist[nx][nz] != Integer.MAX_VALUE) continue;
                dist[nx][nz] = dist[c[0]][c[1]] + 1;
                queue.add(new int[]{nx, nz});
            }
        }
        return dist;
    }

    /**
     * A passage from region-local cell (lcx, lcz) in direction d that stays inside the region.
     * Lab cells also join each other (the hall has no inner walls).
     */
    private boolean inRegionPassage(Region r, int lcx, int lcz, int d) {
        int nx = lcx + DX[d], nz = lcz + DZ[d];
        if (nx < 0 || nz < 0 || nx >= REGION || nz >= REGION) return false;
        int[] lab = labCell(r.rx, r.rz);
        int ax = r.rx * REGION + lcx, az = r.rz * REGION + lcz, bx = r.rx * REGION + nx, bz = r.rz * REGION + nz;
        boolean aLab = ax >= lab[0] && ax < lab[0] + LAB && az >= lab[1] && az < lab[1] + LAB;
        boolean bLab = bx >= lab[0] && bx < lab[0] + LAB && bz >= lab[1] && bz < lab[1] + LAB;
        if (aLab && bLab) return true;
        return switch (d) {
            case EAST -> r.east[lcx][lcz];
            case WEST -> r.east[nx][nz];
            case SOUTH -> r.south[lcx][lcz];
            default -> r.south[nx][nz];
        };
    }

    /** SplitMix64-style mixing of the world salt, two coordinates, a purpose and an epoch. */
    private long hash(int a, int b, int purpose, int epoch) {
        long h = salt ^ (a * 0x9E3779B97F4A7C15L) ^ (b * 0xC2B2AE3D27D4EB4FL)
                ^ (purpose * 0x165667B19E3779F9L) ^ (epoch * 0xD6E8FEB86659FD93L);
        h = (h ^ (h >>> 30)) * 0xBF58476D1CE4E5B9L;
        h = (h ^ (h >>> 27)) * 0x94D049BB133111EBL;
        return h ^ (h >>> 31);
    }

    /** One region at one epoch: its perfect maze (an iterative recursive backtracker), plus nav data. */
    private static final class Region {
        final int rx, rz, epoch;
        final boolean[][] east = new boolean[REGION][REGION];
        final boolean[][] south = new boolean[REGION][REGION];
        int[][] exitSide, arrow, labDist, exits;
        int[] labExit;

        Region(int rx, int rz, int epoch, long seed) {
            this.rx = rx;
            this.rz = rz;
            this.epoch = epoch;
            Random random = new Random(seed);
            boolean[][] seen = new boolean[REGION][REGION];
            ArrayDeque<int[]> stack = new ArrayDeque<>();
            int sx = random.nextInt(REGION), sz = random.nextInt(REGION);
            seen[sx][sz] = true;
            stack.push(new int[]{sx, sz});
            int[] order = new int[4];
            while (!stack.isEmpty()) {
                int[] cell = stack.peek();
                for (int i = 0; i < 4; i++) order[i] = i;
                for (int i = 3; i > 0; i--) {
                    int j = random.nextInt(i + 1), t = order[i];
                    order[i] = order[j];
                    order[j] = t;
                }
                boolean moved = false;
                for (int k = 0; k < 4 && !moved; k++) {
                    int d = order[k];
                    int nx = cell[0] + DX[d], nz = cell[1] + DZ[d];
                    if (nx < 0 || nz < 0 || nx >= REGION || nz >= REGION || seen[nx][nz]) continue;
                    switch (d) {
                        case EAST -> east[cell[0]][cell[1]] = true;
                        case WEST -> east[nx][nz] = true;
                        case SOUTH -> south[cell[0]][cell[1]] = true;
                        default -> south[nx][nz] = true;
                    }
                    seen[nx][nz] = true;
                    stack.push(new int[]{nx, nz});
                    moved = true;
                }
                if (!moved) stack.pop(); // dead end: back up
            }
            // A perfect maze has exactly one route between two cells, which makes the way out
            // very long. Knocking through some extra walls adds loops and shortcuts.
            for (int x = 0; x < REGION; x++) for (int z = 0; z < REGION; z++) {
                if (x < REGION - 1 && !east[x][z] && random.nextDouble() < SHORTCUTS) east[x][z] = true;
                if (z < REGION - 1 && !south[x][z] && random.nextDouble() < SHORTCUTS) south[x][z] = true;
            }
        }
    }
}
