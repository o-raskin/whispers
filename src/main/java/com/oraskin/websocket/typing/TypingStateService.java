package com.oraskin.websocket.typing;

import com.oraskin.chat.service.ChatService;
import com.oraskin.user.session.ClientSession;
import com.oraskin.websocket.WebSocketMessageSender;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public final class TypingStateService {

    private static final long TYPING_TIMEOUT_SECONDS = 5L;

    private final ChatService chatService;
    private final WebSocketMessageSender webSocketMessageSender;

    private final ScheduledExecutorService scheduler;
    private final Map<TypingKey, TypingState> states = new HashMap<>();

    public TypingStateService(ChatService chatService,
                              WebSocketMessageSender webSocketMessageSender) {
        this.chatService = chatService;
        this.webSocketMessageSender = webSocketMessageSender;
        this.scheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "typing-state");
            thread.setDaemon(true);
            return thread;
        });
    }

    public void startTyping(ClientSession clientSession, long chatId) {
        var recipientUserId = chatService.findChatRecipientUserId(clientSession.userId(), chatId);
        TypingKey key = new TypingKey(chatId, clientSession.userId());
        synchronized (states) {
            long version = nextVersion(key);
            cancelCurrent(key);
            ScheduledFuture<?> future = scheduler.schedule(
                    () -> expireTyping(key, recipientUserId, version),
                    TYPING_TIMEOUT_SECONDS,
                    TimeUnit.SECONDS
            );
            states.put(key, new TypingState(recipientUserId, version, future));
        }
        webSocketMessageSender.sendToUser(
                recipientUserId,
                new TypingEvent("typing:start", chatId, chatService.usernameForUserId(clientSession.userId()))
        );
    }

    public void stopTyping(ClientSession clientSession, long chatId) {
        var recipientUserId = chatService.findChatRecipientUserId(clientSession.userId(), chatId);
        TypingKey key = new TypingKey(chatId, clientSession.userId());
        boolean removed = false;
        synchronized (states) {
            TypingState state = states.remove(key);
            if (state != null) {
                state.future().cancel(false);
                removed = true;
            }
        }
        if (removed) {
            webSocketMessageSender.sendToUser(
                    recipientUserId,
                    new TypingEvent("typing:stop", chatId, chatService.usernameForUserId(clientSession.userId()))
            );
        }
    }

    public void clearUser(String userId) {
        Map<TypingKey, TypingState> removedStates = new HashMap<>();
        synchronized (states) {
            states.entrySet().removeIf(entry -> {
                if (!entry.getKey().userId().equals(userId)) {
                    return false;
                }
                entry.getValue().future().cancel(false);
                removedStates.put(entry.getKey(), entry.getValue());
                return true;
            });
        }
        for (Map.Entry<TypingKey, TypingState> entry : removedStates.entrySet()) {
            webSocketMessageSender.sendToUser(
                    entry.getValue().recipientUserId(),
                    new TypingEvent("typing:stop", entry.getKey().chatId(), chatService.usernameForUserId(entry.getKey().userId()))
            );
        }
    }

    private void expireTyping(TypingKey key, String recipientUserId, long version) {
        boolean expired = false;
        synchronized (states) {
            TypingState state = states.get(key);
            if (state != null && state.version() == version) {
                states.remove(key);
                expired = true;
            }
        }
        if (expired) {
            webSocketMessageSender.sendToUser(
                    recipientUserId,
                    new TypingEvent("typing:stop", key.chatId(), chatService.usernameForUserId(key.userId()))
            );
        }
    }

    private long nextVersion(TypingKey key) {
        TypingState current = states.get(key);
        return current == null ? 1L : current.version() + 1L;
    }

    private void cancelCurrent(TypingKey key) {
        TypingState current = states.get(key);
        if (current != null) {
            current.future().cancel(false);
        }
    }

    private record TypingKey(long chatId, String userId) {
    }

    private record TypingState(String recipientUserId, long version, ScheduledFuture<?> future) {
    }
}
