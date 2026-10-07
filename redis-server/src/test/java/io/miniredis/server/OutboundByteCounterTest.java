package io.miniredis.server;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

class OutboundByteCounterTest {

    @Test void passesSmallWrites() {
        EmbeddedChannel ch = new EmbeddedChannel(new OutboundByteCounter(1024));
        ByteBuf payload = Unpooled.buffer().writeBytes(new byte[100]);
        ch.writeOutbound(payload);
        ByteBuf out = ch.readOutbound();
        assertEquals(100, out.readableBytes());
        out.release();
        ch.finishAndReleaseAll();
    }

    @Test void disconnectsOnHardLimit() {
        EmbeddedChannel ch = new EmbeddedChannel(new OutboundByteCounter(64));
        ByteBuf big = Unpooled.buffer().writeBytes(new byte[128]);
        assertThrows(IOException.class, () -> ch.writeOutbound(big));
        assertFalse(ch.isOpen(), "channel should be closed after exceeding hard limit");
        ch.finishAndReleaseAll();
    }
}