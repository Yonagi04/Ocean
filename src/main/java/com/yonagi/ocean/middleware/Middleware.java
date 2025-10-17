package com.yonagi.ocean.middleware;

import com.yonagi.ocean.core.protocol.HttpRequest;

/**
 * @author Yonagi
 * @version 1.0
 * @program Ocean
 * @description
 * @date 2025/10/16 12:58
 */
public interface Middleware {

    boolean handle(HttpRequest request, MiddlewareChain chain) throws Exception;
}
