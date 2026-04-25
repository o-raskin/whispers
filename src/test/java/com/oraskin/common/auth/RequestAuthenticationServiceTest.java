package com.oraskin.common.auth;

import com.oraskin.auth.AuthTestSupport;
import com.oraskin.auth.service.AccessTokenService;
import com.oraskin.chat.service.ChatException;
import com.oraskin.common.http.HttpRequest;
import com.oraskin.common.http.HttpStatus;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;

import static com.oraskin.auth.AuthTestSupport.authenticatedUser;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RequestAuthenticationServiceTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-04-21T10:15:30Z"), ZoneOffset.UTC);

    @Test
    void authenticateRequiredUsesAuthorizationHeader() {
        AuthTestSupport.RecordingAccessTokenStore accessTokenStore = new AuthTestSupport.RecordingAccessTokenStore();
        AccessTokenService accessTokenService = new AccessTokenService(accessTokenStore, AuthTestSupport.authConfig(), CLOCK);
        RequestAuthenticationService requestAuthenticationService = new RequestAuthenticationService(accessTokenService);
        String rawToken = "access-token";
        String tokenHash = accessTokenService.hash(rawToken);
        AuthenticatedUser authenticatedUser = authenticatedUser("alice-id", "alice@example.com", tokenHash);
        accessTokenStore.activate(tokenHash, authenticatedUser);

        HttpRequest authenticatedRequest = requestAuthenticationService.authenticateRequired(
                AuthTestSupport.request("GET", "/messages", Map.of("authorization", "Bearer " + rawToken))
        );

        assertEquals(authenticatedUser, authenticatedRequest.authenticatedUser());
    }

    @Test
    void authenticateRequiredSupportsWebSocketBearerSubprotocol() {
        AuthTestSupport.RecordingAccessTokenStore accessTokenStore = new AuthTestSupport.RecordingAccessTokenStore();
        AccessTokenService accessTokenService = new AccessTokenService(accessTokenStore, AuthTestSupport.authConfig(), CLOCK);
        RequestAuthenticationService requestAuthenticationService = new RequestAuthenticationService(accessTokenService);
        String rawToken = "ws-access-token";
        String tokenHash = accessTokenService.hash(rawToken);
        AuthenticatedUser authenticatedUser = authenticatedUser("alice-id", "alice@example.com", tokenHash);
        accessTokenStore.activate(tokenHash, authenticatedUser);
        HttpRequest request = AuthTestSupport.request(
                "GET",
                "/ws/user",
                Map.of("sec-websocket-protocol", "chat, whispers.bearer." + rawToken + ", trace")
        );

        HttpRequest authenticatedRequest = requestAuthenticationService.authenticateRequired(request);

        assertEquals(authenticatedUser, authenticatedRequest.authenticatedUser());
        assertEquals("whispers.bearer." + rawToken, requestAuthenticationService.resolveWebSocketSubprotocol(request));
    }

    @Test
    void authenticateIfPresentLeavesUnauthenticatedRequestsUnchanged() {
        RequestAuthenticationService requestAuthenticationService = new RequestAuthenticationService(
                new AccessTokenService(new AuthTestSupport.RecordingAccessTokenStore(), AuthTestSupport.authConfig(), CLOCK)
        );
        HttpRequest request = AuthTestSupport.request("GET", "/users", Map.of());

        HttpRequest authenticatedRequest = requestAuthenticationService.authenticateIfPresent(request);

        assertSame(request, authenticatedRequest);
        assertNull(authenticatedRequest.authenticatedUser());
    }

    @Test
    void authenticateRequiredRejectsMissingBearerTokens() {
        RequestAuthenticationService requestAuthenticationService = new RequestAuthenticationService(
                new AccessTokenService(new AuthTestSupport.RecordingAccessTokenStore(), AuthTestSupport.authConfig(), CLOCK)
        );

        ChatException exception = assertThrows(
                ChatException.class,
                () -> requestAuthenticationService.authenticateRequired(AuthTestSupport.request("GET", "/users", Map.of()))
        );

        assertEquals(HttpStatus.UNAUTHORIZED, exception.status());
        assertEquals("Missing bearer token", exception.getMessage());
    }
}
