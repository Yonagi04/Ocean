package com.yonagi.ocean.core.framework;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.yonagi.ocean.annotation.RequestBody;
import com.yonagi.ocean.annotation.RequestParam;
import com.yonagi.ocean.core.protocol.HttpRequest;
import com.yonagi.ocean.core.protocol.HttpResponse;
import com.yonagi.ocean.core.protocol.enums.HttpStatus;
import com.yonagi.ocean.core.protocol.enums.HttpVersion;
import com.yonagi.ocean.handler.RequestHandler;
import com.yonagi.ocean.handler.impl.InternalErrorHandler;
import com.yonagi.ocean.utils.JsonDeserializer;
import com.yonagi.ocean.utils.JsonSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.Collections;
import java.util.Map;

/**
 * @author Yonagi
 * @version 1.0
 * @program Ocean
 * @description
 * @date 2025/10/14 16:10
 */
public class MethodInvokingHandler implements RequestHandler {

    private static final Logger log = LoggerFactory.getLogger(MethodInvokingHandler.class);

    private final Object controllerInstance;
    private final Method handlerMethod;

    private static final String DEFAULT_VALUE_PLACEHOLDER = "\n\t\t\n\t\t\n\u0000\n\t\t\t\n\u0000\n\t\t\n\u0000\n\t\t\n\t\t\n";

    public MethodInvokingHandler(Object controllerInstance, Method handlerMethod) {
        this.controllerInstance = controllerInstance;
        this.handlerMethod = handlerMethod;
        this.handlerMethod.setAccessible(true);
    }

    @Override
    public void handle(HttpRequest request, OutputStream output) throws IOException {
        handle(request, output, true);
    }

    @Override
    public void handle(HttpRequest request, OutputStream output, boolean keepAlive) throws IOException {
        try {
            Object[] args = resolveMethodArguments(request, output, keepAlive);
            Object result = handlerMethod.invoke(controllerInstance, args);

            handleReturnValue(request, result, output, keepAlive);
        } catch (MissingRequiredParameterException e) {
            log.warn("Missing required parameter for method {}: {}", handlerMethod.getName(), e.getMessage());
            HttpResponse response = new HttpResponse.Builder()
                    .httpVersion(request.getHttpVersion())
                    .httpStatus(HttpStatus.BAD_REQUEST)
                    .contentType("text/plain")
                    .body(("Missing required parameter: " + e.getMessage()).getBytes())
                    .build();
            response.write(request, output, keepAlive);
            output.flush();
        } catch (JsonProcessingException e) {
            log.warn("Json processing error: {}", e.getMessage(), e);
            HttpResponse response = new HttpResponse.Builder()
                    .httpVersion(request.getHttpVersion())
                    .httpStatus(HttpStatus.BAD_REQUEST)
                    .contentType("text/plain")
                    .body(("Fail to deserialize body: " + e.getMessage()).getBytes())
                    .build();
            response.write(request, output, keepAlive);
            output.flush();
        } catch (BadRequestException e) {
            HttpResponse response = new HttpResponse.Builder()
                    .httpVersion(request.getHttpVersion())
                    .httpStatus(HttpStatus.BAD_REQUEST)
                    .contentType("text/plain")
                    .body(("Bad Request: " + e.getMessage()).getBytes())
                    .build();
            response.write(request, output, keepAlive);
            output.flush();
        } catch (IllegalAccessException | IllegalArgumentException e) {
            log.error("Illegal Access or Illegal Argument on Controller method {}: {}", handlerMethod.getName(), e.getMessage(), e);
            new InternalErrorHandler().handle(request, output, keepAlive);
        } catch (InvocationTargetException e) {
            log.error("Controller method {}.{} threw an exception: {}",
                    controllerInstance.getClass().getSimpleName(), handlerMethod.getName(), e.getTargetException().getMessage(), e.getTargetException());
            new InternalErrorHandler().handle(request, output, keepAlive);
        }
    }

