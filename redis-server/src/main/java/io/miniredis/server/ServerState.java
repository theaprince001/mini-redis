package io.miniredis.server;

import io.miniredis.core.Clock;
import io.miniredis.core.Config;
import io.miniredis.core.MiniRedisStore;
import io.miniredis.core.Propagator;

/** Server-scoped dependencies. One instance per server. */
public record ServerState(
        CommandDispatcher dispatcher,
        MiniRedisStore store,
        Propagator propagator,
        Config config,
        Clock clock,
        long stallTimeoutMs
) {}