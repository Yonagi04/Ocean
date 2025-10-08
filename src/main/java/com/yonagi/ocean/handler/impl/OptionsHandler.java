package com.yonagi.ocean.handler.impl;

import com.yonagi.ocean.core.protocol.HttpMethod;
import com.yonagi.ocean.core.protocol.HttpRequest;
import com.yonagi.ocean.core.protocol.HttpResponse;
import com.yonagi.ocean.handler.RequestHandler;
import com.yonagi.ocean.utils.LocalConfigLoader;

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
                .statusCode(204)
                .statusText("No Content")
                .contentType("application/json")
                .headers(headers)
                .build();
        response.write(output, keepAlive);
        output.flush();
    }
}
