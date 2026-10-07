package io.miniredis.protocol;

public class AofCorruptionException extends RuntimeException {
    public AofCorruptionException(String message) {
        super(message);
    }
}