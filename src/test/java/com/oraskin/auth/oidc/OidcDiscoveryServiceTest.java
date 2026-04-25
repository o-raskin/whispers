package com.oraskin.auth.oidc;

import com.oraskin.chat.service.ChatException;
import com.oraskin.common.http.HttpStatus;
import com.oraskin.common.json.JsonCodec;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static com.oraskin.auth.oidc.OidcTestSupport.discoveryDocument;
import static com.oraskin.auth.oidc.OidcTestSupport.jsonResponse;
import static com.oraskin.auth.oidc.OidcTestSupport.providerConfig;
import static com.oraskin.auth.oidc.OidcTestSupport.setField;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OidcDiscoveryServiceTest {

    @Test
    void getDiscoveryDocumentFetchesAndCachesDocument() throws Exception {
        HttpClient httpClient = mock(HttpClient.class);
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(jsonResponse(200, JsonCodec.write(discoveryDocument())));

        OidcDiscoveryService service = new OidcDiscoveryService(providerConfig());
        setField(service, OidcDiscoveryService.class, "httpClient", httpClient);

        OidcDiscoveryDocument first = service.getDiscoveryDocument();
        OidcDiscoveryDocument second = service.getDiscoveryDocument();

        assertThat(first).isSameAs(second);
        assertThat(first.tokenEndpoint()).isEqualTo("https://oauth2.googleapis.com/token");
        verify(httpClient, times(1)).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
    }

    @Test
    void getDiscoveryDocumentWrapsIoFailures() throws Exception {
        HttpClient httpClient = mock(HttpClient.class);
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenThrow(new IOException("network down"));

        OidcDiscoveryService service = new OidcDiscoveryService(providerConfig());
        setField(service, OidcDiscoveryService.class, "httpClient", httpClient);

        assertThatThrownBy(service::getDiscoveryDocument)
                .isInstanceOf(ChatException.class)
                .satisfies(throwable -> {
                    ChatException exception = (ChatException) throwable;
                    assertThat(exception.status()).isEqualTo(HttpStatus.UNAUTHORIZED);
                    assertThat(exception).hasMessageContaining("Failed to read OIDC discovery document");
                    assertThat(exception).hasMessageContaining("IOException: network down");
                });
    }
}
