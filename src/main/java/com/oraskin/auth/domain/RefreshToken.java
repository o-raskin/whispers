package com.oraskin.auth.domain;

import java.time.Instant;

public record RefreshToken(
        String token,
        Instant expiresAt
) {
}
