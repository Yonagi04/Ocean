package com.yonagi.ocean.core.protocol.handler.impl;

import com.yonagi.ocean.core.protocol.HttpRequest;
import com.yonagi.ocean.core.protocol.handler.HttpProtocolHandler;
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
 * @date 2025/10/16 18:52
 */
public class HstsProtocolHandler implements HttpProtocolHandler {

    private final boolean isSsl;

    public HstsProtocolHandler(boolean isSsl) {
        this.isSsl = isSsl;
    }

    @Override
    public HttpRequest handle(HttpRequest request, OutputStream output) throws IOException {
        Map<String, String> headers = new HashMap<>();
        if (isSsl) {
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
        request.getAttribute().setHstsHeaders(headers);
        return request;
    }
}
