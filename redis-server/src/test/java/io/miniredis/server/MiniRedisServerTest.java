package io.miniredis.server;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class MiniRedisServerTest {

    private MiniRedisServer server;

    @BeforeEach void start() throws Exception {
        server = new MiniRedisServer(0);
        server.start();
    }

    @AfterEach void stop() {
        if (server != null) server.stop();
    }

    @Test void pingReturnsPong() throws Exception {
        assertEquals("+PONG", sendAndReadLine("*1\r\n$4\r\nPING\r\n"));
    }

    @Test void pingWithMessageEchoesMessage() throws Exception {
        String wire = "*2\r\n$4\r\nPING\r\n$5\r\nhello\r\n";
        assertEquals("$5\r\nhello", sendAndReadLineWithBody(wire, 5));
    }

    @Test void echoReturnsMessage() throws Exception {
        String wire = "*2\r\n$4\r\nECHO\r\n$3\r\nfoo\r\n";
        assertEquals("$3\r\nfoo", sendAndReadLineWithBody(wire, 3));
    }

    @Test void quitClosesConnection() throws Exception {
        try (Socket s = new Socket("127.0.0.1", server.port());
             OutputStream out = s.getOutputStream();
             InputStream in = s.getInputStream()) {
            out.write("*1\r\n$4\r\nQUIT\r\n".getBytes(StandardCharsets.UTF_8));
            out.flush();
            byte[] buf = new byte[64];
            int n = in.read(buf);
            assertTrue(n > 0);
            assertEquals("+OK", new String(buf, 0, n, StandardCharsets.UTF_8).trim());
            assertEquals(-1, in.read());
        }
    }

    @Test void unknownCommandReturnsSanitizedError() throws Exception {
        String wire = "*1\r\n$7\r\nNOPECMD\r\n";
        String reply = sendAndReadLine(wire);
        assertTrue(reply.startsWith("-ERR unknown command 'NOPECMD'"), reply);
    }

    @Test void respondsToTwoPingsOnOneConnection() throws Exception {
        try (Socket s = new Socket("127.0.0.1", server.port());
             OutputStream out = s.getOutputStream();
             InputStream in = s.getInputStream()) {
            out.write("*1\r\n$4\r\nPING\r\n*1\r\n$4\r\nPING\r\n".getBytes(StandardCharsets.UTF_8));
            out.flush();
            assertEquals("+PONG", readLine(in).trim());
            assertEquals("+PONG", readLine(in).trim());
        }
    }

    @Test void pipelinedCommandsAfterQuitAreIgnored() throws Exception {
        try (Socket s = new Socket("127.0.0.1", server.port());
             OutputStream out = s.getOutputStream();
             InputStream in = s.getInputStream()) {
            out.write(("*1\r\n$4\r\nPING\r\n"
                    + "*1\r\n$4\r\nQUIT\r\n"
                    + "*1\r\n$4\r\nPING\r\n").getBytes(StandardCharsets.UTF_8));
            out.flush();
            assertEquals("+PONG", readLine(in).trim());
            assertEquals("+OK",   readLine(in).trim());
            assertEquals(-1, in.read());
        }
    }

    // --- helpers ---

    private String sendAndReadLine(String wire) throws Exception {
        try (Socket s = new Socket("127.0.0.1", server.port());
             OutputStream out = s.getOutputStream();
             InputStream in = s.getInputStream()) {
            out.write(wire.getBytes(StandardCharsets.UTF_8));
            out.flush();
            return readLine(in).trim();
        }
    }

    private String sendAndReadLineWithBody(String wire, int bodyLen) throws Exception {
        try (Socket s = new Socket("127.0.0.1", server.port());
             OutputStream out = s.getOutputStream();
             InputStream in = s.getInputStream()) {
            out.write(wire.getBytes(StandardCharsets.UTF_8));
            out.flush();
            String header = readLine(in).trim();
            byte[] body = in.readNBytes(bodyLen);
            return header + "\r\n" + new String(body, StandardCharsets.UTF_8);
        }
    }

    private static String readLine(InputStream in) throws Exception {
        StringBuilder sb = new StringBuilder();
        int prev = -1;
        while (true) {
            int c = in.read();
            if (c == -1) throw new IllegalStateException("connection closed early");
            if (prev == '\r' && c == '\n') { sb.setLength(sb.length() - 1); return sb.toString(); }
            sb.append((char) c);
            prev = c;
        }
    }
}