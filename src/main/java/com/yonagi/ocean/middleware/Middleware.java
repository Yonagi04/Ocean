package com.yonagi.ocean.middleware;

import com.yonagi.ocean.core.protocol.HttpRequest;
import com.yonagi.ocean.core.protocol.HttpResponse;

/**
 * @author Yonagi
 * @version 1.0
 * @program Ocean
 * @description
 * @date 2025/10/16 12:58
 */
public interface Middleware {

    void handle(HttpRequest request, HttpResponse response, MiddlewareChain chain) throws Exception;
}
