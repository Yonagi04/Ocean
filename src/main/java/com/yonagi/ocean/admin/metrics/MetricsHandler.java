package com.yonagi.ocean.admin.metrics;

import com.yonagi.ocean.core.context.HttpContext;
import com.yonagi.ocean.core.protocol.enums.HttpStatus;
import com.yonagi.ocean.handler.RequestHandler;

/**
 * @author Yonagi
 * @version 1.0
 * @program Ocean
 * @description
 * @date 2025/10/19 16:00
 */
public class MetricsHandler implements RequestHandler {

    private final MetricsRegistry metricsRegistry;

    public MetricsHandler(MetricsRegistry metricsRegistry) {
        this.metricsRegistry = metricsRegistry;
    }

    @Override
    public void handle(HttpContext httpContext) {
        String metricsData = metricsRegistry.getPrometheusFormattedData();

        httpContext.setResponse(httpContext.getResponse().toBuilder()
                .httpVersion(httpContext.getRequest().getHttpVersion())
                .httpStatus(HttpStatus.OK)
                .contentType("text/plain; version=0.0.4; charset=utf-8")
                .body(metricsData.getBytes())
                .build());
    }
}
