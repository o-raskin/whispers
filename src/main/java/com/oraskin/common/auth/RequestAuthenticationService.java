package com.oraskin.common.auth;

import com.oraskin.auth.service.AccessTokenService;
import com.oraskin.chat.service.ChatException;
import com.oraskin.common.http.HttpRequest;
import com.oraskin.common.http.HttpStatus;

import java.util.Locale;
import java.util.Objects;

public final class RequestAuthenticationService {

    private static final String WEBSOCKET_PROTOCOL_PREFIX = "whispers.bearer.";

    private final AccessTokenService accessTokenService;

    public RequestAuthenticationService(AccessTokenService accessTokenService) {
        this.accessTokenService = Objects.requireNonNull(accessTokenService);
    }

    public HttpRequest authenticateRequired(HttpRequest request) {
        String token = extractBearerToken(request);
        if (token == null || token.isBlank()) {
            throw new ChatException(HttpStatus.UNAUTHORIZED, "Missing bearer token");
        }
        return request.withAuthenticatedUser(accessTokenService.authenticate(token));
    }

    public HttpRequest authenticateIfPresent(HttpRequest request) {
        String token = extractBearerToken(request);
        if (token == null || token.isBlank()) {
            return request;
        }
        return request.withAuthenticatedUser(accessTokenService.authenticate(token));
    }

    public String resolveWebSocketSubprotocol(HttpRequest request) {
        String header = request.header("sec-websocket-protocol");
        if (header == null || header.isBlank()) {
            return null;
        }

        for (String candidate : header.split(",")) {
            String protocol = candidate.trim();
            if (protocol.startsWith(WEBSOCKET_PROTOCOL_PREFIX)) {
                return protocol;
            }
        }
        return null;
    }

    private String extractBearerToken(HttpRequest request) {
        String authorization = request.header("authorization");
        if (authorization != null && authorization.regionMatches(true, 0, "Bearer ", 0, 7)) {
            return authorization.substring(7).trim();
        }

        String websocketProtocol = resolveWebSocketSubprotocol(request);
        if (websocketProtocol != null) {
            return websocketProtocol.substring(WEBSOCKET_PROTOCOL_PREFIX.length());
        }
        return null;
    }
}
