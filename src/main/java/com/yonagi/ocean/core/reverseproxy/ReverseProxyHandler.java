package com.yonagi.ocean.core.reverseproxy;

import com.yonagi.ocean.core.ErrorPageRender;
import com.yonagi.ocean.core.configuration.ReverseProxyConfig;
import com.yonagi.ocean.core.context.HttpContext;
import com.yonagi.ocean.core.protocol.HttpRequest;
import com.yonagi.ocean.core.protocol.HttpResponse;
import com.yonagi.ocean.core.protocol.enums.ContentType;
import com.yonagi.ocean.core.protocol.enums.HttpMethod;
import com.yonagi.ocean.core.protocol.enums.HttpStatus;
import com.yonagi.ocean.handler.RequestHandler;
import org.apache.commons.fileupload.FileItem;
import org.apache.commons.fileupload.RequestContext;
import org.apache.commons.fileupload.disk.DiskFileItemFactory;
import org.apache.commons.fileupload.servlet.ServletFileUpload;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.ConnectException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * @author Yonagi
 * @version 1.0
 * @program Ocean
 * @description
 * @date 2025/11/09 10:33
 */
public class ReverseProxyHandler implements RequestHandler {

    private static final Logger log = LoggerFactory.getLogger(ReverseProxyHandler.class);
    private static final Set<String> HOP_BY_HOP_HEADERS = Set.of(
            "connection", "keep-alive", "proxy-authenticate", "proxy-authorization",
            "te", "trailers", "transfer-encoding", "upgrade", "host", "content-length"
    );

    private ReverseProxyConfig proxyConfig;
    private final HttpClient httpClient;

    public ReverseProxyHandler(ReverseProxyConfig proxyConfig) {
        this.proxyConfig = proxyConfig;
        this.httpClient = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NEVER)
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    @Override
    public void handle(HttpContext httpContext) throws IOException {
        String traceId = httpContext.getTraceId();
        HttpRequest request = httpContext.getRequest();
        if (proxyConfig == null) {
            HttpResponse errorResponse = httpContext.getResponse().toBuilder()
                    .httpVersion(request.getHttpVersion())
                    .httpStatus(HttpStatus.INTERNAL_SERVER_ERROR)
                    .contentType(ContentType.TEXT_HTML)
                    .build();
            httpContext.setResponse(errorResponse);
            ErrorPageRender.render(httpContext);
            log.error("[{}] Reverse proxy configuration is missing", traceId);
            return;
        }

        String configId = proxyConfig.getId();
        URI upstreamUri = buildUpstreamUri(request, proxyConfig);
        log.debug("[{}] {} Forwarding request {} to upstream: {}", traceId, configId, request.getUri(), upstreamUri);
        try {
            java.net.http.HttpRequest.Builder requestBuilder = java.net.http.HttpRequest.newBuilder()
                    .uri(upstreamUri)
                    .timeout(Duration.ofMillis(proxyConfig.getTimeout()));
            copyRequestHeaders(request, requestBuilder, proxyConfig);
            setRequestBody(request, requestBuilder);
            java.net.http.HttpRequest upstreamRequest = requestBuilder.build();
            java.net.http.HttpResponse<byte[]> upstreamResponse = httpClient.send(
                    upstreamRequest,
                    java.net.http.HttpResponse.BodyHandlers.ofByteArray()
            );
            forwardResponse(httpContext, upstreamResponse);
        } catch (ConnectException e) {
            HttpResponse errorResponse = httpContext.getResponse().toBuilder()
                    .httpVersion(request.getHttpVersion())
                    .httpStatus(HttpStatus.SERVICE_UNAVAILABLE)
                    .contentType(ContentType.TEXT_HTML)
                    .build();
            httpContext.setResponse(errorResponse);
            ErrorPageRender.render(httpContext);
            log.error("[{}] {} Service Unavailable when proxying request to upstream {}: {}", traceId, configId, upstreamUri, e.getMessage(), e);
        } catch (HttpTimeoutException e) {
            HttpResponse errorResponse = httpContext.getResponse().toBuilder()
                    .httpVersion(request.getHttpVersion())
                    .httpStatus(HttpStatus.GATEWAY_TIMEOUT)
                    .contentType(ContentType.TEXT_HTML)
                    .build();
            httpContext.setResponse(errorResponse);
            ErrorPageRender.render(httpContext);
            log.error("[{}] {} Gateway Timeout when proxying request to upstream {}: {}", traceId, configId, upstreamUri, e.getMessage(), e);
        } catch (Exception e) {
            HttpResponse errorResponse = httpContext.getResponse().toBuilder()
                    .httpVersion(request.getHttpVersion())
                    .httpStatus(HttpStatus.BAD_GATEWAY)
                    .contentType(ContentType.TEXT_HTML)
                    .build();
            httpContext.setResponse(errorResponse);
            ErrorPageRender.render(httpContext);
            log.error("[{}] [{}] Error proxying request to upstream {}: {}", traceId, configId, upstreamUri, e.getMessage(), e);
        }
    }

