package io.miniredis.protocol;

import java.util.Arrays;
import java.util.List;

/**
 * A RESP2 value. Sealed so the encoder switch is exhaustive.
 */
public sealed interface RespValue {

    record SimpleString(String value) implements RespValue {}

    record Error(String value) implements RespValue {}

    record Integer(long value) implements RespValue {}

    record Bulk(byte[] value) implements RespValue {
        @Override
        public boolean equals(Object o) {
            return o instanceof Bulk other && Arrays.equals(value, other.value);
        }

        @Override
        public int hashCode() {
            return Arrays.hashCode(value);
        }
    }

    record NullBulk() implements RespValue {}

    record Array(List<RespValue> values) implements RespValue {}

    record NullArray() implements RespValue {}
}