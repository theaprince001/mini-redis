package io.miniredis.server;

import io.netty.channel.Channel;

public final class Session {

    private final Channel channel;
    private volatile boolean closeRequested;

    public Session(Channel channel) {
        this.channel = channel;
    }

    public Channel channel() {
        return channel;
    }

    public void requestClose() {
        closeRequested = true;
    }

    public boolean isCloseRequested() {
        return closeRequested;
    }
}