package com.yonagi.ocean.middleware.impl;

import com.yonagi.ocean.core.context.HttpContext;
import com.yonagi.ocean.admin.metrics.MetricsRegistry;
import com.yonagi.ocean.middleware.ChainExecutor;
import com.yonagi.ocean.middleware.Middleware;
import com.yonagi.ocean.middleware.annotation.MiddlewarePriority;
import io.micrometer.core.instrument.Timer;

import java.util.concurrent.TimeUnit;

/**
 * @author Yonagi
 * @version 1.0
 * @program Ocean
 * @description
 * @date 2025/10/19 15:20
 */
@MiddlewarePriority(value = 2)
public class MetricsMiddleware implements Middleware {

    @Override
    public void handle(HttpContext httpContext, ChainExecutor executor) throws Exception {
        MetricsRegistry metricsRegistry = httpContext.getConnectionContext().getServerContext().getMetricsRegistry();

        long startTime = System.nanoTime();
        String statusCodeTag = "200";

        try {
            executor.proceed(httpContext);
            statusCodeTag = String.valueOf(httpContext.getResponse().getHttpStatus().getCode());
        } catch (Exception e) {
            if (httpContext.getResponse().getHttpStatus().getCode() != 200) {
                statusCodeTag = String.valueOf(httpContext.getResponse().getHttpStatus().getCode());
            }
            throw e;
        } finally {
            long durationNs = System.nanoTime() - startTime;
            String uri = httpContext.getRequest().getUri();
            Timer dynamicTimer = metricsRegistry.getRequestTimer(uri, statusCodeTag);

            dynamicTimer.record(durationNs, TimeUnit.NANOSECONDS);
        }
    }
}
