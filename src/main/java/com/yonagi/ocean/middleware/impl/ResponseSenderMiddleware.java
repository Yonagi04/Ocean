package com.yonagi.ocean.middleware.impl;

import com.yonagi.ocean.core.context.HttpContext;
import com.yonagi.ocean.core.protocol.HttpResponse;
import com.yonagi.ocean.middleware.ChainExecutor;
import com.yonagi.ocean.middleware.Middleware;
import com.yonagi.ocean.middleware.annotation.MiddlewarePriority;

/**
 * @author Yonagi
 * @version 1.0
 * @program Ocean
 * @description
 * @date 2025/10/18 10:12
 */
@MiddlewarePriority(value = 1)
public class ResponseSenderMiddleware implements Middleware {

    @Override
    public void handle(HttpContext httpContext, ChainExecutor executor) throws Exception {
        try {
            executor.proceed(httpContext);
        } finally {
            if (!httpContext.isCommited()) {
                HttpResponse finalResponse = httpContext.getResponse();
                finalResponse.write(httpContext.getRequest(), httpContext.getOutput(), httpContext.isKeepalive());
                httpContext.getOutput().flush();
            }
        }
    }
}
