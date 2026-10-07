package io.miniredis.core;

import java.util.Set;

public record CommandSpec(
        String name,
        int arity,
        Set<Flag> flags,
        int firstKey,
        int lastKey,
        int step
) {

    public enum Flag { WRITE, READONLY, FAST, DENYOOM, ADMIN, LOADING, STALE }

    public boolean isWrite() { return flags.contains(Flag.WRITE); }

    public boolean acceptsArgCount(int n) {
        if (arity >= 0) return n == arity;
        return n >= -arity;
    }
}