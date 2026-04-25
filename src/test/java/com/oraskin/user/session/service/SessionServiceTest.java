package com.oraskin.user.session.service;

import com.oraskin.auth.AuthTestSupport;
import com.oraskin.chat.service.ChatException;
import com.oraskin.common.http.HttpStatus;
import com.oraskin.user.session.ClientSession;
import com.oraskin.user.session.persistence.InMemorySessionRegistry;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.net.Socket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SessionServiceTest {

    @Test
    void openFindAndCloseSessionManageConnectionLifecycle() {
        InMemorySessionRegistry sessionRegistry = new InMemorySessionRegistry();
        SessionService service = new SessionService(sessionRegistry);

        ClientSession session = service.openSession(
                AuthTestSupport.authenticatedUser("alice-id", "alice@example.com", "hash"),
                new Socket(),
                new ByteArrayOutputStream()
        );

        assertThat(service.findSession("alice-id")).isEqualTo(session);
        service.closeSession("alice-id");
        assertThat(service.findSession("alice-id")).isNull();
    }

    @Test
    void openSessionRejectsDuplicateConnections() {
        InMemorySessionRegistry sessionRegistry = new InMemorySessionRegistry();
        SessionService service = new SessionService(sessionRegistry);
        service.openSession(AuthTestSupport.authenticatedUser("alice-id", "alice@example.com", "hash"), new Socket(), new ByteArrayOutputStream());

        ChatException exception = assertThrows(
                ChatException.class,
                () -> service.openSession(AuthTestSupport.authenticatedUser("alice-id", "alice@example.com", "hash"), new Socket(), new ByteArrayOutputStream())
        );

        assertThat(exception.status()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(exception).hasMessage("User already connected");
    }
}
