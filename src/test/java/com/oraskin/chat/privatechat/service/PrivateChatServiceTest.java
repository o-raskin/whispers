package com.oraskin.chat.privatechat.service;

import com.oraskin.chat.TestSupport.InMemoryChatRepository;
import com.oraskin.chat.TestSupport.InMemoryPrivateChatKeyStore;
import com.oraskin.chat.TestSupport.InMemoryUserStore;
import com.oraskin.chat.key.service.PublicKeyService;
import com.oraskin.chat.key.value.RegisterPrivateChatKeyRequest;
import com.oraskin.chat.privatechat.value.EncryptedPrivateMessagePayload;
import com.oraskin.chat.service.ChatException;
import com.oraskin.chat.value.ChatType;
import com.oraskin.common.http.HttpStatus;
import com.oraskin.connection.SendPrivateMessageCommand;
import org.junit.jupiter.api.Test;

import static com.oraskin.chat.TestSupport.user;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PrivateChatServiceTest {

    @Test
    void privateChatRequiresRegisteredBrowserKeys() {
        InMemoryChatRepository chatRepository = new InMemoryChatRepository();
        InMemoryUserStore userStore = new InMemoryUserStore();
        userStore.add(user("alice-id", "alice@example.com"));
        userStore.add(user("bob-id", "bob@example.com"));

        PrivateChatService service = new PrivateChatService(
                chatRepository,
                userStore,
                new PublicKeyService(new InMemoryPrivateChatKeyStore())
        );

        ChatException exception = assertThrows(
                ChatException.class,
                () -> service.createChat("alice-id", "bob@example.com", "alice-browser-key")
        );

        assertEquals(HttpStatus.CONFLICT, exception.status());
        assertEquals("Register the current browser key before creating a PRIVATE chat", exception.getMessage());
    }

    @Test
    void privateChatExposesKeyMetadataAndStoresOnlyEncryptedPayload() {
        InMemoryChatRepository chatRepository = new InMemoryChatRepository();
        InMemoryPrivateChatKeyStore keyStore = new InMemoryPrivateChatKeyStore();
        PublicKeyService keyService = new PublicKeyService(keyStore);
        InMemoryUserStore userStore = new InMemoryUserStore();
        userStore.add(user("alice-id", "alice@example.com"));
        userStore.add(user("bob-id", "bob@example.com"));

        keyService.registerCurrentKey(
                "alice-id",
                new RegisterPrivateChatKeyRequest("alice-browser-key", "alice-spki", "RSA-OAEP", "spki")
        );
        keyService.registerCurrentKey(
                "bob-id",
                new RegisterPrivateChatKeyRequest("bob-browser-key", "bob-spki", "RSA-OAEP", "spki")
        );

        PrivateChatService service = new PrivateChatService(chatRepository, userStore, keyService);
        var chat = service.createChat("alice-id", "bob@example.com", "alice-browser-key");
        var privateChat = service.getChat("alice-id", chat.chatId(), "alice-browser-key");

        EncryptedPrivateMessagePayload encryptedMessage = new EncryptedPrivateMessagePayload(
                "v1",
                "AES-GCM",
                "RSA-OAEP",
                "ciphertext-base64",
                "nonce-base64",
                "alice-browser-key",
                "sender-envelope-base64",
                "bob-browser-key",
                "recipient-envelope-base64"
        );

        var delivery = service.sendMessage("alice-id", new SendPrivateMessageCommand(chat.chatId(), encryptedMessage));
        var history = service.findMessages("alice-id", chat.chatId(), "alice-browser-key");

        assertEquals(ChatType.PRIVATE, chat.type());
        assertEquals("alice-browser-key", privateChat.currentUserKey().keyId());
        assertEquals("bob-browser-key", privateChat.counterpartKey().keyId());
        assertEquals("bob-id", delivery.recipientUserId());
        assertEquals("ciphertext-base64", delivery.message().encryptedMessage().ciphertext());
        assertEquals("alice@example.com", delivery.message().senderUsername());
        assertTrue(chatRepository.findMessages(chat.chatId()).isEmpty());
        assertEquals(1, chatRepository.findPrivateMessages(chat.chatId()).size());
        assertEquals("sender-envelope-base64", history.getFirst().encryptedMessage().senderMessageKeyEnvelope());
    }

    @Test
    void privateMessagesFailWhenRecipientKeyMetadataIsStale() {
        InMemoryChatRepository chatRepository = new InMemoryChatRepository();
        InMemoryPrivateChatKeyStore keyStore = new InMemoryPrivateChatKeyStore();
        PublicKeyService keyService = new PublicKeyService(keyStore);
        InMemoryUserStore userStore = new InMemoryUserStore();
        userStore.add(user("alice-id", "alice@example.com"));
        userStore.add(user("bob-id", "bob@example.com"));

        keyService.registerCurrentKey(
                "alice-id",
                new RegisterPrivateChatKeyRequest("alice-browser-key", "alice-spki", "RSA-OAEP", "spki")
        );
        keyService.registerCurrentKey(
                "bob-id",
                new RegisterPrivateChatKeyRequest("bob-browser-key", "bob-spki", "RSA-OAEP", "spki")
        );

        PrivateChatService service = new PrivateChatService(chatRepository, userStore, keyService);
        var chat = service.createChat("alice-id", "bob@example.com", "alice-browser-key");

        ChatException exception = assertThrows(
                ChatException.class,
                () -> service.sendMessage(
                        "alice-id",
                        new SendPrivateMessageCommand(
                                chat.chatId(),
                                new EncryptedPrivateMessagePayload(
                                        "v1",
                                        "AES-GCM",
                                        "RSA-OAEP",
                                        "ciphertext-base64",
                                        "nonce-base64",
                                        "alice-browser-key",
                                        "sender-envelope-base64",
                                        "stale-bob-key",
                                        "recipient-envelope-base64"
                                )
                        )
                )
        );

        assertEquals(HttpStatus.CONFLICT, exception.status());
        assertEquals("Recipient browser key for this PRIVATE chat is not registered", exception.getMessage());
    }

    @Test
    void privateChatMessagesRequireMatchingBrowserKeyId() {
        InMemoryChatRepository chatRepository = new InMemoryChatRepository();
        InMemoryPrivateChatKeyStore keyStore = new InMemoryPrivateChatKeyStore();
        PublicKeyService keyService = new PublicKeyService(keyStore);
        InMemoryUserStore userStore = new InMemoryUserStore();
        userStore.add(user("alice-id", "alice@example.com"));
        userStore.add(user("bob-id", "bob@example.com"));

        keyService.registerCurrentKey("alice-id", new RegisterPrivateChatKeyRequest("alice-browser-key", "alice-spki", "RSA-OAEP", "spki"));
        keyService.registerCurrentKey("bob-id", new RegisterPrivateChatKeyRequest("bob-browser-key", "bob-spki", "RSA-OAEP", "spki"));
        keyService.registerCurrentKey("alice-id", new RegisterPrivateChatKeyRequest("alice-browser-key-2", "alice-spki-2", "RSA-OAEP", "spki"));

        PrivateChatService service = new PrivateChatService(chatRepository, userStore, keyService);
        var chat = service.createChat("alice-id", "bob@example.com", "alice-browser-key");

        ChatException exception = assertThrows(
                ChatException.class,
                () -> service.findMessages("alice-id", chat.chatId(), "alice-browser-key-2")
        );

        assertEquals(HttpStatus.CONFLICT, exception.status());
        assertEquals("PRIVATE chat is bound to another browser key", exception.getMessage());
    }

    @Test
    void privateChatsCanBeCreatedFromMultipleBrowsersForSameAccount() {
        InMemoryChatRepository chatRepository = new InMemoryChatRepository();
        InMemoryPrivateChatKeyStore keyStore = new InMemoryPrivateChatKeyStore();
        PublicKeyService keyService = new PublicKeyService(keyStore);
        InMemoryUserStore userStore = new InMemoryUserStore();
        userStore.add(user("alice-id", "alice@example.com"));
        userStore.add(user("bob-id", "bob@example.com"));

        keyService.registerCurrentKey("alice-id", new RegisterPrivateChatKeyRequest("alice-browser-key-1", "alice-spki-1", "RSA-OAEP", "spki"));
        keyService.registerCurrentKey("alice-id", new RegisterPrivateChatKeyRequest("alice-browser-key-2", "alice-spki-2", "RSA-OAEP", "spki"));
        keyService.registerCurrentKey("bob-id", new RegisterPrivateChatKeyRequest("bob-browser-key", "bob-spki", "RSA-OAEP", "spki"));

        PrivateChatService service = new PrivateChatService(chatRepository, userStore, keyService);

        var firstBrowserChat = service.createChat("alice-id", "bob@example.com", "alice-browser-key-1");
        var secondBrowserChat = service.createChat("alice-id", "bob@example.com", "alice-browser-key-2");

        assertEquals(2, chatRepository.findChatsForUser("alice-id").size());
        assertTrue(firstBrowserChat.chatId() != secondBrowserChat.chatId());
        assertEquals(
                "alice-browser-key-1",
                chatRepository.findChat(firstBrowserChat.chatId()).keyIdForUser("alice-id")
        );
        assertEquals(
                "alice-browser-key-2",
                chatRepository.findChat(secondBrowserChat.chatId()).keyIdForUser("alice-id")
        );
    }
}
