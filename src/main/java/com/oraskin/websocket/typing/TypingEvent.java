package com.oraskin.websocket.typing;

public record TypingEvent(String type, String chatId, String username) {
}
