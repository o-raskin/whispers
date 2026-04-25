package com.oraskin.websocket.presence;

import com.oraskin.chat.TestSupport.InMemoryChatRepository;
import com.oraskin.chat.TestSupport.InMemoryUserStore;
import com.oraskin.chat.TestSupport.NoopSessionRegistry;
import com.oraskin.chat.TestSupport.NoopWebSocketMessageSender;
import com.oraskin.chat.service.ChatService;
import com.oraskin.connection.PresenceEvent;
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
import static org.assertj.core.api.Assertions.assertThat;

class PresenceServiceTest {

    @Test
    void sendPresenceFansOutPingEventsToSubscribers() {
        InMemoryChatRepository chatRepository = new InMemoryChatRepository();
        InMemoryUserStore userStore = new InMemoryUserStore();
        userStore.add(user("alice-id", "alice@example.com"));
        userStore.add(user("bob-id", "bob@example.com"));
        ChatService chatService = new ChatService(chatRepository, new NoopSessionRegistry(), userStore, new NoopWebSocketMessageSender());
        chatService.createChat("alice-id", "bob@example.com");
        RecordingWebSocketMessageSender sender = new RecordingWebSocketMessageSender();
        PresenceService service = new PresenceService(chatService, sender);

        service.sendPresence(new ClientSession("alice-id", new Socket(), new ByteArrayOutputStream()));

        PresenceEvent event = (PresenceEvent) sender.messagesFor("bob-id").getFirst();
        assertThat(event.type()).isEqualTo("presence");
        assertThat(event.username()).isEqualTo("alice@example.com");
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
