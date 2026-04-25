package com.oraskin.resource;

import com.oraskin.user.session.persistence.InMemorySessionRegistry;
import com.oraskin.user.session.service.SessionService;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.net.Socket;

import static com.oraskin.auth.AuthTestSupport.authenticatedUser;
import static org.assertj.core.api.Assertions.assertThat;

class UserConnectionControllerTest {

    @Test
    void connectOpensSessionAndReturnsConnectedBanner() {
        InMemorySessionRegistry sessionRegistry = new InMemorySessionRegistry();
        SessionService sessionService = new SessionService(sessionRegistry);
        UserConnectionController controller = new UserConnectionController(sessionService);

        String banner = controller.connect(authenticatedUser("alice-id", "alice@example.com", "hash"), new Socket(), new ByteArrayOutputStream());

        assertThat(banner).isEqualTo("CONNECTED:alice@example.com");
        assertThat(sessionService.findSession("alice-id")).isNotNull();
    }
}
