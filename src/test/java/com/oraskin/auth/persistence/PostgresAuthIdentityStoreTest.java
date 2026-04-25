package com.oraskin.auth.persistence;

import com.oraskin.auth.persistence.entity.UserIdentityRecord;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.logging.Logger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PostgresAuthIdentityStoreTest {

    private final List<Driver> registeredDrivers = new ArrayList<>();

    @AfterEach
    void tearDown() throws Exception {
        for (Driver driver : registeredDrivers) {
            DriverManager.deregisterDriver(driver);
        }
        registeredDrivers.clear();
    }

    @Test
    void lookupMethodsAndSaveMapRows() throws Exception {
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
    void lookupMethodsReturnNullWhenRowsAreMissing() throws Exception {
        Connection connection = mock(Connection.class);
        PreparedStatement statement = mock(PreparedStatement.class);
        ResultSet resultSet = mock(ResultSet.class);
        when(connection.prepareStatement(any())).thenReturn(statement);
        when(statement.executeQuery()).thenReturn(resultSet);
        when(resultSet.next()).thenReturn(false, false, false);
        PostgresAuthIdentityStore store = new PostgresAuthIdentityStore(connectionFactoryBackedBy(connection));

        assertThat(store.findByProviderSubject("google", "missing")).isNull();
        assertThat(store.findByUserId("missing")).isNull();
        assertThat(store.findByEmail("missing@example.com")).isNull();
    }

    private PostgresConnectionFactory connectionFactoryBackedBy(Connection connection) throws Exception {
        String jdbcUrl = "jdbc:mock:identity-" + registeredDrivers.size();
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
