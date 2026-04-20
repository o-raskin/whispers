package com.oraskin.auth.domain;

import java.time.Instant;

public record AccessToken(
        String token,
        String tokenType,
        long expiresInSeconds,
        Instant expiresAt
) {
}
