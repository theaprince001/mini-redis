package io.miniredis.server.commands;

import io.miniredis.core.*;
import io.miniredis.protocol.RespValue;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

abstract class CommandTestSupport {

    MiniRedisStore store;
    Propagator.Recording propagator;
    Config config;
    FakeClock clock;
    Session session;

    void setUp() {
        store = new MiniRedisStore();
        store.setOwnerThread(Thread.currentThread());
        propagator = new Propagator.Recording();
        config = new Config();
        clock = new FakeClock(1_000_000L);
        session = new Session() {
            @Override public void requestClose() {}
            @Override public long clientId() { return 1L; }
        };
    }

    CommandContext ctx() {
        return new CommandContext(store, session, propagator, config, clock.currentTimeMillis());
    }

    static RespValue.Bulk bulk(String s) {
        return new RespValue.Bulk(s.getBytes(StandardCharsets.US_ASCII));
    }

    static List<RespValue> args(String... parts) {
        return Arrays.stream(parts)
                .map(CommandTestSupport::bulk)
                .map(RespValue.class::cast)
                .toList();
    }
}