    private URI buildUpstreamUri(HttpRequest request, ReverseProxyConfig proxyConfig) {
        String path = request.getUri();
        String targetBase = proxyConfig.getTargetUrl();
        String targetPath = path;

        if (proxyConfig.isStripPrefix()) {
            String prefix = getPrefixToStrip(proxyConfig.getPath());
            if (path.startsWith(prefix)) {
                targetPath = path.substring(prefix.length());
                if (!targetPath.startsWith("/") && !targetPath.isEmpty()) {
                    targetPath = "/" + targetPath;
                }
            } else {
                log.warn("PathMatcher matched, but stripPrefix failed for path: {} and config path: {}", path, proxyConfig.getPath());
            }
        }
        String finalUri = targetBase.replaceAll("/+$", "") + targetPath;
        try {
            return new URI(finalUri);
        } catch (Exception e) {
            log.error("Invalid URI constructed: {}", finalUri, e);
            throw new RuntimeException(e);
        }
    }

    private String getPrefixToStrip(String configPath) {
        int wildcardIndex = Math.min(
                configPath.indexOf('*') != -1 ? configPath.indexOf('*') : Integer.MAX_VALUE,
                configPath.indexOf('?') != -1 ? configPath.indexOf('?') : Integer.MAX_VALUE
        );

        String prefix = wildcardIndex == Integer.MAX_VALUE ? configPath : configPath.substring(0, wildcardIndex);
        return prefix.replaceAll("/+$", "");
    }

    private void copyRequestHeaders(HttpRequest request, java.net.http.HttpRequest.Builder requestBuilder, ReverseProxyConfig proxyConfig) {
        request.getHeaders().forEach((name, value) -> {
            if (!HOP_BY_HOP_HEADERS.contains(name.toLowerCase())) {
                requestBuilder.header(name, value);
            }
        });
        requestBuilder.header("X-Forwarded-For", (String) request.getAttribute("clientIp"));
        proxyConfig.getAddHeaders().forEach((name, value) -> {
            if (!HOP_BY_HOP_HEADERS.contains(name.toLowerCase())) {
                requestBuilder.header(name, value);
            }
        });
    }

    private void setRequestBody(HttpRequest request, java.net.http.HttpRequest.Builder requestBuilder) throws IOException {
        java.net.http.HttpRequest.BodyPublisher publisher;
        String contentType = request.getHeaders().get("content-type");

//        if (contentType != null && contentType.startsWith("multipart/form-data")) {
//            // 处理 multipart/form-data
//            publisher = createMultipartBodyPublisher(request, contentType);
//        } else {
//            // 处理普通请求体
//            byte[] body = request.getBody();
//            if (body != null && body.length > 0) {
//                publisher = java.net.http.HttpRequest.BodyPublishers.ofByteArray(body);
//            } else {
//                publisher = java.net.http.HttpRequest.BodyPublishers.noBody();
//            }
//        }
        if (
                request.getMethod() == HttpMethod.POST ||
                request.getMethod() == HttpMethod.PUT ||
                request.getMethod() == HttpMethod.PATCH
        ) {
            byte[] body = request.getBody();

            if (body == null || body.length == 0) {
                if (request.getRawBodyInputStream() != null) {
                    // todo: 修复form-data上传阻塞的bug
                    try (InputStream is = request.getRawBodyInputStream()) {
                        body = is.readAllBytes();
                    } catch (IOException e) {
                        log.error("Failed to read raw body stream into memory.", e);
                        body = null;
                    }
                    request = request.toBuilder()
                            .body(body)
                            .build();
                    if (body != null && body.length > 0) {
                        log.debug("Successfully loaded raw body stream ({} bytes) into memory.", body.length);
                    }
                }
            }

            if (body != null && body.length > 0) {
                publisher = java.net.http.HttpRequest.BodyPublishers.ofByteArray(body);
            } else {
                publisher = java.net.http.HttpRequest.BodyPublishers.noBody();
            }
        } else {
            publisher = java.net.http.HttpRequest.BodyPublishers.noBody();
        }

        switch (request.getMethod().name()) {
            case "GET" -> requestBuilder.GET();
            case "POST" -> requestBuilder.POST(publisher);
            case "PUT" -> requestBuilder.PUT(publisher);
            case "DELETE" -> requestBuilder.method("DELETE", publisher);
            case "PATCH" -> requestBuilder.method("PATCH", publisher);
            case "HEAD" -> requestBuilder.method("HEAD", publisher);
            case "OPTIONS" -> requestBuilder.method("OPTIONS", publisher);
            default -> requestBuilder.method(request.getMethod().name(), publisher);
        }
    }

