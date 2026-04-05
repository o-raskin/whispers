package com.oraskin;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class SessionCache {

    private final Map<String, ClientSession> clients;

    public SessionCache() {
        this.clients = new ConcurrentHashMap<>();
    }

    public ClientSession createSession(String userId, ClientSession session) {
        return clients.putIfAbsent(userId, session);
    }

    public ClientSession findSession(String userId) {
        return clients.get(userId);
    }

    public void terminateSession(String userId) {
        clients.remove(userId);
    }
}
