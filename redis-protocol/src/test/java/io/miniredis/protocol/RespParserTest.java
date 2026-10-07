package io.miniredis.protocol;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RespParserTest {

    private static ByteBuf buf(String s) {
        return Unpooled.copiedBuffer(s, StandardCharsets.UTF_8);
    }

    @Test void parsesSimpleString() {
        ByteBuf b = buf("+OK\r\n");
        assertEquals(new RespValue.SimpleString("OK"), RespParser.parse(b));
        assertEquals(0, b.readableBytes());
    }

    @Test void parsesError() {
        assertEquals(new RespValue.Error("ERR nope"), RespParser.parse(buf("-ERR nope\r\n")));
    }

    @Test void parsesInteger() {
        assertEquals(new RespValue.Integer(12345), RespParser.parse(buf(":12345\r\n")));
    }

    @Test void parsesLongMaxValue() {
        assertEquals(new RespValue.Integer(Long.MAX_VALUE),
                RespParser.parse(buf(":9223372036854775807\r\n")));
    }

    @Test void parsesLongMinValue() {
        assertEquals(new RespValue.Integer(Long.MIN_VALUE),
                RespParser.parse(buf(":-9223372036854775808\r\n")));
    }

    @Test void rejectsLongMaxPlusOne() {
        assertThrows(RespProtocolException.class,
                () -> RespParser.parse(buf(":9223372036854775808\r\n")));
    }

    @Test void rejectsLongMinMinusOne() {
        assertThrows(RespProtocolException.class,
                () -> RespParser.parse(buf(":-9223372036854775809\r\n")));
    }

    @Test void rejectsEmptyInteger() {
        assertThrows(RespProtocolException.class, () -> RespParser.parse(buf(":\r\n")));
    }

    @Test void rejectsLoneMinus() {
        assertThrows(RespProtocolException.class, () -> RespParser.parse(buf(":-\r\n")));
    }

    @Test void rejectsPlusPrefixedInteger() {
        assertThrows(RespProtocolException.class, () -> RespParser.parse(buf(":+5\r\n")));
    }

    @Test void parsesNegativeInteger() {
        assertEquals(new RespValue.Integer(-7), RespParser.parse(buf(":-7\r\n")));
    }

    @Test void parsesBulk() {
        assertEquals(new RespValue.Bulk("hello".getBytes(StandardCharsets.UTF_8)),
                RespParser.parse(buf("$5\r\nhello\r\n")));
    }

    @Test void parsesNullBulk() {
        assertEquals(new RespValue.NullBulk(), RespParser.parse(buf("$-1\r\n")));
    }

    @Test void parsesCommandArray() {
        RespValue v = RespParser.parse(buf("*1\r\n$4\r\nPING\r\n"));
        assertInstanceOf(RespValue.Array.class, v);
        assertEquals(List.of(new RespValue.Bulk("PING".getBytes(StandardCharsets.UTF_8))),
                ((RespValue.Array) v).values());
    }

    @Test void returnsNullOnPartialSimpleString() {
        ByteBuf b = buf("+OK");
        assertNull(RespParser.parse(b));
        assertEquals(3, b.readableBytes());
    }

    @Test void returnsNullOnPartialBulkBody() {
        ByteBuf b = buf("$5\r\nhel");
        assertNull(RespParser.parse(b));
        assertEquals(7, b.readableBytes());
    }

    @Test void returnsNullOnPartialArray() {
        assertNull(RespParser.parse(buf("*2\r\n$3\r\nGET\r\n")));
    }

    @Test void parsesTwoValuesInSequence() {
        ByteBuf b = buf("+OK\r\n:1\r\n");
        assertEquals(new RespValue.SimpleString("OK"), RespParser.parse(b));
        assertEquals(new RespValue.Integer(1), RespParser.parse(b));
        assertEquals(0, b.readableBytes());
    }

    @Test void rejectsUnknownPrefix() {
        assertThrows(RespProtocolException.class, () -> RespParser.parse(buf("!wat\r\n")));
    }

    @Test void rejectsBadBulkLength() {
        assertThrows(RespProtocolException.class, () -> RespParser.parse(buf("$abc\r\n")));
    }

    @Test void binarySafeBulk() {
        byte[] payload = {0, 1, 2, '\r', '\n', 3, (byte) 0xff};
        ByteBuf b = Unpooled.buffer();
        b.writeByte('$');
        b.writeCharSequence(Integer.toString(payload.length), StandardCharsets.US_ASCII);
        b.writeBytes(new byte[]{'\r', '\n'});
        b.writeBytes(payload);
        b.writeBytes(new byte[]{'\r', '\n'});
        RespValue v = RespParser.parse(b);
        assertInstanceOf(RespValue.Bulk.class, v);
        assertArrayEquals(payload, ((RespValue.Bulk) v).value());
    }

    @Test void requestParsesFlatArrayOfBulks() {
        RespValue v = RespParser.parseRequest(buf("*2\r\n$3\r\nGET\r\n$3\r\nfoo\r\n"));
        assertInstanceOf(RespValue.Array.class, v);
        assertEquals(2, ((RespValue.Array) v).values().size());
    }

    @Test void requestRejectsNonArray() {
        assertThrows(RespProtocolException.class,
                () -> RespParser.parseRequest(buf("+OK\r\n")));
    }

    @Test void requestRejectsNestedArray() {
        assertThrows(RespProtocolException.class,
                () -> RespParser.parseRequest(buf("*1\r\n*1\r\n$4\r\nPING\r\n")));
    }

    @Test void requestRejectsNonBulkElement() {
        assertThrows(RespProtocolException.class,
                () -> RespParser.parseRequest(buf("*1\r\n:1\r\n")));
    }

    @Test void requestReturnsNullOnPartial() {
        ByteBuf b = buf("*2\r\n$3\r\nGET\r\n");
        assertNull(RespParser.parseRequest(b));
        assertEquals(0, b.readerIndex());
    }
}