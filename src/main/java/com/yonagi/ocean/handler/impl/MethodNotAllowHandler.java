package com.yonagi.ocean.handler.impl;

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
    public void handle(HttpRequest request, OutputStream output) throws IOException {
        handle(request, output, true); // Default to keep-alive
    }
    
    @Override
    public void handle(HttpRequest request, OutputStream output, boolean keepAlive) throws IOException {
        Map<String, String> headers = new HashMap<>();
        if ((Boolean) request.getAttribute("isSsl")) {
            StringBuilder hstsValue = new StringBuilder();
            long maxAge = Long.parseLong(LocalConfigLoader.getProperty("server.ssl.hsts.max_age", "31536000"));
            hstsValue.append("max-age=").append(maxAge);
            boolean enabledIncludeSubdomains = Boolean.parseBoolean(LocalConfigLoader.getProperty("server.ssl.hsts.enabled_include_subdomains", "false"));
            boolean enabledPreload = Boolean.parseBoolean(LocalConfigLoader.getProperty("server.ssl.hsts.enabled_preload", "false"));
            if (enabledIncludeSubdomains) {
                hstsValue.append("; includeSubDomains");
            }
            if (enabledPreload && enabledIncludeSubdomains && maxAge >= 31536000) {
                hstsValue.append("; preload");
            }
            headers.put("Strict-Transport-Security", hstsValue.toString());
        }
        String body = "<h1>405 Method Not Allowed</h1>";
        HttpResponse response = new HttpResponse.Builder()
                .httpVersion(HttpVersion.HTTP_1_1)
                .httpStatus(HttpStatus.METHOD_NOT_ALLOWED)
                .contentType("text/html")
                .headers(headers)
                .body(body.getBytes())
                .build();
        response.write(request, output, keepAlive);
        output.flush();
    }
}
