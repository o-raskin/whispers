package com.oraskin.chat.service;

import com.oraskin.common.http.HttpStatus;

public final class ChatException extends RuntimeException {

    private final HttpStatus status;

    public ChatException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus status() {
        return status;
    }
}
