package com.oraskin.websocket;

public record WebSocketCommand(WebSocketCommandType type, String chatId, String text) {

}
