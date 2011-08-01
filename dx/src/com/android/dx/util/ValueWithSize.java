package com.android.dx.util;

public class ValueWithSize<V> {
    V value;
    int size;

    public ValueWithSize(V value, int size) {
        this.value = value;
        this.size = size;
    }

    public V getValue() {
        return value;
    }

    public int getSize() {
        return size;
    }
}

