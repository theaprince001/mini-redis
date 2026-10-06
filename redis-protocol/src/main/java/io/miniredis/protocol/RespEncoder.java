package io.miniredis.protocol;

import io.netty.buffer.ByteBuf;

import java.nio.charset.StandardCharsets;
import java.util.List;

public final class RespEncoder {

    private static final byte[] CRLF       = {'\r', '\n'};
    private static final byte[] NULL_BULK  = "$-1\r\n".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] NULL_ARRAY = "*-1\r\n".getBytes(StandardCharsets.US_ASCII);

    private RespEncoder() {}

    public static void encode(ByteBuf out, RespValue value) {
        switch (value) {
            case RespValue.SimpleString s -> {
                out.writeByte('+');
                writeSanitizedLine(out, s.value());
                out.writeBytes(CRLF);
            }
            case RespValue.Error e -> {
                out.writeByte('-');
                writeSanitizedLine(out, e.value());
                out.writeBytes(CRLF);
            }
            case RespValue.Integer i -> {
                out.writeByte(':');
                writeLongAscii(out, i.value());
                out.writeBytes(CRLF);
            }
            case RespValue.Bulk b -> {
                out.writeByte('$');
                writeLongAscii(out, b.value().length);
                out.writeBytes(CRLF);
                out.writeBytes(b.value());
                out.writeBytes(CRLF);
            }
            case RespValue.NullBulk ignored -> out.writeBytes(NULL_BULK);
            case RespValue.Array a -> {
                List<RespValue> values = a.values();
                out.writeByte('*');
                writeLongAscii(out, values.size());
                out.writeBytes(CRLF);
                for (RespValue v : values) encode(out, v);
            }
            case RespValue.NullArray ignored -> out.writeBytes(NULL_ARRAY);
        }
    }

    /** SimpleString and Error are line-delimited. Replace \r and \n with ' '. */
    private static void writeSanitizedLine(ByteBuf out, String s) {
        byte[] bytes = s.getBytes(StandardCharsets.UTF_8);
        for (byte b : bytes) {
            out.writeByte((b == '\r' || b == '\n') ? (byte) ' ' : b);
        }
    }

    private static void writeLongAscii(ByteBuf out, long v) {
        if (v == 0) { out.writeByte('0'); return; }
        if (v < 0) { out.writeByte('-'); v = -v; }
        int digits = 1;
        long tmp = v;
        while (tmp >= 10) { digits++; tmp /= 10; }
        int writerIdx = out.writerIndex();
        out.ensureWritable(digits);
        for (int i = digits - 1; i >= 0; i--) {
            out.setByte(writerIdx + i, (byte) ('0' + (v % 10)));
            v /= 10;
        }
        out.writerIndex(writerIdx + digits);
    }
}