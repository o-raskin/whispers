package com.oraskin.websocket;

public record WebSocketCommand(String type, String chatId, String text) {

    public boolean isPing() {
        return "ping".equals(type);
    }

    public boolean isMessage() {
        return (type == null || "message".equals(type))
                && chatId != null && !chatId.isBlank()
                && text != null && !text.isBlank();
    }
}
