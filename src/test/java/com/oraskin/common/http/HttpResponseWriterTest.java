package com.oraskin.common.http;

import com.oraskin.auth.config.FrontendConfig;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class HttpResponseWriterTest {

    @Test
    void writeJsonIncludesCorsHeadersPayloadAndRepeatedHeaders() throws Exception {
        HttpResponseWriter writer = new HttpResponseWriter(new FrontendConfig(URI.create("https://client.example")));
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        writer.writeJson(
                output,
                HttpStatus.CREATED,
                Map.of("status", "created"),
                Map.of("Set-Cookie", List.of("a=b", "c=d"))
        );

        String response = output.toString(StandardCharsets.UTF_8);
        assertThat(response).startsWith("HTTP/1.1 201 Created");
        assertThat(response).contains("Access-Control-Allow-Origin: https://client.example");
        assertThat(response).contains("Access-Control-Allow-Methods: GET, POST, DELETE, OPTIONS");
        assertThat(response).contains("Set-Cookie: a=b");
        assertThat(response).contains("Set-Cookie: c=d");
        assertThat(response).contains("{\"status\":\"created\"}");
    }

    @Test
    void writeEmptyProducesZeroLengthResponses() throws Exception {
        HttpResponseWriter writer = new HttpResponseWriter(new FrontendConfig(URI.create("https://client.example")));
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        writer.writeEmpty(output, HttpStatus.OK, Map.of("X-Test", List.of("1")));

        String response = output.toString(StandardCharsets.UTF_8);
        assertThat(response).startsWith("HTTP/1.1 200 OK");
        assertThat(response).contains("X-Test: 1");
        assertThat(response).contains("Content-Length: 0");
    }
}
