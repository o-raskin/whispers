package com.oraskin.connection.dto;

public record SendMessageCommand(String chatId, String text) {
}
