package com.yonagi.ocean.framework;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import com.yonagi.ocean.core.context.HttpContext;
import com.yonagi.ocean.core.protocol.enums.ContentType;
import com.yonagi.ocean.framework.annotation.PathVariable;
import com.yonagi.ocean.framework.annotation.RequestBody;
import com.yonagi.ocean.framework.annotation.RequestParam;
import com.yonagi.ocean.core.protocol.HttpRequest;
import com.yonagi.ocean.core.protocol.HttpResponse;
import com.yonagi.ocean.core.protocol.enums.HttpStatus;
import com.yonagi.ocean.core.protocol.enums.HttpVersion;
import com.yonagi.ocean.exception.BadRequestException;
import com.yonagi.ocean.exception.DeserializationException;
import com.yonagi.ocean.exception.MissingRequiredParameterException;
import com.yonagi.ocean.exception.UnsupportedMediaTypeException;
import com.yonagi.ocean.handler.RequestHandler;
import com.yonagi.ocean.framework.utils.FormBinder;
import com.yonagi.ocean.framework.utils.JsonDeserializer;
import com.yonagi.ocean.framework.utils.JsonSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.net.URLDecoder;
import java.util.HashMap;
import java.util.Map;

/**
 * @author Yonagi
 * @version 1.0
 * @program Ocean
 * @description
 * @date 2025/10/14 16:10
 */
public class MethodInvokingHandler implements RequestHandler {

    @FunctionalInterface
    private interface ContentProcessor {
        Object process(HttpRequest request, String contentType, String charSet, Class<?> paramType) throws JsonProcessingException, BadRequestException, UnsupportedEncodingException;
    }

    private final Map<String, ContentProcessor> processors = initProcessors();

    private Map<String, ContentProcessor> initProcessors() {
        Map<String, ContentProcessor> map = new HashMap<>();

        map.put("application/json", (request, contentType, charSet, paramType) -> {
            byte[] body = request.getBody();
            if (body == null || body.length == 0) {
                throw new BadRequestException("JSON body is missing for required @RequestBody parameter: " + paramType.getSimpleName());
            }
            return JsonDeserializer.deserialize(body, paramType);
        });

        map.put("application/x-www-form-urlencoded", (request, contentType, charSet, paramType) -> {
            Map<String, String> formParams = parseFormData(request.getBody(), charSet);
            return FormBinder.bind(formParams, paramType);
        });

        map.put("application/xml", (request, contentType, charSet, paramType) -> {
            byte[] body = request.getBody();
            String xmlContent = new String(body, charSet);
            if (xmlContent == null || xmlContent.isEmpty()) {
                throw new BadRequestException("XML body is missing for required @RequestBody parameter: " + paramType.getSimpleName());
            }
            return xmlMapper.readValue(xmlContent, paramType);
        });

        return map;
    }

    private Map<String, String> parseFormData(byte[] body, String charset) {
        String formData = new String(body);
        Map<String, String> result = new HashMap<>();
        if (formData == null || formData.isEmpty()) {
            return result;
        }
        String[] pairs = formData.split("&");
        for (String pair : pairs) {
            try {
                String[] kv = pair.split("=", 2);
                String key = URLDecoder.decode(kv[0], charset);
                String value = kv.length > 1 ? URLDecoder.decode(kv[1], charset) : "";
                result.put(key, value);
            } catch (UnsupportedEncodingException e) {
                log.error("Error decoding form data: {}, client sent charset type: {}", e.getMessage(), charset, e);
            }
        }
        return result;
    }

    private static final Logger log = LoggerFactory.getLogger(MethodInvokingHandler.class);
    private static final XmlMapper xmlMapper = new XmlMapper();

    private final Object controllerInstance;
    private final Method handlerMethod;

    public MethodInvokingHandler(Object controllerInstance, Method handlerMethod) {
        this.controllerInstance = controllerInstance;
        this.handlerMethod = handlerMethod;
        this.handlerMethod.setAccessible(true);
    }

