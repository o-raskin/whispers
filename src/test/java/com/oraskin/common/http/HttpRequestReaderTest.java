package com.oraskin.common.http;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class HttpRequestReaderTest {

    @Test
    void readParsesRequestLineHeadersQueryParametersAndBody() throws Exception {
        HttpRequestReader reader = new HttpRequestReader();
        byte[] requestBytes = (
                "POST /messages?chatId=7&text=hello%20world HTTP/1.1\r\n"
                        + "Host: localhost\r\n"
                        + "Authorization: Bearer abc\r\n"
                        + "Content-Length: 13\r\n"
                        + "\r\n"
                        + "{\"text\":\"hi\"}"
        ).getBytes(StandardCharsets.UTF_8);

        HttpRequest request = reader.read(new ByteArrayInputStream(requestBytes));

        assertThat(request.method()).isEqualTo("POST");
        assertThat(request.path()).isEqualTo("/messages");
        assertThat(request.params().get("chatId")).isEqualTo("7");
        assertThat(request.params().get("text")).isEqualTo("hello world");
        assertThat(request.header("authorization")).isEqualTo("Bearer abc");
        assertThat(request.body()).isEqualTo("{\"text\":\"hi\"}");
    }

    @Test
    void readRejectsInvalidRequestLinesAndTruncatedBodies() {
        HttpRequestReader reader = new HttpRequestReader();
        byte[] invalidRequest = "BROKEN\r\n\r\n".getBytes(StandardCharsets.UTF_8);
        byte[] truncatedBody = (
                "POST /messages HTTP/1.1\r\n"
                        + "Content-Length: 5\r\n"
                        + "\r\n"
                        + "abc"
        ).getBytes(StandardCharsets.UTF_8);

        IOException invalidLine = assertThrows(IOException.class, () -> reader.read(new ByteArrayInputStream(invalidRequest)));
        EOFException truncated = assertThrows(EOFException.class, () -> reader.read(new ByteArrayInputStream(truncatedBody)));

        assertThat(invalidLine).hasMessage("Invalid HTTP request line.");
        assertThat(truncated).hasMessage("Connection closed before request body completed.");
    }
}
