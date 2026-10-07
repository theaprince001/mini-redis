package io.miniredis.protocol;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RespEncoderTest {

    private static String encode(RespValue v) {
        ByteBuf b = Unpooled.buffer();
        RespEncoder.encode(b, v);
        byte[] out = new byte[b.readableBytes()];
        b.readBytes(out);
        return new String(out, StandardCharsets.UTF_8);
    }

    @Test void simpleString() {
        assertEquals("+OK\r\n", encode(new RespValue.SimpleString("OK")));
    }

    @Test void error() {
        assertEquals("-ERR bad\r\n", encode(new RespValue.Error("ERR bad")));
    }

    @Test void integer() {
        assertEquals(":0\r\n", encode(new RespValue.Integer(0)));
        assertEquals(":-7\r\n", encode(new RespValue.Integer(-7)));
        assertEquals(":1234567890\r\n", encode(new RespValue.Integer(1234567890L)));
    }

    @Test void bulk() {
        assertEquals("$5\r\nhello\r\n",
                encode(new RespValue.Bulk("hello".getBytes(StandardCharsets.UTF_8))));
    }

    @Test void nullBulk() {
        assertEquals("$-1\r\n", encode(new RespValue.NullBulk()));
    }

    @Test void sanitizesCrLfInError() {
        assertEquals("-ERR unknown command 'X  OK'\r\n",
                encode(new RespValue.Error("ERR unknown command 'X\r\nOK'")));
    }

    @Test void sanitizesCrLfInSimpleString() {
        assertEquals("+hi  there\r\n",
                encode(new RespValue.SimpleString("hi\r\nthere")));
    }

    @Test void arrayRoundTrip() {
        RespValue original = new RespValue.Array(List.of(
                new RespValue.Bulk("PING".getBytes(StandardCharsets.UTF_8))
        ));
        String wire = encode(original);
        ByteBuf back = Unpooled.copiedBuffer(wire, StandardCharsets.UTF_8);
        assertEquals(original, RespParser.parse(back));
    }
}