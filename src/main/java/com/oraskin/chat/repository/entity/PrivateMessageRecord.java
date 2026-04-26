package com.oraskin.chat.repository.entity;

import com.oraskin.chat.privatechat.value.EncryptedPrivateMessagePayload;

public record PrivateMessageRecord(
        long chatId,
        String senderUserId,
        EncryptedPrivateMessagePayload encryptedMessage,
        String timestamp,
        String updatedAt
) {
}
