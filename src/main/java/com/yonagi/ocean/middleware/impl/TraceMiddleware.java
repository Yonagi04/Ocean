package com.yonagi.ocean.middleware.impl;

import com.yonagi.ocean.core.context.HttpContext;
import com.yonagi.ocean.core.protocol.HttpRequest;
import com.yonagi.ocean.middleware.ChainExecutor;
import com.yonagi.ocean.middleware.Middleware;
import com.yonagi.ocean.middleware.annotation.MiddlewarePriority;
import com.yonagi.ocean.utils.UUIDUtil;

/**
 * @author Yonagi
 * @version 1.0
 * @program Ocean
 * @description
 * @date 2025/10/18 10:37
 */
@MiddlewarePriority(value = 0)
public class TraceMiddleware implements Middleware {

    @Override
    public void handle(HttpContext httpContext, ChainExecutor executor) throws Exception {
        String traceId = UUIDUtil.getUUID();
        HttpRequest request = httpContext.getRequest();
        if (request.getAttribute("traceId") == null || ((String) request.getAttribute("traceId")).isEmpty()) {
            request.setAttribute("traceId", traceId);
        }
        httpContext.setRequest(request);

        executor.proceed(httpContext);
    }
}
