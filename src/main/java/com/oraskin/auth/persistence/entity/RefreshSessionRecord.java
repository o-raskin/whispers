package com.oraskin.auth.persistence.entity;

import java.time.Instant;

public record RefreshSessionRecord(
        String sessionId,
        String userId,
        String provider,
        String providerSubject,
        Instant expiresAt,
        Instant revokedAt
) {
}
