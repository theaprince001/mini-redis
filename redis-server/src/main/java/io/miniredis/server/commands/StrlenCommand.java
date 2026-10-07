package io.miniredis.server.commands;

import io.miniredis.core.ByteArrayWrapper;
import io.miniredis.core.Command;
import io.miniredis.core.CommandContext;
import io.miniredis.core.RedisType;
import io.miniredis.protocol.RespValue;

import java.util.List;

public final class StrlenCommand implements Command {
    @Override
    public RespValue execute(CommandContext ctx, List<RespValue> args) {
        ByteArrayWrapper key = StringArgs.key(args.get(1));
        MiniRedisStoreAccess.Read r = new MiniRedisStoreAccess(ctx).read(key);
        if (!r.exists()) return new RespValue.Integer(0);
        if (r.type() != RedisType.STRING) {
            return new RespValue.Error(
                    "WRONGTYPE Operation against a key holding the wrong kind of value");
        }
        return new RespValue.Integer(r.bytes().length);
    }
}