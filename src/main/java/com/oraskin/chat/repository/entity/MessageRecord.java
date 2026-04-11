package com.oraskin.chat.repository.entity;

public record MessageRecord(String chatId, String senderUserId, String text, String timestamp) {
}
