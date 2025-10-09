package com.yonagi.ocean.handler.impl;

import com.yonagi.ocean.core.protocol.HttpRequest;
import com.yonagi.ocean.core.protocol.HttpResponse;
import com.yonagi.ocean.handler.RequestHandler;
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
 * @date 2025/10/09 13:09
 */
public class RedirectHandler implements RequestHandler {

    private final String webRoot;

    private static final Logger log = LoggerFactory.getLogger(RedirectHandler.class);

    public RedirectHandler(String webRoot) {
        this.webRoot = webRoot;
    }

    @Override
    public void handle(HttpRequest request, OutputStream output) throws IOException {
        handle(request, output, true);
    }

    @Override
    public void handle(HttpRequest request, OutputStream output, boolean keepAlive) throws IOException {
        String targetUrl = (String) request.getAttribute("targetUrl");
        Integer statusCode = (Integer) request.getAttribute("statusCode");
        if (targetUrl == null || statusCode == null) {
            log.error("RedirectHandler: Missing targetUrl or statusCode attribute");
            return;
        }
        Map<String, String> requestHeaders = request.getHeaders();
        String host = requestHeaders.get("Host");
        if (host == null) {
            host = LocalConfigLoader.getProperty("server.url") + ":" + LocalConfigLoader.getProperty("server.port", "8080");
        }
        // TODO support https
        String protocol = "http";

        String finalLocation;
        if (targetUrl.startsWith("http://") || targetUrl.startsWith("https://")) {
            // TODO safety check
            finalLocation = targetUrl;
        } else {
            String baseUrl = protocol + "://" + host;
            if (!targetUrl.startsWith("/")) {
                finalLocation = baseUrl + "/" + targetUrl;
            } else {
                finalLocation = baseUrl + targetUrl;
            }
        }

        if (statusCode != 301 && statusCode != 302 && statusCode != 307 && statusCode != 308) {
            log.warn("RedirectHandler: Invalid statusCode for redirect: {}, reset to 302", statusCode);
            statusCode = 302;
        }
        HttpResponse.Builder builder = new HttpResponse.Builder()
                .httpVersion(request.getHttpVersion())
                .statusCode(statusCode)
                .contentType(request.getAttribute("contentType") != null ? (String) request.getAttribute("contentType") : "text/html");
        if (statusCode == 301) {
            builder.statusText("Moved Permanently");
        } else if (statusCode == 302) {
            builder.statusText("Found");
        } else if (statusCode == 307) {
            builder.statusText("Temporary Redirect");
        } else {
            builder.statusText("Permanent Redirect");
        }
        Map<String, String> responseHeaders = new HashMap<>();
        responseHeaders.put("Location", finalLocation);
        builder.headers(responseHeaders);

        HttpResponse response = builder.build();
        response.write(output, keepAlive);
        output.flush();
        log.info("Request path: {}, redirect to: {}, status code: {}", request.getUri(), finalLocation, statusCode);
    }
}
