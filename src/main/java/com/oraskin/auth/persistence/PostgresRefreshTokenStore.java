package com.oraskin.auth.persistence;

import com.oraskin.auth.persistence.entity.RefreshSessionRecord;
import com.oraskin.common.postgres.PostgresConnectionFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Objects;

public final class PostgresRefreshTokenStore implements RefreshTokenStore {

    private final PostgresConnectionFactory connectionFactory;

    public PostgresRefreshTokenStore(PostgresConnectionFactory connectionFactory) {
        this.connectionFactory = Objects.requireNonNull(connectionFactory);
    }

    @Override
    public void create(String sessionId, String tokenHash, String userId, String provider, String providerSubject, Instant issuedAt, Instant expiresAt) {
        String sql = """
                INSERT INTO auth_refresh_tokens (session_id, token_hash, user_id, provider, provider_subject, issued_at, expires_at)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """;
        try (Connection connection = connectionFactory.openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, sessionId);
            statement.setString(2, tokenHash);
            statement.setString(3, userId);
            statement.setString(4, provider);
            statement.setString(5, providerSubject);
            statement.setObject(6, OffsetDateTime.ofInstant(issuedAt, ZoneOffset.UTC));
            statement.setObject(7, OffsetDateTime.ofInstant(expiresAt, ZoneOffset.UTC));
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to persist refresh token", e);
        }
    }

    @Override
    public RefreshSessionRecord findActiveByTokenHash(String tokenHash, Instant now) {
        String sql = """
                SELECT session_id, user_id, provider, provider_subject, expires_at, revoked_at
                FROM auth_refresh_tokens
                WHERE token_hash = ? AND revoked_at IS NULL AND expires_at > ?
                """;
        try (Connection connection = connectionFactory.openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, tokenHash);
            statement.setObject(2, OffsetDateTime.ofInstant(now, ZoneOffset.UTC));
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return null;
                }
                OffsetDateTime revokedAt = resultSet.getObject("revoked_at", OffsetDateTime.class);
                return new RefreshSessionRecord(
                        resultSet.getString("session_id"),
                        resultSet.getString("user_id"),
                        resultSet.getString("provider"),
                        resultSet.getString("provider_subject"),
                        resultSet.getObject("expires_at", OffsetDateTime.class).toInstant(),
                        revokedAt == null ? null : revokedAt.toInstant()
                );
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to resolve refresh token", e);
        }
    }

    @Override
    public void revokeSession(String sessionId, Instant revokedAt) {
        String sql = """
                UPDATE auth_refresh_tokens
                SET revoked_at = ?
                WHERE session_id = ? AND revoked_at IS NULL
                """;
        try (Connection connection = connectionFactory.openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, OffsetDateTime.ofInstant(revokedAt, ZoneOffset.UTC));
            statement.setString(2, sessionId);
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to revoke refresh session", e);
        }
    }
}
