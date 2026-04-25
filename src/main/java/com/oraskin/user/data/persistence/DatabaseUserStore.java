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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public final class DatabaseUserStore implements UserStore {

    private final PostgresConnectionFactory connectionFactory;
    private final Clock clock;
    private final UserMapper userMapper;

    public DatabaseUserStore(PostgresConnectionFactory connectionFactory, Clock clock) {
        this.connectionFactory = Objects.requireNonNull(connectionFactory);
        this.clock = Objects.requireNonNull(clock);
        this.userMapper = new UserMapper();
    }

    @Override
    public User ping(String userId) {
        LocalDateTime now = LocalDateTime.now(clock);
        String sql = """
                UPDATE users
                SET last_ping_time = ?
                WHERE user_id = ?
                RETURNING user_id, username, first_name, last_name, last_ping_time
                """;

        try (Connection connection = connectionFactory.openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setTimestamp(1, Timestamp.valueOf(now));
            statement.setString(2, userId);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    throw new IllegalStateException("User not found for ping: " + userId);
                }
                return userMapper.toDomain(mapUserData(resultSet));
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to update ping time", e);
        }
    }

    @Override
    public User findUser(String userId) {
        String sql = """
                SELECT user_id, username, first_name, last_name, last_ping_time
                FROM users
                WHERE user_id = ?
                """;
        try (Connection connection = connectionFactory.openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, userId);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return null;
                }
                return userMapper.toDomain(mapUserData(resultSet));
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to find user by id", e);
        }
    }

    @Override
    public List<User> findUsers(Collection<String> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return List.of();
        }

        List<String> orderedUserIds = userIds.stream()
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (orderedUserIds.isEmpty()) {
            return List.of();
        }

        String placeholders = String.join(", ", java.util.Collections.nCopies(orderedUserIds.size(), "?"));
        String sql = """
                SELECT user_id, username, first_name, last_name, last_ping_time
                FROM users
                WHERE user_id IN (%s)
                """.formatted(placeholders);

        Map<String, User> usersById = new LinkedHashMap<>();
        try (Connection connection = connectionFactory.openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int index = 0; index < orderedUserIds.size(); index++) {
                statement.setString(index + 1, orderedUserIds.get(index));
            }
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    User user = userMapper.toDomain(mapUserData(resultSet));
                    usersById.put(user.userId(), user);
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to find users by ids", e);
        }

        List<User> users = new ArrayList<>();
        for (String userId : orderedUserIds) {
            User user = usersById.get(userId);
            if (user != null) {
                users.add(user);
            }
        }
        return users;
    }

    @Override
    public User findByUsername(String username) {
        String sql = """
                SELECT user_id, username, first_name, last_name, last_ping_time
                FROM users
                WHERE username = ?
                """;
        try (Connection connection = connectionFactory.openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, normalize(username));
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return null;
                }
                return userMapper.toDomain(mapUserData(resultSet));
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to find user by username", e);
        }
    }

    @Override
    public String createUser(String username, String firstName, String lastName) {
        String userId = UUID.randomUUID().toString();
        String sql = """
                INSERT INTO users (username, user_id, first_name, last_name, last_ping_time)
                VALUES (?, ?, ?, ?, NULL)
                """;
        try (Connection connection = connectionFactory.openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, normalize(username));
            statement.setString(2, userId);
            statement.setString(3, firstName);
            statement.setString(4, lastName);
            statement.executeUpdate();
            return userId;
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to create user", e);
        }
    }

    @Override
    public void updateUser(String userId, String username, String firstName, String lastName) {
        String sql = """
                UPDATE users
                SET username = ?, first_name = ?, last_name = ?
                WHERE user_id = ?
                """;
        try (Connection connection = connectionFactory.openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, normalize(username));
            statement.setString(2, firstName);
            statement.setString(3, lastName);
            statement.setString(4, userId);
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to update user", e);
        }
    }

    private UserData mapUserData(ResultSet resultSet) throws SQLException {
        Timestamp timestamp = resultSet.getTimestamp("last_ping_time");
        LocalDateTime lastPingTime = timestamp == null ? null : timestamp.toLocalDateTime();
        return new UserData(
                resultSet.getString("user_id"),
                resultSet.getString("username"),
                resultSet.getString("first_name"),
                resultSet.getString("last_name"),
                lastPingTime
        );
    }

    private String normalize(String username) {
        return username == null ? null : username.trim().toLowerCase();
    }
}
