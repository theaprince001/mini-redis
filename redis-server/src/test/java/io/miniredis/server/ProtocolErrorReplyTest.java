package io.miniredis.server;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class ProtocolErrorReplyTest {

    private MiniRedisServer server;

    @BeforeEach void start() throws Exception {
        server = new MiniRedisServer(0);
        server.start();
    }

    @AfterEach void stop() {
        if (server != null) server.stop();
    }

    @Test @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void badPrefixGetsErrorReplyThenEof() throws Exception {
        assertErrorThenEof("!bad\r\n", "expected array");
    }

    @Test @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void oversizedBulkLengthGetsErrorReply() throws Exception {
        assertErrorThenEof("*1\r\n$4294967296\r\n", "invalid bulk length");
    }

    @Test @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void oversizedArrayCountGetsErrorReply() throws Exception {
        assertErrorThenEof("*1048577\r\n", "invalid array length");
    }

    @Test @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void nonBulkArrayElementGetsErrorReply() throws Exception {
        assertErrorThenEof("*1\r\n:1\r\n", "expected bulk string");
    }

    private void assertErrorThenEof(String wire, String expectedSubstring) throws Exception {
        try (Socket s = new Socket("127.0.0.1", server.port())) {
            s.setSoTimeout(5000);
            OutputStream out = s.getOutputStream();
            InputStream in = s.getInputStream();
            out.write(wire.getBytes(StandardCharsets.UTF_8));
            out.flush();

            String reply = readLine(in);
            assertTrue(reply.startsWith("-ERR Protocol error"), "got: " + reply);
            assertTrue(reply.contains(expectedSubstring),
                    "expected '" + expectedSubstring + "' in: " + reply);
            assertEquals(-1, in.read());
        }
    }

    private static String readLine(InputStream in) throws Exception {
        StringBuilder sb = new StringBuilder();
        int prev = -1;
        while (true) {
            int c = in.read();
            if (c == -1) throw new IllegalStateException("closed before CRLF");
            if (prev == '\r' && c == '\n') { sb.setLength(sb.length() - 1); return sb.toString(); }
            sb.append((char) c);
            prev = c;
        }
    }
}