package com.yonagi.ocean.core;

import com.yonagi.ocean.core.protocol.HttpMethod;
import com.yonagi.ocean.core.protocol.HttpRequest;
import com.yonagi.ocean.core.protocol.HttpRequestParser;
import com.yonagi.ocean.handler.RequestHandler;
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

    public ClientHandler(Socket client, String webRoot) {
        this.client = client;
        this.webRoot = webRoot;
    }

    @Override
    public void run() {
        try (
                InputStream input = client.getInputStream();
                OutputStream output = client.getOutputStream();
        ) {
            HttpRequest request = HttpRequestParser.parse(input);
            if (request == null) {
                return;
            }
            Map<HttpMethod, RequestHandler> handlers = Map.of(
                    HttpMethod.GET, new StaticFileHandler(webRoot),
                    HttpMethod.POST, new ApiHandler(webRoot),
                    HttpMethod.HEAD, new HeadHandler(webRoot),
                    HttpMethod.OPTIONS, new OptionsHandler(webRoot)
            );

            HttpMethod method = request.getMethod();
            if (method == null) {
                new MethodNotAllowHandler().handle(request, output);
                return;
            }
            
            RequestHandler requestHandler = handlers.get(method);
            if (requestHandler != null) {
                requestHandler.handle(request, output);
            } else {
                new MethodNotAllowHandler().handle(request, output);
            }
        } catch (IOException e) {
            log.error("Error handling client: {}", e.getMessage(), e);
        } finally {
            try {
                client.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
