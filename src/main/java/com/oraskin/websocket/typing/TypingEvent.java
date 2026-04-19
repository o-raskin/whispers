package com.oraskin.websocket.typing;

public record TypingEvent(String type, long chatId, String username) {
}
