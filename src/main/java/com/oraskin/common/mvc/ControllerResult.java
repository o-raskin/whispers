package com.oraskin.common.mvc;

public record ControllerResult(Object body) {

    public static ControllerResult body(Object body) {
        return new ControllerResult(body);
    }

    public static ControllerResult empty() {
        return new ControllerResult(null);
    }
}