    @Override
    public void handle(HttpContext httpContext) throws IOException {
        try {
            Object[] args = resolveMethodArguments(httpContext);
            Object result = handlerMethod.invoke(controllerInstance, args);
            handleReturnValue(httpContext, result);
        } catch (MissingRequiredParameterException e) {
            log.warn("[{}] Missing required parameter for method {}: {}", httpContext.getTraceId(), handlerMethod.getName(), e.getMessage());
            HttpResponse response = httpContext.getResponse().toBuilder()
                    .httpVersion(httpContext.getRequest().getHttpVersion())
                    .httpStatus(HttpStatus.BAD_REQUEST)
                    .contentType(ContentType.TEXT_PLAIN)
                    .body(("Missing required parameter: " + e.getMessage()).getBytes())
                    .build();
            httpContext.setResponse(response);
        } catch (JsonProcessingException e) {
            log.warn("[{}] Json processing error: {}", httpContext.getTraceId(), e.getMessage(), e);
            HttpResponse response = httpContext.getResponse().toBuilder()
                    .httpVersion(httpContext.getRequest().getHttpVersion())
                    .httpStatus(HttpStatus.BAD_REQUEST)
                    .contentType(ContentType.TEXT_PLAIN)
                    .body(("Fail to deserialize body").getBytes())
                    .build();
            httpContext.setResponse(response);
        } catch (DeserializationException e) {
            log.warn("[{}] Deserialization error: {}", httpContext.getTraceId(), e.getMessage(), e);
            HttpResponse response = httpContext.getResponse().toBuilder()
                    .httpVersion(httpContext.getRequest().getHttpVersion())
                    .httpStatus(HttpStatus.BAD_REQUEST)
                    .contentType(ContentType.TEXT_PLAIN)
                    .body(("Fail to deserialize body").getBytes())
                    .build();
            httpContext.setResponse(response);
        } catch (BadRequestException e) {
            log.warn("[{}] Bad Request (400) for {}: {}", httpContext.getTraceId(), httpContext.getRequest().getUri(), e.getMessage());
            HttpResponse response = httpContext.getResponse().toBuilder()
                    .httpVersion(httpContext.getRequest().getHttpVersion())
                    .httpStatus(HttpStatus.BAD_REQUEST)
                    .contentType(ContentType.TEXT_PLAIN)
                    .body(("Bad Request").getBytes())
                    .build();
            httpContext.setResponse(response);
        } catch (UnsupportedMediaTypeException e) {
            log.warn("[{}] Unsupported media type for {}: {}", httpContext.getTraceId(), httpContext.getRequest().getUri(), e.getMessage());
            HttpResponse response = httpContext.getResponse().toBuilder()
                    .httpVersion(httpContext.getRequest().getHttpVersion())
                    .httpStatus(HttpStatus.BAD_REQUEST)
                    .contentType(ContentType.TEXT_PLAIN)
                    .body(("Unsupport Media Type").getBytes())
                    .build();
            httpContext.setResponse(response);
        }catch (IllegalAccessException | IllegalArgumentException e) {
            log.error("[{}] Illegal Access or Illegal Argument on Controller method {}: {}", httpContext.getTraceId(), handlerMethod.getName(), e.getMessage(), e);
            HttpResponse response = httpContext.getResponse().toBuilder()
                    .httpVersion(httpContext.getRequest().getHttpVersion())
                    .httpStatus(HttpStatus.INTERNAL_SERVER_ERROR)
                    .contentType(ContentType.TEXT_PLAIN)
                    .body(("Internal Server Error: Illegal Access or Illegal Argument on Controller method").getBytes())
                    .build();
            httpContext.setResponse(response);
        } catch (InvocationTargetException e) {
            log.error("[{}] Controller method {}.{} threw an exception: {}",
                    httpContext.getTraceId(), controllerInstance.getClass().getSimpleName(), handlerMethod.getName(), e.getTargetException().getMessage(), e.getTargetException());
            HttpResponse response = httpContext.getResponse().toBuilder()
                    .httpVersion(httpContext.getRequest().getHttpVersion())
                    .httpStatus(HttpStatus.INTERNAL_SERVER_ERROR)
                    .contentType(ContentType.TEXT_PLAIN)
                    .body(("Internal Server Error").getBytes())
                    .build();
            httpContext.setResponse(response);
        }
    }

