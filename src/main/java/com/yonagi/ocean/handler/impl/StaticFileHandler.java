package com.yonagi.ocean.handler.impl;

import com.yonagi.ocean.cache.*;
import com.yonagi.ocean.core.protocol.HttpMethod;
import com.yonagi.ocean.core.protocol.HttpRequest;
import com.yonagi.ocean.core.protocol.HttpResponse;
import com.yonagi.ocean.core.protocol.HttpVersion;
import com.yonagi.ocean.handler.RequestHandler;
import com.yonagi.ocean.utils.LocalConfigLoader;
import com.yonagi.ocean.utils.MimeTypeUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;

/**
 * @author Yonagi
 * @version 1.0
 * @program Ocean
 * @description
 * @date 2025/10/05 11:49
 */
public class StaticFileHandler implements RequestHandler {

    private static final Logger log = LoggerFactory.getLogger(StaticFileHandler.class);

    private static final String DEFAULT_404_HTML = "<html>\n" +
            "<head>\n" +
            "    <title>404 Not Found</title>\n" +
            "    <style>\n" +
            "        body {\n" +
            "            width: 35em;\n" +
            "            margin: 0 auto;\n" +
            "            font-family: Tahoma, Verdana, Arial, sans-serif;\n" +
            "        }\n" +
            "    </style>\n" +
            "</head>\n" +
            "<body>\n" +
            "<h1>404 Not Found</h1>\n" +
            "<p>The requested resource was not found on this server.</p>\n" +
            "</body>\n" +
            "</html>";

    private final String webRoot;
    private final String errorPagePath;

    private final StaticFileCache fileCache = StaticFileCacheFactory.getInstance();

    public StaticFileHandler(String webRoot) {
        this.webRoot = webRoot;
        this.errorPagePath = LocalConfigLoader.getProperty("server.404_page");
    }

    @Override
    public void handle(HttpRequest request, OutputStream outputStream) throws IOException {
        String uri = request.getUri();
        if ("/".equals(uri)) {
            uri = "/index.html";
        }
        if (uri.startsWith(webRoot)) {
            uri = uri.substring(webRoot.length());
        }
        File file = new File(webRoot, uri);
        if (!file.exists() || file.isDirectory()) {
            writeNotFound(outputStream);
            return;
        }
        if (!file.getCanonicalPath().startsWith(new File(webRoot).getCanonicalPath())) {
            writeNotFound(outputStream);
            log.warn("Attempted directory traversal attack: {}", uri);
            return;
        }

        String contentType = MimeTypeUtil.getMimeType(file.getName());
        if (contentType == null) {
            contentType = "application/octet-stream";
        }

        try {
            boolean isInCache = fileCache.contain(file.getCanonicalPath());
            CachedFile cf = fileCache.get(file);

            HttpResponse httpResponse = new HttpResponse.Builder()
                    .httpVersion(request.getHttpVersion())
                    .statusCode(200)
                    .statusText("OK")
                    .contentType(contentType)
                    .body(cf.getContent())
                    .build();
            httpResponse.write(outputStream);
            outputStream.flush();
            log.info("Served from {}{}", isInCache ? "cache: " : "disk: ", uri);
        } catch (Exception e) {
            log.error("Error serving file: {}", uri, e);
            new InternalErrorHandler().handle(request, outputStream);
        }
    }

    private void writeNotFound(OutputStream outputStream) throws IOException {
        File errorPage = new File(errorPagePath);
        if (errorPage.exists()) {
            try {
                String contentType = MimeTypeUtil.getMimeType(errorPage.getName());
                if (contentType == null) {
                    contentType = "text/html";
                }
                CachedFile cf = fileCache.get(errorPage);
                HttpResponse httpResponse = new HttpResponse.Builder()
                        .httpVersion(HttpVersion.HTTP_1_1)
                        .statusCode(404)
                        .statusText("Not Found")
                        .contentType(contentType)
                        .body(cf.getContent())
                        .build();
                httpResponse.write(outputStream);
                outputStream.flush();
                return;
            } catch (Exception ignore) {
                // fallback to default 404
            }
        }
        HttpResponse httpResponse = new HttpResponse.Builder()
                .httpVersion(HttpVersion.HTTP_1_1)
                .statusCode(404)
                .statusText("Not Found")
                .contentType("text/html")
                .body(DEFAULT_404_HTML.getBytes())
                .build();
        httpResponse.write(outputStream);
        outputStream.flush();
    }
}
