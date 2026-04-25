package com.oraskin.auth.service;

import com.oraskin.auth.AuthTestSupport;
import com.oraskin.auth.domain.RefreshToken;
import com.oraskin.auth.persistence.entity.RefreshSessionRecord;
import com.oraskin.chat.service.ChatException;
import com.oraskin.common.http.HttpStatus;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static com.oraskin.auth.AuthTestSupport.authConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RefreshTokenServiceTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-04-21T10:15:30Z"), ZoneOffset.UTC);

    @Test
    void issueAuthenticateAndRevokeRefreshSessions() {
        AuthTestSupport.RecordingRefreshTokenStore refreshTokenStore = new AuthTestSupport.RecordingRefreshTokenStore();
        AccessTokenService accessTokenService = new AccessTokenService(new AuthTestSupport.RecordingAccessTokenStore(), authConfig(), CLOCK);
        RefreshTokenService service = new RefreshTokenService(refreshTokenStore, accessTokenService, authConfig(), CLOCK);

        RefreshToken refreshToken = service.issue("alice-id", "google", "subject-1");
        String tokenHash = accessTokenService.hash(refreshToken.token());

        RefreshSessionRecord record = service.authenticate(refreshToken.token());
        service.revoke(record);

        assertThat(record.userId()).isEqualTo("alice-id");
        assertThat(refreshTokenStore.createdSessionsById().values()).anySatisfy(session -> assertThat(session.tokenHash()).isEqualTo(tokenHash));
        assertThat(refreshTokenStore.revokedAtBySessionId()).containsKey(record.sessionId());
    }

    @Test
    void authenticateRejectsMissingTokens() {
        AccessTokenService accessTokenService = new AccessTokenService(new AuthTestSupport.RecordingAccessTokenStore(), authConfig(), CLOCK);
        RefreshTokenService service = new RefreshTokenService(new AuthTestSupport.RecordingRefreshTokenStore(), accessTokenService, authConfig(), CLOCK);

        ChatException exception = assertThrows(ChatException.class, () -> service.authenticate(" "));

        assertThat(exception.status()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(exception).hasMessage("Missing refresh token");
    }
}
