package com.oraskin.common.mvc;

import java.lang.reflect.Method;
import java.util.Map;

public record ControllerMethod(
        Object controller,
        Method method,
        String requestMethod,
        String pathPattern,
        Map<String, String> pathVariables,
        boolean publicEndpoint
) {
}
