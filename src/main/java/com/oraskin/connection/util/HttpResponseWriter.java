package com.oraskin.connection.util;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

public final class HttpResponseWriter {

    public void writeText(OutputStream output, int statusCode, String statusText, String body) throws IOException {
        byte[] payload = body.getBytes(StandardCharsets.UTF_8);
        String response = ""
                + "HTTP/1.1 " + statusCode + " " + statusText + "\r\n"
                + "Content-Type: text/plain; charset=UTF-8\r\n"
                + "Content-Length: " + payload.length + "\r\n"
                + "Connection: close\r\n"
                + "\r\n";
        output.write(response.getBytes(StandardCharsets.ISO_8859_1));
        output.write(payload);
        output.flush();
    }

    public void writeJson(OutputStream output, int statusCode, String body) throws IOException {
        byte[] payload = body.getBytes(StandardCharsets.UTF_8);
        String response = ""
                + "HTTP/1.1 " + statusCode + " OK\r\n"
                + "Content-Type: application/json; charset=UTF-8\r\n"
                + "Access-Control-Allow-Origin: *\r\n"
                + "Content-Length: " + payload.length + "\r\n"
                + "Connection: close\r\n"
                + "\r\n";
        output.write(response.getBytes(StandardCharsets.ISO_8859_1));
        output.write(payload);
        output.flush();
    }
}
