package com.yonagi.ocean.handler.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yonagi.ocean.core.protocol.HttpRequest;
import com.yonagi.ocean.core.protocol.HttpResponse;
import com.yonagi.ocean.core.protocol.enums.HttpMethod;
import com.yonagi.ocean.core.protocol.enums.HttpStatus;
import com.yonagi.ocean.handler.RequestHandler;
import com.yonagi.ocean.utils.LocalConfigLoader;
import com.yonagi.ocean.utils.UUIDUtil;
import org.apache.commons.fileupload.FileItem;
import org.apache.commons.fileupload.RequestContext;
import org.apache.commons.fileupload.disk.DiskFileItemFactory;
import org.apache.commons.fileupload.servlet.ServletFileUpload;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author Yonagi
 * @version 1.0
 * @program Ocean
 * @description
 * @date 2025/10/11 09:45
 */
public class UploadFileHandler implements RequestHandler {

    private static final String UPLOAD_DIR = LocalConfigLoader.getProperty("server.upload.dir", "./uploads");
    private static final long MAX_FILE_SIZE = (long) Integer.parseInt(LocalConfigLoader.getProperty("server.upload.max_file_size_mb", "100")) * 1024 * 1024;
    private static final int MEMORY_THRESHOLD = Integer.parseInt(LocalConfigLoader.getProperty("server.upload.memory_threshold_mb", "100")) * 1024 * 1024;
    private static final Logger log = LoggerFactory.getLogger(UploadFileHandler.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public void handle(HttpRequest request, OutputStream output) throws IOException {
        handle(request, output, true);
    }

    @Override
    public void handle(HttpRequest request, OutputStream output, boolean keepAlive) throws IOException {
        if (request.getMethod() != HttpMethod.POST) {
            new MethodNotAllowHandler().handle(request, output, keepAlive);
            return;
        }
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

        final InputStream bodyStream = request.getRawBodyInputStream();
        if (bodyStream == null) {
            HttpResponse response = new HttpResponse.Builder()
                    .httpVersion(request.getHttpVersion())
                    .httpStatus(HttpStatus.BAD_REQUEST)
                    .headers(headers)
                    .body("Request body stream is missing".getBytes())
                    .contentType("text/plain; charset=utf-8")
                    .build();
            response.write(request, output, keepAlive);
            output.flush();
            return;
        }

        RequestContext requestContext = new RequestContext() {
            @Override
            public String getCharacterEncoding() {
                return "ISO-8859-1";
            }

            @Override
            public String getContentType() {
                return request.getHeaders().get("content-type");
            }

            @Override
            public int getContentLength() {
                String lengthString = request.getHeaders().get("content-length");
                if (lengthString != null) {
                    try {
                        long length = Long.parseLong(lengthString);
                        if (length > Integer.MAX_VALUE) {
                            return -1;
                        }
                        return (int) length;
                    } catch (NumberFormatException e) {
                        return -1;
                    }
                }
                return -1;
            }

            @Override
            public InputStream getInputStream() throws IOException {
                return bodyStream;
            }
        };
        if (!ServletFileUpload.isMultipartContent(requestContext)) {
            HttpResponse response = new HttpResponse.Builder()
                    .httpVersion(request.getHttpVersion())
                    .httpStatus(HttpStatus.BAD_REQUEST)
                    .contentType("text/plain; charset=utf-8")
                    .headers(headers)
                    .body("Invalid request body".getBytes())
                    .build();
            response.write(request, output, keepAlive);
            output.flush();
            return;
        }
        DiskFileItemFactory factory = new DiskFileItemFactory();
        factory.setSizeThreshold(MEMORY_THRESHOLD);
        factory.setRepository(new File(System.getProperty("java.io.tmpdir")));
        ServletFileUpload upload = new ServletFileUpload(factory);
        upload.setSizeMax(MAX_FILE_SIZE);

        try {
            List<FileItem> items = upload.parseRequest(requestContext);
            if (items == null || items.isEmpty()) {
                HttpResponse response = new HttpResponse.Builder()
                        .httpVersion(request.getHttpVersion())
                        .httpStatus(HttpStatus.BAD_REQUEST)
                        .contentType("text/plain; charset=utf-8")
                        .headers(headers)
                        .body("Invalid request body".getBytes())
                        .build();
                response.write(request, output, keepAlive);
                output.flush();
                return;
            }

            List<Map<String, Object>> uploadedFiles = new ArrayList<>();
            for (FileItem item : items) {
                if (!item.isFormField()) {
                    String extension = extractExtension(item.getName());
                    String candidate = UUIDUtil.getShortUUIDWithPrefix("file_");
                    String fileName = candidate + extension;
                    if (Paths.get(UPLOAD_DIR, fileName).toFile().exists()) {
                        candidate = UUIDUtil.getTimeBasedShortUUIDWithPrefix("file_");
                        fileName = candidate + extension;
                        if (Paths.get(UPLOAD_DIR, fileName).toFile().exists()) {
                            new InternalErrorHandler().handle(request, output, keepAlive);
                        }
                    }
                    File storeFile = Paths.get(UPLOAD_DIR, fileName).toFile();

                    storeFile.getParentFile().mkdirs();

                    item.write(storeFile);

                    Map<String, Object> fileInfo = new HashMap<>();
                    fileInfo.put("fileName", fileName);
                    fileInfo.put("size", item.getSize());
                    fileInfo.put("contentType", item.getContentType());
                    uploadedFiles.add(fileInfo);
                } else {

                }
            }

            String responseBody = objectMapper.writeValueAsString(Map.of(
                    "status", "success",
                    "totalUploaded", uploadedFiles.size(),
                    "files", uploadedFiles
            ));

            HttpResponse response = new HttpResponse.Builder()
                    .httpVersion(request.getHttpVersion())
                    .httpStatus(HttpStatus.CREATED)
                    .headers(headers)
                    .contentType("application/json")
                    .body(responseBody.getBytes(StandardCharsets.UTF_8))
                    .build();
            response.write(request, output, keepAlive);
            output.flush();
            items.forEach(FileItem::delete);
        } catch (Exception e) {
            log.error("File upload failed: {}", e.getMessage(), e);
            new InternalErrorHandler().handle(request, output, keepAlive);
        }
    }

    private String sanitizeFilename(String fileName) {
        if (fileName == null || fileName.isEmpty()) {
            return "unknown_file";
        }
        return Paths.get(fileName).getFileName().toString();
    }

    private static String extractExtension(String originalFilename) {
        if (originalFilename == null) {
            return "";
        }
        int lastDot = originalFilename.lastIndexOf('.');
        if (lastDot <= 0 || lastDot == originalFilename.length() - 1) {
            return "";
        }
        return originalFilename.substring(lastDot);
    }
}
