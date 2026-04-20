package com.oraskin.chat.service;

import com.oraskin.chat.TestSupport.InMemoryChatRepository;
import com.oraskin.chat.TestSupport.InMemoryPrivateChatKeyStore;
import com.oraskin.chat.TestSupport.InMemoryUserStore;
import com.oraskin.chat.TestSupport.NoopSessionRegistry;
import com.oraskin.chat.key.service.PublicKeyService;
import com.oraskin.chat.value.ChatType;
import com.oraskin.common.http.HttpStatus;
import com.oraskin.connection.MessageDelivery;
import com.oraskin.connection.SendMessageCommand;
import org.junit.jupiter.api.Test;

import static com.oraskin.chat.TestSupport.user;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ChatServiceTest {

    @Test
    void directChatsKeepExistingPlaintextFlow() {
        InMemoryChatRepository chatRepository = new InMemoryChatRepository();
        InMemoryUserStore userStore = new InMemoryUserStore();
        userStore.add(user("alice-id", "alice@example.com"));
        userStore.add(user("bob-id", "bob@example.com"));

        ChatService chatService = new ChatService(
                chatRepository,
                new NoopSessionRegistry(),
                userStore
        );

        var chat = chatService.createChat("alice-id", "bob@example.com");
        MessageDelivery delivery = chatService.sendMessage("alice-id", new SendMessageCommand(chat.chatId(), "hello"));

        assertEquals(ChatType.DIRECT, chat.type());
        assertEquals("bob@example.com", chat.username());
        assertEquals("bob-id", delivery.recipientUserId());
        assertEquals("alice@example.com", delivery.message().senderUserId());
        assertEquals("hello", delivery.message().text());
        assertEquals("hello", chatRepository.findMessages(chat.chatId()).getFirst().text());
    }

    @Test
    void directMessageFlowRejectsPrivateChats() {
        InMemoryChatRepository chatRepository = new InMemoryChatRepository();
        InMemoryUserStore userStore = new InMemoryUserStore();
        userStore.add(user("alice-id", "alice@example.com"));
        userStore.add(user("bob-id", "bob@example.com"));

        ChatService chatService = new ChatService(
                chatRepository,
                new NoopSessionRegistry(),
                userStore
        );
        var privateChat = chatRepository.createChat("alice-id", "alice-key", "bob-id", "bob-key", ChatType.PRIVATE);

        ChatException exception = assertThrows(
                ChatException.class,
                () -> chatService.sendMessage("alice-id", new SendMessageCommand(privateChat.chatId(), "plaintext"))
        );

        assertEquals(HttpStatus.BAD_REQUEST, exception.status());
        assertEquals("Use PRIVATE_MESSAGE for PRIVATE chats", exception.getMessage());
    }

    @Test
    void chatsEndpointFiltersPrivateChatsByBrowserKeyId() {
        InMemoryChatRepository chatRepository = new InMemoryChatRepository();
        InMemoryPrivateChatKeyStore keyStore = new InMemoryPrivateChatKeyStore();
        PublicKeyService publicKeyService = new PublicKeyService(keyStore);
        InMemoryUserStore userStore = new InMemoryUserStore();
        userStore.add(user("alice-id", "alice@example.com"));
        userStore.add(user("bob-id", "bob@example.com"));
        publicKeyService.registerCurrentKey("alice-id", new com.oraskin.chat.key.value.RegisterPrivateChatKeyRequest("alice-key", "alice-spki", "RSA-OAEP", "spki"));
        publicKeyService.registerCurrentKey("bob-id", new com.oraskin.chat.key.value.RegisterPrivateChatKeyRequest("bob-key", "bob-spki", "RSA-OAEP", "spki"));

        ChatService chatService = new ChatService(chatRepository, new NoopSessionRegistry(), userStore);
        chatRepository.createChat("alice-id", "alice-key", "bob-id", "bob-key", ChatType.PRIVATE);
        chatRepository.createChat("alice-id", null, "bob-id", null, ChatType.DIRECT);

        var chatsWithoutKey = chatService.findChatsForUser("alice-id", null);
        var chatsWithOtherKey = chatService.findChatsForUser("alice-id", "other-browser-key");
        var chatsWithMatchingKey = chatService.findChatsForUser("alice-id", "alice-key");

        assertEquals(1, chatsWithoutKey.size());
        assertEquals(ChatType.DIRECT, chatsWithoutKey.getFirst().type());
        assertEquals(1, chatsWithOtherKey.size());
        assertEquals(ChatType.DIRECT, chatsWithOtherKey.getFirst().type());
        assertEquals(2, chatsWithMatchingKey.size());
    }

    @Test
    void chatsEndpointReturnsOnlyPrivateChatsBoundToRequestedBrowserKey() {
        InMemoryChatRepository chatRepository = new InMemoryChatRepository();
        InMemoryUserStore userStore = new InMemoryUserStore();
        userStore.add(user("alice-id", "alice@example.com"));
        userStore.add(user("bob-id", "bob@example.com"));

        ChatService chatService = new ChatService(chatRepository, new NoopSessionRegistry(), userStore);
        chatRepository.createChat("alice-id", "alice-key-1", "bob-id", "bob-key", ChatType.PRIVATE);
        chatRepository.createChat("alice-id", "alice-key-2", "bob-id", "bob-key", ChatType.PRIVATE);

        var firstBrowserChats = chatService.findChatsForUser("alice-id", "alice-key-1");
        var secondBrowserChats = chatService.findChatsForUser("alice-id", "alice-key-2");

        assertEquals(1, firstBrowserChats.size());
        assertEquals(ChatType.PRIVATE, firstBrowserChats.getFirst().type());
        assertEquals(1, secondBrowserChats.size());
        assertEquals(ChatType.PRIVATE, secondBrowserChats.getFirst().type());
        assertNotEquals(firstBrowserChats.getFirst().chatId(), secondBrowserChats.getFirst().chatId());
    }
}
