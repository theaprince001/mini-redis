package io.miniredis.server;

import io.miniredis.core.Command;
import io.miniredis.core.CommandSpec;
import io.miniredis.server.commands.*;

import java.util.Set;

import static io.miniredis.core.CommandSpec.Flag.*;

public final class Commands {

    private Commands() {}

    public static void registerAll(CommandDispatcher d) {
        reg(d, "PING",   -1, Set.of(FAST),             0, 0, 0, new PingCommand());
        reg(d, "ECHO",    2, Set.of(FAST),             0, 0, 0, new EchoCommand());
        reg(d, "QUIT",    1, Set.of(FAST),             0, 0, 0, new QuitCommand());

        reg(d, "SET",    -3, Set.of(WRITE, DENYOOM),   1, 1, 1, new SetCommand());
        reg(d, "GET",     2, Set.of(READONLY, FAST),   1, 1, 1, new GetCommand());
        reg(d, "DEL",    -2, Set.of(WRITE),            1,-1, 1, new DelCommand());
        reg(d, "EXISTS", -2, Set.of(READONLY, FAST),   1,-1, 1, new ExistsCommand());
        reg(d, "INCR",    2, Set.of(WRITE, DENYOOM, FAST), 1, 1, 1, new IncrCommand());
        reg(d, "DECR",    2, Set.of(WRITE, DENYOOM, FAST), 1, 1, 1, new DecrCommand());
        reg(d, "APPEND",  3, Set.of(WRITE, DENYOOM),   1, 1, 1, new AppendCommand());
        reg(d, "STRLEN",  2, Set.of(READONLY, FAST),   1, 1, 1, new StrlenCommand());
    }

    private static void reg(CommandDispatcher d, String name, int arity,
                            Set<CommandSpec.Flag> flags, int firstKey, int lastKey, int step,
                            Command cmd) {
        d.register(new CommandSpec(name, arity, flags, firstKey, lastKey, step), cmd);
    }
}