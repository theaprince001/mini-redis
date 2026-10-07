package io.miniredis.protocol;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class AofFramingTest {

    @Test void roundTripSingleRecord() {
        ByteBuf record = Unpooled.copiedBuffer("*1\r\n$4\r\nPING\r\n", StandardCharsets.UTF_8);
        ByteBuf out = Unpooled.buffer();
        AofFraming.write(out, record);

        ByteBuf parsed = AofFraming.read(out);
        assertNotNull(parsed);
        byte[] bytes = new byte[parsed.readableBytes()];
        parsed.readBytes(bytes);
        assertEquals("*1\r\n$4\r\nPING\r\n", new String(bytes, StandardCharsets.UTF_8));
        assertEquals(0, out.readableBytes());
        parsed.release();
    }

    @Test void returnsNullOnPartialHeader() {
        ByteBuf out = Unpooled.buffer();
        out.writeInt(10);
        assertNull(AofFraming.read(out));
    }

    @Test void returnsNullOnPartialPayload() {
        ByteBuf record = Unpooled.copiedBuffer("hello", StandardCharsets.UTF_8);
        ByteBuf out = Unpooled.buffer();
        AofFraming.write(out, record);
        out.writerIndex(out.writerIndex() - 1);
        assertNull(AofFraming.read(out));
    }

    @Test void corruptLengthBitThrowsNotNull() {
        ByteBuf record = Unpooled.copiedBuffer("hello", StandardCharsets.UTF_8);
        ByteBuf out = Unpooled.buffer();
        AofFraming.write(out, record);
        out.setByte(0, (byte) (out.getByte(0) ^ 0x01));
        assertThrows(AofCorruptionException.class, () -> AofFraming.read(out));
    }

    @Test void payloadBitFlipThrows() {
        ByteBuf record = Unpooled.copiedBuffer("hello", StandardCharsets.UTF_8);
        ByteBuf out = Unpooled.buffer();
        AofFraming.write(out, record);
        out.setByte(AofFraming.HEADER_SIZE, (byte) (out.getByte(AofFraming.HEADER_SIZE) ^ 0x80));
        assertThrows(AofCorruptionException.class, () -> AofFraming.read(out));
    }

    @Test void truncatedAtEveryOffsetReturnsNull() {
        ByteBuf record = Unpooled.copiedBuffer("hello, world", StandardCharsets.UTF_8);
        ByteBuf full = Unpooled.buffer();
        AofFraming.write(full, record);
        int total = full.readableBytes();

        for (int cut = 0; cut < total; cut++) {
            ByteBuf partial = Unpooled.buffer();
            for (int i = 0; i < cut; i++) partial.writeByte(full.getByte(i));
            assertNull(AofFraming.read(partial),
                    "cut at " + cut + " of " + total + " should be incomplete");
            partial.release();
        }
        full.release();
    }

    @Test void zeroExtendedTailIsTreatedAsTornTail() {
        ByteBuf record = Unpooled.copiedBuffer("hello", StandardCharsets.UTF_8);
        ByteBuf out = Unpooled.buffer();
        AofFraming.write(out, record);
        for (int i = 0; i < 64; i++) out.writeByte(0);
        assertNotNull(AofFraming.read(out));
        assertNull(AofFraming.read(out));
    }
}