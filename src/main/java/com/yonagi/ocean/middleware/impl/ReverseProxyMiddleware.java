package com.yonagi.ocean.middleware.impl;

import com.yonagi.ocean.core.reverseproxy.config.ReverseProxyConfig;
import com.yonagi.ocean.core.reverseproxy.ReverseProxyChecker;
import com.yonagi.ocean.core.context.HttpContext;
import com.yonagi.ocean.core.reverseproxy.ReverseProxyHandler;
import com.yonagi.ocean.middleware.ChainExecutor;
import com.yonagi.ocean.middleware.Middleware;
import com.yonagi.ocean.middleware.annotation.MiddlewarePriority;

/**
 * @author Yonagi
 * @version 1.0
 * @program Ocean
 * @description
 * @date 2025/11/08 18:27
 */
@MiddlewarePriority(value = 6)
public class ReverseProxyMiddleware implements Middleware {

    @Override
    public void handle(HttpContext httpContext, ChainExecutor executor) throws Exception {
        ReverseProxyChecker reverseProxyChecker = httpContext.getConnectionContext().getServerContext().getReverseProxyChecker();
        ReverseProxyConfig proxyConfig = reverseProxyChecker.check(httpContext.getRequest());
        if (proxyConfig != null) {
            new ReverseProxyHandler(proxyConfig).handle(httpContext);
            return;
        }
        executor.proceed(httpContext);
    }
}
