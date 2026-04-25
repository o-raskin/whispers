package com.oraskin.auth.oidc;

import com.oraskin.App;
import com.oraskin.ChatServer;
import com.oraskin.auth.AuthTestSupport;
import com.oraskin.auth.config.FrontendConfig;
import com.oraskin.auth.config.OidcProviderConfig;
import com.oraskin.auth.service.AccessTokenService;
import com.oraskin.chat.service.ChatException;
import com.oraskin.common.auth.RequestAuthenticationService;
import com.oraskin.common.http.HttpRequest;
import com.oraskin.common.http.HttpResponseWriter;
import com.oraskin.common.http.HttpStatus;
import com.oraskin.common.mvc.ControllerResultWriter;
import com.oraskin.common.mvc.HttpApiRouter;
import com.oraskin.common.websocket.WebSocketSupport;
import com.oraskin.resource.UserConnectionController;
import com.oraskin.user.session.persistence.InMemorySessionRegistry;
import com.oraskin.user.session.service.SessionService;
import com.oraskin.websocket.typing.TypingStateService;
import org.junit.jupiter.api.Test;
import sun.misc.Unsafe;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.Socket;
import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OidcAndBootstrapTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-04-21T10:15:30Z"), ZoneOffset.UTC);

    @Test
    void oidcAuthenticationServiceRejectsInvalidRequestsBeforeNetworkAndReportsProvider() {
        OidcProviderConfig providerConfig = providerConfig();
        OidcAuthenticationService oidcAuthenticationService = new OidcAuthenticationService(providerConfig, CLOCK);

        assertThat(oidcAuthenticationService.provider()).isEqualTo("google");

        assertValidationFailure(oidcAuthenticationService, new com.oraskin.auth.domain.OAuthLoginRequest(null, providerConfig.redirectUri().toString(), "verifier", "nonce"), "Missing authorization code");
        assertValidationFailure(oidcAuthenticationService, new com.oraskin.auth.domain.OAuthLoginRequest("code", "", "verifier", "nonce"), "Missing redirectUri");
        assertValidationFailure(oidcAuthenticationService, new com.oraskin.auth.domain.OAuthLoginRequest("code", "https://other.example/callback", "verifier", "nonce"), "Unexpected redirectUri");
        assertValidationFailure(oidcAuthenticationService, new com.oraskin.auth.domain.OAuthLoginRequest("code", providerConfig.redirectUri().toString(), "", "nonce"), "Missing codeVerifier");
        assertValidationFailure(oidcAuthenticationService, new com.oraskin.auth.domain.OAuthLoginRequest("code", providerConfig.redirectUri().toString(), "verifier", ""), "Missing nonce");
    }

    @Test
    void oidcDiscoveryServiceReturnsInjectedCachedDocumentWithoutNetwork() throws Exception {
        OidcDiscoveryService oidcDiscoveryService = new OidcDiscoveryService(providerConfig());
        OidcDiscoveryDocument discoveryDocument = new OidcDiscoveryDocument(
                "https://accounts.google.com",
                "https://accounts.google.com/o/oauth2/v2/auth",
                "https://oauth2.googleapis.com/token",
                "https://www.googleapis.com/oauth2/v3/certs"
        );
        Field cachedDocumentField = OidcDiscoveryService.class.getDeclaredField("cachedDocument");
        cachedDocumentField.setAccessible(true);
        cachedDocumentField.set(oidcDiscoveryService, discoveryDocument);

        assertThat(oidcDiscoveryService.getDiscoveryDocument()).isSameAs(discoveryDocument);
    }

    @Test
    void appRejectsInvalidPortArguments() {
        assertThatThrownBy(() -> App.main(new String[]{"invalid"})).isInstanceOf(NumberFormatException.class);
    }

    @Test
    void chatServerNormalizesUpgradeFailuresAndCleansUpSessionsOpenedBeforeHandshake() throws Exception {
        ChatServer server = allocateChatServer();
        HttpResponseWriter httpResponseWriter = new HttpResponseWriter(new FrontendConfig(URI.create("http://localhost:5173")));
        setField(server, "httpResponseWriter", httpResponseWriter);
        ByteArrayOutputStream errorOutput = new ByteArrayOutputStream();
        ByteArrayOutputStream stderr = new ByteArrayOutputStream();
        PrintStream originalError = System.err;
        try {
            System.setErr(new PrintStream(stderr, true, java.nio.charset.StandardCharsets.UTF_8));
            invokePrivate(server, "writeWebSocketUpgradeFailure", new Class<?>[]{OutputStream.class, Exception.class}, errorOutput, new IllegalStateException("secret"));
        } finally {
            System.setErr(originalError);
        }
        String errorResponse = errorOutput.toString();

        assertThat(errorResponse).startsWith("HTTP/1.1 500 Internal Server Error");
        assertThat(errorResponse).contains("Internal server error");
        assertThat(stderr.toString(java.nio.charset.StandardCharsets.UTF_8))
                .contains("WebSocket upgrade failed: IllegalStateException")
                .doesNotContain("secret");

        InMemorySessionRegistry sessionRegistry = new InMemorySessionRegistry();
        SessionService sessionService = new SessionService(sessionRegistry);
        AuthTestSupport.RecordingAccessTokenStore accessTokenStore = new AuthTestSupport.RecordingAccessTokenStore();
        AccessTokenService accessTokenService = new AccessTokenService(accessTokenStore, AuthTestSupport.authConfig(), CLOCK);
        RequestAuthenticationService requestAuthenticationService = new RequestAuthenticationService(accessTokenService);
        String rawToken = "ws-token";
        String tokenHash = accessTokenService.hash(rawToken);
        accessTokenStore.activate(tokenHash, AuthTestSupport.authenticatedUser("alice-id", "alice@example.com", tokenHash));

        setField(server, "webSocketSupport", new WebSocketSupport());
        setField(server, "sessionService", sessionService);
        setField(server, "requestAuthenticationService", requestAuthenticationService);
        setField(server, "controllerResultWriter", new ControllerResultWriter(httpResponseWriter));
        setField(server, "typingStateService", new TypingStateService(null, null));
        setField(server, "httpApiRouter", new HttpApiRouter(
                List.of(new UserConnectionController(sessionService)),
                httpResponseWriter,
                requestAuthenticationService
        ));

        HttpRequest request = new HttpRequest(
                "GET",
                "/ws/user",
                com.oraskin.common.http.QueryParams.fromTarget("/ws/user"),
                Map.of(
                        "upgrade", "websocket",
                        "connection", "Upgrade",
                        "sec-websocket-key", "dGhlIHNhbXBsZSBub25jZQ==",
                        "authorization", "Bearer " + rawToken
                ),
                null,
                null
        );
        FailOnceOutputStream failOnceOutputStream = new FailOnceOutputStream();

        invokePrivate(
                server,
                "handleWebSocketConnection",
                new Class<?>[]{HttpRequest.class, Socket.class, java.io.InputStream.class, OutputStream.class},
                request,
                new Socket(),
                new ByteArrayInputStream(new byte[0]),
                failOnceOutputStream
        );

        assertThat(sessionRegistry.isConnected("alice-id")).isFalse();
        assertThat(failOnceOutputStream.toString()).contains("Bad Request");
    }

    private void assertValidationFailure(OidcAuthenticationService service, com.oraskin.auth.domain.OAuthLoginRequest request, String expectedMessage) {
        assertThatThrownBy(() -> service.authenticate(request))
                .isInstanceOf(ChatException.class)
                .satisfies(throwable -> {
                    ChatException exception = (ChatException) throwable;
                    assertThat(exception.status()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(exception).hasMessage(expectedMessage);
                });
    }

    private OidcProviderConfig providerConfig() {
        return new OidcProviderConfig(
                "google",
                URI.create("https://accounts.google.com"),
                "client-id",
                "client-secret",
                URI.create("http://localhost:5173/auth/callback/google"),
                List.of("openid", "email", "profile")
        );
    }

    private ChatServer allocateChatServer() throws Exception {
        return (ChatServer) unsafe().allocateInstance(ChatServer.class);
    }

    private void invokePrivate(Object target, String methodName, Class<?>[] parameterTypes, Object... args) throws Exception {
        Method method = target.getClass().getDeclaredMethod(methodName, parameterTypes);
        method.setAccessible(true);
        method.invoke(target, args);
    }

    private void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = ChatServer.class.getDeclaredField(fieldName);
        long offset = unsafe().objectFieldOffset(field);
        unsafe().putObject(target, offset, value);
    }

    private Unsafe unsafe() throws Exception {
        Field field = Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        return (Unsafe) field.get(null);
    }

    private static final class FailOnceOutputStream extends OutputStream {

        private final ByteArrayOutputStream delegate = new ByteArrayOutputStream();

        private boolean failed;

        @Override
        public synchronized void write(byte[] b, int off, int len) throws IOException {
            if (!failed) {
                failed = true;
                throw new IOException("handshake write failed");
            }
            delegate.write(b, off, len);
        }

        @Override
        public synchronized void write(int b) throws IOException {
            if (!failed) {
                failed = true;
                throw new IOException("handshake write failed");
            }
            delegate.write(b);
        }

        @Override
        public String toString() {
            return delegate.toString();
        }
    }
}
