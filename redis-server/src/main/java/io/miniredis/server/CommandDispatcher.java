package io.miniredis.server;

import io.miniredis.core.Command;
import io.miniredis.core.CommandContext;
import io.miniredis.core.CommandSpec;
import io.miniredis.protocol.RespValue;

import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class CommandDispatcher {

    private static final int MAX_COMMAND_NAME_LEN = 64;
    private static final int MAX_ECHOED_NAME = 128;

    public record Registered(CommandSpec spec, Command command) {}

    private final Map<String, Registered> registry = new HashMap<>();

    public void register(CommandSpec spec, Command command) {
        registry.put(spec.name().toUpperCase(Locale.ROOT), new Registered(spec, command));
    }

    public Collection<Registered> all() { return registry.values(); }

    public RespValue dispatch(CommandContext ctx, RespValue.Array request) {
        List<RespValue> args = request.values();
        if (args.isEmpty()) return new RespValue.Error("ERR empty command");

        if (!(args.get(0) instanceof RespValue.Bulk nameBulk)) {
            return new RespValue.Error("ERR command name must be a bulk string");
        }
        byte[] raw = nameBulk.value();
        if (raw.length == 0 || raw.length > MAX_COMMAND_NAME_LEN) {
            return unknownCommand(raw);
        }
        for (byte b : raw) {
            int c = b & 0xFF;
            boolean ok = (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || c == '_';
            if (!ok) return unknownCommand(raw);
        }

        String name = new String(raw, StandardCharsets.US_ASCII).toUpperCase(Locale.ROOT);
        Registered r = registry.get(name);
        if (r == null) return unknownCommand(raw);

        if (!r.spec.acceptsArgCount(args.size())) {
            return new RespValue.Error(
                    "ERR wrong number of arguments for '" + name.toLowerCase(Locale.ROOT)
                            + "' command");
        }

        return r.command.execute(ctx, args);
    }

    private static RespValue unknownCommand(byte[] raw) {
        return new RespValue.Error("ERR unknown command '" + sanitizeForError(raw) + "'");
    }

    private static String sanitizeForError(byte[] raw) {
        int n = Math.min(raw.length, MAX_ECHOED_NAME);
        StringBuilder sb = new StringBuilder(n + 3);
        for (int i = 0; i < n; i++) {
            int b = raw[i] & 0xFF;
            char c = (b >= 0x20 && b < 0x7F) ? Character.toUpperCase((char) b) : '?';
            sb.append(c);
        }
        if (raw.length > MAX_ECHOED_NAME) sb.append("...");
        return sb.toString();
    }
}