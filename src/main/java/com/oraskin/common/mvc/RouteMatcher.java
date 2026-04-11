package com.oraskin.common.mvc;

import java.util.LinkedHashMap;
import java.util.Map;

public final class RouteMatcher {

    private RouteMatcher() {
    }

    public static Map<String, String> match(String pattern, String path) {
        String[] patternParts = normalize(pattern).split("/");
        String[] pathParts = normalize(path).split("/");

        if (patternParts.length != pathParts.length) {
            return null;
        }

        Map<String, String> variables = new LinkedHashMap<>();
        for (int i = 0; i < patternParts.length; i++) {
            String patternPart = patternParts[i];
            String pathPart = pathParts[i];

            if (patternPart.startsWith("{") && patternPart.endsWith("}")) {
                variables.put(patternPart.substring(1, patternPart.length() - 1), pathPart);
                continue;
            }

            if (!patternPart.equals(pathPart)) {
                return null;
            }
        }
        return variables;
    }

    private static String normalize(String value) {
        if (value == null || value.isBlank() || "/".equals(value)) {
            return "";
        }
        String normalized = value.startsWith("/") ? value.substring(1) : value;
        return normalized.endsWith("/") ? normalized.substring(0, normalized.length() - 1) : normalized;
    }
}
