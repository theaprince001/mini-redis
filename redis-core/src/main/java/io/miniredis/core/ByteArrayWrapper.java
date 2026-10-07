package io.miniredis.core;

import java.util.Arrays;

/**
 * Binary-safe map key with cached hash. Callers must not mutate the array
 * returned by bytes().
 */
public final class ByteArrayWrapper implements Comparable<ByteArrayWrapper> {

    private final byte[] bytes;
    private final int hash;

    public ByteArrayWrapper(byte[] bytes) {
        this.bytes = bytes;
        this.hash = Arrays.hashCode(bytes);
    }

    public static ByteArrayWrapper of(byte[] bytes) { return new ByteArrayWrapper(bytes); }

    public byte[] bytes() { return bytes; }
    public int length() { return bytes.length; }

    @Override
    public boolean equals(Object o) {
        return o instanceof ByteArrayWrapper b && Arrays.equals(bytes, b.bytes);
    }

    @Override
    public int hashCode() { return hash; }

    @Override
    public int compareTo(ByteArrayWrapper o) {
        return Arrays.compareUnsigned(bytes, o.bytes);
    }
}