package com.oraskin.auth.persistence;

import com.oraskin.auth.persistence.entity.RefreshSessionRecord;
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
import java.time.Instant;
import java.time.OffsetDateTime;
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

class PostgresRefreshTokenStoreTest {

    private final List<Driver> registeredDrivers = new ArrayList<>();

    @AfterEach
    void tearDown() throws Exception {
        for (Driver driver : registeredDrivers) {
            DriverManager.deregisterDriver(driver);
        }
        registeredDrivers.clear();
    }

    @Test
    void createFindAndRevokeRefreshSessions() throws Exception {
        Connection connection = mock(Connection.class);
        PreparedStatement statement = mock(PreparedStatement.class);
        ResultSet resultSet = mock(ResultSet.class);
        when(connection.prepareStatement(any())).thenReturn(statement);
        when(statement.executeQuery()).thenReturn(resultSet);
        when(resultSet.next()).thenReturn(true);
        when(resultSet.getString("session_id")).thenReturn("session-1");
        when(resultSet.getString("user_id")).thenReturn("alice-id");
        when(resultSet.getString("provider")).thenReturn("google");
        when(resultSet.getString("provider_subject")).thenReturn("subject-1");
        when(resultSet.getObject("expires_at", OffsetDateTime.class)).thenReturn(OffsetDateTime.parse("2026-04-21T10:25:30Z"));
        when(resultSet.getObject("revoked_at", OffsetDateTime.class)).thenReturn(null);
        PostgresRefreshTokenStore store = new PostgresRefreshTokenStore(connectionFactoryBackedBy(connection));
        Instant issuedAt = Instant.parse("2026-04-21T10:15:30Z");

        store.create("session-1", "token-hash", "alice-id", "google", "subject-1", issuedAt, issuedAt.plusSeconds(600));
        RefreshSessionRecord sessionRecord = store.findActiveByTokenHash("token-hash", issuedAt.plusSeconds(60));
        store.revokeSession("session-1", issuedAt.plusSeconds(120));

        assertThat(sessionRecord)
                .extracting(RefreshSessionRecord::sessionId, RefreshSessionRecord::userId, RefreshSessionRecord::provider, RefreshSessionRecord::providerSubject)
                .containsExactly("session-1", "alice-id", "google", "subject-1");
        verify(statement).setString(1, "session-1");
        verify(statement).setString(2, "token-hash");
        verify(statement).setString(3, "alice-id");
        verify(statement).setString(4, "google");
        verify(statement).setString(5, "subject-1");
        verify(statement).setObject(eq(6), any(OffsetDateTime.class));
        verify(statement).setObject(eq(7), any(OffsetDateTime.class));
        verify(statement, times(2)).executeUpdate();
    }

    @Test
    void findActiveByTokenHashReturnsNullWhenSessionIsMissing() throws Exception {
        Connection connection = mock(Connection.class);
        PreparedStatement statement = mock(PreparedStatement.class);
        ResultSet resultSet = mock(ResultSet.class);
        when(connection.prepareStatement(any())).thenReturn(statement);
        when(statement.executeQuery()).thenReturn(resultSet);
        when(resultSet.next()).thenReturn(false);
        PostgresRefreshTokenStore store = new PostgresRefreshTokenStore(connectionFactoryBackedBy(connection));

        assertThat(store.findActiveByTokenHash("missing", Instant.parse("2026-04-21T10:15:30Z"))).isNull();
    }

    private PostgresConnectionFactory connectionFactoryBackedBy(Connection connection) throws Exception {
        String jdbcUrl = "jdbc:mock:refresh-" + registeredDrivers.size();
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
