package com.oraskin;

import java.io.ByteArrayOutputStream;

public class HeaderUtil {

    private static final int FIN_BIT = 0x80;
    private static final int SMALL_PAYLOAD_MAX = 125;
    private static final int MEDIUM_PAYLOAD_MAX = 65_535;
    private static final int EXTENDED_PAYLOAD_16 = 126;
    private static final int EXTENDED_PAYLOAD_64 = 127;

    public static byte[] buildHeader(FrameType frameType, int payloadLength) {
        ByteArrayOutputStream header = new ByteArrayOutputStream();

        header.write(FIN_BIT | frameType.code());
        writePayloadLength(header, payloadLength);

        return header.toByteArray();
    }

    private static void writePayloadLength(ByteArrayOutputStream target, int payloadLength) {
        if (payloadLength <= SMALL_PAYLOAD_MAX) {
            target.write(payloadLength);
            return;
        }

        if (payloadLength <= MEDIUM_PAYLOAD_MAX) {
            target.write(EXTENDED_PAYLOAD_16);
            writeUnsignedShort(target, payloadLength);
            return;
        }

        target.write(EXTENDED_PAYLOAD_64);
        writeLong(target, payloadLength);
    }

    private static void writeUnsignedShort(ByteArrayOutputStream target, int value) {
        target.write((value >>> 8) & 0xFF);
        target.write(value & 0xFF);
    }

    private static void writeLong(ByteArrayOutputStream target, long value) {
        target.write((int) ((value >>> 56) & 0xFF));
        target.write((int) ((value >>> 48) & 0xFF));
        target.write((int) ((value >>> 40) & 0xFF));
        target.write((int) ((value >>> 32) & 0xFF));
        target.write((int) ((value >>> 24) & 0xFF));
        target.write((int) ((value >>> 16) & 0xFF));
        target.write((int) ((value >>> 8) & 0xFF));
        target.write((int) (value & 0xFF));
    }
}
