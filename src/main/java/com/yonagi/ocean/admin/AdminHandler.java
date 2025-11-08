package com.yonagi.ocean.admin;

import com.yonagi.ocean.admin.metrics.MetricsRegistry;
import com.yonagi.ocean.core.context.HttpContext;
import com.yonagi.ocean.core.protocol.HttpResponse;
import com.yonagi.ocean.core.protocol.enums.ContentType;
import com.yonagi.ocean.core.protocol.enums.HttpStatus;
import com.yonagi.ocean.handler.RequestHandler;
import com.yonagi.ocean.utils.TemplateRenderer;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * @author Yonagi
 * @version 1.0
 * @program Ocean
 * @description
 * @date 2025/10/19 19:15
 */
public class AdminHandler implements RequestHandler {

    private final MetricsRegistry metricsRegistry;

    public AdminHandler(MetricsRegistry metricsRegistry) {
        this.metricsRegistry = metricsRegistry;
    }

    @Override
    public void handle(HttpContext httpContext) throws IOException {
        try {
            double activeThreadsCount = metricsRegistry.getGaugeValue("app.threadpool.active");
            double queueSize = metricsRegistry.getGaugeValue("app.threadpool.queue.size");
            double maxSize = metricsRegistry.getGaugeValue("app.threadpool.max.size");

            double virtualThreadActiveCount = metricsRegistry.getGaugeValue("app.threadpool.virtual.threads.active");
            double platformTotalThreads = metricsRegistry.getGaugeValue("app.threadpool.platform.threads.total");

            double cacheHitCount = metricsRegistry.getCacheHitCounter().count();
            double httpCacheHitCount = metricsRegistry.getHttpCacheHitCounter().count();
            double routeFallbackCount = metricsRegistry.getRouteFallbackCounter().count();

            double notFoundCount = metricsRegistry.getNotFoundCounter().count();
            double internalErrorCount = metricsRegistry.getInternalServerErrorCounter().count();
            double ratelimitCount = metricsRegistry.getRateLimitRejectedCounter().count();

            long requestCount = metricsRegistry.getTotalRequestCount();
            double avgLatency = metricsRegistry.getAverageLatency();
            double maxLatency = metricsRegistry.getMaxLatency();

            String appVersion = httpContext.getConnectionContext().getServerContext().getEnvironmentInfo().getAppVersion();
            String osName = httpContext.getConnectionContext().getServerContext().getEnvironmentInfo().getOsName();
            String javaVersion = httpContext.getConnectionContext().getServerContext().getEnvironmentInfo().getJavaVersion();
            double jvmMemoryUsed = metricsRegistry.getJvmMemoryUsedMB();
            double processCpuUsage = metricsRegistry.getProcessCpuUsagePercentage();
            String startTime = httpContext.getConnectionContext().getServerContext().getEnvironmentInfo().getUptime();

            Map<String, Object> model = new HashMap<>();
            model.put("activeThreads", activeThreadsCount);
            model.put("queueSize", queueSize);
            model.put("maxSize", maxSize);

            model.put("virtualThreadActiveCount", virtualThreadActiveCount);
            model.put("platformTotalThreads", platformTotalThreads);

            model.put("cacheHit", cacheHitCount);
            model.put("httpCacheHit", httpCacheHitCount);
            model.put("routeFallback", routeFallbackCount);

            model.put("notFound", notFoundCount);
            model.put("ratelimit", ratelimitCount);
            model.put("internalError", internalErrorCount);

            model.put("reqTotal", requestCount);
            model.put("avgLatency", avgLatency);
            model.put("maxLatency", maxLatency);

            model.put("appVersion", appVersion);
            model.put("osName", osName);
            model.put("javaVersion", javaVersion);
            model.put("jvmMemoryUsed", jvmMemoryUsed);
            model.put("processCpuUsage", processCpuUsage);
            model.put("uptime", startTime);

            String html = TemplateRenderer.render("admin", model);
            HttpResponse response = httpContext.getResponse().toBuilder()
                    .httpStatus(HttpStatus.OK)
                    .contentType(ContentType.TEXT_HTML)
                    .body(html.getBytes(StandardCharsets.UTF_8))
                    .build();
            httpContext.setResponse(response);
        } catch (Exception e) {
            throw new RuntimeException("Error rendering admin metrics page", e);
        }
    }
}
