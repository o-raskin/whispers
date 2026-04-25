package com.oraskin.chat.key.persistence;

import com.oraskin.chat.key.persistence.entity.PrivateChatKeyRecord;
import com.oraskin.chat.key.value.PrivateChatKeyStatus;
import com.oraskin.common.postgres.PostgresConfig;
import com.oraskin.common.postgres.PostgresConnectionFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.DriverPropertyInfo;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.logging.Logger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DatabasePrivateChatKeyStoreTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-04-21T10:15:30Z"), ZoneOffset.UTC);

    private final List<Driver> registeredDrivers = new ArrayList<>();

    @AfterEach
    void tearDown() throws Exception {
        for (Driver driver : registeredDrivers) {
            DriverManager.deregisterDriver(driver);
        }
        registeredDrivers.clear();
    }

    @Test
    void upsertKeyAndFindLatestKeyMapReturnedRows() throws Exception {
        Connection connection = mock(Connection.class);
        PreparedStatement statement = mock(PreparedStatement.class);
        ResultSet resultSet = mock(ResultSet.class);
        when(connection.prepareStatement(any())).thenReturn(statement);
        when(statement.executeQuery()).thenReturn(resultSet);
        when(resultSet.next()).thenReturn(true, true);
        when(resultSet.getString("user_id")).thenReturn("alice-id");
        when(resultSet.getString("key_id")).thenReturn("key-1");
        when(resultSet.getString("public_key")).thenReturn("spki");
        when(resultSet.getString("algorithm")).thenReturn("RSA-OAEP");
        when(resultSet.getString("key_format")).thenReturn("spki");
        when(resultSet.getString("status")).thenReturn("ACTIVE");
        when(resultSet.getObject("created_at", java.time.OffsetDateTime.class)).thenReturn(java.time.OffsetDateTime.parse("2026-04-21T10:15:30Z"));
        when(resultSet.getObject("updated_at", java.time.OffsetDateTime.class)).thenReturn(java.time.OffsetDateTime.parse("2026-04-21T10:15:30Z"));
        DatabasePrivateChatKeyStore store = new DatabasePrivateChatKeyStore(connectionFactoryBackedBy(connection), CLOCK);

        PrivateChatKeyRecord upserted = store.upsertKey("alice-id", "key-1", "spki", "RSA-OAEP", "spki");
        PrivateChatKeyRecord latest = store.findLatestKey("alice-id");

        assertThat(upserted).isEqualTo(new PrivateChatKeyRecord("alice-id", "key-1", "spki", "RSA-OAEP", "spki", PrivateChatKeyStatus.ACTIVE, "2026-04-21T10:15:30Z", "2026-04-21T10:15:30Z"));
        assertThat(latest).isEqualTo(upserted);
        verify(statement, times(2)).setString(1, "alice-id");
        verify(statement).setString(2, "key-1");
        verify(statement).setString(3, "spki");
        verify(statement).setString(4, "RSA-OAEP");
        verify(statement).setString(5, "spki");
        verify(statement).setString(6, "ACTIVE");
        verify(statement).setObject(eq(7), any(java.time.OffsetDateTime.class));
        verify(statement).setObject(eq(8), any(java.time.OffsetDateTime.class));
    }

    @Test
    void findKeyFallsBackToSqlTimestamps() throws Exception {
        Connection connection = mock(Connection.class);
        PreparedStatement statement = mock(PreparedStatement.class);
        ResultSet resultSet = mock(ResultSet.class);
        when(connection.prepareStatement(any())).thenReturn(statement);
        when(statement.executeQuery()).thenReturn(resultSet);
        when(resultSet.next()).thenReturn(true);
        when(resultSet.getString("user_id")).thenReturn("alice-id");
        when(resultSet.getString("key_id")).thenReturn("key-1");
        when(resultSet.getString("public_key")).thenReturn("spki");
        when(resultSet.getString("algorithm")).thenReturn("RSA-OAEP");
        when(resultSet.getString("key_format")).thenReturn("spki");
        when(resultSet.getString("status")).thenReturn("ACTIVE");
        when(resultSet.getObject("created_at", java.time.OffsetDateTime.class)).thenReturn(null);
        when(resultSet.getObject("updated_at", java.time.OffsetDateTime.class)).thenReturn(null);
        when(resultSet.getTimestamp("created_at")).thenReturn(Timestamp.from(Instant.parse("2026-04-21T10:15:30Z")));
        when(resultSet.getTimestamp("updated_at")).thenReturn(Timestamp.from(Instant.parse("2026-04-21T10:16:30Z")));
        DatabasePrivateChatKeyStore store = new DatabasePrivateChatKeyStore(connectionFactoryBackedBy(connection), CLOCK);

        PrivateChatKeyRecord key = store.findKey("alice-id", "key-1");

        assertThat(key).isEqualTo(new PrivateChatKeyRecord("alice-id", "key-1", "spki", "RSA-OAEP", "spki", PrivateChatKeyStatus.ACTIVE, "2026-04-21T10:15:30Z", "2026-04-21T10:16:30Z"));
        verify(statement).setString(1, "alice-id");
        verify(statement).setString(2, "key-1");
        verify(statement).setString(3, "ACTIVE");
    }

    private PostgresConnectionFactory connectionFactoryBackedBy(Connection connection) throws Exception {
        String jdbcUrl = "jdbc:mock:key-" + registeredDrivers.size();
        Driver driver = new SingleConnectionDriver(jdbcUrl, connection);
        DriverManager.registerDriver(driver);
        registeredDrivers.add(driver);
        return new PostgresConnectionFactory(new PostgresConfig(jdbcUrl, "user", "password"));
    }

    private record SingleConnectionDriver(String jdbcUrl, Connection connection) implements Driver {

        @Override
        public Connection connect(String url, Properties info) {
            return acceptsURL(url) ? connection : null;
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
