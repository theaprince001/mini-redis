package io.miniredis.server.commands;

import io.miniredis.core.ByteArrayWrapper;
import io.miniredis.protocol.RespValue;

final class StringArgs {
    private StringArgs() {}

    static ByteArrayWrapper key(RespValue v) {
        return new ByteArrayWrapper(((RespValue.Bulk) v).value());
    }

    static byte[] bytes(RespValue v) {
        return ((RespValue.Bulk) v).value();
    }
}