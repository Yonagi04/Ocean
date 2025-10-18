package com.yonagi.ocean.core.context;

import com.yonagi.ocean.core.ConnectionManager;
import com.yonagi.ocean.core.protocol.DefaultProtocolHandlerFactory;
import com.yonagi.ocean.core.protocol.handler.HttpProtocolHandler;
import com.yonagi.ocean.core.ratelimiter.RateLimiterChecker;
import com.yonagi.ocean.core.router.Router;
import com.yonagi.ocean.middleware.Middleware;
import com.yonagi.ocean.middleware.MiddlewareChain;
import com.yonagi.ocean.middleware.MiddlewareLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * @author Yonagi
 * @version 1.0
 * @program Ocean
 * @description
 * @date 2025/10/17 15:03
 */
public class ServerContext {

    private static final Logger log = LoggerFactory.getLogger(ServerContext.class);

    private final ConnectionManager connectionManager;
    private final Router router;
    private final RateLimiterChecker rateLimiterChecker;
    private final MiddlewareChain middlewareChain;

    public ServerContext(MiddlewareChain middlewareChain, RateLimiterChecker rateLimiterChecker, Router router, ConnectionManager connectionManager) {
        this.middlewareChain = middlewareChain;
        this.rateLimiterChecker = rateLimiterChecker;
        this.router = router;
        this.connectionManager = connectionManager;
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
}
