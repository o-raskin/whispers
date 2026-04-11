package com.oraskin.websocket;

import com.oraskin.chat.service.ChatException;
import com.oraskin.chat.service.ChatService;
import com.oraskin.common.json.JsonCodec;
import com.oraskin.common.websocket.WebSocketFrame;
import com.oraskin.common.websocket.WebSocketSupport;
import com.oraskin.connection.MessageDelivery;
import com.oraskin.connection.PresenceEvent;
import com.oraskin.connection.SendMessageCommand;
import com.oraskin.user.session.ClientSession;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;

import static com.oraskin.common.websocket.FrameType.PONG;

public final class WebSocketConnectionHandler {

    private final ChatService chatService;
    private final WebSocketMessageSender webSocketMessageSender;
    private final WebSocketSupport webSocketSupport;

    public WebSocketConnectionHandler(
            ChatService chatService,
            WebSocketMessageSender webSocketMessageSender,
            WebSocketSupport webSocketSupport
    ) {
        this.chatService = chatService;
        this.webSocketMessageSender = webSocketMessageSender;
        this.webSocketSupport = webSocketSupport;
    }

    public void handle(ClientSession session, InputStream input) {
        try {
            processFrames(session, input);
        } catch (SocketException | EOFException ignored) {
            // Client disconnected.
        } catch (ChatException e) {
            writeError(session, e.getMessage());
        } catch (Exception e) {
            writeError(session, e.getMessage());
        }
    }

    private void processFrames(ClientSession session, InputStream input) throws Exception {
        while (true) {
            WebSocketFrame frame = webSocketSupport.readFrame(input);
            if (frame == null) {
                return;
            }

            switch (frame.frameType()) {
                case TEXT -> handleTextFrame(session, new String(frame.payload(), StandardCharsets.UTF_8));
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

    private void handleTextFrame(ClientSession session, String payload) throws IOException {
        WebSocketCommand command = JsonCodec.read(payload, WebSocketCommand.class);
        if (command.isPing()) {
            PresenceEvent presenceEvent = chatService.acceptPing(session.userId());
            webSocketMessageSender.sendToUsers(chatService.findPresenceSubscriberUserIds(session.userId()), presenceEvent);
            return;
        }
        if (command.isMessage()) {
            MessageDelivery delivery = chatService.sendMessage(
                    session.userId(),
                    new SendMessageCommand(command.chatId(), command.text())
            );
            webSocketMessageSender.sendToUser(delivery.recipientUserId(), delivery.message());
            session.sendPayload(JsonCodec.write(delivery.message()));
            return;
        }
        session.sendPayload("ERROR: unsupported message payload.");
    }

    private void writeError(ClientSession session, String message) {
        try {
            session.sendPayload("ERROR: " + message);
        } catch (IOException ignored) {
            // connection already broken
        }
    }
}
