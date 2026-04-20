package com.oraskin.auth.service;

import com.oraskin.auth.config.AuthConfig;
import com.oraskin.auth.domain.AccessToken;
import com.oraskin.auth.persistence.AccessTokenStore;
import com.oraskin.chat.service.ChatException;
import com.oraskin.common.auth.AuthenticatedUser;
import com.oraskin.common.http.HttpStatus;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Objects;

public final class AccessTokenService {

    private final AccessTokenStore accessTokenStore;
    private final AuthConfig authConfig;
    private final Clock clock;
    private final SecureRandom secureRandom;

    public AccessTokenService(AccessTokenStore accessTokenStore, AuthConfig authConfig, Clock clock) {
        this.accessTokenStore = Objects.requireNonNull(accessTokenStore);
        this.authConfig = Objects.requireNonNull(authConfig);
        this.clock = Objects.requireNonNull(clock);
        this.secureRandom = new SecureRandom();
    }

    public AccessToken issueToken(String userId, String provider, String providerSubject) {
        byte[] randomBytes = new byte[32];
        secureRandom.nextBytes(randomBytes);
        String rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);

        Instant issuedAt = Instant.now(clock);
        Instant expiresAt = issuedAt.plus(authConfig.accessTokenTtl());
        accessTokenStore.createForUser(hash(rawToken), userId, provider, providerSubject, issuedAt, expiresAt);
        return new AccessToken(rawToken, "Bearer", authConfig.accessTokenTtl().toSeconds(), expiresAt);
    }

    public AuthenticatedUser authenticate(String rawToken) {
        AuthenticatedUser user = accessTokenStore.findActiveUserByTokenHash(hash(rawToken), Instant.now(clock));
        if (user == null) {
            throw new ChatException(HttpStatus.UNAUTHORIZED, "Invalid or expired bearer token");
        }
        return user;
    }

    public void revoke(String tokenHash) {
        accessTokenStore.revoke(tokenHash, Instant.now(clock));
    }

    public String hash(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(rawToken.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }
}
