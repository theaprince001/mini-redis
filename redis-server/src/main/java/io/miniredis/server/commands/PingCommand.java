package io.miniredis.server.commands;

import io.miniredis.core.Command;
import io.miniredis.core.CommandContext;
import io.miniredis.protocol.RespValue;

import java.util.List;

public final class PingCommand implements Command {
    @Override
    public RespValue execute(CommandContext ctx, List<RespValue> args) {
        if (args.size() == 1) return new RespValue.SimpleString("PONG");
        if (args.size() == 2 && args.get(1) instanceof RespValue.Bulk b) {
            return new RespValue.Bulk(b.value());
        }
        return new RespValue.Error("ERR wrong number of arguments for 'ping' command");
    }
}