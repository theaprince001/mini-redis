package io.miniredis.core;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Single-threaded keyspace. Every mutation goes through put / remove /
 * setExpiry / clearExpiry / flushAll, so Week 3 memory accounting and Week 4
 * AOF hooks land in one place.
 *
 * The store is ignorant of commands: commands decide when to propagate and
 * call ctx.propagate themselves.
 */
public final class MiniRedisStore {

    private final Map<ByteArrayWrapper, RedisObject> data = new HashMap<>();
    private final Map<ByteArrayWrapper, Long> deadlines = new HashMap<>();
    private final DeadlineIndex deadlineIndex = new DeadlineIndex();

    private volatile Thread ownerThread;

    /**
     * Set once by the server before any channel is accepted. Any other thread
     * reaching a mutating method fails immediately rather than as a heisenbug
     * in Week 5.
     */
    public void setOwnerThread(Thread t) { this.ownerThread = t; }

    // ------------------------------------------------------------------
    // Reads
    // ------------------------------------------------------------------

    /**
     * Read a live value. If the key exists but its deadline has passed, the
     * key is deleted and the deletion is reported so the caller can propagate
     * a DEL. Caller must propagate; the store does not.
     */
    public ReadResult read(ByteArrayWrapper key, long now) {
        checkOwner();
        RedisObject value = data.get(key);
        if (value == null) return ReadResult.missing();

        Long deadline = deadlines.get(key);
        if (deadline != null && deadline <= now) {
            removeInternal(key);
            return ReadResult.expired();
        }
        return ReadResult.found(value);
    }

    public int size() {
        checkOwner();
        return data.size();
    }

    public boolean containsKey(ByteArrayWrapper key) {
        checkOwner();
        return data.containsKey(key);
    }

    public long deadline(ByteArrayWrapper key) {
        checkOwner();
        Long d = deadlines.get(key);
        return d == null ? -1L : d;
    }

    // ------------------------------------------------------------------
    // Mutations
    // ------------------------------------------------------------------

    public void put(ByteArrayWrapper key, RedisObject value) {
        checkOwner();
        data.put(key, value);
    }

    public boolean remove(ByteArrayWrapper key) {
        checkOwner();
        return removeInternal(key);
    }

    public void setExpiry(ByteArrayWrapper key, long deadlineMs) {
        checkOwner();
        Long old = deadlines.put(key, deadlineMs);
        if (old != null) deadlineIndex.remove(new DeadlineIndex.Deadline(old, key));
        deadlineIndex.add(new DeadlineIndex.Deadline(deadlineMs, key));
    }

    public boolean clearExpiry(ByteArrayWrapper key) {
        checkOwner();
        Long old = deadlines.remove(key);
        if (old == null) return false;
        deadlineIndex.remove(new DeadlineIndex.Deadline(old, key));
        return true;
    }

    /**
     * Remove keys whose deadline has passed, in deadline order, up to the
     * nanosecond budget. Returns the keys removed so the caller can propagate
     * DELs. Budget is wall-clock work time, measured with nanoTime; `now` is
     * the epoch deadline to compare against.
     */
    public List<ByteArrayWrapper> sweepExpired(long now, long budgetNanos) {
        checkOwner();
        long start = System.nanoTime();
        List<ByteArrayWrapper> expired = new ArrayList<>();
        int n = 0;
        while (true) {
            DeadlineIndex.Deadline head = deadlineIndex.first();
            if (head == null || head.deadlineMs() > now) break;
            deadlineIndex.pollFirst();
            deadlines.remove(head.key());
            data.remove(head.key());
            expired.add(head.key());
            if ((++n & 15) == 0 && System.nanoTime() - start >= budgetNanos) break;
        }
        return expired;
    }

    public void flushAll() {
        checkOwner();
        data.clear();
        deadlines.clear();
        deadlineIndex.clear();
    }

    // ------------------------------------------------------------------
    // Internals
    // ------------------------------------------------------------------

    private boolean removeInternal(ByteArrayWrapper key) {
        boolean had = data.remove(key) != null;
        Long old = deadlines.remove(key);
        if (old != null) deadlineIndex.remove(new DeadlineIndex.Deadline(old, key));
        return had;
    }

    private void checkOwner() {
        Thread t = ownerThread;
        if (t != null && Thread.currentThread() != t) {
            throw new IllegalStateException(
                    "MiniRedisStore mutated from " + Thread.currentThread().getName()
                            + " but owner is " + t.getName());
        }
    }

    public record ReadResult(RedisObject value, boolean expiredOnRead) {
        public static ReadResult missing() { return new ReadResult(null, false); }
        public static ReadResult expired() { return new ReadResult(null, true); }
        public static ReadResult found(RedisObject v) { return new ReadResult(v, false); }
        public boolean found() { return value != null; }
    }
}