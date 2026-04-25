package com.oraskin.user.session.persistence;

import com.oraskin.user.session.ClientSession;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.net.Socket;

import static org.assertj.core.api.Assertions.assertThat;

class InMemorySessionRegistryTest {

    @Test
    void createFindRemoveAndIsConnectedTrackSessionsByUserId() {
        InMemorySessionRegistry registry = new InMemorySessionRegistry();
        ClientSession session = new ClientSession("alice-id", new Socket(), new ByteArrayOutputStream());

        assertThat(registry.createUserSession("alice-id", session)).isTrue();
        assertThat(registry.createUserSession("alice-id", new ClientSession("alice-id", new Socket(), new ByteArrayOutputStream()))).isFalse();
        assertThat(registry.findSession("alice-id")).isEqualTo(session);
        assertThat(registry.isConnected("alice-id")).isTrue();

        registry.remove("alice-id");

        assertThat(registry.findSession("alice-id")).isNull();
        assertThat(registry.isConnected("alice-id")).isFalse();
    }
}
