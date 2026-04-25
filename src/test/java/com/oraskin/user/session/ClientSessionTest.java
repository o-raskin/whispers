package com.oraskin.user.session;

import com.oraskin.common.websocket.FrameType;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ClientSessionTest {

    @Test
    void sendPayloadAndCloseWriteWebSocketFrames() throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        CloseTrackingSocket socket = new CloseTrackingSocket();
        ClientSession session = new ClientSession("alice-id", socket, output);

        session.sendPayload("hello");
        session.close();

        byte[] frames = output.toByteArray();
        assertThat(frames[0] & 0x0F).isEqualTo(FrameType.TEXT.code());
        assertThat(new String(frames, 2, 5, StandardCharsets.UTF_8)).isEqualTo("hello");
        assertThat(frames[7] & 0x0F).isEqualTo(FrameType.CLOSE.code());
        assertThat(socket.closed).isTrue();
    }

    @Test
    void sendControlFrameRejectsNonControlFrameTypes() {
        ClientSession session = new ClientSession("alice-id", new Socket(), new ByteArrayOutputStream());

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> session.sendControlFrame(FrameType.TEXT, new byte[0])
        );

        assertThat(exception).hasMessage("Expected control frame type, but got: TEXT");
    }

    private static final class CloseTrackingSocket extends Socket {

        private boolean closed;

        @Override
        public synchronized void close() throws IOException {
            closed = true;
        }
    }
}
