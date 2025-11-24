package com.yonagi.ocean.handler.impl;

import com.yonagi.ocean.cache.*;
import com.yonagi.ocean.core.ErrorPageRender;
import com.yonagi.ocean.core.context.HttpContext;
import com.yonagi.ocean.core.gzip.GzipEncoder;
import com.yonagi.ocean.core.gzip.GzipEncoderManager;
import com.yonagi.ocean.core.protocol.*;
import com.yonagi.ocean.core.protocol.enums.ContentType;
import com.yonagi.ocean.core.protocol.enums.HttpStatus;
import com.yonagi.ocean.core.protocol.enums.HttpVersion;
import com.yonagi.ocean.handler.RequestHandler;
import com.yonagi.ocean.admin.metrics.MetricsRegistry;
import com.yonagi.ocean.utils.LocalConfigLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.Arrays;
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

    private final String webRoot;

    public StaticFileHandler(String webRoot) {
        this.webRoot = webRoot;
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
        Map<String, String> headers = request.getAttribute().getHstsHeaders();

        File file = new File(webRoot, uri);
        if (file.isDirectory()) {
            if (!uri.endsWith("/")) {
                uri = uri + "/";
            }
            String indexFileUri = uri + "index.html";
            File indexFile = new File(webRoot, indexFileUri);

            if (indexFile.exists() && !indexFile.isDirectory()) {
                uri = indexFileUri;
                file = indexFile;
            } else {
                writeNotFound(httpContext, headers);
                return;
            }
        }
        if (!file.exists()) {
            writeNotFound(httpContext, headers);
            return;
        }
        if (!file.getCanonicalPath().startsWith(new File(webRoot).getCanonicalPath())) {
            writeNotFound(httpContext, headers);
            log.warn("[{}] Attempted directory traversal attack: {}", httpContext.getTraceId(), uri);
            return;
        }

        ContentType contentType = ContentType.fromName(file.getName());

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
                MetricsRegistry metricsRegistry = httpContext.getConnectionContext().getServerContext().getMetricsRegistry();
                metricsRegistry.getHttpCacheHitCounter().increment();
                HttpResponse notModified = httpContext.getResponse().toBuilder()
                        .httpVersion(request.getHttpVersion())
                        .httpStatus(HttpStatus.NOT_MODIFIED)
                        .contentType(contentType)
                        .headers(headers)
                        .build();
                httpContext.setResponse(notModified);
                log.info("[{}] Respond 304 Not Modified for {}", httpContext.getTraceId(), uri);
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
            if (isInCache) {
                MetricsRegistry metricsRegistry = httpContext.getConnectionContext().getServerContext().getMetricsRegistry();
                metricsRegistry.getCacheHitCounter().increment();
            }
            log.info("[{}] Served from {}{}", httpContext.getTraceId(), isInCache ? "cache: " : "disk: ", uri);
        } catch (Exception e) {
            log.error("[{}] Error serving file: {}", httpContext.getTraceId(), uri, e);
            HttpResponse errorResponse = httpContext.getResponse().toBuilder()
                    .httpVersion(request.getHttpVersion())
                    .httpStatus(HttpStatus.INTERNAL_SERVER_ERROR)
                    .contentType(ContentType.TEXT_HTML)
                    .headers(headers)
                    .build();
            httpContext.setResponse(errorResponse);
            ErrorPageRender.render(httpContext);
        }
    }
    
    private void writeNotFound(HttpContext httpContext, Map<String, String> headers) {
        MetricsRegistry metricsRegistry = httpContext.getConnectionContext().getServerContext().getMetricsRegistry();
        metricsRegistry.getNotFoundCounter().increment();
        HttpResponse httpResponse = httpContext.getResponse().toBuilder()
                .httpVersion(HttpVersion.HTTP_1_1)
                .httpStatus(HttpStatus.NOT_FOUND)
                .contentType(ContentType.TEXT_HTML)
                .headers(headers)
                .build();
        httpContext.setResponse(httpResponse);
        ErrorPageRender.render(httpContext);
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
