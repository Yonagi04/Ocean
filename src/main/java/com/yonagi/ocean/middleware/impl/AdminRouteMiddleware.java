package com.yonagi.ocean.middleware.impl;

import com.yonagi.ocean.admin.health.HealthCheckHandler;
import com.yonagi.ocean.core.ErrorPageRender;
import com.yonagi.ocean.core.context.HttpContext;
import com.yonagi.ocean.core.protocol.HttpRequest;
import com.yonagi.ocean.core.protocol.HttpResponse;
import com.yonagi.ocean.core.protocol.enums.HttpStatus;
import com.yonagi.ocean.admin.metrics.MetricsHandler;
import com.yonagi.ocean.admin.utils.AdminUtil;
import com.yonagi.ocean.middleware.ChainExecutor;
import com.yonagi.ocean.middleware.Middleware;
import com.yonagi.ocean.middleware.annotation.MiddlewarePriority;

import java.util.Set;

/**
 * @author Yonagi
 * @version 1.0
 * @program Ocean
 * @description
 * @date 2025/10/19 16:05
 */
@MiddlewarePriority(value = 4)
public class AdminRouteMiddleware implements Middleware {

    @Override
    public void handle(HttpContext httpContext, ChainExecutor executor) throws Exception {
        MetricsHandler metricsHandler = httpContext.getConnectionContext().getServerContext().getMetricsHandler();
        HealthCheckHandler healthCheckHandler = httpContext.getConnectionContext().getServerContext().getHealthCheckHandler();
        Set<String> allowIps = AdminUtil.getWhiteList();
        String metricsUri = AdminUtil.getMetricUri();
        String healthUri = AdminUtil.getHealthUri();

        HttpRequest request = httpContext.getRequest();
        if (metricsUri.equalsIgnoreCase(request.getUri()) || healthUri.equalsIgnoreCase(request.getUri())) {
            String clientIp = (String) request.getAttribute("clientIp");
            if (!allowIps.contains(clientIp)) {
                HttpResponse errorResponse = httpContext.getResponse().toBuilder()
                        .httpVersion(request.getHttpVersion())
                        .httpStatus(HttpStatus.FORBIDDEN)
                        .contentType("text/plain; charset=utf-8")
                        .body("Access denied.".getBytes())
                        .build();
                httpContext.setResponse(errorResponse);
                ErrorPageRender.render(httpContext);
                return;
            }
            if (metricsUri.equalsIgnoreCase(request.getUri())) {
                metricsHandler.handle(httpContext);
            } else if (healthUri.equalsIgnoreCase(request.getUri())) {
                healthCheckHandler.handle(httpContext);
            }
            return;
        }
        executor.proceed(httpContext);
    }
}
