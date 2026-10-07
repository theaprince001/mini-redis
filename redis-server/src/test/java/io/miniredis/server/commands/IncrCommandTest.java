package io.miniredis.server.commands;

import io.miniredis.core.ByteArrayWrapper;
import io.miniredis.core.RedisObject;
import io.miniredis.protocol.RespValue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class IncrCommandTest extends CommandTestSupport {

    @BeforeEach void init() { setUp(); }

    @Test void incrementsMissingKeyFromZero() {
        RespValue r = new IncrCommand().execute(ctx(), args("INCR", "k"));
        assertEquals(new RespValue.Integer(1), r);
    }

    @Test void incrementsExisting() {
        store.put(new ByteArrayWrapper("k".getBytes(StandardCharsets.US_ASCII)),
                RedisObject.string("41".getBytes(StandardCharsets.US_ASCII)));
        RespValue r = new IncrCommand().execute(ctx(), args("INCR", "k"));
        assertEquals(new RespValue.Integer(42), r);
    }

    @Test void propagatesOriginalArgvNotSet() {
        ByteArrayWrapper k = new ByteArrayWrapper("k".getBytes(StandardCharsets.US_ASCII));
        store.put(k, RedisObject.string("5".getBytes(StandardCharsets.US_ASCII)));
        store.setExpiry(k, clock.currentTimeMillis() + 100_000);
        propagator.clear();

        new IncrCommand().execute(ctx(), args("INCR", "k"));

        assertEquals(1, propagator.commands().size());
        List<byte[]> cmd = propagator.commands().get(0);
        assertEquals(2, cmd.size());
        assertEquals("INCR", new String(cmd.get(0), StandardCharsets.US_ASCII));
        assertEquals("k",    new String(cmd.get(1), StandardCharsets.US_ASCII));
        assertTrue(store.deadline(k) > 0);
    }

    @Test void overflowIsAnError() {
        store.put(new ByteArrayWrapper("k".getBytes(StandardCharsets.US_ASCII)),
                RedisObject.string("9223372036854775807".getBytes(StandardCharsets.US_ASCII)));
        RespValue r = new IncrCommand().execute(ctx(), args("INCR", "k"));
        assertEquals(new RespValue.Error("ERR increment or decrement would overflow"), r);
    }

    @Test void nonIntegerIsAnError() {
        store.put(new ByteArrayWrapper("k".getBytes(StandardCharsets.US_ASCII)),
                RedisObject.string("abc".getBytes(StandardCharsets.US_ASCII)));
        RespValue r = new IncrCommand().execute(ctx(), args("INCR", "k"));
        assertEquals(new RespValue.Error("ERR value is not an integer or out of range"), r);
    }

    @Test void leadingZeroIsRejectedAsNonCanonical() {
        store.put(new ByteArrayWrapper("k".getBytes(StandardCharsets.US_ASCII)),
                RedisObject.string("007".getBytes(StandardCharsets.US_ASCII)));
        RespValue r = new IncrCommand().execute(ctx(), args("INCR", "k"));
        assertEquals(new RespValue.Error("ERR value is not an integer or out of range"), r);
    }
}