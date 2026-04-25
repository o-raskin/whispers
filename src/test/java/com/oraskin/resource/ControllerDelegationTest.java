package com.oraskin.resource;

import com.oraskin.auth.AuthTestSupport;
import com.oraskin.auth.api.AuthController;
import com.oraskin.auth.service.AccessTokenService;
import com.oraskin.auth.service.AuthService;
import com.oraskin.auth.service.IdentityProvisioningService;
import com.oraskin.auth.service.RefreshTokenService;
import com.oraskin.chat.TestSupport.InMemoryChatRepository;
import com.oraskin.chat.TestSupport.InMemoryPrivateChatKeyStore;
import com.oraskin.chat.TestSupport.InMemoryUserStore;
import com.oraskin.chat.TestSupport.NoopWebSocketMessageSender;
import com.oraskin.chat.key.service.PublicKeyService;
import com.oraskin.chat.key.value.RegisterPrivateChatKeyRequest;
import com.oraskin.chat.privatechat.service.PrivateChatService;
import com.oraskin.chat.privatechat.value.PrivateChatView;
import com.oraskin.chat.repository.entity.MessageRecord;
import com.oraskin.chat.service.ChatService;
import com.oraskin.chat.value.ChatSummary;
import com.oraskin.common.auth.AuthenticatedUser;
import com.oraskin.common.http.HttpRequest;
import com.oraskin.common.http.QueryParams;
import com.oraskin.common.mvc.ControllerResult;
import com.oraskin.user.profile.UserProfileService;
import com.oraskin.user.profile.UserProfileView;
import com.oraskin.user.session.persistence.InMemorySessionRegistry;
import com.oraskin.user.session.service.SessionService;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.net.Socket;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static com.oraskin.auth.AuthTestSupport.authenticatedUser;
import static com.oraskin.auth.AuthTestSupport.externalIdentity;
import static com.oraskin.auth.AuthTestSupport.oauthLoginRequest;
import static com.oraskin.chat.TestSupport.user;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ControllerDelegationTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-04-21T10:15:30Z"), ZoneOffset.UTC);

    @Test
    void authControllerDelegatesAcrossLoginRefreshMeAndLogout() {
        AuthTestSupport.RecordingAccessTokenStore accessTokenStore = new AuthTestSupport.RecordingAccessTokenStore();
        AuthTestSupport.RecordingRefreshTokenStore refreshTokenStore = new AuthTestSupport.RecordingRefreshTokenStore();
        AuthTestSupport.InMemoryAuthIdentityStore authIdentityStore = new AuthTestSupport.InMemoryAuthIdentityStore();
        AuthTestSupport.RecordingUserStore userStore = new AuthTestSupport.RecordingUserStore();
        AccessTokenService accessTokenService = new AccessTokenService(accessTokenStore, AuthTestSupport.authConfig(), CLOCK);
        RefreshTokenService refreshTokenService = new RefreshTokenService(refreshTokenStore, accessTokenService, AuthTestSupport.authConfig(), CLOCK);
        AuthService authService = new AuthService(
                List.of(new AuthTestSupport.FixedOAuthProviderAuthenticator(
                        "google",
                        externalIdentity("google", "subject-1", "alice@example.com", "Alice", "Example", "https://example.com/a.png")
                )),
                new IdentityProvisioningService(authIdentityStore, userStore),
                authIdentityStore,
                accessTokenService,
                refreshTokenService,
                AuthTestSupport.authConfig(),
                userStore,
                false
        );
        AuthController authController = new AuthController(authService);

        ControllerResult loginResult = authController.login("google", oauthLoginRequest());
        String refreshCookie = loginResult.headers().get("Set-Cookie").getFirst();
        String refreshToken = refreshCookie.split(";", 2)[0].split("=", 2)[1];
        AuthenticatedUser currentUser = authenticatedUser(
                ((com.oraskin.auth.domain.LoginResponse) loginResult.body()).user().userId(),
                "alice@example.com",
                accessTokenStore.createdTokensByHash().keySet().stream().reduce((first, second) -> second).orElseThrow()
        );

        ControllerResult refreshResult = authController.refresh(new HttpRequest(
                "POST",
                "/auth/refresh",
                QueryParams.fromTarget("/auth/refresh"),
                Map.of("cookie", AuthService.REFRESH_COOKIE_NAME + "=" + refreshToken),
                null,
                null
        ));
        com.oraskin.auth.domain.AuthUserProfile me = authController.me(currentUser);
        ControllerResult logoutResult = authController.logout(
                currentUser,
                new HttpRequest(
                        "POST",
                        "/auth/logout",
                        QueryParams.fromTarget("/auth/logout"),
                        Map.of("cookie", AuthService.REFRESH_COOKIE_NAME + "=" + refreshToken),
                        null,
                        null
                )
        );

        assertEquals("alice@example.com", ((com.oraskin.auth.domain.LoginResponse) loginResult.body()).user().username());
        assertNotNull(refreshResult.headers().get("Set-Cookie"));
        assertEquals("alice@example.com", me.username());
        assertEquals("logged_out", ((Map<?, ?>) logoutResult.body()).get("status"));
    }

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
        PublicKeysController publicKeysController = new PublicKeysController(publicKeyService);
        PrivateChatsController privateChatsController = new PrivateChatsController(privateChatService);
        MessagesController messagesController = new MessagesController(chatService);
        UserProfileService userProfileService = new UserProfileService(userStore, new AuthTestSupport.InMemoryAuthIdentityStore());
        UsersController usersController = new UsersController(chatService, userProfileService);

        ChatSummary directChat = chatsController.createChat(alice, "bob@example.com");
        List<ChatSummary> chats = chatsController.getChats(alice, QueryParams.fromTarget("/chats?keyId=alice-key"));
        publicKeysController.registerCurrentKeyPost(alice, new RegisterPrivateChatKeyRequest("alice-key-2", "alice-spki-2", "RSA-OAEP", "spki"));
        ChatSummary privateChatSummary = privateChatsController.createPrivateChat(alice, "bob@example.com", "alice-key");
        PrivateChatView privateChatView = privateChatsController.getPrivateChat(alice, privateChatSummary.chatId(), "alice-key");
        privateChatService.sendMessage(
                "alice-id",
                new com.oraskin.connection.SendPrivateMessageCommand(
                        privateChatSummary.chatId(),
                        new com.oraskin.chat.privatechat.value.EncryptedPrivateMessagePayload(
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
        List<com.oraskin.chat.privatechat.value.PrivateMessageView> privateMessages = privateChatsController.getMessages(alice, privateChatSummary.chatId(), "alice-key");
        chatService.sendMessage("alice-id", new com.oraskin.connection.SendMessageCommand(directChat.chatId(), "hello"));
        List<MessageRecord> messages = messagesController.getMessages(alice, directChat.chatId());
        messagesController.deleteMessage(alice, messages.getFirst().messageId());
        List<com.oraskin.user.data.domain.User> users = usersController.getUsers(alice);
        UserProfileView userProfileView = usersController.getUserProfile(alice, "bob-id");

        assertEquals("bob@example.com", directChat.username());
        assertTrue(chats.stream().anyMatch(chat -> chat.chatId() == directChat.chatId()));
        assertEquals("alice-key", privateChatView.currentUserKey().keyId());
        assertEquals("ciphertext", privateMessages.getFirst().encryptedMessage().ciphertext());
        assertEquals("hello", messages.getFirst().text());
        assertEquals(List.of(), messagesController.getMessages(alice, directChat.chatId()));
        assertEquals("bob@example.com", users.getFirst().username());
        assertEquals("bob@example.com", userProfileView.username());
    }

    @Test
    void userConnectionControllerOpensSessionAndReturnsConnectedBanner() {
        InMemorySessionRegistry sessionRegistry = new InMemorySessionRegistry();
        SessionService sessionService = new SessionService(sessionRegistry);
        UserConnectionController userConnectionController = new UserConnectionController(sessionService);
        AuthenticatedUser user = authenticatedUser("alice-id", "alice@example.com", "hash");
        Socket socket = new Socket();
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        String banner = userConnectionController.connect(user, socket, output);

        assertEquals("CONNECTED:alice@example.com", banner);
        assertNotNull(sessionService.findSession("alice-id"));
    }
}
