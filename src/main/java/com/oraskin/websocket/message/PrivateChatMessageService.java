package com.oraskin.websocket.message;

import com.oraskin.chat.privatechat.service.PrivateChatService;
import com.oraskin.chat.privatechat.value.EncryptedPrivateMessagePayload;
import com.oraskin.common.json.JsonCodec;
import com.oraskin.connection.PrivateMessageDelivery;
import com.oraskin.connection.SendPrivateMessageCommand;
import com.oraskin.user.session.ClientSession;
import com.oraskin.websocket.WebSocketMessageSender;

import java.io.IOException;

public final class PrivateChatMessageService {

    private final PrivateChatService privateChatService;
    private final WebSocketMessageSender webSocketMessageSender;

    public PrivateChatMessageService(
            PrivateChatService privateChatService,
            WebSocketMessageSender webSocketMessageSender
    ) {
        this.privateChatService = privateChatService;
        this.webSocketMessageSender = webSocketMessageSender;
    }

    public void sendMessage(
            ClientSession clientSession,
            long chatId,
            EncryptedPrivateMessagePayload encryptedMessage
    ) throws IOException {
        PrivateMessageDelivery delivery = privateChatService.sendMessage(
                clientSession.userId(),
                new SendPrivateMessageCommand(chatId, encryptedMessage)
        );
        webSocketMessageSender.sendToUser(delivery.recipientUserId(), delivery.message());
        clientSession.sendPayload(JsonCodec.write(delivery.message()));
    }
}
