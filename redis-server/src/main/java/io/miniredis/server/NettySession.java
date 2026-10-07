package io.miniredis.server;

import io.miniredis.core.Session;
import io.netty.channel.Channel;

import java.util.concurrent.atomic.AtomicLong;

public final class NettySession implements Session {

    private static final AtomicLong NEXT_ID = new AtomicLong(1);

    private final Channel channel;
    private final long id = NEXT_ID.getAndIncrement();
    private volatile boolean closeRequested;

    public NettySession(Channel channel) { this.channel = channel; }

    public Channel channel() { return channel; }

    @Override public void requestClose() { closeRequested = true; }
    @Override public long clientId() { return id; }

    public boolean isCloseRequested() { return closeRequested; }
}