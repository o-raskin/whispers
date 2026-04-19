package com.oraskin;

import com.oraskin.chat.service.ChatService;
import com.oraskin.chat.repository.PostgresChatRepository;
import com.oraskin.common.http.HttpRequest;
import com.oraskin.common.http.HttpRequestReader;
import com.oraskin.common.postgres.LiquibaseMigrationRunner;
import com.oraskin.common.postgres.PostgresConfig;
import com.oraskin.common.postgres.PostgresConnectionFactory;
import com.oraskin.user.data.persistence.PostgresUserStore;
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
    private final ChatMessageService chatMessageService;
    private final TypingStateService typingStateService;
    private final PresenceService presenceService;

    public ChatServer(int port) {
        this.port = port;
        Clock clock = Clock.systemUTC();
        InMemorySessionRegistry sessionRegistry = new InMemorySessionRegistry();
        PostgresConfig postgresConfig = PostgresConfig.fromEnvironment();
        PostgresConnectionFactory connectionFactory = new PostgresConnectionFactory(postgresConfig);
        LiquibaseMigrationRunner migrationRunner = new LiquibaseMigrationRunner(connectionFactory);
        migrationRunner.runMigrations();

        PostgresUserStore userStore = new PostgresUserStore(connectionFactory, clock);
        ChatService chatService = new ChatService(
                new PostgresChatRepository(connectionFactory, clock),
                sessionRegistry,
                userStore);
        WebSocketMessageSender webSocketMessageSender = new SessionWebSocketMessageSender(sessionRegistry);
        this.chatMessageService = new ChatMessageService(chatService, webSocketMessageSender);
        this.typingStateService = new TypingStateService(chatService, webSocketMessageSender);
        this.presenceService = new PresenceService(chatService, webSocketMessageSender);
        this.sessionService = new SessionService(sessionRegistry, userStore);
        ChatsController chatsController = new ChatsController(chatService);
        UserConnectionController userConnectionController = new UserConnectionController(sessionService);
        this.httpRequestReader = new HttpRequestReader();
        HttpResponseWriter httpResponseWriter = new HttpResponseWriter();
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
                        userConnectionController,
                        chatsController,
                        new MessagesController(chatService),
                        new UsersController(chatService)
                ),
                httpResponseWriter
        );
    }

    public void start() throws IOException {
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            System.out.println("Chat server is listening on ws://localhost:" + port + "/ws/user?userId=<user>");
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
            ClientSession session = sessionService.findSession(request.params().required("userId"));
            if (session == null) {
                throw new IOException("WebSocket session was not opened by connect controller.");
            }
            try {
                webSocketSupport.writeHandshakeResponse(output, request.header("sec-websocket-key"));
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
