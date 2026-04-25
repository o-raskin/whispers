package com.oraskin;

import com.oraskin.auth.AuthTestSupport;
import com.oraskin.auth.config.FrontendConfig;
import com.oraskin.auth.service.AccessTokenService;
import com.oraskin.common.auth.RequestAuthenticationService;
import com.oraskin.common.http.HttpRequest;
import com.oraskin.common.http.HttpResponseWriter;
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
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ChatServerTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-04-21T10:15:30Z"), ZoneOffset.UTC);

    @Test
    void writeWebSocketUpgradeFailureReturnsGenericClientResponseAndSanitizedLog() throws Exception {
        ChatServer server = allocateChatServer();
        setField(server, "httpResponseWriter", new HttpResponseWriter(new FrontendConfig(URI.create("http://localhost:5173"))));
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ByteArrayOutputStream errorOutput = new ByteArrayOutputStream();
        PrintStream originalError = System.err;

        try {
            System.setErr(new PrintStream(errorOutput, true, StandardCharsets.UTF_8));
            invokePrivate(server, "writeWebSocketUpgradeFailure", new Class<?>[]{OutputStream.class, Exception.class}, output, new IllegalStateException("secret"));
        } finally {
            System.setErr(originalError);
        }

        assertThat(output.toString(StandardCharsets.UTF_8))
                .startsWith("HTTP/1.1 500 Internal Server Error")
                .contains("Internal server error")
                .doesNotContain("secret");
        assertThat(errorOutput.toString(StandardCharsets.UTF_8))
                .contains("WebSocket upgrade failed: IllegalStateException")
                .doesNotContain("secret");
    }

    @Test
    void handleWebSocketConnectionClosesSessionWhenHandshakeWriteFails() throws Exception {
        ChatServer server = allocateChatServer();
        HttpResponseWriter httpResponseWriter = new HttpResponseWriter(new FrontendConfig(URI.create("http://localhost:5173")));
        InMemorySessionRegistry sessionRegistry = new InMemorySessionRegistry();
        SessionService sessionService = new SessionService(sessionRegistry);
        AuthTestSupport.RecordingAccessTokenStore accessTokenStore = new AuthTestSupport.RecordingAccessTokenStore();
        AccessTokenService accessTokenService = new AccessTokenService(accessTokenStore, AuthTestSupport.authConfig(), CLOCK);
        RequestAuthenticationService requestAuthenticationService = new RequestAuthenticationService(accessTokenService);
        String rawToken = "ws-token";
        String tokenHash = accessTokenService.hash(rawToken);
        accessTokenStore.activate(tokenHash, AuthTestSupport.authenticatedUser("alice-id", "alice@example.com", tokenHash));

        setField(server, "httpResponseWriter", httpResponseWriter);
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
        FailOnceOutputStream output = new FailOnceOutputStream();

        invokePrivate(
                server,
                "handleWebSocketConnection",
                new Class<?>[]{HttpRequest.class, Socket.class, java.io.InputStream.class, OutputStream.class},
                request,
                new Socket(),
                new ByteArrayInputStream(new byte[0]),
                output
        );

        assertThat(sessionRegistry.isConnected("alice-id")).isFalse();
        assertThat(output.toString()).contains("Bad Request");
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
