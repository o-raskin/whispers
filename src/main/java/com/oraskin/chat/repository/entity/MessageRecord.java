package com.oraskin.chat.repository.entity;

public record MessageRecord(
        long messageId,
        long chatId,
        String senderUserId,
        String text,
        String timestamp,
        String updatedAt
) {
}
