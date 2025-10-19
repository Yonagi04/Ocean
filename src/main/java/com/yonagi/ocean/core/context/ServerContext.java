package com.yonagi.ocean.core.context;

import com.yonagi.ocean.admin.health.HealthCheckHandler;
import com.yonagi.ocean.admin.health.HealthCheckService;
import com.yonagi.ocean.core.ConnectionManager;
import com.yonagi.ocean.core.ratelimiter.RateLimiterChecker;
import com.yonagi.ocean.core.router.Router;
import com.yonagi.ocean.admin.metrics.MetricsHandler;
import com.yonagi.ocean.admin.metrics.MetricsRegistry;
import com.yonagi.ocean.middleware.MiddlewareChain;

/**
 * @author Yonagi
 * @version 1.0
 * @program Ocean
 * @description
 * @date 2025/10/17 15:03
 */
public class ServerContext {

    private final ConnectionManager connectionManager;
    private final Router router;
    private final RateLimiterChecker rateLimiterChecker;
    private final MiddlewareChain middlewareChain;
    private final MetricsRegistry metricsRegistry;
    private final MetricsHandler metricsHandler;
    private final HealthCheckService healthCheckService;
    private final HealthCheckHandler healthCheckHandler;

    public ServerContext(MiddlewareChain middlewareChain, RateLimiterChecker rateLimiterChecker, Router router,
                         ConnectionManager connectionManager, MetricsRegistry metricsRegistry, HealthCheckService healthCheckService) {
        this.middlewareChain = middlewareChain;
        this.rateLimiterChecker = rateLimiterChecker;
        this.router = router;
        this.connectionManager = connectionManager;
        this.metricsRegistry = metricsRegistry;
        this.metricsHandler = new MetricsHandler(metricsRegistry);
        this.healthCheckService = healthCheckService;
        this.healthCheckHandler = new HealthCheckHandler(healthCheckService);
    }

    public ConnectionManager getConnectionManager() {
        return connectionManager;
    }

    public Router getRouter() {
        return router;
    }

    public RateLimiterChecker getRateLimiterChecker() {
        return rateLimiterChecker;
    }

    public MiddlewareChain getMiddlewareChain() {
        return middlewareChain;
    }

    public MetricsRegistry getMetricsRegistry() {
        return metricsRegistry;
    }

    public MetricsHandler getMetricsHandler() {
        return metricsHandler;
    }

    public HealthCheckHandler getHealthCheckHandler() {
        return healthCheckHandler;
    }

    public HealthCheckService getHealthCheckService() {
        return healthCheckService;
    }
}
