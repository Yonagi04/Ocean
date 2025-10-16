package com.yonagi.ocean.core.protocol.handler.impl;

import com.yonagi.ocean.core.protocol.HttpRequest;
import com.yonagi.ocean.core.protocol.enums.HttpMethod;
import com.yonagi.ocean.core.protocol.handler.HttpProtocolHandler;
import com.yonagi.ocean.core.protocol.utils.BodyReadingUtility;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;

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
        String contentType = request.getHeaders().getOrDefault("content-type", "").toLowerCase();
        boolean isMultiPart = contentType.contains("multipart/form-data");

        boolean shouldReadSynchronously = (request.getMethod() == HttpMethod.POST || request.getMethod() == HttpMethod.PUT) &&
                !isMultiPart;

        if (shouldReadSynchronously) {
            byte[] bodyData = BodyReadingUtility.readTextBodyFromInputStream(request.getRawBodyInputStream(), request.getHeaders());
            log.debug("Synchronous body read complete. Length: {}", bodyData == null ? 0 : bodyData.length);

            return new HttpRequest.Builder()
                    .method(request.getMethod())
                    .uri(request.getUri())
                    .httpVersion(request.getHttpVersion())
                    .headers(request.getHeaders())
                    .queryParams(request.getQueryParams())
                    .body(bodyData)
                    .rawBodyInputStream(null)
                    .build();
        }

        return request;
    }
}
