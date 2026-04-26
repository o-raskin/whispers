package com.oraskin.resource;

import com.oraskin.chat.TestSupport.InMemoryChatRepository;
import com.oraskin.chat.TestSupport.InMemoryUserStore;
import com.oraskin.chat.TestSupport.NoopWebSocketMessageSender;
import com.oraskin.chat.repository.entity.MessageRecord;
import com.oraskin.chat.service.ChatService;
import com.oraskin.chat.value.ChatSummary;
import com.oraskin.chat.value.ChatType;
import com.oraskin.connection.ChatDeleteEvent;
import com.oraskin.common.http.QueryParams;
import com.oraskin.user.session.persistence.InMemorySessionRegistry;
import com.oraskin.websocket.WebSocketMessageSender;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.oraskin.auth.AuthTestSupport.authenticatedUser;
import static com.oraskin.chat.TestSupport.user;
import static org.assertj.core.api.Assertions.assertThat;

class ChatsControllerTest {

    @Test
    void createChatAndGetChatsDelegateToChatService() {
        InMemoryChatRepository chatRepository = new InMemoryChatRepository();
        InMemoryUserStore userStore = new InMemoryUserStore();
        userStore.add(user("alice-id", "alice@example.com"));
        userStore.add(user("bob-id", "bob@example.com"));
        ChatService chatService = new ChatService(chatRepository, new InMemorySessionRegistry(), userStore, new NoopWebSocketMessageSender());
        ChatsController controller = new ChatsController(chatService);

        ChatSummary created = controller.createChat(authenticatedUser("alice-id", "alice@example.com", "hash"), "bob@example.com");
        List<ChatSummary> chats = controller.getChats(
                authenticatedUser("alice-id", "alice@example.com", "hash"),
                QueryParams.fromTarget("/chats")
        );

        assertThat(created.username()).isEqualTo("bob@example.com");
        assertThat(created.type()).isEqualTo(ChatType.DIRECT);
        assertThat(chats).extracting(ChatSummary::chatId).contains(created.chatId());
    }

    @Test
    void deleteChatRemovesChatAndNotifiesParticipants() {
        InMemoryChatRepository chatRepository = new InMemoryChatRepository();
        InMemoryUserStore userStore = new InMemoryUserStore();
        userStore.add(user("alice-id", "alice@example.com"));
        userStore.add(user("bob-id", "bob@example.com"));
        RecordingWebSocketMessageSender sender = new RecordingWebSocketMessageSender();
        ChatService chatService = new ChatService(chatRepository, new InMemorySessionRegistry(), userStore, sender);
        ChatsController controller = new ChatsController(chatService);

        ChatSummary created = controller.createChat(authenticatedUser("alice-id", "alice@example.com", "hash"), "bob@example.com");
        MessageRecord message = chatService.sendMessage("alice-id", new com.oraskin.connection.SendMessageCommand(created.chatId(), "hello")).message();

        controller.deleteChat(authenticatedUser("bob-id", "bob@example.com", "hash"), created.chatId());

        assertThat(chatRepository.findChat(created.chatId())).isNull();
        assertThat(chatRepository.findMessage(message.messageId())).isNull();
        ChatDeleteEvent aliceEvent = (ChatDeleteEvent) sender.messagesFor("alice-id").getFirst();
        ChatDeleteEvent bobEvent = (ChatDeleteEvent) sender.messagesFor("bob-id").getFirst();
        assertThat(aliceEvent.type()).isEqualTo("CHAT_DELETE");
        assertThat(aliceEvent.chatId()).isEqualTo(created.chatId());
        assertThat(bobEvent).isEqualTo(aliceEvent);
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
