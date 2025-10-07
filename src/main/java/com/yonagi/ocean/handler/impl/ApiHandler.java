package com.yonagi.ocean.handler.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yonagi.ocean.core.protocol.HttpRequest;
import com.yonagi.ocean.core.protocol.HttpResponse;
import com.yonagi.ocean.handler.RequestHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * @author Yonagi
 * @version 1.0
 * @program Ocean
 * @description
 * @date 2025/10/07 11:53
 */
public class ApiHandler implements RequestHandler {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    private static final Logger log = LoggerFactory.getLogger(ApiHandler.class);

    @Override
    public void handle(HttpRequest request, OutputStream output) throws IOException {
        String contentType = request.getHeaders().getOrDefault("content-type", "");
        Map<String, Object> responseData = new HashMap<>();
        String charset = "UTF-8";
        String mimeType = contentType.split(";")[0].trim();
        if (contentType.contains("charset=")) {
            charset = contentType.split("charset=")[1].trim();
        }
        if ("application/x-www-form-urlencoded".equals(mimeType)) {
            Map<String, String> formParams = parseFormData(request.getBody(), charset);
            responseData.put("status", "ok");
            responseData.put("data", formParams);
        } else if ("application/json".equals(mimeType)) {
            Map<String, Object> jsonData = objectMapper.readValue(request.getBody(), Map.class);
            responseData.put("status", "ok");
            responseData.put("data", jsonData);
        } else {
            HttpResponse response = new HttpResponse.Builder()
                    .httpVersion(request.getHttpVersion())
                    .statusCode(415)
                    .statusText("Unsupported Media Type")
                    .contentType("text/plain")
                    .body("Unsupported Media Type".getBytes())
                    .build();
            output.write(response.toString().getBytes());
            output.flush();
            log.warn("Client sent unsupported Content-Type: {}", contentType);
            return;
        }
        String responseBody = objectMapper.writeValueAsString(responseData);
        HttpResponse response = new HttpResponse.Builder()
                .httpVersion(request.getHttpVersion())
                .statusCode(200)
                .statusText("OK")
                .contentType("application/json")
                .body(responseBody.getBytes())
                .build();
        output.write(response.toString().getBytes());
        output.flush();
    }

    private Map<String, String> parseFormData(byte[] body, String charset) {
        String formData = new String(body);
        Map<String, String> result = new HashMap<>();
        if (formData == null || formData.isEmpty()) {
            return result;
        }
        String[] pairs = formData.split("&");
        for (String pair : pairs) {
            try {
                String[] kv = pair.split("=", 2);
                String key = URLDecoder.decode(kv[0], charset);
                String value = kv.length > 1 ? URLDecoder.decode(kv[1], charset) : "";
                result.put(key, value);
            } catch (UnsupportedEncodingException e) {
                log.error("Error decoding form data: {}, client sent charset type: {}", e.getMessage(), charset, e);
            }
        }
        return result;
    }
}
