package io.miniredis.server;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class StallTimeoutIntegrationTest {

    private static final long STALL_TIMEOUT_MS = 1_000;

    private MiniRedisServer server;

    @BeforeEach void start() throws Exception {
        server = new MiniRedisServer(
                /* port */            0,
                /* hardLimit */       8L * 1024 * 1024,
                /* stallTimeoutMs */  STALL_TIMEOUT_MS,
                /* soSndBuf */        8 * 1024);
        server.start();
    }

    @AfterEach void stop() {
        if (server != null) server.stop();
    }

    @Test @Timeout(value = 30, unit = TimeUnit.SECONDS)
    void neverReadingClientIsDisconnectedAfterStallTimeout() throws Exception {
        Socket s = new Socket();
        s.setReceiveBufferSize(4096);
        s.setSendBufferSize(4096);
        s.setSoTimeout(15_000);
        s.connect(new InetSocketAddress("127.0.0.1", server.port()));

        try {
            AtomicReference<IOException> writerError = new AtomicReference<>();
            Thread writer = new Thread(() -> {
                try {
                    OutputStream out = s.getOutputStream();
                    byte[] payload = new byte[8192];
                    Arrays.fill(payload, (byte) 'x');
                    byte[] header = ("*2\r\n$4\r\nECHO\r\n$"
                            + payload.length + "\r\n").getBytes(StandardCharsets.UTF_8);
                    byte[] crlf = {'\r', '\n'};
                    while (true) {
                        out.write(header);
                        out.write(payload);
                        out.write(crlf);
                        out.flush();
                    }
                } catch (IOException e) {
                    writerError.set(e);
                }
            }, "stall-test-writer");
            writer.setDaemon(true);

            long started = System.currentTimeMillis();
            writer.start();

            writer.join(20_000);
            long elapsed = System.currentTimeMillis() - started;

            assertFalse(writer.isAlive(), "writer blocked past 20 s; stall timer never fired");
            assertNotNull(writerError.get(),
                    "writer never errored; server did not close the socket");
            assertTrue(elapsed >= STALL_TIMEOUT_MS,
                    "socket closed after " + elapsed + " ms; stall timer must run first");
            assertTrue(elapsed < 20_000,
                    "writer stayed blocked for " + elapsed + " ms");

            try {
                byte[] buf = new byte[8192];
                while (s.getInputStream().read(buf) != -1) { /* drain */ }
            } catch (SocketTimeoutException e) {
                fail("connection still open after stall timeout");
            } catch (IOException ok) {
                // reset acceptable
            }
        } finally {
            s.close();
        }
    }
}