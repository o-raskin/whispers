package com.oraskin.common.postgres;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.DriverPropertyInfo;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.logging.Logger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class PostgresConnectionFactoryTest {

    private final List<Driver> registeredDrivers = new ArrayList<>();

    @AfterEach
    void tearDown() throws Exception {
        for (Driver driver : registeredDrivers) {
            DriverManager.deregisterDriver(driver);
        }
        registeredDrivers.clear();
    }

    @Test
    void openConnectionUsesConfiguredJdbcUrlUsernameAndPassword() throws Exception {
        Connection connection = mock(Connection.class);
        String jdbcUrl = "jdbc:mock:connection-" + registeredDrivers.size();
        RecordingDriver driver = new RecordingDriver(jdbcUrl, connection);
        DriverManager.registerDriver(driver);
        registeredDrivers.add(driver);
        PostgresConnectionFactory factory = new PostgresConnectionFactory(new PostgresConfig(jdbcUrl, "db-user", "db-password"));

        Connection openedConnection = factory.openConnection();

        assertThat(openedConnection).isSameAs(connection);
        assertThat(driver.lastUrl).isEqualTo(jdbcUrl);
        assertThat(driver.lastInfo).containsEntry("user", "db-user");
        assertThat(driver.lastInfo).containsEntry("password", "db-password");
    }

    private static final class RecordingDriver implements Driver {

        private final String jdbcUrl;
        private final Connection connection;
        private Properties lastInfo;
        private String lastUrl;

        private RecordingDriver(String jdbcUrl, Connection connection) {
            this.jdbcUrl = jdbcUrl;
            this.connection = connection;
        }

        @Override
        public Connection connect(String url, Properties info) {
            if (!acceptsURL(url)) {
                return null;
            }
            lastUrl = url;
            lastInfo = info;
            return connection;
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
    }
}
