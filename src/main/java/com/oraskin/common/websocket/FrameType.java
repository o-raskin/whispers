package com.oraskin.common.websocket;

public enum FrameType {
    TEXT(0x1),
    BINARY(0x2),
    CLOSE(0x8),
    PING(0x9),
    PONG(0xA);

    private final int code;

    FrameType(int code) {
        this.code = code;
    }

    public static FrameType fromCode(int code) {
        return switch (code) {
            case 0x1 -> TEXT;
            case 0x2 -> BINARY;
            case 0x8 -> CLOSE;
            case 0x9 -> PING;
            case 0xA -> PONG;
            default -> throw new IllegalArgumentException("Unsupported frame type code: " + code);
        };
    }

    public int code() {
        return code;
    }

    public boolean isControl() {
        return switch (this) {
            case CLOSE, PING, PONG -> true;
            case TEXT, BINARY -> false;
        };
    }
}
