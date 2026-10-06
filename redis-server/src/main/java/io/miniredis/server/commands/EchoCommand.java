package io.miniredis.server.commands;

import io.miniredis.protocol.RespValue;
import io.miniredis.server.Command;
import io.miniredis.server.Session;

import java.util.List;

public final class EchoCommand implements Command {
    @Override
    public RespValue execute(List<RespValue> args, Session session) {
        if (args.size() != 2 || !(args.get(1) instanceof RespValue.Bulk b)) {
            return new RespValue.Error("ERR wrong number of arguments for 'echo' command");
        }
        return new RespValue.Bulk(b.value());
    }
}