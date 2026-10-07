package io.miniredis.server.commands;

import io.miniredis.core.ByteArrayWrapper;
import io.miniredis.core.Command;
import io.miniredis.core.CommandContext;
import io.miniredis.core.Limits;
import io.miniredis.core.RedisObject;
import io.miniredis.core.RedisType;
import io.miniredis.protocol.RespValue;

import java.util.ArrayList;
import java.util.List;

public final class AppendCommand implements Command {

    @Override
    public RespValue execute(CommandContext ctx, List<RespValue> args) {
        byte[] kb = StringArgs.bytes(args.get(1));
        byte[] suffix = StringArgs.bytes(args.get(2));
        ByteArrayWrapper key = new ByteArrayWrapper(kb);
        MiniRedisStoreAccess.Read r = new MiniRedisStoreAccess(ctx).read(key);

        byte[] combined;
        if (!r.exists()) {
            combined = suffix;
        } else {
            if (r.type() != RedisType.STRING) {
                return new RespValue.Error(
                        "WRONGTYPE Operation against a key holding the wrong kind of value");
            }
            byte[] old = r.bytes();
            long total = (long) old.length + suffix.length;
            if (total > Limits.MAX_STRING_SIZE) {
                return new RespValue.Error(
                        "ERR string exceeds maximum allowed size (proto-max-bulk-len)");
            }
            combined = new byte[(int) total];
            System.arraycopy(old, 0, combined, 0, old.length);
            System.arraycopy(suffix, 0, combined, old.length, suffix.length);
        }

        ctx.store().put(key, RedisObject.string(combined));
        // TTL preserved locally: put does not touch the deadline.

        // Propagate the original argv verbatim. APPEND is deterministic given
        // identical state; the replica produces the same result and keeps its
        // own TTL. Rewriting as SET would write the full value on every append.
        List<byte[]> prop = new ArrayList<>(args.size());
        for (RespValue a : args) prop.add(StringArgs.bytes(a));
        ctx.propagate(prop);

        return new RespValue.Integer(combined.length);
    }
}