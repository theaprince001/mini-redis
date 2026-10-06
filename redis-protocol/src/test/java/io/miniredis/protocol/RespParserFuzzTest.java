package io.miniredis.protocol;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class RespParserFuzzTest {

    private static final String[] INPUTS = {
            "+OK\r\n",
            ":12345\r\n",
            "$5\r\nhello\r\n",
            "$0\r\n\r\n",
            "*-1\r\n",
            "*2\r\n$3\r\nGET\r\n$3\r\nfoo\r\n",
            "*3\r\n$3\r\nSET\r\n$3\r\nkey\r\n$5\r\nvalue\r\n",
            "*1\r\n$4\r\nPING\r\n"
    };

    @Test void splitAtEveryByteTwoWay() {
        for (String input : INPUTS) {
            byte[] bytes = input.getBytes(StandardCharsets.UTF_8);
            RespValue baseline = RespParser.parse(Unpooled.copiedBuffer(bytes));
            assertNotNull(baseline, "baseline null for " + escape(input));

            for (int split = 1; split < bytes.length; split++) {
                RespValue got = parseSplitTwo(bytes, split);
                assertEquals(baseline, got,
                        "two-way split at " + split + " failed for " + escape(input));
            }
        }
    }

    @Test void splitAtEveryByteThreeWaySmallInputs() {
        for (String input : INPUTS) {
            byte[] bytes = input.getBytes(StandardCharsets.UTF_8);
            if (bytes.length > 32) continue;
            RespValue baseline = RespParser.parse(Unpooled.copiedBuffer(bytes));

            for (int i = 1; i < bytes.length - 1; i++) {
                for (int j = i + 1; j < bytes.length; j++) {
                    RespValue got = parseSplitThree(bytes, i, j);
                    assertEquals(baseline, got,
                            "three-way " + i + "," + j + " failed for " + escape(input));
                }
            }
        }
    }

    @Test void pipelinedThreeCommandsSplitAtEveryOffset() {
        String input = "*1\r\n$4\r\nPING\r\n"
                + "*2\r\n$4\r\nECHO\r\n$2\r\nhi\r\n"
                + "*2\r\n$3\r\nGET\r\n$3\r\nfoo\r\n";
        byte[] bytes = input.getBytes(StandardCharsets.UTF_8);

        List<RespValue> baseline = new ArrayList<>();
        ByteBuf whole = Unpooled.copiedBuffer(bytes);
        RespValue v;
        while ((v = RespParser.parseRequest(whole)) != null) baseline.add(v);
        whole.release();
        assertEquals(3, baseline.size());

        for (int split = 1; split < bytes.length; split++) {
            ByteBuf b = Unpooled.buffer();
            b.writeBytes(bytes, 0, split);
            List<RespValue> got = new ArrayList<>();
            RespValue parsed;
            while ((parsed = RespParser.parseRequest(b)) != null) got.add(parsed);
            b.writeBytes(bytes, split, bytes.length - split);
            while ((parsed = RespParser.parseRequest(b)) != null) got.add(parsed);
            assertEquals(baseline, got, "pipelined split at " + split);
            b.release();
        }
    }

    @Test void randomGarbageNeverThrowsUnexpected() {
        Random rnd = new Random(0xC0FFEE);
        for (int iter = 0; iter < 5000; iter++) {
            int len = rnd.nextInt(96);
            byte[] bytes = new byte[len];
            rnd.nextBytes(bytes);
            runParserAndCheckOutcome(RespParser::parse, bytes, "parse");
            runParserAndCheckOutcome(RespParser::parseRequest, bytes, "parseRequest");
        }
    }

    @Test void mutationFuzzOnValidInputs() {
        String[] valid = {
                "*1\r\n$4\r\nPING\r\n",
                "*2\r\n$3\r\nGET\r\n$3\r\nfoo\r\n",
                "*3\r\n$3\r\nSET\r\n$3\r\nkey\r\n$5\r\nvalue\r\n",
                "$5\r\nhello\r\n",
                ":12345\r\n",
                "+OK\r\n",
        };
        Random rnd = new Random(0xBADC0DE);
        for (String base : valid) {
            byte[] seed = base.getBytes(StandardCharsets.UTF_8);
            for (int iter = 0; iter < 2000; iter++) {
                byte[] mutated = mutate(seed, rnd);
                runParserAndCheckOutcome(RespParser::parse, mutated, "mutate/parse");
                runParserAndCheckOutcome(RespParser::parseRequest, mutated, "mutate/parseRequest");
            }
        }
    }

    // --- helpers ---

    private interface Parser { RespValue apply(ByteBuf b); }

    private static void runParserAndCheckOutcome(Parser p, byte[] bytes, String tag) {
        ByteBuf b = Unpooled.wrappedBuffer(bytes);
        try {
            p.apply(b);
        } catch (RespProtocolException expected) {
            // fine
        } catch (Throwable t) {
            fail("[" + tag + "] unexpected " + t.getClass().getName()
                    + " on " + bytesToHex(bytes), t);
        } finally {
            b.release();
        }
    }

    private static RespValue parseSplitTwo(byte[] bytes, int split) {
        ByteBuf b = Unpooled.buffer();
        b.writeBytes(bytes, 0, split);
        RespValue v = RespParser.parse(b);
        if (v != null) return v;
        b.writeBytes(bytes, split, bytes.length - split);
        return RespParser.parse(b);
    }

    private static RespValue parseSplitThree(byte[] bytes, int i, int j) {
        ByteBuf b = Unpooled.buffer();
        b.writeBytes(bytes, 0, i);
        RespValue v = RespParser.parse(b);
        if (v == null) {
            b.writeBytes(bytes, i, j - i);
            v = RespParser.parse(b);
            if (v == null) {
                b.writeBytes(bytes, j, bytes.length - j);
                v = RespParser.parse(b);
            }
        }
        return v;
    }

    private static byte[] mutate(byte[] src, Random rnd) {
        int op = rnd.nextInt(3);
        switch (op) {
            case 0 -> {
                byte[] out = src.clone();
                if (out.length == 0) return out;
                int i = rnd.nextInt(out.length);
                out[i] ^= (byte) (1 << rnd.nextInt(8));
                return out;
            }
            case 1 -> {
                byte[] out = new byte[src.length + 1];
                int i = rnd.nextInt(out.length);
                System.arraycopy(src, 0, out, 0, i);
                out[i] = (byte) rnd.nextInt(256);
                System.arraycopy(src, i, out, i + 1, src.length - i);
                return out;
            }
            default -> {
                if (src.length == 0) return src;
                byte[] out = new byte[src.length - 1];
                int i = rnd.nextInt(src.length);
                System.arraycopy(src, 0, out, 0, i);
                System.arraycopy(src, i + 1, out, i, src.length - i - 1);
                return out;
            }
        }
    }

    private static String escape(String s) {
        return s.replace("\r", "\\r").replace("\n", "\\n");
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) sb.append(String.format("%02x", b));
        return sb.toString();
    }
}