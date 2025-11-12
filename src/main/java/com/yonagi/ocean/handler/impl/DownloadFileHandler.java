package com.yonagi.ocean.handler.impl;

import com.yonagi.ocean.core.ErrorPageRender;
import com.yonagi.ocean.core.context.HttpContext;
import com.yonagi.ocean.core.protocol.HttpRequest;
import com.yonagi.ocean.core.protocol.HttpResponse;
import com.yonagi.ocean.core.protocol.enums.ContentType;
import com.yonagi.ocean.core.protocol.enums.HttpStatus;
import com.yonagi.ocean.core.protocol.enums.HttpVersion;
import com.yonagi.ocean.handler.RequestHandler;
import com.yonagi.ocean.utils.LocalConfigLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Paths;
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

    @Override
    public void handle(HttpContext httpContext) throws IOException {
        HttpRequest request = httpContext.getRequest();
        OutputStream output = httpContext.getOutput();
        String requestUri = request.getUri();
        String fileName = extractFileNameSafely(requestUri);

        File file = Paths.get(DOWNLOAD_PATH, fileName).toFile();
        if (!file.exists() || file.isDirectory()) {
            log.warn("[{}] File {} is not exists or is a directory", httpContext.getTraceId(), fileName);
            writeNotFound(httpContext);
            return;
        }

        try {
            Map<String, String> headers = request.getAttribute().getHstsHeaders();
            headers.put("Content-Length", String.valueOf(file.length()));
            headers.put("Content-Disposition", "attachment; filename=\"" + file.getName() + "\"");

            HttpResponse response = httpContext.getResponse().toBuilder()
                    .httpVersion(request.getHttpVersion())
                    .httpStatus(HttpStatus.OK)
                    .headers(headers)
                    .contentType(ContentType.fromName(fileName))
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
            log.error("[{}] Failed to stream file data: {}", httpContext.getTraceId(), e.getMessage(), e);
            HttpResponse errorResponse = httpContext.getResponse().toBuilder()
                    .httpVersion(request.getHttpVersion())
                    .httpStatus(HttpStatus.INTERNAL_SERVER_ERROR)
                    .contentType(ContentType.TEXT_HTML)
                    .build();
            httpContext.setResponse(errorResponse);
            ErrorPageRender.render(httpContext);
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

    private void writeNotFound(HttpContext httpContext) {
        HttpRequest request = httpContext.getRequest();

        Map<String, String> headers = request.getAttribute().getHstsHeaders();
        HttpResponse httpResponse = httpContext.getResponse().toBuilder()
                .httpVersion(HttpVersion.HTTP_1_1)
                .httpStatus(HttpStatus.NOT_FOUND)
                .contentType(ContentType.TEXT_HTML)
                .headers(headers)
                .build();
        httpContext.setResponse(httpResponse);
        ErrorPageRender.render(httpContext);
    }
}
