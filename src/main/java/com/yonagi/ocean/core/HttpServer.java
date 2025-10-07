package com.yonagi.ocean.core;

import com.yonagi.ocean.cache.StaticFileCacheFactory;
import com.yonagi.ocean.utils.LocalConfigLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * @author Yonagi
 * @version 1.0
 * @program Ocean
 * @description
 * @date 2025/10/03 17:33
 */
public class HttpServer {

    private Integer port;
    private String webRoot;
    private Boolean isRunning;
    private ServerSocket serverSocket;
    private ThreadPoolExecutor threadPool;

    private static final Logger log = LoggerFactory.getLogger(HttpServer.class);

    public HttpServer() {
        int port = Integer.parseInt(LocalConfigLoader.getProperty("server.port"));
        String webRoot = LocalConfigLoader.getProperty("server.webroot");
        int corePoolSize = Math.min(Runtime.getRuntime().availableProcessors(),
                LocalConfigLoader.getProperty("server.threads") == null ? 2 : Integer.parseInt(LocalConfigLoader.getProperty("server.threads")));
        int maximumPoolSize = Runtime.getRuntime().availableProcessors() + 1;
        this.port = port;
        this.threadPool = new ThreadPoolExecutor(
                corePoolSize,
                maximumPoolSize,
                60L,
                TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(1000),
                new ThreadPoolExecutor.AbortPolicy()
        );
        this.webRoot = webRoot;
        StaticFileCacheFactory.init();
    }

    public void start() {
        try {
            serverSocket = new ServerSocket(port);
            isRunning = true;
            log.info("Ocean is running at http://{}:{}", InetAddress.getLocalHost().getHostAddress(), port);
            log.info("Web root: {}", webRoot);
            while (isRunning) {
                Socket client = serverSocket.accept();
                threadPool.execute(new ClientHandler(client, webRoot));
            }
        } catch (Exception e) {
            log.error("Error starting Ocean: {}", e.getMessage(), e);
        } finally {
            stop();
        }
    }

    public void stop() {
        isRunning = false;
        if (serverSocket != null && !serverSocket.isClosed()) {
            try {
                serverSocket.close();
            } catch (Exception e) {
                log.error("Error closing socket: {}", e.getMessage(), e);
            }
        }
        if (threadPool != null && !threadPool.isShutdown()) {
            threadPool.shutdown();
        }
        log.info("Ocean has stopped.");
    }
}
