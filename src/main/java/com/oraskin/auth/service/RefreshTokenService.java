package com.oraskin.auth.service;

import com.oraskin.auth.config.AuthConfig;
import com.oraskin.auth.domain.RefreshToken;
import com.oraskin.auth.persistence.RefreshTokenStore;
import com.oraskin.auth.persistence.entity.RefreshSessionRecord;
import com.oraskin.chat.service.ChatException;
import com.oraskin.common.http.HttpStatus;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.Objects;
import java.util.UUID;

public final class RefreshTokenService {

    private final RefreshTokenStore refreshTokenStore;
    private final AccessTokenService accessTokenService;
    private final AuthConfig authConfig;
    private final Clock clock;
    private final SecureRandom secureRandom;

    public RefreshTokenService(
            RefreshTokenStore refreshTokenStore,
            AccessTokenService accessTokenService,
            AuthConfig authConfig,
            Clock clock
    ) {
        this.refreshTokenStore = Objects.requireNonNull(refreshTokenStore);
        this.accessTokenService = Objects.requireNonNull(accessTokenService);
        this.authConfig = Objects.requireNonNull(authConfig);
        this.clock = Objects.requireNonNull(clock);
        this.secureRandom = new SecureRandom();
    }

    public RefreshToken issue(String userId, String provider, String providerSubject) {
        byte[] randomBytes = new byte[48];
        secureRandom.nextBytes(randomBytes);
        String rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
        Instant issuedAt = Instant.now(clock);
        Instant expiresAt = issuedAt.plus(authConfig.refreshTokenTtl());
        refreshTokenStore.create(
                UUID.randomUUID().toString(),
                accessTokenService.hash(rawToken),
                userId,
                provider,
                providerSubject,
                issuedAt,
                expiresAt
        );
        return new RefreshToken(rawToken, expiresAt);
    }

    public RefreshSessionRecord authenticate(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new ChatException(HttpStatus.UNAUTHORIZED, "Missing refresh token");
        }
        RefreshSessionRecord record = refreshTokenStore.findActiveByTokenHash(accessTokenService.hash(rawToken), Instant.now(clock));
        if (record == null) {
            throw new ChatException(HttpStatus.UNAUTHORIZED, "Invalid or expired refresh token");
        }
        return record;
    }

    public void revoke(RefreshSessionRecord sessionRecord) {
        refreshTokenStore.revokeSession(sessionRecord.sessionId(), Instant.now(clock));
    }
}
