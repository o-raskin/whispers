package com.oraskin.connection;

public record SendMessageCommand(String chatId, String text) {
}
