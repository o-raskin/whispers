package com.oraskin.user.session.service;

import com.oraskin.chat.service.ChatException;
import com.oraskin.common.auth.AuthenticatedUser;
import com.oraskin.common.http.HttpStatus;
import com.oraskin.user.data.persistence.UserStore;
import com.oraskin.user.session.ClientSession;
import com.oraskin.user.session.persistence.SessionRegistry;

import java.io.OutputStream;
import java.net.Socket;
import java.util.Objects;

public final class SessionService {

    private final SessionRegistry sessionRegistry;

    public SessionService(SessionRegistry sessionRegistry) {
        this.sessionRegistry = Objects.requireNonNull(sessionRegistry);
    }

    public ClientSession openSession(AuthenticatedUser user, Socket socket, OutputStream output) {
        ClientSession session = new ClientSession(user.userId(), socket, output);
        if (!sessionRegistry.createUserSession(user.userId(), session)) {
            throw new ChatException(HttpStatus.CONFLICT, "User already connected");
        }
        return session;
    }

    public ClientSession findSession(String userId) {
        return sessionRegistry.findSession(userId);
    }

    public void closeSession(String userId) {
        sessionRegistry.remove(userId);
    }
}
