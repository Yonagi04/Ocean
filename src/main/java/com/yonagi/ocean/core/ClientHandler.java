package com.yonagi.ocean.core;

import com.yonagi.ocean.core.protocol.enums.HttpMethod;
import com.yonagi.ocean.core.protocol.HttpRequest;
import com.yonagi.ocean.core.protocol.HttpRequestParser;
import com.yonagi.ocean.core.ratelimiter.RateLimiterChecker;
import com.yonagi.ocean.core.router.Router;
import com.yonagi.ocean.handler.impl.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.Socket;
import java.util.Map;

/**
 * @author Yonagi
 * @version 1.0
 * @program Ocean
 * @description
 * @date 2025/10/03 18:23
 */
public class ClientHandler implements Runnable {

    private static final Logger log = LoggerFactory.getLogger(ClientHandler.class);

    private final Socket client;
    private final String webRoot;
    private final ConnectionManager connectionManager;
    private final Router router;
    private final RateLimiterChecker rateLimiterChecker;

    public ClientHandler(Socket client, String webRoot, ConnectionManager connectionManager,
                         Router router, RateLimiterChecker rateLimiterChecker) {
        this.client = client;
        this.webRoot = webRoot;
        this.connectionManager = connectionManager;
        this.router = router;
        this.rateLimiterChecker = rateLimiterChecker;
    }

    @Override
    public void run() {
        // Register this connection for Keep-Alive management
        connectionManager.registerConnection(client);
        
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
                request.setAttribute("clientIp", client.getInetAddress().getHostAddress());

                boolean shouldKeepAlive = shouldKeepAlive(request);
                if (!shouldKeepAlive) {
                    handleRequest(request, output, false);
                    break;
                }

                handleRequest(request, output, true);
                connectionManager.recordRequest(client);
                if (!connectionManager.shouldKeepAlive(client)) {
                    break;
                }
            }
        } catch (IOException e) {
            log.error("Error handling client: {}", e.getMessage(), e);
        } finally {
            try {
                connectionManager.removeConnection(client);
                client.close();
            } catch (IOException e) {
                log.warn("Error closing client connection: {}", e.getMessage());
            }
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
}
