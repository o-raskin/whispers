package com.oraskin.websocket;

import com.oraskin.common.json.JsonCodec;
import com.oraskin.user.session.ClientSession;
import com.oraskin.user.session.persistence.SessionRegistry;

import java.io.IOException;
import java.util.Collection;
import java.util.Objects;

public final class SessionWebSocketMessageSender implements WebSocketMessageSender {

    private final SessionRegistry sessionRegistry;

    public SessionWebSocketMessageSender(SessionRegistry sessionRegistry) {
        this.sessionRegistry = Objects.requireNonNull(sessionRegistry);
    }

    @Override
    public void sendToUser(String userId, Object payload) {
        if (userId == null || payload == null) {
            return;
        }

        ClientSession session = sessionRegistry.findSession(userId);
        if (session == null) {
            return;
        }

        try {
            var jsonPayload = JsonCodec.write(payload);
            session.sendPayload(jsonPayload);
        } catch (IOException e) {
            System.err.println("Cannot send WebSocket payload to user '" + session.userId() + "': " + e.getClass().getSimpleName());
        }
    }

    @Override
    public void sendToUsers(Collection<String> userIds, Object payload) {
        if (userIds == null || payload == null) {
            return;
        }

        for (String userId : userIds) {
            sendToUser(userId, payload);
        }
    }
}
