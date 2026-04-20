package com.oraskin.user.session.persistence;

import com.oraskin.user.session.ClientSession;

public interface SessionRegistry {

    boolean createUserSession(String userId, ClientSession session);

    ClientSession findSession(String userId);

    void remove(String userId);

    boolean isConnected(String userId);
}
