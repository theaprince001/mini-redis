package io.miniredis.server;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufHolder;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import io.netty.util.ReferenceCountUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Tracks pending outbound bytes per channel. If the hard cap is exceeded,
 * fails the pending write and closes the channel.
 *
 * This is the "hard per-client cap" from design doc §5.10.
 * Watermarks pause/resume reads; this cap kills pathological clients.
 */
public final class OutboundByteCounter extends ChannelOutboundHandlerAdapter {

    private static final Logger log = LoggerFactory.getLogger(OutboundByteCounter.class);

    private final long hardLimit;
    private final AtomicLong pending = new AtomicLong();

    public OutboundByteCounter(long hardLimit) {
        this.hardLimit = hardLimit;
    }

    public long pendingBytes() {
        return pending.get();
    }

    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) {
        long size = byteSize(msg);
        long now = pending.addAndGet(size);
        if (now > hardLimit) {
            pending.addAndGet(-size);
            ReferenceCountUtil.release(msg);
            promise.tryFailure(new IOException(
                    "client output buffer limit exceeded (" + now + " > " + hardLimit + ")"));
            log.warn("Disconnecting slow client {}: outbound limit exceeded",
                    ctx.channel().remoteAddress());
            ctx.close();
            return;
        }
        ctx.write(msg, promise).addListener(future -> pending.addAndGet(-size));
    }

    private static long byteSize(Object msg) {
        if (msg instanceof ByteBuf b) return b.readableBytes();
        if (msg instanceof ByteBufHolder h) return h.content().readableBytes();
        return 32L; // rough estimate for other message types
    }
}