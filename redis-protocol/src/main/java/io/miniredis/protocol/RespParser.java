package io.miniredis.protocol;

import io.netty.buffer.ByteBuf;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Streaming RESP2 parser.
 *
 * General parser: parse() accepts any RESP value (used for replies, e.g. by a
 * future replica).
 * Request parser: parseRequest() accepts ONLY a flat array of bulk strings,
 * which is the shape every Redis client sends.
 *
 * Both return null when the buffer does not contain a full value; the reader
 * index is unchanged in that case. Both throw RespProtocolException on
 * malformed input.
 */
public final class RespParser {

    public static final int MAX_BULK  = 8 * 1024 * 1024;
    public static final int MAX_ARRAY = 1_048_576;
    public static final int MAX_LINE  = 64 * 1024;
    public static final int MAX_DEPTH = 32;

    private static final byte CR = '\r';
    private static final byte LF = '\n';

    private static final class IncompleteFrame extends RuntimeException {
        IncompleteFrame() { super(null, null, false, false); }
    }

    private RespParser() {}

    // ------------------------------------------------------------------
    // General parser (any RESP value)
    // ------------------------------------------------------------------

    public static RespValue parse(ByteBuf buf) {
        int mark = buf.readerIndex();
        try {
            return parseValue(buf, 0);
        } catch (IncompleteFrame e) {
            buf.readerIndex(mark);
            return null;
        }
    }

    private static RespValue parseValue(ByteBuf buf, int depth) {
        if (depth > MAX_DEPTH) {
            throw new RespProtocolException("Protocol error: nesting depth exceeded");
        }
        if (!buf.isReadable()) throw new IncompleteFrame();
        byte prefix = buf.readByte();
        return switch (prefix) {
            case '+' -> new RespValue.SimpleString(readLine(buf));
            case '-' -> new RespValue.Error(readLine(buf));
            case ':' -> new RespValue.Integer(parseIntegerStrict(buf));
            case '$' -> parseBulk(buf);
            case '*' -> parseArray(buf, depth);
            default -> throw new RespProtocolException(
                    "Protocol error: unknown prefix byte '" + (char) prefix + "'");
        };
    }

    private static RespValue parseBulk(ByteBuf buf) {
        int len = parseLength(buf, "bulk length", MAX_BULK, true);
        if (len == -1) return new RespValue.NullBulk();
        if (len < 0) throw new RespProtocolException("Protocol error: invalid bulk length " + len);
        if (buf.readableBytes() < len + 2) throw new IncompleteFrame();
        byte[] data = new byte[len];
        buf.readBytes(data);
        expectCrlf(buf);
        return new RespValue.Bulk(data);
    }

    private static RespValue parseArray(ByteBuf buf, int depth) {
        int count = parseLength(buf, "array length", MAX_ARRAY, true);
        if (count == -1) return new RespValue.NullArray();
        if (count < 0) throw new RespProtocolException("Protocol error: invalid array length " + count);
        List<RespValue> values = new ArrayList<>(Math.min(count, 16));
        for (int i = 0; i < count; i++) values.add(parseValue(buf, depth + 1));
        return new RespValue.Array(values);
    }

    // ------------------------------------------------------------------
    // Request parser (flat array of bulk strings)
    // ------------------------------------------------------------------

    public static RespValue parseRequest(ByteBuf buf) {
        int mark = buf.readerIndex();
        try {
            if (!buf.isReadable()) throw new IncompleteFrame();
            if (buf.getByte(buf.readerIndex()) != '*') {
                throw new RespProtocolException("Protocol error: expected array");
            }
            buf.skipBytes(1);
            int count = parseLength(buf, "array length", MAX_ARRAY, false);
            if (count < 1) {
                throw new RespProtocolException("Protocol error: invalid array length " + count);
            }
            List<RespValue> values = new ArrayList<>(Math.min(count, 16));
            for (int i = 0; i < count; i++) {
                if (!buf.isReadable()) throw new IncompleteFrame();
                if (buf.getByte(buf.readerIndex()) != '$') {
                    throw new RespProtocolException("Protocol error: expected bulk string");
                }
                buf.skipBytes(1);
                int len = parseLength(buf, "bulk length", MAX_BULK, false);
                if (len < 0) {
                    throw new RespProtocolException("Protocol error: invalid bulk length " + len);
                }
                if (buf.readableBytes() < len + 2) throw new IncompleteFrame();
                byte[] data = new byte[len];
                buf.readBytes(data);
                expectCrlf(buf);
                values.add(new RespValue.Bulk(data));
            }
            return new RespValue.Array(values);
        } catch (IncompleteFrame e) {
            buf.readerIndex(mark);
            return null;
        }
    }

