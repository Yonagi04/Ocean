package com.yonagi.ocean.handler.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yonagi.ocean.core.context.HttpContext;
import com.yonagi.ocean.core.protocol.HttpRequest;
import com.yonagi.ocean.core.protocol.HttpResponse;
import com.yonagi.ocean.core.protocol.enums.HttpStatus;
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
 * @date 2025/10/08 12:45
 */
public class GenericApiHandler implements RequestHandler {

    private static final Logger log = LoggerFactory.getLogger(GenericApiHandler.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    public GenericApiHandler() {}

    public GenericApiHandler(String webRoot) {
        log.debug("GenericApiHandler initialized with webRoot: {}", webRoot);
    }

    @Override
    public void handle(HttpContext httpContext) throws IOException {
        HttpRequest request = httpContext.getRequest();

        log.info("GenericApiHandler handling {} request to {}", request.getMethod(), request.getUri());

        Map<String, Object> responseData = new HashMap<>();
        responseData.put("method", request.getMethod().toString());
        responseData.put("path", request.getUri());
        responseData.put("message", "Generic API handler response");

        // 根据HTTP方法处理不同的逻辑
        switch (request.getMethod()) {
            case GET:
                handleGetRequest(request, responseData);
                break;
            case POST:
                handlePostRequest(request, responseData);
                break;
            case PUT:
                handlePutRequest(request, responseData);
                break;
            case DELETE:
                handleDeleteRequest(request, responseData);
                break;
            default:
                responseData.put("error", "Unsupported HTTP method");
        }
        Map<String, String> headers = (Map<String, String>) request.getAttribute("HstsHeaders");

        String responseBody = objectMapper.writeValueAsString(responseData);
        HttpResponse response = httpContext.getResponse().toBuilder()
                .httpVersion(request.getHttpVersion())
                .httpStatus(HttpStatus.OK)
                .contentType("application/json")
                .headers(headers)
                .body(responseBody.getBytes())
                .build();

        httpContext.setResponse(response);
    }

    private void handleGetRequest(HttpRequest request, Map<String, Object> responseData) {
        // 处理GET请求 - 通常处理查询参数
        Map<String, String> queryParams = parseQueryParameters(request.getUri());
        responseData.put("queryParams", queryParams);
        responseData.put("operation", "read");
    }

    private void handlePostRequest(HttpRequest request, Map<String, Object> responseData) {
        // 处理POST请求 - 通常处理请求体
        responseData.put("operation", "create");
        responseData.put("body", "POST request body would be processed here");
    }

    private void handlePutRequest(HttpRequest request, Map<String, Object> responseData) {
        // 处理PUT请求
        responseData.put("operation", "update");
        responseData.put("body", "PUT request body would be processed here");
    }

    private void handleDeleteRequest(HttpRequest request, Map<String, Object> responseData) {
        // 处理DELETE请求
        responseData.put("operation", "delete");
    }

    private Map<String, String> parseQueryParameters(String uri) {
        Map<String, String> params = new HashMap<>();
        if (uri.contains("?")) {
            String queryString = uri.substring(uri.indexOf("?") + 1);
            String[] pairs = queryString.split("&");
            for (String pair : pairs) {
                if (pair.contains("=")) {
                    String[] keyValue = pair.split("=", 2);
                    params.put(keyValue[0], keyValue.length > 1 ? keyValue[1] : "");
                }
            }
        }
        return params;
    }
}