    private java.net.http.HttpRequest.BodyPublisher createMultipartBodyPublisher(HttpRequest request, String contentType) throws IOException {
        DiskFileItemFactory factory = new DiskFileItemFactory();
        factory.setSizeThreshold(1024 * 1024);
        factory.setRepository(new File(System.getProperty("java.io.tmpdir")));
        ServletFileUpload upload = new ServletFileUpload(factory);

        try {
            RequestContext requestContext = new RequestContext() {
                @Override
                public String getCharacterEncoding() {
                    return "UTF-8";
                }

                @Override
                public String getContentType() {
                    return contentType;
                }

                @Override
                public int getContentLength() {
                    String lengthString = request.getHeaders().get("content-length");
                    if (lengthString != null) {
                        try {
                            long length = Long.parseLong(lengthString);

                            if (length >= 0 && length <= Integer.MAX_VALUE) {
                                return (int) length;
                            }
                            return -1;
                        } catch (NumberFormatException e) {
                            return -1;
                        }
                    }
                    byte[] body = request.getBody();
                    if (body != null && body.length <= Integer.MAX_VALUE) {
                        return body.length;
                    }
                    return -1;
                }

                @Override
                public InputStream getInputStream() throws IOException {
                    return request.getRawBodyInputStream();
                }
            };

            List<FileItem> items = upload.parseRequest(requestContext);
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            String boundary = getBoundary(contentType);

            for (FileItem item : items) {
                outputStream.write(("--" + boundary + "\r\n").getBytes("UTF-8"));
                if (item.isFormField()) {
                    // 普通字段
                    String fieldHeader = String.format("Content-Disposition: form-data; name=\"%s\"\r\n\r\n",
                            item.getFieldName());
                    outputStream.write(fieldHeader.getBytes("UTF-8"));
                    outputStream.write(item.getString("UTF-8").getBytes("UTF-8"));
                } else {
                    // 文件字段
                    String fileHeader = String.format("Content-Disposition: form-data; name=\"%s\"; filename=\"%s\"\r\n",
                            item.getFieldName(), item.getName());
                    outputStream.write(fileHeader.getBytes("UTF-8"));
                    outputStream.write(("Content-Type: " + item.getContentType() + "\r\n\r\n").getBytes("UTF-8"));
                    outputStream.write(item.get());
                }
                outputStream.write("\r\n".getBytes("UTF-8"));
            }

            outputStream.write(("--" + boundary + "--\r\n").getBytes("UTF-8"));
            byte[] bodyBytes = outputStream.toByteArray();

            log.debug("Rebuilt multipart body, size: {} bytes", bodyBytes.length);
            return java.net.http.HttpRequest.BodyPublishers.ofByteArray(bodyBytes);
        } catch (Exception e) {
            log.error("Failed to process multipart/form-data", e);
            throw new IOException("Failed to process multipart/form-data", e);
        }
    }

    private void forwardResponse(HttpContext httpContext, java.net.http.HttpResponse<byte[]> upstreamResponse) {
        if (upstreamResponse.statusCode() >= 400 && upstreamResponse.statusCode() < 600) {
            log.warn("[{}] Upstream returned error status code: {}", httpContext.getTraceId(), upstreamResponse.statusCode());
            HttpResponse errorResponse = httpContext.getResponse().toBuilder()
                    .httpVersion(httpContext.getRequest().getHttpVersion())
                    .httpStatus(HttpStatus.fromCode(upstreamResponse.statusCode()))
                    .contentType(ContentType.TEXT_HTML)
                    .build();
            httpContext.setResponse(errorResponse);
            ErrorPageRender.render(httpContext);
            return;
        }
        HttpResponse clientResponse = httpContext.getResponse();
        HttpResponse.Builder responseBuilder = clientResponse.toBuilder()
                .httpVersion(clientResponse.getHttpVersion())
                .httpStatus(HttpStatus.fromCode(upstreamResponse.statusCode()))
                .body(upstreamResponse.body());
        upstreamResponse.headers().map().forEach((name, values) -> {
            if (!HOP_BY_HOP_HEADERS.contains(name.toLowerCase())) {
                if (values != null && values.size() == 1) {
                    responseBuilder.headers(Map.of(name, values.getFirst()));
                } else if (values != null && values.size() > 1) {
                    responseBuilder.headers(Map.of(name, String.join(";", values)));
                }
            }
        });
        httpContext.setResponse(responseBuilder.build());
    }

    private String getBoundary(String contentType) {
        String[] parts = contentType.split("boundary=");
        if (parts.length > 1) {
            String boundary = parts[1].trim();
            if (boundary.startsWith("\"") && boundary.endsWith("\"")) {
                boundary = boundary.substring(1, boundary.length() - 1);
            }
            int semicolonIndex = boundary.indexOf(';');
            if (semicolonIndex > 0) {
                boundary = boundary.substring(0, semicolonIndex);
            }
            return boundary;
        }
        return "----WebKitFormBoundary";
    }
}