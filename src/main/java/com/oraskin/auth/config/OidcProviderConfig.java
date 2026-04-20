package com.oraskin.auth.config;

import java.net.URI;
import java.util.List;

public record OidcProviderConfig(
        String provider,
        URI issuerUri,
        String clientId,
        String clientSecret,
        URI redirectUri,
        List<String> scopes
) {

    public static OidcProviderConfig google(FrontendConfig frontendConfig) {
        String redirect = readEnv(
                "WHISPERS_OIDC_GOOGLE_REDIRECT_URI",
                frontendConfig.origin() + "/auth/callback/google"
        );
        return new OidcProviderConfig(
                "google",
                URI.create(readEnv("WHISPERS_OIDC_GOOGLE_ISSUER", "https://accounts.google.com")),
                requireEnv("WHISPERS_GOOGLE_CLIENT_ID"),
                requireEnv("WHISPERS_GOOGLE_CLIENT_SECRET"),
                URI.create(redirect),
                List.of("openid", "email", "profile")
        );
    }

    private static String requireEnv(String key) {
        String value = System.getenv(key);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing required environment variable: " + key);
        }
        return value;
    }

    private static String readEnv(String key, String defaultValue) {
        String value = System.getenv(key);
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        return value;
    }
}
