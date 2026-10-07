package io.miniredis.server.commands;

import io.miniredis.core.ByteArrayWrapper;
import io.miniredis.core.Command;
import io.miniredis.core.CommandContext;
import io.miniredis.core.RedisObject;
import io.miniredis.core.RedisType;
import io.miniredis.core.StrictNumbers;
import io.miniredis.protocol.RespValue;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public final class IncrCommand implements Command {

    @Override
    public RespValue execute(CommandContext ctx, List<RespValue> args) {
        return incrBy(ctx, args, 1L);
    }

    static RespValue incrBy(CommandContext ctx, List<RespValue> args, long delta) {
        byte[] kb = StringArgs.bytes(args.get(1));
        ByteArrayWrapper key = new ByteArrayWrapper(kb);
        MiniRedisStoreAccess.Read r = new MiniRedisStoreAccess(ctx).read(key);

        long current = 0L;
        if (r.exists()) {
            if (r.type() != RedisType.STRING) {
                return new RespValue.Error(
                        "WRONGTYPE Operation against a key holding the wrong kind of value");
            }
            if (!StrictNumbers.isStrictLong(r.bytes())) {
                return new RespValue.Error("ERR value is not an integer or out of range");
            }
            current = StrictNumbers.parseStrictLong(r.bytes());
        }

        long next;
        try {
            next = Math.addExact(current, delta);
        } catch (ArithmeticException e) {
            return new RespValue.Error("ERR increment or decrement would overflow");
        }

        byte[] nextBytes = Long.toString(next).getBytes(StandardCharsets.US_ASCII);
        ctx.store().put(key, RedisObject.string(nextBytes));
        // TTL preserved locally: put does not touch the deadline.

        // Propagate the original argv verbatim. INCR and DECR are deterministic
        // given identical state, so the replica applies the same computation
        // and preserves its own TTL exactly as the master did.
        List<byte[]> prop = new ArrayList<>(args.size());
        for (RespValue a : args) prop.add(StringArgs.bytes(a));
        ctx.propagate(prop);

        return new RespValue.Integer(next);
    }
}