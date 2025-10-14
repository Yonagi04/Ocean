package com.yonagi.ocean.core;

import com.yonagi.ocean.core.protocol.HttpResponse;
import com.yonagi.ocean.core.protocol.enums.HttpMethod;
import com.yonagi.ocean.core.protocol.HttpRequest;
import com.yonagi.ocean.core.protocol.HttpRequestParser;
import com.yonagi.ocean.core.protocol.enums.HttpStatus;
import com.yonagi.ocean.core.ratelimiter.RateLimiterChecker;
import com.yonagi.ocean.core.router.Router;
import com.yonagi.ocean.handler.impl.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.SSLException;
import javax.net.ssl.SSLSocket;
import java.io.*;
import java.net.Socket;
import java.nio.charset.Charset;
import java.nio.charset.IllegalCharsetNameException;
import java.nio.charset.StandardCharsets;
import java.nio.charset.UnsupportedCharsetException;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * @author Yonagi
 * @version 1.0
 * @program Ocean
 * @description
 * @date 2025/10/03 18:23
 */
public class ClientHandler implements Runnable {

    private static final Logger log = LoggerFactory.getLogger(ClientHandler.class);
    private static final Pattern ABORT_PATTERN = Pattern.compile("connection (reset|abort)|主机中的软件中止|socket closed", Pattern.CASE_INSENSITIVE);

    private final Socket client;
    private final String webRoot;
    private final ConnectionManager connectionManager;
    private final Router router;
    private final RateLimiterChecker rateLimiterChecker;
    private final boolean isSsl;
    private final boolean redirectSslEnabled;
    private final int sslPort;

    public ClientHandler(Socket client, String webRoot, boolean isSsl, int sslPort, boolean redirectSslEnabled,
                         ConnectionManager connectionManager, Router router, RateLimiterChecker rateLimiterChecker) {
        this.client = client;
        this.webRoot = webRoot;
        this.isSsl = isSsl;
        this.sslPort = sslPort;
        this.redirectSslEnabled = redirectSslEnabled;
        this.connectionManager = connectionManager;
        this.router = router;
        this.rateLimiterChecker = rateLimiterChecker;
    }

    @Override
    public void run() {
        // Register this connection for Keep-Alive management
        connectionManager.registerConnection(client);

        if (isSsl && client instanceof SSLSocket) {
            try {
                ((SSLSocket) client).startHandshake();
            } catch (SSLException e) {
                log.error("SSL/TLS Handshake failed for client: {}", e.getMessage());
                performHandshakeCleanup(client);
                return;
            } catch (IOException e) {
                String msg = e.getMessage();
                if (msg != null && ABORT_PATTERN.matcher(msg).find()) {
                    log.info("Client actively closed connection during SSL Handshake for client {}: {}", client.getInetAddress().getHostAddress(), msg);
                } else {
                    log.error("I/O error during SSL Handshake for client {}: {}", client.getInetAddress().getHostAddress(), msg, e);
                }
                performHandshakeCleanup(client);
                return;
            }
        }

        try (InputStream input = client.getInputStream();
             OutputStream output = client.getOutputStream()) {
            
            // Handle multiple requests on the same connection
            while (!client.isClosed() && !client.isInputShutdown()) {
                if (!connectionManager.shouldKeepAlive(client)) {
                    break;
                }
                HttpRequest request = HttpRequestParser.parse(input);
                if (request == null) {
                    break;
                }
                if (!isSsl && redirectSslEnabled) {
                    String host = client.getLocalAddress().getHostAddress();
                    if (host.equals("0.0.0.0") || host.equals("127.0.0.1")) {
                        String hostHeader = request.getHeaders().getOrDefault("Host", "localhost");
                        host = hostHeader.split(":")[0];
                    }
                    String originalUri = request.getUri();
                    String redirectLocation = "https://" + host + ":" + sslPort + originalUri;
                    Map<String, String> responseHeaders = new HashMap<>();
                    responseHeaders.put("Location", redirectLocation);
                    HttpResponse redirectResponse = new HttpResponse.Builder()
                            .httpVersion(request.getHttpVersion())
                            .httpStatus(HttpStatus.MOVED_PERMANENTLY)
                            .contentType("text/html")
                            .headers(responseHeaders)
                            .body(String.format("Redirecting to %s", redirectLocation).getBytes())
                            .build();
                    redirectResponse.write(request, output, false);
                    log.info("Redirected HTTP request to HTTPS: {}", redirectLocation);
                    break;
                }

                String contentType = request.getHeaders().getOrDefault("content-type", "").toLowerCase();
                boolean isMultiPart = contentType.contains("multipart/form-data");

                boolean shouldReadSynchronously = (request.getMethod() == HttpMethod.POST || request.getMethod() == HttpMethod.PUT) &&
                        !isMultiPart;
                HttpRequest finalRequest = request;
                if (shouldReadSynchronously) {
                    byte[] bodyData = readTextBodyFromInputStream(request.getRawBodyInputStream(), request.getHeaders());
                    log.info("BodyData Length: {}", bodyData == null ? 0 : bodyData.length);
                    finalRequest = new HttpRequest.Builder()
                            .method(request.getMethod())
                            .uri(request.getUri())
                            .httpVersion(request.getHttpVersion())
                            .headers(request.getHeaders())
                            .queryParams(request.getQueryParams())
                            .body(bodyData)
                            .rawBodyInputStream(null)
                            .build();
                }

                finalRequest.setAttribute("clientIp", client.getInetAddress().getHostAddress());
                finalRequest.setAttribute("requestProtocol", isSsl ? "https" : "http");

                Map<String, String> corsHeaders = CorsManager.handleCors(finalRequest);
                boolean isPreflightTerminated = corsHeaders != null && "true".equals(corsHeaders.get("__IS_PREFLIGHT__"));
                if (isPreflightTerminated) {
                    log.info("CORS preflight request (OPTIONS) handled and terminated");
                    HttpResponse response = new HttpResponse.Builder()
                            .httpVersion(finalRequest.getHttpVersion())
                            .httpStatus(HttpStatus.NO_CONTENT)
                            .headers(corsHeaders)
                            .body(new byte[0])
                            .build();
                    response.write(finalRequest, output, false);
                    break;
                }
                if (corsHeaders != null && !corsHeaders.isEmpty()) {
                    corsHeaders.remove("__IS_PREFLIGHT__");
                    finalRequest.setAttribute("CorsResponseHeaders", corsHeaders);
                }

                boolean shouldKeepAlive = shouldKeepAlive(finalRequest);
                if (!shouldKeepAlive) {
                    handleRequest(finalRequest, output, false);
                    break;
                }

                handleRequest(finalRequest, output, true);
                connectionManager.recordRequest(client);
                if (!connectionManager.shouldKeepAlive(client)) {
                    break;
                }
            }
        } catch (IOException e) {
            String msg = e.getMessage();
            if (msg != null && ABORT_PATTERN.matcher(msg).find()) {
                log.info("Client actively closed connection during I/O: {}", msg);
            } else {
                log.error("Error handling client: {}", e.getMessage(), e);
            }
        } finally {
            try {
                connectionManager.removeConnection(client);
                client.close();
            } catch (IOException e) {
                log.warn("Error closing client connection: {}", e.getMessage());
            }
        }
    }

