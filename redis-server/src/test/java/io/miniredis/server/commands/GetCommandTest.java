package io.miniredis.server.commands;

import io.miniredis.protocol.RespValue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class GetCommandTest extends CommandTestSupport {

    @BeforeEach void init() { setUp(); }

    @Test void returnsNullForMissing() {
        assertEquals(new RespValue.NullBulk(),
                new GetCommand().execute(ctx(), args("GET", "k")));
    }

    @Test void returnsValue() {
        new SetCommand().execute(ctx(), args("SET", "k", "v"));
        assertEquals(new RespValue.Bulk("v".getBytes(java.nio.charset.StandardCharsets.US_ASCII)),
                new GetCommand().execute(ctx(), args("GET", "k")));
    }

    @Test void lazyExpiredKeyPropagatesDel() {
        new SetCommand().execute(ctx(), args("SET", "k", "v", "EX", "10"));
        propagator.clear();
        clock.advance(20_000);
        new GetCommand().execute(ctx(), args("GET", "k"));
        assertEquals(1, propagator.commands().size());
        assertEquals("DEL",
                new String(propagator.commands().get(0).get(0),
                        StandardCharsets.US_ASCII));
    }
}