package com.oraskin.auth.oidc;

import com.oraskin.auth.config.OidcProviderConfig;
import com.oraskin.auth.domain.ExternalUserIdentity;
import com.oraskin.auth.domain.OAuthLoginRequest;
import com.oraskin.auth.service.OAuthProviderAuthenticator;
import com.oraskin.chat.service.ChatException;
import com.oraskin.common.http.HttpStatus;
import com.oraskin.common.json.JsonCodec;

import java.io.IOException;
import java.math.BigInteger;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.Signature;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.RSAPublicKeySpec;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

public final class OidcAuthenticationService implements OAuthProviderAuthenticator {

    private final OidcProviderConfig providerConfig;
    private final OidcDiscoveryService discoveryService;
    private final HttpClient httpClient;
    private final Clock clock;
    private volatile CachedKeys cachedKeys;

    public OidcAuthenticationService(OidcProviderConfig providerConfig, Clock clock) {
        this.providerConfig = Objects.requireNonNull(providerConfig);
        this.discoveryService = new OidcDiscoveryService(providerConfig);
        this.clock = Objects.requireNonNull(clock);
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    }

    @Override
    public String provider() {
        return providerConfig.provider();
    }

    @Override
    public ExternalUserIdentity authenticate(OAuthLoginRequest request) {
        validateRequest(request);
        OidcTokenResponse tokenResponse = exchangeCode(request);
        OidcIdTokenClaims claims = verifyIdToken(tokenResponse.idToken(), request.nonce());
        String email = normalizeEmail(claims.email());
        if (email == null || claims.givenName() == null || claims.familyName() == null) {
            throw new ChatException(HttpStatus.UNAUTHORIZED, "OIDC required claims are missing");
        }
        return new ExternalUserIdentity(
                provider(),
                claims.sub(),
                email,
                email,
                email,
                claims.givenName(),
                claims.familyName(),
                claims.picture()
        );
    }

    private void validateRequest(OAuthLoginRequest request) {
        if (request.code() == null || request.code().isBlank()) {
            throw new ChatException(HttpStatus.BAD_REQUEST, "Missing authorization code");
        }
        if (request.redirectUri() == null || request.redirectUri().isBlank()) {
            throw new ChatException(HttpStatus.BAD_REQUEST, "Missing redirectUri");
        }
        if (!providerConfig.redirectUri().toString().equals(request.redirectUri())) {
            throw new ChatException(HttpStatus.BAD_REQUEST, "Unexpected redirectUri");
        }
        if (request.codeVerifier() == null || request.codeVerifier().isBlank()) {
            throw new ChatException(HttpStatus.BAD_REQUEST, "Missing codeVerifier");
        }
        if (request.nonce() == null || request.nonce().isBlank()) {
            throw new ChatException(HttpStatus.BAD_REQUEST, "Missing nonce");
        }
    }

    private OidcTokenResponse exchangeCode(OAuthLoginRequest request) {
        OidcDiscoveryDocument discoveryDocument = discoveryService.getDiscoveryDocument();
        String body = form(Map.of(
                "grant_type", "authorization_code",
                "code", request.code(),
                "client_id", providerConfig.clientId(),
                "client_secret", providerConfig.clientSecret(),
                "redirect_uri", request.redirectUri(),
                "code_verifier", request.codeVerifier()
        ));
        HttpRequest exchangeRequest = HttpRequest.newBuilder(java.net.URI.create(discoveryDocument.tokenEndpoint()))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .timeout(Duration.ofSeconds(10))
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        try {
            HttpResponse<String> response = httpClient.send(exchangeRequest, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new ChatException(HttpStatus.UNAUTHORIZED, "OIDC token exchange failed");
            }
            return JsonCodec.read(response.body(), OidcTokenResponse.class);
        } catch (IOException e) {
            throw new ChatException(HttpStatus.UNAUTHORIZED, "Failed to read OIDC token response");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ChatException(HttpStatus.UNAUTHORIZED, "OIDC token request was interrupted");
        }
    }

    private OidcIdTokenClaims verifyIdToken(String idToken, String expectedNonce) {
        String[] parts = idToken.split("\\.");
        if (parts.length != 3) {
            throw new ChatException(HttpStatus.UNAUTHORIZED, "Invalid ID token format");
        }
        JwtHeader header = parseHeader(parts[0]);
        OidcIdTokenClaims claims = parseClaims(parts[1]);
        verifyClaims(claims, expectedNonce);
        verifySignature(parts[0], parts[1], parts[2], header.kid());
        return claims;
    }

    private JwtHeader parseHeader(String encodedHeader) {
        try {
            return JsonCodec.read(decode(encodedHeader), JwtHeader.class);
        } catch (IOException e) {
            throw new ChatException(HttpStatus.UNAUTHORIZED, "Invalid ID token header");
        }
    }

