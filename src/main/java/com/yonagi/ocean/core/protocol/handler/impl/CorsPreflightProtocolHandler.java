package com.yonagi.ocean.core.protocol.handler.impl;

import com.yonagi.ocean.core.CorsManager;
import com.yonagi.ocean.core.protocol.HttpRequest;
import com.yonagi.ocean.core.protocol.HttpResponse;
import com.yonagi.ocean.core.protocol.enums.ContentType;
import com.yonagi.ocean.core.protocol.enums.HttpStatus;
import com.yonagi.ocean.core.protocol.handler.HttpProtocolHandler;
import com.yonagi.ocean.utils.LocalConfigLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * @author Yonagi
 * @version 1.0
 * @program Ocean
 * @description
 * @date 2025/10/16 15:26
 */
public class CorsPreflightProtocolHandler implements HttpProtocolHandler {

    private static final Logger log = LoggerFactory.getLogger(CorsPreflightProtocolHandler.class);

    private final boolean isSsl;

    private static final long maxAge = Long.parseLong(LocalConfigLoader.getProperty("server.ssl.hsts.max_age", "31536000"));
    private static final boolean enabledIncludeSubdomains = Boolean.parseBoolean(LocalConfigLoader.getProperty("server.ssl.hsts.enabled_include_subdomains", "false"));
    private static final boolean enabledPreload = Boolean.parseBoolean(LocalConfigLoader.getProperty("server.ssl.hsts.enabled_preload", "false"));

    public CorsPreflightProtocolHandler(boolean isSsl) {
        this.isSsl = isSsl;
    }

    public Map<String, String> applyHstsHeader() {
        Map<String, String> hstsHeader = new HashMap<>();
        if (isSsl) {
            StringBuilder hstsValue = new StringBuilder();
            hstsValue.append("max-age=").append(maxAge);

            if (enabledIncludeSubdomains) {
                hstsValue.append("; includeSubDomains");
            }
            if (enabledPreload && enabledIncludeSubdomains && maxAge >= 31536000) {
                hstsValue.append("; preload");
            }
            hstsHeader.put("Strict-Transport-Security", hstsValue.toString());
        }
        return hstsHeader;
    }

    @Override
    public HttpRequest handle(HttpRequest request, OutputStream output) throws IOException {
        Map<String, String> headers = CorsManager.handleCors(request);
        boolean isPreflightTerminated = headers != null && "true".equals(headers.get("__IS_PREFLIGHT__"));
        if (isPreflightTerminated) {
            log.info("CORS preflight request (OPTIONS) handled and terminated");
            Map<String, String> hstsHeaders = (Map<String, String>) request.getAttribute("HstsHeaders");
            if (hstsHeaders != null && !hstsHeaders.isEmpty()) {
                headers.putAll(hstsHeaders);
            }
            HttpResponse response = new HttpResponse.Builder()
                    .httpVersion(request.getHttpVersion())
                    .httpStatus(HttpStatus.NO_CONTENT)
                    .headers(headers)
                    .contentType(ContentType.TEXT_PLAIN)
                    .body(new byte[0])
                    .build();
            response.write(request, output, false);
            output.flush();
            return null;
        }

        if (headers != null && !headers.isEmpty()) {
            headers.remove("__IS_PREFLIGHT__");
            request.setAttribute("CorsResponseHeaders", headers);
        }
        return request;
    }
}
