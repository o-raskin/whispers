package com.oraskin.connection.dto;

public record MessageRecord(String chatId, String senderUserId, String text, String timestamp) {
}
