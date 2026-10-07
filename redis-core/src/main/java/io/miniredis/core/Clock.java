package io.miniredis.core;

@FunctionalInterface
public interface Clock {
    long currentTimeMillis();
}