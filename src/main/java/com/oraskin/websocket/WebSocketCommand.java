package com.oraskin.websocket;

import com.oraskin.chat.privatechat.value.EncryptedPrivateMessagePayload;

public record WebSocketCommand(
        WebSocketCommandType type,
        Long chatId,
        String text,
        EncryptedPrivateMessagePayload privateMessage
) {

}