    // ------------------------------------------------------------------
    // Primitive readers
    // ------------------------------------------------------------------

    /** Scan for CRLF within MAX_LINE bytes. Returns index of CR. */
    private static int scanToCrlf(ByteBuf buf) {
        int start = buf.readerIndex();
        int end = buf.writerIndex();
        if (end - start < 2) throw new IncompleteFrame();

        int maxCrPos = start + MAX_LINE;
        int scanEnd = Math.min(end - 1, maxCrPos + 1);
        for (int i = start; i < scanEnd; i++) {
            if (buf.getByte(i) == CR && buf.getByte(i + 1) == LF) return i;
        }
        if (end - 1 > maxCrPos) {
            throw new RespProtocolException("Protocol error: line too long");
        }
        throw new IncompleteFrame();
    }

    /** Read a length. max must fit in int. allowMinusOne permits the -1 sentinel. */
    private static int parseLength(ByteBuf buf, String what, int max, boolean allowMinusOne) {
        int crlf = scanToCrlf(buf);
        int start = buf.readerIndex();

        int i = start;
        boolean negative = false;
        if (buf.getByte(i) == '-') { negative = true; i++; }
        if (i >= crlf) throw new RespProtocolException("Protocol error: invalid " + what);

        long value = 0;
        while (i < crlf) {
            int d = buf.getByte(i) - '0';
            if (d < 0 || d > 9) {
                throw new RespProtocolException("Protocol error: invalid " + what);
            }
            value = value * 10 + d;
            if (value > 1_000_000_000_000_000L) {
                throw new RespProtocolException("Protocol error: " + what + " too large");
            }
            i++;
        }
        buf.readerIndex(crlf + 2);

        long result = negative ? -value : value;
        if (allowMinusOne && result == -1) return -1;
        if (result < 0 || result > max) {
            throw new RespProtocolException("Protocol error: invalid " + what + " " + result);
        }
        return (int) result;
    }

    /** Read an integer in the full long range. Used by the general parser. */
    private static long parseIntegerStrict(ByteBuf buf) {
        int crlf = scanToCrlf(buf);
        int start = buf.readerIndex();

        int i = start;
        boolean negative = false;
        if (buf.getByte(i) == '-') { negative = true; i++; }
        if (i >= crlf) throw new RespProtocolException("Protocol error: invalid integer");

        long limit = negative ? Long.MIN_VALUE : -Long.MAX_VALUE;
        long acc = 0;
        while (i < crlf) {
            int d = buf.getByte(i) - '0';
            if (d < 0 || d > 9) {
                throw new RespProtocolException("Protocol error: invalid integer");
            }
            if (acc < (limit + d) / 10) {
                throw new RespProtocolException("Protocol error: integer out of range");
            }
            acc = acc * 10 - d;
            i++;
        }
        buf.readerIndex(crlf + 2);
        return negative ? acc : -acc;
    }

    private static String readLine(ByteBuf buf) {
        int crlf = scanToCrlf(buf);
        int start = buf.readerIndex();
        byte[] bytes = new byte[crlf - start];
        buf.readBytes(bytes);
        buf.skipBytes(2);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private static void expectCrlf(ByteBuf buf) {
        if (buf.readableBytes() < 2) throw new IncompleteFrame();
        byte cr = buf.readByte();
        byte lf = buf.readByte();
        if (cr != CR || lf != LF) {
            throw new RespProtocolException("Protocol error: expected CRLF");
        }
    }
}