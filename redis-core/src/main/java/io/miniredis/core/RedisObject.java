package io.miniredis.core;

public final class RedisObject {

    private final RedisType type;
    private final byte[] value;

    private RedisObject(RedisType type, byte[] value) {
        this.type = type;
        this.value = value;
    }

    public static RedisObject string(byte[] value) {
        return new RedisObject(RedisType.STRING, value);
    }

    public RedisType type() { return type; }

    public byte[] stringBytes() {
        if (type != RedisType.STRING) {
            throw new IllegalStateException("not a string: " + type);
        }
        return value;
    }

    public int stringLength() { return stringBytes().length; }
}