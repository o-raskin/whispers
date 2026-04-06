package com.oraskin;

import com.oraskin.chat.ChatException;
import com.oraskin.chat.ChatService;
import com.oraskin.chat.ChatSummary;
import com.oraskin.chat.InMemoryChatStore;
import com.oraskin.connection.dto.HttpRequest;
import com.oraskin.connection.dto.MessageDelivery;
import com.oraskin.connection.dto.SendMessageCommand;
import com.oraskin.connection.dto.WebSocketFrame;
import com.oraskin.connection.util.*;
import com.oraskin.user.ClientSession;
import com.oraskin.user.SessionCache;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;

import static com.oraskin.connection.dto.FrameType.PONG;

public final class ChatServer {

    private final int port;
    private final ChatService chatService;
    private final HttpRequestReader httpRequestReader;
    private final HttpResponseWriter httpResponseWriter;
    private final WebSocketSupport webSocketSupport;

    public ChatServer(int port) {
        this(
                port,
                new ChatService(new InMemoryChatStore(Clock.systemUTC()), new SessionCache()),
                new HttpRequestReader(),
                new HttpResponseWriter(),
                new WebSocketSupport()
        );
    }

    ChatServer(
            int port,
            ChatService chatService,
            HttpRequestReader httpRequestReader,
            HttpResponseWriter httpResponseWriter,
            WebSocketSupport webSocketSupport
    ) {
        this.port = port;
        this.chatService = chatService;
        this.httpRequestReader = httpRequestReader;
        this.httpResponseWriter = httpResponseWriter;
        this.webSocketSupport = webSocketSupport;
    }

    public void start() throws IOException {
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            System.out.println("Chat server is listening on ws://localhost:" + port + "/connect?userId=<user>");
            while (true) {
                Socket socket = serverSocket.accept();
                Thread.ofVirtual().start(() -> handleConnection(socket));
            }
        }
    }

    private void handleConnection(Socket socket) {
        ClientSession session = null;
        try (socket) {
            InputStream input = socket.getInputStream();
            OutputStream output = socket.getOutputStream();

            HttpRequest request = httpRequestReader.read(input);
            if (!webSocketSupport.isUpgrade(request)) {
                handleHttpRequest(request, output);
                return;
            }

            webSocketSupport.validateHandshake(request);
            String userId = QueryParams.fromTarget(request.target()).required("userId");
            session = new ClientSession(userId, socket, output);
            if (!chatService.registerSession(userId, session)) {
                httpResponseWriter.writeText(output, 409, "User already connected", "User already connected");
                return;
            }

            webSocketSupport.writeHandshakeResponse(output, request.header("sec-websocket-key"));
            session.sendPayload("CONNECTED:" + userId);
            System.out.println("Connected user: " + userId);

            processWebSocketFrames(session, input);
        } catch (SocketException | EOFException ignored) {
            // Client disconnected.
        } catch (ChatException e) {
            writeSessionError(session, e.getMessage());
        } catch (Exception e) {
            System.err.println("Connection failed: " + e.getMessage());
            writeSessionError(session, e.getMessage());
        } finally {
            if (session != null) {
                chatService.terminateSession(session.userId());
                System.out.println("Disconnected user: " + session.userId());
            }
        }
    }

    private void processWebSocketFrames(ClientSession session, InputStream input) throws IOException {
        while (true) {
            WebSocketFrame frame = webSocketSupport.readFrame(input);
            if (frame == null) {
                return;
            }

            switch (frame.frameType()) {
                case TEXT -> routeMessage(session, new String(frame.payload(), StandardCharsets.UTF_8));
                case CLOSE -> {
                    session.close();
                    return;
                }
                case PING -> session.sendControlFrame(PONG, frame.payload());
                case PONG -> {
                    // Ignore pong frames.
                }
                default -> {
                    session.sendPayload("ERROR: unsupported frame type.");
                    return;
                }
            }
        }
    }

    private void routeMessage(ClientSession sender, String rawPayload) throws IOException {
        SendMessageCommand command = JsonCodec.parseSendMessage(rawPayload);
        MessageDelivery delivery = chatService.sendMessage(sender.userId(), command);
        String payload = JsonCodec.message(delivery.message());

        sender.sendPayload(payload);
        ClientSession recipient = chatService.findSession(delivery.recipientUserId());
        if (recipient != null) {
            recipient.sendPayload(payload);
        }
    }

    private void handleHttpRequest(HttpRequest request, OutputStream output) throws IOException {
        try {
            if ("GET".equals(request.method()) && "/chats".equals(request.path())) {
                handleGetChats(request, output);
                return;
            }
            if ("POST".equals(request.method()) && "/chats".equals(request.path())) {
                handleCreateChat(request, output);
                return;
            }
            if ("GET".equals(request.method()) && "/messages".equals(request.path())) {
                handleGetMessages(request, output);
                return;
            }

            httpResponseWriter.writeJson(output, 404, JsonCodec.error("Not found"));
        } catch (ChatException e) {
            httpResponseWriter.writeJson(output, e.statusCode(), JsonCodec.error(e.getMessage()));
        } catch (IOException e) {
            httpResponseWriter.writeJson(output, 400, JsonCodec.error(e.getMessage()));
        }
    }

    private void handleGetChats(HttpRequest request, OutputStream output) throws IOException {
        String userId = QueryParams.fromTarget(request.target()).required("userId");
        httpResponseWriter.writeJson(output, 200, JsonCodec.chatSummaries(chatService.findChatsForUser(userId)));
    }

    private void handleCreateChat(HttpRequest request, OutputStream output) throws IOException {
        QueryParams queryParams = QueryParams.fromTarget(request.target());
        String userId = queryParams.required("userId");
        String targetUserId = queryParams.required("targetUserId");
        ChatSummary summary = chatService.createChat(userId, targetUserId);
        httpResponseWriter.writeJson(output, 200, JsonCodec.chatSummary(summary));
    }

    private void handleGetMessages(HttpRequest request, OutputStream output) throws IOException {
        QueryParams queryParams = QueryParams.fromTarget(request.target());
        String userId = queryParams.required("userId");
        String chatId = queryParams.required("chatId");
        httpResponseWriter.writeJson(output, 200, JsonCodec.messages(chatService.findMessages(userId, chatId)));
    }

    private static void writeSessionError(ClientSession session, String message) {
        if (session == null) {
            return;
        }
        try {
            session.sendPayload("ERROR: " + message);
        } catch (IOException ignored) {
            // Connection is already broken.
        }
    }
}
