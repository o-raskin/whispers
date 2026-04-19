package com.oraskin.websocket.message;

import com.oraskin.chat.service.ChatService;
import com.oraskin.common.json.JsonCodec;
import com.oraskin.connection.MessageDelivery;
import com.oraskin.connection.SendMessageCommand;
import com.oraskin.user.session.ClientSession;
import com.oraskin.websocket.WebSocketMessageSender;

import java.io.IOException;

public class ChatMessageService {

    private final ChatService chatService;
    private final WebSocketMessageSender webSocketMessageSender;

    public ChatMessageService(ChatService chatService,
                              WebSocketMessageSender webSocketMessageSender) {
        this.chatService = chatService;
        this.webSocketMessageSender = webSocketMessageSender;
    }

    public void sendMessage(ClientSession clientSession, long chatId, String text) throws IOException {
        MessageDelivery delivery = chatService.sendMessage(
                clientSession.userId(),
                new SendMessageCommand(chatId, text)
        );
        webSocketMessageSender.sendToUser(delivery.recipientUserId(), delivery.message());
        clientSession.sendPayload(JsonCodec.write(delivery.message()));
    }
}
