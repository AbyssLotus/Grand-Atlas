package com.seaweed.grandatlas.core.hydro;

import static com.seaweed.grandatlas.core.Maths.lerp;

import com.seaweed.grandatlas.core.noise.GradientNoise;
import com.seaweed.grandatlas.core.world.WorldField;
import java.util.Arrays;

/**
 * Distance to the sea in blocks, on a 1 km grid, interpolated between grid points. The drainage
 * leans on it so water heads for the coast. A shortest-path distance has no pits: every land
 * point has a neighbour nearer the sea, and the interpolation adds none. So the pull it gives
 * never traps water. It's exact up to {@link #REACH} km from the coast and capped beyond; each
 * tile is computed with that much margin, so tiles agree wherever they meet.
 */
final class CoastDistance {
    static final double CELL = 1024;
    private static final int TILE = 16;
    private static final int REACH = 40;

    private final WorldField world;
    private final TileCache<float[]> tiles = new TileCache<>(256);

    CoastDistance(WorldField world) {
        this.world = world;
    }

    double at(double x, double z) {
        double gx = x / CELL, gz = z / CELL;
        int i = GradientNoise.floor(gx), j = GradientNoise.floor(gz);
        double fx = gx - i, fz = gz - j;
        return lerp(fz, lerp(fx, node(i, j), node(i + 1, j)), lerp(fx, node(i, j + 1), node(i + 1, j + 1)));
    }

    private double node(int i, int j) {
        int ti = Math.floorDiv(i, TILE), tj = Math.floorDiv(j, TILE);
        float[] tile = tiles.get(ti, tj, () -> compute(ti, tj));
        return tile[(i - ti * TILE) + (j - tj * TILE) * TILE];
    }

    /** Dijkstra from every sea point within REACH of the tile, 8-connected. */
    private float[] compute(int ti, int tj) {
        int w = TILE + 2 * REACH;
        int i0 = ti * TILE - REACH, j0 = tj * TILE - REACH;
        double[] dist = new double[w * w];
        Arrays.fill(dist, Double.MAX_VALUE);
        Heap heap = new Heap(w * w);
        for (int b = 0; b < w; b++) {
            for (int a = 0; a < w; a++) {
                if (world.at((i0 + a) * CELL, (j0 + b) * CELL).continentalness() < -0.02) {
                    dist[a + b * w] = 0;
                    heap.push(0, a + b * w);
                }
            }
        }
        while (!heap.isEmpty()) {
            double d = heap.topKey();
            int at = heap.pop();
            if (d > dist[at]) continue;
            int a = at % w, b = at / w;
            for (int k = 0; k < 8; k++) {
                int na = a + Drainage.DX[k], nb = b + Drainage.DZ[k];
                if (na < 0 || nb < 0 || na >= w || nb >= w) continue;
                double nd = d + ((k & 1) == 0 ? 1 : Math.sqrt(2));
                if (nd < dist[na + nb * w]) {
                    dist[na + nb * w] = nd;
                    heap.push(nd, na + nb * w);
                }
            }
        }
        float[] out = new float[TILE * TILE];
        for (int b = 0; b < TILE; b++) {
            for (int a = 0; a < TILE; a++) {
                out[a + b * TILE] = (float) (Math.min(dist[(a + REACH) + (b + REACH) * w], REACH) * CELL);
            }
        }
        return out;
    }
}
