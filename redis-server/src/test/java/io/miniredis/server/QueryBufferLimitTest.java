package io.miniredis.server;

import io.miniredis.protocol.RespParser;
import io.miniredis.protocol.RespProtocolException;
import io.miniredis.protocol.RespValue;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.DecoderException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class QueryBufferLimitTest {

    private static final int CHUNK = 1024 * 1024;

    // ------------------------------------------------------------------
    // Positive: cap rejects an oversized multi-argument request
    // ------------------------------------------------------------------
    @Test @Timeout(value = 30, unit = TimeUnit.SECONDS)
    void oversizedMultiArgumentRequestIsRejected() {
        final int argLen = RespParser.MAX_BULK - 2048;
        final int thirdArgBytes = 128 * 1024;

        byte[] request = buildOversizedRequest(argLen, thirdArgBytes);

        MiniRedisServer.RespFrameDecoder decoder =
                new MiniRedisServer.RespFrameDecoder(MiniRedisServer.DEFAULT_MAX_QUERY_BUFFER);
        EmbeddedChannel ch = new EmbeddedChannel(decoder);
        try {
            RuntimeException thrown = assertThrows(RuntimeException.class, () -> {
                for (int off = 0; off < request.length; off += CHUNK) {
                    int n = Math.min(CHUNK, request.length - off);
                    ch.writeInbound(Unpooled.wrappedBuffer(request, off, n));
                }
            });
            RespProtocolException rpe = findCause(thrown, RespProtocolException.class);
            assertNotNull(rpe, "expected RespProtocolException in chain: " + thrown);
            assertTrue(rpe.getMessage().contains("query buffer exceeded"),
                    "got: " + rpe.getMessage());
            assertTrue(thrown instanceof DecoderException
                            || thrown instanceof RespProtocolException,
                    "unexpected throwable type: " + thrown.getClass().getName());
        } finally {
            ch.finishAndReleaseAll();
        }
    }

    // ------------------------------------------------------------------
    // Negative: cap does NOT reject a legitimate large request
    // ------------------------------------------------------------------
    @Test @Timeout(value = 30, unit = TimeUnit.SECONDS)
    void singleMaxSizeArgumentIsAccepted() {
        final int argLen = RespParser.MAX_BULK - 2048;

        byte[] request = buildSingleArgumentRequest(argLen);
        assertTrue(request.length < MiniRedisServer.DEFAULT_MAX_QUERY_BUFFER,
                "test premise: single-arg request must fit under the cap");

        MiniRedisServer.RespFrameDecoder decoder =
                new MiniRedisServer.RespFrameDecoder(MiniRedisServer.DEFAULT_MAX_QUERY_BUFFER);
        EmbeddedChannel ch = new EmbeddedChannel(decoder);
        try {
            for (int off = 0; off < request.length; off += CHUNK) {
                int n = Math.min(CHUNK, request.length - off);
                ch.writeInbound(Unpooled.wrappedBuffer(request, off, n));
            }

            Object inbound = ch.readInbound();
            assertNotNull(inbound, "decoder produced no value for a valid request");
            assertInstanceOf(RespValue.Array.class, inbound);
            RespValue.Array arr = (RespValue.Array) inbound;
            assertEquals(1, arr.values().size());
            RespValue.Bulk bulk = assertInstanceOf(RespValue.Bulk.class, arr.values().get(0));
            assertEquals(argLen, bulk.value().length);
        } finally {
            ch.finishAndReleaseAll();
        }
    }

    // ------------------------------------------------------------------
    // Wiring: MiniRedisServer passes maxQueryBuffer to the decoder
    // ------------------------------------------------------------------
    @Test @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void serverWiresConfiguredCapIntoDecoder() throws Exception {
        MiniRedisServer tiny = new MiniRedisServer(
                /* port */                    0,
                /* clientOutboundHardLimit */ MiniRedisServer.DEFAULT_CLIENT_OUTBOUND_HARD_LIMIT,
                /* stallTimeoutMs */          MiniRedisServer.DEFAULT_STALL_TIMEOUT_MS,
                /* soSndBuf */                0,
                /* maxQueryBuffer */          1024);
        tiny.start();
        try {
            try (Socket s = new Socket("127.0.0.1", tiny.port())) {
                s.setSoTimeout(5000);

                AtomicReference<String> captured = new AtomicReference<>();
                CountDownLatch done = new CountDownLatch(1);
                Thread reader = new Thread(() -> {
                    try {
                        InputStream in = s.getInputStream();
                        StringBuilder sb = new StringBuilder();
                        int prev = -1;
                        while (true) {
                            int c = in.read();
                            if (c == -1) return;
                            if (prev == '\r' && c == '\n') {
                                sb.setLength(sb.length() - 1);
                                captured.set(sb.toString());
                                return;
                            }
                            sb.append((char) c);
                            prev = c;
                        }
                    } catch (Exception ignored) {
                        // RST acceptable
                    } finally {
                        done.countDown();
                    }
                }, "wiring-reader");
                reader.setDaemon(true);
                reader.start();

                OutputStream out = s.getOutputStream();
                out.write("*1\r\n$8388608\r\n".getBytes(StandardCharsets.US_ASCII));
                byte[] partial = new byte[2048];
                Arrays.fill(partial, (byte) 'x');
                out.write(partial);
                out.flush();

                assertTrue(done.await(5, TimeUnit.SECONDS),
                        "reader never completed: no reply and connection stayed open");
                String reply = captured.get();
                assertNotNull(reply, "reply lost (likely TCP reset)");
                assertTrue(reply.startsWith("-ERR Protocol error"), "got: " + reply);
                assertTrue(reply.contains("query buffer exceeded"), "got: " + reply);
            }
        } finally {
            tiny.stop();
        }
    }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    private static byte[] buildOversizedRequest(int argLen, int thirdArgBytes) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            out.write("*3\r\n".getBytes(StandardCharsets.US_ASCII));
            byte[] argBytes = new byte[argLen];
            Arrays.fill(argBytes, (byte) 'x');
            for (int i = 0; i < 2; i++) {
                out.write(("$" + argLen + "\r\n").getBytes(StandardCharsets.US_ASCII));
                out.write(argBytes);
                out.write('\r');
                out.write('\n');
            }
            out.write(("$" + argLen + "\r\n").getBytes(StandardCharsets.US_ASCII));
            out.write(new byte[thirdArgBytes]);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return out.toByteArray();
    }

    private static byte[] buildSingleArgumentRequest(int argLen) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            out.write("*1\r\n".getBytes(StandardCharsets.US_ASCII));
            out.write(("$" + argLen + "\r\n").getBytes(StandardCharsets.US_ASCII));
            byte[] argBytes = new byte[argLen];
            Arrays.fill(argBytes, (byte) 'x');
            out.write(argBytes);
            out.write('\r');
            out.write('\n');
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return out.toByteArray();
    }

    private static <T extends Throwable> T findCause(Throwable t, Class<T> type) {
        while (t != null) {
            if (type.isInstance(t)) return type.cast(t);
            t = t.getCause();
        }
        return null;
    }
}