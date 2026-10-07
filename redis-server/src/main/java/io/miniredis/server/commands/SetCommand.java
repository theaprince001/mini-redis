package io.miniredis.server.commands;

import io.miniredis.core.ByteArrayWrapper;
import io.miniredis.core.Command;
import io.miniredis.core.CommandContext;
import io.miniredis.core.Limits;
import io.miniredis.core.RedisObject;
import io.miniredis.core.RedisType;
import io.miniredis.core.StrictNumbers;
import io.miniredis.protocol.RespValue;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * SET key value [NX | XX] [GET] [EX s | PX ms | EXAT ts | PXAT ts | KEEPTTL]
 *
 * Propagation transform: options are stripped. The transformed form is
 *   SET key value                 (no TTL)
 *   SET key value PXAT <abs-ms>   (relative or absolute TTL)
 *   SET key value KEEPTTL         (KEEPTTL)
 * NX/XX/GET are conditions; they were already evaluated by this instance.
 * Re-evaluating them on a replica could fail because the replica's state may
 * match the post-condition state.
 */
public final class SetCommand implements Command {

    private static final byte[] SET    = "SET".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] PXAT   = "PXAT".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] KEEPTTL= "KEEPTTL".getBytes(StandardCharsets.US_ASCII);

    private static final long NO_TTL     = -1L;
    private static final long KEEP_TTL   = -2L;

    @Override
    public RespValue execute(CommandContext ctx, List<RespValue> args) {
        byte[] keyBytes = StringArgs.bytes(args.get(1));
        byte[] valueBytes = StringArgs.bytes(args.get(2));

        // Defence in depth: the parser already caps a bulk at MAX_BULK (8 MB),
        // which equals Limits.MAX_STRING_SIZE. This check will not fire in
        // practice unless one of the constants moves. Kept so the invariant
        // is stated in code, not just implied by the parser.
        if (valueBytes.length > Limits.MAX_STRING_SIZE) {
            return new RespValue.Error(
                    "ERR string exceeds maximum allowed size (proto-max-bulk-len)");
        }

        boolean nx = false, xx = false, get = false;
        long ttlMs = NO_TTL;
        boolean ttlSet = false;

        int i = 3;
        while (i < args.size()) {
            String opt = new String(StringArgs.bytes(args.get(i)), StandardCharsets.US_ASCII)
                    .toUpperCase(Locale.ROOT);
            switch (opt) {
                case "NX" -> {
                    if (nx || xx) return syntaxError();
                    nx = true; i++;
                }
                case "XX" -> {
                    if (xx || nx) return syntaxError();
                    xx = true; i++;
                }
                case "GET" -> {
                    if (get) return syntaxError();
                    get = true; i++;
                }
                case "KEEPTTL" -> {
                    if (ttlSet) return syntaxError();
                    ttlMs = KEEP_TTL; ttlSet = true; i++;
                }
                case "EX", "PX", "EXAT", "PXAT" -> {
                    if (ttlSet || i + 1 >= args.size()) return syntaxError();
                    byte[] raw = StringArgs.bytes(args.get(i + 1));
                    if (!StrictNumbers.isStrictLong(raw)) {
                        return new RespValue.Error(
                                "ERR value is not an integer or out of range");
                    }
                    long n = StrictNumbers.parseStrictLong(raw);
                    if (n <= 0) {
                        return new RespValue.Error(
                                "ERR invalid expire time in 'set' command");
                    }
                    long computed;
                    try {
                        computed = switch (opt) {
                            case "EX"   -> Math.multiplyExact(n, 1000L);
                            case "PX"   -> n;
                            case "EXAT" -> Math.multiplyExact(n, 1000L);
                            case "PXAT" -> n;
                            default -> throw new IllegalStateException();
                        };
                    } catch (ArithmeticException e) {
                        return new RespValue.Error(
                                "ERR invalid expire time in 'set' command");
                    }
                    if (opt.equals("EX") || opt.equals("PX")) {
                        try {
                            computed = Math.addExact(ctx.now(), computed);
                        } catch (ArithmeticException e) {
                            return new RespValue.Error(
                                    "ERR invalid expire time in 'set' command");
                        }
                    }
                    ttlMs = computed;
                    ttlSet = true;
                    i += 2;
                }
                default -> { return syntaxError(); }
            }
        }

        ByteArrayWrapper key = new ByteArrayWrapper(keyBytes);
        MiniRedisStoreAccess access = new MiniRedisStoreAccess(ctx);
        MiniRedisStoreAccess.Read old = access.read(key);

        RespValue getReply = null;
        if (get) {
            if (old.exists()) {
                if (old.type() != RedisType.STRING) {
                    return new RespValue.Error(
                            "WRONGTYPE Operation against a key holding the wrong kind of value");
                }
                getReply = new RespValue.Bulk(old.bytes());
            } else {
                getReply = new RespValue.NullBulk();
            }
        }

        boolean exists = old.exists();
        if (nx && exists) return get ? getReply : new RespValue.NullBulk();
        if (xx && !exists) return get ? getReply : new RespValue.NullBulk();

        ctx.store().put(key, RedisObject.string(valueBytes));

        if (ttlMs == KEEP_TTL) {
            // Leave any existing deadline in place.
        } else if (ttlSet) {
            ctx.store().setExpiry(key, ttlMs);
        } else {
            // Plain SET clears any existing TTL.
            ctx.store().clearExpiry(key);
        }

        // Build the transformed command. Conditions are stripped; the
        // conditional has already been resolved on this instance.
        List<byte[]> prop = new ArrayList<>(5);
        prop.add(SET);
        prop.add(keyBytes);
        prop.add(valueBytes);
        if (ttlMs == KEEP_TTL) {
            prop.add(KEEPTTL);
        } else if (ttlSet) {
            prop.add(PXAT);
            prop.add(Long.toString(ttlMs).getBytes(StandardCharsets.US_ASCII));
        }
        ctx.propagate(prop);

        return get ? getReply : new RespValue.SimpleString("OK");
    }

    private static RespValue syntaxError() {
        return new RespValue.Error("ERR syntax error");
    }
}