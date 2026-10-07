package io.miniredis.server.commands;

import io.miniredis.core.ByteArrayWrapper;
import io.miniredis.protocol.RespValue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SetCommandTest extends CommandTestSupport {

    @BeforeEach void init() { setUp(); }

    @Test void plainSetReturnsSimpleStringOk() {
        RespValue r = new SetCommand().execute(ctx(), args("SET", "k", "v"));
        assertEquals(new RespValue.SimpleString("OK"), r);
    }

    @Test void plainSetStoresValue() {
        new SetCommand().execute(ctx(), args("SET", "k", "v"));
        ByteArrayWrapper k = new ByteArrayWrapper("k".getBytes(StandardCharsets.US_ASCII));
        assertEquals("v", new String(store.read(k, 0).value().stringBytes(), StandardCharsets.US_ASCII));
    }

    @Test void plainSetPropagatesVerbatim() {
        new SetCommand().execute(ctx(), args("SET", "k", "v"));
        assertEquals(1, propagator.commands().size());
        List<byte[]> cmd = propagator.commands().get(0);
        assertEquals(3, cmd.size());
        assertEquals("SET", new String(cmd.get(0), StandardCharsets.US_ASCII));
    }

    @Test void setWithExPropagatesPxat() {
        new SetCommand().execute(ctx(), args("SET", "k", "v", "EX", "10"));
        List<byte[]> cmd = propagator.commands().get(0);
        assertEquals(5, cmd.size());
        assertEquals("SET",    new String(cmd.get(0), StandardCharsets.US_ASCII));
        assertEquals("k",      new String(cmd.get(1), StandardCharsets.US_ASCII));
        assertEquals("v",      new String(cmd.get(2), StandardCharsets.US_ASCII));
        assertEquals("PXAT",   new String(cmd.get(3), StandardCharsets.US_ASCII));
        long abs = Long.parseLong(new String(cmd.get(4), StandardCharsets.US_ASCII));
        assertEquals(1_000_000L + 10_000L, abs);
    }

    @Test void setWithKeepttlPropagatesKeepttl() {
        new SetCommand().execute(ctx(), args("SET", "k", "v", "KEEPTTL"));
        List<byte[]> cmd = propagator.commands().get(0);
        assertEquals(4, cmd.size());
        assertEquals("KEEPTTL", new String(cmd.get(3), StandardCharsets.US_ASCII));
    }

    @Test void nxOnExistingReturnsNullAndDoesNotStore() {
        new SetCommand().execute(ctx(), args("SET", "k", "old"));
        propagator.clear();
        RespValue r = new SetCommand().execute(ctx(), args("SET", "k", "new", "NX"));
        assertEquals(new RespValue.NullBulk(), r);
        ByteArrayWrapper k = new ByteArrayWrapper("k".getBytes(StandardCharsets.US_ASCII));
        assertEquals("old", new String(store.read(k, 0).value().stringBytes(),
                StandardCharsets.US_ASCII));
        assertTrue(propagator.commands().isEmpty());
    }

    @Test void xxOnMissingReturnsNull() {
        RespValue r = new SetCommand().execute(ctx(), args("SET", "k", "v", "XX"));
        assertEquals(new RespValue.NullBulk(), r);
    }

    @Test void nxAndXxTogetherIsSyntaxError() {
        RespValue r = new SetCommand().execute(ctx(), args("SET", "k", "v", "NX", "XX"));
        assertEquals(new RespValue.Error("ERR syntax error"), r);
    }

    @Test void exAndKeepttlTogetherIsSyntaxError() {
        RespValue r = new SetCommand().execute(ctx(), args("SET", "k", "v", "EX", "10", "KEEPTTL"));
        assertEquals(new RespValue.Error("ERR syntax error"), r);
    }

    @Test void exNonIntegerIsAnError() {
        RespValue r = new SetCommand().execute(ctx(), args("SET", "k", "v", "EX", "abc"));
        assertEquals(new RespValue.Error("ERR value is not an integer or out of range"), r);
    }

    @Test void exZeroIsAnError() {
        RespValue r = new SetCommand().execute(ctx(), args("SET", "k", "v", "EX", "0"));
        assertEquals(new RespValue.Error("ERR invalid expire time in 'set' command"), r);
    }

    @Test void exNegativeIsAnError() {
        RespValue r = new SetCommand().execute(ctx(), args("SET", "k", "v", "EX", "-1"));
        assertEquals(new RespValue.Error("ERR invalid expire time in 'set' command"), r);
    }

    @Test void exOverflowIsAnError() {
        RespValue r = new SetCommand().execute(ctx(), args("SET", "k", "v", "EX", "9223372036854775807"));
        assertEquals(new RespValue.Error("ERR invalid expire time in 'set' command"), r);
    }

    @Test void optionNamesAreCaseInsensitive() {
        RespValue r = new SetCommand().execute(ctx(), args("SET", "k", "v", "ex", "10"));
        assertEquals(new RespValue.SimpleString("OK"), r);
    }

    @Test void setWithGetReturnsOldValue() {
        new SetCommand().execute(ctx(), args("SET", "k", "old"));
        RespValue r = new SetCommand().execute(ctx(), args("SET", "k", "new", "GET"));
        assertEquals(new RespValue.Bulk("old".getBytes(StandardCharsets.US_ASCII)), r);
    }

    @Test void setWithGetOnMissingReturnsNull() {
        RespValue r = new SetCommand().execute(ctx(), args("SET", "k", "v", "GET"));
        assertEquals(new RespValue.NullBulk(), r);
    }

    @Test void plainSetClearsExistingTtl() {
        new SetCommand().execute(ctx(), args("SET", "k", "v", "EX", "100"));
        ByteArrayWrapper k = new ByteArrayWrapper("k".getBytes(StandardCharsets.US_ASCII));
        assertTrue(store.deadline(k) > 0);
        new SetCommand().execute(ctx(), args("SET", "k", "v2"));
        assertEquals(-1L, store.deadline(k));
    }

    @Test void keepttlPreservesExistingTtl() {
        new SetCommand().execute(ctx(), args("SET", "k", "v", "EX", "100"));
        ByteArrayWrapper k = new ByteArrayWrapper("k".getBytes(StandardCharsets.US_ASCII));
        long original = store.deadline(k);
        new SetCommand().execute(ctx(), args("SET", "k", "v2", "KEEPTTL"));
        assertEquals(original, store.deadline(k));
    }

    @Test void unknownOptionIsSyntaxError() {
        RespValue r = new SetCommand().execute(ctx(), args("SET", "k", "v", "FOO"));
        assertEquals(new RespValue.Error("ERR syntax error"), r);
    }
}