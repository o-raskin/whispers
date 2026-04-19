package com.oraskin.common.postgres;

public record PostgresConfig(String jdbcUrl, String username, String password) {

    private static final String DEFAULT_JDBC_URL = "jdbc:postgresql://localhost:5432/whispers";
    private static final String DEFAULT_USERNAME = "whispers";
    private static final String DEFAULT_PASSWORD = "whispers";

    public static PostgresConfig fromEnvironment() {
        String jdbcUrl = readEnv("WHISPERS_DB_URL", DEFAULT_JDBC_URL);
        String username = readEnv("WHISPERS_DB_USER", DEFAULT_USERNAME);
        String password = readEnv("WHISPERS_DB_PASSWORD", DEFAULT_PASSWORD);
        return new PostgresConfig(jdbcUrl, username, password);
    }

    private static String readEnv(String key, String defaultValue) {
        String value = System.getenv(key);
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        return value;
    }
}
