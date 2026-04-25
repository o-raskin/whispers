package com.oraskin.common.http;

import com.oraskin.auth.config.FrontendConfig;
import com.oraskin.chat.service.ChatException;
import com.oraskin.common.http.cookie.ResponseCookie;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CommonHttpTest {

    @Test
    void httpRequestReaderParsesRequestLineHeadersQueryParametersAndBody() throws Exception {
        HttpRequestReader httpRequestReader = new HttpRequestReader();
        byte[] requestBytes = (
                "POST /messages?chatId=7&text=hello%20world HTTP/1.1\r\n"
                        + "Host: localhost\r\n"
                        + "Authorization: Bearer abc\r\n"
                        + "Content-Length: 13\r\n"
                        + "\r\n"
                        + "{\"text\":\"hi\"}"
        ).getBytes(StandardCharsets.UTF_8);

        HttpRequest request = httpRequestReader.read(new ByteArrayInputStream(requestBytes));

        assertEquals("POST", request.method());
        assertEquals("/messages", request.path());
        assertEquals("7", request.params().get("chatId"));
        assertEquals("hello world", request.params().get("text"));
        assertEquals("Bearer abc", request.header("authorization"));
        assertEquals("{\"text\":\"hi\"}", request.body());
    }

    @Test
    void httpRequestReaderRejectsTruncatedBodies() {
        HttpRequestReader httpRequestReader = new HttpRequestReader();
        byte[] requestBytes = (
                "POST /messages HTTP/1.1\r\n"
                        + "Content-Length: 5\r\n"
                        + "\r\n"
                        + "abc"
        ).getBytes(StandardCharsets.UTF_8);

        assertThrows(EOFException.class, () -> httpRequestReader.read(new ByteArrayInputStream(requestBytes)));
    }

    @Test
    void httpRequestReaderRejectsInvalidRequestLines() {
        HttpRequestReader httpRequestReader = new HttpRequestReader();
        byte[] requestBytes = "BROKEN\r\n\r\n".getBytes(StandardCharsets.UTF_8);

        IOException exception = assertThrows(IOException.class, () -> httpRequestReader.read(new ByteArrayInputStream(requestBytes)));

        assertEquals("Invalid HTTP request line.", exception.getMessage());
    }

    @Test
    void queryParamsDecodeValuesAndRequiredValidation() throws Exception {
        QueryParams queryParams = QueryParams.fromTarget("/users?name=alice%20example&empty=&encoded=%2Btest");

        assertEquals("alice example", queryParams.get("name"));
        assertNull(queryParams.get("empty"));
        assertEquals("+test", queryParams.required("encoded"));
        assertThrows(java.io.IOException.class, () -> queryParams.required("missing"));
    }

    @Test
    void httpRequestReadsNamedCookies() {
        HttpRequest request = new HttpRequest(
                "GET",
                "/auth/refresh",
                QueryParams.fromTarget("/auth/refresh"),
                Map.of("cookie", "theme=dark; whispers_refresh_token=refresh-token; other=value"),
                null,
                null
        );

        assertEquals("refresh-token", request.cookie("whispers_refresh_token"));
        assertNull(request.cookie("missing"));
    }

    @Test
    void responseCookieSerializesEnabledAttributes() {
        ResponseCookie responseCookie = new ResponseCookie(
                "whispers_refresh_token",
                "refresh-token",
                true,
                true,
                "Lax",
                "/auth",
                Duration.ofMinutes(30)
        );

        assertEquals(
                "whispers_refresh_token=refresh-token; Path=/auth; Max-Age=1800; HttpOnly; Secure; SameSite=Lax",
                responseCookie.serialize()
        );
    }

    @Test
    void transportErrorMapperDistinguishesExpectedAndUnexpectedFailures() {
        ChatException chatException = new ChatException(HttpStatus.CONFLICT, "already connected");

        assertEquals(HttpStatus.CONFLICT, TransportErrorMapper.httpStatus(chatException));
        assertEquals("already connected", TransportErrorMapper.clientMessage(chatException));
        assertEquals(HttpStatus.BAD_REQUEST, TransportErrorMapper.httpStatus(new java.io.IOException("bad request")));
        assertEquals("bad request", TransportErrorMapper.clientMessage(new java.io.IOException("bad request")));
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, TransportErrorMapper.httpStatus(new IllegalStateException("secret")));
        assertEquals("Internal server error", TransportErrorMapper.clientMessage(new IllegalStateException("secret")));
    }

    @Test
    void httpResponseWriterWritesJsonAndEmptyResponsesWithCorsHeaders() throws Exception {
        HttpResponseWriter httpResponseWriter = new HttpResponseWriter(new FrontendConfig(URI.create("https://client.example")));
        ByteArrayOutputStream jsonOutput = new ByteArrayOutputStream();

        httpResponseWriter.writeJson(
                jsonOutput,
                HttpStatus.CREATED,
                Map.of("status", "created"),
                Map.of("Set-Cookie", List.of("a=b"))
        );

        String jsonResponse = jsonOutput.toString(StandardCharsets.UTF_8);
        assertTrue(jsonResponse.startsWith("HTTP/1.1 201 Created"));
        assertTrue(jsonResponse.contains("Access-Control-Allow-Origin: https://client.example"));
        assertTrue(jsonResponse.contains("Set-Cookie: a=b"));
        assertTrue(jsonResponse.contains("{\"status\":\"created\"}"));

        ByteArrayOutputStream emptyOutput = new ByteArrayOutputStream();
        httpResponseWriter.writeEmpty(emptyOutput, HttpStatus.OK, Map.of("X-Test", List.of("1")));

        String emptyResponse = emptyOutput.toString(StandardCharsets.UTF_8);
        assertTrue(emptyResponse.startsWith("HTTP/1.1 200 OK"));
        assertTrue(emptyResponse.contains("X-Test: 1"));
        assertTrue(emptyResponse.contains("Content-Length: 0"));
    }

    @Test
    void httpResponseWriterRepeatsMultiValueHeaders() throws Exception {
        HttpResponseWriter httpResponseWriter = new HttpResponseWriter(new FrontendConfig(URI.create("https://client.example")));
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        httpResponseWriter.writeJson(
                output,
                HttpStatus.OK,
                Map.of("status", "ok"),
                Map.of("Set-Cookie", List.of("a=b", "c=d"))
        );

        String response = output.toString(StandardCharsets.UTF_8);
        assertTrue(response.contains("Set-Cookie: a=b"));
        assertTrue(response.contains("Set-Cookie: c=d"));
    }
}
