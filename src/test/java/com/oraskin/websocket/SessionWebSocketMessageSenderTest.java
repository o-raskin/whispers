package com.oraskin.websocket;

import com.oraskin.user.session.ClientSession;
import com.oraskin.user.session.persistence.InMemorySessionRegistry;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SessionWebSocketMessageSenderTest {

    @Test
    void sendToUserAndSendToUsersSerializePayloadsForConnectedSessions() {
        InMemorySessionRegistry sessionRegistry = new InMemorySessionRegistry();
        ByteArrayOutputStream aliceOutput = new ByteArrayOutputStream();
        ByteArrayOutputStream bobOutput = new ByteArrayOutputStream();
        sessionRegistry.createUserSession("alice-id", new ClientSession("alice-id", new Socket(), aliceOutput));
        sessionRegistry.createUserSession("bob-id", new ClientSession("bob-id", new Socket(), bobOutput));
        SessionWebSocketMessageSender sender = new SessionWebSocketMessageSender(sessionRegistry);

        sender.sendToUser("alice-id", Map.of("type", "message", "text", "hello"));
        sender.sendToUsers(List.of("alice-id", "bob-id"), Map.of("type", "presence"));

        assertThat(firstTextFrame(aliceOutput)).contains("\"text\":\"hello\"");
        assertThat(lastTextFrame(aliceOutput)).contains("\"type\":\"presence\"");
        assertThat(firstTextFrame(bobOutput)).contains("\"type\":\"presence\"");
    }

    @Test
    void sendOperationsIgnoreNullsMissingSessionsAndSanitizeIoFailures() {
        InMemorySessionRegistry sessionRegistry = new InMemorySessionRegistry();
        SessionWebSocketMessageSender sender = new SessionWebSocketMessageSender(sessionRegistry);
        ByteArrayOutputStream errorOutput = new ByteArrayOutputStream();
        PrintStream originalError = System.err;
        sessionRegistry.createUserSession("alice-id", new ClientSession("alice-id", new Socket(), new FailingOutputStream()));

        try {
            System.setErr(new PrintStream(errorOutput, true, StandardCharsets.UTF_8));
            sender.sendToUser(null, Map.of("type", "presence"));
            sender.sendToUser("missing", Map.of("type", "presence"));
            sender.sendToUser("alice-id", null);
            sender.sendToUsers(Arrays.asList("missing", null), Map.of("type", "presence"));
            sender.sendToUsers(List.of("alice-id"), null);
            sender.sendToUser("alice-id", Map.of("type", "message", "text", "hello"));
        } finally {
            System.setErr(originalError);
        }

        assertThat(errorOutput.toString(StandardCharsets.UTF_8))
                .contains("Cannot send WebSocket payload to user 'alice-id': IOException")
                .doesNotContain("database password");
    }

    private String firstTextFrame(ByteArrayOutputStream output) {
        return textFrames(output).getFirst();
    }

    private String lastTextFrame(ByteArrayOutputStream output) {
        return textFrames(output).getLast();
    }

    private List<String> textFrames(ByteArrayOutputStream output) {
        byte[] bytes = output.toByteArray();
        java.util.ArrayList<String> frames = new java.util.ArrayList<>();
        int offset = 0;
        while (offset < bytes.length) {
            int lengthCode = bytes[offset + 1] & 0x7F;
            int headerLength = lengthCode < 126 ? 2 : 4;
            int payloadLength = lengthCode < 126 ? lengthCode : ((bytes[offset + 2] & 0xFF) << 8) | (bytes[offset + 3] & 0xFF);
            frames.add(new String(bytes, offset + headerLength, payloadLength, StandardCharsets.UTF_8));
            offset += headerLength + payloadLength;
        }
        return frames;
    }

    private static final class FailingOutputStream extends OutputStream {

        @Override
        public void write(int b) throws IOException {
            throw new IOException("database password leaked");
        }
    }
}
