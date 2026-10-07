package io.miniredis.core;

import io.miniredis.protocol.RespValue;

import java.util.List;

public interface Command {
    RespValue execute(CommandContext ctx, List<RespValue> args);
}