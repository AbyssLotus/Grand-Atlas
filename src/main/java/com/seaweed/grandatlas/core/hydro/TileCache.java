package com.seaweed.grandatlas.core.hydro;

import java.util.Iterator;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.function.Supplier;

/**
 * Tiles computed once and shared by every worldgen thread. A thread that asks for a tile another
 * thread is computing waits for it instead of computing it again. Tiles only ever depend on
 * tiles of an earlier stage, so waiting can't deadlock. Past {@code limit} tiles, about a quarter
 * are dropped; they're deterministic, so a dropped tile just gets computed again.
 */
final class TileCache<V> {
    private final ConcurrentHashMap<Long, FutureTask<V>> map = new ConcurrentHashMap<>();
    private final int limit;

    TileCache(int limit) {
        this.limit = limit;
    }

    static long key(int ti, int tj) {
        return ((long) ti << 32) | (tj & 0xFFFFFFFFL);
    }

    V get(int ti, int tj, Supplier<V> make) {
        long key = key(ti, tj);
        FutureTask<V> task = map.get(key);
        if (task == null) {
            FutureTask<V> mine = new FutureTask<>(make::get);
            task = map.putIfAbsent(key, mine);
            if (task == null) {
                task = mine;
                if (map.size() > limit) trim(key);
                mine.run();
            }
        }
        try {
            return task.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        } catch (ExecutionException e) {
            map.remove(key, task);
            throw new IllegalStateException("Grand Atlas drainage tile " + ti + ", " + tj + " failed", e.getCause());
        }
    }

    private void trim(long keep) {
        int drop = map.size() / 4;
        for (Iterator<Long> it = map.keySet().iterator(); it.hasNext() && drop > 0; ) {
            if (it.next() != keep) {
                it.remove();
                drop--;
            }
        }
    }
}
