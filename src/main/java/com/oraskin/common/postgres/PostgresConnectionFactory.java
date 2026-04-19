package com.oraskin.common.postgres;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Objects;

public final class PostgresConnectionFactory {

    private final PostgresConfig config;

    public PostgresConnectionFactory(PostgresConfig config) {
        this.config = Objects.requireNonNull(config);
    }

    public Connection openConnection() throws SQLException {
        return DriverManager.getConnection(config.jdbcUrl(), config.username(), config.password());
    }
}
