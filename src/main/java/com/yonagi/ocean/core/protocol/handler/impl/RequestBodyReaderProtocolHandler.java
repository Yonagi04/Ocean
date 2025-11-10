package com.yonagi.ocean.core.protocol.handler.impl;

import com.yonagi.ocean.core.protocol.HttpRequest;
import com.yonagi.ocean.core.protocol.enums.HttpMethod;
import com.yonagi.ocean.core.protocol.handler.HttpProtocolHandler;
import com.yonagi.ocean.core.protocol.utils.BodyReadingUtility;
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
 * @date 2025/10/16 15:11
 */
public class RequestBodyReaderProtocolHandler implements HttpProtocolHandler {

    private static final Logger log = LoggerFactory.getLogger(RequestBodyReaderProtocolHandler.class);

    public RequestBodyReaderProtocolHandler() {

    }

    @Override
    public HttpRequest handle(HttpRequest request, OutputStream output) throws IOException {
        boolean shouldReadSynchronously =
                request.getMethod() == HttpMethod.POST ||
                        request.getMethod() == HttpMethod.PUT ||
                        request.getMethod() == HttpMethod.PATCH;

        if (shouldReadSynchronously) {
            String contentType = request.getHeaders().get("content-type");
            String contentLength = request.getHeaders().get("content-length");
            boolean isMultipart = contentType != null && contentType.toLowerCase().startsWith("multipart/");
            
            log.debug("Reading request body: method={}, contentType={}, contentLength={}, isMultipart={}, " +
                     "rawBodyInputStream={}", 
                     request.getMethod(), contentType, contentLength, isMultipart, 
                     request.getRawBodyInputStream() != null);
            
            byte[] bodyData = BodyReadingUtility.readBodyFromInputStream(request.getRawBodyInputStream(), request.getHeaders());
            
            log.debug("Read request body: expectedLength={}, actualLength={}, isMultipart={}", 
                     contentLength != null ? contentLength : "unknown", 
                     bodyData != null ? bodyData.length : 0, isMultipart);
            
            // 验证读取结果
            if (contentLength != null && !contentLength.trim().isEmpty()) {
                try {
                    int expectedLength = Integer.parseInt(contentLength.trim());
                    if (expectedLength > 0 && (bodyData == null || bodyData.length == 0)) {
                        log.error("Failed to read request body: Content-Length is {} but read {} bytes. " +
                                 "Content-Type: {}, isMultipart: {}", 
                                 expectedLength, bodyData != null ? bodyData.length : 0, 
                                 contentType, isMultipart);
                        throw new IOException("Failed to read request body: expected " + expectedLength + 
                                            " bytes but got " + (bodyData != null ? bodyData.length : 0) + " bytes");
                    }
                } catch (NumberFormatException e) {
                    log.warn("Invalid Content-Length header: {}", contentLength, e);
                }
            }
            
            // 创建新的 headers，更新 Content-Length 以匹配实际读取的 body 长度
            Map<String, String> newHeaders = new HashMap<>(request.getHeaders());
            if (bodyData != null && bodyData.length > 0) {
                // 更新 Content-Length 头部以匹配实际读取的 body 长度
                int actualLength = bodyData.length;
                String originalContentLength = newHeaders.get("content-length");
                if (originalContentLength != null) {
                    try {
                        int originalLength = Integer.parseInt(originalContentLength.trim());
                        if (originalLength != actualLength) {
                            log.warn("Content-Length mismatch: original={}, actual={}. Updating Content-Length header to match actual body length.", 
                                    originalLength, actualLength);
                        }
                    } catch (NumberFormatException e) {
                        log.warn("Invalid original Content-Length: {}", originalContentLength);
                    }
                }
                // 更新 Content-Length 头部
                newHeaders.put("content-length", String.valueOf(actualLength));
                log.debug("Updated Content-Length header to {} (actual body length)", actualLength);
            } else if (bodyData != null && bodyData.length == 0) {
                // 如果 body 为空，移除 Content-Length 头部或设置为 0
                newHeaders.put("content-length", "0");
            }
            
            HttpRequest.Builder builder = new HttpRequest.Builder()
                    .method(request.getMethod())
                    .uri(request.getUri())
                    .httpVersion(request.getHttpVersion())
                    .headers(newHeaders)
                    .queryParams(request.getQueryParams())
                    .body(bodyData);
            
            // 保留原始输入流引用（如果 body 为空，可能需要后续读取）
            if (request.getRawBodyInputStream() != null && (bodyData == null || bodyData.length == 0)) {
                builder.rawBodyInputStream(request.getRawBodyInputStream());
            }
            
            HttpRequest newRequest = builder.build();
            log.debug("Created new HttpRequest with body length: {}, Content-Length header: {}", 
                     newRequest.getBody() != null ? newRequest.getBody().length : 0,
                     newRequest.getHeaders().get("content-length"));
            
            return newRequest;
        }

        return request;
    }
}
