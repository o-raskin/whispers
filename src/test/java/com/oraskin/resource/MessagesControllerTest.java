package com.oraskin.resource;

import com.oraskin.chat.TestSupport.InMemoryChatRepository;
import com.oraskin.chat.TestSupport.InMemoryUserStore;
import com.oraskin.chat.repository.entity.MessageRecord;
import com.oraskin.chat.service.ChatService;
import com.oraskin.connection.MessageDeleteEvent;
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

class MessagesControllerTest {

    @Test
    void getMessagesReturnsMessageHistoryForAuthenticatedUser() {
        InMemoryChatRepository chatRepository = new InMemoryChatRepository();
        InMemoryUserStore userStore = new InMemoryUserStore();
        userStore.add(user("alice-id", "alice@example.com"));
        userStore.add(user("bob-id", "bob@example.com"));
        ChatService chatService = new ChatService(chatRepository, new InMemorySessionRegistry(), userStore, new RecordingWebSocketMessageSender());
        MessagesController controller = new MessagesController(chatService);
        long chatId = chatService.createChat("alice-id", "bob@example.com").chatId();
        chatService.sendMessage("alice-id", new com.oraskin.connection.SendMessageCommand(chatId, "hello"));

        List<MessageRecord> messages = controller.getMessages(authenticatedUser("alice-id", "alice@example.com", "hash"), chatId);

        assertThat(messages).hasSize(1);
        assertThat(messages.getFirst().messageId()).isEqualTo(1L);
        assertThat(messages.getFirst().text()).isEqualTo("hello");
        assertThat(messages.getFirst().senderUserId()).isEqualTo("alice@example.com");
    }

    @Test
    void deleteMessageRemovesOwnedMessage() {
        InMemoryChatRepository chatRepository = new InMemoryChatRepository();
        InMemoryUserStore userStore = new InMemoryUserStore();
        userStore.add(user("alice-id", "alice@example.com"));
        userStore.add(user("bob-id", "bob@example.com"));
        RecordingWebSocketMessageSender sender = new RecordingWebSocketMessageSender();
        ChatService chatService = new ChatService(chatRepository, new InMemorySessionRegistry(), userStore, sender);
        MessagesController controller = new MessagesController(chatService);
        long chatId = chatService.createChat("alice-id", "bob@example.com").chatId();
        MessageRecord message = chatService.sendMessage("alice-id", new com.oraskin.connection.SendMessageCommand(chatId, "hello")).message();

        controller.deleteMessage(authenticatedUser("alice-id", "alice@example.com", "hash"), message.messageId());

        assertThat(controller.getMessages(authenticatedUser("alice-id", "alice@example.com", "hash"), chatId)).isEmpty();
        MessageDeleteEvent aliceEvent = (MessageDeleteEvent) sender.messagesFor("alice-id").getFirst();
        MessageDeleteEvent bobEvent = (MessageDeleteEvent) sender.messagesFor("bob-id").getFirst();
        assertThat(aliceEvent.type()).isEqualTo("MESSAGE_DELETE");
        assertThat(aliceEvent.chatId()).isEqualTo(chatId);
        assertThat(aliceEvent.messageId()).isEqualTo(message.messageId());
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
