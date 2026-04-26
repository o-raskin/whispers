package com.oraskin.resource;

import com.oraskin.auth.AuthTestSupport;
import com.oraskin.chat.TestSupport.InMemoryChatRepository;
import com.oraskin.chat.TestSupport.InMemoryPrivateChatKeyStore;
import com.oraskin.chat.TestSupport.InMemoryUserStore;
import com.oraskin.chat.TestSupport.NoopWebSocketMessageSender;
import com.oraskin.chat.key.service.PublicKeyService;
import com.oraskin.chat.key.value.RegisterPrivateChatKeyRequest;
import com.oraskin.chat.privatechat.service.PrivateChatService;
import com.oraskin.chat.privatechat.value.EncryptedPrivateMessagePayload;
import com.oraskin.chat.privatechat.value.PrivateChatView;
import com.oraskin.chat.privatechat.value.PrivateMessageView;
import com.oraskin.chat.repository.entity.MessageRecord;
import com.oraskin.chat.service.ChatService;
import com.oraskin.chat.value.ChatSummary;
import com.oraskin.chat.value.EditMessageRequest;
import com.oraskin.common.auth.AuthenticatedUser;
import com.oraskin.common.http.QueryParams;
import com.oraskin.user.data.domain.User;
import com.oraskin.user.profile.UserProfileService;
import com.oraskin.user.profile.UserProfileView;
import com.oraskin.user.session.persistence.InMemorySessionRegistry;
import com.oraskin.user.session.service.SessionService;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.net.Socket;
import java.util.List;

import static com.oraskin.auth.AuthTestSupport.authenticatedUser;
import static com.oraskin.chat.TestSupport.user;
import static org.assertj.core.api.Assertions.assertThat;

class ResourceControllersTest {

    @Test
    void chatAndUserControllersDelegateToUnderlyingServices() {
        InMemoryChatRepository chatRepository = new InMemoryChatRepository();
        InMemoryPrivateChatKeyStore keyStore = new InMemoryPrivateChatKeyStore();
        PublicKeyService publicKeyService = new PublicKeyService(keyStore);
        InMemoryUserStore userStore = new InMemoryUserStore();
        userStore.add(user("alice-id", "alice@example.com"));
        userStore.add(user("bob-id", "bob@example.com"));
        publicKeyService.registerCurrentKey("alice-id", new RegisterPrivateChatKeyRequest("alice-key", "alice-spki", "RSA-OAEP", "spki"));
        publicKeyService.registerCurrentKey("bob-id", new RegisterPrivateChatKeyRequest("bob-key", "bob-spki", "RSA-OAEP", "spki"));
        ChatService chatService = new ChatService(chatRepository, new InMemorySessionRegistry(), userStore, new NoopWebSocketMessageSender());
        PrivateChatService privateChatService = new PrivateChatService(chatRepository, userStore, publicKeyService);
        AuthenticatedUser alice = authenticatedUser("alice-id", "alice@example.com", "hash");

        ChatsController chatsController = new ChatsController(chatService);
        MessagesController messagesController = new MessagesController(chatService);
        PrivateChatsController privateChatsController = new PrivateChatsController(privateChatService);
        PublicKeysController publicKeysController = new PublicKeysController(publicKeyService);
        UsersController usersController = new UsersController(chatService, new UserProfileService(userStore, new AuthTestSupport.InMemoryAuthIdentityStore()));

        ChatSummary directChat = chatsController.createChat(alice, "bob@example.com");
        List<ChatSummary> chats = chatsController.getChats(alice, QueryParams.fromTarget("/chats?keyId=alice-key"));
        publicKeysController.registerCurrentKeyPost(alice, new RegisterPrivateChatKeyRequest("alice-key-2", "alice-spki-2", "RSA-OAEP", "spki"));
        ChatSummary privateChatSummary = privateChatsController.createPrivateChat(alice, "bob@example.com", "alice-key");
        PrivateChatView privateChatView = privateChatsController.getPrivateChat(alice, privateChatSummary.chatId(), "alice-key");
        privateChatService.sendMessage(
                "alice-id",
                new com.oraskin.connection.SendPrivateMessageCommand(
                        privateChatSummary.chatId(),
                        new EncryptedPrivateMessagePayload(
                                "v1",
                                "AES-GCM",
                                "RSA-OAEP",
                                "ciphertext",
                                "nonce",
                                "alice-key",
                                "sender-envelope",
                                "bob-key",
                                "recipient-envelope"
                        )
                )
        );
        List<PrivateMessageView> privateMessages = privateChatsController.getMessages(alice, privateChatSummary.chatId(), "alice-key");
        MessageRecord message = chatService.sendMessage("alice-id", new com.oraskin.connection.SendMessageCommand(directChat.chatId(), "hello")).message();
        messagesController.editMessage(alice, message.messageId(), new EditMessageRequest("hello again"));
        List<MessageRecord> messages = messagesController.getMessages(alice, directChat.chatId());
        chatsController.deleteChat(alice, directChat.chatId());
        List<User> users = usersController.getUsers(alice);
        UserProfileView userProfileView = usersController.getUserProfile(alice, "bob-id");

        assertThat(directChat.username()).isEqualTo("bob@example.com");
        assertThat(chats).extracting(ChatSummary::chatId).contains(directChat.chatId());
        assertThat(privateChatView.currentUserKey().keyId()).isEqualTo("alice-key");
        assertThat(privateMessages.getFirst().encryptedMessage().ciphertext()).isEqualTo("ciphertext");
        assertThat(messages.getFirst().text()).isEqualTo("hello again");
        assertThat(messages.getFirst().updatedAt()).isEqualTo("2026-04-20T12:05:00Z");
        assertThat(chatsController.getChats(alice, QueryParams.fromTarget("/chats?keyId=alice-key")))
                .extracting(ChatSummary::chatId)
                .doesNotContain(directChat.chatId());
        assertThat(users.getFirst().username()).isEqualTo("bob@example.com");
        assertThat(userProfileView.username()).isEqualTo("bob@example.com");
    }

    @Test
    void userConnectionControllerOpensSessionAndReturnsConnectedBanner() {
        InMemorySessionRegistry sessionRegistry = new InMemorySessionRegistry();
        SessionService sessionService = new SessionService(sessionRegistry);
        UserConnectionController controller = new UserConnectionController(sessionService);

        assertThat(controller.connect(authenticatedUser("alice-id", "alice@example.com", "hash"), new Socket(), new ByteArrayOutputStream()))
                .isEqualTo("CONNECTED:alice@example.com");
        assertThat(sessionService.findSession("alice-id")).isNotNull();
    }
}
