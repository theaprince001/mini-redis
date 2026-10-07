package io.miniredis.core;

import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.*;

class StrictNumbersTest {

    private static byte[] b(String s) { return s.getBytes(StandardCharsets.US_ASCII); }

    @Test void acceptsCanonical() {
        assertEquals(0L, StrictNumbers.parseStrictLong(b("0")));
        assertEquals(1L, StrictNumbers.parseStrictLong(b("1")));
        assertEquals(12345L, StrictNumbers.parseStrictLong(b("12345")));
        assertEquals(-7L, StrictNumbers.parseStrictLong(b("-7")));
        assertEquals(Long.MAX_VALUE, StrictNumbers.parseStrictLong(b("9223372036854775807")));
        assertEquals(Long.MIN_VALUE, StrictNumbers.parseStrictLong(b("-9223372036854775808")));
    }

    @Test void rejectsEmpty() { assertFalse(StrictNumbers.isStrictLong(b(""))); }
    @Test void rejectsLoneMinus() { assertFalse(StrictNumbers.isStrictLong(b("-"))); }
    @Test void rejectsPlus() { assertFalse(StrictNumbers.isStrictLong(b("+5"))); }
    @Test void rejectsWhitespace() {
        assertFalse(StrictNumbers.isStrictLong(b(" 5")));
        assertFalse(StrictNumbers.isStrictLong(b("5 ")));
    }
    @Test void rejectsLeadingZero() { assertFalse(StrictNumbers.isStrictLong(b("007"))); }
    @Test void rejectsNegativeZero() { assertFalse(StrictNumbers.isStrictLong(b("-0"))); }
    @Test void rejectsNonDigit() { assertFalse(StrictNumbers.isStrictLong(b("1a"))); }
    @Test void rejectsOverflow() {
        assertFalse(StrictNumbers.isStrictLong(b("9223372036854775808")));
        assertFalse(StrictNumbers.isStrictLong(b("-9223372036854775809")));
    }
}