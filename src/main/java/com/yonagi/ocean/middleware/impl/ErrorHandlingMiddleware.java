package com.yonagi.ocean.middleware.impl;

import com.yonagi.ocean.core.context.HttpContext;
import com.yonagi.ocean.core.protocol.HttpResponse;
import com.yonagi.ocean.core.protocol.enums.HttpStatus;
import com.yonagi.ocean.middleware.ChainExecutor;
import com.yonagi.ocean.middleware.Middleware;
import com.yonagi.ocean.middleware.annotation.MiddlewarePriority;

/**
 * @author Yonagi
 * @version 1.0
 * @program Ocean
 * @description
 * @date 2025/10/18 10:59
 */
@MiddlewarePriority(value = 2)
public class ErrorHandlingMiddleware implements Middleware {

    @Override
    public void handle(HttpContext httpContext, ChainExecutor executor) throws Exception {
        try {
            executor.proceed(httpContext);
        } catch (Exception e) {
            // todo: 错误信息body返回traceId, ua, 路径, method等信息
            HttpResponse httpResponse = httpContext.getResponse().toBuilder()
                    .httpVersion(httpContext.getRequest().getHttpVersion())
                    .httpStatus(HttpStatus.INTERNAL_SERVER_ERROR)
                    .contentType("text/plain; charset=utf-8")
                    .body("Internal server error".getBytes())
                    .build();
            httpContext.setResponse(httpResponse);
        }
    }
}