    private Object[] resolveMethodArguments(HttpContext httpContext) throws MissingRequiredParameterException, JsonProcessingException, BadRequestException, UnsupportedEncodingException {
        HttpRequest request = httpContext.getRequest();
        Parameter[] parameters = handlerMethod.getParameters();
        Object[] args = new Object[parameters.length];

        @SuppressWarnings("unchecked")
        Map<String, String> pathVariables = (Map<String, String>) request.getAttribute("PathVariableAttributes");
        Map<String, String> queryParameters = request.getQueryParams();

        for (int i = 0; i < parameters.length; i++) {
            Parameter parameter = parameters[i];
            Class<?> paramType = parameter.getType();

            if (paramType.isAssignableFrom(HttpRequest.class)) {
                args[i] = request;
                continue;
            }
            if (parameter.isAnnotationPresent(RequestBody.class)) {
                args[i] = resolveRequestBody(request, paramType);
                continue;
            }
            if (parameter.isAnnotationPresent(PathVariable.class)) {
                args[i] = resolvePathVariable(parameter, paramType, pathVariables);
                continue;
            }
            if (parameter.isAnnotationPresent(RequestParam.class)) {
                args[i] = resolveRequestParam(parameter, paramType, queryParameters);
                continue;
            }

            // 默认回退：尝试从路径变量中通过参数名匹配 (仅当 -parameters 编译时有效)
            String paramName = parameter.getName();
            if (pathVariables != null && pathVariables.containsKey(paramName)) {
                String value = pathVariables.get(paramName);
                args[i] = convertValue(value, paramType);
                continue;
            }

            log.warn("[{}] Unresolved parameter: {} {} for method {}. Using default value.",
                    httpContext.getTraceId(), paramType.getSimpleName(), paramName, handlerMethod.getName());
            args[i] = getDefaultValueForType(paramType);
        }
        return args;
    }

    private Object resolveRequestBody(HttpRequest request, Class<?> paramType) throws DeserializationException, BadRequestException, JsonProcessingException, UnsupportedEncodingException {
        String contentType = request.getHeaders().getOrDefault("content-type", "");
        String mimeType = contentType.split(";")[0].trim().toLowerCase();
        String charset = "UTF-8";
        if (contentType.contains("chatset=")) {
            charset = contentType.split("charset=")[1].trim();
        }
        ContentProcessor processor = processors.get(mimeType);
        if (processor == null) {
            throw new UnsupportedMediaTypeException("Media type '" + mimeType + "' is not supported for @RequestBody.");
        }
        return processor.process(request, contentType, charset, paramType);
    }

    private Object resolvePathVariable(Parameter parameter, Class<?> paramType, Map<String, String> pathVariables) throws BadRequestException {
        PathVariable annotation = parameter.getAnnotation(PathVariable.class);
        String name = annotation.value().isEmpty() ? parameter.getName() : annotation.value();

        if (pathVariables == null || !pathVariables.containsKey(name)) {
            throw new BadRequestException("Missing required PathVariable: {" + name + "}");
        }

        String value = pathVariables.get(name);
        return convertValue(value, paramType);
    }

    private Object resolveRequestParam(Parameter parameter, Class<?> paramType, Map<String, String> queryParameters) throws BadRequestException {
        RequestParam annotation = parameter.getAnnotation(RequestParam.class);
        String name = annotation.value() == null || annotation.value().isEmpty() ? parameter.getName() : annotation.value();
        String defaultValue = annotation.defaultValue().equals(RequestParam.NO_DEFAULT_VALUE) ? null : annotation.defaultValue();
        boolean required = annotation.required();

        String value = queryParameters.get(name);

        if (value == null) {
            if (defaultValue != null) {
                value = defaultValue;
            } else if (required) {
                throw new BadRequestException("Missing required Query Parameter: " + name);
            } else {
                return getDefaultValueForType(paramType);
            }
        }
        if (value.isEmpty() && paramType != String.class && defaultValue == null && required) {
            throw new BadRequestException("Required Query Parameter '" + name + "' cannot be empty.");
        }

        return convertValue(value, paramType);
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

    private void handleReturnValue(HttpContext httpContext,Object returnValue) throws IOException {
        if (handlerMethod.getReturnType() == void.class || returnValue == null) {
            log.debug("Controller method returned void or null, assuming response was handled manually.");
        } else {
            String jsonResponse = JsonSerializer.serialize(returnValue);

            HttpResponse response = httpContext.getResponse().toBuilder()
                    .httpVersion(HttpVersion.HTTP_1_1)
                    .httpStatus(HttpStatus.OK)
                    .contentType(ContentType.APPLICATION_JSON)
                    .body(jsonResponse.getBytes())
                    .build();
            httpContext.setResponse(response);
            log.debug("Successfully wrote JSON response for return value: {}", handlerMethod.getReturnType().getSimpleName());
        }
    }
}
