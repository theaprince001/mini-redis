package io.miniredis.core;

import java.util.TreeSet;

/**
 * Deadline index. Every TTL change is O(log n) and every sweep is exact:
 * pop entries whose deadline has passed. No sampling heuristic is needed.
 *
 * Only {@link MiniRedisStore} may touch this; the map and the index must not
 * drift apart. The active sweep itself lives in the store and is scheduled
 * by the server on the worker event loop.
 */
public final class DeadlineIndex {

    public record Deadline(long deadlineMs, ByteArrayWrapper key)
            implements Comparable<Deadline> {
        @Override
        public int compareTo(Deadline o) {
            int c = Long.compare(deadlineMs, o.deadlineMs);
            return c != 0 ? c : key.compareTo(o.key);
        }
    }

    private final TreeSet<Deadline> queue = new TreeSet<>();

    public void add(Deadline d) { queue.add(d); }
    public void remove(Deadline d) { queue.remove(d); }
    public Deadline first() { return queue.isEmpty() ? null : queue.first(); }
    public Deadline pollFirst() { return queue.isEmpty() ? null : queue.pollFirst(); }
    public void clear() { queue.clear(); }
    public int size() { return queue.size(); }
}