package com.yonagi.ocean.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import io.micrometer.prometheusmetrics.PrometheusConfig;
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;
import io.prometheus.client.CollectorRegistry;

import java.util.concurrent.ThreadPoolExecutor;

/**
 * @author Yonagi
 * @version 1.0
 * @program Ocean
 * @description
 * @date 2025/10/19 14:34
 */
public class MetricsRegistry {

    private final PrometheusMeterRegistry registry;

    private final Counter notFoundCounter;
    private final Counter routeFallbackCounter;
    private final Counter rateLimitRejectedCounter;
    private final Counter cacheHitCounter;

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

    public <T> void gauge(String name, T obj, java.util.function.ToDoubleFunction<T> f) {
        registry.gauge(name, obj, f);
    }
}
