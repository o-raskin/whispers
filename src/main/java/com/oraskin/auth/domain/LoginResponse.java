package com.oraskin.auth.domain;

public record LoginResponse(
        String accessToken,
        String tokenType,
        long expiresInSeconds,
        AuthUserProfile user
) {
}
