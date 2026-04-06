package com.oraskin.connection.dto;

import java.net.URI;
import java.util.Locale;
import java.util.Map;

public record HttpRequest(String method, String target, Map<String, String> headers) {

    public String header(String name) {
        return headers.get(name.toLowerCase(Locale.ROOT));
    }

    public String path() {
        return URI.create(target).getPath();
    }
}
