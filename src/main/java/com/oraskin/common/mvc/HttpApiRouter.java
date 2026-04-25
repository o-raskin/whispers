package com.oraskin.common.mvc;

import com.oraskin.common.auth.RequestAuthenticationService;
import com.oraskin.common.http.ErrorResponse;
import com.oraskin.common.http.HttpRequest;
import com.oraskin.common.http.HttpResponseWriter;
import com.oraskin.common.http.HttpStatus;
import com.oraskin.common.http.TransportErrorMapper;
import com.oraskin.common.mvc.annotation.PublicEndpoint;
import com.oraskin.common.mvc.annotation.RequestMapping;
import com.oraskin.common.mvc.annotation.RestController;

import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class HttpApiRouter {

    private final List<ControllerMethod> controllerMethods;
    private final ControllerMethodInvoker controllerMethodInvoker;
    private final ControllerResultWriter controllerResultWriter;
    private final HttpResponseWriter httpResponseWriter;
    private final RequestAuthenticationService requestAuthenticationService;

    public HttpApiRouter(
            List<Object> controllers,
            HttpResponseWriter httpResponseWriter,
            RequestAuthenticationService requestAuthenticationService
    ) {
        this.controllerMethods = scanControllers(controllers);
        this.controllerMethodInvoker = new ControllerMethodInvoker();
        this.controllerResultWriter = new ControllerResultWriter(httpResponseWriter);
        this.httpResponseWriter = httpResponseWriter;
        this.requestAuthenticationService = requestAuthenticationService;
    }

    public void route(HttpRequest request, OutputStream output) throws IOException {
        try {
            if ("OPTIONS".equals(request.method())) {
                httpResponseWriter.writeEmpty(output, HttpStatus.OK, Map.of());
                return;
            }
            ControllerResult result = invoke(request, null, output);
            if (result != null) {
                controllerResultWriter.writeHttp(output, result);
                return;
            }
            httpResponseWriter.writeJson(output, HttpStatus.NOT_FOUND, new ErrorResponse("Not found"));
        } catch (Exception e) {
            HttpStatus status = TransportErrorMapper.httpStatus(e);
            if (status == HttpStatus.INTERNAL_SERVER_ERROR) {
                logUnexpectedFailure("HTTP request failed", e);
            }
            httpResponseWriter.writeJson(output, status, new ErrorResponse(TransportErrorMapper.clientMessage(e)));
        }
    }

    public ControllerResult invoke(HttpRequest request, Socket socket, OutputStream output) throws Exception {
        ControllerMethod controllerMethod = findControllerMethod(request);
        if (controllerMethod == null) {
            return null;
        }
        HttpRequest authenticatedRequest = authenticateRequest(request, controllerMethod);
        return controllerMethodInvoker.invoke(controllerMethod, authenticatedRequest, null, authenticatedRequest.body(), socket, output);
    }

    private HttpRequest authenticateRequest(HttpRequest request, ControllerMethod controllerMethod) {
        if (controllerMethod.publicEndpoint()) {
            return requestAuthenticationService.authenticateIfPresent(request);
        }
        return requestAuthenticationService.authenticateRequired(request);
    }

    private ControllerMethod findControllerMethod(HttpRequest request) {
        for (ControllerMethod controllerMethod : controllerMethods) {
            Map<String, String> pathVariables = RouteMatcher.match(controllerMethod.pathPattern(), request.path());
            if (controllerMethod.requestMethod().equals(request.method()) && pathVariables != null) {
                return new ControllerMethod(
                        controllerMethod.controller(),
                        controllerMethod.method(),
                        controllerMethod.requestMethod(),
                        controllerMethod.pathPattern(),
                        pathVariables,
                        controllerMethod.publicEndpoint()
                );
            }
        }
        return null;
    }

    private static List<ControllerMethod> scanControllers(List<Object> controllers) {
        List<ControllerMethod> methods = new ArrayList<>();
        for (Object controller : controllers) {
            RestController annotation = controller.getClass().getAnnotation(RestController.class);
            if (annotation == null) {
                continue;
            }
            String basePath = annotation.value();
            for (Method method : controller.getClass().getDeclaredMethods()) {
                RequestMapping mapping = method.getAnnotation(RequestMapping.class);
                if (mapping == null) {
                    continue;
                }
                String pathPattern = basePath + mapping.value();
                methods.add(new ControllerMethod(
                        controller,
                        method,
                        mapping.method(),
                        pathPattern,
                        Map.of(),
                        method.isAnnotationPresent(PublicEndpoint.class)
                ));
            }
        }
        return List.copyOf(methods);
    }

    private void logUnexpectedFailure(String message, Exception failure) {
        System.err.println(message + ": " + failure.getClass().getSimpleName());
    }
}
