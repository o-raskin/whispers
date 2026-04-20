package com.oraskin.chat.key.persistence;

import com.oraskin.chat.key.persistence.entity.PrivateChatKeyRecord;
import com.oraskin.chat.key.value.PrivateChatKeyStatus;
import com.oraskin.common.postgres.PostgresConnectionFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Objects;

public final class DatabasePrivateChatKeyStore implements PrivateChatKeyStore {

    private final PostgresConnectionFactory connectionFactory;
    private final Clock clock;

    public DatabasePrivateChatKeyStore(PostgresConnectionFactory connectionFactory, Clock clock) {
        this.connectionFactory = Objects.requireNonNull(connectionFactory);
        this.clock = Objects.requireNonNull(clock);
    }

    @Override
    public PrivateChatKeyRecord upsertKey(String userId, String keyId, String publicKey, String algorithm, String format) {
        String upsertSql = """
                INSERT INTO public_keys (
                    user_id,
                    key_id,
                    public_key,
                    algorithm,
                    key_format,
                    status,
                    created_at,
                    updated_at
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (user_id, key_id) DO UPDATE
                    SET public_key = EXCLUDED.public_key,
                        algorithm = EXCLUDED.algorithm,
                        key_format = EXCLUDED.key_format,
                        status = EXCLUDED.status,
                        updated_at = EXCLUDED.updated_at
                RETURNING
                    user_id,
                    key_id,
                    public_key,
                    algorithm,
                    key_format,
                    status,
                    created_at,
                    updated_at
                """;

        Instant now = Instant.now(clock);
        OffsetDateTime timestamp = OffsetDateTime.ofInstant(now, ZoneOffset.UTC);
        try (Connection connection = connectionFactory.openConnection();
             PreparedStatement statement = connection.prepareStatement(upsertSql)) {
            statement.setString(1, userId);
            statement.setString(2, keyId);
            statement.setString(3, publicKey);
            statement.setString(4, algorithm);
            statement.setString(5, format);
            statement.setString(6, PrivateChatKeyStatus.ACTIVE.name());
            statement.setObject(7, timestamp);
            statement.setObject(8, timestamp);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    throw new IllegalStateException("Public key upsert did not return a row");
                }
                return mapKey(resultSet);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to store public key", e);
        }
    }

    @Override
    public PrivateChatKeyRecord findKey(String userId, String keyId) {
        String sql = """
                SELECT
                    user_id,
                    key_id,
                    public_key,
                    algorithm,
                    key_format,
                    status,
                    created_at,
                    updated_at
                FROM public_keys
                WHERE user_id = ? AND key_id = ? AND status = ?
                LIMIT 1
                """;

        try (Connection connection = connectionFactory.openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, userId);
            statement.setString(2, keyId);
            statement.setString(3, PrivateChatKeyStatus.ACTIVE.name());
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return null;
                }
                return mapKey(resultSet);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to load public key", e);
        }
    }

    @Override
    public PrivateChatKeyRecord findLatestKey(String userId) {
        String sql = """
                SELECT
                    user_id,
                    key_id,
                    public_key,
                    algorithm,
                    key_format,
                    status,
                    created_at,
                    updated_at
                FROM public_keys
                WHERE user_id = ? AND status = ?
                ORDER BY updated_at DESC
                LIMIT 1
                """;

        try (Connection connection = connectionFactory.openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, userId);
            statement.setString(2, PrivateChatKeyStatus.ACTIVE.name());
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return null;
                }
                return mapKey(resultSet);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to load active private chat key", e);
        }
    }

    private PrivateChatKeyRecord mapKey(ResultSet resultSet) throws SQLException {
        return new PrivateChatKeyRecord(
                resultSet.getString("user_id"),
                resultSet.getString("key_id"),
                resultSet.getString("public_key"),
                resultSet.getString("algorithm"),
                resultSet.getString("key_format"),
                PrivateChatKeyStatus.valueOf(resultSet.getString("status")),
                mapTimestamp(resultSet, "created_at"),
                mapTimestamp(resultSet, "updated_at")
        );
    }

    private String mapTimestamp(ResultSet resultSet, String columnName) throws SQLException {
        OffsetDateTime timestamp = resultSet.getObject(columnName, OffsetDateTime.class);
        if (timestamp != null) {
            return timestamp.toInstant().toString();
        }

        Timestamp value = resultSet.getTimestamp(columnName);
        return value == null ? null : value.toInstant().toString();
    }
}
