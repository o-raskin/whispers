package com.oraskin.websocket.presence;

import com.oraskin.chat.service.ChatService;
import com.oraskin.connection.PresenceEvent;
import com.oraskin.user.session.ClientSession;
import com.oraskin.websocket.WebSocketMessageSender;

public class PresenceService {

    private final ChatService chatService;
    private final WebSocketMessageSender webSocketMessageSender;

    public PresenceService(ChatService chatService,
                           WebSocketMessageSender webSocketMessageSender) {
        this.chatService = chatService;
        this.webSocketMessageSender = webSocketMessageSender;
    }

    public void sendPresence(ClientSession clientSession) {
        PresenceEvent presenceEvent = chatService.acceptPing(clientSession.userId());
        var recipients = chatService.findPresenceSubscriberUserIds(clientSession.userId());
        webSocketMessageSender.sendToUsers(recipients, presenceEvent);
    }
}
