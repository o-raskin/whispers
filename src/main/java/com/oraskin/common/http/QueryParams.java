package com.oraskin.common.http;

import java.io.IOException;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

public final class QueryParams {

    private final Map<String, String> values;

    private QueryParams(Map<String, String> values) {
        this.values = Map.copyOf(values);
    }

    public static QueryParams fromTarget(String target) {
        URI uri = URI.create(target);
        String query = uri.getRawQuery();
        if (query == null || query.isBlank()) {
            return new QueryParams(Map.of());
        }

        Map<String, String> values = new LinkedHashMap<>();
        for (String pair : query.split("&")) {
            int separator = pair.indexOf('=');
            String key = separator >= 0 ? pair.substring(0, separator) : pair;
            String rawValue = separator >= 0 ? pair.substring(separator + 1) : "";
            String decodedValue = URLDecoder.decode(rawValue, StandardCharsets.UTF_8);
            if (!key.isBlank() && !decodedValue.isBlank()) {
                values.put(key, decodedValue);
            }
        }
        return new QueryParams(values);
    }

    public String required(String key) throws IOException {
        String value = values.get(key);
        if (value == null || value.isBlank()) {
            throw new IOException("Missing query parameter: " + key);
        }
        return value;
    }

    public String get(String key) {
        String value = values.get(key);
        return value == null || value.isBlank() ? null : value;
    }
}
