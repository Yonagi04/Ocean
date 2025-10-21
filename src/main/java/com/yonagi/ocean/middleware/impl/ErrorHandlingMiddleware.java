package com.yonagi.ocean.middleware.impl;

import com.yonagi.ocean.core.ErrorPageRender;
import com.yonagi.ocean.core.context.HttpContext;
import com.yonagi.ocean.core.protocol.HttpResponse;
import com.yonagi.ocean.core.protocol.enums.ContentType;
import com.yonagi.ocean.core.protocol.enums.HttpStatus;
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
 * @date 2025/10/18 10:59
 */
@MiddlewarePriority(value = 3)
public class ErrorHandlingMiddleware implements Middleware {

    private static final Logger log = LoggerFactory.getLogger(ErrorHandlingMiddleware.class);

    @Override
    public void handle(HttpContext httpContext, ChainExecutor executor) throws Exception {
        try {
            executor.proceed(httpContext);
        } catch (Exception e) {
            log.error("[{}] {}", httpContext.getTraceId(), e.getMessage(), e);
            httpContext.getConnectionContext().getServerContext().getMetricsRegistry().getInternalServerErrorCounter().increment();
            HttpResponse httpResponse = httpContext.getResponse().toBuilder()
                    .httpVersion(httpContext.getRequest().getHttpVersion())
                    .httpStatus(HttpStatus.INTERNAL_SERVER_ERROR)
                    .contentType(ContentType.TEXT_PLAIN)
                    .body("Internal server error".getBytes())
                    .build();
            httpContext.setResponse(httpResponse);
            ErrorPageRender.render(httpContext);
        }
    }
}
