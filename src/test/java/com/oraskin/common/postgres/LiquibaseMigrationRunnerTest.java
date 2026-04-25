package com.oraskin.common.postgres;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.DriverPropertyInfo;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.logging.Logger;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LiquibaseMigrationRunnerTest {

    private final List<Driver> registeredDrivers = new ArrayList<>();

    @AfterEach
    void tearDown() throws Exception {
        for (Driver driver : registeredDrivers) {
            DriverManager.deregisterDriver(driver);
        }
        registeredDrivers.clear();
    }

    @Test
    void runMigrationsWrapsSqlFailures() throws Exception {
        String jdbcUrl = "jdbc:mock:liquibase-" + registeredDrivers.size();
        Driver driver = new Driver() {
            @Override
            public Connection connect(String url, Properties info) throws SQLException {
                if (!acceptsURL(url)) {
                    return null;
                }
                throw new SQLException("db down");
            }

            @Override
            public boolean acceptsURL(String url) {
                return jdbcUrl.equals(url);
            }

            @Override
            public DriverPropertyInfo[] getPropertyInfo(String url, Properties info) {
                return new DriverPropertyInfo[0];
            }

            @Override
            public int getMajorVersion() {
                return 1;
            }

            @Override
            public int getMinorVersion() {
                return 0;
            }

            @Override
            public boolean jdbcCompliant() {
                return false;
            }

            @Override
            public Logger getParentLogger() {
                return Logger.getGlobal();
            }
        };
        DriverManager.registerDriver(driver);
        registeredDrivers.add(driver);
        PostgresConnectionFactory connectionFactory = new PostgresConnectionFactory(new PostgresConfig(jdbcUrl, "user", "password"));

        assertThatThrownBy(new LiquibaseMigrationRunner(connectionFactory)::runMigrations)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Failed to apply Liquibase changelog")
                .hasCauseInstanceOf(SQLException.class);
    }
}