    private Object[] resolveMethodArguments(HttpRequest request, OutputStream output, boolean keepAlive) throws MissingRequiredParameterException, JsonProcessingException, BadRequestException {
        Parameter[] parameters = handlerMethod.getParameters();
        Object[] args = new Object[parameters.length];

        @SuppressWarnings("unchecked")
        Map<String, String> pathVariables = (Map<String, String>) request.getAttribute("PathVariableAttributes");
        Map<String, String> queryParameters = request.getQueryParams() != null ? request.getQueryParams() : Collections.emptyMap();

        for (int i = 0; i < parameters.length; i++) {
            Parameter parameter = parameters[i];
            Class<?> paramType = parameter.getType();
            String paramName = parameter.getName();

            if (paramType.isAssignableFrom(HttpRequest.class)) {
                args[i] = request;
                continue;
            }
            RequestBody requestBody = parameter.getAnnotation(RequestBody.class);
            if (requestBody != null) {
                byte[] body = request.getBody();
                if ((body == null || body.length == 0)) {
                    if (requestBody.required()) {
                        throw new BadRequestException(
                                String.format("Request body (type: %s) is required but missing or empty.", paramType.getSimpleName()));
                    }
                    args[i] = null;
                } else {
                    args[i] = JsonDeserializer.deserialize(body, paramType);
                }
                continue;
            }

            String value = null;
            RequestParam requestParam = parameter.getAnnotation(RequestParam.class);
            if (requestParam != null) {
                String paramKey = requestParam.value().isEmpty() ? paramName : requestParam.value();
                value = queryParameters.get(paramKey);

                if (value == null || value.isEmpty()) {
                    if (!requestParam.defaultValue().equals(DEFAULT_VALUE_PLACEHOLDER)) {
                        value = requestParam.defaultValue();
                    } else if (requestParam.required()) {
                        throw new MissingRequiredParameterException(
                                String.format("Query parameter '%s' is required but not found.", paramKey));
                    }
                }
            } else if (pathVariables != null && pathVariables.containsKey(paramName)) {
                value = pathVariables.get(paramName);
            }

            if (value != null) {
                args[i] = convertValue(value, paramType);
            } else {
                if (paramType == String.class && value == null) {
                    args[i] = "";
                    log.debug("Parameter {} {} resolved to empty string.", paramType.getSimpleName(), paramName);
                } else if (!paramType.isPrimitive()) {
                    args[i] = null;
                    log.debug("Parameter {} {} resolved to null.", paramName, paramType.getSimpleName());
                } else {
                    args[i] = getDefaultValueForType(paramType);
                    log.warn("Unresolved primitive parameter: {} {} for method {}. Using default value.",
                            paramType.getSimpleName(), paramName, handlerMethod.getName());
                }
            }
        }
        return args;
    }

    private Object convertValue(String value, Class<?> targetType) {
        if (targetType == String.class) {
            return value;
        } else if (targetType == int.class || targetType == Integer.class) {
            try {
                return Integer.parseInt(value);
            } catch (NumberFormatException e) {
                log.warn("Invalid integer format for value: {}", value);
                return getDefaultValueForType(targetType);
            }
        } else if (targetType == long.class || targetType == Long.class) {
            try {
                return Long.parseLong(value);
            } catch (NumberFormatException e) {
                log.warn("Invalid long format for value: {}", value);
                return getDefaultValueForType(targetType);
            }
        } else if (targetType == boolean.class || targetType == Boolean.class) {
            return Boolean.parseBoolean(value);
        } else if (targetType == double.class || targetType == Double.class) {
            try {
                return Double.parseDouble(value);
            } catch (NumberFormatException e) {
                log.warn("Invalid double format for value: {}", value);
                return getDefaultValueForType(targetType);
            }
        } else if (targetType == float.class || targetType == Float.class) {
            try {
                return Float.parseFloat(value);
            } catch (NumberFormatException e) {
                log.warn("Invalid float format for value: {}", value);
                return getDefaultValueForType(targetType);
            }
        }
        log.warn("Unsupported conversion type: {}", targetType.getSimpleName());
        return value;
    }

    private Object getDefaultValueForType(Class<?> type) {
        if (type == int.class) {
            return 0;
        }
        if (type == long.class) {
            return 0L;
        }
        if (type == boolean.class) {
            return false;
        }
        if (type == double.class) {
            return 0.0d;
        }
        if (type == float.class) {
            return 0.0f;
        }
        if (type == String.class) {
            return "";
        }
        return null;
    }

    private void handleReturnValue(HttpRequest request,Object returnValue, OutputStream output, boolean keepAlive) throws IOException {
        if (handlerMethod.getReturnType() == void.class || returnValue == null) {
            log.debug("Controller method returned void or null, assuming response was handled manually.");
        } else {
            String jsonResponse = JsonSerializer.serialize(returnValue);

            HttpResponse response = new HttpResponse.Builder()
                    .httpVersion(HttpVersion.HTTP_1_1)
                    .httpStatus(HttpStatus.OK)
                    .contentType("application/json; charset=utf-8")
                    .body(jsonResponse.getBytes())
                    .build();
            response.write(request, output, keepAlive);
            output.flush();
            log.debug("Successfully wrote JSON response for return value: {}", handlerMethod.getReturnType().getSimpleName());
        }
    }

    private static class MissingRequiredParameterException extends Exception {
        public MissingRequiredParameterException(String message) {
            super(message);
        }
    }

    private static class BadRequestException extends Exception {
        public BadRequestException(String message) {
            super(message);
        }
    }
}
