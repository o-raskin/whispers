package com.oraskin;

public record WebSocketFrame(FrameType frameType, byte[] payload) {
}
