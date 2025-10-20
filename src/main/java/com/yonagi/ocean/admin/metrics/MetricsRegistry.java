package com.yonagi.ocean.admin.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import io.micrometer.prometheusmetrics.PrometheusConfig;
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;

import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * @author Yonagi
 * @version 1.0
 * @program Ocean
 * @description
 * @date 2025/10/19 14:34
 */
public class MetricsRegistry {

    private final PrometheusMeterRegistry registry;
    private static final String REQUEST_TIMER_NAME = "http.server.requests";

    private final Counter notFoundCounter;
    private final Counter routeFallbackCounter;
    private final Counter rateLimitRejectedCounter;
    private final Counter cacheHitCounter;
    private final Counter httpCacheHitCounter;
    private final Counter internalServerErrorCounter;

    public MetricsRegistry(ThreadPoolExecutor threadPoolExecutor) {
        this.registry = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);

        this.notFoundCounter = Counter.builder("not.found.total")
                .description("Counts requests not found (404s)")
                .register(registry);
        this.routeFallbackCounter = Counter.builder("route.fallback.total")
                .description("Counts requests that used route fallback")
                .register(registry);
        this.rateLimitRejectedCounter = Counter.builder("ratelimit.rejected.total")
                .description("Counts requests rejected by rate limiter (429s)")
                .register(registry);
        this.cacheHitCounter = Counter.builder("cache.hit.total")
                .description("Counts cache hits")
                .register(registry);
        this.httpCacheHitCounter = Counter.builder("http.cache.hit.total")
                .description("Counts HTTP cache hits")
                .register(registry);
        this.internalServerErrorCounter = Counter.builder("internal.server.error.total")
                .description("Counts internal server errors (500s)")
                .register(registry);

        gauge("app.threadpool.active",
                threadPoolExecutor,
                ThreadPoolExecutor::getActiveCount);
        gauge("app.threadpool.queue.size",
                threadPoolExecutor,
                executor -> executor.getQueue().size());
    }

    public String getPrometheusFormattedData() {
        return this.registry.scrape();
    }

    public Timer getRequestTimer(String uri, String status) {
        return Timer.builder("http.server.requests")
                .description("Records incoming request latency")
                .tags(Tags.of("uri", uri, "status", status))
                .register(registry);
    }

    public Counter getNotFoundCounter() {
        return notFoundCounter;
    }

    public Counter getRateLimitRejectedCounter() {
        return rateLimitRejectedCounter;
    }

    public Counter getRouteFallbackCounter() {
        return routeFallbackCounter;
    }

    public Counter getCacheHitCounter() {
        return cacheHitCounter;
    }

    public Counter getHttpCacheHitCounter() {
        return httpCacheHitCounter;
    }

    public Counter getInternalServerErrorCounter() {
        return internalServerErrorCounter;
    }

    public <T> void gauge(String name, T obj, java.util.function.ToDoubleFunction<T> f) {
        registry.gauge(name, obj, f);
    }

    public Double getGaugeValue(String name) {
        Meter meter = registry.find(name).gauge();
        if (meter instanceof io.micrometer.core.instrument.Gauge) {
            return ((io.micrometer.core.instrument.Gauge) meter).value();
        }
        return 0.0;
    }

    public double getAverageLatency() {
        long totalCount = 0;
        double totalTime = 0.0;

        for (Meter meter : registry.find(REQUEST_TIMER_NAME).meters()) {
            if (meter instanceof Timer) {
                Timer timer = (Timer) meter;
                totalCount += timer.count();
                totalTime += timer.totalTime(TimeUnit.MILLISECONDS);
            }
        }
        if (totalCount == 0) {
            return 0.0;
        }
        return totalTime / totalCount;
    }

    public double getMaxLatency() {
        double maxLatency = 0.0;

        for (Meter meter : registry.find(REQUEST_TIMER_NAME).meters()) {
            if (meter instanceof Timer) {
                Timer timer = (Timer) meter;
                maxLatency = Math.max(maxLatency, timer.max(TimeUnit.MILLISECONDS));
            }
        }
        return maxLatency;
    }

    public long getTotalRequestCount() {
        long totalCount = 0;
        for (Meter meter : registry.find(REQUEST_TIMER_NAME).meters()) {
            if (meter instanceof Timer) {
                totalCount += ((Timer) meter).count();
            }
        }
        return totalCount;
    }
}
