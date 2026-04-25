package com.oraskin.auth.api;

import com.oraskin.auth.AuthTestSupport;
import com.oraskin.auth.domain.AuthUserProfile;
import com.oraskin.auth.domain.LoginResponse;
import com.oraskin.auth.service.AccessTokenService;
import com.oraskin.auth.service.AuthService;
import com.oraskin.auth.service.IdentityProvisioningService;
import com.oraskin.auth.service.RefreshTokenService;
import com.oraskin.common.auth.AuthenticatedUser;
import com.oraskin.common.http.HttpRequest;
import com.oraskin.common.http.QueryParams;
import com.oraskin.common.mvc.ControllerResult;
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
import static org.assertj.core.api.Assertions.assertThat;

class AuthControllerTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-04-21T10:15:30Z"), ZoneOffset.UTC);

    @Test
    void authControllerDelegatesAcrossLoginRefreshMeAndLogout() {
        AuthTestSupport.RecordingAccessTokenStore accessTokenStore = new AuthTestSupport.RecordingAccessTokenStore();
        AuthTestSupport.RecordingRefreshTokenStore refreshTokenStore = new AuthTestSupport.RecordingRefreshTokenStore();
        AuthTestSupport.InMemoryAuthIdentityStore authIdentityStore = new AuthTestSupport.InMemoryAuthIdentityStore();
        AuthTestSupport.RecordingUserStore userStore = new AuthTestSupport.RecordingUserStore();
        AccessTokenService accessTokenService = new AccessTokenService(accessTokenStore, authConfig(), CLOCK);
        RefreshTokenService refreshTokenService = new RefreshTokenService(refreshTokenStore, accessTokenService, authConfig(), CLOCK);
        AuthService authService = new AuthService(
                List.of(new AuthTestSupport.FixedOAuthProviderAuthenticator(
                        "google",
                        externalIdentity("google", "subject-1", "alice@example.com", "Alice", "Example", "https://example.com/a.png")
                )),
                new IdentityProvisioningService(authIdentityStore, userStore),
                authIdentityStore,
                accessTokenService,
                refreshTokenService,
                authConfig(),
                userStore,
                false
        );
        AuthController controller = new AuthController(authService);

        ControllerResult loginResult = controller.login("google", oauthLoginRequest());
        String refreshToken = loginResult.headers().get("Set-Cookie").getFirst().split(";", 2)[0].split("=", 2)[1];
        LoginResponse loginResponse = (LoginResponse) loginResult.body();
        String latestAccessTokenHash = accessTokenStore.createdTokensByHash().keySet().stream().reduce((first, second) -> second).orElseThrow();
        AuthenticatedUser user = authenticatedUser(loginResponse.user().userId(), "alice@example.com", latestAccessTokenHash);
        HttpRequest refreshRequest = new HttpRequest(
                "POST",
                "/auth/refresh",
                QueryParams.fromTarget("/auth/refresh"),
                Map.of("cookie", AuthService.REFRESH_COOKIE_NAME + "=" + refreshToken),
                null,
                null
        );
        HttpRequest logoutRequest = new HttpRequest(
                "POST",
                "/auth/logout",
                QueryParams.fromTarget("/auth/logout"),
                Map.of("cookie", AuthService.REFRESH_COOKIE_NAME + "=" + refreshToken),
                null,
                null
        );

        ControllerResult refreshResult = controller.refresh(refreshRequest);
        AuthUserProfile userProfile = controller.me(user);
        ControllerResult logoutResult = controller.logout(user, logoutRequest);

        assertThat(loginResponse.user().username()).isEqualTo("alice@example.com");
        assertThat(refreshResult.headers().get("Set-Cookie")).isNotEmpty();
        assertThat(userProfile.username()).isEqualTo("alice@example.com");
        assertThat((Map<String, String>) logoutResult.body()).containsEntry("status", "logged_out");
    }
}
