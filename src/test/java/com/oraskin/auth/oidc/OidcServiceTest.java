package com.oraskin.auth.oidc;

import com.oraskin.auth.config.OidcProviderConfig;
import com.oraskin.auth.domain.ExternalUserIdentity;
import com.oraskin.auth.domain.OAuthLoginRequest;
import com.oraskin.chat.service.ChatException;
import com.oraskin.common.http.HttpStatus;
import com.oraskin.common.json.JsonCodec;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import sun.misc.Unsafe;

import javax.net.ssl.SSLSession;
import java.io.IOException;
import java.lang.reflect.Field;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.interfaces.RSAPublicKey;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayDeque;
import java.util.Base64;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OidcServiceTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-04-21T10:15:30Z"), ZoneOffset.UTC);

    @Test
    void discoveryServiceFetchesAndCachesDiscoveryDocument() throws Exception {
        HttpClient httpClient = mock(HttpClient.class);
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(
                jsonResponse(
                        200,
                        JsonCodec.write(new OidcDiscoveryDocument(
                                "https://accounts.google.com",
                                "https://accounts.google.com/o/oauth2/v2/auth",
                                "https://oauth2.googleapis.com/token",
                                "https://www.googleapis.com/oauth2/v3/certs"
                        ))
                )
        );

        OidcDiscoveryService discoveryService = new OidcDiscoveryService(providerConfig());
        setField(discoveryService, OidcDiscoveryService.class, "httpClient", httpClient);

        OidcDiscoveryDocument first = discoveryService.getDiscoveryDocument();
        OidcDiscoveryDocument second = discoveryService.getDiscoveryDocument();
        ArgumentCaptor<HttpRequest> requestCaptor = ArgumentCaptor.forClass(HttpRequest.class);

        assertThat(second).isSameAs(first);
        assertThat(first.tokenEndpoint()).isEqualTo("https://oauth2.googleapis.com/token");
        verify(httpClient, times(1)).send(requestCaptor.capture(), any(HttpResponse.BodyHandler.class));
        assertThat(requestCaptor.getValue().uri()).hasToString("https://accounts.google.com/.well-known/openid-configuration");
    }

    @Test
    void discoveryServiceRejectsUnexpectedStatuses() throws Exception {
        HttpClient httpClient = mock(HttpClient.class);
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(jsonResponse(503, "{}"));

        OidcDiscoveryService discoveryService = new OidcDiscoveryService(providerConfig());
        setField(discoveryService, OidcDiscoveryService.class, "httpClient", httpClient);

        assertThatThrownBy(discoveryService::getDiscoveryDocument)
                .isInstanceOf(ChatException.class)
                .satisfies(throwable -> {
                    ChatException exception = (ChatException) throwable;
                    assertThat(exception.status()).isEqualTo(HttpStatus.UNAUTHORIZED);
                    assertThat(exception).hasMessageContaining("status 503");
                });
    }

    @Test
    void discoveryServiceRestoresInterruptFlagWhenFetchIsInterrupted() throws Exception {
        HttpClient httpClient = mock(HttpClient.class);
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenThrow(new InterruptedException("stopped"));

        OidcDiscoveryService discoveryService = new OidcDiscoveryService(providerConfig());
        setField(discoveryService, OidcDiscoveryService.class, "httpClient", httpClient);

        assertThatThrownBy(discoveryService::getDiscoveryDocument)
                .isInstanceOf(ChatException.class)
                .satisfies(throwable -> {
                    ChatException exception = (ChatException) throwable;
                    assertThat(exception.status()).isEqualTo(HttpStatus.UNAUTHORIZED);
                    assertThat(exception).hasMessageContaining("interrupted");
                });
        assertThat(Thread.currentThread().isInterrupted()).isTrue();
        assertThat(Thread.interrupted()).isTrue();
    }

    @Test
    void discoveryServiceWrapsIoFailures() throws Exception {
        HttpClient httpClient = mock(HttpClient.class);
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenThrow(new IOException("network down"));

        OidcDiscoveryService discoveryService = new OidcDiscoveryService(providerConfig());
        setField(discoveryService, OidcDiscoveryService.class, "httpClient", httpClient);

        assertThatThrownBy(discoveryService::getDiscoveryDocument)
                .isInstanceOf(ChatException.class)
                .satisfies(throwable -> {
                    ChatException exception = (ChatException) throwable;
                    assertThat(exception.status()).isEqualTo(HttpStatus.UNAUTHORIZED);
                    assertThat(exception).hasMessageContaining("Failed to read OIDC discovery document");
                    assertThat(exception).hasMessageContaining("IOException: network down");
                });
    }

    @Test
    void authenticationServiceAuthenticatesValidTokenAndCachesSigningKeys() throws Exception {
        KeyPair keyPair = rsaKeyPair();
        OidcDiscoveryDocument discoveryDocument = discoveryDocument();
        HttpClient httpClient = mock(HttpClient.class);
        Deque<HttpResponse<String>> responses = new ArrayDeque<>();
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
        responses.add(jsonResponse(200, JsonCodec.write(new OidcTokenResponse("access-2", signedIdToken(
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
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenAnswer(invocation -> responses.removeFirst());

        OidcAuthenticationService service = new OidcAuthenticationService(providerConfig(), CLOCK);
        setField(service, OidcAuthenticationService.class, "discoveryService", cachedDiscoveryService(discoveryDocument));
        setField(service, OidcAuthenticationService.class, "httpClient", httpClient);

        ExternalUserIdentity first = service.authenticate(loginRequest("code-1", "nonce-1"));
        ExternalUserIdentity second = service.authenticate(loginRequest("code-2", "nonce-1"));
        ArgumentCaptor<HttpRequest> requestCaptor = ArgumentCaptor.forClass(HttpRequest.class);

        assertThat(first)
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
        assertThat(second).isEqualTo(first);

        verify(httpClient, times(3)).send(requestCaptor.capture(), any(HttpResponse.BodyHandler.class));
        assertThat(requestCaptor.getAllValues())
                .extracting(request -> request.uri().toString())
                .containsExactly(
                        discoveryDocument.tokenEndpoint(),
                        discoveryDocument.jwksUri(),
                        discoveryDocument.tokenEndpoint()
                );
        assertThat(requestCaptor.getAllValues().getFirst().headers().firstValue("Content-Type"))
                .contains("application/x-www-form-urlencoded");
        assertThat(readRequestBody(requestCaptor.getAllValues().getFirst())).isNotBlank();
    }

    @Test
    void authenticationServiceRejectsUnexpectedIssuerBeforeSignatureLookup() throws Exception {
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
        verify(httpClient, times(1)).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
    }

    @Test
    void authenticationServiceRejectsInvalidNonceBeforeSignatureLookup() throws Exception {
        KeyPair keyPair = rsaKeyPair();
        OidcDiscoveryDocument discoveryDocument = discoveryDocument();
        HttpClient httpClient = mock(HttpClient.class);
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(
                jsonResponse(200, JsonCodec.write(new OidcTokenResponse("access-1", signedIdToken(
                        keyPair,
                        "google-key-1",
                        new OidcIdTokenClaims(
                                discoveryDocument.issuer(),
                                "subject-123",
                                providerConfig().clientId(),
                                CLOCK.instant().plusSeconds(300).getEpochSecond(),
                                CLOCK.instant().minusSeconds(5).getEpochSecond(),
                                "wrong-nonce",
                                "alice@example.com",
                                "Alice",
                                "Example",
                                null
                        )
                ), 3600, "Bearer", "openid email profile")))
        );

        OidcAuthenticationService service = new OidcAuthenticationService(providerConfig(), CLOCK);
        setField(service, OidcAuthenticationService.class, "discoveryService", cachedDiscoveryService(discoveryDocument));
        setField(service, OidcAuthenticationService.class, "httpClient", httpClient);

        assertThatThrownBy(() -> service.authenticate(loginRequest("code-1", "nonce-1")))
                .isInstanceOf(ChatException.class)
                .satisfies(throwable -> {
                    ChatException exception = (ChatException) throwable;
                    assertThat(exception.status()).isEqualTo(HttpStatus.UNAUTHORIZED);
                    assertThat(exception).hasMessage("ID token nonce is invalid");
                });
        verify(httpClient, times(1)).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
    }

    @Test
    void authenticationServiceRejectsTokensMissingRequiredClaimsAfterSignatureVerification() throws Exception {
        KeyPair keyPair = rsaKeyPair();
        OidcDiscoveryDocument discoveryDocument = discoveryDocument();
        HttpClient httpClient = mock(HttpClient.class);
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(
                jsonResponse(200, JsonCodec.write(new OidcTokenResponse("access-1", signedIdToken(
                        keyPair,
                        "google-key-1",
                        new OidcIdTokenClaims(
                                discoveryDocument.issuer(),
                                "subject-123",
                                providerConfig().clientId(),
                                CLOCK.instant().plusSeconds(300).getEpochSecond(),
                                CLOCK.instant().minusSeconds(5).getEpochSecond(),
                                "nonce-1",
                                "alice@example.com",
                                null,
                                "Example",
                                null
                        )
                ), 3600, "Bearer", "openid email profile"))),
                jsonResponse(
                        200,
                        JsonCodec.write(new OidcJwkSet(List.of(jwk("google-key-1", (RSAPublicKey) keyPair.getPublic())))),
                        Map.of("Cache-Control", List.of("public, max-age=600"))
                )
        );

        OidcAuthenticationService service = new OidcAuthenticationService(providerConfig(), CLOCK);
        setField(service, OidcAuthenticationService.class, "discoveryService", cachedDiscoveryService(discoveryDocument));
        setField(service, OidcAuthenticationService.class, "httpClient", httpClient);

        assertThatThrownBy(() -> service.authenticate(loginRequest("code-1", "nonce-1")))
                .isInstanceOf(ChatException.class)
                .satisfies(throwable -> {
                    ChatException exception = (ChatException) throwable;
                    assertThat(exception.status()).isEqualTo(HttpStatus.UNAUTHORIZED);
                    assertThat(exception).hasMessage("OIDC required claims are missing");
                });
        verify(httpClient, times(2)).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
    }

    private OidcProviderConfig providerConfig() {
        return new OidcProviderConfig(
                "google",
                URI.create("https://accounts.google.com/"),
                "client-id",
                "client-secret",
                URI.create("http://localhost:5173/auth/callback/google"),
                List.of("openid", "email", "profile")
        );
    }

    private OidcDiscoveryDocument discoveryDocument() {
        return new OidcDiscoveryDocument(
                "https://accounts.google.com",
                "https://accounts.google.com/o/oauth2/v2/auth",
                "https://oauth2.googleapis.com/token",
                "https://www.googleapis.com/oauth2/v3/certs"
        );
    }

    private OAuthLoginRequest loginRequest(String code, String nonce) {
        return new OAuthLoginRequest(code, providerConfig().redirectUri().toString(), "code-verifier", nonce);
    }

    private OidcDiscoveryService cachedDiscoveryService(OidcDiscoveryDocument document) throws Exception {
        OidcDiscoveryService discoveryService = new OidcDiscoveryService(providerConfig());
        setField(discoveryService, OidcDiscoveryService.class, "cachedDocument", document);
        return discoveryService;
    }

    private OidcJwk jwk(String keyId, RSAPublicKey publicKey) {
        return new OidcJwk(
                keyId,
                "RSA",
                "RS256",
                "sig",
                Base64.getUrlEncoder().withoutPadding().encodeToString(publicKey.getModulus().toByteArray()),
                Base64.getUrlEncoder().withoutPadding().encodeToString(publicKey.getPublicExponent().toByteArray())
        );
    }

    private KeyPair rsaKeyPair() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048, SecureRandom.getInstanceStrong());
        return generator.generateKeyPair();
    }

    private String signedIdToken(KeyPair keyPair, String keyId, OidcIdTokenClaims claims) throws Exception {
        String header = Base64.getUrlEncoder().withoutPadding().encodeToString(
                JsonCodec.write(Map.of("alg", "RS256", "kid", keyId, "typ", "JWT")).getBytes(StandardCharsets.UTF_8)
        );
        String payload = Base64.getUrlEncoder().withoutPadding().encodeToString(
                JsonCodec.write(claims).getBytes(StandardCharsets.UTF_8)
        );
        Signature signature = Signature.getInstance("SHA256withRSA");
        signature.initSign(keyPair.getPrivate());
        signature.update((header + "." + payload).getBytes(StandardCharsets.US_ASCII));
        return header + "." + payload + "." + Base64.getUrlEncoder().withoutPadding().encodeToString(signature.sign());
    }

    private TestHttpResponse jsonResponse(int status, String body) {
        return jsonResponse(status, body, Map.of());
    }

    private TestHttpResponse jsonResponse(int status, String body, Map<String, List<String>> headers) {
        return new TestHttpResponse(status, body, headers);
    }

    private void setField(Object target, Class<?> type, String fieldName, Object value) throws Exception {
        Field field = type.getDeclaredField(fieldName);
        long offset = unsafe().objectFieldOffset(field);
        unsafe().putObject(target, offset, value);
    }

    private Unsafe unsafe() throws Exception {
        Field field = Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        return (Unsafe) field.get(null);
    }

    private String readRequestBody(HttpRequest request) {
        HttpRequest.BodyPublisher publisher = request.bodyPublisher().orElseThrow();
        BodySubscriber subscriber = new BodySubscriber();
        publisher.subscribe(subscriber);
        return subscriber.awaitBody();
    }

    private static final class BodySubscriber implements Flow.Subscriber<ByteBuffer> {

        private final CompletableFuture<String> body = new CompletableFuture<>();
        private final StringBuilder builder = new StringBuilder();

        @Override
        public void onSubscribe(Flow.Subscription subscription) {
            subscription.request(Long.MAX_VALUE);
        }

        @Override
        public void onNext(ByteBuffer item) {
            builder.append(StandardCharsets.UTF_8.decode(item.duplicate()));
        }

        @Override
        public void onError(Throwable throwable) {
            body.completeExceptionally(throwable);
        }

        @Override
        public void onComplete() {
            body.complete(builder.toString());
        }

        private String awaitBody() {
            return body.join();
        }
    }

    private record TestHttpResponse(int statusCode, String body, Map<String, List<String>> rawHeaders) implements HttpResponse<String> {

        @Override
        public HttpRequest request() {
            return null;
        }

        @Override
        public Optional<HttpResponse<String>> previousResponse() {
            return Optional.empty();
        }

        @Override
        public HttpHeaders headers() {
            return HttpHeaders.of(rawHeaders, (name, value) -> true);
        }

        @Override
        public Optional<SSLSession> sslSession() {
            return Optional.empty();
        }

        @Override
        public URI uri() {
            return URI.create("http://localhost");
        }

        @Override
        public HttpClient.Version version() {
            return HttpClient.Version.HTTP_1_1;
        }
    }
}
