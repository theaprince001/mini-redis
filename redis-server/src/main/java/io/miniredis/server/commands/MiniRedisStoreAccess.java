package io.miniredis.server.commands;

import io.miniredis.core.ByteArrayWrapper;
import io.miniredis.core.CommandContext;
import io.miniredis.core.MiniRedisStore;
import io.miniredis.core.RedisObject;
import io.miniredis.core.RedisType;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Thin wrapper around MiniRedisStore for command code. Handles the
 * lazy-expiry DEL propagation so every command gets it for free.
 */
final class MiniRedisStoreAccess {

    private final CommandContext ctx;

    MiniRedisStoreAccess(CommandContext ctx) { this.ctx = ctx; }

    Read read(ByteArrayWrapper key) {
        MiniRedisStore.ReadResult r = ctx.store().read(key, ctx.now());
        if (r.expiredOnRead()) {
            // Lazy expiry: propagate a DEL so AOF and replicas see the delete.
            ctx.propagate(List.of(
                    "DEL".getBytes(StandardCharsets.US_ASCII),
                    key.bytes()));
        }
        if (!r.found()) return Read.missing();
        RedisObject obj = r.value();
        return new Read(true, obj.type(),
                obj.type() == RedisType.STRING ? obj.stringBytes() : null);
    }

    record Read(boolean exists, RedisType type, byte[] bytes) {
        static Read missing() { return new Read(false, null, null); }
    }
}