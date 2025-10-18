package com.yonagi.ocean.middleware;

import com.yonagi.ocean.core.context.HttpContext;
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

    private final List<Middleware> middlewares;

    public MiddlewareChain(List<Middleware> globalMiddlewares) {
        this.middlewares = List.copyOf(globalMiddlewares);
    }

    public List<Middleware> getMiddlewares() {
        return middlewares;
    }

    public ChainExecutor newExecutor(Runnable finalHandler) {
        return new ChainExecutor(this.middlewares, finalHandler);
    }
}
