package io.miniredis.core;

import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class MiniRedisStoreTest {

    private static byte[] b(String s) { return s.getBytes(StandardCharsets.US_ASCII); }

    private MiniRedisStore newStore() {
        MiniRedisStore s = new MiniRedisStore();
        s.setOwnerThread(Thread.currentThread());
        return s;
    }

    @Test void putAndRead() {
        MiniRedisStore s = newStore();
        ByteArrayWrapper k = new ByteArrayWrapper(b("k"));
        s.put(k, RedisObject.string(b("v")));
        MiniRedisStore.ReadResult r = s.read(k, 0);
        assertTrue(r.found());
        assertArrayEquals(b("v"), r.value().stringBytes());
    }

    @Test void expiredOnReadDeletesAndReports() {
        MiniRedisStore s = newStore();
        ByteArrayWrapper k = new ByteArrayWrapper(b("k"));
        s.put(k, RedisObject.string(b("v")));
        s.setExpiry(k, 100);
        MiniRedisStore.ReadResult r = s.read(k, 200);
        assertFalse(r.found());
        assertTrue(r.expiredOnRead());
        assertFalse(s.containsKey(k));
    }

    @Test void sweepReturnsExpiredInOrder() {
        MiniRedisStore s = newStore();
        ByteArrayWrapper a = new ByteArrayWrapper(b("a"));
        ByteArrayWrapper b = new ByteArrayWrapper(b("b"));
        ByteArrayWrapper c = new ByteArrayWrapper(b("c"));
        s.put(a, RedisObject.string(b("v")));
        s.put(b, RedisObject.string(b("v")));
        s.put(c, RedisObject.string(b("v")));
        s.setExpiry(a, 100);
        s.setExpiry(b, 200);
        s.setExpiry(c, 300);
        List<ByteArrayWrapper> expired = s.sweepExpired(250, 1_000_000_000L);
        assertEquals(2, expired.size());
        assertEquals(a, expired.get(0));
        assertEquals(b, expired.get(1));
        assertTrue(s.containsKey(c));
    }

    @Test void sweepStopsAtBudget() {
        MiniRedisStore s = newStore();
        for (int i = 0; i < 1000; i++) {
            ByteArrayWrapper k = new ByteArrayWrapper(b("k" + i));
            s.put(k, RedisObject.string(b("v")));
            s.setExpiry(k, 100);
        }
        List<ByteArrayWrapper> expired = s.sweepExpired(200, 0);
        assertTrue(expired.size() < 1000, "should not drain everything with zero budget");
        assertTrue(expired.size() >= 16, "should drain at least one budget check");
    }

    @Test void sweepDrainsWithUnlimitedBudget() {
        MiniRedisStore s = newStore();
        for (int i = 0; i < 100; i++) {
            ByteArrayWrapper k = new ByteArrayWrapper(b("k" + i));
            s.put(k, RedisObject.string(b("v")));
            s.setExpiry(k, 100);
        }
        List<ByteArrayWrapper> expired = s.sweepExpired(200, Long.MAX_VALUE);
        assertEquals(100, expired.size());
        assertEquals(0, s.size());
    }

    @Test void clearExpiryRemovesDeadline() {
        MiniRedisStore s = newStore();
        ByteArrayWrapper k = new ByteArrayWrapper(b("k"));
        s.put(k, RedisObject.string(b("v")));
        s.setExpiry(k, 500);
        assertTrue(s.clearExpiry(k));
        assertEquals(-1L, s.deadline(k));
        assertFalse(s.clearExpiry(k));
    }

    @Test void ownerThreadIsEnforced() throws Exception {
        MiniRedisStore s = newStore();
        ByteArrayWrapper k = new ByteArrayWrapper(b("k"));
        s.put(k, RedisObject.string(b("v")));
        Throwable[] caught = new Throwable[1];
        Thread t = new Thread(() -> {
            try { s.put(k, RedisObject.string(b("x"))); }
            catch (Throwable e) { caught[0] = e; }
        });
        t.start(); t.join();
        assertNotNull(caught[0]);
        assertInstanceOf(IllegalStateException.class, caught[0]);
    }
}