package com.yonagi.ocean.middleware.impl;

import com.yonagi.ocean.core.ErrorPageRender;
import com.yonagi.ocean.core.context.HttpContext;
import com.yonagi.ocean.core.protocol.HttpRequest;
import com.yonagi.ocean.core.protocol.HttpResponse;
import com.yonagi.ocean.core.protocol.enums.HttpStatus;
import com.yonagi.ocean.metrics.MetricsHandler;
import com.yonagi.ocean.metrics.utils.MetricsUtil;
import com.yonagi.ocean.middleware.ChainExecutor;
import com.yonagi.ocean.middleware.Middleware;
import com.yonagi.ocean.middleware.annotation.MiddlewarePriority;
import com.yonagi.ocean.utils.LocalConfigLoader;

import java.util.Arrays;
import java.util.Collections;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * @author Yonagi
 * @version 1.0
 * @program Ocean
 * @description
 * @date 2025/10/19 16:05
 */
@MiddlewarePriority(value = 4)
public class MetricsRouteMiddleware implements Middleware {

    @Override
    public void handle(HttpContext httpContext, ChainExecutor executor) throws Exception {
        MetricsHandler metricsHandler = httpContext.getConnectionContext().getServerContext().getMetricsHandler();
        Set<String> allowIps = MetricsUtil.getWhiteList();
        String metricsUri = MetricsUtil.getMetricUri();

        HttpRequest request = httpContext.getRequest();
        if (metricsUri.equalsIgnoreCase(request.getUri())) {
            String clientIp = (String) request.getAttribute("clientIp");
            if (!allowIps.isEmpty() && !allowIps.contains(clientIp)) {
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

            metricsHandler.handle(httpContext);
            return;
        }
        executor.proceed(httpContext);
    }
}
