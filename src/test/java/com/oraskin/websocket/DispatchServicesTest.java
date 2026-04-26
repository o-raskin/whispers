package com.oraskin.websocket;

import com.oraskin.chat.TestSupport.InMemoryChatRepository;
import com.oraskin.chat.TestSupport.InMemoryPrivateChatKeyStore;
import com.oraskin.chat.TestSupport.InMemoryUserStore;
import com.oraskin.chat.TestSupport.NoopSessionRegistry;
import com.oraskin.chat.TestSupport.NoopWebSocketMessageSender;
import com.oraskin.chat.key.service.PublicKeyService;
import com.oraskin.chat.key.value.RegisterPrivateChatKeyRequest;
import com.oraskin.chat.privatechat.service.PrivateChatService;
import com.oraskin.chat.privatechat.value.EncryptedPrivateMessagePayload;
import com.oraskin.chat.privatechat.value.PrivateMessageView;
import com.oraskin.chat.repository.entity.MessageRecord;
import com.oraskin.chat.service.ChatService;
import com.oraskin.connection.PresenceEvent;
import com.oraskin.user.session.ClientSession;
import com.oraskin.user.session.persistence.InMemorySessionRegistry;
import com.oraskin.websocket.message.ChatMessageService;
import com.oraskin.websocket.message.PrivateChatMessageService;
import com.oraskin.websocket.presence.PresenceService;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.oraskin.chat.TestSupport.user;
import static org.assertj.core.api.Assertions.assertThat;

class DispatchServicesTest {

