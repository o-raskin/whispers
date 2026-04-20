package com.oraskin.chat.privatechat.value;

import com.oraskin.chat.value.ChatType;

public record PrivateMessageView(
        long chatId,
        String senderUsername,
        ChatType chatType,
        EncryptedPrivateMessagePayload encryptedMessage,
        String timestamp
) {
}
