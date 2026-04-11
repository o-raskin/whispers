package com.oraskin.common.websocket;

public record WebSocketFrame(FrameType frameType, byte[] payload) {
}
