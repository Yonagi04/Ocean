package com.yonagi.ocean.core.reverseproxy;

import com.yonagi.ocean.core.ErrorPageRender;
import com.yonagi.ocean.core.loadbalance.HealthChecker;
import com.yonagi.ocean.core.loadbalance.LoadBalancer;
import com.yonagi.ocean.core.loadbalance.LoadBalancerFactory;
import com.yonagi.ocean.core.loadbalance.config.Upstream;
import com.yonagi.ocean.core.loadbalance.config.enums.Strategy;
import com.yonagi.ocean.core.reverseproxy.config.ReverseProxyConfig;
import com.yonagi.ocean.core.context.HttpContext;
import com.yonagi.ocean.core.protocol.HttpRequest;
import com.yonagi.ocean.core.protocol.HttpResponse;
import com.yonagi.ocean.core.protocol.enums.ContentType;
import com.yonagi.ocean.core.protocol.enums.HttpStatus;
import com.yonagi.ocean.handler.RequestHandler;
import com.yonagi.ocean.utils.LocalConfigLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
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

    private final ReverseProxyConfig proxyConfig;
    private final HttpClient httpClient;
    private final LoadBalancer loadBalancer;
    private final HealthChecker healthChecker;

    public ReverseProxyHandler(ReverseProxyConfig proxyConfig) {
        this.proxyConfig = proxyConfig;
        this.httpClient = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NEVER)
                .connectTimeout(Duration.ofSeconds(5))
                .build();
        boolean enableLb = Boolean.parseBoolean(LocalConfigLoader.getProperty("server.load_balance.enabled", "false"));
        if (enableLb) {
            this.loadBalancer = LoadBalancerFactory.createLoadBalancer(proxyConfig.getLbConfig());
        } else {
            this.loadBalancer = LoadBalancerFactory.createLoadBalancer(proxyConfig.getLbConfig(), Strategy.NONE);
        }
        this.healthChecker = new HealthChecker(proxyConfig.getLbConfig());
        this.healthChecker.start();
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
            log.error("[{}] Reverse proxy config is missing", traceId);
            return;
        }

        String configId = proxyConfig.getId();
        URI upstreamUri;
        try {
            upstreamUri = buildUpstreamUri(request, proxyConfig);
        } catch (ConnectException e) {
            HttpResponse errorResponse = httpContext.getResponse().toBuilder()
                    .httpVersion(request.getHttpVersion())
                    .httpStatus(HttpStatus.SERVICE_UNAVAILABLE)
                    .contentType(ContentType.TEXT_HTML)
                    .build();
            httpContext.setResponse(errorResponse);
            ErrorPageRender.render(httpContext);
            log.error("[{}] {} Service Unavailable: {}", traceId, configId, e.getMessage(), e);
            return;
        } catch (Exception e) {
            HttpResponse errorResponse = httpContext.getResponse().toBuilder()
                    .httpVersion(request.getHttpVersion())
                    .httpStatus(HttpStatus.BAD_GATEWAY)
                    .contentType(ContentType.TEXT_HTML)
                    .build();
            httpContext.setResponse(errorResponse);
            ErrorPageRender.render(httpContext);
            log.error("[{}] {} Building Upstream uri meets with exception: {}", traceId, configId, e.getMessage(), e);
            return;
        }

        log.info("[{}] {} Forwarding {} request {} to upstream: {}", traceId, configId, request.getMethod(), request.getUri(), upstreamUri);
        
        // 在设置请求体之前记录请求体状态
        byte[] bodyBeforeProxy = request.getBody();
        String contentLength = request.getHeaders().get("content-length");
        log.debug("[{}] Request body status before proxying: body={}, bodyLength={}, contentLength={}",
                traceId, bodyBeforeProxy != null ? "exists" : "null", 
                bodyBeforeProxy != null ? bodyBeforeProxy.length : 0, contentLength);
        
        try {
            java.net.http.HttpRequest.Builder requestBuilder = java.net.http.HttpRequest.newBuilder()
                    .uri(upstreamUri)
                    .timeout(Duration.ofMillis(proxyConfig.getTimeout()));
            copyRequestHeaders(request, requestBuilder, proxyConfig, traceId);
            setRequestBody(request, requestBuilder, traceId);
            java.net.http.HttpRequest upstreamRequest = requestBuilder.build();
            
            // 验证构建的请求
            log.debug("[{}] Built upstream request: method={}, URI={}", traceId, upstreamRequest.method(), upstreamRequest.uri());
            upstreamRequest.headers().map().forEach((name, values) -> {
                if (name.equalsIgnoreCase("content-length")) {
                    log.warn("[{}] WARNING: Content-Length header found in upstream request: {} = {}", traceId, name, values);
                } else {
                    log.debug("[{}] Upstream request header: {} = {}", traceId, name, values);
                }
            });
            
            // 记录 BodyPublisher 信息（用于调试）
            if (log.isDebugEnabled() && upstreamRequest.bodyPublisher().isPresent()) {
                log.debug("[{}] BodyPublisher is present for upstream request", traceId);
                int requestBodyLength = request.getBody() != null ? request.getBody().length : 0;
                log.debug("[{}] Request body length that should be sent: {} bytes", traceId, requestBodyLength);
            } else {
                log.debug("[{}] No BodyPublisher for upstream request", traceId);
            }

            java.net.http.HttpResponse<byte[]> upstreamResponse;
            try {
                upstreamResponse = httpClient.send(
                        upstreamRequest,
                        java.net.http.HttpResponse.BodyHandlers.ofByteArray()
                );
                log.debug("[{}] Received response from upstream: status={}, headers={}",
                        traceId, upstreamResponse.statusCode(), upstreamResponse.headers().map().keySet());
            } catch (java.io.IOException ioException) {
                // 检查是否是 Content-Length 相关的错误
                String ioErrorMessage = ioException.getMessage();
                if (ioErrorMessage != null && ioErrorMessage.contains("content-length")) {
                    Throwable cause = ioException.getCause();
                    if (cause != null) {
                        log.error("[{}] Caused by: {}", traceId, cause.getClass().getName());
                        log.error("[{}] Cause message: {}", traceId, cause.getMessage());
                        if (cause instanceof java.io.EOFException) {
                            log.error("[{}] EOFException detected - connection may have been closed unexpectedly", traceId);
                        }
                    }
                }
                throw ioException;
            }
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
            loadBalancer.reportFailure(upstreamUri.toString(), System.currentTimeMillis());
            log.debug("[{}] Request from {} reported failure to load balancer for upstream {}", traceId, request.getAttribute("clientIp"), upstreamUri);
        } catch (HttpTimeoutException e) {
            HttpResponse errorResponse = httpContext.getResponse().toBuilder()
                    .httpVersion(request.getHttpVersion())
                    .httpStatus(HttpStatus.GATEWAY_TIMEOUT)
                    .contentType(ContentType.TEXT_HTML)
                    .build();
            httpContext.setResponse(errorResponse);
            ErrorPageRender.render(httpContext);
            log.error("[{}] {} Gateway Timeout when proxying request to upstream {}: {}", traceId, configId, upstreamUri, e.getMessage(), e);
            loadBalancer.reportFailure(upstreamUri.toString(), System.currentTimeMillis());
            log.debug("[{}] Request from {} reported failure to load balancer for upstream {}", traceId, request.getAttribute("clientIp"), upstreamUri);
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

    private URI buildUpstreamUri(HttpRequest request, ReverseProxyConfig proxyConfig) throws ConnectException {
        String path = request.getUri();
        Upstream selectedUpstream = loadBalancer.choose(request);
        if (selectedUpstream == null) {
            log.error("No healthy upstreams available for reverse proxy");
            throw new ConnectException("No healthy upstreams available");
        }
        String targetBase = selectedUpstream.getUrl();
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

    private void copyRequestHeaders(HttpRequest request, java.net.http.HttpRequest.Builder requestBuilder, ReverseProxyConfig proxyConfig, String traceId) {
        // 记录原始 Content-Length（如果存在）
        String originalContentLength = request.getHeaders().get("content-length");
        if (originalContentLength != null) {
            log.debug("[{}] Original Content-Length header: {} (will be filtered and set by HttpClient based on BodyPublisher)", 
                     traceId, originalContentLength);
        }
        
        // 复制请求头（过滤掉 hop-by-hop 头部，包括 content-length）
        request.getHeaders().forEach((name, value) -> {
            String lowerName = name.toLowerCase();
            if (!HOP_BY_HOP_HEADERS.contains(lowerName)) {
                requestBuilder.header(name, value);
            } else {
                log.debug("[{}] Filtering out hop-by-hop header: {}", traceId, name);
            }
        });
        
        // 添加 X-Forwarded-For 头部
        String clientIp = (String) request.getAttribute("clientIp");
        if (clientIp != null) {
            requestBuilder.header("X-Forwarded-For", clientIp);
        }
        
        // 添加配置中指定的额外头部
        proxyConfig.getAddHeaders().forEach((name, value) -> {
            String lowerName = name.toLowerCase();
            if (!HOP_BY_HOP_HEADERS.contains(lowerName)) {
                requestBuilder.header(name, value);
            } else {
                log.debug("[{}] Filtering out hop-by-hop header from addHeaders: {}", traceId, name);
            }
        });
    }

    private void setRequestBody(HttpRequest request, java.net.http.HttpRequest.Builder requestBuilder, String traceId) throws IOException {
        java.net.http.HttpRequest.BodyPublisher publisher;
        byte[] body = request.getBody();
        String contentType = request.getHeaders().get("content-type");
        boolean isMultipart = contentType != null && contentType.toLowerCase().startsWith("multipart/");

        boolean hasBodyMethod = "POST".equals(request.getMethod().name()) ||
                                "PUT".equals(request.getMethod().name()) ||
                                "PATCH".equals(request.getMethod().name());

        String contentLengthHeader = request.getHeaders().get("content-length");
        int expectedContentLength = 0;
        if (contentLengthHeader != null && !contentLengthHeader.trim().isEmpty()) {
            try {
                expectedContentLength = Integer.parseInt(contentLengthHeader.trim());
            } catch (NumberFormatException e) {
                log.warn("[{}] Invalid Content-Length header: {}", traceId, contentLengthHeader);
            }
        }
        
        log.debug("[{}] Setting request body: method={}, hasBodyMethod={}, expectedLength={}, actualLength={}, isMultipart={}, contentType={}",
                traceId, request.getMethod().name(), hasBodyMethod, expectedContentLength, 
                body != null ? body.length : 0, isMultipart, contentType);

        if (hasBodyMethod && expectedContentLength > 0) {
            if (body == null || body.length == 0) {
                log.error("[{}] CRITICAL: Request body is empty but Content-Length is {} bytes! " +
                         "This will cause HttpClient to fail with 'fixed content-length: {}, bytes received: 0'. " +
                         "Content-Type: {}, RawBodyInputStream: {}", 
                         traceId, expectedContentLength, expectedContentLength, contentType, 
                         request.getRawBodyInputStream() != null ? "exists" : "null");
                throw new IOException("Request body is empty but Content-Length header indicates " + 
                                     expectedContentLength + " bytes. This will cause upstream request to fail.");
            }

            if (body.length != expectedContentLength) {
                log.warn("[{}] Body length mismatch: expected {} bytes, actual {} bytes. " +
                        "Content-Type: {}. This may cause issues but we'll proceed with actual data.", 
                        traceId, expectedContentLength, body.length, contentType);
            }
            if (body.length == 0) {
                log.error("[{}] Body array is empty but we're trying to create BodyPublisher! This should not happen.", traceId);
                throw new IOException("Body array is empty but Content-Length indicates " + expectedContentLength + " bytes");
            }
            byte[] bodyCopy = new byte[body.length];
            System.arraycopy(body, 0, bodyCopy, 0, body.length);
            log.debug("[{}] Created body array copy: {} bytes (original array hash: {}, copy array hash: {})",
                    traceId, bodyCopy.length, System.identityHashCode(body), System.identityHashCode(bodyCopy));

            if (bodyCopy.length == 0) {
                log.error("[{}] CRITICAL: bodyCopy array is empty after copy!", traceId);
                throw new IOException("Body array is empty after copy, cannot create BodyPublisher");
            }
            publisher = java.net.http.HttpRequest.BodyPublishers.ofByteArray(bodyCopy);
            log.debug("[{}] BodyPublisher created successfully with {} bytes", traceId, bodyCopy.length);
        } else if (body != null && body.length > 0) {
            log.debug("[{}] Creating BodyPublisher with {} bytes (no Content-Length header)", traceId, body.length);
            byte[] bodyCopy = new byte[body.length];
            System.arraycopy(body, 0, bodyCopy, 0, body.length);
            publisher = java.net.http.HttpRequest.BodyPublishers.ofByteArray(bodyCopy);
        } else {
            if (hasBodyMethod) {
                log.debug("[{}] No body data for {} request (expectedLength: {})", 
                        traceId, request.getMethod().name(), expectedContentLength);
            }
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
        
        log.debug("[{}] Request body publisher created successfully for {} request", traceId, request.getMethod().name());
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

    public void shutdown() {
        healthChecker.stop();
    }
}