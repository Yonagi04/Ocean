package com.yonagi.ocean.middleware.impl;

import com.yonagi.ocean.core.context.HttpContext;
import com.yonagi.ocean.core.protocol.HttpRequest;
import com.yonagi.ocean.core.protocol.HttpResponse;
import com.yonagi.ocean.core.protocol.enums.HttpMethod;
import com.yonagi.ocean.middleware.ChainExecutor;
import com.yonagi.ocean.middleware.Middleware;
import com.yonagi.ocean.middleware.annotation.MiddlewarePriority;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Yonagi
 * @version 1.0
 * @program Ocean
 * @description
 * @date 2025/10/18 10:04
 */
@MiddlewarePriority(value = 3)
public class LoggingMiddleware implements Middleware {

    private static final Logger log = LoggerFactory.getLogger(LoggingMiddleware.class);

    @Override
    public void handle(HttpContext httpContext, ChainExecutor executor) throws Exception {
        long startTimeMills = System.currentTimeMillis();

        executor.proceed(httpContext);

        long duration = System.currentTimeMillis() - startTimeMills;

        HttpRequest request = httpContext.getRequest();
        HttpResponse response = httpContext.getResponse();

        String traceId = request.getAttribute("traceId") == null ? "" : (String) request.getAttribute("traceId");
        HttpMethod method = request.getMethod();
        String uri = request.getUri();
        int statusCode = response.getHttpStatus().getCode();

        log.info("[traceId={} {} {} -> {} ({} ms)]", traceId, method, uri, statusCode, duration);
    }
}
