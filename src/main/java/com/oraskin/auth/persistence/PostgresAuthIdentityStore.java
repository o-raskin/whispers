package com.oraskin.auth.persistence;

import com.oraskin.auth.persistence.entity.UserIdentityRecord;
import com.oraskin.common.postgres.PostgresConnectionFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Objects;

public final class PostgresAuthIdentityStore implements AuthIdentityStore {

    private final PostgresConnectionFactory connectionFactory;

    public PostgresAuthIdentityStore(PostgresConnectionFactory connectionFactory) {
        this.connectionFactory = Objects.requireNonNull(connectionFactory);
    }

    @Override
    public UserIdentityRecord findByProviderSubject(String provider, String providerSubject) {
        String sql = """
                SELECT user_id, provider, provider_subject, email, picture_url
                FROM auth_external_identities
                WHERE provider = ? AND provider_subject = ?
                """;

        try (Connection connection = connectionFactory.openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, provider);
            statement.setString(2, providerSubject);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return null;
                }
                return new UserIdentityRecord(
                        resultSet.getString("user_id"),
                        resultSet.getString("provider"),
                        resultSet.getString("provider_subject"),
                        resultSet.getString("email"),
                        resultSet.getString("picture_url")
                );
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to find external identity", e);
        }
    }

    @Override
    public UserIdentityRecord findByUserId(String userId) {
        String sql = """
                SELECT user_id, provider, provider_subject, email, picture_url
                FROM auth_external_identities
                WHERE user_id = ?
                ORDER BY last_login_at DESC
                LIMIT 1
                """;

        try (Connection connection = connectionFactory.openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, userId);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return null;
                }
                return new UserIdentityRecord(
                        resultSet.getString("user_id"),
                        resultSet.getString("provider"),
                        resultSet.getString("provider_subject"),
                        resultSet.getString("email"),
                        resultSet.getString("picture_url")
                );
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to find external identity by user id", e);
        }
    }

    @Override
    public UserIdentityRecord findByEmail(String email) {
        String sql = """
                SELECT user_id, provider, provider_subject, email, picture_url
                FROM auth_external_identities
                WHERE lower(email) = lower(?)
                ORDER BY last_login_at DESC
                LIMIT 1
                """;

        try (Connection connection = connectionFactory.openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, email);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return null;
                }
                return new UserIdentityRecord(
                        resultSet.getString("user_id"),
                        resultSet.getString("provider"),
                        resultSet.getString("provider_subject"),
                        resultSet.getString("email"),
                        resultSet.getString("picture_url")
                );
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to find external identity by email", e);
        }
    }

    @Override
    public void save(UserIdentityRecord identityRecord) {
        String sql = """
                INSERT INTO auth_external_identities (
                    user_id, provider, provider_subject, email, picture_url, created_at, last_login_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (provider, provider_subject) DO UPDATE
                    SET user_id = EXCLUDED.user_id,
                        email = EXCLUDED.email,
                        picture_url = EXCLUDED.picture_url,
                        last_login_at = EXCLUDED.last_login_at
                """;

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        try (Connection connection = connectionFactory.openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, identityRecord.userId());
            statement.setString(2, identityRecord.provider());
            statement.setString(3, identityRecord.providerSubject());
            statement.setString(4, identityRecord.email());
            statement.setString(5, identityRecord.pictureUrl());
            statement.setObject(6, now);
            statement.setObject(7, now);
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to save external identity", e);
        }
    }
}