    private OidcIdTokenClaims parseClaims(String encodedClaims) {
        try {
            return JsonCodec.read(decode(encodedClaims), OidcIdTokenClaims.class);
        } catch (IOException e) {
            throw new ChatException(HttpStatus.UNAUTHORIZED, "Invalid ID token claims");
        }
    }

    private void verifyClaims(OidcIdTokenClaims claims, String expectedNonce) {
        OidcDiscoveryDocument discoveryDocument = discoveryService.getDiscoveryDocument();
        Instant now = Instant.now(clock);
        if (!discoveryDocument.issuer().equals(claims.iss())) {
            throw new ChatException(HttpStatus.UNAUTHORIZED, "Unexpected token issuer");
        }
        if (!providerConfig.clientId().equals(claims.aud())) {
            throw new ChatException(HttpStatus.UNAUTHORIZED, "Unexpected token audience");
        }
        if (claims.exp() <= now.getEpochSecond()) {
            throw new ChatException(HttpStatus.UNAUTHORIZED, "ID token has expired");
        }
        if (claims.sub() == null || claims.sub().isBlank()) {
            throw new ChatException(HttpStatus.UNAUTHORIZED, "ID token subject is missing");
        }
        if (!expectedNonce.equals(claims.nonce())) {
            throw new ChatException(HttpStatus.UNAUTHORIZED, "ID token nonce is invalid");
        }
    }

    private void verifySignature(String header, String claims, String signature, String keyId) {
        RSAPublicKey key = findKey(keyId);
        try {
            Signature verifier = Signature.getInstance("SHA256withRSA");
            verifier.initVerify(key);
            verifier.update((header + "." + claims).getBytes(StandardCharsets.US_ASCII));
            if (!verifier.verify(Base64.getUrlDecoder().decode(signature))) {
                throw new ChatException(HttpStatus.UNAUTHORIZED, "ID token signature is invalid");
            }
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Failed to verify ID token signature", e);
        }
    }

    private RSAPublicKey findKey(String keyId) {
        CachedKeys snapshot = cachedKeys;
        Instant now = Instant.now(clock);
        if (snapshot == null || snapshot.expiresAt().isBefore(now)) {
            snapshot = refreshKeys();
        }
        OidcJwk jwk = snapshot.keys().get(keyId);
        if (jwk == null) {
            snapshot = refreshKeys();
            jwk = snapshot.keys().get(keyId);
        }
        if (jwk == null) {
            throw new ChatException(HttpStatus.UNAUTHORIZED, "Signing key was not found");
        }
        try {
            BigInteger modulus = new BigInteger(1, Base64.getUrlDecoder().decode(jwk.n()));
            BigInteger exponent = new BigInteger(1, Base64.getUrlDecoder().decode(jwk.e()));
            PublicKey publicKey = KeyFactory.getInstance("RSA").generatePublic(new RSAPublicKeySpec(modulus, exponent));
            return (RSAPublicKey) publicKey;
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Failed to load signing key", e);
        }
    }

    private synchronized CachedKeys refreshKeys() {
        OidcDiscoveryDocument discoveryDocument = discoveryService.getDiscoveryDocument();
        HttpRequest request = HttpRequest.newBuilder(java.net.URI.create(discoveryDocument.jwksUri()))
                .timeout(Duration.ofSeconds(10))
                .GET()
                .build();
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new ChatException(HttpStatus.UNAUTHORIZED, "Failed to fetch OIDC signing keys");
            }
            OidcJwkSet jwkSet = JsonCodec.read(response.body(), OidcJwkSet.class);
            long maxAge = 300L;
            String cacheControl = response.headers().firstValue("Cache-Control").orElse("");
            for (String token : cacheControl.split(",")) {
                String trimmed = token.trim().toLowerCase();
                if (trimmed.startsWith("max-age=")) {
                    maxAge = Long.parseLong(trimmed.substring("max-age=".length()));
                }
            }
            cachedKeys = new CachedKeys(
                    jwkSet.keys().stream().collect(Collectors.toUnmodifiableMap(OidcJwk::kid, key -> key)),
                    Instant.now(clock).plusSeconds(maxAge)
            );
            return cachedKeys;
        } catch (IOException e) {
            throw new ChatException(HttpStatus.UNAUTHORIZED, "Failed to read OIDC signing keys");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ChatException(HttpStatus.UNAUTHORIZED, "OIDC signing key request was interrupted");
        }
    }

    private String decode(String encoded) {
        return new String(Base64.getUrlDecoder().decode(encoded), StandardCharsets.UTF_8);
    }

    private String normalizeEmail(String email) {
        return email == null ? null : email.trim().toLowerCase();
    }

    private String form(Map<String, String> values) {
        return values.entrySet().stream()
                .map(entry -> URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8)
                        + "=" + URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8))
                .collect(Collectors.joining("&"));
    }

    private record JwtHeader(String alg, String kid, String typ) {
    }

    private record CachedKeys(Map<String, OidcJwk> keys, Instant expiresAt) {
    }
}
