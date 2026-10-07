package io.miniredis.server.commands;

import io.miniredis.core.ByteArrayWrapper;
import io.miniredis.core.Command;
import io.miniredis.core.CommandContext;
import io.miniredis.protocol.RespValue;

import java.util.List;

public final class ExistsCommand implements Command {
    @Override
    public RespValue execute(CommandContext ctx, List<RespValue> args) {
        long count = 0;
        for (int i = 1; i < args.size(); i++) {
            ByteArrayWrapper key = StringArgs.key(args.get(i));
            if (new MiniRedisStoreAccess(ctx).read(key).exists()) count++;
        }
        return new RespValue.Integer(count);
    }
}