package com.oraskin.websocket;

import com.oraskin.common.websocket.WebSocketSupport;
import com.oraskin.user.session.ClientSession;
import com.oraskin.websocket.message.ChatMessageService;
import com.oraskin.websocket.message.PrivateChatMessageService;
import com.oraskin.websocket.presence.PresenceService;
import com.oraskin.websocket.typing.TypingStateService;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class WebSocketConnectionHandlerTest {

    @Test
    void unexpectedMessageFailuresSendGenericInternalServerError() {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ByteArrayOutputStream errorOutput = new ByteArrayOutputStream();
        PrintStream originalError = System.err;
        ClientSession session = new ClientSession("alice-id", new Socket(), output);
        WebSocketConnectionHandler handler = new WebSocketConnectionHandler(
                new WebSocketSupport(),
                new PresenceService(null, null),
                new FailingChatMessageService(),
                new PrivateChatMessageService(null, null),
                new TypingStateService(null, null)
        );

        try {
            System.setErr(new PrintStream(errorOutput, true, StandardCharsets.UTF_8));
            handler.handle(session, new ByteArrayInputStream(maskedTextFrame("{\"type\":\"MESSAGE\",\"chatId\":1,\"text\":\"hello\"}")));
        } finally {
            System.setErr(originalError);
        }

        assertThat(firstTextFrame(output)).isEqualTo("ERROR: Internal server error");
        assertThat(errorOutput.toString(StandardCharsets.UTF_8)).contains("WebSocket message handling failed: IllegalStateException").doesNotContain("database password");
    }

    @Test
    void missingCommandTypeReturnsClientVisibleValidationError() {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ClientSession session = new ClientSession("alice-id", new Socket(), output);
        WebSocketConnectionHandler handler = new WebSocketConnectionHandler(
                new WebSocketSupport(),
                new PresenceService(null, null),
                new ChatMessageService(null, null),
                new PrivateChatMessageService(null, null),
                new TypingStateService(null, null)
        );

        handler.handle(session, new ByteArrayInputStream(maskedTextFrame("{\"chatId\":1}")));

        assertThat(firstTextFrame(output)).isEqualTo("ERROR: type is required");
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

    private String firstTextFrame(ByteArrayOutputStream output) {
        byte[] frame = output.toByteArray();
        int payloadLength = frame[1] & 0x7F;
        return new String(frame, 2, payloadLength, StandardCharsets.UTF_8);
    }

    private static final class FailingChatMessageService extends ChatMessageService {

        private FailingChatMessageService() {
            super(null, null);
        }

        @Override
        public void sendMessage(ClientSession clientSession, long chatId, String text) throws IOException {
            throw new IllegalStateException("database password leaked");
        }
    }
}
