package com.oraskin.auth.oidc;

import com.oraskin.auth.config.OidcProviderConfig;
import com.oraskin.auth.domain.OAuthLoginRequest;
import com.oraskin.common.json.JsonCodec;
import sun.misc.Unsafe;

import javax.net.ssl.SSLSession;
import java.lang.reflect.Field;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.interfaces.RSAPublicKey;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;

final class OidcTestSupport {

    static final Clock CLOCK = Clock.fixed(Instant.parse("2026-04-21T10:15:30Z"), ZoneOffset.UTC);

    private OidcTestSupport() {
    }

    static OidcProviderConfig providerConfig() {
        return new OidcProviderConfig(
                "google",
                URI.create("https://accounts.google.com/"),
                "client-id",
                "client-secret",
                URI.create("http://localhost:5173/auth/callback/google"),
                List.of("openid", "email", "profile")
        );
    }

    static OidcDiscoveryDocument discoveryDocument() {
        return new OidcDiscoveryDocument(
                "https://accounts.google.com",
                "https://accounts.google.com/o/oauth2/v2/auth",
                "https://oauth2.googleapis.com/token",
                "https://www.googleapis.com/oauth2/v3/certs"
        );
    }

    static OAuthLoginRequest loginRequest(String code, String nonce) {
        return new OAuthLoginRequest(code, providerConfig().redirectUri().toString(), "code-verifier", nonce);
    }

    static KeyPair rsaKeyPair() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048, SecureRandom.getInstanceStrong());
        return generator.generateKeyPair();
    }

    static OidcJwk jwk(String keyId, RSAPublicKey publicKey) {
        return new OidcJwk(
                keyId,
                "RSA",
                "RS256",
                "sig",
                Base64.getUrlEncoder().withoutPadding().encodeToString(publicKey.getModulus().toByteArray()),
                Base64.getUrlEncoder().withoutPadding().encodeToString(publicKey.getPublicExponent().toByteArray())
        );
    }

    static String signedIdToken(KeyPair keyPair, String keyId, OidcIdTokenClaims claims) throws Exception {
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

    static TestHttpResponse jsonResponse(int status, String body) {
        return new TestHttpResponse(status, body, Map.of());
    }

    static TestHttpResponse jsonResponse(int status, String body, Map<String, List<String>> headers) {
        return new TestHttpResponse(status, body, headers);
    }

    static OidcDiscoveryService cachedDiscoveryService(OidcDiscoveryDocument document) throws Exception {
        OidcDiscoveryService discoveryService = new OidcDiscoveryService(providerConfig());
        setField(discoveryService, OidcDiscoveryService.class, "cachedDocument", document);
        return discoveryService;
    }

    static void setField(Object target, Class<?> type, String fieldName, Object value) throws Exception {
        Field field = type.getDeclaredField(fieldName);
        long offset = unsafe().objectFieldOffset(field);
        unsafe().putObject(target, offset, value);
    }

    private static Unsafe unsafe() throws Exception {
        Field field = Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        return (Unsafe) field.get(null);
    }

    record TestHttpResponse(int statusCode, String body, Map<String, List<String>> rawHeaders) implements HttpResponse<String> {

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
