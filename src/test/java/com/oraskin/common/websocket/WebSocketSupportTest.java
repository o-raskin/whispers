package com.oraskin.common.websocket;

import com.oraskin.common.http.HttpRequest;
import com.oraskin.common.http.QueryParams;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WebSocketSupportTest {

    @Test
    void validateHandshakeAcceptsExpectedUpgradeRequest() throws Exception {
        WebSocketSupport webSocketSupport = new WebSocketSupport();
        HttpRequest request = request(Map.of(
                "upgrade", "websocket",
                "connection", "keep-alive, Upgrade",
                "sec-websocket-key", "test-key"
        ));

        webSocketSupport.validateHandshake(request);

        assertTrue(webSocketSupport.isUpgrade(request));
    }

    @Test
    void validateHandshakeRejectsMissingHeaders() {
        WebSocketSupport webSocketSupport = new WebSocketSupport();

        IOException exception = assertThrows(IOException.class, () -> webSocketSupport.validateHandshake(request(Map.of())));

        assertEquals("Missing WebSocket upgrade header.", exception.getMessage());
    }

    @Test
    void writeHandshakeResponseIncludesAcceptHeaderAndProtocol() throws Exception {
        WebSocketSupport webSocketSupport = new WebSocketSupport();
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        webSocketSupport.writeHandshakeResponse(output, "dGhlIHNhbXBsZSBub25jZQ==", "whispers.bearer.token");

        String response = output.toString(StandardCharsets.UTF_8);
        assertTrue(response.startsWith("HTTP/1.1 101 Switching Protocols"));
        assertTrue(response.contains("Sec-WebSocket-Accept: s3pPLMBiTxaQ9kYGzzhZRbK+xOo="));
        assertTrue(response.contains("Sec-WebSocket-Protocol: whispers.bearer.token"));
    }

    @Test
    void readFrameUnmasksClientPayloads() throws Exception {
        WebSocketSupport webSocketSupport = new WebSocketSupport();

        WebSocketFrame frame = webSocketSupport.readFrame(new ByteArrayInputStream(maskedTextFrame("hello")));

        assertEquals(FrameType.TEXT, frame.frameType());
        assertEquals("hello", new String(frame.payload(), StandardCharsets.UTF_8));
    }

    @Test
    void readFrameRejectsUnmaskedClientFrames() {
        WebSocketSupport webSocketSupport = new WebSocketSupport();
        byte[] frame = new byte[]{(byte) 0x81, 0x05, 'h', 'e', 'l', 'l', 'o'};

        IOException exception = assertThrows(IOException.class, () -> webSocketSupport.readFrame(new ByteArrayInputStream(frame)));

        assertEquals("Client frames must be masked.", exception.getMessage());
    }

    private HttpRequest request(Map<String, String> headers) {
        return new HttpRequest("GET", "/ws/user", QueryParams.fromTarget("/ws/user"), headers, null, null);
    }

    private byte[] maskedTextFrame(String payload) {
        byte[] text = payload.getBytes(StandardCharsets.UTF_8);
        byte[] mask = new byte[]{1, 2, 3, 4};
        byte[] frame = new byte[2 + mask.length + text.length];
        frame[0] = (byte) 0x81;
        frame[1] = (byte) (0x80 | text.length);
        System.arraycopy(mask, 0, frame, 2, mask.length);
        for (int index = 0; index < text.length; index++) {
            frame[2 + mask.length + index] = (byte) (text[index] ^ mask[index % mask.length]);
        }
        return frame;
    }
}
