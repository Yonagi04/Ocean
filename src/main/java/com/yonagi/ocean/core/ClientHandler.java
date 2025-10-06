package com.yonagi.ocean.core;

import com.yonagi.ocean.handler.StaticFileHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.Socket;

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
                BufferedReader reader = new BufferedReader(new InputStreamReader(input));
        ) {
            String requestLine = reader.readLine();
            if (requestLine == null || requestLine.isEmpty()) {
                return;
            }
            log.info("Request: {}", requestLine);
            String[] parts = requestLine.split(" ");
            if (parts.length < 2) {
                return;
            }

            String method = parts[0];
            String uri = parts[1];
            if (!"GET".equals(method)) {
                writeMethodNotAllow(output);
                return;
            }
            StaticFileHandler staticFileHandler = new StaticFileHandler(webRoot);
            staticFileHandler.handle(uri, output);
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

    private void writeMethodNotAllow(OutputStream outputStream) throws IOException {
        String body = "<h1>405 Method Not Allowed</h1>";
        HttpResponse response = new HttpResponse.Builder()
                .statusCode(405)
                .statusText("Method Not Allowed")
                .contentType("text/html")
                .body(body.getBytes())
                .build();
        outputStream.write(response.toString().getBytes());
        outputStream.flush();
    }
}
