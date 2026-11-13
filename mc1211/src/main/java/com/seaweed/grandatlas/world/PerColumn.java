package com.seaweed.grandatlas.world;

/**
 * What a density function knows about a column, kept per thread for the last few columns asked.
 *
 * <p>1.21.1 samples density functions one point at a time (26.3 hands over whole volumes, so the
 * column work was done once per column there). The chunk generator walks a 4x4 cell block by
 * block, so the same sixteen columns come round again and again: a small table keyed on the low
 * bits of x and z holds them all.
 */
final class PerColumn<T> {
    interface Loader<T> {
        T load(int x, int z);
    }

    private static final int N = 64;

    private static final class Table {
        final int[] xs = new int[N], zs = new int[N];
        final Object[] values = new Object[N];
    }

    private final ThreadLocal<Table> tables = ThreadLocal.withInitial(Table::new);
    private final Loader<T> loader;

    PerColumn(Loader<T> loader) {
        this.loader = loader;
    }

    @SuppressWarnings("unchecked")
    T get(int x, int z) {
        Table t = tables.get();
        int i = (x & 7) << 3 | (z & 7);
        Object v = t.values[i];
        if (v == null || t.xs[i] != x || t.zs[i] != z) {
            v = loader.load(x, z);
            t.xs[i] = x;
            t.zs[i] = z;
            t.values[i] = v;
        }
        return (T) v;
    }
}
