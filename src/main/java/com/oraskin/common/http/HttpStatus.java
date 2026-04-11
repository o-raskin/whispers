package com.oraskin.common.http;

public enum HttpStatus {
    SWITCHING_PROTOCOLS(101, "Switching Protocols"),
    OK(200, "OK"),
    BAD_REQUEST(400, "Bad Request"),
    NOT_FOUND(404, "Not Found"),
    CONFLICT(409, "Conflict");

    private final int code;
    private final String reasonPhrase;

    HttpStatus(int code, String reasonPhrase) {
        this.code = code;
        this.reasonPhrase = reasonPhrase;
    }

    public int code() {
        return code;
    }

    public String reasonPhrase() {
        return reasonPhrase;
    }
}
