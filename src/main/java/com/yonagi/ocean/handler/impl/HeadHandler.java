package com.yonagi.ocean.handler.impl;

import com.yonagi.ocean.core.context.HttpContext;
import com.yonagi.ocean.core.protocol.HttpRequest;
import com.yonagi.ocean.core.protocol.HttpResponse;
import com.yonagi.ocean.core.protocol.enums.HttpStatus;
import com.yonagi.ocean.core.protocol.enums.HttpVersion;
import com.yonagi.ocean.handler.RequestHandler;
import com.yonagi.ocean.utils.MimeTypeUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
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
    public void handle(HttpContext httpContext) throws IOException {
        HttpRequest request = httpContext.getRequest();
        Map<String, String> headers = (Map<String, String>) request.getAttribute("HstsHeaders");

        String uri = request.getUri();
        if ("/".equals(uri)) {
            uri = "index.html";
        }
        if (uri.startsWith(webRoot)) {
            uri = uri.substring(webRoot.length());
        }
        File file = new File(webRoot, uri);
        if (!file.exists() || file.isDirectory()) {
            writeNotFound(httpContext);
            return;
        }
        if (!file.getCanonicalPath().startsWith(new File(webRoot).getCanonicalPath())) {
            writeNotFound(httpContext);
            log.warn("[{}] Attempted directory traversal attack: {}", httpContext.getTraceId(), uri);
            return;
        }
        String contentType = MimeTypeUtil.getMimeType(file.getName());
        if (contentType == null) {
            contentType = "application/octet-stream";
        }
        HttpResponse response = httpContext.getResponse().toBuilder()
                .httpVersion(request.getHttpVersion())
                .httpStatus(HttpStatus.OK)
                .contentType(contentType)
                .headers(headers)
                .build();
        httpContext.setResponse(response);
    }
    
    private void writeNotFound(HttpContext httpContext) throws IOException {
        Map<String, String> headers = (Map<String, String>) httpContext.getRequest().getAttribute("HstsHeaders");
        HttpResponse response = httpContext.getResponse().toBuilder()
                .httpVersion(HttpVersion.HTTP_1_1)
                .httpStatus(HttpStatus.NOT_FOUND)
                .contentType("text/plain; charset=utf/8")
                .headers(headers)
                .build();
        httpContext.setResponse(response);
    }
}
