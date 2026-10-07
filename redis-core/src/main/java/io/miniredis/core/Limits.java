package io.miniredis.core;

public final class Limits {
    /**
     * Maximum size of a single string value. Matches RESP MAX_BULK. A value
     * larger than the output cap would disconnect a client on GET, so the
     * write is rejected before that can happen.
     */

    public static final int MAX_STRING_SIZE = 8 * 1024 * 1024;

    private Limits() {}
}