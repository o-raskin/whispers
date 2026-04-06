package com.oraskin.chat;

public final class ChatException extends RuntimeException {

    private final int statusCode;

    public ChatException(int statusCode, String message) {
        super(message);
        this.statusCode = statusCode;
    }

    public int statusCode() {
        return statusCode;
    }
}
