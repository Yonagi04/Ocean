package com.yonagi.ocean.middleware;

import com.yonagi.ocean.core.protocol.HttpRequest;
import com.yonagi.ocean.core.protocol.HttpResponse;
import com.yonagi.ocean.core.protocol.enums.HttpStatus;
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
public class MiddlewareChain {

    private static final Logger log = LoggerFactory.getLogger(MiddlewareChain.class);

    private final List<Middleware> middlewares = new ArrayList<>();
    private int currentIndex = 0;

    public void addMiddleWare(Middleware middleware) {
        this.middlewares.add(middleware);
    }

    public void execute(HttpRequest request, HttpResponse response, Runnable finalHandler) throws Exception {
        this.currentIndex = 0;
        invokeNext(request, response, finalHandler);
    }

    private void invokeNext(HttpRequest request, HttpResponse response, Runnable finalHandler) throws Exception {
        if (currentIndex < middlewares.size()) {
            Middleware current = middlewares.get(currentIndex++);
            current.handle(request, response, this);
        } else {
            finalHandler.run();
        }
    }

    public void next(HttpRequest request, HttpResponse response, Runnable finalHandler) throws Exception {
        invokeNext(request, response, finalHandler);
    }
}
