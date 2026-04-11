package com.oraskin.common.http;

import com.oraskin.common.http.HttpStatus;
import com.oraskin.common.json.JsonCodec;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

public final class HttpResponseWriter {

    public void writeText(OutputStream output, HttpStatus status, String body) throws IOException {
        byte[] payload = body.getBytes(StandardCharsets.UTF_8);
        String response = ""
                + "HTTP/1.1 " + status.code() + " " + status.reasonPhrase() + "\r\n"
                + "Content-Type: text/plain; charset=UTF-8\r\n"
                + "Content-Length: " + payload.length + "\r\n"
                + "Connection: close\r\n"
                + "\r\n";
        output.write(response.getBytes(StandardCharsets.ISO_8859_1));
        output.write(payload);
        output.flush();
    }

    public void writeJson(OutputStream output, HttpStatus status, Object body) throws IOException {
        byte[] payload = JsonCodec.write(body).getBytes(StandardCharsets.UTF_8);
        String response = ""
                + "HTTP/1.1 " + status.code() + " " + status.reasonPhrase() + "\r\n"
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
