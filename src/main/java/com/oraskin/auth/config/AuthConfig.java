package com.oraskin.auth.config;

import java.net.URI;
import java.time.Duration;

public record AuthConfig(Duration accessTokenTtl, Duration refreshTokenTtl, String tokenIssuer, URI postLogoutRedirectUri) {

    private static final long DEFAULT_ACCESS_TOKEN_TTL_SECONDS = 86_400L;
    private static final long DEFAULT_REFRESH_TOKEN_TTL_SECONDS = 2_592_000L;
    private static final String DEFAULT_TOKEN_ISSUER = "whispers";

    public static AuthConfig fromEnvironment(FrontendConfig frontendConfig) {
        String ttlValue = readEnv("WHISPERS_AUTH_TOKEN_TTL_SECONDS", String.valueOf(DEFAULT_ACCESS_TOKEN_TTL_SECONDS));
        String refreshTtlValue = readEnv("WHISPERS_AUTH_REFRESH_TOKEN_TTL_SECONDS", String.valueOf(DEFAULT_REFRESH_TOKEN_TTL_SECONDS));
        long ttlSeconds = Long.parseLong(ttlValue);
        long refreshTtlSeconds = Long.parseLong(refreshTtlValue);
        if (ttlSeconds <= 0) {
            throw new IllegalStateException("WHISPERS_AUTH_TOKEN_TTL_SECONDS must be positive");
        }
        if (refreshTtlSeconds <= 0) {
            throw new IllegalStateException("WHISPERS_AUTH_REFRESH_TOKEN_TTL_SECONDS must be positive");
        }

        return new AuthConfig(
                Duration.ofSeconds(ttlSeconds),
                Duration.ofSeconds(refreshTtlSeconds),
                readEnv("WHISPERS_AUTH_TOKEN_ISSUER", DEFAULT_TOKEN_ISSUER),
                URI.create(readEnv("WHISPERS_AUTH_POST_LOGOUT_REDIRECT_URI", frontendConfig.origin() + "/"))
        );
    }

    private static String readEnv(String key, String defaultValue) {
        String value = System.getenv(key);
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        return value;
    }
}
