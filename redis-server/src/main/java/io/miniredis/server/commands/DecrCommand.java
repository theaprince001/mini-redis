package io.miniredis.server.commands;

import io.miniredis.core.Command;
import io.miniredis.core.CommandContext;
import io.miniredis.protocol.RespValue;

import java.util.List;

public final class DecrCommand implements Command {
    @Override
    public RespValue execute(CommandContext ctx, List<RespValue> args) {
        return IncrCommand.incrBy(ctx, args, -1L);
    }
}