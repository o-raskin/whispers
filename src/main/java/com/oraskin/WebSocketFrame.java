package com.oraskin;

public record WebSocketFrame(int opcode, byte[] payload) {
}
