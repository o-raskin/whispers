package com.oraskin.websocket;

import com.oraskin.chat.service.ChatException;
import com.oraskin.common.http.HttpStatus;
import com.oraskin.common.json.JsonCodec;
import com.oraskin.common.websocket.WebSocketFrame;
import com.oraskin.common.websocket.WebSocketSupport;
import com.oraskin.user.session.ClientSession;
import com.oraskin.websocket.message.ChatMessageService;
import com.oraskin.websocket.message.PrivateChatMessageService;
import com.oraskin.websocket.presence.PresenceService;
import com.oraskin.websocket.typing.TypingStateService;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;

import static com.oraskin.common.websocket.FrameType.PONG;

public final class WebSocketConnectionHandler {

    private final WebSocketSupport webSocketSupport;
    private final PresenceService presenceService;
    private final ChatMessageService chatMessageService;
    private final PrivateChatMessageService privateChatMessageService;
    private final TypingStateService typingStateService;

    public WebSocketConnectionHandler(
            WebSocketSupport webSocketSupport,
            PresenceService presenceService,
            ChatMessageService chatMessageService,
            PrivateChatMessageService privateChatMessageService,
            TypingStateService typingStateService
    ) {
        this.webSocketSupport = webSocketSupport;
        this.presenceService = presenceService;
        this.chatMessageService = chatMessageService;
        this.privateChatMessageService = privateChatMessageService;
        this.typingStateService = typingStateService;
    }

    public void handle(ClientSession session, InputStream input) {
        try {
            processFrames(session, input);
        } catch (SocketException | EOFException ignored) {
            // Client disconnected.
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
                case TEXT -> handleTextFrameSafely(session, new String(frame.payload(), StandardCharsets.UTF_8));
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

    private void handleTextFrameSafely(ClientSession session, String payload) {
        try {
            handleTextFrame(session, payload);
        } catch (ChatException e) {
            writeError(session, e.getMessage());
        } catch (Exception e) {
            writeError(session, e.getMessage());
        }
    }

    private void handleTextFrame(ClientSession session, String payload) throws IOException {
        WebSocketCommand command = JsonCodec.read(payload, WebSocketCommand.class);
        switch (command.type()) {
            case PRESENCE -> presenceService.sendPresence(session);
            case MESSAGE -> chatMessageService.sendMessage(session, requireChatId(command), command.text());
            case PRIVATE_MESSAGE -> privateChatMessageService.sendMessage(session, requireChatId(command), command.privateMessage());
            case TYPING_START -> typingStateService.startTyping(session, requireChatId(command));
            case TYPING_END -> typingStateService.stopTyping(session, requireChatId(command));
            default -> session.sendPayload("ERROR: unsupported message payload.");
        }
    }

    private long requireChatId(WebSocketCommand command) {
        if (command.chatId() == null) {
            throw new ChatException(HttpStatus.BAD_REQUEST, "chatId is required");
        }
        return command.chatId();
    }

    private void writeError(ClientSession session, String message) {
        try {
            session.sendPayload("ERROR: " + message);
        } catch (IOException ignored) {
            // connection already broken
        }
    }
}
