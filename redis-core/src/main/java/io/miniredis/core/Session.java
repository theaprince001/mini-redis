package io.miniredis.core;

/**
 * Command-side view of a client connection. Not tied to Netty so command
 * tests do not need a channel.
 */
public interface Session {
    void requestClose();
    long clientId();
}