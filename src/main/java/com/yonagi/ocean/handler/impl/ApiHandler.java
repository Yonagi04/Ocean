package com.yonagi.ocean.handler.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import com.yonagi.ocean.core.ErrorPageRender;
import com.yonagi.ocean.core.context.HttpContext;
import com.yonagi.ocean.core.gzip.GzipEncoder;
import com.yonagi.ocean.core.gzip.GzipEncoderManager;
import com.yonagi.ocean.core.protocol.HttpRequest;
import com.yonagi.ocean.core.protocol.HttpResponse;
import com.yonagi.ocean.core.protocol.enums.ContentType;
import com.yonagi.ocean.core.protocol.enums.HttpStatus;
import com.yonagi.ocean.handler.RequestHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.*;

/**
 * @author Yonagi
 * @version 1.0
 * @program Ocean
 * @description
 * @date 2025/10/07 11:53
 */
public class ApiHandler implements RequestHandler {

    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final XmlMapper xmlMapper = new XmlMapper();

    private static final Logger log = LoggerFactory.getLogger(ApiHandler.class);


    public ApiHandler() {

    }

    @Override
    public void handle(HttpContext httpContext) throws IOException {
        HttpRequest request = httpContext.getRequest();
        String contentType = request.getHeaders().getOrDefault("content-type", "");
        Map<String, Object> responseData = new HashMap<>();
        String charset = "UTF-8";
        String mimeType = contentType.split(";")[0].trim();
        if (contentType.contains("charset=")) {
            charset = contentType.split("charset=")[1].trim();
        }
        Map<String, String> headers = (Map<String, String>) request.getAttribute("HstsHeaders");

        // 使用策略映射替代 if-else
        ContentProcessor processor = processors.get(mimeType);
        if (processor == null) {
            HttpResponse response = httpContext.getResponse().toBuilder()
                    .httpVersion(request.getHttpVersion())
                    .httpStatus(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
                    .contentType(ContentType.TEXT_PLAIN)
                    .headers(headers)
                    .body("Unsupported Media Type".getBytes())
                    .build();
            httpContext.setResponse(response);
            ErrorPageRender.render(httpContext);
            log.warn("[{}] Client sent unsupported Content-Type: {}", httpContext.getTraceId(), contentType);
            return;
        }

        try {
            processor.process(request, contentType, charset, responseData);
        } catch (Exception e) {
            String msgPrefix = "application/xml".equals(mimeType) ? "XML parsing error: " : "Multipart form data parsing error: ";
            log.error("[{}] {}{}", httpContext.getTraceId(), msgPrefix, e.getMessage(), e);
            HttpResponse errorResponse = httpContext.getResponse().toBuilder()
                    .httpVersion(request.getHttpVersion())
                    .httpStatus(HttpStatus.BAD_REQUEST)
                    .contentType(ContentType.TEXT_PLAIN)
                    .headers(headers)
                    .body((msgPrefix + e.getMessage()).getBytes())
                    .build();
            ErrorPageRender.render(httpContext);
            httpContext.setResponse(errorResponse);
            return;
        }

        String responseBody = objectMapper.writeValueAsString(responseData);
        GzipEncoder encoder = GzipEncoderManager.getEncoderInstance();
        String acceptEncoding = request.getHeaders().getOrDefault("accept-encoding", "");
        byte[] finalBody = encoder.encode(responseBody.getBytes(charset), acceptEncoding);

        if (!Arrays.equals(finalBody, responseBody.getBytes(charset))) {
            headers.put("Content-Encoding", "gzip");
        }

        HttpResponse response = httpContext.getResponse().toBuilder()
                .httpVersion(request.getHttpVersion())
                .httpStatus(HttpStatus.CREATED)
                .contentType(ContentType.APPLICATION_JSON)
                .headers(headers)
                .body(finalBody)
                .build();
        httpContext.setResponse(response);
    }

    @FunctionalInterface
    private interface ContentProcessor {
        void process(HttpRequest request, String contentType, String charset, Map<String, Object> responseData) throws Exception;
    }

    private final Map<String, ContentProcessor> processors = initProcessors();

    private Map<String, ContentProcessor> initProcessors() {
        Map<String, ContentProcessor> map = new HashMap<>();

        map.put("application/x-www-form-urlencoded", (request, contentType, charset, responseData) -> {
            Map<String, String> formParams = parseFormData(request.getBody(), charset);
            responseData.put("status", "ok");
            responseData.put("data", formParams);
        });

        map.put("application/json", (request, contentType, charset, responseData) -> {
            @SuppressWarnings("unchecked")
            Map<String, Object> jsonData = objectMapper.readValue(request.getBody(), Map.class);
            responseData.put("status", "ok");
            responseData.put("data", jsonData);
        });

        map.put("application/xml", (request, contentType, charset, responseData) -> {
            Map<String, Object> xmlData = parseXmlData(request.getBody(), charset);
            responseData.put("status", "ok");
            responseData.put("data", xmlData);
        });

        return map;
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

    private Map<String, Object> parseXmlData(byte[] body, String charset) throws Exception {
        String xmlContent = new String(body, charset);
        log.debug("Parsing XML content: {}", xmlContent);
        if (xmlContent == null || xmlContent.trim().isEmpty()) {
            return new HashMap<>();
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> xmlMap = xmlMapper.readValue(xmlContent, Map.class);
        return xmlMap;
    }
}
