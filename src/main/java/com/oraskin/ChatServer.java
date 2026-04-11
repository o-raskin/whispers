package com.oraskin;

import com.oraskin.chat.service.ChatService;
import com.oraskin.chat.repository.InMemoryChatStore;
import com.oraskin.common.http.HttpRequest;
import com.oraskin.common.http.HttpRequestReader;
import com.oraskin.user.data.persistence.InMemoryUserStore;
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

    public ChatServer(int port) {
        this.port = port;
        InMemorySessionRegistry sessionRegistry = new InMemorySessionRegistry();
        InMemoryUserStore userStore = new InMemoryUserStore(Clock.systemUTC());
        ChatService chatService = new ChatService(
                new InMemoryChatStore(Clock.systemUTC()),
                sessionRegistry,
                userStore);
        WebSocketMessageSender webSocketMessageSender = new SessionWebSocketMessageSender(sessionRegistry);
        this.sessionService = new SessionService(sessionRegistry, userStore);
        ChatsController chatsController = new ChatsController(chatService);
        UserConnectionController userConnectionController = new UserConnectionController(sessionService);
        this.httpRequestReader = new HttpRequestReader();
        HttpResponseWriter httpResponseWriter = new HttpResponseWriter();
        this.webSocketSupport = new WebSocketSupport();
        this.controllerResultWriter = new ControllerResultWriter(httpResponseWriter);
        this.webSocketConnectionHandler = new WebSocketConnectionHandler(
                chatService,
                webSocketMessageSender,
                webSocketSupport
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
