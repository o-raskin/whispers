package com.oraskin.common.http;

import com.oraskin.common.auth.AuthenticatedUser;

import java.util.Locale;
import java.util.Map;

public record HttpRequest(
        String method,
        String path,
        QueryParams params,
        Map<String, String> headers,
        String body,
        AuthenticatedUser authenticatedUser
) {

    public String header(String name) {
        return headers.get(name.toLowerCase(Locale.ROOT));
    }

    public String cookie(String name) {
        String cookieHeader = header("cookie");
        if (cookieHeader == null || cookieHeader.isBlank()) {
            return null;
        }

        for (String cookie : cookieHeader.split(";")) {
            String[] parts = cookie.trim().split("=", 2);
            if (parts.length == 2 && parts[0].trim().equals(name)) {
                return parts[1].trim();
            }
        }
        return null;
    }

    public HttpRequest withAuthenticatedUser(AuthenticatedUser user) {
        return new HttpRequest(method, path, params, headers, body, user);
    }
}
