package com.oraskin.auth.oidc;

import com.oraskin.auth.config.OidcProviderConfig;
import com.oraskin.chat.service.ChatException;
import com.oraskin.common.http.HttpStatus;
import com.oraskin.common.json.JsonCodec;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Objects;

public final class OidcDiscoveryService {

    private final OidcProviderConfig providerConfig;
    private final HttpClient httpClient;
    private volatile OidcDiscoveryDocument cachedDocument;

    public OidcDiscoveryService(OidcProviderConfig providerConfig) {
        this.providerConfig = Objects.requireNonNull(providerConfig);
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    }

    public OidcDiscoveryDocument getDiscoveryDocument() {
        if (cachedDocument != null) {
            return cachedDocument;
        }
        synchronized (this) {
            if (cachedDocument != null) {
                return cachedDocument;
            }
            String issuer = providerConfig.issuerUri().toString().replaceAll("/$", "");
            URI discoveryUri = URI.create(issuer + "/.well-known/openid-configuration");
            HttpRequest request = HttpRequest.newBuilder(discoveryUri)
                    .timeout(Duration.ofSeconds(10))
                    .GET()
                    .build();
            try {
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() != 200) {
                    throw new ChatException(
                            HttpStatus.UNAUTHORIZED,
                            "OIDC discovery failed for provider " + providerConfig.provider()
                                    + " with status " + response.statusCode()
                    );
                }
                cachedDocument = JsonCodec.read(response.body(), OidcDiscoveryDocument.class);
                return cachedDocument;
            } catch (IOException e) {
                throw new ChatException(
                        HttpStatus.UNAUTHORIZED,
                        "Failed to read OIDC discovery document: "
                                + e.getClass().getSimpleName() + ": " + e.getMessage()
                );
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new ChatException(
                        HttpStatus.UNAUTHORIZED,
                        "OIDC discovery request was interrupted: "
                                + e.getClass().getSimpleName() + ": " + e.getMessage()
                );
            }
        }
    }
}
