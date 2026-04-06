package com.oraskin.connection.util;

import com.oraskin.connection.dto.HttpRequest;

import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public final class HttpRequestReader {

    public HttpRequest read(InputStream input) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        int matched = 0;
        while (matched < 4) {
            int next = input.read();
            if (next < 0) {
                throw new EOFException("Connection closed before request completed.");
            }
            buffer.write(next);
            matched = switch (matched) {
                case 0 -> next == '\r' ? 1 : 0;
                case 1 -> next == '\n' ? 2 : 0;
                case 2 -> next == '\r' ? 3 : 0;
                case 3 -> next == '\n' ? 4 : 0;
                default -> 0;
            };
        }

        String requestText = buffer.toString(StandardCharsets.ISO_8859_1);
        String[] lines = requestText.split("\r\n");
        if (lines.length == 0) {
            throw new IOException("Empty HTTP request.");
        }

        String[] requestLine = lines[0].split(" ");
        if (requestLine.length < 3) {
            throw new IOException("Invalid HTTP request line.");
        }

        Map<String, String> headers = new LinkedHashMap<>();
        for (int i = 1; i < lines.length; i++) {
            String line = lines[i];
            if (line.isEmpty()) {
                break;
            }
            int separator = line.indexOf(':');
            if (separator <= 0) {
                continue;
            }
            String name = line.substring(0, separator).trim().toLowerCase(Locale.ROOT);
            String value = line.substring(separator + 1).trim();
            headers.put(name, value);
        }
        return new HttpRequest(requestLine[0], requestLine[1], headers);
    }
}
