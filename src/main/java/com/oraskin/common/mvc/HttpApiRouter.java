package com.oraskin.common.mvc;

import com.oraskin.chat.service.ChatException;
import com.oraskin.common.http.ErrorResponse;
import com.oraskin.common.http.HttpRequest;
import com.oraskin.common.http.HttpResponseWriter;
import com.oraskin.common.http.HttpStatus;
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

    public HttpApiRouter(List<Object> controllers, HttpResponseWriter httpResponseWriter) {
        this.controllerMethods = scanControllers(controllers);
        this.controllerMethodInvoker = new ControllerMethodInvoker();
        this.controllerResultWriter = new ControllerResultWriter(httpResponseWriter);
        this.httpResponseWriter = httpResponseWriter;
    }

    public void route(HttpRequest request, OutputStream output) throws IOException {
        try {
            ControllerResult result = invoke(request, null, output);
            if (result != null) {
                controllerResultWriter.writeHttp(output, result);
                return;
            }
            httpResponseWriter.writeJson(output, HttpStatus.NOT_FOUND, new ErrorResponse("Not found"));
        } catch (ChatException e) {
            httpResponseWriter.writeJson(output, e.status(), new ErrorResponse(e.getMessage()));
        } catch (IOException e) {
            httpResponseWriter.writeJson(output, HttpStatus.BAD_REQUEST, new ErrorResponse(e.getMessage()));
        } catch (Exception e) {
            httpResponseWriter.writeJson(output, HttpStatus.BAD_REQUEST, new ErrorResponse(e.getMessage()));
        }
    }

    public ControllerResult invoke(HttpRequest request, Socket socket, OutputStream output) throws Exception {
        ControllerMethod controllerMethod = findControllerMethod(request);
        if (controllerMethod == null) {
            return null;
        }
        return controllerMethodInvoker.invoke(controllerMethod, request, null, request.body(), socket, output);
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
                        pathVariables
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
                        Map.of()
                ));
            }
        }
        return List.copyOf(methods);
    }
}
