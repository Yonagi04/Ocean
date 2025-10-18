package com.yonagi.ocean.middleware;

import com.yonagi.ocean.core.context.HttpContext;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author Yonagi
 * @version 1.0
 * @program Ocean
 * @description
 * @date 2025/10/18 10:26
 */
public class ChainExecutor {

    private final List<Middleware> middlewares;
    private final Runnable finalHandler;
    private final AtomicInteger currentIndex = new AtomicInteger(0);

    public ChainExecutor(List<Middleware> middlewares, Runnable finalHandler) {
        this.middlewares = middlewares;
        this.finalHandler = finalHandler;
    }

    public void execute(HttpContext httpContext) throws Exception {
        this.proceed(httpContext);
    }

    public void proceed(HttpContext httpContext) throws Exception {
        int index = currentIndex.getAndIncrement();

        if (index < middlewares.size()) {
            Middleware current = middlewares.get(index);

            current.handle(httpContext, this);
        } else {
            finalHandler.run();
        }
    }
}
