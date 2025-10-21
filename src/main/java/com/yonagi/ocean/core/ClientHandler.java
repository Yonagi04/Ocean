package com.yonagi.ocean.core;

import com.yonagi.ocean.admin.metrics.MetricsRegistry;
import com.yonagi.ocean.core.context.ConnectionContext;
import com.yonagi.ocean.core.context.HttpContext;
import com.yonagi.ocean.core.protocol.DefaultProtocolHandlerFactory;
import com.yonagi.ocean.core.protocol.HttpResponse;
import com.yonagi.ocean.core.protocol.enums.ContentType;
import com.yonagi.ocean.core.protocol.enums.HttpMethod;
import com.yonagi.ocean.core.protocol.HttpRequest;
import com.yonagi.ocean.core.protocol.HttpRequestParser;
import com.yonagi.ocean.core.protocol.enums.HttpStatus;
import com.yonagi.ocean.core.protocol.handler.HttpProtocolHandler;
import com.yonagi.ocean.core.ratelimiter.RateLimiterChecker;
import com.yonagi.ocean.core.router.Router;
import com.yonagi.ocean.handler.impl.*;
import com.yonagi.ocean.middleware.ChainExecutor;
import com.yonagi.ocean.middleware.MiddlewareChain;
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
import java.util.List;
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
    private final ConnectionManager connectionManager;
    private final ConnectionContext connectionContext;
    private final Router router;
    private final MiddlewareChain chain;

    private final List<HttpProtocolHandler> protocolHandlers;

    public ClientHandler(Socket client, ConnectionContext connectionContext) {
        this.client = client;
        this.connectionContext = connectionContext;

        this.connectionManager = connectionContext.getServerContext().getConnectionManager();
        this.router = connectionContext.getServerContext().getRouter();
        this.chain = connectionContext.getServerContext().getMiddlewareChain();

        this.protocolHandlers = new DefaultProtocolHandlerFactory().createHandlers(connectionContext);
    }

    @Override
    public void run() {
        // Register this connection for Keep-Alive management
        connectionManager.registerConnection(client);

        if (connectionContext.isSsl() && client instanceof SSLSocket) {
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
                HttpRequest currentRequest = request;

                for (HttpProtocolHandler handler : protocolHandlers) {
                    currentRequest = handler.handle(currentRequest, output);
                    if (currentRequest == null) {
                        break;
                    }
                }
                if (currentRequest == null) {
                    break;
                }
                currentRequest.setAttribute("clientIp", client.getInetAddress().getHostAddress());
                currentRequest.setAttribute("isSsl", connectionContext.isSsl());

                boolean shouldKeepAlive = shouldKeepAlive(currentRequest);
                if (!shouldKeepAlive) {
                    handleRequest(currentRequest, output, false);
                    break;
                }

                handleRequest(currentRequest, output, true);
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
        HttpResponse initialResponse = new HttpResponse.Builder()
                .httpVersion(request.getHttpVersion())
                .httpStatus(HttpStatus.OK)
                .contentType(ContentType.TEXT_PLAIN)
                .build();
        HttpContext httpContext = new HttpContext(request, initialResponse, output, keepAlive, connectionContext);

        Runnable routeHandler = () -> {
            try {
                router.route(httpContext);
            } catch (Exception e) {
                throw new RuntimeException("Routing failed", e);
            }
        };

        ChainExecutor chainExecutor = chain.newExecutor(routeHandler);
        try {
            chainExecutor.execute(httpContext);
        } catch (Exception e) {
            log.error("FATAL: Unhandled exception escaped the middleware chain: {}", e.getMessage(), e);
            sendFatalErrorResponse(httpContext);
        }
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

    private void sendFatalErrorResponse(HttpContext httpContext) {
        try {
            httpContext.getConnectionContext().getServerContext().getMetricsRegistry().getInternalServerErrorCounter().increment();
            HttpResponse errorResponse = httpContext.getResponse().toBuilder()
                    .httpVersion(httpContext.getResponse().getHttpVersion())
                    .httpStatus(HttpStatus.INTERNAL_SERVER_ERROR)
                    .contentType(ContentType.TEXT_PLAIN)
                    .body("FATAL SERVER ERROR".getBytes())
                    .build();
            httpContext.setResponse(errorResponse);
            ErrorPageRender.render(httpContext);
            httpContext.getResponse().write(httpContext.getRequest(), httpContext.getOutput(), httpContext.isKeepalive());
            httpContext.getOutput().flush();
        } catch (IOException e) {
            log.error("Failed to write fatal error response to client: {}", e.getMessage(), e);
        }
    }
}
