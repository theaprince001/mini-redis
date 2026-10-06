package io.miniredis.server;

import io.miniredis.protocol.RespValue;

import java.util.List;

public interface Command {
    RespValue execute(List<RespValue> args, Session session);
}