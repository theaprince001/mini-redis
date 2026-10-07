package io.miniredis.server.commands;

import io.miniredis.core.ByteArrayWrapper;
import io.miniredis.core.Command;
import io.miniredis.core.CommandContext;
import io.miniredis.protocol.RespValue;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public final class DelCommand implements Command {

    private static final byte[] DEL = "DEL".getBytes(StandardCharsets.US_ASCII);

    @Override
    public RespValue execute(CommandContext ctx, List<RespValue> args) {
        List<byte[]> toPropagate = new ArrayList<>();
        toPropagate.add(DEL);
        long removed = 0;
        for (int i = 1; i < args.size(); i++) {
            byte[] kb = StringArgs.bytes(args.get(i));
            ByteArrayWrapper key = new ByteArrayWrapper(kb);
            // Read first to trigger lazy-expiry propagation. If the key is
            // expired but not yet swept, this emits a DEL to AOF/replicas
            // before we remove the key ourselves. Do not "optimize" the read
            // away.
            new MiniRedisStoreAccess(ctx).read(key);
            if (ctx.store().remove(key)) {
                removed++;
                toPropagate.add(kb);
            }
        }
        if (toPropagate.size() > 1) ctx.propagate(toPropagate);
        return new RespValue.Integer(removed);
    }
}