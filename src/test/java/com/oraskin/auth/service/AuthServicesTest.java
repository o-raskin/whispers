package com.oraskin.auth.service;

import com.oraskin.auth.AuthTestSupport;
import com.oraskin.auth.config.AuthConfig;
import com.oraskin.auth.domain.AccessToken;
import com.oraskin.auth.domain.ExternalUserIdentity;
import com.oraskin.auth.domain.LoginResponse;
import com.oraskin.auth.domain.RefreshToken;
import com.oraskin.auth.persistence.entity.RefreshSessionRecord;
import com.oraskin.auth.persistence.entity.UserIdentityRecord;
import com.oraskin.chat.service.ChatException;
import com.oraskin.common.auth.AuthenticatedUser;
import com.oraskin.common.http.HttpRequest;
import com.oraskin.common.http.HttpStatus;
import com.oraskin.common.mvc.ControllerResult;
import com.oraskin.user.data.domain.User;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static com.oraskin.auth.AuthTestSupport.authConfig;
import static com.oraskin.auth.AuthTestSupport.authenticatedUser;
import static com.oraskin.auth.AuthTestSupport.externalIdentity;
import static com.oraskin.auth.AuthTestSupport.oauthLoginRequest;
import static com.oraskin.auth.AuthTestSupport.request;
import static com.oraskin.auth.AuthTestSupport.user;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuthServicesTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-04-21T10:15:30Z"), ZoneOffset.UTC);

    @Test
    void accessTokenServiceIssuesHashesAuthenticatesAndRevokesTokens() {
        AuthTestSupport.RecordingAccessTokenStore accessTokenStore = new AuthTestSupport.RecordingAccessTokenStore();
        AccessTokenService accessTokenService = new AccessTokenService(accessTokenStore, authConfig(), CLOCK);

        AccessToken issuedToken = accessTokenService.issueToken("alice-id", "google", "alice-subject");

        assertEquals("Bearer", issuedToken.tokenType());
        assertEquals(authConfig().accessTokenTtl().toSeconds(), issuedToken.expiresInSeconds());
        assertEquals(Instant.now(CLOCK).plus(authConfig().accessTokenTtl()), issuedToken.expiresAt());

        String tokenHash = accessTokenService.hash(issuedToken.token());
        AuthTestSupport.StoredAccessToken storedToken = accessTokenStore.createdToken(tokenHash);
        assertNotNull(storedToken);
        assertEquals("alice-id", storedToken.userId());
        assertEquals("google", storedToken.provider());
        assertEquals(Instant.now(CLOCK), storedToken.issuedAt());

        AuthenticatedUser authenticatedUser = authenticatedUser("alice-id", "alice@example.com", tokenHash);
        accessTokenStore.activate(tokenHash, authenticatedUser);
        assertEquals(authenticatedUser, accessTokenService.authenticate(issuedToken.token()));

        accessTokenService.revoke(tokenHash);
        assertEquals(Instant.now(CLOCK), accessTokenStore.revokedAtByHash().get(tokenHash));
    }

    @Test
    void accessTokenServiceRejectsUnknownTokens() {
        AccessTokenService accessTokenService = new AccessTokenService(
                new AuthTestSupport.RecordingAccessTokenStore(),
                authConfig(),
                CLOCK
        );

        ChatException exception = assertThrows(ChatException.class, () -> accessTokenService.authenticate("missing-token"));

        assertEquals(HttpStatus.UNAUTHORIZED, exception.status());
        assertEquals("Invalid or expired bearer token", exception.getMessage());
    }

    @Test
    void refreshTokenServiceIssuesAuthenticatesAndRevokesSessions() {
        AuthTestSupport.RecordingRefreshTokenStore refreshTokenStore = new AuthTestSupport.RecordingRefreshTokenStore();
        AccessTokenService accessTokenService = new AccessTokenService(
                new AuthTestSupport.RecordingAccessTokenStore(),
                authConfig(),
                CLOCK
        );
        RefreshTokenService refreshTokenService = new RefreshTokenService(refreshTokenStore, accessTokenService, authConfig(), CLOCK);

        RefreshToken refreshToken = refreshTokenService.issue("alice-id", "google", "alice-subject");

        assertEquals(Instant.now(CLOCK).plus(authConfig().refreshTokenTtl()), refreshToken.expiresAt());

        String tokenHash = accessTokenService.hash(refreshToken.token());
        String sessionId = refreshTokenStore.createdSessionsById().values().stream()
                .filter(session -> session.tokenHash().equals(tokenHash))
                .findFirst()
                .orElseThrow()
                .sessionId();
        AuthTestSupport.StoredRefreshSession storedSession = refreshTokenStore.createdSession(sessionId);
        assertEquals("alice-id", storedSession.userId());
        assertEquals("google", storedSession.provider());

        RefreshSessionRecord authenticatedSession = refreshTokenService.authenticate(refreshToken.token());
        assertEquals(sessionId, authenticatedSession.sessionId());

        refreshTokenService.revoke(authenticatedSession);
        assertEquals(Instant.now(CLOCK), refreshTokenStore.revokedAtBySessionId().get(sessionId));
    }

    @Test
    void refreshTokenServiceRejectsMissingAndUnknownTokens() {
        AccessTokenService accessTokenService = new AccessTokenService(
                new AuthTestSupport.RecordingAccessTokenStore(),
                authConfig(),
                CLOCK
        );
        RefreshTokenService refreshTokenService = new RefreshTokenService(
                new AuthTestSupport.RecordingRefreshTokenStore(),
                accessTokenService,
                authConfig(),
                CLOCK
        );

        ChatException missingToken = assertThrows(ChatException.class, () -> refreshTokenService.authenticate(" "));
        ChatException invalidToken = assertThrows(ChatException.class, () -> refreshTokenService.authenticate("missing"));

        assertEquals(HttpStatus.UNAUTHORIZED, missingToken.status());
        assertEquals("Missing refresh token", missingToken.getMessage());
        assertEquals(HttpStatus.UNAUTHORIZED, invalidToken.status());
        assertEquals("Invalid or expired refresh token", invalidToken.getMessage());
    }

    @Test
    void identityProvisioningServiceReusesExistingIdentityAndExistingEmailUsers() {
        AuthTestSupport.InMemoryAuthIdentityStore authIdentityStore = new AuthTestSupport.InMemoryAuthIdentityStore();
        AuthTestSupport.RecordingUserStore userStore = new AuthTestSupport.RecordingUserStore();
        userStore.add(new User("existing-id", "old@example.com", "Old", "Name", null));
        authIdentityStore.save(new UserIdentityRecord("existing-id", "google", "subject-1", "old@example.com", "https://example.com/old.png"));

        IdentityProvisioningService identityProvisioningService = new IdentityProvisioningService(authIdentityStore, userStore);
        ExternalUserIdentity updatedIdentity = externalIdentity("google", "subject-1", "alice@example.com", "Alice", "Example", "https://example.com/picture.png");

        IdentityProvisioningService.ProvisionedIdentity provisionedIdentity = identityProvisioningService.provision(updatedIdentity);

        assertEquals("existing-id", provisionedIdentity.userId());
        assertEquals(List.of("existing-id"), userStore.updatedUserIds());
        assertEquals("alice@example.com", userStore.findUser("existing-id").username());

        AuthTestSupport.RecordingUserStore emailMatchedUserStore = new AuthTestSupport.RecordingUserStore();
        emailMatchedUserStore.add(new User("email-match-id", "alice@example.com", "Alice", "Example", null));
        IdentityProvisioningService emailMatchedService = new IdentityProvisioningService(new AuthTestSupport.InMemoryAuthIdentityStore(), emailMatchedUserStore);

        IdentityProvisioningService.ProvisionedIdentity emailMatchedIdentity = emailMatchedService.provision(updatedIdentity);

        assertEquals("email-match-id", emailMatchedIdentity.userId());
        assertEquals(List.of("email-match-id"), emailMatchedUserStore.updatedUserIds());
    }

    @Test
    void identityProvisioningServiceCreatesNewUsersWhenNoMatchExists() {
        AuthTestSupport.RecordingUserStore userStore = new AuthTestSupport.RecordingUserStore();
        IdentityProvisioningService identityProvisioningService = new IdentityProvisioningService(new AuthTestSupport.InMemoryAuthIdentityStore(), userStore);

        IdentityProvisioningService.ProvisionedIdentity provisionedIdentity = identityProvisioningService.provision(
                externalIdentity("google", "subject-1", "alice@example.com", "Alice", "Example", "https://example.com/picture.png")
        );

        assertEquals(1, userStore.createdUserIds().size());
        assertEquals(provisionedIdentity.userId(), userStore.createdUserIds().getFirst());
        assertEquals("alice@example.com", userStore.findUser(provisionedIdentity.userId()).username());
    }

    @Test
    void authServiceLoginRefreshAndLogoutFlowIssuesCookiesAndRotatesSessions() {
        AuthTestSupport.RecordingAccessTokenStore accessTokenStore = new AuthTestSupport.RecordingAccessTokenStore();
        AuthTestSupport.RecordingRefreshTokenStore refreshTokenStore = new AuthTestSupport.RecordingRefreshTokenStore();
        AuthTestSupport.InMemoryAuthIdentityStore authIdentityStore = new AuthTestSupport.InMemoryAuthIdentityStore();
        AuthTestSupport.RecordingUserStore userStore = new AuthTestSupport.RecordingUserStore();
        AuthConfig authConfig = authConfig();

        AccessTokenService accessTokenService = new AccessTokenService(accessTokenStore, authConfig, CLOCK);
        RefreshTokenService refreshTokenService = new RefreshTokenService(refreshTokenStore, accessTokenService, authConfig, CLOCK);
        IdentityProvisioningService identityProvisioningService = new IdentityProvisioningService(authIdentityStore, userStore);
        AuthTestSupport.FixedOAuthProviderAuthenticator authenticator = new AuthTestSupport.FixedOAuthProviderAuthenticator(
                "google",
                externalIdentity("google", "subject-1", "alice@example.com", "Alice", "Example", "https://example.com/picture.png")
        );
        AuthService authService = new AuthService(
                List.of(authenticator),
                identityProvisioningService,
                authIdentityStore,
                accessTokenService,
                refreshTokenService,
                authConfig,
                userStore,
                false
        );

        ControllerResult loginResult = authService.login("google", oauthLoginRequest());

        assertEquals(HttpStatus.OK, loginResult.status());
        LoginResponse loginResponse = (LoginResponse) loginResult.body();
        assertEquals("alice@example.com", loginResponse.user().username());
        assertEquals("google", loginResponse.user().provider());
        assertEquals("https://example.com/picture.png", loginResponse.user().pictureUrl());
        assertEquals(oauthLoginRequest(), authenticator.lastRequest());
        assertEquals(1, accessTokenStore.createdTokensByHash().size());
        assertEquals(1, refreshTokenStore.createdSessionsById().size());

        String loginCookie = loginResult.headers().get("Set-Cookie").getFirst();
        String firstRefreshToken = cookieValue(loginCookie);
        assertTrue(loginCookie.contains("whispers_refresh_token="));
        assertTrue(loginCookie.contains("HttpOnly"));
        assertTrue(loginCookie.contains("SameSite=Lax"));

        ControllerResult refreshResult = authService.refresh(request(
                "POST",
                "/auth/refresh",
                Map.of("cookie", AuthService.REFRESH_COOKIE_NAME + "=" + firstRefreshToken)
        ));
        LoginResponse refreshedLogin = (LoginResponse) refreshResult.body();
        String refreshedCookie = refreshResult.headers().get("Set-Cookie").getFirst();
        String secondRefreshToken = cookieValue(refreshedCookie);

        assertEquals("https://example.com/picture.png", refreshedLogin.user().pictureUrl());
        assertNotEquals(firstRefreshToken, secondRefreshToken);
        assertEquals(2, refreshTokenStore.createdSessionsById().size());
        assertEquals(1, refreshTokenStore.revokedAtBySessionId().size());

        String accessTokenHash = accessTokenStore.createdTokensByHash().keySet().stream().reduce((first, second) -> second).orElseThrow();
        AuthenticatedUser currentUser = authenticatedUser(loginResponse.user().userId(), loginResponse.user().username(), accessTokenHash);

        ControllerResult logoutResult = authService.logout(
                currentUser,
                request(
                        "POST",
                        "/auth/logout",
                        Map.of("cookie", AuthService.REFRESH_COOKIE_NAME + "=" + secondRefreshToken)
                )
        );

        assertEquals(HttpStatus.OK, logoutResult.status());
        assertEquals(1, accessTokenStore.revokedAtByHash().size());
        assertEquals(2, refreshTokenStore.revokedAtBySessionId().size());
        assertTrue(logoutResult.headers().get("Set-Cookie").getFirst().contains("Max-Age=0"));
    }

    @Test
    void authServiceLogoutClearsCookieEvenForInvalidRefreshTokens() {
        AuthTestSupport.RecordingAccessTokenStore accessTokenStore = new AuthTestSupport.RecordingAccessTokenStore();
        AuthTestSupport.RecordingRefreshTokenStore refreshTokenStore = new AuthTestSupport.RecordingRefreshTokenStore();
        AuthTestSupport.RecordingUserStore userStore = new AuthTestSupport.RecordingUserStore();
        userStore.add(user("alice-id", "alice@example.com"));
        AccessTokenService accessTokenService = new AccessTokenService(accessTokenStore, authConfig(), CLOCK);
        RefreshTokenService refreshTokenService = new RefreshTokenService(refreshTokenStore, accessTokenService, authConfig(), CLOCK);
        AuthService authService = new AuthService(
                List.of(),
                new IdentityProvisioningService(new AuthTestSupport.InMemoryAuthIdentityStore(), userStore),
                new AuthTestSupport.InMemoryAuthIdentityStore(),
                accessTokenService,
                refreshTokenService,
                authConfig(),
                userStore,
                false
        );

        AccessToken accessToken = accessTokenService.issueToken("alice-id", "google", "subject-1");
        AuthenticatedUser currentUser = authenticatedUser("alice-id", "alice@example.com", accessTokenService.hash(accessToken.token()));

        ControllerResult logoutResult = authService.logout(
                currentUser,
                request(
                        "POST",
                        "/auth/logout",
                        Map.of("cookie", AuthService.REFRESH_COOKIE_NAME + "=missing-refresh-token")
                )
        );

        assertEquals(HttpStatus.OK, logoutResult.status());
        assertEquals(1, accessTokenStore.revokedAtByHash().size());
        assertTrue(logoutResult.headers().get("Set-Cookie").getFirst().contains("Max-Age=0"));
    }

    @Test
    void authServiceReturnsCurrentUserProfileFromStoredUserData() {
        AuthTestSupport.RecordingUserStore userStore = new AuthTestSupport.RecordingUserStore();
        userStore.add(new User("alice-id", "alice@example.com", "Alice", "Example", null));
        AuthService authService = new AuthService(
                List.of(),
                new IdentityProvisioningService(new AuthTestSupport.InMemoryAuthIdentityStore(), userStore),
                new AuthTestSupport.InMemoryAuthIdentityStore(),
                new AccessTokenService(new AuthTestSupport.RecordingAccessTokenStore(), authConfig(), CLOCK),
                new RefreshTokenService(
                        new AuthTestSupport.RecordingRefreshTokenStore(),
                        new AccessTokenService(new AuthTestSupport.RecordingAccessTokenStore(), authConfig(), CLOCK),
                        authConfig(),
                        CLOCK
                ),
                authConfig(),
                userStore,
                false
        );

        assertEquals(
                "alice@example.com",
                authService.getCurrentUser(authenticatedUser("alice-id", "alice@example.com", "hash")).username()
        );
    }

    @Test
    void authServiceRejectsUnsupportedProviders() {
        AuthService authService = new AuthService(
                List.of(),
                new IdentityProvisioningService(new AuthTestSupport.InMemoryAuthIdentityStore(), new AuthTestSupport.RecordingUserStore()),
                new AuthTestSupport.InMemoryAuthIdentityStore(),
                new AccessTokenService(new AuthTestSupport.RecordingAccessTokenStore(), authConfig(), CLOCK),
                new RefreshTokenService(
                        new AuthTestSupport.RecordingRefreshTokenStore(),
                        new AccessTokenService(new AuthTestSupport.RecordingAccessTokenStore(), authConfig(), CLOCK),
                        authConfig(),
                        CLOCK
                ),
                authConfig(),
                new AuthTestSupport.RecordingUserStore(),
                false
        );

        ChatException exception = assertThrows(
                ChatException.class,
                () -> authService.login("github", oauthLoginRequest())
        );

        assertEquals(HttpStatus.BAD_REQUEST, exception.status());
        assertEquals("Unsupported auth provider: github", exception.getMessage());
    }

    private String cookieValue(String setCookieHeader) {
        String cookiePair = setCookieHeader.split(";", 2)[0];
        return cookiePair.substring(cookiePair.indexOf('=') + 1);
    }
}
