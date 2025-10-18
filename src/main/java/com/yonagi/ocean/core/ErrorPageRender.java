package com.yonagi.ocean.core;

import com.yonagi.ocean.core.context.HttpContext;
import com.yonagi.ocean.core.protocol.HttpRequest;
import com.yonagi.ocean.core.protocol.HttpResponse;
import com.yonagi.ocean.core.protocol.enums.HttpStatus;
import com.yonagi.ocean.utils.TemplateRenderer;

import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Map;

/**
 * @author Yonagi
 * @version 1.0
 * @program Ocean
 * @description
 * @date 2025/10/18 14:46
 */
public class ErrorPageRender {

    public static void render(HttpContext httpContext) {
        HttpRequest request = httpContext.getRequest();
        HttpResponse response = httpContext.getResponse();

        String clientIp = (String) request.getAttributeOrDefault("clientIp", "");
        String userAgent = request.getHeaders().getOrDefault("user-agent", "Unknown");
        String method = request.getMethod() != null ? request.getMethod().name() : "UNKNOWN";
        String uri = request.getUri();
        String timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
        String traceId = (String) request.getAttributeOrDefault("traceId", "");

        Map<String, Object> data = Map.ofEntries(
                Map.entry("traceId", traceId),
                Map.entry("status", response.getHttpStatus().toString()),
                Map.entry("uri", uri),
                Map.entry("method", method),
                Map.entry("clientIp", clientIp),
                Map.entry("userAgent", userAgent),
                Map.entry("timestamp", timestamp)
        );
        String html = "";
        try {
            html =  TemplateRenderer.render("error", data);
        } catch (Exception e) {
            html =  "<h1>" + response.getHttpStatus().getCode() + " Error</h1><p>" + response.getHttpStatus().getReasonPhrase() + "</p>";
        }

        HttpResponse errorResponse = response.toBuilder()
                .httpStatus(HttpStatus.INTERNAL_SERVER_ERROR)
                .contentType("text/html; charset=utf-8")
                .body(html.getBytes(StandardCharsets.UTF_8))
                .build();

        httpContext.setResponse(errorResponse);
    }
}
