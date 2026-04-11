package com.oraskin.websocket;

import java.util.Collection;

public interface WebSocketMessageSender {

    void sendToUser(String userId, Object payload);

    void sendToUsers(Collection<String> userIds, Object payload);
}
