package com.oraskin.user;

public interface SessionRegistry {

    boolean register(String userId, ClientSession session);

    ClientSession findSession(String userId);

    void remove(String userId);

    boolean isConnected(String userId);
}
