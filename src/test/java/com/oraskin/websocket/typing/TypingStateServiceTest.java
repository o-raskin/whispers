package com.oraskin.websocket.typing;

import com.oraskin.chat.TestSupport.InMemoryChatRepository;
import com.oraskin.chat.TestSupport.InMemoryUserStore;
import com.oraskin.chat.TestSupport.NoopSessionRegistry;
import com.oraskin.chat.TestSupport.NoopWebSocketMessageSender;
import com.oraskin.chat.service.ChatService;
import com.oraskin.user.session.ClientSession;
import com.oraskin.websocket.WebSocketMessageSender;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.oraskin.chat.TestSupport.user;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class TypingStateServiceTest {

    @Test
    void repeatedTypingStartOnlyNotifiesRecipientOncePerActiveTypingWindow() {
        InMemoryChatRepository chatRepository = new InMemoryChatRepository();
        InMemoryUserStore userStore = new InMemoryUserStore();
        userStore.add(user("alice-id", "alice@example.com"));
        userStore.add(user("bob-id", "bob@example.com"));

        ChatService chatService = new ChatService(chatRepository, new NoopSessionRegistry(), userStore, new NoopWebSocketMessageSender());
        var chat = chatService.createChat("alice-id", "bob@example.com");
        RecordingWebSocketMessageSender sender = new RecordingWebSocketMessageSender();
        TypingStateService typingStateService = new TypingStateService(chatService, sender);

        ClientSession session = new ClientSession("alice-id", new Socket(), new ByteArrayOutputStream());
        typingStateService.startTyping(session, chat.chatId());
        typingStateService.startTyping(session, chat.chatId());

        List<Object> notifications = sender.messagesFor("bob-id");
        assertEquals(1, notifications.size());
        TypingEvent event = assertInstanceOf(TypingEvent.class, notifications.getFirst());
        assertEquals("typing:start", event.type());
        assertEquals(chat.chatId(), event.chatId());
        assertEquals("alice@example.com", event.username());
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
