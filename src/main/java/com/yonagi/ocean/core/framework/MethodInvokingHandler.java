package com.yonagi.ocean.core.framework;

import com.yonagi.ocean.core.protocol.HttpRequest;
import com.yonagi.ocean.core.protocol.HttpResponse;
import com.yonagi.ocean.core.protocol.enums.HttpStatus;
import com.yonagi.ocean.handler.RequestHandler;
import com.yonagi.ocean.handler.impl.InternalErrorHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

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
            Object result = handlerMethod.invoke(controllerInstance, request, output, keepAlive);

            if (result instanceof String) {
                HttpResponse response = new HttpResponse.Builder()
                        .httpVersion(request.getHttpVersion())
                        .httpStatus(HttpStatus.OK)
                        .contentType("text/html; charset=UTF-8")
                        .body(((String) result).getBytes())
                        .build();
                response.write(request, output, keepAlive);
                output.flush();
            }
        } catch (IllegalAccessException | IllegalArgumentException e) {
            new InternalErrorHandler().handle(request, output, keepAlive);
        } catch (InvocationTargetException e) {
            log.error("Controller method {} threw an exception: {}", handlerMethod.getName(), e.getTargetException().getMessage(), e.getTargetException());
            new InternalErrorHandler().handle(request, output, keepAlive);
        }
    }
}
