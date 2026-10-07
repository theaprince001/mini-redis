package io.miniredis.core;

import java.util.List;

/**
 * Per-command context. Caches the timestamp once; SET k v EX 10 and the PXAT
 * transform that rewrites it must use the same instant.
 */
public final class CommandContext {

    private final MiniRedisStore store;
    private final Session session;
    private final Propagator propagator;
    private final Config config;
    private final long now;

    public CommandContext(MiniRedisStore store, Session session,
                          Propagator propagator, Config config, long now) {
        this.store = store;
        this.session = session;
        this.propagator = propagator;
        this.config = config;
        this.now = now;
    }

    public MiniRedisStore store() { return store; }
    public Session session() { return session; }
    public Config config() { return config; }
    public long now() { return now; }

    public void propagate(List<byte[]> argv) { propagator.propagate(argv); }
}