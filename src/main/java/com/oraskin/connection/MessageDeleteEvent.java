package com.oraskin.connection;

public record MessageDeleteEvent(String type, long chatId, long messageId) {
}
