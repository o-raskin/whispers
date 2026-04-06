package com.oraskin.user;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class SessionCache implements SessionRegistry {

    private final Map<String, ClientSession> clients;

    public SessionCache() {
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
