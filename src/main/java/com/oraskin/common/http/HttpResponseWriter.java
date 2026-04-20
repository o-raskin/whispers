package com.oraskin.common.http;

import com.oraskin.auth.config.FrontendConfig;
import com.oraskin.common.json.JsonCodec;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class HttpResponseWriter {

    private final FrontendConfig frontendConfig;

    public HttpResponseWriter(FrontendConfig frontendConfig) {
        this.frontendConfig = Objects.requireNonNull(frontendConfig);
    }

    public void writeJson(OutputStream output, HttpStatus status, Object body) throws IOException {
        writeJson(output, status, body, Map.of());
    }

    public void writeJson(OutputStream output, HttpStatus status, Object body, Map<String, List<String>> headers) throws IOException {
        byte[] payload = JsonCodec.write(body).getBytes(StandardCharsets.UTF_8);
        StringBuilder response = new StringBuilder()
                .append("HTTP/1.1 ").append(status.code()).append(' ').append(status.reasonPhrase()).append("\r\n")
                .append("Content-Type: application/json; charset=UTF-8\r\n")
                .append(corsHeaders());
        appendHeaders(response, headers);
        response.append("Content-Length: ").append(payload.length).append("\r\n")
                .append("Connection: close\r\n")
                .append("\r\n");
        output.write(response.toString().getBytes(StandardCharsets.ISO_8859_1));
        output.write(payload);
        output.flush();
    }

    public void writeEmpty(OutputStream output, HttpStatus status, Map<String, List<String>> headers) throws IOException {
        StringBuilder response = new StringBuilder()
                .append("HTTP/1.1 ").append(status.code()).append(' ').append(status.reasonPhrase()).append("\r\n")
                .append(corsHeaders());
        appendHeaders(response, headers);
        response.append("Content-Length: 0\r\n")
                .append("Connection: close\r\n")
                .append("\r\n");
        output.write(response.toString().getBytes(StandardCharsets.ISO_8859_1));
        output.flush();
    }

    private void appendHeaders(StringBuilder response, Map<String, List<String>> headers) {
        for (Map.Entry<String, List<String>> entry : headers.entrySet()) {
            for (String value : entry.getValue()) {
                response.append(entry.getKey()).append(": ").append(value).append("\r\n");
            }
        }
    }

    private String corsHeaders() {
        return ""
                + "Access-Control-Allow-Origin: " + frontendConfig.origin() + "\r\n"
                + "Access-Control-Allow-Credentials: true\r\n"
                + "Access-Control-Allow-Headers: Authorization, Content-Type\r\n"
                + "Access-Control-Allow-Methods: GET, POST, OPTIONS\r\n"
                + "Vary: Origin\r\n";
    }
}
