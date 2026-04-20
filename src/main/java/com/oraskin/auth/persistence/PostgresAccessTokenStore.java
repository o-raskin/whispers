package com.oraskin.auth.persistence;

import com.oraskin.common.auth.AuthenticatedUser;
import com.oraskin.common.postgres.PostgresConnectionFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Objects;

public final class PostgresAccessTokenStore implements AccessTokenStore {

    private final PostgresConnectionFactory connectionFactory;

    public PostgresAccessTokenStore(PostgresConnectionFactory connectionFactory) {
        this.connectionFactory = Objects.requireNonNull(connectionFactory);
    }

    @Override
    public void createForUser(String tokenHash, String userId, String provider, String providerSubject, Instant issuedAt, Instant expiresAt) {
        String sql = """
                INSERT INTO auth_access_tokens (token_hash, user_id, provider, provider_subject, issued_at, expires_at)
                VALUES (?, ?, ?, ?, ?, ?)
                """;
        try (Connection connection = connectionFactory.openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, tokenHash);
            statement.setString(2, userId);
            statement.setString(3, provider);
            statement.setString(4, providerSubject);
            statement.setObject(5, OffsetDateTime.ofInstant(issuedAt, ZoneOffset.UTC));
            statement.setObject(6, OffsetDateTime.ofInstant(expiresAt, ZoneOffset.UTC));
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to persist access token", e);
        }
    }

    @Override
    public AuthenticatedUser findActiveUserByTokenHash(String tokenHash, Instant now) {
        String sql = """
                UPDATE auth_access_tokens token
                SET last_used_at = ?
                FROM users app_user
                WHERE token.token_hash = ?
                  AND token.revoked_at IS NULL
                  AND token.expires_at > ?
                  AND app_user.user_id = token.user_id
                RETURNING app_user.user_id, app_user.username, token.provider, token.provider_subject,
                          app_user.username AS email, NULL AS display_name, app_user.first_name, app_user.last_name,
                          (
                              SELECT identity.picture_url
                              FROM auth_external_identities identity
                              WHERE identity.user_id = token.user_id
                                AND identity.provider = token.provider
                                AND identity.provider_subject = token.provider_subject
                          ) AS picture_url,
                          token.token_hash
                """;

        try (Connection connection = connectionFactory.openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            OffsetDateTime nowValue = OffsetDateTime.ofInstant(now, ZoneOffset.UTC);
            statement.setObject(1, nowValue);
            statement.setString(2, tokenHash);
            statement.setObject(3, nowValue);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return null;
                }
                return new AuthenticatedUser(
                        resultSet.getString("user_id"),
                        resultSet.getString("username"),
                        resultSet.getString("provider"),
                        resultSet.getString("provider_subject"),
                        resultSet.getString("email"),
                        resultSet.getString("display_name"),
                        resultSet.getString("first_name"),
                        resultSet.getString("last_name"),
                        resultSet.getString("picture_url"),
                        resultSet.getString("token_hash")
                );
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to resolve access token", e);
        }
    }

    @Override
    public void revoke(String tokenHash, Instant revokedAt) {
        String sql = """
                UPDATE auth_access_tokens
                SET revoked_at = ?
                WHERE token_hash = ? AND revoked_at IS NULL
                """;
        try (Connection connection = connectionFactory.openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, OffsetDateTime.ofInstant(revokedAt, ZoneOffset.UTC));
            statement.setString(2, tokenHash);
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to revoke access token", e);
        }
    }
}
