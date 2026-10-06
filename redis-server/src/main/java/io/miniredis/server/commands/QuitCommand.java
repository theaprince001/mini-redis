package io.miniredis.server.commands;

import io.miniredis.protocol.RespValue;
import io.miniredis.server.Command;
import io.miniredis.server.Session;

import java.util.List;

public final class QuitCommand implements Command {
    @Override
    public RespValue execute(List<RespValue> args, Session session) {
        session.requestClose();
        return new RespValue.SimpleString("OK");
    }
}