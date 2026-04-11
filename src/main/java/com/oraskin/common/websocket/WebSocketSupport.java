package com.oraskin.common.websocket;

import com.oraskin.common.http.HttpRequest;
import com.oraskin.common.http.HttpStatus;
import com.oraskin.common.websocket.FrameType;
import com.oraskin.common.websocket.WebSocketFrame;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.Locale;

public final class WebSocketSupport {

    private static final String WEBSOCKET_MAGIC = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11";

    public boolean isUpgrade(HttpRequest request) {
        String upgrade = request.header("upgrade");
        return "websocket".equalsIgnoreCase(upgrade);
    }

    public void validateHandshake(HttpRequest request) throws IOException {
        if (!"GET".equals(request.method())) {
            throw new IOException("WebSocket handshake must use GET.");
        }

        String upgrade = request.header("upgrade");
        if (!"websocket".equalsIgnoreCase(upgrade)) {
            throw new IOException("Missing WebSocket upgrade header.");
        }

        String connection = request.header("connection");
        if (connection == null || !connection.toLowerCase(Locale.ROOT).contains("upgrade")) {
            throw new IOException("Missing Connection: Upgrade header.");
        }

        if (request.header("sec-websocket-key") == null) {
            throw new IOException("Missing Sec-WebSocket-Key header.");
        }
    }

    public void writeHandshakeResponse(OutputStream output, String websocketKey) throws Exception {
        String acceptValue = Base64.getEncoder()
                .encodeToString(MessageDigest.getInstance("SHA-1")
                        .digest((websocketKey + WEBSOCKET_MAGIC).getBytes(StandardCharsets.ISO_8859_1)));

        String response = ""
                + "HTTP/1.1 " + HttpStatus.SWITCHING_PROTOCOLS.code() + " " + HttpStatus.SWITCHING_PROTOCOLS.reasonPhrase() + "\r\n"
                + "Upgrade: websocket\r\n"
                + "Connection: Upgrade\r\n"
                + "Sec-WebSocket-Accept: " + acceptValue + "\r\n"
                + "\r\n";
        output.write(response.getBytes(StandardCharsets.ISO_8859_1));
        output.flush();
    }

    public WebSocketFrame readFrame(InputStream input) throws IOException {
        int firstByte = input.read();
        if (firstByte < 0) {
            return null;
        }

        int secondByte = readRequiredByte(input);
        boolean fin = (firstByte & 0x80) != 0;
        int opcode = firstByte & 0x0F;
        boolean masked = (secondByte & 0x80) != 0;

        if (!fin) {
            throw new IOException("Fragmented frames are not supported.");
        }
        if (!masked) {
            throw new IOException("Client frames must be masked.");
        }

        long payloadLength = secondByte & 0x7F;
        if (payloadLength == 126) {
            payloadLength = ((long) readRequiredByte(input) << 8) | readRequiredByte(input);
        } else if (payloadLength == 127) {
            payloadLength = ByteBuffer.wrap(readExactly(input, 8)).getLong();
        }

        if (payloadLength > Integer.MAX_VALUE) {
            throw new IOException("Frame payload is too large.");
        }

        byte[] mask = readExactly(input, 4);
        byte[] payload = readExactly(input, (int) payloadLength);
        for (int i = 0; i < payload.length; i++) {
            payload[i] ^= mask[i % 4];
        }
        return new WebSocketFrame(FrameType.fromCode(opcode), payload);
    }

    private static int readRequiredByte(InputStream input) throws IOException {
        int value = input.read();
        if (value < 0) {
            throw new EOFException("Unexpected end of stream.");
        }
        return value;
    }

    private static byte[] readExactly(InputStream input, int length) throws IOException {
        byte[] buffer = new byte[length];
        int offset = 0;
        while (offset < length) {
            int read = input.read(buffer, offset, length - offset);
            if (read < 0) {
                throw new EOFException("Unexpected end of stream.");
            }
            offset += read;
        }
        return buffer;
    }
}
