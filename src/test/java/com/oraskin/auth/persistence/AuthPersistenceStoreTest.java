package com.oraskin.auth.persistence;

import com.oraskin.auth.persistence.entity.RefreshSessionRecord;
import com.oraskin.auth.persistence.entity.UserIdentityRecord;
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

class AuthPersistenceStoreTest {

    private final List<Driver> registeredDrivers = new ArrayList<>();

    @AfterEach
    void tearDown() throws Exception {
        for (Driver driver : registeredDrivers) {
            DriverManager.deregisterDriver(driver);
        }
        registeredDrivers.clear();
    }

    @Test
    void postgresAccessTokenStorePersistsIssuesAndRevokesTokens() throws Exception {
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
    void postgresAuthIdentityStoreMapsLookupMethodsAndSave() throws Exception {
        Connection connection = mock(Connection.class);
        PreparedStatement statement = mock(PreparedStatement.class);
        ResultSet resultSet = mock(ResultSet.class);
        when(connection.prepareStatement(any())).thenReturn(statement);
        when(statement.executeQuery()).thenReturn(resultSet);
        when(resultSet.next()).thenReturn(true, true, true);
        when(resultSet.getString("user_id")).thenReturn("alice-id");
        when(resultSet.getString("provider")).thenReturn("google");
        when(resultSet.getString("provider_subject")).thenReturn("subject-1");
        when(resultSet.getString("email")).thenReturn("alice@example.com");
        when(resultSet.getString("picture_url")).thenReturn("https://example.com/alice.png");
        PostgresAuthIdentityStore store = new PostgresAuthIdentityStore(connectionFactoryBackedBy(connection));

        UserIdentityRecord byProvider = store.findByProviderSubject("google", "subject-1");
        UserIdentityRecord byUserId = store.findByUserId("alice-id");
        UserIdentityRecord byEmail = store.findByEmail("alice@example.com");
        store.save(new UserIdentityRecord("alice-id", "google", "subject-1", "alice@example.com", "https://example.com/alice.png"));

        assertThat(byProvider).isEqualTo(new UserIdentityRecord("alice-id", "google", "subject-1", "alice@example.com", "https://example.com/alice.png"));
        assertThat(byUserId).isEqualTo(byProvider);
        assertThat(byEmail).isEqualTo(byProvider);
        verify(statement).setString(1, "google");
        verify(statement).setString(2, "subject-1");
        verify(statement).executeUpdate();
    }

    @Test
    void postgresRefreshTokenStorePersistsResolvesAndRevokesSessions() throws Exception {
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
    void postgresStoresReturnNullWhenLookupsDoNotFindRows() throws Exception {
        Connection accessConnection = mock(Connection.class);
        PreparedStatement accessStatement = mock(PreparedStatement.class);
        ResultSet accessResultSet = mock(ResultSet.class);
        when(accessConnection.prepareStatement(any())).thenReturn(accessStatement);
        when(accessStatement.executeQuery()).thenReturn(accessResultSet);
        when(accessResultSet.next()).thenReturn(false);
        PostgresAccessTokenStore accessTokenStore = new PostgresAccessTokenStore(connectionFactoryBackedBy(accessConnection));

        assertThat(accessTokenStore.findActiveUserByTokenHash("missing", Instant.parse("2026-04-21T10:15:30Z"))).isNull();

        Connection identityConnection = mock(Connection.class);
        PreparedStatement identityStatement = mock(PreparedStatement.class);
        ResultSet identityResultSet = mock(ResultSet.class);
        when(identityConnection.prepareStatement(any())).thenReturn(identityStatement);
        when(identityStatement.executeQuery()).thenReturn(identityResultSet);
        when(identityResultSet.next()).thenReturn(false, false, false);
        PostgresAuthIdentityStore authIdentityStore = new PostgresAuthIdentityStore(connectionFactoryBackedBy(identityConnection));

        assertThat(authIdentityStore.findByProviderSubject("google", "missing")).isNull();
        assertThat(authIdentityStore.findByUserId("missing")).isNull();
        assertThat(authIdentityStore.findByEmail("missing@example.com")).isNull();

        Connection refreshConnection = mock(Connection.class);
        PreparedStatement refreshStatement = mock(PreparedStatement.class);
        ResultSet refreshResultSet = mock(ResultSet.class);
        when(refreshConnection.prepareStatement(any())).thenReturn(refreshStatement);
        when(refreshStatement.executeQuery()).thenReturn(refreshResultSet);
        when(refreshResultSet.next()).thenReturn(false);
        PostgresRefreshTokenStore refreshTokenStore = new PostgresRefreshTokenStore(connectionFactoryBackedBy(refreshConnection));

        assertThat(refreshTokenStore.findActiveByTokenHash("missing", Instant.parse("2026-04-21T10:15:30Z"))).isNull();
    }

    private PostgresConnectionFactory connectionFactoryBackedBy(Connection connection) throws Exception {
        String jdbcUrl = "jdbc:mock:auth-" + registeredDrivers.size();
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