    private void performHandshakeCleanup(Socket client) {
        connectionManager.removeConnection(client);
        try {
            client.close();
        } catch (IOException closeE) {
            log.warn("Error closing client after SSL handshake failure: {}", closeE.getMessage());
        }
    }

    private void handleRequest(HttpRequest request, OutputStream output, boolean keepAlive) throws IOException {
        HttpMethod method = request.getMethod();
        String path = request.getUri();
        
        if (method == null) {
            new MethodNotAllowHandler().handle(request, output, keepAlive);
            return;
        }

        // 限流器做限流判断
        if (!rateLimiterChecker.check(request)) {
            new TooManyRequestsHandler().handle(request, output, keepAlive);
            return;
        }

        // 使用Router进行路由转发
        router.route(method, path, request, output, keepAlive);
    }

    /**
     * Determine if the connection should be kept alive based on request headers
     */
    private boolean shouldKeepAlive(HttpRequest request) {
        // Check if Keep-Alive is enabled in connection manager
        if (!connectionManager.shouldKeepAlive(client)) {
            return false;
        }

        if (request.getHttpVersion() == null || 
            !request.getHttpVersion().getVersion().equals("HTTP/1.1")) {
            return false;
        }
        
        // Check Connection header
        Map<String, String> headers = request.getHeaders();
        if (headers == null) {
            return true;
        }
        String connectionHeader = headers.get("Connection");
        if (connectionHeader == null) {
            return true;
        }
        return !connectionHeader.equalsIgnoreCase("close");
    }

    private byte[] readTextBodyFromInputStream(InputStream rawInputStream, Map<String, String> headers) throws IOException {
        if (rawInputStream == null) {
            return new byte[0];
        }
        String contentLengthStr = headers.get("content-length");
        int contentLength = contentLengthStr == null ? 0 : Integer.parseInt(contentLengthStr);

        if (contentLength <= 0) {
            return new byte[0];
        }

        Charset charset = StandardCharsets.UTF_8;
        String contentType = headers.get("content-type");
        if (contentType != null) {
            try {
                String[] parts = contentType.split(";");
                for (String part : parts) {
                    String trimmedPart = part.trim().toLowerCase();
                    if (trimmedPart.startsWith("charset=")) {
                        String charsetName = trimmedPart.substring("charset=".length()).trim();
                        charset = Charset.forName(charsetName);
                        break;
                    }
                }
            } catch (IllegalCharsetNameException | UnsupportedCharsetException e) {
                log.warn("Unsupported charset in Content-Type: {}, defaulting to UTF-8.", contentType);
            }
        }

        InputStreamReader isr = new InputStreamReader(rawInputStream, charset);
        BufferedReader reader = new BufferedReader(isr);

        CharArrayWriter writer = new CharArrayWriter(contentLength);
        char[] buffer = new char[1024];
        int remaining = contentLength;

        while (remaining > 0) {
            int read = reader.read(buffer, 0, Math.min(buffer.length, remaining));
            if (read == -1) {
                throw new IOException("Stream ended prematurely. Excepted " + contentLength + " bytes.");
            }
            writer.write(buffer, 0, read);
            remaining -= read;
        }
        return writer.toString().getBytes(charset);
    }
}
