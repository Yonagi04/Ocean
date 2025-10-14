package com.yonagi.ocean.handler.impl;

import com.yonagi.ocean.core.protocol.HttpRequest;
import com.yonagi.ocean.core.protocol.HttpResponse;
import com.yonagi.ocean.core.protocol.enums.HttpStatus;
import com.yonagi.ocean.core.protocol.enums.HttpVersion;
import com.yonagi.ocean.handler.RequestHandler;
import com.yonagi.ocean.utils.LocalConfigLoader;
import com.yonagi.ocean.utils.MimeTypeUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * @author Yonagi
 * @version 1.0
 * @program Ocean
 * @description
 * @date 2025/10/07 11:55
 */
public class HeadHandler implements RequestHandler {

    private final String webRoot;

    private static final Logger log = LoggerFactory.getLogger(HeadHandler.class);

    public HeadHandler(String webRoot) {
        this.webRoot = webRoot;
    }

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

        String uri = request.getUri();
        if ("/".equals(uri)) {
            uri = "index.html";
        }
        if (uri.startsWith(webRoot)) {
            uri = uri.substring(webRoot.length());
        }
        File file = new File(webRoot, uri);
        if (!file.exists() || file.isDirectory()) {
            writeNotFound(request, output, keepAlive, headers);
            return;
        }
        if (!file.getCanonicalPath().startsWith(new File(webRoot).getCanonicalPath())) {
            writeNotFound(request, output, keepAlive, headers);
            log.warn("Attempted directory traversal attack: {}", uri);
            return;
        }
        String contentType = MimeTypeUtil.getMimeType(file.getName());
        if (contentType == null) {
            contentType = "application/octet-stream";
        }
        HttpResponse response = new HttpResponse.Builder()
                .httpVersion(request.getHttpVersion())
                .httpStatus(HttpStatus.OK)
                .contentType(contentType)
                .headers(headers)
                .build();
        response.write(request, output, keepAlive);
        output.flush();
    }
    
    private void writeNotFound(HttpRequest request, OutputStream output,
                               boolean keepAlive, Map<String, String> headers) throws IOException {
        HttpResponse response = new HttpResponse.Builder()
                .httpVersion(HttpVersion.HTTP_1_1)
                .httpStatus(HttpStatus.NOT_FOUND)
                .contentType("text/html")
                .headers(headers)
                .build();
        response.write(request, output, keepAlive);
        output.flush();
    }
}
