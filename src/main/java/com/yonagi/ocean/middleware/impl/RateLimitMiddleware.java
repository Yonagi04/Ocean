package com.yonagi.ocean.middleware.impl;

import com.yonagi.ocean.core.ErrorPageRender;
import com.yonagi.ocean.core.context.ConnectionContext;
import com.yonagi.ocean.core.context.HttpContext;
import com.yonagi.ocean.core.protocol.HttpRequest;
import com.yonagi.ocean.core.protocol.HttpResponse;
import com.yonagi.ocean.core.protocol.enums.HttpStatus;
import com.yonagi.ocean.core.ratelimiter.RateLimiterChecker;
import com.yonagi.ocean.metrics.MetricsRegistry;
import com.yonagi.ocean.middleware.ChainExecutor;
import com.yonagi.ocean.middleware.Middleware;
import com.yonagi.ocean.middleware.annotation.MiddlewarePriority;

/**
 * @author Yonagi
 * @version 1.0
 * @program Ocean
 * @description
 * @date 2025/10/18 11:12
 */
@MiddlewarePriority(value = 5)
public class RateLimitMiddleware implements Middleware {

    @Override
    public void handle(HttpContext httpContext, ChainExecutor executor) throws Exception {
        ConnectionContext connectionContext = httpContext.getConnectionContext();
        RateLimiterChecker rateLimiterChecker = connectionContext.getServerContext().getRateLimiterChecker();
        HttpRequest request = httpContext.getRequest();

        if (!rateLimiterChecker.check(request)) {
            HttpResponse errorResponse = httpContext.getResponse().toBuilder()
                    .httpVersion(request.getHttpVersion())
                    .httpStatus(HttpStatus.TOO_MANY_REQUESTS)
                    .contentType("text/plain; charset=utf-8")
                    .body("Rate limit exceeded. Try again later.".getBytes())
                    .build();
            httpContext.setResponse(errorResponse);
            ErrorPageRender.render(httpContext);

            MetricsRegistry metricsRegistry = httpContext.getConnectionContext().getServerContext().getMetricsRegistry();
            metricsRegistry.getRateLimitRejectedCounter().increment();

            return;
        }

        executor.proceed(httpContext);
    }
}
