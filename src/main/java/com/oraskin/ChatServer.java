package com.oraskin;

import com.oraskin.auth.api.AuthController;
import com.oraskin.auth.config.AuthConfig;
import com.oraskin.auth.config.FrontendConfig;
import com.oraskin.auth.config.OidcProviderConfig;
import com.oraskin.auth.oidc.OidcAuthenticationService;
import com.oraskin.auth.persistence.PostgresAccessTokenStore;
import com.oraskin.auth.persistence.PostgresAuthIdentityStore;
import com.oraskin.auth.persistence.PostgresRefreshTokenStore;
import com.oraskin.auth.service.AccessTokenService;
import com.oraskin.auth.service.AuthService;
import com.oraskin.auth.service.IdentityProvisioningService;
import com.oraskin.auth.service.RefreshTokenService;
import com.oraskin.chat.repository.ChatRepository;
import com.oraskin.chat.service.ChatService;
import com.oraskin.chat.repository.DatabaseChatRepository;
import com.oraskin.common.auth.RequestAuthenticationService;
import com.oraskin.common.http.HttpRequest;
import com.oraskin.common.http.HttpRequestReader;
import com.oraskin.common.postgres.LiquibaseMigrationRunner;
import com.oraskin.common.postgres.PostgresConfig;
import com.oraskin.common.postgres.PostgresConnectionFactory;
import com.oraskin.user.data.persistence.DatabaseUserStore;
import com.oraskin.user.data.persistence.UserStore;
import com.oraskin.user.profile.UserProfileService;
import com.oraskin.user.session.persistence.InMemorySessionRegistry;
import com.oraskin.user.session.ClientSession;
import com.oraskin.common.mvc.ControllerResult;
import com.oraskin.common.mvc.ControllerResultWriter;
import com.oraskin.common.mvc.HttpApiRouter;
import com.oraskin.resource.ChatsController;
import com.oraskin.resource.MessagesController;
import com.oraskin.resource.UserConnectionController;
import com.oraskin.resource.UsersController;
import com.oraskin.common.http.HttpResponseWriter;
import com.oraskin.common.websocket.WebSocketSupport;
import com.oraskin.websocket.SessionWebSocketMessageSender;
import com.oraskin.websocket.WebSocketMessageSender;
import com.oraskin.websocket.WebSocketConnectionHandler;
import com.oraskin.websocket.message.ChatMessageService;
import com.oraskin.websocket.presence.PresenceService;
import com.oraskin.websocket.typing.TypingStateService;
import com.oraskin.user.session.service.SessionService;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.time.Clock;
import java.util.List;

public final class ChatServer {

    private final int port;
    private final HttpRequestReader httpRequestReader;
    private final HttpApiRouter httpApiRouter;
    private final WebSocketSupport webSocketSupport;
    private final WebSocketConnectionHandler webSocketConnectionHandler;
    private final ControllerResultWriter controllerResultWriter;
    private final SessionService sessionService;
    private final RequestAuthenticationService requestAuthenticationService;
    private final ChatMessageService chatMessageService;
    private final TypingStateService typingStateService;
    private final PresenceService presenceService;

