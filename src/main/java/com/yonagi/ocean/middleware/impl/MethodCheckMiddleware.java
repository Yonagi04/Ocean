package com.yonagi.ocean.middleware.impl;

import com.alibaba.nacos.api.naming.pojo.healthcheck.impl.Http;
import com.yonagi.ocean.core.ErrorPageRender;
import com.yonagi.ocean.core.context.HttpContext;
import com.yonagi.ocean.core.protocol.HttpRequest;
import com.yonagi.ocean.core.protocol.HttpResponse;
import com.yonagi.ocean.core.protocol.enums.HttpMethod;
import com.yonagi.ocean.core.protocol.enums.HttpStatus;
import com.yonagi.ocean.middleware.ChainExecutor;
import com.yonagi.ocean.middleware.Middleware;
import com.yonagi.ocean.middleware.MiddlewareChain;
import com.yonagi.ocean.middleware.annotation.MiddlewarePriority;

/**
 * @author Yonagi
 * @version 1.0
 * @program Ocean
 * @description
 * @date 2025/10/18 10:04
 */
@MiddlewarePriority(value = 4)
public class MethodCheckMiddleware implements Middleware {

    @Override
    public void handle(HttpContext httpContext, ChainExecutor executor) throws Exception {
        HttpRequest request = httpContext.getRequest();
        HttpMethod method = request.getMethod();

        if (method == null) {
            HttpResponse errorResponse = httpContext.getResponse().toBuilder()
                    .httpVersion(request.getHttpVersion())
                    .httpStatus(HttpStatus.METHOD_NOT_ALLOWED)
                    .contentType("text/plain; charset=utf-8")
                    .body("HTTP Method not specified or supported.".getBytes())
                    .build();
            httpContext.setResponse(errorResponse);
            ErrorPageRender.render(httpContext);
            return;
        }

        executor.proceed(httpContext);
    }
}
