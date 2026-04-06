package com.oraskin.connection.dto;

public record WebSocketFrame(FrameType frameType, byte[] payload) {
}
