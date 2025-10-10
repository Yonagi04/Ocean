package com.yonagi.ocean.handler.impl;

import com.yonagi.ocean.core.protocol.*;
import com.yonagi.ocean.core.protocol.enums.HttpMethod;
import com.yonagi.ocean.core.protocol.enums.HttpStatus;
import com.yonagi.ocean.core.protocol.enums.HttpVersion;
import com.yonagi.ocean.handler.RequestHandler;
import com.yonagi.ocean.utils.LocalConfigLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author Yonagi
 * @version 1.0
 * @program Ocean
 * @description
 * @date 2025/10/07 11:55
 */
public class OptionsHandler implements RequestHandler {

    private static final Logger log = LoggerFactory.getLogger(OptionsHandler.class);

    private static String webRoot;

    public OptionsHandler(String webRoot) {
        this.webRoot = webRoot;
    }

    @Override
    public void handle(HttpRequest request, OutputStream output) throws IOException {
        handle(request, output, true); // Default to keep-alive
    }
    
    @Override
    public void handle(HttpRequest request, OutputStream output, boolean keepAlive) throws IOException {
        String uri = request.getUri();
        if ("/".equals(uri)) {
            uri = "index.html";
        }
        if (uri.startsWith(webRoot)) {
            uri = uri.substring(webRoot.length());
        }
        File file = new File(webRoot, uri);
        if (!file.exists() || file.isDirectory()) {
            writeNotFound(output, keepAlive);
            return;
        }
        if (!file.getCanonicalPath().startsWith(new File(webRoot).getCanonicalPath())) {
            writeNotFound(output, keepAlive);
            log.warn("Attempted directory traversal attack: {}", uri);
            return;
        }

        List<String> allowedMethods = new ArrayList<>();
        for (HttpMethod method : HttpMethod.values()) {
            allowedMethods.add(method.name());
        }
        String allow = String.join(", ", allowedMethods);
        Map<String, String> headers = new HashMap<>(Map.of(
                "Allow", allow
        ));
        if (Boolean.parseBoolean(LocalConfigLoader.getProperty("server.cors.enabled"))) {
            headers.put("Access-Control-Allow-Origin", LocalConfigLoader.getProperty("server.cors.allow_origin"));
            headers.put("Access-Control-Allow-Methods", LocalConfigLoader.getProperty("server.cors.allow_methods"));
            headers.put("Access-Control-Allow-Headers", LocalConfigLoader.getProperty("server.cors.allow_headers"));
            headers.put("Access-Control-Max-Age", LocalConfigLoader.getProperty("server.cors.max_age"));
            headers.put("Access-Control-Allow-Credentials", LocalConfigLoader.getProperty("server.cors.allow_credentials"));
            headers.put("Access-Control-Expose-Headers", LocalConfigLoader.getProperty("server.cors.expose_headers"));
        }
        HttpResponse response = new HttpResponse.Builder()
                .httpVersion(request.getHttpVersion())
                .httpStatus(HttpStatus.NO_CONTENT)
                .contentType("text/html")
                .headers(headers)
                .build();
        response.write(output, keepAlive);
        output.flush();
    }

    private void writeNotFound(OutputStream output, boolean keepAlive) throws IOException {
        HttpResponse httpResponse = new HttpResponse.Builder()
                .httpVersion(HttpVersion.HTTP_1_1)
                .httpStatus(HttpStatus.NOT_FOUND)
                .contentType("text/html")
                .build();
        httpResponse.write(output, keepAlive);
        output.flush();
    }
}
