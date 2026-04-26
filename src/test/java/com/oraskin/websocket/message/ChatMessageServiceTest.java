package com.oraskin.websocket.message;

import com.oraskin.chat.TestSupport.InMemoryChatRepository;
import com.oraskin.chat.TestSupport.InMemoryUserStore;
import com.oraskin.chat.TestSupport.NoopWebSocketMessageSender;
import com.oraskin.chat.repository.entity.MessageRecord;
import com.oraskin.chat.service.ChatService;
import com.oraskin.chat.TestSupport.NoopSessionRegistry;
import com.oraskin.user.session.ClientSession;
import com.oraskin.websocket.WebSocketMessageSender;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.oraskin.chat.TestSupport.user;
import static org.assertj.core.api.Assertions.assertThat;

class ChatMessageServiceTest {

    @Test
    void sendMessageDeliversToRecipientAndEchoesSender() throws Exception {
        InMemoryChatRepository chatRepository = new InMemoryChatRepository();
        InMemoryUserStore userStore = new InMemoryUserStore();
        userStore.add(user("alice-id", "alice@example.com"));
        userStore.add(user("bob-id", "bob@example.com"));
        ChatService chatService = new ChatService(chatRepository, new NoopSessionRegistry(), userStore, new NoopWebSocketMessageSender());
        long chatId = chatService.createChat("alice-id", "bob@example.com").chatId();
        RecordingWebSocketMessageSender sender = new RecordingWebSocketMessageSender();
        ChatMessageService service = new ChatMessageService(chatService, sender);
        ByteArrayOutputStream aliceOutput = new ByteArrayOutputStream();
        ClientSession aliceSession = new ClientSession("alice-id", new Socket(), aliceOutput);

        service.sendMessage(aliceSession, chatId, "hello");

        MessageRecord recipientMessage = (MessageRecord) sender.messagesFor("bob-id").getFirst();
        assertThat(recipientMessage.senderUserId()).isEqualTo("alice@example.com");
        assertThat(recipientMessage.text()).isEqualTo("hello");
        assertThat(recipientMessage.updatedAt()).isNull();
        assertThat(firstTextFrame(aliceOutput)).contains("\"text\":\"hello\"");
        assertThat(firstTextFrame(aliceOutput)).contains("\"updatedAt\":null");
    }

    private String firstTextFrame(ByteArrayOutputStream output) {
        byte[] frame = output.toByteArray();
        int lengthCode = frame[1] & 0x7F;
        int headerLength = 2;
        int payloadLength;
        if (lengthCode < 126) {
            payloadLength = lengthCode;
        } else if (lengthCode == 126) {
            payloadLength = ((frame[2] & 0xFF) << 8) | (frame[3] & 0xFF);
            headerLength = 4;
        } else {
            throw new IllegalStateException("Test helper does not support 64-bit payload lengths");
        }
        return new String(frame, headerLength, payloadLength, StandardCharsets.UTF_8);
    }

    private static final class RecordingWebSocketMessageSender implements WebSocketMessageSender {

        private final Map<String, List<Object>> payloadsByUserId = new LinkedHashMap<>();

        @Override
        public void sendToUser(String userId, Object payload) {
            payloadsByUserId.computeIfAbsent(userId, ignored -> new ArrayList<>()).add(payload);
        }

        @Override
        public void sendToUsers(Collection<String> userIds, Object payload) {
            for (String userId : userIds) {
                sendToUser(userId, payload);
            }
        }

        private List<Object> messagesFor(String userId) {
            return payloadsByUserId.getOrDefault(userId, List.of());
        }
    }
}
