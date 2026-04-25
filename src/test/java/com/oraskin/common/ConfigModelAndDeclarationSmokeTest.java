package com.oraskin.common;

import com.oraskin.auth.config.AuthConfig;
import com.oraskin.auth.config.FrontendConfig;
import com.oraskin.auth.config.OidcProviderConfig;
import com.oraskin.auth.domain.AccessToken;
import com.oraskin.auth.domain.AuthProvider;
import com.oraskin.auth.domain.AuthUserProfile;
import com.oraskin.auth.domain.ExternalUserIdentity;
import com.oraskin.auth.domain.LoginResponse;
import com.oraskin.auth.domain.OAuthLoginRequest;
import com.oraskin.auth.domain.RefreshToken;
import com.oraskin.auth.oidc.OidcDiscoveryDocument;
import com.oraskin.auth.oidc.OidcIdTokenClaims;
import com.oraskin.auth.oidc.OidcJwk;
import com.oraskin.auth.oidc.OidcJwkSet;
import com.oraskin.auth.oidc.OidcTokenResponse;
import com.oraskin.auth.persistence.AccessTokenStore;
import com.oraskin.auth.persistence.AuthIdentityStore;
import com.oraskin.auth.persistence.RefreshTokenStore;
import com.oraskin.auth.service.OAuthProviderAuthenticator;
import com.oraskin.chat.key.persistence.PrivateChatKeyStore;
import com.oraskin.chat.key.persistence.entity.PrivateChatKeyRecord;
import com.oraskin.chat.key.value.PrivateChatKeyStatus;
import com.oraskin.chat.key.value.PrivateChatKeyView;
import com.oraskin.chat.key.value.RegisterPrivateChatKeyRequest;
import com.oraskin.chat.privatechat.value.EncryptedPrivateMessagePayload;
import com.oraskin.chat.privatechat.value.PrivateChatView;
import com.oraskin.chat.privatechat.value.PrivateMessageView;
import com.oraskin.chat.repository.ChatRepository;
import com.oraskin.chat.repository.entity.ChatRecord;
import com.oraskin.chat.repository.entity.MessageRecord;
import com.oraskin.chat.repository.entity.PrivateMessageRecord;
import com.oraskin.chat.service.ChatException;
import com.oraskin.chat.value.ChatSummary;
import com.oraskin.chat.value.ChatType;
import com.oraskin.common.auth.AuthenticatedUser;
import com.oraskin.common.http.ErrorResponse;
import com.oraskin.common.http.HttpStatus;
import com.oraskin.common.json.JsonCodec;
import com.oraskin.common.mvc.ControllerMethod;
import com.oraskin.common.mvc.ControllerResult;
import com.oraskin.common.mvc.annotation.PathVariable;
import com.oraskin.common.mvc.annotation.PublicEndpoint;
import com.oraskin.common.mvc.annotation.RequestBody;
import com.oraskin.common.mvc.annotation.RequestMapping;
import com.oraskin.common.mvc.annotation.RequestParam;
import com.oraskin.common.mvc.annotation.RestController;
import com.oraskin.common.postgres.PostgresConfig;
import com.oraskin.common.websocket.FrameType;
import com.oraskin.common.websocket.HeaderUtil;
import com.oraskin.common.websocket.WebSocketFrame;
import com.oraskin.connection.MessageDelivery;
import com.oraskin.connection.MessageDeleteEvent;
import com.oraskin.connection.PingResponse;
import com.oraskin.connection.PresenceEvent;
import com.oraskin.connection.PrivateMessageDelivery;
import com.oraskin.connection.SendMessageCommand;
import com.oraskin.connection.SendPrivateMessageCommand;
import com.oraskin.user.data.domain.User;
import com.oraskin.user.data.mapper.UserMapper;
import com.oraskin.user.data.persistence.UserStore;
import com.oraskin.user.data.persistence.entity.UserData;
import com.oraskin.user.profile.UserProfileView;
import com.oraskin.user.session.persistence.SessionRegistry;
import com.oraskin.websocket.WebSocketCommand;
import com.oraskin.websocket.WebSocketCommandType;
import com.oraskin.websocket.WebSocketMessageSender;
import com.oraskin.websocket.typing.TypingEvent;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigModelAndDeclarationSmokeTest {

    @Test
    void configRecordsExposeTheirStateAndFrontendHelpers() throws Exception {
        FrontendConfig frontendConfig = new FrontendConfig(URI.create("https://client.example/app/"));
        AuthConfig authConfig = new AuthConfig(Duration.ofMinutes(10), Duration.ofDays(30), "whispers", URI.create("https://client.example/logout"));
        OidcProviderConfig oidcProviderConfig = new OidcProviderConfig(
                "google",
                URI.create("https://accounts.google.com"),
                "client-id",
                "client-secret",
                URI.create("https://client.example/auth/callback/google"),
                List.of("openid", "email", "profile")
        );
        PostgresConfig postgresConfig = new PostgresConfig("jdbc:postgresql://localhost:5432/whispers", "whispers", "whispers");

        assertEquals("https://client.example/app", frontendConfig.origin());
        assertTrue(frontendConfig.secureCookies());
        assertEquals(Duration.ofMinutes(10), authConfig.accessTokenTtl());
        assertEquals("client-id", oidcProviderConfig.clientId());
        assertEquals("jdbc:postgresql://localhost:5432/whispers", postgresConfig.jdbcUrl());

        Constructor<AuthProvider> constructor = AuthProvider.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        AuthProvider authProvider = constructor.newInstance();
        assertNotNull(authProvider);
        assertEquals("google", AuthProvider.GOOGLE);
    }

    @Test
    void domainRecordsEnumsAndMapperExposeExpectedValues() throws Exception {
        LocalDateTime pingTime = LocalDateTime.of(2026, 4, 21, 12, 30);
        Instant expiry = Instant.parse("2026-04-22T10:15:30Z");
        AuthenticatedUser authenticatedUser = new AuthenticatedUser("alice-id", "alice@example.com", "google", "subject", "alice@example.com", "Alice", "Alice", "Example", "https://example.com/a.png", "hash");
        AuthUserProfile authUserProfile = new AuthUserProfile("alice-id", "alice@example.com", "alice@example.com", "Alice", "Alice", "Example", "https://example.com/a.png", "google");
        AccessToken accessToken = new AccessToken("token", "Bearer", 600, expiry);
        RefreshToken refreshToken = new RefreshToken("refresh", expiry);
        ExternalUserIdentity externalUserIdentity = new ExternalUserIdentity("google", "subject", "alice@example.com", "alice@example.com", "Alice", "Alice", "Example", "https://example.com/a.png");
        LoginResponse loginResponse = new LoginResponse("token", "Bearer", 600, authUserProfile);
        OAuthLoginRequest loginRequest = new OAuthLoginRequest("code", "https://client.example/auth/callback/google", "verifier", "nonce");
        RegisterPrivateChatKeyRequest registerRequest = new RegisterPrivateChatKeyRequest("key-1", "spki", "RSA-OAEP", "spki");
        PrivateChatKeyRecord keyRecord = new PrivateChatKeyRecord("alice-id", "key-1", "spki", "RSA-OAEP", "spki", PrivateChatKeyStatus.ACTIVE, "2026-04-21T10:00:00Z", "2026-04-21T10:01:00Z");
        PrivateChatKeyView keyView = new PrivateChatKeyView("key-1", "spki", "RSA-OAEP", "spki", PrivateChatKeyStatus.ACTIVE, "2026-04-21T10:00:00Z", "2026-04-21T10:01:00Z");
        EncryptedPrivateMessagePayload encryptedMessage = new EncryptedPrivateMessagePayload("v1", "AES-GCM", "RSA-OAEP", "ciphertext", "nonce", "alice-key", "sender-envelope", "bob-key", "recipient-envelope");
        PrivateChatView privateChatView = new PrivateChatView(5L, "bob@example.com", ChatType.PRIVATE, keyView, keyView);
        PrivateMessageView privateMessageView = new PrivateMessageView(5L, "alice@example.com", ChatType.PRIVATE, encryptedMessage, "2026-04-21T10:00:00Z");
        ChatSummary chatSummary = new ChatSummary(7L, "bob@example.com", ChatType.DIRECT);
        ChatRecord chatRecord = new ChatRecord(7L, "alice-id", "bob-id", ChatType.PRIVATE, "alice-key", "bob-key");
        MessageRecord messageRecord = new MessageRecord(11L, 7L, "alice@example.com", "hello", "2026-04-21T10:00:00Z");
        PrivateMessageRecord privateMessageRecord = new PrivateMessageRecord(7L, "alice-id", encryptedMessage, "2026-04-21T10:00:00Z");
        ChatException chatException = new ChatException(HttpStatus.CONFLICT, "already connected");
        ErrorResponse errorResponse = new ErrorResponse("boom");
        PresenceEvent presenceEvent = new PresenceEvent("presence", "alice@example.com", pingTime);
        PingResponse pingResponse = new PingResponse("ok");
        SendMessageCommand sendMessageCommand = new SendMessageCommand(7L, "hello");
        SendPrivateMessageCommand sendPrivateMessageCommand = new SendPrivateMessageCommand(7L, encryptedMessage);
        MessageDelivery messageDelivery = new MessageDelivery("bob-id", messageRecord);
        MessageDeleteEvent messageDeleteEvent = new MessageDeleteEvent("MESSAGE_DELETE", 7L, 11L);
        PrivateMessageDelivery privateMessageDelivery = new PrivateMessageDelivery("bob-id", privateMessageView);
        User user = new User("alice-id", "alice@example.com", "Alice", "Example", pingTime);
        UserData userData = new UserData("alice-id", "alice@example.com", "Alice", "Example", pingTime);
        UserProfileView userProfileView = new UserProfileView("alice-id", "alice@example.com", "Alice", "Example", "https://example.com/a.png", "google");
        WebSocketCommand webSocketCommand = new WebSocketCommand(WebSocketCommandType.PRIVATE_MESSAGE, 7L, null, encryptedMessage);
        TypingEvent typingEvent = new TypingEvent("typing:start", 7L, "alice@example.com");
        UserMapper userMapper = new UserMapper();

        assertEquals("alice-id", authenticatedUser.userId());
        assertEquals("token", accessToken.token());
        assertEquals(expiry, refreshToken.expiresAt());
        assertEquals("alice@example.com", externalUserIdentity.email());
        assertEquals(authUserProfile, loginResponse.user());
        assertEquals("code", loginRequest.code());
        assertEquals("key-1", registerRequest.keyId());
        assertEquals("spki", keyRecord.publicKey());
        assertEquals(PrivateChatKeyStatus.ACTIVE, keyView.status());
        assertEquals("ciphertext", encryptedMessage.ciphertext());
        assertEquals(keyView, privateChatView.currentUserKey());
        assertEquals(ChatType.PRIVATE, privateMessageView.chatType());
        assertEquals(ChatType.DIRECT, chatSummary.type());
        assertTrue(chatRecord.hasParticipant("alice-id"));
        assertEquals("bob-id", chatRecord.otherUserId("alice-id"));
        assertEquals("alice-key", chatRecord.keyIdForUser("alice-id"));
        assertEquals("bob-key", chatRecord.otherUserKeyId("alice-id"));
        assertEquals(11L, messageRecord.messageId());
        assertEquals("hello", messageRecord.text());
        assertEquals(encryptedMessage, privateMessageRecord.encryptedMessage());
        assertEquals(HttpStatus.CONFLICT, chatException.status());
        assertEquals("boom", errorResponse.error());
        assertEquals(pingTime, presenceEvent.lastPingTime());
        assertEquals("ok", pingResponse.status());
        assertEquals(7L, sendMessageCommand.chatId());
        assertEquals(encryptedMessage, sendPrivateMessageCommand.encryptedMessage());
        assertEquals(messageRecord, messageDelivery.message());
        assertEquals("MESSAGE_DELETE", messageDeleteEvent.type());
        assertEquals(privateMessageView, privateMessageDelivery.message());
        assertEquals("alice@example.com", user.username());
        assertEquals("alice-id", userData.getUserId());
        assertEquals(new UserData("alice-id", "other@example.com", null, null, null), userData);
        assertEquals(new UserData("alice-id", "other@example.com", null, null, null).hashCode(), userData.hashCode());
        assertEquals("alice@example.com", userMapper.toDomain(userData).username());
        assertEquals("google", userProfileView.provider());
        assertEquals(WebSocketCommandType.PRIVATE_MESSAGE, webSocketCommand.type());
        assertEquals("typing:start", typingEvent.type());
        assertEquals(200, HttpStatus.OK.code());
        assertEquals("OK", HttpStatus.OK.reasonPhrase());
        assertEquals(ChatType.DIRECT, ChatType.valueOf("DIRECT"));
        assertEquals(WebSocketCommandType.MESSAGE, WebSocketCommandType.valueOf("MESSAGE"));
        assertEquals(PrivateChatKeyStatus.REVOKED, PrivateChatKeyStatus.valueOf("REVOKED"));
    }

    @Test
    void oidcJsonRecordsAndCodecRoundTripCorrectly() throws Exception {
        OidcDiscoveryDocument discoveryDocument = JsonCodec.read(
                """
                {"issuer":"https://accounts.google.com","authorization_endpoint":"https://accounts.google.com/o/oauth2/v2/auth","token_endpoint":"https://oauth2.googleapis.com/token","jwks_uri":"https://www.googleapis.com/oauth2/v3/certs","ignored":"value"}
                """,
                OidcDiscoveryDocument.class
        );
        OidcIdTokenClaims claims = JsonCodec.read(
                """
                {"iss":"https://accounts.google.com","sub":"subject","aud":"client-id","exp":2000000000,"iat":1000000000,"nonce":"nonce","email":"alice@example.com","given_name":"Alice","family_name":"Example","picture":"https://example.com/a.png","ignored":"value"}
                """,
                OidcIdTokenClaims.class
        );
        OidcJwk jwk = new OidcJwk("kid", "RSA", "RS256", "sig", "n", "e");
        OidcJwkSet jwkSet = JsonCodec.read("{\"keys\":[{\"kid\":\"kid\",\"kty\":\"RSA\",\"alg\":\"RS256\",\"use\":\"sig\",\"n\":\"n\",\"e\":\"e\"}]}", OidcJwkSet.class);
        OidcTokenResponse tokenResponse = JsonCodec.read(
                """
                {"access_token":"access","id_token":"header.payload.signature","expires_in":3600,"token_type":"Bearer","scope":"openid email"}
                """,
                OidcTokenResponse.class
        );
        String serializedPresenceEvent = JsonCodec.write(new PresenceEvent("presence", "alice@example.com", LocalDateTime.of(2026, 4, 21, 12, 0)));

        assertEquals("https://accounts.google.com", discoveryDocument.issuer());
        assertEquals("Alice", claims.givenName());
        assertEquals("kid", jwk.kid());
        assertEquals("kid", jwkSet.keys().getFirst().kid());
        assertEquals("access", tokenResponse.accessToken());
        assertTrue(serializedPresenceEvent.contains("\"lastPingTime\""));
    }

    @Test
    void websocketHelpersAndMvcDeclarationsExposeExpectedMetadata() throws Exception {
        assertEquals(FrameType.TEXT, FrameType.fromCode(0x1));
        assertTrue(FrameType.PING.isControl());
        assertFalse(FrameType.BINARY.isControl());
        assertArrayEquals(new byte[]{(byte) 0x81, 0x05}, HeaderUtil.buildHeader(FrameType.TEXT, 5));
        assertArrayEquals(new byte[]{(byte) 0x81, 0x7E, 0x01, 0x00}, HeaderUtil.buildHeader(FrameType.TEXT, 256));
        byte[] largeHeader = HeaderUtil.buildHeader(FrameType.BINARY, 70_000);
        assertEquals((byte) 0x82, largeHeader[0]);
        assertEquals(10, largeHeader.length);

        WebSocketFrame webSocketFrame = new WebSocketFrame(FrameType.TEXT, "hello".getBytes());
        assertEquals(FrameType.TEXT, webSocketFrame.frameType());
        assertEquals("hello", new String(webSocketFrame.payload()));

        Method method = AnnotatedController.class.getDeclaredMethod("endpoint", String.class, String.class, Body.class);
        ControllerMethod controllerMethod = new ControllerMethod(new AnnotatedController(), method, "POST", "/annotated/{id}", Map.of("id", "42"), true);
        ControllerResult controllerResult = ControllerResult.created(Map.of("status", "created"));

        assertEquals("/annotated", AnnotatedController.class.getAnnotation(RestController.class).value());
        assertEquals("POST", method.getAnnotation(RequestMapping.class).method());
        assertTrue(method.isAnnotationPresent(PublicEndpoint.class));
        assertEquals("id", method.getParameters()[0].getAnnotation(PathVariable.class).value());
        assertEquals("name", method.getParameters()[1].getAnnotation(RequestParam.class).value());
        assertNotNull(method.getParameters()[2].getAnnotation(RequestBody.class));
        assertEquals("POST", controllerMethod.requestMethod());
        assertEquals(HttpStatus.CREATED, controllerResult.status());
    }

    @Test
    void contractInterfacesExistAsExpectedRepositoryBoundaries() {
        assertTrue(AccessTokenStore.class.isInterface());
        assertTrue(AuthIdentityStore.class.isInterface());
        assertTrue(RefreshTokenStore.class.isInterface());
        assertTrue(OAuthProviderAuthenticator.class.isInterface());
        assertTrue(PrivateChatKeyStore.class.isInterface());
        assertTrue(ChatRepository.class.isInterface());
        assertTrue(UserStore.class.isInterface());
        assertTrue(SessionRegistry.class.isInterface());
        assertTrue(WebSocketMessageSender.class.isInterface());
    }

    @RestController("/annotated")
    private static final class AnnotatedController {

        @PublicEndpoint
        @RequestMapping(method = "POST", value = "/{id}")
        public String endpoint(
                @PathVariable("id") String id,
                @RequestParam("name") String name,
                @RequestBody Body body
        ) {
            return id + ":" + name + ":" + body.value();
        }
    }

    private record Body(String value) {
    }
}
