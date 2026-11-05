package com.seaweed.grandatlas.core.hydro;

/** A binary min-heap of int payloads keyed by double, for the drainage floods. */
final class Heap {
    private double[] keys;
    private int[] values;
    private int size;

    Heap(int capacity) {
        keys = new double[Math.max(16, capacity)];
        values = new int[keys.length];
    }

    boolean isEmpty() {
        return size == 0;
    }

    void clear() {
        size = 0;
    }

    double topKey() {
        return keys[0];
    }

    void push(double key, int value) {
        if (size == keys.length) {
            keys = java.util.Arrays.copyOf(keys, size * 2);
            values = java.util.Arrays.copyOf(values, size * 2);
        }
        int i = size++;
        while (i > 0) {
            int parent = (i - 1) >> 1;
            if (keys[parent] <= key) break;
            keys[i] = keys[parent];
            values[i] = values[parent];
            i = parent;
        }
        keys[i] = key;
        values[i] = value;
    }

    int pop() {
        int top = values[0];
        double key = keys[--size];
        int value = values[size];
        int i = 0;
        while (true) {
            int child = 2 * i + 1;
            if (child >= size) break;
            if (child + 1 < size && keys[child + 1] < keys[child]) child++;
            if (keys[child] >= key) break;
            keys[i] = keys[child];
            values[i] = values[child];
            i = child;
        }
        keys[i] = key;
        values[i] = value;
        return top;
    }
}
