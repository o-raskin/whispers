package com.oraskin.auth.oidc;

import com.oraskin.auth.domain.ExternalUserIdentity;
import com.oraskin.chat.service.ChatException;
import com.oraskin.common.http.HttpStatus;
import com.oraskin.common.json.JsonCodec;
import org.junit.jupiter.api.Test;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.KeyPair;
import java.security.interfaces.RSAPublicKey;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;

import static com.oraskin.auth.oidc.OidcTestSupport.CLOCK;
import static com.oraskin.auth.oidc.OidcTestSupport.cachedDiscoveryService;
import static com.oraskin.auth.oidc.OidcTestSupport.discoveryDocument;
import static com.oraskin.auth.oidc.OidcTestSupport.jsonResponse;
import static com.oraskin.auth.oidc.OidcTestSupport.jwk;
import static com.oraskin.auth.oidc.OidcTestSupport.loginRequest;
import static com.oraskin.auth.oidc.OidcTestSupport.providerConfig;
import static com.oraskin.auth.oidc.OidcTestSupport.rsaKeyPair;
import static com.oraskin.auth.oidc.OidcTestSupport.setField;
import static com.oraskin.auth.oidc.OidcTestSupport.signedIdToken;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OidcAuthenticationServiceTest {

    @Test
    void authenticateReturnsNormalizedExternalIdentityForValidSignedToken() throws Exception {
        KeyPair keyPair = rsaKeyPair();
        OidcDiscoveryDocument discoveryDocument = discoveryDocument();
        HttpClient httpClient = mock(HttpClient.class);
        Deque<OidcTestSupport.TestHttpResponse> responses = new ArrayDeque<>();
        responses.add(jsonResponse(200, JsonCodec.write(new OidcTokenResponse("access-1", signedIdToken(
                keyPair,
                "google-key-1",
                new OidcIdTokenClaims(
                        discoveryDocument.issuer(),
                        "subject-123",
                        providerConfig().clientId(),
                        CLOCK.instant().plusSeconds(300).getEpochSecond(),
                        CLOCK.instant().minusSeconds(5).getEpochSecond(),
                        "nonce-1",
                        "  Alice@Example.com  ",
                        "Alice",
                        "Example",
                        "https://example.com/alice.png"
                )
        ), 3600, "Bearer", "openid email profile"))));
        responses.add(jsonResponse(
                200,
                JsonCodec.write(new OidcJwkSet(List.of(jwk("google-key-1", (RSAPublicKey) keyPair.getPublic())))),
                Map.of("Cache-Control", List.of("public, max-age=600"))
        ));
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenAnswer(invocation -> responses.removeFirst());

        OidcAuthenticationService service = new OidcAuthenticationService(providerConfig(), CLOCK);
        setField(service, OidcAuthenticationService.class, "discoveryService", cachedDiscoveryService(discoveryDocument));
        setField(service, OidcAuthenticationService.class, "httpClient", httpClient);

        ExternalUserIdentity identity = service.authenticate(loginRequest("code-1", "nonce-1"));

        assertThat(identity)
                .extracting(
                        ExternalUserIdentity::provider,
                        ExternalUserIdentity::providerSubject,
                        ExternalUserIdentity::email,
                        ExternalUserIdentity::username,
                        ExternalUserIdentity::firstName,
                        ExternalUserIdentity::lastName,
                        ExternalUserIdentity::pictureUrl
                )
                .containsExactly(
                        "google",
                        "subject-123",
                        "alice@example.com",
                        "alice@example.com",
                        "Alice",
                        "Example",
                        "https://example.com/alice.png"
                );
        verify(httpClient, times(2)).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
    }

    @Test
    void authenticateRejectsTokensWithUnexpectedIssuer() throws Exception {
        KeyPair keyPair = rsaKeyPair();
        HttpClient httpClient = mock(HttpClient.class);
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(
                jsonResponse(200, JsonCodec.write(new OidcTokenResponse("access-1", signedIdToken(
                        keyPair,
                        "google-key-1",
                        new OidcIdTokenClaims(
                                "https://other-issuer.example",
                                "subject-123",
                                providerConfig().clientId(),
                                CLOCK.instant().plusSeconds(300).getEpochSecond(),
                                CLOCK.instant().minusSeconds(5).getEpochSecond(),
                                "nonce-1",
                                "alice@example.com",
                                "Alice",
                                "Example",
                                null
                        )
                ), 3600, "Bearer", "openid email profile")))
        );

        OidcAuthenticationService service = new OidcAuthenticationService(providerConfig(), CLOCK);
        setField(service, OidcAuthenticationService.class, "discoveryService", cachedDiscoveryService(discoveryDocument()));
        setField(service, OidcAuthenticationService.class, "httpClient", httpClient);

        assertThatThrownBy(() -> service.authenticate(loginRequest("code-1", "nonce-1")))
                .isInstanceOf(ChatException.class)
                .satisfies(throwable -> {
                    ChatException exception = (ChatException) throwable;
                    assertThat(exception.status()).isEqualTo(HttpStatus.UNAUTHORIZED);
                    assertThat(exception).hasMessage("Unexpected token issuer");
                });
    }
}
