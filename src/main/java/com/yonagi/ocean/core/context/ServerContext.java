package com.yonagi.ocean.core.context;

import com.yonagi.ocean.core.ConnectionManager;
import com.yonagi.ocean.core.ratelimiter.RateLimiterChecker;
import com.yonagi.ocean.core.router.Router;
import com.yonagi.ocean.metrics.MetricsHandler;
import com.yonagi.ocean.metrics.MetricsRegistry;
import com.yonagi.ocean.middleware.MiddlewareChain;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

    public ServerContext(MiddlewareChain middlewareChain, RateLimiterChecker rateLimiterChecker, Router router,
                         ConnectionManager connectionManager, MetricsRegistry metricsRegistry) {
        this.middlewareChain = middlewareChain;
        this.rateLimiterChecker = rateLimiterChecker;
        this.router = router;
        this.connectionManager = connectionManager;
        this.metricsRegistry = metricsRegistry;
        this.metricsHandler = new MetricsHandler(metricsRegistry);
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
}
