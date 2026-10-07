package io.miniredis.core;

public final class FakeClock implements Clock {

    private volatile long now;

    public FakeClock(long initialMillis) { this.now = initialMillis; }

    @Override
    public long currentTimeMillis() { return now; }

    public void advance(long millis) { now += millis; }
    public void set(long millis) { now = millis; }
}