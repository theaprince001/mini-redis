package io.miniredis.protocol;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class RespParserLimitTest {

    private static ByteBuf ascii(String s) {
        return Unpooled.copiedBuffer(s, StandardCharsets.US_ASCII);
    }

    @Test void rejectsBulkLengthOverMax() {
        assertThrows(RespProtocolException.class,
                () -> RespParser.parse(ascii("$" + (RespParser.MAX_BULK + 1L) + "\r\n")));
    }

    @Test void rejectsBulkLengthThatWouldTruncateInt() {
        assertThrows(RespProtocolException.class,
                () -> RespParser.parse(ascii("$4294967296\r\n")));
    }

    @Test void rejectsArrayCountOverMax() {
        assertThrows(RespProtocolException.class,
                () -> RespParser.parse(ascii("*" + (RespParser.MAX_ARRAY + 1L) + "\r\n")));
    }

    @Test void rejectsLineTooLong() {
        ByteBuf b = Unpooled.buffer();
        b.writeByte('+');
        for (int i = 0; i < RespParser.MAX_LINE + 16; i++) b.writeByte('A');
        assertThrows(RespProtocolException.class, () -> RespParser.parse(b));
    }

    @Test void rejectsNestingTooDeep() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < RespParser.MAX_DEPTH + 4; i++) sb.append("*1\r\n");
        sb.append("$0\r\n\r\n");
        assertThrows(RespProtocolException.class,
                () -> RespParser.parse(ascii(sb.toString())));
    }

    @Test void rejectsPlusPrefixedLength() {
        assertThrows(RespProtocolException.class, () -> RespParser.parse(ascii("$+5\r\n")));
    }

    @Test void rejectsNonAsciiDigits() {
        ByteBuf b = Unpooled.buffer();
        b.writeByte('$');
        b.writeByte(0xD9); b.writeByte(0xA0);
        b.writeBytes(new byte[]{'\r','\n'});
        assertThrows(RespProtocolException.class, () -> RespParser.parse(b));
    }

    @Test void rejectsWhitespaceInLength() {
        assertThrows(RespProtocolException.class, () -> RespParser.parse(ascii("$ 5\r\n")));
    }
}