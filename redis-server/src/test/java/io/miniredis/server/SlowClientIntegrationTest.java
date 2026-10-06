package io.miniredis.server;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

class SlowClientIntegrationTest {

    private MiniRedisServer server;

    @BeforeEach void start() throws Exception {
        server = new MiniRedisServer(
                /* port */            0,
                /* hardLimit */       256 * 1024,
                /* stallTimeoutMs */  60_000,
                /* soSndBuf */        8 * 1024);
        server.start();
    }

    @AfterEach void stop() {
        if (server != null) server.stop();
    }

    @Test @Timeout(value = 30, unit = TimeUnit.SECONDS)
    void slowClientIsDisconnectedAtHardLimit() throws Exception {
        Socket s = new Socket();
        s.setReceiveBufferSize(4096);
        s.setSendBufferSize(16 * 1024);
        s.setSoTimeout(20_000);
        s.connect(new InetSocketAddress("127.0.0.1", server.port()));

        try {
            AtomicBoolean writerGotIoException = new AtomicBoolean(false);

            Thread writer = new Thread(() -> {
                try {
                    OutputStream out = s.getOutputStream();
                    byte[] payload = new byte[4096];
                    Arrays.fill(payload, (byte) 'x');
                    byte[] header = ("*2\r\n$4\r\nECHO\r\n$"
                            + payload.length + "\r\n").getBytes(StandardCharsets.UTF_8);
                    byte[] crlf = {'\r', '\n'};
                    for (int i = 0; i < 10_000; i++) {
                        out.write(header);
                        out.write(payload);
                        out.write(crlf);
                        out.flush();
                    }
                } catch (IOException e) {
                    writerGotIoException.set(true);
                }
            }, "slow-client-writer");
            writer.setDaemon(true);
            writer.start();

            writer.join(20_000);
            assertFalse(writer.isAlive(), "writer should have finished or errored");
            assertTrue(writerGotIoException.get(),
                    "server never closed the slow client: the hard cap did not fire");

            InputStream in = s.getInputStream();
            try {
                byte[] buf = new byte[8192];
                while (in.read(buf) != -1) { /* drain */ }
            } catch (SocketTimeoutException e) {
                fail("connection still open after hard cap");
            } catch (IOException ok) {
                // reset acceptable
            }
        } finally {
            s.close();
        }
    }
}