package com.oraskin.resource;

import com.oraskin.chat.TestSupport.InMemoryChatRepository;
import com.oraskin.chat.TestSupport.InMemoryPrivateChatKeyStore;
import com.oraskin.chat.TestSupport.InMemoryUserStore;
import com.oraskin.chat.key.service.PublicKeyService;
import com.oraskin.chat.key.value.RegisterPrivateChatKeyRequest;
import com.oraskin.chat.privatechat.service.PrivateChatService;
import com.oraskin.chat.privatechat.value.EncryptedPrivateMessagePayload;
import com.oraskin.chat.privatechat.value.PrivateChatView;
import com.oraskin.chat.privatechat.value.PrivateMessageView;
import com.oraskin.chat.value.ChatSummary;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.oraskin.auth.AuthTestSupport.authenticatedUser;
import static com.oraskin.chat.TestSupport.user;
import static org.assertj.core.api.Assertions.assertThat;

class PrivateChatsControllerTest {

    @Test
    void createGetAndListMessagesDelegateToPrivateChatService() {
        InMemoryChatRepository chatRepository = new InMemoryChatRepository();
        InMemoryPrivateChatKeyStore keyStore = new InMemoryPrivateChatKeyStore();
        PublicKeyService publicKeyService = new PublicKeyService(keyStore);
        InMemoryUserStore userStore = new InMemoryUserStore();
        userStore.add(user("alice-id", "alice@example.com"));
        userStore.add(user("bob-id", "bob@example.com"));
        publicKeyService.registerCurrentKey("alice-id", new RegisterPrivateChatKeyRequest("alice-key", "alice-spki", "RSA-OAEP", "spki"));
        publicKeyService.registerCurrentKey("bob-id", new RegisterPrivateChatKeyRequest("bob-key", "bob-spki", "RSA-OAEP", "spki"));
        PrivateChatService privateChatService = new PrivateChatService(chatRepository, userStore, publicKeyService);
        PrivateChatsController controller = new PrivateChatsController(privateChatService);

        ChatSummary chat = controller.createPrivateChat(authenticatedUser("alice-id", "alice@example.com", "hash"), "bob@example.com", "alice-key");
        PrivateChatView view = controller.getPrivateChat(authenticatedUser("alice-id", "alice@example.com", "hash"), chat.chatId(), "alice-key");
        privateChatService.sendMessage(
                "alice-id",
                new com.oraskin.connection.SendPrivateMessageCommand(
                        chat.chatId(),
                        new EncryptedPrivateMessagePayload(
                                "v1", "AES-GCM", "RSA-OAEP", "ciphertext", "nonce", "alice-key", "sender-envelope", "bob-key", "recipient-envelope"
                        )
                )
        );
        List<PrivateMessageView> messages = controller.getMessages(authenticatedUser("alice-id", "alice@example.com", "hash"), chat.chatId(), "alice-key");

        assertThat(view.currentUserKey().keyId()).isEqualTo("alice-key");
        assertThat(messages).hasSize(1);
        assertThat(messages.getFirst().encryptedMessage().ciphertext()).isEqualTo("ciphertext");
    }
}
