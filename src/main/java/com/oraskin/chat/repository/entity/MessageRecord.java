package com.oraskin.chat.repository.entity;

public record MessageRecord(long chatId, String senderUserId, String text, String timestamp) {
}
