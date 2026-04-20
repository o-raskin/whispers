package com.oraskin.common.http.cookie;

import java.time.Duration;

public record ResponseCookie(
        String name,
        String value,
        boolean httpOnly,
        boolean secure,
        String sameSite,
        String path,
        Duration maxAge
) {

    public String serialize() {
        StringBuilder builder = new StringBuilder()
                .append(name).append('=').append(value)
                .append("; Path=").append(path);
        if (maxAge != null) {
            builder.append("; Max-Age=").append(maxAge.toSeconds());
        }
        if (httpOnly) {
            builder.append("; HttpOnly");
        }
        if (secure) {
            builder.append("; Secure");
        }
        if (sameSite != null && !sameSite.isBlank()) {
            builder.append("; SameSite=").append(sameSite);
        }
        return builder.toString();
    }
}
