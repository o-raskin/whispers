package com.oraskin.user.data.persistence;

import com.oraskin.common.postgres.PostgresConfig;
import com.oraskin.common.postgres.PostgresConnectionFactory;
import com.oraskin.user.data.domain.User;
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
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.logging.Logger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DatabaseUserStoreTest {

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
    void pingFindUserAndCreateUserUseMappedRowsAndNormalization() throws Exception {
        Connection connection = mock(Connection.class);
        PreparedStatement pingStatement = mock(PreparedStatement.class);
        PreparedStatement findUserStatement = mock(PreparedStatement.class);
        PreparedStatement createUserStatement = mock(PreparedStatement.class);
        ResultSet pingResultSet = mock(ResultSet.class);
        ResultSet findUserResultSet = mock(ResultSet.class);
        when(connection.prepareStatement(any())).thenReturn(pingStatement, findUserStatement, createUserStatement);
        when(pingStatement.executeQuery()).thenReturn(pingResultSet);
        when(pingResultSet.next()).thenReturn(true);
        when(pingResultSet.getString("user_id")).thenReturn("alice-id");
        when(pingResultSet.getString("username")).thenReturn("alice@example.com");
        when(pingResultSet.getString("first_name")).thenReturn("Alice");
        when(pingResultSet.getString("last_name")).thenReturn("Example");
        when(pingResultSet.getTimestamp("last_ping_time")).thenReturn(Timestamp.valueOf(LocalDateTime.of(2026, 4, 21, 10, 15)));
        when(findUserStatement.executeQuery()).thenReturn(findUserResultSet);
        when(findUserResultSet.next()).thenReturn(true);
        when(findUserResultSet.getString("user_id")).thenReturn("bob-id");
        when(findUserResultSet.getString("username")).thenReturn("bob@example.com");
        when(findUserResultSet.getString("first_name")).thenReturn("Bob");
        when(findUserResultSet.getString("last_name")).thenReturn("Example");
        when(findUserResultSet.getTimestamp("last_ping_time")).thenReturn(Timestamp.valueOf(LocalDateTime.of(2026, 4, 21, 10, 16)));
        DatabaseUserStore store = new DatabaseUserStore(connectionFactoryBackedBy(connection), CLOCK);

        User pinged = store.ping("alice-id");
        User found = store.findUser("bob-id");
        String createdUserId = store.createUser(" Alice@Example.com ", "Alice", "Example");

        assertThat(pinged).isEqualTo(new User("alice-id", "alice@example.com", "Alice", "Example", LocalDateTime.of(2026, 4, 21, 10, 15)));
        assertThat(found).isEqualTo(new User("bob-id", "bob@example.com", "Bob", "Example", LocalDateTime.of(2026, 4, 21, 10, 16)));
        assertThat(createdUserId).isNotBlank();
        verify(pingStatement).setString(2, "alice-id");
        verify(findUserStatement).setString(1, "bob-id");
        verify(createUserStatement).setString(1, "alice@example.com");
        verify(createUserStatement).setString(eq(2), any(String.class));
    }

    @Test
    void findUsersFindByUsernameAndUpdateUserNormalizeUsernames() throws Exception {
        Connection connection = mock(Connection.class);
        PreparedStatement findUsersStatement = mock(PreparedStatement.class);
        PreparedStatement findByUsernameStatement = mock(PreparedStatement.class);
        PreparedStatement updateUserStatement = mock(PreparedStatement.class);
        ResultSet findUsersResultSet = mock(ResultSet.class);
        ResultSet findByUsernameResultSet = mock(ResultSet.class);
        when(connection.prepareStatement(any())).thenReturn(findUsersStatement, findByUsernameStatement, updateUserStatement);
        when(findUsersStatement.executeQuery()).thenReturn(findUsersResultSet);
        when(findUsersResultSet.next()).thenReturn(true, true, false);
        when(findUsersResultSet.getString("user_id")).thenReturn("bob-id", "alice-id");
        when(findUsersResultSet.getString("username")).thenReturn("bob@example.com", "alice@example.com");
        when(findUsersResultSet.getString("first_name")).thenReturn("Bob", "Alice");
        when(findUsersResultSet.getString("last_name")).thenReturn("Example", "Example");
        when(findUsersResultSet.getTimestamp("last_ping_time"))
                .thenReturn(Timestamp.valueOf(LocalDateTime.of(2026, 4, 21, 10, 16)), Timestamp.valueOf(LocalDateTime.of(2026, 4, 21, 10, 15)));
        when(findByUsernameStatement.executeQuery()).thenReturn(findByUsernameResultSet);
        when(findByUsernameResultSet.next()).thenReturn(true);
        when(findByUsernameResultSet.getString("user_id")).thenReturn("alice-id");
        when(findByUsernameResultSet.getString("username")).thenReturn("alice@example.com");
        when(findByUsernameResultSet.getString("first_name")).thenReturn("Alice");
        when(findByUsernameResultSet.getString("last_name")).thenReturn("Example");
        when(findByUsernameResultSet.getTimestamp("last_ping_time")).thenReturn(Timestamp.valueOf(LocalDateTime.of(2026, 4, 21, 10, 15)));
        DatabaseUserStore store = new DatabaseUserStore(connectionFactoryBackedBy(connection), CLOCK);

        List<User> users = store.findUsers(Arrays.asList("alice-id", null, "bob-id", "alice-id"));
        User found = store.findByUsername(" Alice@Example.com ");
        store.updateUser("alice-id", " Alice@Example.com ", "Alice", "Example");

        assertThat(users).extracting(User::userId).containsExactly("alice-id", "bob-id");
        assertThat(found.username()).isEqualTo("alice@example.com");
        verify(findUsersStatement).setString(1, "alice-id");
        verify(findUsersStatement).setString(2, "bob-id");
        verify(findByUsernameStatement).setString(1, "alice@example.com");
        verify(updateUserStatement).setString(1, "alice@example.com");
        verify(updateUserStatement).setString(4, "alice-id");
    }

    private PostgresConnectionFactory connectionFactoryBackedBy(Connection connection) throws Exception {
        String jdbcUrl = "jdbc:mock:user-" + registeredDrivers.size();
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
