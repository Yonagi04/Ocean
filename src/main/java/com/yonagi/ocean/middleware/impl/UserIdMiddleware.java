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
 * @date 2025/11/12 18:40
 */
@MiddlewarePriority(value = 3)
public class UserIdMiddleware implements Middleware {

    private static final String COOKIE_HEADER = "cookie";
    private static final String COOKIE_NAME = "VISITOR_ID";

    @Override
    public void handle(HttpContext httpContext, ChainExecutor executor) throws Exception {
        HttpRequest request = httpContext.getRequest();
        String sessionId = request.getHeaders().get("X-Session-Id");
        boolean isSetCookie = false;

        if (sessionId == null || sessionId.isEmpty()) {
            sessionId = getVisitorIdFromCookie(request);
        }
        if (sessionId == null) {
            sessionId = UUIDUtil.getUUID();
            isSetCookie = true;
        }
        httpContext.setSessionId(sessionId);
        if (isSetCookie) {
            request.setAttribute("isSetCookie", true);
        }
        executor.proceed(httpContext);
    }

    private String getVisitorIdFromCookie(HttpRequest request) {
        String cookieHeader = request.getHeaders().get(COOKIE_HEADER);
        if (cookieHeader == null || cookieHeader.isEmpty()) {
            return null;
        }

        String[] cookies = cookieHeader.split(";");
        for (String cookie : cookies) {
            String[] parts = cookie.trim().split("=", 2);
            if (parts.length == 2 && parts[0].equals(COOKIE_NAME)) {
                return parts[1];
            }
        }
        return null;
    }
}
