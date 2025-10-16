package com.yonagi.ocean.middleware;

import com.yonagi.ocean.core.protocol.HttpRequest;
import com.yonagi.ocean.core.protocol.HttpResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Yonagi
 * @version 1.0
 * @program Ocean
 * @description
 * @date 2025/10/16 13:10
 */
public class FilterChain {

    private static final Logger log = LoggerFactory.getLogger(FilterChain.class);

    private final List<Middleware> middlewares = new ArrayList<>();

    public void addMiddleWare(Middleware middleware) {
        this.middlewares.add(middleware);
    }

    public boolean process(HttpRequest request) {
        for (Middleware middleware : middlewares) {
            try {
                boolean shouldContinue = middleware.handle(request);
                if (!shouldContinue) {
                    log.info("Middleware {} short-circuited the request chain", middleware.getClass().getSimpleName());
                    return false;
                }
            } catch (Exception e) {
                log.error("Middleware {} threw an unhandled exception: {}", middleware.getClass().getSimpleName(), e.getMessage(), e);
                request.setAttribute("MiddlewareException", e);
                return false;
            }
        }
        return true;
    }
}
