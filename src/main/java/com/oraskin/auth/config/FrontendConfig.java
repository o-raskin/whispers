package com.oraskin.auth.config;

import java.net.URI;

public record FrontendConfig(URI baseUri) {

    private static final String DEFAULT_FRONTEND_BASE_URL = "http://localhost:5173";

    public static FrontendConfig fromEnvironment() {
        return new FrontendConfig(URI.create(readEnv("WHISPERS_FRONTEND_BASE_URL", DEFAULT_FRONTEND_BASE_URL)));
    }

    public String origin() {
        return baseUri().toString().replaceAll("/$", "");
    }

    public boolean secureCookies() {
        return "https".equalsIgnoreCase(baseUri().getScheme());
    }

    private static String readEnv(String key, String defaultValue) {
        String value = System.getenv(key);
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        return value;
    }
}
