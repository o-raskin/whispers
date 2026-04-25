package com.oraskin.websocket.message;

import com.oraskin.chat.TestSupport.InMemoryChatRepository;
import com.oraskin.chat.TestSupport.InMemoryPrivateChatKeyStore;
import com.oraskin.chat.TestSupport.InMemoryUserStore;
import com.oraskin.chat.key.service.PublicKeyService;
import com.oraskin.chat.key.value.RegisterPrivateChatKeyRequest;
import com.oraskin.chat.privatechat.service.PrivateChatService;
import com.oraskin.chat.privatechat.value.EncryptedPrivateMessagePayload;
import com.oraskin.chat.privatechat.value.PrivateMessageView;
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

class PrivateChatMessageServiceTest {

    @Test
    void sendMessageDeliversEncryptedPayloadsAndEchoesSender() throws Exception {
        InMemoryChatRepository chatRepository = new InMemoryChatRepository();
        InMemoryPrivateChatKeyStore keyStore = new InMemoryPrivateChatKeyStore();
        PublicKeyService publicKeyService = new PublicKeyService(keyStore);
        InMemoryUserStore userStore = new InMemoryUserStore();
        userStore.add(user("alice-id", "alice@example.com"));
        userStore.add(user("bob-id", "bob@example.com"));
        publicKeyService.registerCurrentKey("alice-id", new RegisterPrivateChatKeyRequest("alice-key", "alice-spki", "RSA-OAEP", "spki"));
        publicKeyService.registerCurrentKey("bob-id", new RegisterPrivateChatKeyRequest("bob-key", "bob-spki", "RSA-OAEP", "spki"));
        PrivateChatService privateChatService = new PrivateChatService(chatRepository, userStore, publicKeyService);
        long chatId = privateChatService.createChat("alice-id", "bob@example.com", "alice-key").chatId();
        RecordingWebSocketMessageSender sender = new RecordingWebSocketMessageSender();
        PrivateChatMessageService service = new PrivateChatMessageService(privateChatService, sender);
        ByteArrayOutputStream aliceOutput = new ByteArrayOutputStream();
        ClientSession aliceSession = new ClientSession("alice-id", new Socket(), aliceOutput);
        EncryptedPrivateMessagePayload encryptedMessage = new EncryptedPrivateMessagePayload(
                "v1", "AES-GCM", "RSA-OAEP", "ciphertext", "nonce", "alice-key", "sender-envelope", "bob-key", "recipient-envelope"
        );

        service.sendMessage(aliceSession, chatId, encryptedMessage);

        PrivateMessageView recipientMessage = (PrivateMessageView) sender.messagesFor("bob-id").getFirst();
        assertThat(recipientMessage.encryptedMessage().ciphertext()).isEqualTo("ciphertext");
        assertThat(firstTextFrame(aliceOutput)).contains("\"ciphertext\":\"ciphertext\"");
    }

    private String firstTextFrame(ByteArrayOutputStream output) {
        byte[] frame = output.toByteArray();
        int lengthCode = frame[1] & 0x7F;
        if (lengthCode < 126) {
            return new String(frame, 2, lengthCode, StandardCharsets.UTF_8);
        }
        int payloadLength = ((frame[2] & 0xFF) << 8) | (frame[3] & 0xFF);
        return new String(frame, 4, payloadLength, StandardCharsets.UTF_8);
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
