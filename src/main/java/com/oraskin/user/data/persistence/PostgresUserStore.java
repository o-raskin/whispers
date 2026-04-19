package com.oraskin.user.data.persistence;

import com.oraskin.common.postgres.PostgresConnectionFactory;
import com.oraskin.user.data.domain.User;
import com.oraskin.user.data.mapper.UserMapper;
import com.oraskin.user.data.persistence.entity.UserData;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

public final class PostgresUserStore implements UserStore {

    private final PostgresConnectionFactory connectionFactory;
    private final Clock clock;
    private final UserMapper userMapper;

    public PostgresUserStore(PostgresConnectionFactory connectionFactory, Clock clock) {
        this.connectionFactory = Objects.requireNonNull(connectionFactory);
        this.clock = Objects.requireNonNull(clock);
        this.userMapper = new UserMapper();
    }

    @Override
    public void remember(String username) {
        String sql = """
                INSERT INTO users (username, first_name, last_name, last_ping_time)
                VALUES (?, NULL, NULL, NULL)
                ON CONFLICT (username) DO NOTHING
                """;

        try (Connection connection = connectionFactory.openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, username);
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to remember user", e);
        }
    }

    @Override
    public User ping(String username) {
        LocalDateTime now = LocalDateTime.now(clock);
        String sql = """
                INSERT INTO users (username, first_name, last_name, last_ping_time)
                VALUES (?, NULL, NULL, ?)
                ON CONFLICT (username) DO UPDATE
                    SET last_ping_time = EXCLUDED.last_ping_time
                RETURNING username, first_name, last_name, last_ping_time
                """;

        try (Connection connection = connectionFactory.openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, username);
            statement.setTimestamp(2, Timestamp.valueOf(now));

            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    throw new IllegalStateException("Ping update did not return a row");
                }
                return userMapper.toDomain(mapUserData(resultSet));
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to update ping time", e);
        }
    }

    @Override
    public User findUser(String username) {
        remember(username);

        String sql = """
                SELECT username, first_name, last_name, last_ping_time
                FROM users
                WHERE username = ?
                """;

        try (Connection connection = connectionFactory.openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, username);

            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    throw new IllegalStateException("User was not found after remember: " + username);
                }
                return userMapper.toDomain(mapUserData(resultSet));
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to find user", e);
        }
    }

    @Override
    public List<User> findUsers(Collection<String> usernames) {
        List<User> users = new ArrayList<>();
        for (String username : usernames) {
            users.add(findUser(username));
        }
        return users;
    }

    private UserData mapUserData(ResultSet resultSet) throws SQLException {
        Timestamp timestamp = resultSet.getTimestamp("last_ping_time");
        LocalDateTime lastPingTime = timestamp == null ? null : timestamp.toLocalDateTime();
        return new UserData(
                resultSet.getString("username"),
                resultSet.getString("first_name"),
                resultSet.getString("last_name"),
                lastPingTime
        );
    }
}
