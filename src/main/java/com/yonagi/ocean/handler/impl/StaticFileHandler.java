package com.yonagi.ocean.handler.impl;

import com.yonagi.ocean.cache.*;
import com.yonagi.ocean.core.context.HttpContext;
import com.yonagi.ocean.core.gzip.GzipEncoder;
import com.yonagi.ocean.core.gzip.GzipEncoderManager;
import com.yonagi.ocean.core.protocol.*;
import com.yonagi.ocean.core.protocol.enums.HttpStatus;
import com.yonagi.ocean.core.protocol.enums.HttpVersion;
import com.yonagi.ocean.handler.RequestHandler;
import com.yonagi.ocean.utils.LocalConfigLoader;
import com.yonagi.ocean.utils.MimeTypeUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

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

    public StaticFileHandler(String webRoot) {
        this.webRoot = webRoot;
        this.errorPagePath = LocalConfigLoader.getProperty("server.not_found_page");
    }
    
    @Override
    public void handle(HttpContext httpContext) throws IOException {
        HttpRequest request = httpContext.getRequest();
        StaticFileCache fileCache = StaticFileCacheFactory.getInstance();
        String uri = request.getUri();
        if ("/".equals(uri)) {
            uri = "/index.html";
        }
        if (uri.startsWith(webRoot)) {
            uri = uri.substring(webRoot.length());
        }
        Map<String, String> headers = (Map<String, String>) request.getAttribute("HstsHeaders");

        File file = new File(webRoot, uri);
        if (!file.exists() || file.isDirectory()) {
            writeNotFound(httpContext, headers);
            return;
        }
        if (!file.getCanonicalPath().startsWith(new File(webRoot).getCanonicalPath())) {
            writeNotFound(httpContext, headers);
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

            String etag = generateETag(cf);
            if (Boolean.parseBoolean(LocalConfigLoader.getProperty("server.http_cache.enabled"))) {
                String maxAgeSecond = LocalConfigLoader.getProperty("server.http_cache.max_age_seconds");
                String cacheScope = LocalConfigLoader.getProperty("server.http_cache.cache_scope");
                String cacheControl = "max-age=" + maxAgeSecond + ", " + cacheScope;
                headers.put("Cache-Control", cacheControl);
                headers.put("ETag", etag);
            }

            String ifNoneMatch = request.getHeaders() != null ? request.getHeaders().get("if-none-match") : null;
            if (ifNoneMatch != null && matchesEtag(ifNoneMatch, etag)) {
                HttpResponse notModified = httpContext.getResponse().toBuilder()
                        .httpVersion(request.getHttpVersion())
                        .httpStatus(HttpStatus.NOT_MODIFIED)
                        .contentType(contentType)
                        .headers(headers)
                        .build();
                httpContext.setResponse(notModified);
                log.info("Respond 304 Not Modified for {}", uri);
                return;
            }

            GzipEncoder encoder = GzipEncoderManager.getEncoderInstance();
            String acceptEncoding = request.getHeaders().get("accept-encoding");
            byte[] finalBody = encoder.encode(cf.getContent(), acceptEncoding);
            if (!Arrays.equals(finalBody, cf.getContent())) {
                headers.put("Content-Encoding", "gzip");
            }
            HttpResponse httpResponse = httpContext.getResponse().toBuilder()
                    .httpVersion(request.getHttpVersion())
                    .httpStatus(HttpStatus.OK)
                    .contentType(contentType)
                    .headers(headers)
                    .body(finalBody)
                    .build();
            httpContext.setResponse(httpResponse);
            log.info("Served from {}{}", isInCache ? "cache: " : "disk: ", uri);
        } catch (Exception e) {
            log.error("Error serving file: {}", uri, e);
            new InternalErrorHandler().handle(httpContext);
        }
    }
    
    private void writeNotFound(HttpContext httpContext, Map<String, String> headers) throws IOException {
        StaticFileCache fileCache = StaticFileCacheFactory.getInstance();
        File errorPage = new File(errorPagePath);
        if (errorPage.exists()) {
            try {
                String contentType = MimeTypeUtil.getMimeType(errorPage.getName());
                if (contentType == null) {
                    contentType = "text/html";
                }
                CachedFile cf = fileCache.get(errorPage);
                HttpResponse httpResponse = httpContext.getResponse().toBuilder()
                        .httpVersion(HttpVersion.HTTP_1_1)
                        .httpStatus(HttpStatus.NOT_FOUND)
                        .contentType(contentType)
                        .headers(headers)
                        .body(cf.getContent())
                        .build();
                httpContext.setResponse(httpResponse);
                return;
            } catch (Exception ignore) {
                // fallback to default 404
            }
        }
        HttpResponse httpResponse = httpContext.getResponse().toBuilder()
                .httpVersion(HttpVersion.HTTP_1_1)
                .httpStatus(HttpStatus.NOT_FOUND)
                .contentType("text/html")
                .headers(headers)
                .body(DEFAULT_404_HTML.getBytes())
                .build();
        httpContext.setResponse(httpResponse);
    }

    private String generateETag(CachedFile cf) {
        long lastModified = cf.getLastModified();
        int length = cf.getContent() != null ? cf.getContent().length : 0;
        return "\"" + lastModified + "-" + length + "\"";
    }

    private boolean matchesEtag(String ifNoneMatch, String etag) {
        String candidate = ifNoneMatch.trim();
        if ("*".equals(candidate)) {
            return true;
        }
        String[] parts = candidate.split(",");
        for (String part : parts) {
            if (etag.equals(part.trim())) {
                return true;
            }
        }
        return false;
    }
}
