package com.oraskin.user.session.persistence;

import com.oraskin.user.session.ClientSession;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class InMemorySessionRegistry implements SessionRegistry {

    private final Map<String, ClientSession> clients;

    public InMemorySessionRegistry() {
        this.clients = new ConcurrentHashMap<>();
    }

    @Override
    public boolean register(String userId, ClientSession session) {
        return clients.putIfAbsent(userId, session) == null;
    }

    @Override
    public ClientSession findSession(String userId) {
        return clients.get(userId);
    }

    @Override
    public void remove(String userId) {
        clients.remove(userId);
    }

    @Override
    public boolean isConnected(String userId) {
        return clients.containsKey(userId);
    }
}
