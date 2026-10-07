package io.miniredis.server.commands;

import io.miniredis.core.ByteArrayWrapper;
import io.miniredis.core.RedisObject;
import io.miniredis.protocol.RespValue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AppendCommandTest extends CommandTestSupport {

    @BeforeEach void init() { setUp(); }

    @Test void appendsToExisting() {
        store.put(new ByteArrayWrapper("k".getBytes(StandardCharsets.US_ASCII)),
                RedisObject.string("foo".getBytes(StandardCharsets.US_ASCII)));
        RespValue r = new AppendCommand().execute(ctx(), args("APPEND", "k", "bar"));
        assertEquals(new RespValue.Integer(6), r);
    }

    @Test void createsWhenMissing() {
        RespValue r = new AppendCommand().execute(ctx(), args("APPEND", "k", "hello"));
        assertEquals(new RespValue.Integer(5), r);
    }

    @Test void propagatesOriginalArgvNotSet() {
        ByteArrayWrapper k = new ByteArrayWrapper("k".getBytes(StandardCharsets.US_ASCII));
        store.put(k, RedisObject.string("foo".getBytes(StandardCharsets.US_ASCII)));
        store.setExpiry(k, clock.currentTimeMillis() + 100_000);
        propagator.clear();

        new AppendCommand().execute(ctx(), args("APPEND", "k", "bar"));

        assertEquals(1, propagator.commands().size());
        List<byte[]> cmd = propagator.commands().get(0);
        assertEquals(3, cmd.size());
        assertEquals("APPEND", new String(cmd.get(0), StandardCharsets.US_ASCII));
        assertEquals("k",      new String(cmd.get(1), StandardCharsets.US_ASCII));
        assertEquals("bar",    new String(cmd.get(2), StandardCharsets.US_ASCII));
        assertTrue(store.deadline(k) > 0);
    }

    @Test void rejectsOversizedResult() {
        // Build a value just under the limit and append enough to exceed.
        int near = 8 * 1024 * 1024 - 2;
        byte[] big = new byte[near];
        store.put(new ByteArrayWrapper("k".getBytes(StandardCharsets.US_ASCII)),
                RedisObject.string(big));
        RespValue r = new AppendCommand().execute(ctx(), args("APPEND", "k", "xyz"));
        assertEquals(new RespValue.Error(
                "ERR string exceeds maximum allowed size (proto-max-bulk-len)"), r);
    }
}