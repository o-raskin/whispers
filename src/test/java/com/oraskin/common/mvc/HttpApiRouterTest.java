package com.oraskin.common.mvc;

import com.oraskin.auth.config.AuthConfig;
import com.oraskin.auth.config.FrontendConfig;
import com.oraskin.auth.persistence.AccessTokenStore;
import com.oraskin.auth.service.AccessTokenService;
import com.oraskin.common.auth.AuthenticatedUser;
import com.oraskin.common.auth.RequestAuthenticationService;
import com.oraskin.common.http.HttpRequest;
import com.oraskin.common.http.HttpResponseWriter;
import com.oraskin.common.http.QueryParams;
import com.oraskin.common.mvc.annotation.PublicEndpoint;
import com.oraskin.common.mvc.annotation.RequestMapping;
import com.oraskin.common.mvc.annotation.RequestParam;
import com.oraskin.common.mvc.annotation.RestController;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class HttpApiRouterTest {

    @Test
    void unexpectedControllerFailuresReturnInternalServerErrorWithoutLeakingDetails() throws Exception {
        HttpApiRouter router = routerFor(new FailingController());
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ByteArrayOutputStream errorOutput = new ByteArrayOutputStream();
        PrintStream originalError = System.err;

        try {
            System.setErr(new PrintStream(errorOutput, true, StandardCharsets.UTF_8));
            router.route(request("GET", "/test/fail"), output);
        } finally {
            System.setErr(originalError);
        }

        String response = output.toString(StandardCharsets.UTF_8);
        assertThat(response).startsWith("HTTP/1.1 500 Internal Server Error");
        assertThat(response).contains("{\"error\":\"Internal server error\"}");
        assertThat(response).doesNotContain("database password");
        assertThat(errorOutput.toString(StandardCharsets.UTF_8)).contains("HTTP request failed: IllegalStateException").doesNotContain("database password");
    }

    @Test
    void missingRequiredQueryParameterStillReturnsBadRequest() throws Exception {
        HttpApiRouter router = routerFor(new QueryParamController());
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        router.route(request("GET", "/test/query"), output);

        String response = output.toString(StandardCharsets.UTF_8);
        assertThat(response).startsWith("HTTP/1.1 400 Bad Request");
        assertThat(response).contains("{\"error\":\"Missing query parameter: chatId\"}");
    }

    private HttpApiRouter routerFor(Object controller) {
        FrontendConfig frontendConfig = new FrontendConfig(URI.create("http://localhost:5173"));
        HttpResponseWriter responseWriter = new HttpResponseWriter(frontendConfig);
        AuthConfig authConfig = new AuthConfig(
                Duration.ofMinutes(5),
                Duration.ofDays(30),
                "whispers",
                URI.create("http://localhost:5173/")
        );
        AccessTokenService accessTokenService = new AccessTokenService(new NoopAccessTokenStore(), authConfig, Clock.systemUTC());
        return new HttpApiRouter(
                List.of(controller),
                responseWriter,
                new RequestAuthenticationService(accessTokenService)
        );
    }

    private HttpRequest request(String method, String target) {
        return new HttpRequest(method, URI.create(target).getPath(), QueryParams.fromTarget(target), Map.of(), null, null);
    }

    @RestController("/test")
    private static final class FailingController {

        @PublicEndpoint
        @RequestMapping(method = "GET", value = "/fail")
        public String fail() {
            throw new IllegalStateException("database password leaked");
        }
    }

    @RestController("/test")
    private static final class QueryParamController {

        @PublicEndpoint
        @RequestMapping(method = "GET", value = "/query")
        public String query(@RequestParam("chatId") long chatId) {
            return String.valueOf(chatId);
        }
    }

    private static final class NoopAccessTokenStore implements AccessTokenStore {

        @Override
        public void createForUser(String tokenHash, String userId, String provider, String providerSubject, Instant issuedAt, Instant expiresAt) {
        }

        @Override
        public AuthenticatedUser findActiveUserByTokenHash(String tokenHash, Instant now) {
            return null;
        }

        @Override
        public void revoke(String tokenHash, Instant revokedAt) {
        }
    }
}
