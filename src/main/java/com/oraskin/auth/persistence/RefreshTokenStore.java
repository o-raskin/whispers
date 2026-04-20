package com.oraskin.auth.persistence;

import com.oraskin.auth.persistence.entity.RefreshSessionRecord;

import java.time.Instant;

public interface RefreshTokenStore {

    void create(String sessionId, String tokenHash, String userId, String provider, String providerSubject, Instant issuedAt, Instant expiresAt);

    RefreshSessionRecord findActiveByTokenHash(String tokenHash, Instant now);

    void revokeSession(String sessionId, Instant revokedAt);
}
