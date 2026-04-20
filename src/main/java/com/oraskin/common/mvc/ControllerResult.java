package com.oraskin.common.mvc;

import com.oraskin.common.http.HttpStatus;

import java.util.List;
import java.util.Map;

public record ControllerResult(HttpStatus status, Object body, Map<String, List<String>> headers) {

    public static ControllerResult body(Object body) {
        return new ControllerResult(HttpStatus.OK, body, Map.of());
    }

    public static ControllerResult created(Object body) {
        return new ControllerResult(HttpStatus.CREATED, body, Map.of());
    }

    public static ControllerResult withStatus(HttpStatus status, Object body) {
        return new ControllerResult(status, body, Map.of());
    }

    public static ControllerResult withHeaders(HttpStatus status, Object body, Map<String, List<String>> headers) {
        return new ControllerResult(status, body, headers);
    }

    public static ControllerResult empty() {
        return new ControllerResult(HttpStatus.OK, null, Map.of());
    }
}
