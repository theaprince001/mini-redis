package io.miniredis.protocol;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

import java.util.zip.CRC32;

/**
 * AOF record framing: [ LEN(4) | HCRC(4) | payload(LEN) | PCRC(4) ].
 * HCRC lets us distinguish a torn tail (return null) from a corrupt record
 * (throw). Not compatible with redis-check-aof by design.
 */
public final class AofFraming {

    public static final int HEADER_SIZE   = 8;   // LEN + HCRC
    public static final int FOOTER_SIZE   = 4;   // PCRC
    public static final int MAX_RECORD_SIZE = 64 * 1024 * 1024;

    private AofFraming() {}

    public static void write(ByteBuf out, ByteBuf payload) {
        int len = payload.readableBytes();
        if (len < 0 || len > MAX_RECORD_SIZE) {
            throw new IllegalArgumentException("AOF record too large: " + len);
        }

        CRC32 headerCrc = new CRC32();
        headerCrc.update(lenBytes(len));

        CRC32 payloadCrc = new CRC32();
        payloadCrc.update(payload.nioBuffer(payload.readerIndex(), len));

        out.writeInt(len);
        out.writeInt((int) headerCrc.getValue());
        out.writeBytes(payload, payload.readerIndex(), len);
        out.writeInt((int) payloadCrc.getValue());
    }

    /**
     * Returns null only for a genuinely incomplete record (torn tail).
     * Throws AofCorruptionException on header CRC mismatch, invalid length,
     * or payload CRC mismatch. On success, returns a new buffer the caller
     * owns and advances the reader index past the record.
     */
    public static ByteBuf read(ByteBuf in) {
        if (in.readableBytes() < HEADER_SIZE) return null;

        int start = in.readerIndex();
        int len = in.getInt(start);
        int expectedHeaderCrc = in.getInt(start + 4);

        if (crc32(lenBytes(len)) != expectedHeaderCrc) {
            // Filesystems may zero-extend a file after a crash. A run of
            // zeros is a torn tail, not corruption.
            if (isAllZeros(in, start)) return null;
            throw new AofCorruptionException(
                    "AOF header CRC mismatch at offset " + start);
        }
        if (len < 0 || len > MAX_RECORD_SIZE) {
            throw new AofCorruptionException(
                    "AOF record length out of range at offset " + start + ": " + len);
        }
        if (in.readableBytes() < HEADER_SIZE + len + FOOTER_SIZE) {
            return null;
        }

        in.skipBytes(HEADER_SIZE);
        byte[] payload = new byte[len];
        in.readBytes(payload);
        int storedPayloadCrc = in.readInt();

        if (crc32(payload) != storedPayloadCrc) {
            throw new AofCorruptionException(
                    "AOF payload CRC mismatch at offset " + start);
        }
        return Unpooled.wrappedBuffer(payload);
    }

    private static byte[] lenBytes(int len) {
        return new byte[] {
                (byte) (len >>> 24),
                (byte) (len >>> 16),
                (byte) (len >>> 8),
                (byte) len,
        };
    }

    private static int crc32(byte[] bytes) {
        CRC32 crc = new CRC32();
        crc.update(bytes);
        return (int) crc.getValue();
    }

    private static boolean isAllZeros(ByteBuf buf, int fromIndex) {
        int end = buf.writerIndex();
        for (int i = fromIndex; i < end; i++) {
            if (buf.getByte(i) != 0) return false;
        }
        return true;
    }
}