    public ChatServer(int port) {
        this.port = port;

        Clock clock = Clock.systemUTC();

        PostgresConfig postgresConfig = PostgresConfig.fromEnvironment();
        PostgresConnectionFactory connectionFactory = new PostgresConnectionFactory(postgresConfig);
        LiquibaseMigrationRunner migrationRunner = new LiquibaseMigrationRunner(connectionFactory);
        migrationRunner.runMigrations();

        UserStore userStore = new DatabaseUserStore(connectionFactory, clock);
        ChatRepository chatRepository = new DatabaseChatRepository(connectionFactory, clock);

        InMemorySessionRegistry sessionRegistry = new InMemorySessionRegistry();

        FrontendConfig frontendConfig = FrontendConfig.fromEnvironment();

        OidcProviderConfig googleOidcProviderConfig = OidcProviderConfig.google(frontendConfig);
        AuthConfig authConfig = AuthConfig.fromEnvironment(frontendConfig);
        PostgresAuthIdentityStore authIdentityStore = new PostgresAuthIdentityStore(connectionFactory);
        AccessTokenService accessTokenService = new AccessTokenService(
                new PostgresAccessTokenStore(connectionFactory),
                authConfig,
                clock
        );
        RefreshTokenService refreshTokenService = new RefreshTokenService(
                new PostgresRefreshTokenStore(connectionFactory),
                accessTokenService,
                authConfig,
                clock
        );
        this.requestAuthenticationService = new RequestAuthenticationService(accessTokenService);
        AuthService authService = new AuthService(
                List.of(
                        new OidcAuthenticationService(
                                googleOidcProviderConfig,
                                clock
                        )
                ),
                new IdentityProvisioningService(
                        authIdentityStore,
                        userStore
                ),
                authIdentityStore,
                accessTokenService,
                refreshTokenService,
                authConfig,
                userStore,
                frontendConfig.secureCookies()
        );

        ChatService chatService = new ChatService(
                chatRepository,
                sessionRegistry,
                userStore
        );
        UserProfileService userProfileService = new UserProfileService(userStore, authIdentityStore);
        WebSocketMessageSender webSocketMessageSender = new SessionWebSocketMessageSender(sessionRegistry);

        this.chatMessageService = new ChatMessageService(chatService, webSocketMessageSender);
        this.typingStateService = new TypingStateService(chatService, webSocketMessageSender);
        this.presenceService = new PresenceService(chatService, webSocketMessageSender);
        this.sessionService = new SessionService(sessionRegistry);
        ChatsController chatsController = new ChatsController(chatService);
        UserConnectionController userConnectionController = new UserConnectionController(sessionService);
        this.httpRequestReader = new HttpRequestReader();
        HttpResponseWriter httpResponseWriter = new HttpResponseWriter(frontendConfig);
        this.webSocketSupport = new WebSocketSupport();
        this.controllerResultWriter = new ControllerResultWriter(httpResponseWriter);
        this.webSocketConnectionHandler = new WebSocketConnectionHandler(
                webSocketSupport,
                presenceService,
                chatMessageService,
                typingStateService
        );
        this.httpApiRouter = new HttpApiRouter(
                List.of(
                        new AuthController(authService),
                        userConnectionController,
                        chatsController,
                        new MessagesController(chatService),
                        new UsersController(chatService, userProfileService)
                ),
                httpResponseWriter,
                requestAuthenticationService
        );
    }

    public void start() throws IOException {
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            System.out.println("Chat server is listening on port: " + port);
            while (true) {
                Socket socket = serverSocket.accept();
                System.out.println("Accepted: " + socket.getRemoteSocketAddress());
                Thread.ofVirtual().start(() -> handleConnection(socket));
            }
        }
    }

    private void handleConnection(Socket socket) {
        try (socket) {
            InputStream input = socket.getInputStream();
            OutputStream output = socket.getOutputStream();

            HttpRequest request = httpRequestReader.read(input);
            if (webSocketSupport.isUpgrade(request)) {
                handleWebSocketConnection(request, socket, input, output);
                return;
            }
            httpApiRouter.route(request, output);
        } catch (SocketException | EOFException ignored) {
            // Client disconnected while request was being read.
        } catch (IOException e) {
            System.err.println("Connection failed: " + e.getMessage());
        }
    }

    private void handleWebSocketConnection(HttpRequest request, Socket socket, InputStream input, OutputStream output) throws IOException {
        try {
            webSocketSupport.validateHandshake(request);
            ControllerResult connectResult = httpApiRouter.invoke(request, socket, output);
            if (connectResult == null) {
                throw new IOException("WebSocket connect route not found.");
            }
            HttpRequest authenticatedRequest = requestAuthenticationService.authenticateRequired(request);
            ClientSession session = sessionService.findSession(authenticatedRequest.authenticatedUser().userId());
            if (session == null) {
                throw new IOException("WebSocket session was not opened by connect controller.");
            }
            try {
                webSocketSupport.writeHandshakeResponse(
                        output,
                        request.header("sec-websocket-key"),
                        requestAuthenticationService.resolveWebSocketSubprotocol(request)
                );
                controllerResultWriter.writeWebSocket(session, connectResult);
                webSocketConnectionHandler.handle(session, input);
            } finally {
                typingStateService.clearUser(session.userId());
                sessionService.closeSession(session.userId());
                System.out.println("Disconnected user: " + session.userId());
            }
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException("WebSocket connection failed: " + e.getMessage(), e);
        }
    }

}
