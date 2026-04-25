package com.oraskin.user.session;

import com.oraskin.auth.AuthTestSupport;
import com.oraskin.chat.service.ChatException;
import com.oraskin.common.websocket.FrameType;
import com.oraskin.user.session.persistence.InMemorySessionRegistry;
import com.oraskin.user.session.service.SessionService;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SessionRuntimeTest {

    @Test
    void clientSessionWritesTextFramesAndCloseFrames() throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        CloseTrackingSocket socket = new CloseTrackingSocket();
        ClientSession session = new ClientSession("alice-id", socket, output);

        session.sendPayload("hello");
        session.close();

        byte[] frames = output.toByteArray();
        assertEquals(FrameType.TEXT.code(), frames[0] & 0x0F);
        assertEquals("hello", new String(frames, 2, 5, StandardCharsets.UTF_8));
        assertEquals(FrameType.CLOSE.code(), frames[7] & 0x0F);
        assertTrue(socket.closed());
    }

    @Test
    void clientSessionRejectsNonControlFramesInSendControlFrame() {
        ClientSession session = new ClientSession("alice-id", new Socket(), new ByteArrayOutputStream());

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> session.sendControlFrame(FrameType.TEXT, new byte[0])
        );

        assertEquals("Expected control frame type, but got: TEXT", exception.getMessage());
    }

    @Test
    void inMemorySessionRegistryAndSessionServiceTrackConnectionsAndRejectDuplicates() {
        InMemorySessionRegistry sessionRegistry = new InMemorySessionRegistry();
        SessionService sessionService = new SessionService(sessionRegistry);

        ClientSession session = sessionService.openSession(
                AuthTestSupport.authenticatedUser("alice-id", "alice@example.com", "hash"),
                new Socket(),
                new ByteArrayOutputStream()
        );

        assertTrue(sessionRegistry.isConnected("alice-id"));
        assertEquals(session, sessionService.findSession("alice-id"));

        ChatException exception = assertThrows(
                ChatException.class,
                () -> sessionService.openSession(
                        AuthTestSupport.authenticatedUser("alice-id", "alice@example.com", "hash"),
                        new Socket(),
                        new ByteArrayOutputStream()
                )
        );

        assertEquals("User already connected", exception.getMessage());

        sessionService.closeSession("alice-id");
        assertFalse(sessionRegistry.isConnected("alice-id"));
    }

    private static final class CloseTrackingSocket extends Socket {

        private boolean closed;

        @Override
        public synchronized void close() throws IOException {
            closed = true;
        }

        private boolean closed() {
            return closed;
        }
    }
}
