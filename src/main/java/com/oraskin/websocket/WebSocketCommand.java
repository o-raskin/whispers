package com.oraskin.websocket;

public record WebSocketCommand(WebSocketCommandType type, Long chatId, String text) {

}
