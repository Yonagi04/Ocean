package com.yonagi.ocean.handler.impl;

import com.yonagi.ocean.core.protocol.HttpRequest;
import com.yonagi.ocean.core.protocol.HttpResponse;
import com.yonagi.ocean.core.protocol.HttpVersion;
import com.yonagi.ocean.handler.RequestHandler;
import com.yonagi.ocean.utils.MimeTypeUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;

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
        String contentType = MimeTypeUtil.getMimeType(file.getName());
        if (contentType == null) {
            contentType = "application/octet-stream";
        }
        HttpResponse response = new HttpResponse.Builder()
                .httpVersion(request.getHttpVersion())
                .statusCode(200)
                .statusText("OK")
                .contentType(contentType)
                .build();
        response.write(output, keepAlive);
        output.flush();
    }

    private void writeNotFound(OutputStream output) throws IOException {
        writeNotFound(output, true); // Default to keep-alive
    }
    
    private void writeNotFound(OutputStream output, boolean keepAlive) throws IOException {
        HttpResponse response = new HttpResponse.Builder()
                .httpVersion(HttpVersion.HTTP_1_1)
                .statusCode(404)
                .statusText("Not Found")
                .contentType("text/html")
                .build();
        response.write(output, keepAlive);
        output.flush();
    }
}
