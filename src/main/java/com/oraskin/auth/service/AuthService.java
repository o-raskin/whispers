package com.oraskin.auth.service;

import com.oraskin.auth.config.AuthConfig;
import com.oraskin.auth.domain.AccessToken;
import com.oraskin.auth.domain.AuthUserProfile;
import com.oraskin.auth.domain.LoginResponse;
import com.oraskin.auth.domain.OAuthLoginRequest;
import com.oraskin.auth.domain.RefreshToken;
import com.oraskin.auth.persistence.AuthIdentityStore;
import com.oraskin.auth.persistence.entity.UserIdentityRecord;
import com.oraskin.auth.persistence.entity.RefreshSessionRecord;
import com.oraskin.chat.service.ChatException;
import com.oraskin.common.auth.AuthenticatedUser;
import com.oraskin.common.http.HttpRequest;
import com.oraskin.common.http.HttpStatus;
import com.oraskin.common.http.cookie.ResponseCookie;
import com.oraskin.common.mvc.ControllerResult;
import com.oraskin.user.data.domain.User;
import com.oraskin.user.data.persistence.UserStore;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

public final class AuthService {

    public static final String REFRESH_COOKIE_NAME = "whispers_refresh_token";

    private final Map<String, OAuthProviderAuthenticator> providerAuthenticators;
    private final IdentityProvisioningService identityProvisioningService;
    private final AuthIdentityStore authIdentityStore;
    private final AccessTokenService accessTokenService;
    private final RefreshTokenService refreshTokenService;
    private final AuthConfig authConfig;
    private final UserStore userStore;
    private final boolean secureCookies;

    public AuthService(
            List<OAuthProviderAuthenticator> providerAuthenticators,
            IdentityProvisioningService identityProvisioningService,
            AuthIdentityStore authIdentityStore,
            AccessTokenService accessTokenService,
            RefreshTokenService refreshTokenService,
            AuthConfig authConfig,
            UserStore userStore,
            boolean secureCookies
    ) {
        this.providerAuthenticators = providerAuthenticators.stream()
                .collect(Collectors.toUnmodifiableMap(OAuthProviderAuthenticator::provider, Function.identity()));
        this.identityProvisioningService = Objects.requireNonNull(identityProvisioningService);
        this.authIdentityStore = Objects.requireNonNull(authIdentityStore);
        this.accessTokenService = Objects.requireNonNull(accessTokenService);
        this.refreshTokenService = Objects.requireNonNull(refreshTokenService);
        this.authConfig = Objects.requireNonNull(authConfig);
        this.userStore = Objects.requireNonNull(userStore);
        this.secureCookies = secureCookies;
    }

    public ControllerResult login(String provider, OAuthLoginRequest request) {
        OAuthProviderAuthenticator authenticator = providerAuthenticators.get(provider);
        if (authenticator == null) {
            throw new ChatException(HttpStatus.BAD_REQUEST, "Unsupported auth provider: " + provider);
        }

        IdentityProvisioningService.ProvisionedIdentity provisioned = identityProvisioningService.provision(
                authenticator.authenticate(request)
        );
        AccessToken accessToken = accessTokenService.issueToken(
                provisioned.userId(),
                provisioned.externalIdentity().provider(),
                provisioned.externalIdentity().providerSubject()
        );
        RefreshToken refreshToken = refreshTokenService.issue(
                provisioned.userId(),
                provisioned.externalIdentity().provider(),
                provisioned.externalIdentity().providerSubject()
        );
        return withRefreshCookie(
                loginResponse(
                        provisioned.userId(),
                        accessToken,
                        provisioned.externalIdentity().provider(),
                        provisioned.externalIdentity().providerSubject(),
                        provisioned.externalIdentity().pictureUrl()
                ),
                refreshToken
        );
    }

    public ControllerResult refresh(HttpRequest request) {
        RefreshSessionRecord sessionRecord = refreshTokenService.authenticate(request.cookie(REFRESH_COOKIE_NAME));
        refreshTokenService.revoke(sessionRecord);
        AccessToken accessToken = accessTokenService.issueToken(
                sessionRecord.userId(),
                sessionRecord.provider(),
                sessionRecord.providerSubject()
        );
        RefreshToken refreshToken = refreshTokenService.issue(
                sessionRecord.userId(),
                sessionRecord.provider(),
                sessionRecord.providerSubject()
        );
        return withRefreshCookie(
                loginResponse(
                        sessionRecord.userId(),
                        accessToken,
                        sessionRecord.provider(),
                        sessionRecord.providerSubject(),
                        null
                ),
                refreshToken
        );
    }

    public AuthUserProfile getCurrentUser(AuthenticatedUser user) {
        return toProfile(userStore.findUser(user.userId()), user.provider(), user.pictureUrl());
    }

    public ControllerResult logout(AuthenticatedUser user, HttpRequest request) {
        if (user != null) {
            accessTokenService.revoke(user.accessTokenHash());
        }
        String refreshToken = request.cookie(REFRESH_COOKIE_NAME);
        if (refreshToken != null && !refreshToken.isBlank()) {
            try {
                RefreshSessionRecord sessionRecord = refreshTokenService.authenticate(refreshToken);
                refreshTokenService.revoke(sessionRecord);
            } catch (ChatException ignored) {
                // already invalid, clear cookie anyway
            }
        }
        return ControllerResult.withHeaders(
                HttpStatus.OK,
                Map.of(
                        "status", "logged_out",
                        "redirectUri", authConfig.postLogoutRedirectUri().toString()
                ),
                Map.of("Set-Cookie", List.of(clearRefreshCookie().serialize()))
        );
    }

    private ControllerResult withRefreshCookie(LoginResponse response, RefreshToken refreshToken) {
        return ControllerResult.withHeaders(
                HttpStatus.OK,
                response,
                Map.of("Set-Cookie", List.of(buildRefreshCookie(refreshToken).serialize()))
        );
    }

    private LoginResponse loginResponse(String userId, AccessToken accessToken, String provider) {
        return loginResponse(userId, accessToken, provider, null, null);
    }

    private LoginResponse loginResponse(
            String userId,
            AccessToken accessToken,
            String provider,
            String providerSubject,
            String pictureUrl
    ) {
        if (pictureUrl == null && provider != null && providerSubject != null) {
            pictureUrl = findPictureUrl(provider, providerSubject);
        }
        User user = userStore.findUser(userId);
        return new LoginResponse(
                accessToken.token(),
                accessToken.tokenType(),
                accessToken.expiresInSeconds(),
                toProfile(user, provider, pictureUrl)
        );
    }

    private AuthUserProfile toProfile(User user, String provider, String pictureUrl) {
        return new AuthUserProfile(
                user.userId(),
                user.username(),
                user.username(),
                user.username(),
                user.firstName(),
                user.lastName(),
                pictureUrl,
                provider
        );
    }

    private String findPictureUrl(String provider, String providerSubject) {
        UserIdentityRecord identity = authIdentityStore.findByProviderSubject(provider, providerSubject);
        return identity == null ? null : identity.pictureUrl();
    }

    private ResponseCookie buildRefreshCookie(RefreshToken refreshToken) {
        return new ResponseCookie(
                REFRESH_COOKIE_NAME,
                refreshToken.token(),
                true,
                secureCookies,
                "Lax",
                "/auth",
                Duration.between(java.time.Instant.now(), refreshToken.expiresAt())
        );
    }

    private ResponseCookie clearRefreshCookie() {
        return new ResponseCookie(
                REFRESH_COOKIE_NAME,
                "",
                true,
                secureCookies,
                "Lax",
                "/auth",
                Duration.ZERO
        );
    }
}
