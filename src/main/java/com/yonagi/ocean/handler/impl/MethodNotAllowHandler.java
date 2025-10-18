package com.yonagi.ocean.handler.impl;

import com.yonagi.ocean.core.context.HttpContext;
import com.yonagi.ocean.core.protocol.HttpRequest;
import com.yonagi.ocean.core.protocol.HttpResponse;
import com.yonagi.ocean.core.protocol.enums.HttpStatus;
import com.yonagi.ocean.core.protocol.enums.HttpVersion;
import com.yonagi.ocean.handler.RequestHandler;
import com.yonagi.ocean.utils.LocalConfigLoader;

import java.io.IOException;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * @author Yonagi
 * @version 1.0
 * @program Ocean
 * @description
 * @date 2025/10/07 11:58
 */
public class MethodNotAllowHandler implements RequestHandler {

    
    @Override
    public void handle(HttpContext httpContext) throws IOException {
        Map<String, String> headers = (Map<String, String>) httpContext.getRequest().getAttribute("HstsHeaders");
        String body = "HTTP Method not specified or supported.";
        HttpResponse response = httpContext.getResponse().toBuilder()
                .httpVersion(HttpVersion.HTTP_1_1)
                .httpStatus(HttpStatus.METHOD_NOT_ALLOWED)
                .contentType("text/html")
                .headers(headers)
                .body(body.getBytes())
                .build();
        httpContext.setResponse(response);
    }
}
