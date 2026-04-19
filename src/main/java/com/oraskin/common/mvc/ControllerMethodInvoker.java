package com.oraskin.common.mvc;

import com.oraskin.common.http.HttpRequest;
import com.oraskin.common.http.QueryParams;
import com.oraskin.common.json.JsonCodec;
import com.oraskin.common.mvc.annotation.PathVariable;
import com.oraskin.common.mvc.annotation.RequestBody;
import com.oraskin.common.mvc.annotation.RequestParam;
import com.oraskin.user.session.ClientSession;

import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.net.Socket;
import java.util.Map;

public final class ControllerMethodInvoker {

    public ControllerResult invoke(
            ControllerMethod controllerMethod,
            HttpRequest request,
            ClientSession session,
            String payload,
            Socket socket,
            OutputStream output
    ) throws Exception {
        Object[] args = bindArguments(
                controllerMethod.method(),
                request,
                session,
                payload,
                controllerMethod.pathVariables(),
                socket,
                output
        );
        try {
            Object value = controllerMethod.method().invoke(controllerMethod.controller(), args);
            if (value == null) {
                return ControllerResult.empty();
            }
            return ControllerResult.body(value);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof Exception exception) {
                throw exception;
            }
            throw e;
        }
    }

    private Object[] bindArguments(
            Method method,
            HttpRequest request,
            ClientSession session,
            String payload,
            Map<String, String> pathVariables,
            Socket socket,
            OutputStream output
    ) throws IOException {
        Parameter[] parameters = method.getParameters();
        Object[] args = new Object[parameters.length];

        for (int i = 0; i < parameters.length; i++) {
            Parameter parameter = parameters[i];
            if (parameter.getType().equals(HttpRequest.class)) {
                args[i] = request;
                continue;
            }
            if (parameter.getType().equals(QueryParams.class)) {
                args[i] = request.params();
                continue;
            }
            if (parameter.getType().equals(ClientSession.class)) {
                args[i] = session;
                continue;
            }
            if (parameter.getType().equals(Socket.class)) {
                args[i] = socket;
                continue;
            }
            if (parameter.getType().equals(OutputStream.class)) {
                args[i] = output;
                continue;
            }
            if (parameter.isAnnotationPresent(RequestParam.class)) {
                String name = parameter.getAnnotation(RequestParam.class).value();
                args[i] = convertSimpleValue(request.params().required(name), parameter.getType());
                continue;
            }
            if (parameter.isAnnotationPresent(PathVariable.class)) {
                String name = parameter.getAnnotation(PathVariable.class).value();
                args[i] = convertSimpleValue(pathVariables.get(name), parameter.getType());
                continue;
            }
            if (parameter.isAnnotationPresent(RequestBody.class)) {
                args[i] = JsonCodec.read(payload, parameter.getType());
                continue;
            }
            throw new IllegalStateException("Unsupported controller parameter: " + parameter);
        }

        return args;
    }

    private Object convertSimpleValue(String value, Class<?> targetType) {
        if (targetType.equals(String.class)) {
            return value;
        }
        if (targetType.equals(long.class) || targetType.equals(Long.class)) {
            return Long.parseLong(value);
        }
        throw new IllegalStateException("Unsupported simple parameter type: " + targetType);
    }
}
