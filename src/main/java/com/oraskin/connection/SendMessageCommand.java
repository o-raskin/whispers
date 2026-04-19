package com.oraskin.connection;

public record SendMessageCommand(Long chatId, String text) {
}
