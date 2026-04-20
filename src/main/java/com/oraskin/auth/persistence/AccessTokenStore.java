package com.oraskin.auth.persistence;

import com.oraskin.common.auth.AuthenticatedUser;

import java.time.Instant;

public interface AccessTokenStore {

    void createForUser(String tokenHash, String userId, String provider, String providerSubject, Instant issuedAt, Instant expiresAt);

    AuthenticatedUser findActiveUserByTokenHash(String tokenHash, Instant now);

    void revoke(String tokenHash, Instant revokedAt);
}
