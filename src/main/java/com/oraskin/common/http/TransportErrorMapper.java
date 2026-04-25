package com.oraskin.common.http;

import com.oraskin.chat.service.ChatException;

import java.io.IOException;

public final class TransportErrorMapper {

    private TransportErrorMapper() {
    }

    public static HttpStatus httpStatus(Throwable failure) {
        if (failure instanceof ChatException chatException) {
            return chatException.status();
        }
        if (failure instanceof IOException || failure instanceof IllegalArgumentException) {
            return HttpStatus.BAD_REQUEST;
        }
        return HttpStatus.INTERNAL_SERVER_ERROR;
    }

    public static String clientMessage(Throwable failure) {
        HttpStatus status = httpStatus(failure);
        if (status == HttpStatus.INTERNAL_SERVER_ERROR) {
            return "Internal server error";
        }
        return failure.getMessage();
    }
}
