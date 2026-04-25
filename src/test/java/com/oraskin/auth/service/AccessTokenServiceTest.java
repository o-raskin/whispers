package com.oraskin.auth.service;

import com.oraskin.auth.AuthTestSupport;
import com.oraskin.auth.domain.AccessToken;
import com.oraskin.chat.service.ChatException;
import com.oraskin.common.auth.AuthenticatedUser;
import com.oraskin.common.http.HttpStatus;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static com.oraskin.auth.AuthTestSupport.authConfig;
import static com.oraskin.auth.AuthTestSupport.authenticatedUser;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AccessTokenServiceTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-04-21T10:15:30Z"), ZoneOffset.UTC);

    @Test
    void issueAuthenticateAndRevokeRoundTrip() {
        AuthTestSupport.RecordingAccessTokenStore accessTokenStore = new AuthTestSupport.RecordingAccessTokenStore();
        AccessTokenService service = new AccessTokenService(accessTokenStore, authConfig(), CLOCK);

        AccessToken issuedToken = service.issueToken("alice-id", "google", "subject-1");
        String tokenHash = service.hash(issuedToken.token());
        AuthenticatedUser user = authenticatedUser("alice-id", "alice@example.com", tokenHash);
        accessTokenStore.activate(tokenHash, user);

        assertThat(service.authenticate(issuedToken.token())).isEqualTo(user);
        service.revoke(tokenHash);
        assertThat(accessTokenStore.revokedAtByHash()).containsKey(tokenHash);
    }

    @Test
    void authenticateRejectsUnknownTokens() {
        AccessTokenService service = new AccessTokenService(new AuthTestSupport.RecordingAccessTokenStore(), authConfig(), CLOCK);

        ChatException exception = assertThrows(ChatException.class, () -> service.authenticate("missing"));

        assertThat(exception.status()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(exception).hasMessage("Invalid or expired bearer token");
    }
}
