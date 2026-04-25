package com.oraskin.resource;

import com.oraskin.chat.TestSupport.InMemoryChatRepository;
import com.oraskin.chat.TestSupport.InMemoryUserStore;
import com.oraskin.chat.TestSupport.NoopWebSocketMessageSender;
import com.oraskin.chat.service.ChatService;
import com.oraskin.chat.value.ChatSummary;
import com.oraskin.chat.value.ChatType;
import com.oraskin.common.http.QueryParams;
import com.oraskin.user.session.persistence.InMemorySessionRegistry;
import org.junit.jupiter.api.Test;

import java.util.List;

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
}