    @Test
    void sessionWebSocketMessageSenderSerializesPayloadsForSingleAndMultipleUsers() {
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
    void sessionWebSocketMessageSenderSanitizesUnexpectedSendFailures() {
        InMemorySessionRegistry sessionRegistry = new InMemorySessionRegistry();
        SessionWebSocketMessageSender sender = new SessionWebSocketMessageSender(sessionRegistry);
        ByteArrayOutputStream errorOutput = new ByteArrayOutputStream();
        PrintStream originalError = System.err;
        sessionRegistry.createUserSession("alice-id", new ClientSession("alice-id", new Socket(), new FailingOutputStream()));

        try {
            System.setErr(new PrintStream(errorOutput, true, StandardCharsets.UTF_8));
            sender.sendToUser("alice-id", Map.of("type", "message", "text", "hello"));
        } finally {
            System.setErr(originalError);
        }

        assertThat(errorOutput.toString(StandardCharsets.UTF_8))
                .contains("Cannot send WebSocket payload to user 'alice-id': IOException")
                .doesNotContain("database password");
    }

    @Test
    void sessionWebSocketMessageSenderIgnoresNullPayloadsAndMissingSessions() {
        InMemorySessionRegistry sessionRegistry = new InMemorySessionRegistry();
        ByteArrayOutputStream aliceOutput = new ByteArrayOutputStream();
        sessionRegistry.createUserSession("alice-id", new ClientSession("alice-id", new Socket(), aliceOutput));
        SessionWebSocketMessageSender sender = new SessionWebSocketMessageSender(sessionRegistry);

        sender.sendToUser(null, Map.of("type", "presence"));
        sender.sendToUser("missing", Map.of("type", "presence"));
        sender.sendToUser("alice-id", null);
        sender.sendToUsers(Arrays.asList("missing", null), Map.of("type", "presence"));
        sender.sendToUsers(List.of("alice-id"), null);

        assertThat(aliceOutput.toByteArray()).isEmpty();
    }

    @Test
    void chatMessageServiceDeliversToRecipientAndEchoesSender() throws Exception {
        InMemoryChatRepository chatRepository = new InMemoryChatRepository();
        InMemoryUserStore userStore = new InMemoryUserStore();
        userStore.add(user("alice-id", "alice@example.com"));
        userStore.add(user("bob-id", "bob@example.com"));
        ChatService chatService = new ChatService(chatRepository, new NoopSessionRegistry(), userStore, new NoopWebSocketMessageSender());
        var chat = chatService.createChat("alice-id", "bob@example.com");
        RecordingWebSocketMessageSender sender = new RecordingWebSocketMessageSender();
        ChatMessageService chatMessageService = new ChatMessageService(chatService, sender);
        ByteArrayOutputStream aliceOutput = new ByteArrayOutputStream();
        ClientSession aliceSession = new ClientSession("alice-id", new Socket(), aliceOutput);

        chatMessageService.sendMessage(aliceSession, chat.chatId(), "hello");

        MessageRecord recipientMessage = (MessageRecord) sender.messagesFor("bob-id").getFirst();
        assertThat(recipientMessage.senderUserId()).isEqualTo("alice@example.com");
        assertThat(recipientMessage.text()).isEqualTo("hello");
        assertThat(recipientMessage.updatedAt()).isNull();
        assertThat(firstTextFrame(aliceOutput)).contains("\"text\":\"hello\"");
        assertThat(firstTextFrame(aliceOutput)).contains("\"updatedAt\":null");
    }

    @Test
    void privateChatMessageServiceDeliversEncryptedPayloadsAndEchoesSender() throws Exception {
        InMemoryChatRepository chatRepository = new InMemoryChatRepository();
        InMemoryPrivateChatKeyStore keyStore = new InMemoryPrivateChatKeyStore();
        PublicKeyService publicKeyService = new PublicKeyService(keyStore);
        InMemoryUserStore userStore = new InMemoryUserStore();
        userStore.add(user("alice-id", "alice@example.com"));
        userStore.add(user("bob-id", "bob@example.com"));
        publicKeyService.registerCurrentKey("alice-id", new RegisterPrivateChatKeyRequest("alice-key", "alice-spki", "RSA-OAEP", "spki"));
        publicKeyService.registerCurrentKey("bob-id", new RegisterPrivateChatKeyRequest("bob-key", "bob-spki", "RSA-OAEP", "spki"));
        PrivateChatService privateChatService = new PrivateChatService(chatRepository, userStore, publicKeyService);
        var chat = privateChatService.createChat("alice-id", "bob@example.com", "alice-key");
        RecordingWebSocketMessageSender sender = new RecordingWebSocketMessageSender();
        PrivateChatMessageService privateChatMessageService = new PrivateChatMessageService(privateChatService, sender);
        ByteArrayOutputStream aliceOutput = new ByteArrayOutputStream();
        ClientSession aliceSession = new ClientSession("alice-id", new Socket(), aliceOutput);
        EncryptedPrivateMessagePayload encryptedMessage = new EncryptedPrivateMessagePayload(
                "v1",
                "AES-GCM",
                "RSA-OAEP",
                "ciphertext",
                "nonce",
                "alice-key",
                "sender-envelope",
                "bob-key",
                "recipient-envelope"
        );

        privateChatMessageService.sendMessage(aliceSession, chat.chatId(), encryptedMessage);

        PrivateMessageView recipientMessage = (PrivateMessageView) sender.messagesFor("bob-id").getFirst();
        assertThat(recipientMessage.encryptedMessage().ciphertext()).isEqualTo("ciphertext");
        assertThat(firstTextFrame(aliceOutput)).contains("\"ciphertext\":\"ciphertext\"");
    }

    @Test
    void presenceServiceFansOutPingEventsToSubscribers() {
        InMemoryChatRepository chatRepository = new InMemoryChatRepository();
        InMemoryUserStore userStore = new InMemoryUserStore();
        userStore.add(user("alice-id", "alice@example.com"));
        userStore.add(user("bob-id", "bob@example.com"));
        ChatService chatService = new ChatService(chatRepository, new NoopSessionRegistry(), userStore, new NoopWebSocketMessageSender());
        chatService.createChat("alice-id", "bob@example.com");
        RecordingWebSocketMessageSender sender = new RecordingWebSocketMessageSender();
        PresenceService presenceService = new PresenceService(chatService, sender);

        presenceService.sendPresence(new ClientSession("alice-id", new Socket(), new ByteArrayOutputStream()));

        PresenceEvent presenceEvent = (PresenceEvent) sender.messagesFor("bob-id").getFirst();
        assertThat(presenceEvent.type()).isEqualTo("presence");
        assertThat(presenceEvent.username()).isEqualTo("alice@example.com");
    }

    private String firstTextFrame(ByteArrayOutputStream output) {
        return textFrames(output).getFirst();
    }

    private String lastTextFrame(ByteArrayOutputStream output) {
        return textFrames(output).getLast();
    }

    private List<String> textFrames(ByteArrayOutputStream output) {
        byte[] bytes = output.toByteArray();
        List<String> frames = new ArrayList<>();
        int offset = 0;
        while (offset < bytes.length) {
            int lengthCode = bytes[offset + 1] & 0x7F;
            int headerLength = 2;
            int payloadLength;
            if (lengthCode < 126) {
                payloadLength = lengthCode;
            } else if (lengthCode == 126) {
                payloadLength = ((bytes[offset + 2] & 0xFF) << 8) | (bytes[offset + 3] & 0xFF);
                headerLength = 4;
            } else {
                throw new IllegalStateException("Test helper does not support 64-bit payload lengths");
            }
            frames.add(new String(bytes, offset + headerLength, payloadLength, StandardCharsets.UTF_8));
            offset += headerLength + payloadLength;
        }
        return frames;
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

    private static final class FailingOutputStream extends OutputStream {

        @Override
        public void write(int b) throws IOException {
            throw new IOException("database password leaked");
        }
    }
}
