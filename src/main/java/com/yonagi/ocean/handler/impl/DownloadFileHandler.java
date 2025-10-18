package com.yonagi.ocean.handler.impl;

import com.yonagi.ocean.cache.CachedFile;
import com.yonagi.ocean.cache.StaticFileCache;
import com.yonagi.ocean.cache.StaticFileCacheFactory;
import com.yonagi.ocean.core.context.HttpContext;
import com.yonagi.ocean.core.protocol.HttpRequest;
import com.yonagi.ocean.core.protocol.HttpResponse;
import com.yonagi.ocean.core.protocol.enums.HttpStatus;
import com.yonagi.ocean.core.protocol.enums.HttpVersion;
import com.yonagi.ocean.handler.RequestHandler;
import com.yonagi.ocean.utils.LocalConfigLoader;
import com.yonagi.ocean.utils.MimeTypeUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

/**
 * @author Yonagi
 * @version 1.0
 * @program Ocean
 * @description
 * @date 2025/10/11 16:22
 */
public class DownloadFileHandler implements RequestHandler {

    private static final String DOWNLOAD_PATH = LocalConfigLoader.getProperty("server.download.dir", "./uploads");
    private static final Logger log = LoggerFactory.getLogger(DownloadFileHandler.class);
    private static final String errorPagePath = LocalConfigLoader.getProperty("server.not_found_page", "./www/404.html");

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

    @Override
    public void handle(HttpContext httpContext) throws IOException {
        HttpRequest request = httpContext.getRequest();
        OutputStream output = httpContext.getOutput();
        String requestUri = request.getUri();
        String fileName = extractFileNameSafely(requestUri);

        File file = Paths.get(DOWNLOAD_PATH, fileName).toFile();
        if (!file.exists() || file.isDirectory()) {
            log.warn("File {} is not exists or is a directory", fileName);
            writeNotFound(httpContext);
            return;
        }

        try {
            Map<String, String> headers = (Map<String, String>) request.getAttribute("HstsHeaders");
            headers.put("Content-Length", String.valueOf(file.length()));
            headers.put("Content-Disposition", "attachment; filename=\"" + file.getName() + "\"");

            HttpResponse response = httpContext.getResponse().toBuilder()
                    .httpVersion(request.getHttpVersion())
                    .httpStatus(HttpStatus.OK)
                    .headers(headers)
                    .contentType(MimeTypeUtil.getMimeType(fileName))
                    .build();
            response.writeStreaming(request, output, httpContext.isKeepalive(), file.length());
            output.flush();

            try (FileInputStream fileInput = new FileInputStream(file)) {
                byte[] buffer = new byte[4096];
                int bytesRead;
                while ((bytesRead = fileInput.read(buffer)) != -1) {
                    output.write(buffer, 0, bytesRead);
                }
                output.flush();
            } catch (Exception e) {
                throw new IOException("Failed to stream file data", e);
            } finally {
                httpContext.commitResponse();
            }
        } catch (IOException e) {
            log.error("Failed to stream file data: {}", e.getMessage(), e);
            new InternalErrorHandler().handle(httpContext);
        }
    }

    private String extractFileNameSafely(String uri) {
        if (uri == null || uri.length() == 0) {
            return "";
        }
        int queryIndex = uri.indexOf('?');
        if (queryIndex != -1) {
            uri = uri.substring(0, queryIndex);
        }
        int lastSlashIndex = uri.lastIndexOf('/');
        if (lastSlashIndex == -1 || lastSlashIndex == uri.length() - 1) {
            return "";
        }
        return uri.substring(lastSlashIndex + 1);
    }

    private void writeNotFound(HttpContext httpContext) throws IOException {
        HttpRequest request = httpContext.getRequest();

        StaticFileCache fileCache = StaticFileCacheFactory.getInstance();
        File errorPage = new File(errorPagePath);
        Map<String, String> headers = new HashMap<>();
        if ((Boolean) request.getAttribute("isSsl")) {
            StringBuilder hstsValue = new StringBuilder();
            long maxAge = Long.parseLong(LocalConfigLoader.getProperty("server.ssl.hsts.max_age", "31536000"));
            hstsValue.append("max-age=").append(maxAge);
            boolean enabledIncludeSubdomains = Boolean.parseBoolean(LocalConfigLoader.getProperty("server.ssl.hsts.enabled_include_subdomains", "false"));
            boolean enabledPreload = Boolean.parseBoolean(LocalConfigLoader.getProperty("server.ssl.hsts.enabled_preload", "false"));
            if (enabledIncludeSubdomains) {
                hstsValue.append("; includeSubDomains");
            }
            if (enabledPreload && enabledIncludeSubdomains && maxAge >= 31536000) {
                hstsValue.append("; preload");
            }
            headers.put("Strict-Transport-Security", hstsValue.toString());
        }
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
}
