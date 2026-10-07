package io.miniredis.core;

/**
 * Strict integer parsing for INCR/DECR and any Redis value that must be a
 * canonical long. Rejects what Redis rejects: empty, lone minus, leading
 * plus, leading zeros including "-0", whitespace, non-digits, overflow.
 */
public final class StrictNumbers {

    private StrictNumbers() {}

    public static boolean isStrictLong(byte[] s) {
        try {
            parseStrictLong(s);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    public static long parseStrictLong(byte[] s) {
        if (s == null || s.length == 0) throw new NumberFormatException("empty");

        int i = 0;
        boolean negative = false;
        if (s[0] == '-') { negative = true; i = 1; }

        if (i >= s.length) throw new NumberFormatException("lone minus");

        if (s[i] == '0') {
            if (s.length - i > 1) throw new NumberFormatException("leading zero");
            if (negative) throw new NumberFormatException("negative zero");
            return 0L;
        }

        long limit = negative ? Long.MIN_VALUE : -Long.MAX_VALUE;
        long acc = 0;
        while (i < s.length) {
            int d = s[i] - '0';
            if (d < 0 || d > 9) throw new NumberFormatException("non-digit");
            if (acc < (limit + d) / 10) throw new NumberFormatException("overflow");
            acc = acc * 10 - d;
            i++;
        }
        return negative ? acc : -acc;
    }
}