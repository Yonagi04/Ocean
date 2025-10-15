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
import java.util.ArrayList;
import java.util.List;

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
            Object[] args = resolveMethodArguments(request, output, keepAlive);

            Object result = handlerMethod.invoke(controllerInstance, args);

            if (result instanceof String) {
                HttpResponse response = new HttpResponse.Builder()
                        .httpVersion(request.getHttpVersion())
                        .httpStatus(HttpStatus.OK)
                        .contentType("text/html; charset=UTF-8")
                        .body(((String) result).getBytes())
                        .build();
                response.write(request, output, keepAlive);
            }
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

    private Object[] resolveMethodArguments(HttpRequest request, OutputStream output, boolean keepAlive) {
        Class<?>[] parameterTypes = handlerMethod.getParameterTypes();
        List<Object> args = new ArrayList<>(parameterTypes.length);

        for (Class<?> paramType : parameterTypes) {
            if (paramType.isAssignableFrom(HttpRequest.class)) {
                args.add(request);
            } else if (paramType.isAssignableFrom(OutputStream.class)) {
                args.add(output);
            } else if (paramType.isAssignableFrom(Boolean.class) || paramType.isAssignableFrom(boolean.class)) {
                // TODO 会使用 @RequestParam 等注解来区分 boolean 值的来源
                args.add(keepAlive);
            }
            // 如果 Controller 需要其他类型的参数（例如自定义实体、PathVariable等），
            // TODO 这里需要扩展解析逻辑。
            else {
                log.warn("Controller method {}.{} requires unsupported argument type: {}. Using null.",
                        controllerInstance.getClass().getSimpleName(), handlerMethod.getName(), paramType.getName());
                args.add(null);
            }
        }
        return args.toArray();
    }
}
