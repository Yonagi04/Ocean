package com.yonagi.ocean.core;

import com.yonagi.ocean.cache.StaticFileCacheFactory;
import com.yonagi.ocean.core.configuration.KeepAliveConfig;
import com.yonagi.ocean.core.router.RouteConfigManager;
import com.yonagi.ocean.core.configuration.ServerStartupConfig;
import com.yonagi.ocean.core.configuration.source.route.ConfigSource;
import com.yonagi.ocean.core.configuration.source.route.FallbackConfigSource;
import com.yonagi.ocean.core.configuration.source.route.LocalConfigSource;
import com.yonagi.ocean.core.configuration.source.route.NacosConfigSource;
import com.yonagi.ocean.core.router.Router;
import com.yonagi.ocean.utils.LocalConfigLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ThreadPoolExecutor;

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
    private KeepAliveConfig keepAliveConfig;
    private ConnectionManager connectionManager;
    private Router router;
    private RouteConfigManager routeConfigManager;
    private ConfigSource configSource;

    private static final Logger log = LoggerFactory.getLogger(HttpServer.class);

    public HttpServer() {
        log.info("\n" +
                " _______  _______  _______  _______  _       \n" +
                "(  ___  )(  ____ \\(  ____ \\(  ___  )( (    /|\n" +
                "| (   ) || (    \\/| (    \\/| (   ) ||  \\  ( |\n" +
                "| |   | || |      | (__    | (___) ||   \\ | |\n" +
                "| |   | || |      |  __)   |  ___  || (\\ \\) |\n" +
                "| |   | || |      | (      | (   ) || | \\   |\n" +
                "| (___) || (____/\\| (____/\\| )   ( || )  \\  |\n" +
                "(_______)(_______/(_______/|/     \\||/    )_)\n" +
                "                                             ");
        ServerStartupConfig startupConfig = new ServerStartupConfig();
        this.port = startupConfig.getPort();
        this.webRoot = startupConfig.getWebRoot();
        this.threadPool = startupConfig.getThreadPool();
        this.keepAliveConfig = startupConfig.getKeepAliveConfig();

        // Initialize static file cache
        StaticFileCacheFactory.init();

        // Initialize core components
        initializeComponents(startupConfig);

        // Register configuration change listener
        this.configSource.onChange(() -> routeConfigManager.refreshRoutes(configSource));

        // Initialize routes if enabled
        if (Boolean.parseBoolean(LocalConfigLoader.getProperty("server.router.enabled", "true"))) {
            this.routeConfigManager.initializeRoutes(configSource);
        }
        
        log.info("HTTP Keep-Alive enabled: {}, timeout: {}s, max requests: {}", 
                keepAliveConfig.isEnabled(), 
                keepAliveConfig.getTimeoutSeconds(), 
                keepAliveConfig.getMaxRequests());
    }

    public void start() {
        try {
            serverSocket = new ServerSocket(port);
            isRunning = true;
            log.info("Ocean is running at http://{}:{}", InetAddress.getLocalHost().getHostAddress(), port);
            log.info("Web root: {}", webRoot);
            while (isRunning) {
                Socket client = serverSocket.accept();
                threadPool.execute(new ClientHandler(client, webRoot, connectionManager, router));
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
        if (connectionManager != null) {
            try {
                connectionManager.close();
            } catch (Exception e) {
                log.error("Error closing connection manager: {}", e.getMessage(), e);
            }
        }
        if (router != null) {
            router.shutdown();
        }

        log.info("Ocean has stopped.");
    }

    private void initializeComponents(ServerStartupConfig startupConfig) {
        // Initialize connection manager
        this.connectionManager = new ConnectionManager(startupConfig.getKeepAliveConfig());

        // Initialize router, route configuration manager and initial route configuration
        this.router = new Router(webRoot);
        this.routeConfigManager = new RouteConfigManager(router);
        this.configSource = new FallbackConfigSource(new NacosConfigSource(), new LocalConfigSource());
    }
}
