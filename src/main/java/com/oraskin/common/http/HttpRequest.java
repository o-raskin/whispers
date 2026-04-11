package com.oraskin.common.http;

import com.oraskin.common.http.QueryParams;

import java.util.Locale;
import java.util.Map;

public record HttpRequest(String method, String path, QueryParams params, Map<String, String> headers, String body) {

    public String header(String name) {
        return headers.get(name.toLowerCase(Locale.ROOT));
    }
}
