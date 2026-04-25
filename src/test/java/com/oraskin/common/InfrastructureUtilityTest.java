package com.oraskin.common;

import com.oraskin.App;
import com.oraskin.common.json.JsonCodec;
import com.oraskin.common.postgres.LiquibaseMigrationRunner;
import com.oraskin.common.postgres.PostgresConfig;
import com.oraskin.common.postgres.PostgresConnectionFactory;
import com.oraskin.common.websocket.FrameType;
import com.oraskin.common.websocket.HeaderUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.DriverPropertyInfo;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.logging.Logger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class InfrastructureUtilityTest {

    private final List<Driver> registeredDrivers = new ArrayList<>();

    @AfterEach
    void tearDown() throws Exception {
        for (Driver driver : registeredDrivers) {
            DriverManager.deregisterDriver(driver);
        }
        registeredDrivers.clear();
    }

    @Test
    void jsonCodecSerializesAndDeserializesPayloads() throws Exception {
        String payload = JsonCodec.write(Map.of("type", "presence", "count", 2));

        assertThat(payload).contains("\"type\":\"presence\"");
        assertThat(JsonCodec.read(payload, Map.class)).containsEntry("type", "presence");
    }

    @Test
    void jsonCodecRejectsInvalidJsonAndWrapsSerializationFailures() {
        assertThatThrownBy(() -> JsonCodec.read("{", Map.class)).isInstanceOf(java.io.IOException.class);

        Map<String, Object> recursivePayload = new LinkedHashMap<>();
        recursivePayload.put("self", recursivePayload);

        assertThatThrownBy(() -> JsonCodec.write(recursivePayload))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Cannot serialize JSON payload.");
    }

    @Test
    void headerUtilEncodesSmallMediumAndLargePayloadLengths() {
        assertThat(HeaderUtil.buildHeader(FrameType.TEXT, 5)).containsExactly((byte) 0x81, (byte) 0x05);
        assertThat(HeaderUtil.buildHeader(FrameType.TEXT, 126)).containsExactly((byte) 0x81, (byte) 0x7E, (byte) 0x00, (byte) 0x7E);
        assertThat(HeaderUtil.buildHeader(FrameType.TEXT, 65_536))
                .startsWith((byte) 0x81, (byte) 0x7F)
                .hasSize(10);
    }

    @Test
    void postgresConnectionFactoryUsesConfiguredJdbcCredentials() throws Exception {
        Connection connection = mock(Connection.class);
        PostgresConnectionFactory factory = connectionFactoryBackedBy(connection, false);

        assertThat(factory.openConnection()).isSameAs(connection);
    }

    @Test
    void liquibaseMigrationRunnerWrapsSqlFailures() throws Exception {
        PostgresConnectionFactory connectionFactory = connectionFactoryBackedBy(null, true);

        assertThatThrownBy(new LiquibaseMigrationRunner(connectionFactory)::runMigrations)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Failed to apply Liquibase changelog")
                .hasCauseInstanceOf(SQLException.class);
    }

    @Test
    void appRejectsNonNumericPortArgument() {
        assertThatThrownBy(() -> App.main(new String[]{"invalid"})).isInstanceOf(NumberFormatException.class);
    }

    private PostgresConnectionFactory connectionFactoryBackedBy(Connection connection, boolean fail) throws Exception {
        String jdbcUrl = "jdbc:mock:infra-" + registeredDrivers.size();
        Driver driver = new Driver() {
            @Override
            public Connection connect(String url, Properties info) throws SQLException {
                if (!acceptsURL(url)) {
                    return null;
                }
                if (fail) {
                    throw new SQLException("db down");
                }
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
        };
        DriverManager.registerDriver(driver);
        registeredDrivers.add(driver);
        return new PostgresConnectionFactory(new PostgresConfig(jdbcUrl, "user", "password"));
    }
}
