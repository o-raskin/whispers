package com.oraskin.auth.persistence;

import com.oraskin.common.auth.AuthenticatedUser;
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

class PostgresAccessTokenStoreTest {

    private final List<Driver> registeredDrivers = new ArrayList<>();

    @AfterEach
    void tearDown() throws Exception {
        for (Driver driver : registeredDrivers) {
            DriverManager.deregisterDriver(driver);
        }
        registeredDrivers.clear();
    }

    @Test
    void createFindAndRevokeTokens() throws Exception {
        Connection connection = mock(Connection.class);
        PreparedStatement statement = mock(PreparedStatement.class);
        ResultSet resultSet = mock(ResultSet.class);
        when(connection.prepareStatement(any())).thenReturn(statement);
        when(statement.executeQuery()).thenReturn(resultSet);
        when(resultSet.next()).thenReturn(true);
        when(resultSet.getString("user_id")).thenReturn("alice-id");
        when(resultSet.getString("username")).thenReturn("alice@example.com");
        when(resultSet.getString("provider")).thenReturn("google");
        when(resultSet.getString("provider_subject")).thenReturn("subject-1");
        when(resultSet.getString("email")).thenReturn("alice@example.com");
        when(resultSet.getString("display_name")).thenReturn("Alice Example");
        when(resultSet.getString("first_name")).thenReturn("Alice");
        when(resultSet.getString("last_name")).thenReturn("Example");
        when(resultSet.getString("picture_url")).thenReturn("https://example.com/alice.png");
        when(resultSet.getString("token_hash")).thenReturn("token-hash");
        PostgresAccessTokenStore store = new PostgresAccessTokenStore(connectionFactoryBackedBy(connection));
        Instant issuedAt = Instant.parse("2026-04-21T10:15:30Z");

        store.createForUser("token-hash", "alice-id", "google", "subject-1", issuedAt, issuedAt.plusSeconds(300));
        AuthenticatedUser authenticatedUser = store.findActiveUserByTokenHash("token-hash", issuedAt.plusSeconds(60));
        store.revoke("token-hash", issuedAt.plusSeconds(120));

        assertThat(authenticatedUser)
                .extracting(AuthenticatedUser::userId, AuthenticatedUser::username, AuthenticatedUser::provider, AuthenticatedUser::providerSubject, AuthenticatedUser::accessTokenHash)
                .containsExactly("alice-id", "alice@example.com", "google", "subject-1", "token-hash");
        verify(statement).setString(1, "token-hash");
        verify(statement).setString(2, "alice-id");
        verify(statement).setString(3, "google");
        verify(statement).setString(4, "subject-1");
        verify(statement).setObject(eq(5), any(OffsetDateTime.class));
        verify(statement).setObject(eq(6), any(OffsetDateTime.class));
        verify(statement, times(2)).executeUpdate();
    }

    @Test
    void findActiveUserReturnsNullWhenTokenDoesNotExist() throws Exception {
        Connection connection = mock(Connection.class);
        PreparedStatement statement = mock(PreparedStatement.class);
        ResultSet resultSet = mock(ResultSet.class);
        when(connection.prepareStatement(any())).thenReturn(statement);
        when(statement.executeQuery()).thenReturn(resultSet);
        when(resultSet.next()).thenReturn(false);
        PostgresAccessTokenStore store = new PostgresAccessTokenStore(connectionFactoryBackedBy(connection));

        assertThat(store.findActiveUserByTokenHash("missing", Instant.parse("2026-04-21T10:15:30Z"))).isNull();
    }

    private PostgresConnectionFactory connectionFactoryBackedBy(Connection connection) throws Exception {
        String jdbcUrl = "jdbc:mock:access-" + registeredDrivers.size();
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
